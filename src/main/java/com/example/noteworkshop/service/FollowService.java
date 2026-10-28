package com.example.noteworkshop.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.noteworkshop.common.PageResponse;
import com.example.noteworkshop.entity.Note;
import com.example.noteworkshop.entity.User;
import com.example.noteworkshop.entity.UserFollow;
import com.example.noteworkshop.exception.BusinessException;
import com.example.noteworkshop.exception.ErrorCode;
import com.example.noteworkshop.mapper.NoteMapper;
import com.example.noteworkshop.mapper.UserFollowMapper;
import com.example.noteworkshop.mapper.UserMapper;
import com.example.noteworkshop.security.UserContext;
import com.example.noteworkshop.vo.AuthorStatsVO;
import com.example.noteworkshop.vo.FollowStatusVO;
import com.example.noteworkshop.vo.HotAuthorVO;
import com.example.noteworkshop.vo.NoteSummaryVO;
import com.example.noteworkshop.vo.UserProfileVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FollowService {
    private final UserFollowMapper userFollowMapper;
    private final UserMapper userMapper;
    private final NoteMapper noteMapper;
    private final NoteService noteService;

    public FollowService(UserFollowMapper userFollowMapper, UserMapper userMapper,
                         NoteMapper noteMapper, NoteService noteService) {
        this.userFollowMapper = userFollowMapper;
        this.userMapper = userMapper;
        this.noteMapper = noteMapper;
        this.noteService = noteService;
    }

    @Transactional
    public FollowStatusVO follow(Long targetUserId) {
        User target = requireAuthor(targetUserId);
        Long currentUserId = UserContext.get().id();
        if (currentUserId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不能关注自己");
        }
        long count = userFollowMapper.selectCount(Wrappers.<UserFollow>lambdaQuery()
                .eq(UserFollow::getFollowerId, currentUserId)
                .eq(UserFollow::getFollowingId, targetUserId));
        if (count == 0) {
            UserFollow follow = new UserFollow();
            follow.setFollowerId(currentUserId);
            follow.setFollowingId(targetUserId);
            try {
                userFollowMapper.insert(follow);
            } catch (DuplicateKeyException ignored) {
                // 并发关注由唯一索引兜底。
            }
        }
        return status(target.getId(), currentUserId);
    }

    @Transactional
    public FollowStatusVO unfollow(Long targetUserId) {
        requireAuthor(targetUserId);
        Long currentUserId = UserContext.get().id();
        userFollowMapper.delete(Wrappers.<UserFollow>lambdaQuery()
                .eq(UserFollow::getFollowerId, currentUserId)
                .eq(UserFollow::getFollowingId, targetUserId));
        return status(targetUserId, currentUserId);
    }

    public FollowStatusVO followStatus(Long targetUserId) {
        requireAuthor(targetUserId);
        return status(targetUserId, UserContext.get().id());
    }

    public UserProfileVO publicProfile(Long userId) {
        User user = requireAuthor(userId);
        return new UserProfileVO(user.getId(), user.getUsername(), user.getNickname(), user.getAvatarUrl(),
                user.getRole(), user.getStatus(), user.getCreatedAt());
    }

    public AuthorStatsVO authorStats(Long authorId) {
        requireAuthor(authorId);
        long followerCount = userFollowMapper.selectCount(Wrappers.<UserFollow>lambdaQuery()
                .eq(UserFollow::getFollowingId, authorId));
        long followingCount = userFollowMapper.selectCount(Wrappers.<UserFollow>lambdaQuery()
                .eq(UserFollow::getFollowerId, authorId));
        long publicNoteCount = noteMapper.selectCount(Wrappers.<Note>lambdaQuery()
                .eq(Note::getUserId, authorId)
                .eq(Note::getVisibility, 1)
                .eq(Note::getStatus, 1)
                .eq(Note::getAuditStatus, 1));
        return new AuthorStatsVO(followerCount, followingCount, publicNoteCount);
    }

    public PageResponse<HotAuthorVO> pageHotAuthors(long page, long size) {
        validatePage(page, size);
        Page<User> users = userMapper.selectHotAuthors(Page.of(page, size));
        List<Long> ids = users.getRecords().stream().map(User::getId).toList();
        Map<Long, Long> counts = new HashMap<>();
        if (!ids.isEmpty()) {
            List<UserFollow> follows = userFollowMapper.selectList(Wrappers.<UserFollow>lambdaQuery()
                    .in(UserFollow::getFollowingId, ids));
            for (UserFollow follow : follows) {
                counts.merge(follow.getFollowingId(), 1L, Long::sum);
            }
        }
        List<HotAuthorVO> records = users.getRecords().stream()
                .map(user -> new HotAuthorVO(user.getId(), user.getNickname(), user.getAvatarUrl(),
                        counts.getOrDefault(user.getId(), 0L)))
                .toList();
        return new PageResponse<>(records, users.getTotal(), users.getCurrent(), users.getSize(), users.getPages());
    }

    public PageResponse<NoteSummaryVO> pageAuthorPublicNotes(Long authorId, long page, long size) {
        requireAuthor(authorId);
        return noteService.pageAuthorPublicNotes(authorId, page, size);
    }

    private FollowStatusVO status(Long targetUserId, Long currentUserId) {
        long followerCount = userFollowMapper.selectCount(Wrappers.<UserFollow>lambdaQuery()
                .eq(UserFollow::getFollowingId, targetUserId));
        long followingCount = userFollowMapper.selectCount(Wrappers.<UserFollow>lambdaQuery()
                .eq(UserFollow::getFollowerId, targetUserId));
        boolean following = userFollowMapper.selectCount(Wrappers.<UserFollow>lambdaQuery()
                .eq(UserFollow::getFollowerId, currentUserId)
                .eq(UserFollow::getFollowingId, targetUserId)) > 0;
        return new FollowStatusVO(following, followerCount, followingCount);
    }

    private User requireAuthor(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "作者不存在");
        }
        return user;
    }

    private void validatePage(long page, long size) {
        if (page < 1 || size < 1 || size > 50) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "分页参数不合法");
        }
    }
}
package com.example.noteworkshop.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.noteworkshop.common.PageResponse;
import com.example.noteworkshop.dto.NoteReviewRequest;
import com.example.noteworkshop.dto.UserStatusUpdateRequest;
import com.example.noteworkshop.entity.AiAuditLog;
import com.example.noteworkshop.entity.User;
import com.example.noteworkshop.exception.BusinessException;
import com.example.noteworkshop.exception.ErrorCode;
import com.example.noteworkshop.mapper.AiAuditLogMapper;
import com.example.noteworkshop.mapper.UserMapper;
import com.example.noteworkshop.security.UserContext;
import com.example.noteworkshop.vo.AiStatsVO;
import com.example.noteworkshop.vo.NoteSummaryVO;
import com.example.noteworkshop.vo.UserProfileVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminService {
    private final UserMapper userMapper;
    private final NoteService noteService;
    private final AiAuditLogMapper aiAuditLogMapper;
    private final CommentService commentService;

    public AdminService(UserMapper userMapper, NoteService noteService, AiAuditLogMapper aiAuditLogMapper,
                        CommentService commentService) {
        this.userMapper = userMapper;
        this.noteService = noteService;
        this.aiAuditLogMapper = aiAuditLogMapper;
        this.commentService = commentService;
    }

    public PageResponse<UserProfileVO> pageUsers(long page, long size) {
        validatePage(page, size);
        Page<User> result = userMapper.selectPage(Page.of(page, size),
                Wrappers.<User>lambdaQuery().orderByDesc(User::getCreatedAt).orderByDesc(User::getId));
        List<UserProfileVO> records = result.getRecords().stream().map(this::toProfileVO).toList();
        return new PageResponse<>(records, result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    @Transactional
    public UserProfileVO updateUserStatus(Long userId, UserStatusUpdateRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if (UserContext.get().id().equals(userId) && Integer.valueOf(0).equals(request.status())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不能禁用当前管理员账号");
        }
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, userId)
                .set(User::getStatus, request.status()));
        user.setStatus(request.status());
        return toProfileVO(user);
    }

    public PageResponse<NoteSummaryVO> pageNotes(Integer auditStatus, long page, long size) {
        return noteService.pageAdminNotes(auditStatus, page, size);
    }

    public PageResponse<NoteSummaryVO> pagePendingNotes(long page, long size) {
        return noteService.pageAdminNotes(0, page, size);
    }

    public void approveNote(Long noteId) {
        noteService.approveNote(noteId);
    }

    public void rejectNote(Long noteId, NoteReviewRequest request) {
        noteService.rejectNote(noteId, request);
    }

    public void hideNote(Long noteId) {
        noteService.hideNote(noteId);
    }

    public void deleteComment(Long commentId) {
        commentService.delete(commentId);
    }

    public AiStatsVO aiStats() {
        long total = aiAuditLogMapper.selectCount(Wrappers.<AiAuditLog>lambdaQuery());
        long success = aiAuditLogMapper.selectCount(Wrappers.<AiAuditLog>lambdaQuery().eq(AiAuditLog::getSuccess, 1));
        long failure = aiAuditLogMapper.selectCount(Wrappers.<AiAuditLog>lambdaQuery().eq(AiAuditLog::getSuccess, 0));
        return new AiStatsVO(total, success, failure);
    }

    private void validatePage(long page, long size) {
        if (page < 1 || size < 1 || size > 50) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "分页参数不合法");
        }
    }

    private UserProfileVO toProfileVO(User user) {
        return new UserProfileVO(user.getId(), user.getUsername(), user.getNickname(), user.getAvatarUrl(),
                user.getRole(), user.getStatus(), user.getCreatedAt());
    }
}
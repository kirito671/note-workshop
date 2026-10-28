package com.example.noteworkshop.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.noteworkshop.dto.CommentCreateRequest;
import com.example.noteworkshop.entity.Note;
import com.example.noteworkshop.entity.NoteComment;
import com.example.noteworkshop.entity.User;
import com.example.noteworkshop.exception.BusinessException;
import com.example.noteworkshop.exception.ErrorCode;
import com.example.noteworkshop.mapper.NoteCommentMapper;
import com.example.noteworkshop.mapper.NoteMapper;
import com.example.noteworkshop.mapper.UserMapper;
import com.example.noteworkshop.security.CurrentUser;
import com.example.noteworkshop.security.UserContext;
import com.example.noteworkshop.vo.CommentVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class CommentService {
    private static final Logger log = LoggerFactory.getLogger(CommentService.class);
    private static final int STATUS_NORMAL = 1;
    private static final int STATUS_DELETED = 0;

    private final NoteCommentMapper noteCommentMapper;
    private final NoteMapper noteMapper;
    private final UserMapper userMapper;

    public CommentService(NoteCommentMapper noteCommentMapper, NoteMapper noteMapper, UserMapper userMapper) {
        this.noteCommentMapper = noteCommentMapper;
        this.noteMapper = noteMapper;
        this.userMapper = userMapper;
    }

    public List<CommentVO> list(Long noteId) {
        requireCommentableNote(noteId);
        List<NoteComment> comments = noteCommentMapper.selectList(Wrappers.<NoteComment>lambdaQuery()
                .eq(NoteComment::getNoteId, noteId).eq(NoteComment::getStatus, STATUS_NORMAL)
                .orderByAsc(NoteComment::getCreatedAt).orderByAsc(NoteComment::getId));
        if (comments.isEmpty()) return List.of();

        Map<Long, List<NoteComment>> replies = new HashMap<>();
        Set<Long> activeIds = new HashSet<>();
        for (NoteComment comment : comments) {
            activeIds.add(comment.getId());
            if (comment.getParentId() != null) {
                replies.computeIfAbsent(comment.getParentId(), ignored -> new ArrayList<>()).add(comment);
            }
        }
        Map<Long, User> users = loadUsers(comments);
        List<CommentVO> result = new ArrayList<>();
        for (NoteComment comment : comments) {
            if (comment.getParentId() == null || !activeIds.contains(comment.getParentId())) {
                result.add(toVO(comment, replies, users, new HashSet<>()));
            }
        }
        return result;
    }

    @Transactional
    public CommentVO create(Long noteId, CommentCreateRequest request) {
        requireCommentableNote(noteId);
        CurrentUser currentUser = UserContext.get();
        if (request.parentId() != null) {
            NoteComment parent = noteCommentMapper.selectById(request.parentId());
            if (parent == null || !noteId.equals(parent.getNoteId())
                    || !Integer.valueOf(STATUS_NORMAL).equals(parent.getStatus())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "父评论不存在");
            }
        }
        NoteComment comment = new NoteComment();
        comment.setNoteId(noteId);
        comment.setUserId(currentUser.id());
        comment.setParentId(request.parentId());
        comment.setContent(request.content().trim());
        comment.setStatus(STATUS_NORMAL);
        noteCommentMapper.insert(comment);
        NoteComment saved = noteCommentMapper.selectById(comment.getId());
        User user = userMapper.selectById(currentUser.id());
        log.info("Comment created, id={}, noteId={}, userId={}, parentId={}",
                saved.getId(), noteId, currentUser.id(), saved.getParentId());
        return toVO(saved, Map.of(), Map.of(user.getId(), user), new HashSet<>());
    }

    @Transactional
    public void delete(Long commentId) {
        NoteComment comment = noteCommentMapper.selectById(commentId);
        if (comment == null) throw new BusinessException(ErrorCode.NOT_FOUND, "评论不存在");
        CurrentUser currentUser = UserContext.get();
        if (!currentUser.id().equals(comment.getUserId()) && !Integer.valueOf(1).equals(currentUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能删除自己的评论");
        }
        if (Integer.valueOf(STATUS_DELETED).equals(comment.getStatus())) return;

        List<NoteComment> all = noteCommentMapper.selectList(
                Wrappers.<NoteComment>lambdaQuery().eq(NoteComment::getNoteId, comment.getNoteId()));
        Map<Long, List<Long>> children = new HashMap<>();
        for (NoteComment item : all) {
            if (item.getParentId() != null) {
                children.computeIfAbsent(item.getParentId(), ignored -> new ArrayList<>()).add(item.getId());
            }
        }
        Set<Long> ids = new LinkedHashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(commentId);
        while (!queue.isEmpty()) {
            Long current = queue.removeFirst();
            if (ids.add(current)) queue.addAll(children.getOrDefault(current, List.of()));
        }
        noteCommentMapper.update(null, Wrappers.<NoteComment>lambdaUpdate()
                .in(NoteComment::getId, ids).set(NoteComment::getStatus, STATUS_DELETED));
        log.info("Comment tree logically deleted, rootId={}, affected={}, operatorId={}",
                commentId, ids.size(), currentUser.id());
    }

    private void requireCommentableNote(Long noteId) {
        Note note = noteMapper.selectById(noteId);
        if (note == null || !Integer.valueOf(1).equals(note.getVisibility())
                || !Integer.valueOf(1).equals(note.getStatus())
                || !Integer.valueOf(1).equals(note.getAuditStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公开笔记不存在");
        }
    }

    private Map<Long, User> loadUsers(List<NoteComment> comments) {
        Set<Long> ids = new HashSet<>();
        comments.forEach(comment -> ids.add(comment.getUserId()));
        Map<Long, User> users = new HashMap<>();
        if (!ids.isEmpty()) for (User user : userMapper.selectBatchIds(ids)) users.put(user.getId(), user);
        return users;
    }

    private CommentVO toVO(NoteComment comment, Map<Long, List<NoteComment>> replies,
                           Map<Long, User> users, Set<Long> path) {
        if (!path.add(comment.getId())) return null;
        User user = users.get(comment.getUserId());
        List<CommentVO> children = new ArrayList<>();
        for (NoteComment child : replies.getOrDefault(comment.getId(), List.of())) {
            CommentVO childVO = toVO(child, replies, users, path);
            if (childVO != null) children.add(childVO);
        }
        path.remove(comment.getId());
        return new CommentVO(comment.getId(), comment.getNoteId(), comment.getUserId(),
                user == null ? "已注销用户" : user.getNickname(), user == null ? null : user.getAvatarUrl(),
                comment.getParentId(), comment.getContent(), comment.getCreatedAt(), children);
    }
}
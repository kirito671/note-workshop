package com.example.noteworkshop.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.noteworkshop.entity.Note;
import com.example.noteworkshop.entity.NoteLike;
import com.example.noteworkshop.exception.BusinessException;
import com.example.noteworkshop.exception.ErrorCode;
import com.example.noteworkshop.mapper.NoteLikeMapper;
import com.example.noteworkshop.mapper.NoteMapper;
import com.example.noteworkshop.security.CurrentUser;
import com.example.noteworkshop.security.UserContext;
import com.example.noteworkshop.vo.LikeStatusVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NoteLikeService {
    private final NoteMapper noteMapper;
    private final NoteLikeMapper noteLikeMapper;

    public NoteLikeService(NoteMapper noteMapper, NoteLikeMapper noteLikeMapper) {
        this.noteMapper = noteMapper;
        this.noteLikeMapper = noteLikeMapper;
    }

    @Transactional
    public LikeStatusVO like(Long noteId) {
        Note note = requirePublicNote(noteId);
        CurrentUser currentUser = UserContext.get();
        Long userId = currentUser.id();

        long existingLikes = noteLikeMapper.selectCount(
                Wrappers.<NoteLike>lambdaQuery()
                        .eq(NoteLike::getUserId, userId)
                        .eq(NoteLike::getNoteId, noteId)
        );

        if (existingLikes == 0) {
            NoteLike noteLike = new NoteLike();
            noteLike.setUserId(userId);
            noteLike.setNoteId(noteId);
            try {
                noteLikeMapper.insert(noteLike);
            } catch (DuplicateKeyException ignored) {
                // 并发重复点赞时由唯一索引兜底，最终仍返回已点赞状态。
            }
        }

        return new LikeStatusVO(true, countLikes(note.getId()));
    }

    @Transactional
    public LikeStatusVO unlike(Long noteId) {
        Note note = requirePublicNote(noteId);
        CurrentUser currentUser = UserContext.get();

        noteLikeMapper.delete(
                Wrappers.<NoteLike>lambdaQuery()
                        .eq(NoteLike::getUserId, currentUser.id())
                        .eq(NoteLike::getNoteId, noteId)
        );

        return new LikeStatusVO(false, countLikes(note.getId()));
    }

    private Note requirePublicNote(Long noteId) {
        Note note = noteMapper.selectById(noteId);
        if (note == null
                || !Integer.valueOf(1).equals(note.getVisibility())
                || !Integer.valueOf(1).equals(note.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "笔记不存在");
        }
        return note;
    }

    private long countLikes(Long noteId) {
        return noteLikeMapper.selectCount(
                Wrappers.<NoteLike>lambdaQuery()
                        .eq(NoteLike::getNoteId, noteId)
        );
    }
}
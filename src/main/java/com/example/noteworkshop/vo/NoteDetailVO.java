package com.example.noteworkshop.vo;

import java.time.LocalDateTime;
import java.util.List;

public record NoteDetailVO(
        Long id,
        Long userId,
        String authorNickname,
        String authorAvatarUrl,
        String title,
        String bookName,
        String content,
        Integer visibility,
        Integer status,
        Integer auditStatus,
        String auditRemark,
        long likeCount,
        boolean likedByMe,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<String> images,
        NoteAiResultVO aiResult
) {
}
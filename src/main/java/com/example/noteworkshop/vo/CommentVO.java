package com.example.noteworkshop.vo;

import java.time.LocalDateTime;
import java.util.List;

public record CommentVO(
        Long id,
        Long noteId,
        Long userId,
        String nickname,
        String avatarUrl,
        Long parentId,
        String content,
        LocalDateTime createdAt,
        List<CommentVO> replies
) {
}
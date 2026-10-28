package com.example.noteworkshop.vo;

import java.time.LocalDateTime;

public record NoteSummaryVO(
        Long id,
        Long userId,
        String authorNickname,
        String authorAvatarUrl,
        String title,
        String bookName,
        Integer visibility,
        Integer status,
        Integer auditStatus,
        String auditRemark,
        long likeCount,
        boolean likedByMe,
        LocalDateTime createdAt
) {
}
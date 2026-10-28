package com.example.noteworkshop.vo;

import java.time.LocalDateTime;

public record NoteSummaryVO(
        Long id,
        Long userId,
        String title,
        String bookName,
        Integer visibility,
        Integer status,
        long likeCount,
        boolean likedByMe,
        LocalDateTime createdAt
) {
}
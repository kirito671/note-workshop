package com.example.noteworkshop.vo;

import java.time.LocalDateTime;

public record AiConversationVO(
        Long id,
        String title,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
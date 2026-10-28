package com.example.noteworkshop.vo;

import java.time.LocalDateTime;

public record AiMessageVO(
        Long id,
        String role,
        String content,
        LocalDateTime createdAt
) {
}
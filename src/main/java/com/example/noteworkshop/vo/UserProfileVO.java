package com.example.noteworkshop.vo;

import java.time.LocalDateTime;

public record UserProfileVO(
        Long id,
        String username,
        String nickname,
        String avatarUrl,
        Integer role,
        Integer status,
        LocalDateTime createdAt
) {
}
package com.example.noteworkshop.vo;

public record HotAuthorVO(
        Long userId,
        String nickname,
        String avatarUrl,
        long followerCount
) {
}
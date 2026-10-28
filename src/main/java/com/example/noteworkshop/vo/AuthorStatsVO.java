package com.example.noteworkshop.vo;

public record AuthorStatsVO(
        long followerCount,
        long followingCount,
        long publicNoteCount
) {
}
package com.example.noteworkshop.vo;

public record FollowStatusVO(
        boolean following,
        long followerCount,
        long followingCount
) {
}
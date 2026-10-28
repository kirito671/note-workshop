package com.example.noteworkshop.vo;

public record AiStatsVO(
        long total,
        long success,
        long failure
) {
}
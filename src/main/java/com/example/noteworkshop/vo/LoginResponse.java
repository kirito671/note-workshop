package com.example.noteworkshop.vo;

public record LoginResponse(
        String token,
        String tokenType,
        long expiresInMinutes
) {
}
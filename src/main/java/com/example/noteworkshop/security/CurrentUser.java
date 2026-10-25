package com.example.noteworkshop.security;

public record CurrentUser(
        Long id,
        String username,
        Integer role
) {
}
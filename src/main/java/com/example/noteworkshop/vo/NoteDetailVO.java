package com.example.noteworkshop.vo;

import java.time.LocalDateTime;
import java.util.List;

public record NoteDetailVO(
        Long id,
        Long userId,
        String title,
        String bookName,
        String content,
        Integer visibility,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<String> images,
        String aiResult
) {
}
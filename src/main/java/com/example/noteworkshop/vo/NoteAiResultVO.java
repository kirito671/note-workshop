package com.example.noteworkshop.vo;

public record NoteAiResultVO(
        Long id,
        String summary,
        String audience,
        Integer completenessScore,
        Integer relevanceScore,
        Integer depthScore
) {
}
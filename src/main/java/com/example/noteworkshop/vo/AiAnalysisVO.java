package com.example.noteworkshop.vo;

public record AiAnalysisVO(
        Long noteId,
        String summary,
        String audience,
        Integer completenessScore,
        Integer relevanceScore,
        Integer depthScore
) {
}
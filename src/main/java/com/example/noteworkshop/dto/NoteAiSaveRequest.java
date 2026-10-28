package com.example.noteworkshop.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NoteAiSaveRequest(
        @NotBlank @Size(max = 5000) String summary,
        @NotBlank @Size(max = 500) String audience,
        @NotNull @Min(0) @Max(100) Integer completenessScore,
        @NotNull @Min(0) @Max(100) Integer relevanceScore,
        @NotNull @Min(0) @Max(100) Integer depthScore
) {
}
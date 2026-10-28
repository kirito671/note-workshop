package com.example.noteworkshop.dto;

import jakarta.validation.constraints.Size;

public record NoteReviewRequest(
        @Size(max = 500) String remark
) {
}
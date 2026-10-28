package com.example.noteworkshop.dto;

import jakarta.validation.constraints.Size;

public record AiConversationCreateRequest(
        @Size(max = 100) String title
) {
}
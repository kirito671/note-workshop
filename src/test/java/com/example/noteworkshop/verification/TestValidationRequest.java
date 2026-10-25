package com.example.noteworkshop.verification;

import jakarta.validation.constraints.NotBlank;

public record TestValidationRequest(
        @NotBlank(message = "标题不能为空")
        String title
) {
}

package com.example.noteworkshop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 50, message = "用户名长度必须为 3-50 个字符")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 72, message = "密码长度必须为 6-72 个字符")
        String password,

        @NotBlank(message = "昵称不能为空")
        @Size(max = 50, message = "昵称不能超过 50 个字符")
        String nickname
) {
}

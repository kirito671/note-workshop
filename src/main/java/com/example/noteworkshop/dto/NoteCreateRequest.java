package com.example.noteworkshop.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NoteCreateRequest(
        @NotBlank(message = "标题不能为空")
        @Size(max = 120, message = "标题不能超过 120 个字符")
        String title,

        @NotBlank(message = "图书名称不能为空")
        @Size(max = 200, message = "图书名称不能超过 200 个字符")
        String bookName,

        @NotBlank(message = "笔记内容不能为空")
        @Size(max = 10000, message = "笔记内容不能超过 10000 个字符")
        String content,

        @NotNull(message = "可见性不能为空")
        @Min(value = 0, message = "可见性只能是 0 或 1")
        @Max(value = 1, message = "可见性只能是 0 或 1")
        Integer visibility
) {
}
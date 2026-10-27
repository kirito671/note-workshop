package com.example.noteworkshop.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
public record NoteImageOrderRequest(
        @NotEmpty(message = "图片排序列表不能为空")
        List<@NotNull(message = "图片 ID 不能为空") Long> imageIds
) {
}

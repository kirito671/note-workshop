package com.example.noteworkshop.controller;

import com.example.noteworkshop.common.ApiResponse;
import com.example.noteworkshop.dto.CommentCreateRequest;
import com.example.noteworkshop.service.CommentService;
import com.example.noteworkshop.vo.CommentVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/notes/{noteId}/comments")
public class CommentController {
    private final CommentService commentService;
    public CommentController(CommentService commentService) { this.commentService = commentService; }

    @GetMapping
    public ApiResponse<List<CommentVO>> list(@PathVariable Long noteId) {
        return ApiResponse.success(commentService.list(noteId));
    }

    @PostMapping
    public ApiResponse<CommentVO> create(@PathVariable Long noteId, @Valid @RequestBody CommentCreateRequest request) {
        return ApiResponse.success(commentService.create(noteId, request));
    }

    @DeleteMapping("/{commentId}")
    public ApiResponse<Void> delete(@PathVariable Long noteId, @PathVariable Long commentId) {
        commentService.delete(commentId);
        return ApiResponse.success(null);
    }
}
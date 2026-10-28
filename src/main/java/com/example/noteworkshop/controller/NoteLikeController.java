package com.example.noteworkshop.controller;

import com.example.noteworkshop.common.ApiResponse;
import com.example.noteworkshop.service.NoteLikeService;
import com.example.noteworkshop.vo.LikeStatusVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notes")
public class NoteLikeController {
    private final NoteLikeService noteLikeService;

    public NoteLikeController(NoteLikeService noteLikeService) {
        this.noteLikeService = noteLikeService;
    }

    @PostMapping("/{id}/like")
    public ApiResponse<LikeStatusVO> like(@PathVariable Long id) {
        return ApiResponse.success(noteLikeService.like(id));
    }

    @DeleteMapping("/{id}/like")
    public ApiResponse<LikeStatusVO> unlike(@PathVariable Long id) {
        return ApiResponse.success(noteLikeService.unlike(id));
    }
}
package com.example.noteworkshop.controller;

import com.example.noteworkshop.common.ApiResponse;
import com.example.noteworkshop.common.PageResponse;
import com.example.noteworkshop.dto.NoteReviewRequest;
import com.example.noteworkshop.dto.UserStatusUpdateRequest;
import com.example.noteworkshop.service.AdminService;
import com.example.noteworkshop.vo.AiStatsVO;
import com.example.noteworkshop.vo.NoteSummaryVO;
import com.example.noteworkshop.vo.UserProfileVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService adminService;
    public AdminController(AdminService adminService) { this.adminService = adminService; }

    @GetMapping("/users")
    public ApiResponse<PageResponse<UserProfileVO>> pageUsers(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.success(adminService.pageUsers(page, size));
    }

    @PutMapping("/users/{id}/status")
    public ApiResponse<UserProfileVO> updateUserStatus(@PathVariable Long id,
            @Valid @RequestBody UserStatusUpdateRequest request) {
        return ApiResponse.success(adminService.updateUserStatus(id, request));
    }

    @GetMapping("/notes")
    public ApiResponse<PageResponse<NoteSummaryVO>> pageNotes(@RequestParam(required = false) Integer auditStatus,
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.success(adminService.pageNotes(auditStatus, page, size));
    }

    @GetMapping("/notes/pending")
    public ApiResponse<PageResponse<NoteSummaryVO>> pagePendingNotes(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.success(adminService.pagePendingNotes(page, size));
    }

    @PutMapping("/notes/{id}/approve")
    public ApiResponse<Void> approveNote(@PathVariable Long id) {
        adminService.approveNote(id); return ApiResponse.success(null);
    }

    @PutMapping("/notes/{id}/reject")
    public ApiResponse<Void> rejectNote(@PathVariable Long id, @Valid @RequestBody NoteReviewRequest request) {
        adminService.rejectNote(id, request); return ApiResponse.success(null);
    }

    @PutMapping("/notes/{id}/hide")
    public ApiResponse<Void> hideNote(@PathVariable Long id) {
        adminService.hideNote(id); return ApiResponse.success(null);
    }

    @DeleteMapping("/comments/{id}")
    public ApiResponse<Void> deleteComment(@PathVariable Long id) {
        adminService.deleteComment(id); return ApiResponse.success(null);
    }

    @GetMapping("/ai/stats")
    public ApiResponse<AiStatsVO> aiStats() { return ApiResponse.success(adminService.aiStats()); }
}
package com.example.noteworkshop.controller;

import com.example.noteworkshop.common.ApiResponse;
import com.example.noteworkshop.common.PageResponse;
import com.example.noteworkshop.service.FollowService;
import com.example.noteworkshop.vo.AuthorStatsVO;
import com.example.noteworkshop.vo.FollowStatusVO;
import com.example.noteworkshop.vo.HotAuthorVO;
import com.example.noteworkshop.vo.NoteSummaryVO;
import com.example.noteworkshop.vo.UserProfileVO;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class FollowController {
    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    @PostMapping("/{id}/follow")
    public ApiResponse<FollowStatusVO> follow(@PathVariable Long id) {
        return ApiResponse.success(followService.follow(id));
    }

    @DeleteMapping("/{id}/follow")
    public ApiResponse<FollowStatusVO> unfollow(@PathVariable Long id) {
        return ApiResponse.success(followService.unfollow(id));
    }

    @GetMapping("/{id}/follow-status")
    public ApiResponse<FollowStatusVO> followStatus(@PathVariable Long id) {
        return ApiResponse.success(followService.followStatus(id));
    }

    @GetMapping("/{id}/profile")
    public ApiResponse<UserProfileVO> publicProfile(@PathVariable Long id) {
        return ApiResponse.success(followService.publicProfile(id));
    }

    @GetMapping("/{id}/stats")
    public ApiResponse<AuthorStatsVO> authorStats(@PathVariable Long id) {
        return ApiResponse.success(followService.authorStats(id));
    }

    @GetMapping("/{id}/notes")
    public ApiResponse<PageResponse<NoteSummaryVO>> authorNotes(@PathVariable Long id,
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.success(followService.pageAuthorPublicNotes(id, page, size));
    }

    @GetMapping("/hot-authors")
    public ApiResponse<PageResponse<HotAuthorVO>> hotAuthors(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size) {
        return ApiResponse.success(followService.pageHotAuthors(page, size));
    }
}
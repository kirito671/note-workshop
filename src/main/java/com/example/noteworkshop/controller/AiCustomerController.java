package com.example.noteworkshop.controller;

import com.example.noteworkshop.common.ApiResponse;
import com.example.noteworkshop.common.PageResponse;
import com.example.noteworkshop.dto.AiChatRequest;
import com.example.noteworkshop.dto.AiConversationCreateRequest;
import com.example.noteworkshop.service.AiCustomerService;
import com.example.noteworkshop.vo.AiChatResponseVO;
import com.example.noteworkshop.vo.AiConversationDetailVO;
import com.example.noteworkshop.vo.AiConversationVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/conversations")
public class AiCustomerController {
    private final AiCustomerService aiCustomerService;
    public AiCustomerController(AiCustomerService aiCustomerService) { this.aiCustomerService = aiCustomerService; }

    @PostMapping
    public ApiResponse<AiConversationVO> create(@Valid @RequestBody(required = false) AiConversationCreateRequest request) {
        return ApiResponse.success(aiCustomerService.create(request));
    }

    @GetMapping
    public ApiResponse<PageResponse<AiConversationVO>> page(
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long size) {
        return ApiResponse.success(aiCustomerService.page(page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<AiConversationDetailVO> detail(@PathVariable Long id) {
        return ApiResponse.success(aiCustomerService.detail(id));
    }

    @PostMapping("/{id}/messages")
    public ApiResponse<AiChatResponseVO> send(@PathVariable Long id, @Valid @RequestBody AiChatRequest request) {
        return ApiResponse.success(aiCustomerService.send(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        aiCustomerService.delete(id);
        return ApiResponse.success(null);
    }
}
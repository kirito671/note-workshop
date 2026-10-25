package com.example.noteworkshop.verification;

import com.example.noteworkshop.common.ApiResponse;
import com.example.noteworkshop.exception.BusinessException;
import com.example.noteworkshop.exception.ErrorCode;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/test")
public class TestVerificationController {
    @GetMapping("/unexpected-error")
    public ApiResponse<Void> unexpectedError() {
        throw new RuntimeException("测试服务器异常");
    }
    @PostMapping("/validation-error")
    public ApiResponse<Void> validationError(
            @Valid @RequestBody TestValidationRequest request
    ) {
        return ApiResponse.success();
    }

    @GetMapping("/success")
    public ApiResponse<Map<String, Boolean>> success() {
        return ApiResponse.success(Map.of("verified", true));
    }

    @GetMapping("/business-error")
    public ApiResponse<Void> businessError() {
        throw new BusinessException(ErrorCode.FORBIDDEN, "测试无权限");
    }
}
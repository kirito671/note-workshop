package com.example.noteworkshop.verification;

import com.example.noteworkshop.common.ApiResponse;
import com.example.noteworkshop.exception.BusinessException;
import com.example.noteworkshop.exception.ErrorCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/test")
public class TestVerificationController {

    @GetMapping("/success")
    public ApiResponse<Map<String, Boolean>> success() {
        return ApiResponse.success(Map.of("verified", true));
    }

    @GetMapping("/business-error")
    public ApiResponse<Void> businessError() {
        throw new BusinessException(ErrorCode.FORBIDDEN, "测试无权限");
    }
}
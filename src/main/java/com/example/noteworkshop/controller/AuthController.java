package com.example.noteworkshop.controller;

import com.example.noteworkshop.common.ApiResponse;
import com.example.noteworkshop.dto.LoginRequest;
import com.example.noteworkshop.dto.RegisterRequest;
import com.example.noteworkshop.service.AuthService;
import com.example.noteworkshop.service.UserService;
import com.example.noteworkshop.vo.LoginResponse;
import com.example.noteworkshop.vo.UserProfileVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;
    private final AuthService authService;

    public AuthController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<UserProfileVO> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(userService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

}
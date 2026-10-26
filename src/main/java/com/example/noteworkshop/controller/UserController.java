package com.example.noteworkshop.controller;

import com.example.noteworkshop.common.ApiResponse;
import com.example.noteworkshop.dto.UpdateProfileRequest;
import com.example.noteworkshop.service.UserService;
import com.example.noteworkshop.vo.UserProfileVO;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileVO> getCurrentProfile() {
        return ApiResponse.success(userService.getCurrentProfile());
    }

    @PutMapping("/me")
    public ApiResponse<UserProfileVO> updateCurrentProfile(
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return ApiResponse.success(userService.updateCurrentProfile(request));
    }
    @PostMapping(
            value = "/me/avatar",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ApiResponse<UserProfileVO> updateCurrentAvatar(
            @RequestPart("file") MultipartFile file
    ) {
        return ApiResponse.success(userService.updateCurrentAvatar(file));
    }
}
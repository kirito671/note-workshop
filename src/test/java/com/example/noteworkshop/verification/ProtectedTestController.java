package com.example.noteworkshop.verification;

import com.example.noteworkshop.common.ApiResponse;
import com.example.noteworkshop.security.CurrentUser;
import com.example.noteworkshop.security.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
public class ProtectedTestController {

    @GetMapping("/protected")
    public ApiResponse<CurrentUser> protectedEndpoint() {
        return ApiResponse.success(UserContext.get());
    }
}
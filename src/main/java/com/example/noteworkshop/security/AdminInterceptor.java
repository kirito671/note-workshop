package com.example.noteworkshop.security;

import com.example.noteworkshop.exception.BusinessException;
import com.example.noteworkshop.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        CurrentUser currentUser = UserContext.get();
        if (!Integer.valueOf(1).equals(currentUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要管理员权限");
        }
        return true;
    }
}
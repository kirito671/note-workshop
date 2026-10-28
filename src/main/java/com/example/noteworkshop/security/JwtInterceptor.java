package com.example.noteworkshop.security;

import com.example.noteworkshop.entity.User;
import com.example.noteworkshop.exception.BusinessException;
import com.example.noteworkshop.exception.ErrorCode;
import com.example.noteworkshop.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class JwtInterceptor implements HandlerInterceptor {
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserMapper userMapper;

    public JwtInterceptor(JwtService jwtService, UserMapper userMapper) {
        this.jwtService = jwtService;
        this.userMapper = userMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UserContext.clear();
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录");
        }

        try {
            Claims claims = jwtService.parseClaims(token);
            String subject = claims.getSubject();
            if (subject == null) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录信息不完整");
            }
            User user = userMapper.selectById(Long.valueOf(subject));
            if (user == null) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号不存在，请重新登录");
            }
            if (!Integer.valueOf(1).equals(user.getStatus())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被禁用");
            }
            UserContext.set(new CurrentUser(user.getId(), user.getUsername(), user.getRole()));
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态无效或已过期");
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        UserContext.clear();
    }
}
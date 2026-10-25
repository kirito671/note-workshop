package com.example.noteworkshop.security;

import com.example.noteworkshop.exception.BusinessException;
import com.example.noteworkshop.exception.ErrorCode;
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

    public JwtInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
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
            String username = claims.get("username", String.class);
            Integer role = claims.get("role", Integer.class);

            if (subject == null || username == null || role == null) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录信息不完整");
            }

            UserContext.set(new CurrentUser(Long.valueOf(subject), username, role));
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态无效或已过期");
        }
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception
    ) {
        UserContext.clear();
    }
}
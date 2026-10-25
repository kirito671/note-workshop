package com.example.noteworkshop.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.noteworkshop.dto.LoginRequest;
import com.example.noteworkshop.entity.User;
import com.example.noteworkshop.exception.BusinessException;
import com.example.noteworkshop.exception.ErrorCode;
import com.example.noteworkshop.mapper.UserMapper;
import com.example.noteworkshop.security.JwtService;
import com.example.noteworkshop.vo.LoginResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String username = request.username().trim();
        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, username)
        );

        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }

        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }

        String token = jwtService.generateToken(user);
        log.info("User login succeeded, id={}, username={}", user.getId(), user.getUsername());

        return new LoginResponse(
                token,
                "Bearer",
                jwtService.getExpirationMinutes()
        );
    }
}
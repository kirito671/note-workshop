package com.example.noteworkshop.verification;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.noteworkshop.dto.RegisterRequest;
import com.example.noteworkshop.entity.User;
import com.example.noteworkshop.mapper.UserMapper;
import com.example.noteworkshop.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthLoginIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Test
    void shouldLoginAndReturnJwt() throws Exception {
        String username = createUser();
        String body = """
                {
                  "username": "%s",
                  "password": "123456"
                }
                """.formatted(username);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresInMinutes").value(120));
    }

    @Test
    void shouldRejectWrongPassword() throws Exception {
        String username = createUser();
        String body = """
                {
                  "username": "%s",
                  "password": "wrong-password"
                }
                """.formatted(username);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void shouldRejectDisabledUser() throws Exception {
        String username = createUser();
        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, username)
        );
        user.setStatus(0);
        userMapper.updateById(user);

        String body = """
                {
                  "username": "%s",
                  "password": "123456"
                }
                """.formatted(username);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("账号已被禁用"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    private String createUser() {
        String username = "login_" + System.nanoTime();
        userService.register(new RegisterRequest(username, "123456", "登录测试用户"));
        return username;
    }
}
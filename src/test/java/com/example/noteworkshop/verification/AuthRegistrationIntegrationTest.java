package com.example.noteworkshop.verification;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.noteworkshop.entity.User;
import com.example.noteworkshop.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthRegistrationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldRegisterUserAndStoreBcryptPassword() throws Exception {
        String username = uniqueUsername();
        String password = "123456";
        String body = """
                {
                  "username": "%s",
                  "password": "%s",
                  "nickname": "测试用户"
                }
                """.formatted(username, password);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.password").doesNotExist());

        User saved = userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, username)
        );

        assertThat(saved).isNotNull();
        assertThat(saved.getPassword()).isNotEqualTo(password);
        assertThat(passwordEncoder.matches(password, saved.getPassword())).isTrue();
    }

    @Test
    void shouldRejectDuplicateUsername() throws Exception {
        String username = uniqueUsername();
        String body = """
                {
                  "username": "%s",
                  "password": "123456",
                  "nickname": "测试用户"
                }
                """.formatted(username);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("用户名已存在"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void shouldRejectInvalidRegistrationRequest() throws Exception {
        String body = """
                {
                  "username": "a",
                  "password": "1",
                  "nickname": ""
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    private String uniqueUsername() {
        return "test_" + System.nanoTime();
    }
}
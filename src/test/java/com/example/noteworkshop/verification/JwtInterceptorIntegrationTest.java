package com.example.noteworkshop.verification;

import com.example.noteworkshop.dto.RegisterRequest;
import com.example.noteworkshop.security.UserContext;
import com.example.noteworkshop.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class JwtInterceptorIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Test
    void shouldRejectProtectedRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/test/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("请先登录"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void shouldAllowProtectedRequestWithValidToken() throws Exception {
        LoginSession session = registerAndLogin();

        mockMvc.perform(get("/api/test/protected")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.token()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value(session.username()))
                .andExpect(jsonPath("$.data.role").value(0));

        assertThat(UserContext.getOrNull()).isNull();
    }

    @Test
    void shouldRejectProtectedRequestWithInvalidToken() throws Exception {
        mockMvc.perform(get("/api/test/protected")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("登录状态无效或已过期"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    private LoginSession registerAndLogin() throws Exception {
        String username = "jwt_" + System.nanoTime();
        userService.register(new RegisterRequest(username, "123456", "JWT测试用户"));

        String body = """
                {
                  "username": "%s",
                  "password": "123456"
                }
                """.formatted(username);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        String token = json.path("data").path("token").asText();
        assertThat(token).isNotBlank();

        return new LoginSession(username, token);
    }

    private record LoginSession(String username, String token) {
    }
}
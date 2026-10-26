package com.example.noteworkshop.verification;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.noteworkshop.dto.RegisterRequest;
import com.example.noteworkshop.entity.User;
import com.example.noteworkshop.mapper.UserMapper;
import com.example.noteworkshop.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "app.upload.dir=target/test-uploads")
class AvatarUploadIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Test
    void shouldUploadPngAndServeItFromStaticUrl() throws Exception {
        LoginSession session = registerAndLogin();
        byte[] pngBytes = createPng();

        MockMultipartFile avatar = new MockMultipartFile(
                "file",
                "avatar.png",
                MediaType.IMAGE_PNG_VALUE,
                pngBytes
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/users/me/avatar")
                        .file(avatar)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.token()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.avatarUrl").isNotEmpty())
                .andReturn();

        JsonNode uploadJson = objectMapper.readTree(uploadResult.getResponse().getContentAsByteArray());
        String avatarUrl = uploadJson.path("data").path("avatarUrl").asText();
        assertThat(avatarUrl).startsWith("/uploads/avatars/");

        User savedUser = userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, session.username())
        );
        assertThat(savedUser).isNotNull();
        assertThat(savedUser.getAvatarUrl()).isEqualTo(avatarUrl);

        mockMvc.perform(get(avatarUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(pngBytes));
    }

    @Test
    void shouldRejectNonImageAvatar() throws Exception {
        LoginSession session = registerAndLogin();
        MockMultipartFile avatar = new MockMultipartFile(
                "file",
                "avatar.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "not an image".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/users/me/avatar")
                        .file(avatar)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.token()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("头像仅支持 jpg、jpeg、png 格式"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void shouldRejectAvatarUploadWithoutToken() throws Exception {
        MockMultipartFile avatar = new MockMultipartFile(
                "file",
                "avatar.png",
                MediaType.IMAGE_PNG_VALUE,
                createPng()
        );

        mockMvc.perform(multipart("/api/users/me/avatar").file(avatar))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("请先登录"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void shouldRejectOversizedAvatar() throws Exception {
        LoginSession session = registerAndLogin();
        byte[] oversizedContent = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile avatar = new MockMultipartFile(
                "file",
                "avatar.png",
                MediaType.IMAGE_PNG_VALUE,
                oversizedContent
        );

        mockMvc.perform(multipart("/api/users/me/avatar")
                        .file(avatar)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.token()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("头像文件不能超过 5MB"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
    private LoginSession registerAndLogin() throws Exception {
        String username = "avatar_" + System.nanoTime();
        userService.register(new RegisterRequest(username, "123456", "头像测试用户"));

        String body = """
                {
                  "username": "%s",
                  "password": "123456"
                }
                """.formatted(username);

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsByteArray());
        String token = loginJson.path("data").path("token").asText();
        assertThat(token).isNotBlank();

        return new LoginSession(username, token);
    }

    private byte[] createPng() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private record LoginSession(String username, String token) {
    }
}
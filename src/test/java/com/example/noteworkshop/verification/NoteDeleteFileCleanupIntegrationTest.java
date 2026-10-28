package com.example.noteworkshop.verification;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.noteworkshop.dto.RegisterRequest;
import com.example.noteworkshop.entity.NoteImage;
import com.example.noteworkshop.entity.NoteLike;
import com.example.noteworkshop.mapper.NoteImageMapper;
import com.example.noteworkshop.mapper.NoteLikeMapper;
import com.example.noteworkshop.mapper.NoteMapper;
import com.example.noteworkshop.mapper.UserMapper;
import com.example.noteworkshop.service.UserService;
import com.example.noteworkshop.vo.UserProfileVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.util.FileSystemUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.upload.dir=target/test-uploads")
class NoteDeleteFileCleanupIntegrationTest {
    private static final Path UPLOAD_ROOT = Path.of("target/test-uploads").toAbsolutePath().normalize();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private NoteImageMapper noteImageMapper;

    @Autowired
    private NoteLikeMapper noteLikeMapper;

    private final List<Long> noteIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void cleanUp() throws Exception {
        for (Long noteId : noteIds) {
            noteImageMapper.delete(Wrappers.<NoteImage>lambdaQuery().eq(NoteImage::getNoteId, noteId));
            noteLikeMapper.delete(Wrappers.<NoteLike>lambdaQuery().eq(NoteLike::getNoteId, noteId));
            noteMapper.deleteById(noteId);
        }
        for (Long userId : userIds) {
            userMapper.deleteById(userId);
        }
        FileSystemUtils.deleteRecursively(UPLOAD_ROOT.resolve("notes"));
        Files.createDirectories(UPLOAD_ROOT.resolve("notes"));
    }

    @Test
    void shouldDeletePhysicalImageAfterDatabaseCommit() throws Exception {
        LoginSession owner = registerAndLogin("file_cleanup_owner");
        long noteId = createNote(owner.token());
        UploadedImage image = uploadImage(owner.token(), noteId, "cleanup-one.png");
        insertLike(owner.userId(), noteId);
        Path imagePath = resolveUploadPath(image.imageUrl());

        assertThat(imagePath).isRegularFile();
        assertThat(noteImageMapper.selectCount(imageQuery(noteId))).isEqualTo(1L);

        mockMvc.perform(delete("/api/notes/{id}", noteId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertThat(noteMapper.selectById(noteId)).isNull();
        assertThat(noteImageMapper.selectCount(imageQuery(noteId))).isZero();
        assertThat(noteLikeMapper.selectCount(likeQuery(noteId))).isZero();
        assertThat(imagePath).doesNotExist();
    }

    @Test
    void shouldKeepImageWhenAnotherUserCannotDeleteNote() throws Exception {
        LoginSession owner = registerAndLogin("file_cleanup_other_owner");
        LoginSession other = registerAndLogin("file_cleanup_other_user");
        long noteId = createNote(owner.token());
        UploadedImage image = uploadImage(owner.token(), noteId, "cleanup-forbidden.png");
        insertLike(owner.userId(), noteId);
        Path imagePath = resolveUploadPath(image.imageUrl());

        mockMvc.perform(delete("/api/notes/{id}", noteId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + other.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        assertThat(noteMapper.selectById(noteId)).isNotNull();
        assertThat(noteImageMapper.selectCount(imageQuery(noteId))).isEqualTo(1L);
        assertThat(imagePath).isRegularFile();
    }

    @Test
    void shouldKeepImageWhenDeleteHasNoJwt() throws Exception {
        LoginSession owner = registerAndLogin("file_cleanup_no_token_owner");
        long noteId = createNote(owner.token());
        UploadedImage image = uploadImage(owner.token(), noteId, "cleanup-no-token.png");
        Path imagePath = resolveUploadPath(image.imageUrl());

        mockMvc.perform(delete("/api/notes/{id}", noteId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        assertThat(noteMapper.selectById(noteId)).isNotNull();
        assertThat(imagePath).isRegularFile();
    }

    private void insertLike(Long userId, long noteId) {
        NoteLike noteLike = new NoteLike();
        noteLike.setUserId(userId);
        noteLike.setNoteId(noteId);
        noteLikeMapper.insert(noteLike);
    }

    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<NoteImage> imageQuery(long noteId) {
        return Wrappers.<NoteImage>lambdaQuery().eq(NoteImage::getNoteId, noteId);
    }

    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<NoteLike> likeQuery(long noteId) {
        return Wrappers.<NoteLike>lambdaQuery().eq(NoteLike::getNoteId, noteId);
    }

    private UploadedImage uploadImage(String token, long noteId, String filename) throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "files",
                filename,
                MediaType.IMAGE_PNG_VALUE,
                createPng()
        );
        MockHttpServletRequestBuilder request = multipart("/api/notes/{id}/images", noteId)
                .file(image)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andReturn();

        JsonNode imageJson = responseJson(result).path("data").get(0);
        return new UploadedImage(imageJson.path("imageUrl").asText());
    }

    private LoginSession registerAndLogin(String prefix) throws Exception {
        String username = prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        UserProfileVO profile = userService.register(
                new RegisterRequest(username, "123456", "file cleanup test user")
        );
        userIds.add(profile.id());

        String body = objectMapper.writeValueAsString(Map.of(
                "username", username,
                "password", "123456"
        ));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        String token = responseJson(loginResult).path("data").path("token").asText();
        assertThat(token).isNotBlank();
        return new LoginSession(profile.id(), token);
    }

    private long createNote(String token) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "title", "file cleanup test " + UUID.randomUUID(),
                "bookName", "file cleanup test book",
                "content", "file cleanup test content",
                "visibility", 1
        ));

        MvcResult result = mockMvc.perform(post("/api/notes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        long noteId = responseJson(result).path("data").path("id").asLong();
        noteIds.add(noteId);
        return noteId;
    }

    private Path resolveUploadPath(String imageUrl) {
        String relativePath = imageUrl.substring("/uploads/".length());
        return UPLOAD_ROOT.resolve(relativePath).normalize();
    }

    private JsonNode responseJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private byte[] createPng() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return outputStream.toByteArray();
    }

    private record LoginSession(Long userId, String token) {
    }

    private record UploadedImage(String imageUrl) {
    }
}
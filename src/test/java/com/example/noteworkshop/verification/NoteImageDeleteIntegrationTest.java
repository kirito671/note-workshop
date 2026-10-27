package com.example.noteworkshop.verification;

import com.example.noteworkshop.dto.RegisterRequest;
import com.example.noteworkshop.entity.NoteImage;
import com.example.noteworkshop.mapper.NoteImageMapper;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileSystemUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "app.upload.dir=target/test-uploads")
class NoteImageDeleteIntegrationTest {
    private static final Path UPLOAD_ROOT = Path.of("target/test-uploads").toAbsolutePath().normalize();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private NoteImageMapper noteImageMapper;

    @AfterEach
    void cleanUpUploadedNoteFiles() throws Exception {
        FileSystemUtils.deleteRecursively(UPLOAD_ROOT.resolve("notes"));
        Files.createDirectories(UPLOAD_ROOT.resolve("notes"));
    }

    @Test
    void shouldDeleteOwnImageRecordAndPhysicalFile() throws Exception {
        LoginSession owner = registerAndLogin("delete_owner");
        long noteId = createNote(owner.token());
        UploadedImage image = uploadImage(owner.token(), noteId, "delete-success.png");
        Path imagePath = resolveUploadPath(image.imageUrl());

        assertThat(noteImageMapper.selectById(image.id())).isNotNull();
        assertThat(imagePath).isRegularFile();

        mockMvc.perform(delete("/api/notes/{id}/images/{imageId}", noteId, image.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(nullValue()));

        assertThat(noteImageMapper.selectById(image.id())).isNull();
        assertThat(imagePath).doesNotExist();

        mockMvc.perform(get("/api/notes/{id}", noteId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.images.length()").value(0));
    }

    @Test
    void shouldRejectDeletingAnotherUsersImage() throws Exception {
        LoginSession owner = registerAndLogin("delete_other_owner");
        LoginSession other = registerAndLogin("delete_other_user");
        long noteId = createNote(owner.token());
        UploadedImage image = uploadImage(owner.token(), noteId, "delete-forbidden.png");
        Path imagePath = resolveUploadPath(image.imageUrl());

        mockMvc.perform(delete("/api/notes/{id}/images/{imageId}", noteId, image.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + other.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.data").value(nullValue()));

        assertThat(noteImageMapper.selectById(image.id())).isNotNull();
        assertThat(imagePath).isRegularFile();
    }

    @Test
    void shouldRejectImageThatDoesNotBelongToPathNote() throws Exception {
        LoginSession owner = registerAndLogin("delete_mismatch_owner");
        long firstNoteId = createNote(owner.token());
        long secondNoteId = createNote(owner.token());
        UploadedImage image = uploadImage(owner.token(), firstNoteId, "delete-mismatch.png");
        Path imagePath = resolveUploadPath(image.imageUrl());

        mockMvc.perform(delete("/api/notes/{id}/images/{imageId}", secondNoteId, image.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.data").value(nullValue()));

        assertThat(noteImageMapper.selectById(image.id())).isNotNull();
        assertThat(imagePath).isRegularFile();
    }

    @Test
    void shouldRejectDeletingMissingImage() throws Exception {
        LoginSession owner = registerAndLogin("delete_missing_owner");
        long noteId = createNote(owner.token());

        mockMvc.perform(delete("/api/notes/{id}/images/{imageId}", noteId, Long.MAX_VALUE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void shouldRejectDeleteWithoutJwt() throws Exception {
        LoginSession owner = registerAndLogin("delete_no_token_owner");
        long noteId = createNote(owner.token());
        UploadedImage image = uploadImage(owner.token(), noteId, "delete-no-token.png");
        Path imagePath = resolveUploadPath(image.imageUrl());

        mockMvc.perform(delete("/api/notes/{id}/images/{imageId}", noteId, image.id()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.data").value(nullValue()));

        assertThat(noteImageMapper.selectById(image.id())).isNotNull();
        assertThat(imagePath).isRegularFile();
    }

    private LoginSession registerAndLogin(String prefix) throws Exception {
        String username = prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        UserProfileVO profile = userService.register(
                new RegisterRequest(username, "123456", "image delete test user")
        );

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
                "title", "image delete test " + UUID.randomUUID(),
                "bookName", "image delete test book",
                "content", "image delete test content",
                "visibility", 1
        ));

        MvcResult result = mockMvc.perform(post("/api/notes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        return responseJson(result).path("data").path("id").asLong();
    }

    private UploadedImage uploadImage(String token, long noteId, String filename) throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "files",
                filename,
                MediaType.IMAGE_PNG_VALUE,
                createPng()
        );

        MvcResult result = mockMvc.perform(multipart("/api/notes/{id}/images", noteId)
                        .file(image)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andReturn();

        JsonNode imageJson = responseJson(result).path("data").get(0);
        return new UploadedImage(
                imageJson.path("id").asLong(),
                imageJson.path("imageUrl").asText()
        );
    }

    private JsonNode responseJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private Path resolveUploadPath(String imageUrl) {
        String relativePath = imageUrl.substring("/uploads/".length());
        return UPLOAD_ROOT.resolve(relativePath).normalize();
    }

    private byte[] createPng() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return outputStream.toByteArray();
    }

    private record LoginSession(Long userId, String token) {
    }

    private record UploadedImage(Long id, String imageUrl) {
    }
}
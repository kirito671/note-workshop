package com.example.noteworkshop.verification;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.noteworkshop.dto.RegisterRequest;
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
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "app.upload.dir=target/test-uploads")
class NoteImageUploadIntegrationTest {
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
    void shouldUploadSixImagesAndReturnAllUrlsInDetail() throws Exception {
        LoginSession owner = registerAndLogin("upload_six_owner");
        long noteId = createNote(owner.token());

        List<UploadedImage> uploaded = uploadImages(owner.token(), noteId, sixPngs());

        assertThat(uploaded).hasSize(6);
        assertThat(noteImageMapper.selectCount(
                Wrappers.lambdaQuery(com.example.noteworkshop.entity.NoteImage.class)
                        .eq(com.example.noteworkshop.entity.NoteImage::getNoteId, noteId)
        )).isEqualTo(6L);
        assertThat(uploaded).allSatisfy(image -> assertThat(resolveUploadPath(image.imageUrl())).isRegularFile());

        MvcResult detailResult = mockMvc.perform(get("/api/notes/{id}", noteId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode detailImages = responseJson(detailResult).path("data").path("images");
        assertThat(detailImages.size()).isEqualTo(6L);
        for (int index = 0; index < uploaded.size(); index++) {
            assertThat(detailImages.get(index).asText()).isEqualTo(uploaded.get(index).imageUrl());
        }
    }

    @Test
    void shouldRejectUploadWithoutFiles() throws Exception {
        LoginSession owner = registerAndLogin("upload_empty_owner");
        long noteId = createNote(owner.token());

        mockMvc.perform(multipart("/api/notes/{id}/images", noteId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.data").value(nullValue()));

        assertThat(noteImageMapper.selectCount(
                Wrappers.lambdaQuery(com.example.noteworkshop.entity.NoteImage.class)
                        .eq(com.example.noteworkshop.entity.NoteImage::getNoteId, noteId)
        )).isZero();
    }

    @Test
    void shouldRejectMoreThanSixImages() throws Exception {
        LoginSession owner = registerAndLogin("upload_too_many_owner");
        long noteId = createNote(owner.token());

        mockMvc.perform(uploadRequest(noteId, pngs(7))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.data").value(nullValue()));

        assertThat(noteImageMapper.selectCount(
                Wrappers.lambdaQuery(com.example.noteworkshop.entity.NoteImage.class)
                        .eq(com.example.noteworkshop.entity.NoteImage::getNoteId, noteId)
        )).isZero();
    }

    @Test
    void shouldRemoveAlreadySavedFilesWhenOneFileInBatchIsInvalid() throws Exception {
        LoginSession owner = registerAndLogin("upload_rollback_owner");
        long noteId = createNote(owner.token());
        List<MockMultipartFile> files = new ArrayList<>();
        files.add(new MockMultipartFile(
                "files",
                "valid.png",
                MediaType.IMAGE_PNG_VALUE,
                createPng()
        ));
        files.add(new MockMultipartFile(
                "files",
                "fake.png",
                MediaType.IMAGE_PNG_VALUE,
                "not-an-image".getBytes()
        ));

        mockMvc.perform(uploadRequest(noteId, files)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.data").value(nullValue()));

        assertThat(noteImageMapper.selectCount(
                Wrappers.lambdaQuery(com.example.noteworkshop.entity.NoteImage.class)
                        .eq(com.example.noteworkshop.entity.NoteImage::getNoteId, noteId)
        )).isZero();

        Path noteDirectory = UPLOAD_ROOT.resolve("notes").resolve(String.valueOf(noteId));
        try (Stream<Path> filesOnDisk = Files.list(noteDirectory)) {
            assertThat(filesOnDisk.findAny()).isEmpty();
        }
    }

    @Test
    void shouldRejectUploadByAnotherUser() throws Exception {
        LoginSession owner = registerAndLogin("upload_other_owner");
        LoginSession other = registerAndLogin("upload_other_user");
        long noteId = createNote(owner.token());

        mockMvc.perform(uploadRequest(noteId, pngs(1))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + other.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.data").value(nullValue()));

        assertThat(noteImageMapper.selectCount(
                Wrappers.lambdaQuery(com.example.noteworkshop.entity.NoteImage.class)
                        .eq(com.example.noteworkshop.entity.NoteImage::getNoteId, noteId)
        )).isZero();
    }

    @Test
    void shouldReturnNotFoundWhenNoteDoesNotExist() throws Exception {
        LoginSession owner = registerAndLogin("upload_missing_note_owner");

        mockMvc.perform(uploadRequest(Long.MAX_VALUE, pngs(1))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void shouldRejectUploadWithoutJwt() throws Exception {
        LoginSession owner = registerAndLogin("upload_no_token_owner");
        long noteId = createNote(owner.token());

        mockMvc.perform(uploadRequest(noteId, pngs(1)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.data").value(nullValue()));

        assertThat(noteImageMapper.selectCount(
                Wrappers.lambdaQuery(com.example.noteworkshop.entity.NoteImage.class)
                        .eq(com.example.noteworkshop.entity.NoteImage::getNoteId, noteId)
        )).isZero();
    }

    private MockMultipartHttpServletRequestBuilder uploadRequest(
            long noteId,
            List<MockMultipartFile> files
    ) {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/notes/{id}/images", noteId);
        for (MockMultipartFile file : files) {
            request.file(file);
        }
        return request;
    }

    private LoginSession registerAndLogin(String prefix) throws Exception {
        String username = prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        UserProfileVO profile = userService.register(
                new RegisterRequest(username, "123456", "image upload test user")
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
                "title", "image upload test " + UUID.randomUUID(),
                "bookName", "image upload test book",
                "content", "image upload test content",
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

    private List<UploadedImage> uploadImages(String token, long noteId, List<MockMultipartFile> files) throws Exception {
        MvcResult result = mockMvc.perform(uploadRequest(noteId, files)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(files.size()))
                .andReturn();

        List<UploadedImage> uploaded = new ArrayList<>();
        responseJson(result).path("data").forEach(image -> uploaded.add(new UploadedImage(
                image.path("id").asLong(),
                image.path("imageUrl").asText()
        )));
        return uploaded;
    }

    private List<MockMultipartFile> sixPngs() throws Exception {
        return pngs(6);
    }

    private List<MockMultipartFile> pngs(int count) throws Exception {
        List<MockMultipartFile> files = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            files.add(new MockMultipartFile(
                    "files",
                    "upload-" + index + ".png",
                    MediaType.IMAGE_PNG_VALUE,
                    createPng()
            ));
        }
        return files;
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
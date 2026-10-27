package com.example.noteworkshop.verification;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "app.upload.dir=target/test-uploads")
class NoteImageOrderIntegrationTest {
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
    void shouldReorderAllImagesAndReturnNewOrder() throws Exception {
        LoginSession owner = registerAndLogin("order_owner");
        long noteId = createNote(owner.token());
        List<UploadedImage> images = uploadImages(owner.token(), noteId, 3);

        List<Long> requestedOrder = List.of(
                images.get(2).id(),
                images.get(0).id(),
                images.get(1).id()
        );

        MvcResult result = mockMvc.perform(reorderRequest(owner.token(), noteId, requestedOrder))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        assertThat(responseIds(responseJson(result))).containsExactlyElementsOf(requestedOrder);
        assertThat(noteImageMapper.selectById(requestedOrder.get(0)).getSortOrder()).isZero();
        assertThat(noteImageMapper.selectById(requestedOrder.get(1)).getSortOrder()).isEqualTo(1);
        assertThat(noteImageMapper.selectById(requestedOrder.get(2)).getSortOrder()).isEqualTo(2);

        MvcResult detailResult = mockMvc.perform(get("/api/notes/{id}", noteId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode detailImages = responseJson(detailResult).path("data").path("images");
        assertThat(detailImages.size()).isEqualTo(3);
        assertThat(detailImages.get(0).asText()).isEqualTo(images.get(2).imageUrl());
        assertThat(detailImages.get(1).asText()).isEqualTo(images.get(0).imageUrl());
        assertThat(detailImages.get(2).asText()).isEqualTo(images.get(1).imageUrl());
    }

    @Test
    void shouldRejectDuplicateImageIds() throws Exception {
        LoginSession owner = registerAndLogin("order_duplicate");
        long noteId = createNote(owner.token());
        List<UploadedImage> images = uploadImages(owner.token(), noteId, 3);

        mockMvc.perform(reorderRequest(owner.token(), noteId, List.of(
                        images.get(0).id(),
                        images.get(0).id(),
                        images.get(1).id(),
                        images.get(2).id()
                )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void shouldRejectIncompleteImageOrder() throws Exception {
        LoginSession owner = registerAndLogin("order_missing");
        long noteId = createNote(owner.token());
        List<UploadedImage> images = uploadImages(owner.token(), noteId, 3);

        mockMvc.perform(reorderRequest(owner.token(), noteId, List.of(
                        images.get(0).id(),
                        images.get(1).id()
                )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void shouldRejectImageFromAnotherNote() throws Exception {
        LoginSession owner = registerAndLogin("order_foreign_owner");
        long firstNoteId = createNote(owner.token());
        long secondNoteId = createNote(owner.token());
        List<UploadedImage> firstNoteImages = uploadImages(owner.token(), firstNoteId, 3);
        UploadedImage foreignImage = uploadImages(owner.token(), secondNoteId, 1).get(0);

        mockMvc.perform(reorderRequest(owner.token(), firstNoteId, List.of(
                        firstNoteImages.get(0).id(),
                        firstNoteImages.get(1).id(),
                        foreignImage.id()
                )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void shouldRejectEmptyImageOrder() throws Exception {
        LoginSession owner = registerAndLogin("order_empty");
        long noteId = createNote(owner.token());
        uploadImages(owner.token(), noteId, 2);

        mockMvc.perform(reorderRequest(owner.token(), noteId, List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void shouldRejectReorderByAnotherUser() throws Exception {
        LoginSession owner = registerAndLogin("order_other_owner");
        LoginSession other = registerAndLogin("order_other_user");
        long noteId = createNote(owner.token());
        List<UploadedImage> images = uploadImages(owner.token(), noteId, 2);

        mockMvc.perform(reorderRequest(other.token(), noteId, List.of(
                        images.get(1).id(),
                        images.get(0).id()
                )))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void shouldReturnNotFoundForMissingNote() throws Exception {
        LoginSession owner = registerAndLogin("order_missing_note");

        mockMvc.perform(reorderRequest(owner.token(), Long.MAX_VALUE, List.of(1L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void shouldRejectReorderWithoutJwt() throws Exception {
        LoginSession owner = registerAndLogin("order_no_token_owner");
        long noteId = createNote(owner.token());
        List<UploadedImage> images = uploadImages(owner.token(), noteId, 2);
        String body = objectMapper.writeValueAsString(Map.of(
                "imageIds", List.of(images.get(1).id(), images.get(0).id())
        ));

        mockMvc.perform(put("/api/notes/{id}/images/order", noteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    private MockHttpServletRequestBuilder reorderRequest(
            String token,
            long noteId,
            List<Long> imageIds
    ) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("imageIds", imageIds));
        return put("/api/notes/{id}/images/order", noteId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private LoginSession registerAndLogin(String prefix) throws Exception {
        String username = prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        UserProfileVO profile = userService.register(
                new RegisterRequest(username, "123456", "image order test user")
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
                "title", "image order test " + UUID.randomUUID(),
                "bookName", "image order test book",
                "content", "image order test content",
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

    private List<UploadedImage> uploadImages(String token, long noteId, int count) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/notes/{id}/images", noteId);
        for (int index = 0; index < count; index++) {
            request.file(new MockMultipartFile(
                    "files",
                    "order-" + index + ".png",
                    MediaType.IMAGE_PNG_VALUE,
                    createPng()
            ));
        }

        MvcResult result = mockMvc.perform(request
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(count))
                .andReturn();

        List<UploadedImage> images = new ArrayList<>();
        responseJson(result).path("data").forEach(image -> images.add(new UploadedImage(
                image.path("id").asLong(),
                image.path("imageUrl").asText()
        )));
        return images;
    }

    private List<Long> responseIds(JsonNode response) {
        List<Long> ids = new ArrayList<>();
        response.path("data").forEach(image -> ids.add(image.path("id").asLong()));
        return ids;
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

    private record UploadedImage(Long id, String imageUrl) {
    }
}
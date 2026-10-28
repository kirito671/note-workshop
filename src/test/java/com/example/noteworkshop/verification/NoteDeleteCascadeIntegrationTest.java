package com.example.noteworkshop.verification;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.noteworkshop.dto.RegisterRequest;
import com.example.noteworkshop.entity.NoteImage;
import com.example.noteworkshop.entity.NoteLike;
import com.example.noteworkshop.mapper.NoteImageMapper;
import com.example.noteworkshop.mapper.NoteLikeMapper;
import com.example.noteworkshop.mapper.NoteMapper;
import com.example.noteworkshop.service.UserService;
import com.example.noteworkshop.vo.UserProfileVO;
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NoteDeleteCascadeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private NoteImageMapper noteImageMapper;

    @Autowired
    private NoteLikeMapper noteLikeMapper;

    @Test
    void shouldDeleteNoteImageAndLikeRecordsInOneTransaction() throws Exception {
        LoginSession owner = registerAndLogin("cascade_delete_owner");
        long noteId = createNote(owner.token());
        insertImage(noteId);
        insertLike(owner.userId(), noteId);

        assertThat(noteImageMapper.selectCount(imageQuery(noteId))).isEqualTo(1L);
        assertThat(noteLikeMapper.selectCount(likeQuery(noteId))).isEqualTo(1L);

        mockMvc.perform(delete("/api/notes/{id}", noteId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(nullValue()));

        assertThat(noteMapper.selectById(noteId)).isNull();
        assertThat(noteImageMapper.selectCount(imageQuery(noteId))).isZero();
        assertThat(noteLikeMapper.selectCount(likeQuery(noteId))).isZero();
    }

    @Test
    void shouldKeepRelationsWhenAnotherUserDeletesNote() throws Exception {
        LoginSession owner = registerAndLogin("cascade_other_owner");
        LoginSession other = registerAndLogin("cascade_other_user");
        long noteId = createNote(owner.token());
        insertImage(noteId);
        insertLike(owner.userId(), noteId);

        mockMvc.perform(delete("/api/notes/{id}", noteId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + other.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.data").value(nullValue()));

        assertThat(noteMapper.selectById(noteId)).isNotNull();
        assertThat(noteImageMapper.selectCount(imageQuery(noteId))).isEqualTo(1L);
        assertThat(noteLikeMapper.selectCount(likeQuery(noteId))).isEqualTo(1L);
    }

    @Test
    void shouldReturnNotFoundWhenDeletingMissingNote() throws Exception {
        LoginSession owner = registerAndLogin("cascade_missing_owner");

        mockMvc.perform(delete("/api/notes/{id}", Long.MAX_VALUE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void shouldRejectDeleteWithoutJwt() throws Exception {
        LoginSession owner = registerAndLogin("cascade_no_token_owner");
        long noteId = createNote(owner.token());
        insertImage(noteId);
        insertLike(owner.userId(), noteId);

        mockMvc.perform(delete("/api/notes/{id}", noteId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.data").value(nullValue()));

        assertThat(noteMapper.selectById(noteId)).isNotNull();
        assertThat(noteImageMapper.selectCount(imageQuery(noteId))).isEqualTo(1L);
        assertThat(noteLikeMapper.selectCount(likeQuery(noteId))).isEqualTo(1L);
    }

    private void insertImage(long noteId) {
        NoteImage noteImage = new NoteImage();
        noteImage.setNoteId(noteId);
        noteImage.setImageUrl("/uploads/notes/" + noteId + "/cascade.png");
        noteImage.setSortOrder(0);
        noteImageMapper.insert(noteImage);
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

    private LoginSession registerAndLogin(String prefix) throws Exception {
        String username = prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        UserProfileVO profile = userService.register(
                new RegisterRequest(username, "123456", "cascade test user")
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
                "title", "cascade test " + UUID.randomUUID(),
                "bookName", "cascade test book",
                "content", "cascade test content",
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

    private JsonNode responseJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private record LoginSession(Long userId, String token) {
    }
}
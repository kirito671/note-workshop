package com.example.noteworkshop.verification;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.noteworkshop.dto.RegisterRequest;
import com.example.noteworkshop.entity.Note;
import com.example.noteworkshop.entity.NoteLike;
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
class NoteLikeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private NoteLikeMapper noteLikeMapper;

    @Test
    void shouldLikePublicNoteAndReturnLikedStatus() throws Exception {
        LoginSession user = registerAndLogin("like_success");
        long noteId = createNote(user.token(), 1);

        mockMvc.perform(likeRequest(user.token(), noteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.liked").value(true))
                .andExpect(jsonPath("$.data.likeCount").value(1));

        assertThat(countLikes(noteId)).isEqualTo(1L);
    }

    @Test
    void shouldKeepLikeIdempotentWhenUserLikesTwice() throws Exception {
        LoginSession user = registerAndLogin("like_idempotent");
        long noteId = createNote(user.token(), 1);

        mockMvc.perform(likeRequest(user.token(), noteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(true))
                .andExpect(jsonPath("$.data.likeCount").value(1));

        mockMvc.perform(likeRequest(user.token(), noteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(true))
                .andExpect(jsonPath("$.data.likeCount").value(1));

        assertThat(countLikes(noteId)).isEqualTo(1L);
    }

    @Test
    void shouldUnlikeNoteAndReturnUnlikedStatus() throws Exception {
        LoginSession user = registerAndLogin("unlike_success");
        long noteId = createNote(user.token(), 1);
        mockMvc.perform(likeRequest(user.token(), noteId))
                .andExpect(status().isOk());

        mockMvc.perform(unlikeRequest(user.token(), noteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.liked").value(false))
                .andExpect(jsonPath("$.data.likeCount").value(0));

        assertThat(countLikes(noteId)).isZero();
    }

    @Test
    void shouldKeepUnlikeIdempotentWhenUserUnlikesTwice() throws Exception {
        LoginSession user = registerAndLogin("unlike_idempotent");
        long noteId = createNote(user.token(), 1);
        mockMvc.perform(likeRequest(user.token(), noteId))
                .andExpect(status().isOk());

        mockMvc.perform(unlikeRequest(user.token(), noteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(false));
        mockMvc.perform(unlikeRequest(user.token(), noteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.liked").value(false))
                .andExpect(jsonPath("$.data.likeCount").value(0));

        assertThat(countLikes(noteId)).isZero();
    }

    @Test
    void shouldCountLikesFromMultipleUsers() throws Exception {
        LoginSession firstUser = registerAndLogin("like_multi_first");
        LoginSession secondUser = registerAndLogin("like_multi_second");
        long noteId = createNote(firstUser.token(), 1);

        mockMvc.perform(likeRequest(firstUser.token(), noteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeCount").value(1));
        mockMvc.perform(likeRequest(secondUser.token(), noteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeCount").value(2));

        assertThat(countLikes(noteId)).isEqualTo(2L);
    }

    @Test
    void shouldRejectLikeForPrivateNote() throws Exception {
        LoginSession user = registerAndLogin("like_private");
        long noteId = createNote(user.token(), 0);

        mockMvc.perform(likeRequest(user.token(), noteId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.data").value(nullValue()));

        assertThat(countLikes(noteId)).isZero();
    }

    @Test
    void shouldRejectLikeForHiddenNote() throws Exception {
        LoginSession user = registerAndLogin("like_hidden");
        long noteId = createNote(user.token(), 1);
        Note note = noteMapper.selectById(noteId);
        note.setStatus(0);
        noteMapper.updateById(note);

        mockMvc.perform(likeRequest(user.token(), noteId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.data").value(nullValue()));

        assertThat(countLikes(noteId)).isZero();
    }

    @Test
    void shouldRejectLikeForMissingNote() throws Exception {
        LoginSession user = registerAndLogin("like_missing");

        mockMvc.perform(likeRequest(user.token(), Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void shouldRejectLikeAndUnlikeWithoutJwt() throws Exception {
        mockMvc.perform(post("/api/notes/{id}/like", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.data").value(nullValue()));

        mockMvc.perform(delete("/api/notes/{id}/like", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder likeRequest(
            String token,
            long noteId
    ) {
        return post("/api/notes/{id}/like", noteId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder unlikeRequest(
            String token,
            long noteId
    ) {
        return delete("/api/notes/{id}/like", noteId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private long countLikes(long noteId) {
        return noteLikeMapper.selectCount(
                Wrappers.<NoteLike>lambdaQuery().eq(NoteLike::getNoteId, noteId)
        );
    }

    private LoginSession registerAndLogin(String prefix) throws Exception {
        String username = prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        UserProfileVO profile = userService.register(
                new RegisterRequest(username, "123456", "like test user")
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

    private long createNote(String token, int visibility) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "title", "like test " + UUID.randomUUID(),
                "bookName", "like test book",
                "content", "like test content",
                "visibility", visibility
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
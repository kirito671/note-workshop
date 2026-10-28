package com.example.noteworkshop.verification;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NoteLikedPageIntegrationTest {

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
    void shouldReturnOnlyCurrentUsersLikedNotes() throws Exception {
        LoginSession firstUser = registerAndLogin("liked_page_first");
        LoginSession secondUser = registerAndLogin("liked_page_second");
        CreatedNote firstNote = createNote(firstUser.token(), 1);
        CreatedNote secondNote = createNote(secondUser.token(), 1);

        like(firstUser.token(), firstNote.id());
        like(secondUser.token(), secondNote.id());

        JsonNode firstUserPage = getLikedPage(firstUser.token());
        assertThat(responseIds(firstUserPage)).containsExactly(firstNote.id());
        assertThat(firstUserPage.path("total").asLong()).isEqualTo(1);
        assertThat(findRecord(firstUserPage, firstNote.id()).path("likedByMe").asBoolean()).isTrue();

        JsonNode secondUserPage = getLikedPage(secondUser.token());
        assertThat(responseIds(secondUserPage)).containsExactly(secondNote.id());
        assertThat(secondUserPage.path("total").asLong()).isEqualTo(1);
    }

    @Test
    void shouldOrderByMostRecentLikeFirst() throws Exception {
        LoginSession user = registerAndLogin("liked_page_order");
        CreatedNote firstNote = createNote(user.token(), 1);
        CreatedNote secondNote = createNote(user.token(), 1);

        like(user.token(), firstNote.id());
        like(user.token(), secondNote.id());

        JsonNode page = getLikedPage(user.token());
        assertThat(responseIds(page)).containsExactly(secondNote.id(), firstNote.id());
    }

    @Test
    void shouldPaginateLikedNotes() throws Exception {
        LoginSession user = registerAndLogin("liked_page_paging");
        CreatedNote firstNote = createNote(user.token(), 1);
        CreatedNote secondNote = createNote(user.token(), 1);
        CreatedNote thirdNote = createNote(user.token(), 1);

        like(user.token(), firstNote.id());
        like(user.token(), secondNote.id());
        like(user.token(), thirdNote.id());

        MvcResult firstPageResult = mockMvc.perform(get("/api/notes/liked")
                        .param("page", "1")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode firstPage = responseJson(firstPageResult).path("data");
        assertThat(firstPage.path("total").asLong()).isEqualTo(3);
        assertThat(firstPage.path("page").asLong()).isEqualTo(1);
        assertThat(firstPage.path("size").asLong()).isEqualTo(2);
        assertThat(firstPage.path("pages").asLong()).isEqualTo(2);
        assertThat(firstPage.path("records").size()).isEqualTo(2);

        MvcResult secondPageResult = mockMvc.perform(get("/api/notes/liked")
                        .param("page", "2")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode secondPage = responseJson(secondPageResult).path("data");
        assertThat(secondPage.path("total").asLong()).isEqualTo(3);
        assertThat(secondPage.path("pages").asLong()).isEqualTo(2);
        assertThat(secondPage.path("records").size()).isEqualTo(1);

        List<Long> allIds = new ArrayList<>();
        allIds.addAll(responseIds(firstPage));
        allIds.addAll(responseIds(secondPage));
        assertThat(allIds).containsExactlyInAnyOrder(firstNote.id(), secondNote.id(), thirdNote.id());
    }

    @Test
    void shouldExcludePrivateAndHiddenNotesFromLikedPage() throws Exception {
        LoginSession user = registerAndLogin("liked_page_visibility");
        CreatedNote publicNote = createNote(user.token(), 1);
        CreatedNote privateNote = createNote(user.token(), 0);
        CreatedNote hiddenNote = createNote(user.token(), 1);

        like(user.token(), publicNote.id());
        insertLike(user.userId(), privateNote.id());
        insertLike(user.userId(), hiddenNote.id());

        Note hidden = noteMapper.selectById(hiddenNote.id());
        hidden.setStatus(0);
        noteMapper.updateById(hidden);

        JsonNode page = getLikedPage(user.token());
        assertThat(responseIds(page)).containsExactly(publicNote.id());
        assertThat(page.path("total").asLong()).isEqualTo(1);
    }

    @Test
    void shouldRemoveUnlikeNoteFromLikedPage() throws Exception {
        LoginSession user = registerAndLogin("liked_page_unlike");
        CreatedNote note = createNote(user.token(), 1);
        like(user.token(), note.id());
        assertThat(getLikedPage(user.token()).path("total").asLong()).isEqualTo(1);

        mockMvc.perform(delete("/api/notes/{id}/like", note.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        JsonNode page = getLikedPage(user.token());
        assertThat(page.path("total").asLong()).isZero();
        assertThat(page.path("records").size()).isZero();
    }

    @Test
    void shouldReturnEmptyPageWhenUserHasNoLikes() throws Exception {
        LoginSession user = registerAndLogin("liked_page_empty");

        JsonNode page = getLikedPage(user.token());
        assertThat(page.path("total").asLong()).isZero();
        assertThat(page.path("pages").asLong()).isZero();
        assertThat(page.path("records").size()).isZero();
    }

    @Test
    void shouldRejectInvalidPaginationAndMissingJwt() throws Exception {
        LoginSession user = registerAndLogin("liked_page_invalid");

        mockMvc.perform(get("/api/notes/liked")
                        .param("page", "0")
                        .param("size", "10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.data").value(nullValue()));

        mockMvc.perform(get("/api/notes/liked")
                        .param("page", "1")
                        .param("size", "51")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.data").value(nullValue()));

        mockMvc.perform(get("/api/notes/liked"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    private void like(String token, long noteId) throws Exception {
        mockMvc.perform(post("/api/notes/{id}/like", noteId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private void insertLike(Long userId, long noteId) {
        NoteLike noteLike = new NoteLike();
        noteLike.setUserId(userId);
        noteLike.setNoteId(noteId);
        noteLikeMapper.insert(noteLike);
    }

    private JsonNode getLikedPage(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/notes/liked")
                        .param("page", "1")
                        .param("size", "50")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return responseJson(result).path("data");
    }

    private LoginSession registerAndLogin(String prefix) throws Exception {
        String username = prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        UserProfileVO profile = userService.register(
                new RegisterRequest(username, "123456", "liked page test user")
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

    private CreatedNote createNote(String token, int visibility) throws Exception {
        String title = "liked page " + UUID.randomUUID();
        String body = objectMapper.writeValueAsString(Map.of(
                "title", title,
                "bookName", "liked page book",
                "content", "liked page content",
                "visibility", visibility
        ));

        MvcResult result = mockMvc.perform(post("/api/notes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        long noteId = responseJson(result).path("data").path("id").asLong();
        if (visibility == 1) {
            Note note = noteMapper.selectById(noteId);
            note.setAuditStatus(1);
            noteMapper.updateById(note);
        }
        return new CreatedNote(noteId);
    }

    private JsonNode findRecord(JsonNode page, long noteId) {
        for (JsonNode record : page.path("records")) {
            if (record.path("id").asLong() == noteId) {
                return record;
            }
        }
        return null;
    }

    private List<Long> responseIds(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.path("records").forEach(record -> ids.add(record.path("id").asLong()));
        return ids;
    }

    private JsonNode responseJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private record LoginSession(Long userId, String token) {
    }

    private record CreatedNote(long id) {
    }
}
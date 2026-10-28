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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NoteRankingIntegrationTest {

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
    void shouldOrderByLikeCountAndIncludeZeroLikeNotes() throws Exception {
        LoginSession owner = registerAndLogin("ranking_order_owner");
        LoginSession firstLiker = registerAndLogin("ranking_order_first");
        LoginSession secondLiker = registerAndLogin("ranking_order_second");
        LoginSession thirdLiker = registerAndLogin("ranking_order_third");
        long mostLikedNote = createNote(owner.token(), 1);
        long mediumLikedNote = createNote(owner.token(), 1);
        long zeroLikeNote = createNote(owner.token(), 1);

        insertLike(firstLiker.userId(), mostLikedNote);
        insertLike(secondLiker.userId(), mostLikedNote);
        insertLike(thirdLiker.userId(), mostLikedNote);
        insertLike(firstLiker.userId(), mediumLikedNote);
        insertLike(secondLiker.userId(), mediumLikedNote);

        JsonNode records = getRanking(owner.token(), 1, 50).path("records");
        int mostLikedIndex = indexOf(records, mostLikedNote);
        int mediumLikedIndex = indexOf(records, mediumLikedNote);
        int zeroLikeIndex = indexOf(records, zeroLikeNote);

        assertThat(mostLikedIndex).isGreaterThanOrEqualTo(0);
        assertThat(mediumLikedIndex).isGreaterThanOrEqualTo(0);
        assertThat(zeroLikeIndex).isGreaterThanOrEqualTo(0);
        assertThat(mostLikedIndex).isLessThan(mediumLikedIndex);
        assertThat(mediumLikedIndex).isLessThan(zeroLikeIndex);
        assertThat(records.get(mostLikedIndex).path("likeCount").asLong()).isEqualTo(3);
        assertThat(records.get(mediumLikedIndex).path("likeCount").asLong()).isEqualTo(2);
        assertThat(records.get(zeroLikeIndex).path("likeCount").asLong()).isZero();
    }

    @Test
    void shouldExcludePrivateAndHiddenNotesFromRanking() throws Exception {
        LoginSession owner = registerAndLogin("ranking_filter_owner");
        LoginSession liker = registerAndLogin("ranking_filter_liker");
        long publicNote = createNote(owner.token(), 1);
        long privateNote = createNote(owner.token(), 0);
        long hiddenNote = createNote(owner.token(), 1);
        insertLike(liker.userId(), publicNote);
        insertLike(liker.userId(), privateNote);
        insertLike(liker.userId(), hiddenNote);

        Note hidden = noteMapper.selectById(hiddenNote);
        hidden.setStatus(0);
        noteMapper.updateById(hidden);

        JsonNode records = getRanking(owner.token(), 1, 50).path("records");
        assertThat(indexOf(records, publicNote)).isGreaterThanOrEqualTo(0);
        assertThat(indexOf(records, privateNote)).isEqualTo(-1);
        assertThat(indexOf(records, hiddenNote)).isEqualTo(-1);
    }

    @Test
    void shouldApplyPaginationConsistently() throws Exception {
        LoginSession owner = registerAndLogin("ranking_page_owner");
        LoginSession firstLiker = registerAndLogin("ranking_page_first");
        LoginSession secondLiker = registerAndLogin("ranking_page_second");
        long firstNote = createNote(owner.token(), 1);
        long secondNote = createNote(owner.token(), 1);
        long thirdNote = createNote(owner.token(), 1);
        insertLike(firstLiker.userId(), firstNote);
        insertLike(secondLiker.userId(), firstNote);
        insertLike(firstLiker.userId(), secondNote);
        insertLike(firstLiker.userId(), thirdNote);

        JsonNode allRecords = getRanking(owner.token(), 1, 50);
        long expectedTotal = allRecords.path("total").asLong();
        assertThat(expectedTotal).isGreaterThanOrEqualTo(3);

        MvcResult firstPageResult = mockMvc.perform(get("/api/notes/ranking")
                        .param("page", "1")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode firstPage = responseJson(firstPageResult).path("data");
        assertThat(firstPage.path("total").asLong()).isEqualTo(expectedTotal);
        assertThat(firstPage.path("page").asLong()).isEqualTo(1);
        assertThat(firstPage.path("size").asLong()).isEqualTo(2);
        assertThat(firstPage.path("pages").asLong()).isEqualTo((expectedTotal + 1) / 2);
        assertThat(firstPage.path("records").size()).isEqualTo(2);

        MvcResult secondPageResult = mockMvc.perform(get("/api/notes/ranking")
                        .param("page", "2")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode secondPage = responseJson(secondPageResult).path("data");
        assertThat(secondPage.path("total").asLong()).isEqualTo(expectedTotal);
        assertThat(secondPage.path("page").asLong()).isEqualTo(2);
        assertThat(secondPage.path("records").size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void shouldReturnLikedByMeAccordingToCurrentUser() throws Exception {
        LoginSession owner = registerAndLogin("ranking_state_owner");
        LoginSession viewer = registerAndLogin("ranking_state_viewer");
        long noteId = createNote(owner.token(), 1);

        mockMvc.perform(post("/api/notes/{id}/like", noteId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewer.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        JsonNode viewerRecords = getRanking(viewer.token(), 1, 50).path("records");
        JsonNode viewerRecord = findRecord(viewerRecords, noteId);
        assertThat(viewerRecord).isNotNull();
        assertThat(viewerRecord.path("likeCount").asLong()).isEqualTo(1);
        assertThat(viewerRecord.path("likedByMe").asBoolean()).isTrue();

        JsonNode ownerRecords = getRanking(owner.token(), 1, 50).path("records");
        JsonNode ownerRecord = findRecord(ownerRecords, noteId);
        assertThat(ownerRecord).isNotNull();
        assertThat(ownerRecord.path("likeCount").asLong()).isEqualTo(1);
        assertThat(ownerRecord.path("likedByMe").asBoolean()).isFalse();
    }

    @Test
    void shouldRejectInvalidPagination() throws Exception {
        LoginSession user = registerAndLogin("ranking_invalid");

        mockMvc.perform(get("/api/notes/ranking")
                        .param("page", "0")
                        .param("size", "10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.data").value(nullValue()));

        mockMvc.perform(get("/api/notes/ranking")
                        .param("page", "1")
                        .param("size", "51")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void shouldRejectRankingWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/notes/ranking"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    private JsonNode getRanking(String token, long page, long size) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/notes/ranking")
                        .param("page", String.valueOf(page))
                        .param("size", String.valueOf(size))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return responseJson(result).path("data");
    }

    private int indexOf(JsonNode records, long noteId) {
        for (int index = 0; index < records.size(); index++) {
            if (records.get(index).path("id").asLong() == noteId) {
                return index;
            }
        }
        return -1;
    }

    private JsonNode findRecord(JsonNode records, long noteId) {
        int index = indexOf(records, noteId);
        return index < 0 ? null : records.get(index);
    }

    private void insertLike(Long userId, long noteId) {
        NoteLike noteLike = new NoteLike();
        noteLike.setUserId(userId);
        noteLike.setNoteId(noteId);
        noteLikeMapper.insert(noteLike);
    }

    private LoginSession registerAndLogin(String prefix) throws Exception {
        String username = prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        UserProfileVO profile = userService.register(
                new RegisterRequest(username, "123456", "ranking test user")
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
                "title", "ranking test " + UUID.randomUUID(),
                "bookName", "ranking test book",
                "content", "ranking test content",
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
package com.example.noteworkshop.verification;

import com.example.noteworkshop.dto.RegisterRequest;
import com.example.noteworkshop.entity.Note;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
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
class NotePaginationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private NoteMapper noteMapper;

    @Test
    void shouldPageCurrentUsersNotesIncludingPrivateNotes() throws Exception {
        LoginSession owner = registerAndLogin("page_owner");
        LoginSession other = registerAndLogin("page_other");

        long privateNoteId = createNote(owner.token(), "Private " + owner.marker(), "Private Book", "private body", 0);
        long firstPublicNoteId = createNote(owner.token(), "Public A " + owner.marker(), "Book A", "public body", 1);
        long secondPublicNoteId = createNote(owner.token(), "Public B " + owner.marker(), "Book B", "public body", 1);
        long otherUserNoteId = createNote(other.token(), "Other " + other.marker(), "Other Book", "other body", 1);

        MvcResult firstPageResult = mockMvc.perform(get("/api/notes/my")
                        .param("page", "1")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode firstPage = responseJson(firstPageResult);
        assertThat(firstPage.path("data").path("total").asLong()).isEqualTo(3);
        assertThat(firstPage.path("data").path("page").asLong()).isEqualTo(1);
        assertThat(firstPage.path("data").path("size").asLong()).isEqualTo(2);
        assertThat(firstPage.path("data").path("pages").asLong()).isEqualTo(2);
        assertThat(firstPage.path("data").path("records").size()).isEqualTo(2);

        MvcResult secondPageResult = mockMvc.perform(get("/api/notes/my")
                        .param("page", "2")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode secondPage = responseJson(secondPageResult);
        assertThat(secondPage.path("data").path("total").asLong()).isEqualTo(3);
        assertThat(secondPage.path("data").path("page").asLong()).isEqualTo(2);
        assertThat(secondPage.path("data").path("size").asLong()).isEqualTo(2);
        assertThat(secondPage.path("data").path("pages").asLong()).isEqualTo(2);
        assertThat(secondPage.path("data").path("records").size()).isEqualTo(1);

        List<Long> allRecordIds = new ArrayList<>();
        allRecordIds.addAll(recordIds(firstPage));
        allRecordIds.addAll(recordIds(secondPage));

        assertThat(allRecordIds).containsExactlyInAnyOrder(
                privateNoteId,
                firstPublicNoteId,
                secondPublicNoteId
        );
        assertThat(allRecordIds).doesNotContain(otherUserNoteId);
    }

    @Test
    void shouldReturnOnlyPublicAndNormalNotesInPublicPage() throws Exception {
        LoginSession owner = registerAndLogin("public_owner");
        LoginSession viewer = registerAndLogin("public_viewer");
        String keyword = "visibility_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        long publicNoteId = createNote(
                owner.token(),
                "Public " + keyword,
                "Visible Book",
                "public body",
                1
        );
        createNote(
                owner.token(),
                "Private " + keyword,
                "Private Book",
                "private body",
                0
        );
        long hiddenNoteId = createNote(
                owner.token(),
                "Hidden " + keyword,
                "Hidden Book",
                "hidden body",
                1
        );

        Note hiddenNote = noteMapper.selectById(hiddenNoteId);
        hiddenNote.setStatus(0);
        noteMapper.updateById(hiddenNote);

        MvcResult result = mockMvc.perform(get("/api/notes/public")
                        .param("page", "1")
                        .param("size", "10")
                        .param("keyword", keyword)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewer.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode data = responseJson(result).path("data");
        assertThat(data.path("total").asLong()).isEqualTo(1);
        assertThat(data.path("page").asLong()).isEqualTo(1);
        assertThat(data.path("size").asLong()).isEqualTo(10);
        assertThat(data.path("pages").asLong()).isEqualTo(1);
        assertThat(data.path("records").size()).isEqualTo(1);

        JsonNode record = data.path("records").get(0);
        assertThat(record.path("id").asLong()).isEqualTo(publicNoteId);
        assertThat(record.path("userId").asLong()).isEqualTo(owner.userId());
        assertThat(record.path("visibility").asInt()).isEqualTo(1);
        assertThat(record.path("status").asInt()).isEqualTo(1);
        assertThat(record.has("content")).isFalse();
    }

    @Test
    void shouldMatchKeywordAgainstTitleOrBookName() throws Exception {
        LoginSession viewer = registerAndLogin("keyword_viewer");
        String keyword = "keyword_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        long titleMatchId = createNote(
                viewer.token(),
                "Title match " + keyword,
                "Unrelated Book One",
                "body",
                1
        );
        long bookNameMatchId = createNote(
                viewer.token(),
                "Unrelated Title",
                "Book match " + keyword,
                "body",
                1
        );
        long nonMatchingId = createNote(
                viewer.token(),
                "Unrelated Title Two",
                "Unrelated Book Two",
                "body",
                1
        );

        MvcResult result = mockMvc.perform(get("/api/notes/public")
                        .param("page", "1")
                        .param("size", "10")
                        .param("keyword", "  " + keyword + "  ")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewer.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode data = responseJson(result).path("data");
        assertThat(data.path("total").asLong()).isEqualTo(2);
        assertThat(recordIds(responseJson(result)))
                .containsExactlyInAnyOrder(titleMatchId, bookNameMatchId)
                .doesNotContain(nonMatchingId);
    }

    @Test
    void shouldRejectInvalidPaginationParameters() throws Exception {
        LoginSession session = registerAndLogin("invalid_page");

        expectValidationFailure(
                get("/api/notes/my")
                        .param("page", "0")
                        .param("size", "10"),
                session.token()
        );
        expectValidationFailure(
                get("/api/notes/public")
                        .param("page", "1")
                        .param("size", "51"),
                session.token()
        );
    }

    @Test
    void shouldRejectPaginationRequestsWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/notes/my"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.data").value(nullValue()));

        mockMvc.perform(get("/api/notes/public"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    private void expectValidationFailure(
            MockHttpServletRequestBuilder request,
            String token
    ) throws Exception {
        mockMvc.perform(request
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    private List<Long> recordIds(JsonNode response) {
        List<Long> ids = new ArrayList<>();
        response.path("data").path("records").forEach(record -> ids.add(record.path("id").asLong()));
        return ids;
    }

    private JsonNode responseJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private long createNote(
            String token,
            String title,
            String bookName,
            String content,
            int visibility
    ) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "title", title,
                "bookName", bookName,
                "content", content,
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
        return noteId;
    }

    private LoginSession registerAndLogin(String scenario) throws Exception {
        String marker = scenario + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        UserProfileVO profile = userService.register(
                new RegisterRequest(marker, "123456", "pagination test user")
        );

        String body = objectMapper.writeValueAsString(Map.of(
                "username", marker,
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

        return new LoginSession(profile.id(), token, marker);
    }

    private record LoginSession(Long userId, String token, String marker) {
    }
}
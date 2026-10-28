package com.example.noteworkshop.verification;

import com.example.noteworkshop.dto.RegisterRequest;
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NoteLikeDisplayIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Test
    void shouldReturnZeroLikeStateForUnlikedNoteDetail() throws Exception {
        LoginSession owner = registerAndLogin("display_zero");
        CreatedNote note = createNote(owner.token());

        mockMvc.perform(get("/api/notes/{id}", note.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.likeCount").value(0))
                .andExpect(jsonPath("$.data.likedByMe").value(false));
    }

    @Test
    void shouldReturnLikeCountAndLikedByMeInDetail() throws Exception {
        LoginSession owner = registerAndLogin("display_detail_owner");
        LoginSession liker = registerAndLogin("display_detail_liker");
        CreatedNote note = createNote(owner.token());

        like(liker.token(), note.id());

        MvcResult likerDetail = mockMvc.perform(get("/api/notes/{id}", note.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + liker.token()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode likerData = responseJson(likerDetail).path("data");
        assertThat(likerData.path("likeCount").asLong()).isEqualTo(1);
        assertThat(likerData.path("likedByMe").asBoolean()).isTrue();

        MvcResult ownerDetail = mockMvc.perform(get("/api/notes/{id}", note.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode ownerData = responseJson(ownerDetail).path("data");
        assertThat(ownerData.path("likeCount").asLong()).isEqualTo(1);
        assertThat(ownerData.path("likedByMe").asBoolean()).isFalse();
    }

    @Test
    void shouldReturnCorrectLikedByMeInPublicPage() throws Exception {
        LoginSession owner = registerAndLogin("display_public_owner");
        LoginSession liker = registerAndLogin("display_public_liker");
        CreatedNote note = createNote(owner.token());
        like(liker.token(), note.id());

        MvcResult likerView = mockMvc.perform(get("/api/notes/public")
                        .param("page", "1")
                        .param("size", "50")
                        .param("keyword", note.title())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + liker.token()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode likedRecord = findRecord(responseJson(likerView), note.id());
        assertThat(likedRecord).isNotNull();
        assertThat(likedRecord.path("likeCount").asLong()).isEqualTo(1);
        assertThat(likedRecord.path("likedByMe").asBoolean()).isTrue();

        MvcResult ownerView = mockMvc.perform(get("/api/notes/public")
                        .param("page", "1")
                        .param("size", "50")
                        .param("keyword", note.title())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode ownerRecord = findRecord(responseJson(ownerView), note.id());
        assertThat(ownerRecord).isNotNull();
        assertThat(ownerRecord.path("likeCount").asLong()).isEqualTo(1);
        assertThat(ownerRecord.path("likedByMe").asBoolean()).isFalse();
    }

    @Test
    void shouldReturnLikeStateInMyNotesPage() throws Exception {
        LoginSession owner = registerAndLogin("display_my_owner");
        CreatedNote note = createNote(owner.token());
        like(owner.token(), note.id());

        MvcResult myPage = mockMvc.perform(get("/api/notes/my")
                        .param("page", "1")
                        .param("size", "50")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode record = findRecord(responseJson(myPage), note.id());
        assertThat(record).isNotNull();
        assertThat(record.path("likeCount").asLong()).isEqualTo(1);
        assertThat(record.path("likedByMe").asBoolean()).isTrue();
    }

    @Test
    void shouldUpdateLikeStateAfterUnlike() throws Exception {
        LoginSession owner = registerAndLogin("display_unlike_owner");
        LoginSession liker = registerAndLogin("display_unlike_liker");
        CreatedNote note = createNote(owner.token());
        like(liker.token(), note.id());

        mockMvc.perform(get("/api/notes/{id}", note.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + liker.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeCount").value(1))
                .andExpect(jsonPath("$.data.likedByMe").value(true));

        unlike(liker.token(), note.id());

        mockMvc.perform(get("/api/notes/{id}", note.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + liker.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likeCount").value(0))
                .andExpect(jsonPath("$.data.likedByMe").value(false));
    }

    @Test
    void shouldCountLikesFromMultipleUsersInDetail() throws Exception {
        LoginSession owner = registerAndLogin("display_multi_owner");
        LoginSession firstLiker = registerAndLogin("display_multi_first");
        LoginSession secondLiker = registerAndLogin("display_multi_second");
        CreatedNote note = createNote(owner.token());

        like(firstLiker.token(), note.id());
        like(secondLiker.token(), note.id());

        MvcResult ownerDetail = mockMvc.perform(get("/api/notes/{id}", note.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode ownerData = responseJson(ownerDetail).path("data");
        assertThat(ownerData.path("likeCount").asLong()).isEqualTo(2);
        assertThat(ownerData.path("likedByMe").asBoolean()).isFalse();

        MvcResult firstLikerDetail = mockMvc.perform(get("/api/notes/{id}", note.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstLiker.token()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode firstLikerData = responseJson(firstLikerDetail).path("data");
        assertThat(firstLikerData.path("likeCount").asLong()).isEqualTo(2);
        assertThat(firstLikerData.path("likedByMe").asBoolean()).isTrue();
    }

    private void like(String token, long noteId) throws Exception {
        mockMvc.perform(post("/api/notes/{id}/like", noteId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private void unlike(String token, long noteId) throws Exception {
        mockMvc.perform(delete("/api/notes/{id}/like", noteId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private LoginSession registerAndLogin(String prefix) throws Exception {
        String username = prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        UserProfileVO profile = userService.register(
                new RegisterRequest(username, "123456", "like display test user")
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

    private CreatedNote createNote(String token) throws Exception {
        String title = "like display " + UUID.randomUUID();
        String body = objectMapper.writeValueAsString(Map.of(
                "title", title,
                "bookName", "like display book",
                "content", "like display content",
                "visibility", 1
        ));

        MvcResult result = mockMvc.perform(post("/api/notes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        return new CreatedNote(
                responseJson(result).path("data").path("id").asLong(),
                title
        );
    }

    private JsonNode findRecord(JsonNode response, long noteId) {
        for (JsonNode record : response.path("data").path("records")) {
            if (record.path("id").asLong() == noteId) {
                return record;
            }
        }
        return null;
    }

    private JsonNode responseJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private record LoginSession(Long userId, String token) {
    }

    private record CreatedNote(long id, String title) {
    }
}
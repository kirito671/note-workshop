package com.example.noteworkshop.verification;

import com.example.noteworkshop.dto.RegisterRequest;
import com.example.noteworkshop.entity.Note;
import com.example.noteworkshop.entity.User;
import com.example.noteworkshop.mapper.NoteMapper;
import com.example.noteworkshop.mapper.UserMapper;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NoteCrudIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private UserMapper userMapper;

    @Test
    void shouldCreateNoteWithCurrentUserAsAuthor() throws Exception {
        LoginSession session = registerAndLogin();

        CreatedNote created = createNote(session.token(), 1, "原始标题", "原始内容");

        mockMvc.perform(get("/api/notes/{id}", created.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.token()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value(session.userId()))
                .andExpect(jsonPath("$.data.status").value(1));

        Note saved = noteMapper.selectById(created.id());
        assertThat(saved).isNotNull();
        assertThat(saved.getUserId()).isEqualTo(session.userId());
        assertThat(saved.getStatus()).isEqualTo(1);
    }

    @Test
    void shouldAllowOwnerToViewPrivateNoteButRejectOtherUser() throws Exception {
        LoginSession owner = registerAndLogin();
        LoginSession other = registerAndLogin();
        CreatedNote note = createNote(owner.token(), 0, "私有标题", "私有正文");

        mockMvc.perform(get("/api/notes/{id}", note.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("私有正文"));

        mockMvc.perform(get("/api/notes/{id}", note.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + other.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权查看该私有笔记"));
    }

    @Test
    void shouldAllowOwnerToUpdateOwnNote() throws Exception {
        LoginSession owner = registerAndLogin();
        CreatedNote note = createNote(owner.token(), 1, "旧标题", "旧正文");
        String body = """
                {
                  "title": "新标题",
                  "bookName": "新图书",
                  "content": "新正文",
                  "visibility": 0
                }
                """;

        mockMvc.perform(put("/api/notes/{id}", note.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("新标题"))
                .andExpect(jsonPath("$.data.visibility").value(0))
                .andExpect(jsonPath("$.data.userId").value(owner.userId()));

        Note updated = noteMapper.selectById(note.id());
        assertThat(updated.getTitle()).isEqualTo("新标题");
        assertThat(updated.getContent()).isEqualTo("新正文");
        assertThat(updated.getVisibility()).isZero();
        assertThat(updated.getUserId()).isEqualTo(owner.userId());
        assertThat(updated.getStatus()).isEqualTo(1);
    }

    @Test
    void shouldRejectOtherUserUpdateAndDelete() throws Exception {
        LoginSession owner = registerAndLogin();
        LoginSession other = registerAndLogin();
        CreatedNote note = createNote(owner.token(), 1, "受保护标题", "受保护正文");
        String updateBody = """
                {
                  "title": "越权修改",
                  "bookName": "测试",
                  "content": "不应成功",
                  "visibility": 1
                }
                """;

        mockMvc.perform(put("/api/notes/{id}", note.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + other.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权操作该笔记"));

        mockMvc.perform(delete("/api/notes/{id}", note.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + other.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权操作该笔记"));

        Note unchanged = noteMapper.selectById(note.id());
        assertThat(unchanged).isNotNull();
        assertThat(unchanged.getTitle()).isEqualTo("受保护标题");
    }

    @Test
    void shouldAllowOwnerToDeleteOwnNote() throws Exception {
        LoginSession owner = registerAndLogin();
        CreatedNote note = createNote(owner.token(), 1, "待删除标题", "待删除正文");

        mockMvc.perform(delete("/api/notes/{id}", note.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(nullValue()));

        assertThat(noteMapper.selectById(note.id())).isNull();

        mockMvc.perform(get("/api/notes/{id}", note.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void shouldAllowAdminToManageOtherUsersNote() throws Exception {
        LoginSession owner = registerAndLogin();
        LoginSession admin = registerAdminAndLogin();
        CreatedNote note = createNote(owner.token(), 0, "管理员测试标题", "管理员测试正文");

        mockMvc.perform(get("/api/notes/{id}", note.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("管理员测试正文"));

        String updateBody = """
                {
                  "title": "管理员修改后的标题",
                  "bookName": "管理员测试图书",
                  "content": "管理员修改后的正文",
                  "visibility": 0
                }
                """;

        mockMvc.perform(put("/api/notes/{id}", note.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("管理员修改后的标题"))
                .andExpect(jsonPath("$.data.userId").value(owner.userId()));

        Note updated = noteMapper.selectById(note.id());
        assertThat(updated.getUserId()).isEqualTo(owner.userId());
        assertThat(updated.getTitle()).isEqualTo("管理员修改后的标题");

        mockMvc.perform(delete("/api/notes/{id}", note.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertThat(noteMapper.selectById(note.id())).isNull();
    }
    private LoginSession registerAndLogin() throws Exception {
        String username = "note_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        UserProfileVO profile = userService.register(
                new RegisterRequest(username, "123456", "笔记测试用户")
        );

        String body = """
                {
                  "username": "%s",
                  "password": "123456"
                }
                """.formatted(username);

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsByteArray());
        String token = loginJson.path("data").path("token").asText();
        assertThat(token).isNotBlank();

        return new LoginSession(profile.id(), token);
    }

    private LoginSession registerAdminAndLogin() throws Exception {
        String username = "admin_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        UserProfileVO profile = userService.register(
                new RegisterRequest(username, "123456", "管理员测试用户")
        );

        User user = userMapper.selectById(profile.id());
        user.setRole(1);
        userMapper.updateById(user);

        String body = """
                {
                  "username": "%s",
                  "password": "123456"
                }
                """.formatted(username);

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsByteArray());
        String token = loginJson.path("data").path("token").asText();
        assertThat(token).isNotBlank();

        return new LoginSession(profile.id(), token);
    }
    private CreatedNote createNote(String token, int visibility, String title, String content) throws Exception {
        String body = """
                {
                  "title": "%s",
                  "bookName": "测试图书",
                  "content": "%s",
                  "visibility": %d
                }
                """.formatted(title, content, visibility);

        MvcResult result = mockMvc.perform(post("/api/notes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return new CreatedNote(json.path("data").path("id").asLong());
    }

    private record LoginSession(Long userId, String token) {
    }

    private record CreatedNote(Long id) {
    }
}
package com.example.noteworkshop.verification;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.noteworkshop.dto.RegisterRequest;
import com.example.noteworkshop.entity.Note;
import com.example.noteworkshop.entity.NoteComment;
import com.example.noteworkshop.entity.User;
import com.example.noteworkshop.mapper.NoteCommentMapper;
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class P1FeatureIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserService userService;
    @Autowired private UserMapper userMapper;
    @Autowired private NoteMapper noteMapper;
    @Autowired private NoteCommentMapper noteCommentMapper;

    @Test
    void shouldFollowAuthorAndExposeAuthorStatsAndNotes() throws Exception {
        Session admin = registerAndLogin("p1_admin_follow", true);
        Session author = registerAndLogin("p1_author", false);
        Session fan = registerAndLogin("p1_fan", false);
        long noteId = createPublicNote(author.token(), "关注作者测试");
        approve(noteId, admin.token());

        mockMvc.perform(post("/api/users/{id}/follow", author.userId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(fan.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.following").value(true))
                .andExpect(jsonPath("$.data.followerCount").value(1));

        mockMvc.perform(post("/api/users/{id}/follow", author.userId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(fan.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.followerCount").value(1));

        mockMvc.perform(get("/api/users/{id}/stats", author.userId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(fan.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.followerCount").value(1))
                .andExpect(jsonPath("$.data.publicNoteCount").value(1));

        mockMvc.perform(get("/api/users/{id}/notes", author.userId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(fan.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(noteId));

        mockMvc.perform(get("/api/users/hot-authors")
                        .header(HttpHeaders.AUTHORIZATION, bearer(fan.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].userId").value(author.userId()));

        mockMvc.perform(delete("/api/users/{id}/follow", author.userId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(fan.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.following").value(false))
                .andExpect(jsonPath("$.data.followerCount").value(0));
    }

    @Test
    void shouldBuildNestedCommentsAndCascadeLogicalDelete() throws Exception {
        Session admin = registerAndLogin("p1_admin_comment", true);
        Session owner = registerAndLogin("p1_comment_owner", false);
        Session first = registerAndLogin("p1_comment_first", false);
        Session second = registerAndLogin("p1_comment_second", false);
        long noteId = createPublicNote(owner.token(), "评论测试");
        approve(noteId, admin.token());

        long rootId = createComment(noteId, first.token(), "一级评论", null);
        long replyId = createComment(noteId, second.token(), "二级回复", rootId);
        long nestedId = createComment(noteId, first.token(), "三级回复", replyId);

        mockMvc.perform(get("/api/notes/{id}/comments", noteId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(first.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(rootId))
                .andExpect(jsonPath("$.data[0].replies[0].id").value(replyId))
                .andExpect(jsonPath("$.data[0].replies[0].replies[0].id").value(nestedId));

        mockMvc.perform(delete("/api/notes/{noteId}/comments/{id}", noteId, replyId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(first.token())))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/notes/{noteId}/comments/{id}", noteId, replyId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(second.token())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notes/{id}/comments", noteId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(first.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(rootId))
                .andExpect(jsonPath("$.data[0].replies.length()").value(0));

        assertThat(noteCommentMapper.selectById(replyId).getStatus()).isZero();
        assertThat(noteCommentMapper.selectById(nestedId).getStatus()).isZero();

        mockMvc.perform(delete("/api/admin/comments/{id}", rootId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isOk());
        assertThat(noteCommentMapper.selectById(rootId).getStatus()).isZero();
    }

    @Test
    void shouldEnforceReviewWorkflowAndImmediateAccountDisable() throws Exception {
        Session admin = registerAndLogin("p1_admin_review", true);
        Session author = registerAndLogin("p1_review_author", false);
        long noteId = createPublicNote(author.token(), "待审核笔记");

        mockMvc.perform(get("/api/notes/{id}", noteId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value(0));

        mockMvc.perform(get("/api/notes/public")
                        .header(HttpHeaders.AUTHORIZATION, bearer(author.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.id == %d)]".formatted(noteId)).isEmpty());

        mockMvc.perform(put("/api/admin/notes/{id}/reject", noteId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("remark", "请补充书籍版本"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notes/{id}", noteId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value(2))
                .andExpect(jsonPath("$.data.auditRemark").value("请补充书籍版本"));

        mockMvc.perform(put("/api/notes/{id}", noteId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "修订后的笔记", "bookName", "测试图书",
                                "content", "已补充书籍版本和阅读说明", "visibility", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auditStatus").value(0));

        mockMvc.perform(post("/api/notes/{id}/submit", noteId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author.token())))
                .andExpect(status().isOk());

        approve(noteId, admin.token());
        mockMvc.perform(get("/api/notes/public")
                        .header(HttpHeaders.AUTHORIZATION, bearer(author.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.id == %d)]".formatted(noteId)).isNotEmpty());

        mockMvc.perform(put("/api/admin/users/{id}/status", author.userId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(author.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    private long createPublicNote(String token, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/notes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title + " " + UUID.randomUUID(),
                                "bookName", "P1 测试图书", "content", "这是一段用于集成测试的公开笔记内容。",
                                "visibility", 1))))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).path("data").path("id").asLong();
    }

    private long createComment(long noteId, String token, String content, Long parentId) throws Exception {
        Map<String, Object> body = parentId == null ? Map.of("content", content)
                : Map.of("content", content, "parentId", parentId);
        MvcResult result = mockMvc.perform(post("/api/notes/{id}/comments", noteId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).path("data").path("id").asLong();
    }

    private void approve(long noteId, String adminToken) throws Exception {
        mockMvc.perform(put("/api/admin/notes/{id}/approve", noteId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());
    }

    private Session registerAndLogin(String prefix, boolean admin) throws Exception {
        String username = prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        UserProfileVO profile = userService.register(new RegisterRequest(username, "123456", prefix));
        if (admin) {
            User user = userMapper.selectById(profile.id());
            user.setRole(1);
            userMapper.updateById(user);
        }
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", "123456"))))
                .andExpect(status().isOk()).andReturn();
        return new Session(profile.id(), json(login).path("data").path("token").asText());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String bearer(String token) { return "Bearer " + token; }

    private record Session(Long userId, String token) { }
}
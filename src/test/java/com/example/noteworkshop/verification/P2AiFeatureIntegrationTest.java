package com.example.noteworkshop.verification;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.noteworkshop.dto.RegisterRequest;
import com.example.noteworkshop.entity.AiAuditLog;
import com.example.noteworkshop.entity.AiMessage;
import com.example.noteworkshop.entity.NoteAiResult;
import com.example.noteworkshop.entity.NoteComment;
import com.example.noteworkshop.entity.User;
import com.example.noteworkshop.mapper.AiAuditLogMapper;
import com.example.noteworkshop.mapper.AiMessageMapper;
import com.example.noteworkshop.mapper.NoteAiResultMapper;
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
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class P2AiFeatureIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserService userService;
    @Autowired private UserMapper userMapper;
    @Autowired private NoteMapper noteMapper;
    @Autowired private NoteAiResultMapper noteAiResultMapper;
    @Autowired private NoteCommentMapper noteCommentMapper;
    @Autowired private AiAuditLogMapper aiAuditLogMapper;
    @Autowired private AiMessageMapper aiMessageMapper;

    @Test
    void shouldAnalyzeAndSaveOneAiResultPerNote() throws Exception {
        Session admin = registerAndLogin("p2_admin_note_ai", true);
        Session owner = registerAndLogin("p2_note_ai_owner", false);
        Session other = registerAndLogin("p2_note_ai_other", false);
        long noteId = createApprovedNote(owner.token(), admin.token());

        MvcResult analysis = mockMvc.perform(post("/api/notes/{id}/ai-analysis", noteId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.noteId").value(noteId))
                .andExpect(jsonPath("$.data.summary").isNotEmpty())
                .andExpect(jsonPath("$.data.completenessScore").isNumber())
                .andReturn();
        JsonNode analysisData = json(analysis).path("data");

        mockMvc.perform(post("/api/notes/{id}/ai-analysis", noteId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.token())))
                .andExpect(status().isForbidden());

        String saveBody = objectMapper.writeValueAsString(Map.of(
                "summary", analysisData.path("summary").asText(),
                "audience", analysisData.path("audience").asText(),
                "completenessScore", analysisData.path("completenessScore").asInt(),
                "relevanceScore", analysisData.path("relevanceScore").asInt(),
                "depthScore", analysisData.path("depthScore").asInt()));
        mockMvc.perform(post("/api/notes/{id}/ai-result", noteId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(saveBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary").value(analysisData.path("summary").asText()));

        mockMvc.perform(post("/api/notes/{id}/ai-result", noteId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "summary", "更新后的 AI 摘要", "audience", "进阶读者",
                                "completenessScore", 88, "relevanceScore", 91, "depthScore", 84))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary").value("更新后的 AI 摘要"));

        assertThat(noteAiResultMapper.selectCount(Wrappers.<NoteAiResult>lambdaQuery()
                .eq(NoteAiResult::getNoteId, noteId))).isEqualTo(1);
        mockMvc.perform(get("/api/notes/{id}", noteId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiResult.summary").value("更新后的 AI 摘要"));

        assertThat(aiAuditLogMapper.selectCount(Wrappers.<AiAuditLog>lambdaQuery()
                .eq(AiAuditLog::getUserId, owner.userId())
                .eq(AiAuditLog::getScene, "NOTE_ANALYZE"))).isPositive();
    }

    @Test
    void shouldPersistCustomerServiceAndIsolateConversations() throws Exception {
        Session admin = registerAndLogin("p2_admin_chat", true);
        Session owner = registerAndLogin("p2_chat_owner", false);
        Session other = registerAndLogin("p2_chat_other", false);

        MvcResult created = mockMvc.perform(post("/api/ai/conversations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("新的阅读咨询"))
                .andReturn();
        long conversationId = json(created).path("data").path("id").asLong();

        mockMvc.perform(post("/api/ai/conversations/{id}/messages", conversationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"我如何发布公开笔记？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userMessage.role").value("USER"))
                .andExpect(jsonPath("$.data.assistantMessage.content", containsString("待审核")));

        mockMvc.perform(post("/api/ai/conversations/{id}/messages", conversationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"审核被驳回后怎么办？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assistantMessage.content", containsString("重新提交")));

        mockMvc.perform(get("/api/ai/conversations/{id}", conversationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()").value(4));

        mockMvc.perform(get("/api/ai/conversations/{id}", conversationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        mockMvc.perform(get("/api/admin/ai/stats")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").isNumber())
                .andExpect(jsonPath("$.data.success").isNumber())
                .andExpect(jsonPath("$.data.failure").isNumber());

        mockMvc.perform(delete("/api/ai/conversations/{id}", conversationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isOk());
        assertThat(aiMessageMapper.selectCount(Wrappers.<AiMessage>lambdaQuery()
                .eq(AiMessage::getConversationId, conversationId))).isZero();
    }

    @Test
    void shouldRefuseOutOfScopeCustomerQuestion() throws Exception {
        Session owner = registerAndLogin("p2_chat_scope", false);
        MvcResult created = mockMvc.perform(post("/api/ai/conversations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"边界测试\"}"))
                .andExpect(status().isOk()).andReturn();
        long conversationId = json(created).path("data").path("id").asLong();

        mockMvc.perform(post("/api/ai/conversations/{id}/messages", conversationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"请帮我写一首诗\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assistantMessage.content", containsString("超出了笔记工坊客服范围")));
    }

    @Test
    void shouldCleanupCommentAndAiResultWhenNoteIsDeleted() throws Exception {
        Session admin = registerAndLogin("p2_cleanup_admin", true);
        Session owner = registerAndLogin("p2_cleanup_owner", false);
        long noteId = createApprovedNote(owner.token(), admin.token());

        mockMvc.perform(post("/api/notes/{id}/comments", noteId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"cleanup comment\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/notes/{id}/ai-result", noteId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "summary", "cleanup summary", "audience", "cleanup audience",
                                "completenessScore", 80, "relevanceScore", 81, "depthScore", 82))))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/notes/{id}", noteId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner.token())))
                .andExpect(status().isOk());

        assertThat(noteCommentMapper.selectCount(Wrappers.<NoteComment>lambdaQuery()
                .eq(NoteComment::getNoteId, noteId))).isZero();
        assertThat(noteAiResultMapper.selectCount(Wrappers.<NoteAiResult>lambdaQuery()
                .eq(NoteAiResult::getNoteId, noteId))).isZero();
    }
    private long createApprovedNote(String token, String adminToken) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/notes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "AI 阅读分析笔记", "bookName", "Java 编程思想",
                                "content", "例如，理解集合框架需要对比 ArrayList 与 LinkedList 的底层结构。"
                                        + "因为数组查询快，但是插入删除成本较高；总结时要结合实际使用场景。",
                                "visibility", 1))))
                .andExpect(status().isOk()).andReturn();
        long noteId = json(created).path("data").path("id").asLong();
        mockMvc.perform(put("/api/admin/notes/{id}/approve", noteId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());
        return noteId;
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
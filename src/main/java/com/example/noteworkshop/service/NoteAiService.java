package com.example.noteworkshop.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.noteworkshop.dto.NoteAiSaveRequest;
import com.example.noteworkshop.entity.Note;
import com.example.noteworkshop.entity.NoteAiResult;
import com.example.noteworkshop.exception.BusinessException;
import com.example.noteworkshop.exception.ErrorCode;
import com.example.noteworkshop.mapper.NoteAiResultMapper;
import com.example.noteworkshop.mapper.NoteMapper;
import com.example.noteworkshop.security.CurrentUser;
import com.example.noteworkshop.security.UserContext;
import com.example.noteworkshop.vo.AiAnalysisVO;
import com.example.noteworkshop.vo.NoteAiResultVO;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NoteAiService {
    private static final Logger log = LoggerFactory.getLogger(NoteAiService.class);
    private static final String SCENE = "NOTE_ANALYZE";
    private static final String SYSTEM_PROMPT = """
            你是读书笔记分析助手。请只分析用户提供的笔记内容，返回严格 JSON：
            {"summary":"不超过180字的中文摘要","audience":"适宜人群","completenessScore":0到100整数,
            "relevanceScore":0到100整数,"depthScore":0到100整数}。
            不要输出 Markdown，不要添加额外字段，不要编造笔记中不存在的信息。
            """;

    private final NoteMapper noteMapper;
    private final NoteAiResultMapper noteAiResultMapper;
    private final AiProvider aiProvider;
    private final AiAuditService aiAuditService;

    public NoteAiService(NoteMapper noteMapper, NoteAiResultMapper noteAiResultMapper,
                         AiProvider aiProvider, AiAuditService aiAuditService) {
        this.noteMapper = noteMapper;
        this.noteAiResultMapper = noteAiResultMapper;
        this.aiProvider = aiProvider;
        this.aiAuditService = aiAuditService;
    }

    public AiAnalysisVO analyze(Long noteId) {
        Note note = requireManageableNote(noteId);
        CurrentUser currentUser = UserContext.get();
        long startedAt = System.currentTimeMillis();
        try {
            AiAnalysisVO result = aiProvider.isRemoteConfigured() ? analyzeRemotely(note) : analyzeLocally(note);
            aiAuditService.record(currentUser.id(), SCENE, true, System.currentTimeMillis() - startedAt, null);
            return result;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Note AI analysis failed, noteId={}, userId={}", noteId, currentUser.id(), exception);
            aiAuditService.record(currentUser.id(), SCENE, false, System.currentTimeMillis() - startedAt,
                    exception.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 分析失败，请稍后重试");
        }
    }

    @Transactional
    public NoteAiResultVO saveResult(Long noteId, NoteAiSaveRequest request) {
        Note note = requireManageableNote(noteId);
        NoteAiResult existing = noteAiResultMapper.selectOne(
                Wrappers.<NoteAiResult>lambdaQuery().eq(NoteAiResult::getNoteId, noteId));
        if (existing == null) {
            NoteAiResult result = new NoteAiResult();
            result.setNoteId(note.getId());
            copyRequest(result, request);
            noteAiResultMapper.insert(result);
            existing = result;
        } else {
            copyRequest(existing, request);
            noteAiResultMapper.updateById(existing);
        }
        log.info("AI note result saved, noteId={}, resultId={}, userId={}",
                noteId, existing.getId(), UserContext.get().id());
        return toVO(existing);
    }

    @Transactional
    public void deleteResult(Long noteId) {
        requireManageableNote(noteId);
        noteAiResultMapper.delete(Wrappers.<NoteAiResult>lambdaQuery().eq(NoteAiResult::getNoteId, noteId));
    }

    private AiAnalysisVO analyzeRemotely(Note note) {
        String prompt = "标题：" + note.getTitle() + "\n图书：" + note.getBookName() + "\n正文：\n" + note.getContent();
        String content = aiProvider.complete(SYSTEM_PROMPT,
                List.of(new AiProvider.Message("user", prompt)), true);
        JsonNode json = aiProvider.parseJsonContent(content);
        String summary = json.path("summary").asText("").trim();
        String audience = json.path("audience").asText("").trim();
        if (summary.isEmpty() || audience.isEmpty()) {
            throw new IllegalStateException("AI 返回字段不完整");
        }
        return new AiAnalysisVO(note.getId(), summary, audience,
                clampScore(json.path("completenessScore").asInt(0)),
                clampScore(json.path("relevanceScore").asInt(0)),
                clampScore(json.path("depthScore").asInt(0)));
    }

    private AiAnalysisVO analyzeLocally(Note note) {
        String normalized = note.getContent().replaceAll("\\s+", " ").trim();
        String summary = normalized.length() <= 180 ? normalized : normalized.substring(0, 180) + "…";
        String audience = inferAudience(note.getContent());
        int completeness = Math.min(95, 58 + normalized.length() / 40);
        int relevance = Math.min(96, 62 + Math.min(note.getBookName().length(), 12)
                + Math.min(note.getTitle().length(), 12));
        int depth = Math.min(94, 52 + countSignals(note.getContent()) * 6 + normalized.length() / 160);
        return new AiAnalysisVO(note.getId(), summary, audience,
                clampScore(completeness), clampScore(relevance), clampScore(depth));
    }

    private String inferAudience(String content) {
        if (content.contains("源码") || content.contains("原理") || content.contains("架构")) {
            return "具备一定基础、希望深入理解原理的进阶读者";
        }
        if (content.contains("算法") || content.contains("数据库") || content.contains("计算机")) {
            return "计算机相关专业学生与正在准备技术面试的读者";
        }
        if (content.contains("入门") || content.contains("基础") || content.contains("初学")) {
            return "刚开始系统学习该主题的入门读者";
        }
        return "对该书主题感兴趣的在校学生与普通读者";
    }

    private int countSignals(String content) {
        int count = 0;
        String[] signals = {"因为", "但是", "例如", "总结", "原理", "实践", "问题", "思考", "对比", "结论"};
        for (String signal : signals) if (content.contains(signal)) count++;
        return count;
    }

    private int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private Note requireManageableNote(Long noteId) {
        Note note = noteMapper.selectById(noteId);
        if (note == null) throw new BusinessException(ErrorCode.NOT_FOUND, "笔记不存在");
        CurrentUser currentUser = UserContext.get();
        if (!currentUser.id().equals(note.getUserId()) && !Integer.valueOf(1).equals(currentUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能分析自己的笔记");
        }
        return note;
    }

    private void copyRequest(NoteAiResult result, NoteAiSaveRequest request) {
        result.setSummary(request.summary().trim());
        result.setAudience(request.audience().trim());
        result.setCompletenessScore(request.completenessScore());
        result.setRelevanceScore(request.relevanceScore());
        result.setDepthScore(request.depthScore());
    }

    private NoteAiResultVO toVO(NoteAiResult result) {
        return new NoteAiResultVO(result.getId(), result.getSummary(), result.getAudience(),
                result.getCompletenessScore(), result.getRelevanceScore(), result.getDepthScore());
    }
}
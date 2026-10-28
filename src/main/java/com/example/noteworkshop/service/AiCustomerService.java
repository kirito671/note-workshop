package com.example.noteworkshop.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.noteworkshop.common.PageResponse;
import com.example.noteworkshop.dto.AiChatRequest;
import com.example.noteworkshop.dto.AiConversationCreateRequest;
import com.example.noteworkshop.entity.AiConversation;
import com.example.noteworkshop.entity.AiMessage;
import com.example.noteworkshop.exception.BusinessException;
import com.example.noteworkshop.exception.ErrorCode;
import com.example.noteworkshop.mapper.AiConversationMapper;
import com.example.noteworkshop.mapper.AiMessageMapper;
import com.example.noteworkshop.security.UserContext;
import com.example.noteworkshop.vo.AiChatResponseVO;
import com.example.noteworkshop.vo.AiConversationDetailVO;
import com.example.noteworkshop.vo.AiConversationVO;
import com.example.noteworkshop.vo.AiMessageVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class AiCustomerService {
    private static final Logger log = LoggerFactory.getLogger(AiCustomerService.class);
    private static final String DEFAULT_TITLE = "新的阅读咨询";
    private static final String SCENE = "CUSTOMER_SERVICE";
    private static final int SLIDING_WINDOW_MESSAGES = 20;
    private static final String SYSTEM_PROMPT = """
            你是“笔记工坊 Note-Workshop”的平台客服。只回答与平台有关的问题，包括发布和公开笔记、
            审核规则、点赞评论关注、文件上传、账号登录、AI 阅读辅助和 AI 客服用法。
            如果问题与平台无关，礼貌说明无法回答并引导用户询问平台功能。回答简洁、准确，不编造平台规则。
            """;

    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final AiProvider aiProvider;
    private final AiAuditService aiAuditService;

    public AiCustomerService(AiConversationMapper conversationMapper, AiMessageMapper messageMapper,
                             AiProvider aiProvider, AiAuditService aiAuditService) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.aiProvider = aiProvider;
        this.aiAuditService = aiAuditService;
    }

    @Transactional
    public AiConversationVO create(AiConversationCreateRequest request) {
        AiConversation conversation = new AiConversation();
        conversation.setUserId(UserContext.get().id());
        conversation.setTitle(request != null && StringUtils.hasText(request.title())
                ? request.title().trim() : DEFAULT_TITLE);
        conversationMapper.insert(conversation);
        return toConversationVO(conversationMapper.selectById(conversation.getId()));
    }

    public PageResponse<AiConversationVO> page(long page, long size) {
        validatePage(page, size);
        Page<AiConversation> result = conversationMapper.selectPage(Page.of(page, size),
                Wrappers.<AiConversation>lambdaQuery()
                        .eq(AiConversation::getUserId, UserContext.get().id())
                        .orderByDesc(AiConversation::getUpdatedAt)
                        .orderByDesc(AiConversation::getId));
        List<AiConversationVO> records = result.getRecords().stream().map(this::toConversationVO).toList();
        return new PageResponse<>(records, result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    public AiConversationDetailVO detail(Long conversationId) {
        AiConversation conversation = requireOwnedConversation(conversationId);
        List<AiMessageVO> messages = messageMapper.selectList(Wrappers.<AiMessage>lambdaQuery()
                        .eq(AiMessage::getConversationId, conversationId)
                        .orderByAsc(AiMessage::getCreatedAt).orderByAsc(AiMessage::getId))
                .stream().map(this::toMessageVO).toList();
        return new AiConversationDetailVO(toConversationVO(conversation), messages);
    }

    public AiChatResponseVO send(Long conversationId, AiChatRequest request) {
        AiConversation conversation = requireOwnedConversation(conversationId);
        Long userId = UserContext.get().id();
        String content = request.content().trim();

        AiMessage userMessage = insertMessage(conversationId, "USER", content);
        String preferredTitle = DEFAULT_TITLE.equals(conversation.getTitle()) ? titleFrom(content) : conversation.getTitle();
        touchConversation(conversationId, preferredTitle);

        long startedAt = System.currentTimeMillis();
        try {
            List<AiMessage> recent = recentMessages(conversationId);
            String assistantContent = aiProvider.isRemoteConfigured()
                    ? answerRemotely(recent)
                    : answerLocally(recent, content);
            AiMessage assistantMessage = insertMessage(conversationId, "ASSISTANT", assistantContent);
            touchConversation(conversationId, preferredTitle);
            aiAuditService.record(userId, SCENE, true, System.currentTimeMillis() - startedAt, null);
            log.info("AI customer service replied, conversationId={}, userId={}", conversationId, userId);
            return new AiChatResponseVO(conversationId, toMessageVO(userMessage), toMessageVO(assistantMessage));
        } catch (Exception exception) {
            log.error("AI customer service failed, conversationId={}, userId={}", conversationId, userId, exception);
            aiAuditService.record(userId, SCENE, false, System.currentTimeMillis() - startedAt, exception.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "AI 客服暂时不可用，请稍后重试");
        }
    }

    @Transactional
    public void delete(Long conversationId) {
        requireOwnedConversation(conversationId);
        messageMapper.delete(Wrappers.<AiMessage>lambdaQuery().eq(AiMessage::getConversationId, conversationId));
        conversationMapper.deleteById(conversationId);
        log.info("AI conversation deleted, id={}, userId={}", conversationId, UserContext.get().id());
    }

    private String answerRemotely(List<AiMessage> recent) {
        List<AiProvider.Message> context = recent.stream()
                .map(message -> new AiProvider.Message(message.getRole().toLowerCase(), message.getContent()))
                .toList();
        return aiProvider.complete(SYSTEM_PROMPT, context, false).trim();
    }

    private String answerLocally(List<AiMessage> recent, String currentContent) {
        String combined = recent.stream().map(AiMessage::getContent).reduce("", (left, right) -> left + " " + right);
        String text = combined + " " + currentContent;
        if (containsAny(text, "审核", "驳回", "通过", "待审")) {
            return "公开笔记有“待审核、审核通过、审核驳回”三种状态。被驳回后，你可以查看驳回意见，修改并重新提交审核；审核通过后才会参与公开列表、点赞和评论。";
        }
        if (containsAny(text, "发布", "公开", "私有", "可见")) {
            return "发布笔记时，在编辑器里把可见性设为“公开”，保存后会进入待审核状态；管理员通过后才会出现在公开书架。私有笔记只有你自己能查看，不需要审核。";
        }
        if (containsAny(text, "点赞", "排行", "喜欢")) {
            return "只有审核通过的公开笔记可以点赞。取消点赞是幂等的；“我点赞的”按点赞时间分页；热度排行按点赞数降序展示。";
        }
        if (containsAny(text, "评论", "回复", "删除评论")) {
            return "公开笔记详情页支持评论和多级回复。你只能删除自己的评论；删除父评论时，它的所有子回复会一并逻辑删除，管理员也可以删除违规评论。";
        }
        if (containsAny(text, "关注", "粉丝", "作者")) {
            return "在作者主页或笔记详情中可以关注作者。作者统计页会展示粉丝数、关注数和公开笔记数，热门作者按粉丝数排序。";
        }
        if (containsAny(text, "头像", "图片", "上传", "文件")) {
            return "头像支持 JPG、PNG，单张最大 5MB。笔记最多上传 6 张配图，每张最大 5MB。上传失败时先检查格式、大小和是否超过数量上限。";
        }
        if (containsAny(text, "AI", "总结", "评分", "适宜人群", "分析")) {
            return "在笔记详情中点击“AI 阅读分析”，系统会生成摘要、适宜人群和三项评分。结果不会自动写入笔记，你可以选择保存或丢弃；一篇笔记只保留一份 AI 结果。";
        }
        if (containsAny(text, "账号", "登录", "注册", "密码", "禁用")) {
            return "注册需要 3-50 位用户名和至少 6 位密码。忘记密码或账号被禁用需要联系管理员处理；被禁用后，已有登录凭证也会立即失效。";
        }
        if (containsAny(text, "客服", "会话", "历史")) {
            return "AI 客服支持新建会话、查看历史会话和继续旧会话。每个会话只属于当前用户，系统只携带最近 10 轮对话作为上下文。";
        }
        return "这个问题超出了笔记工坊客服范围。你可以询问笔记发布、审核、点赞评论、关注作者、图片上传、账号登录或 AI 阅读辅助等平台功能。";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) if (text.contains(keyword)) return true;
        return false;
    }

    private List<AiMessage> recentMessages(Long conversationId) {
        List<AiMessage> messages = messageMapper.selectList(Wrappers.<AiMessage>lambdaQuery()
                .eq(AiMessage::getConversationId, conversationId)
                .orderByDesc(AiMessage::getCreatedAt)
                .orderByDesc(AiMessage::getId)
                .last("LIMIT " + SLIDING_WINDOW_MESSAGES));
        List<AiMessage> ordered = new ArrayList<>(messages);
        Collections.reverse(ordered);
        return ordered;
    }

    private AiMessage insertMessage(Long conversationId, String role, String content) {
        AiMessage message = new AiMessage();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        messageMapper.insert(message);
        return messageMapper.selectById(message.getId());
    }

    private AiConversation requireOwnedConversation(Long conversationId) {
        AiConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在");
        if (!UserContext.get().id().equals(conversation.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问其他用户的会话");
        }
        return conversation;
    }

    private void touchConversation(Long conversationId, String title) {
        conversationMapper.update(null, Wrappers.<AiConversation>lambdaUpdate()
                .eq(AiConversation::getId, conversationId)
                .set(AiConversation::getTitle, title)
                .set(AiConversation::getUpdatedAt, LocalDateTime.now()));
    }

    private String titleFrom(String content) {
        String title = content.replaceAll("\\s+", " ").trim();
        return title.length() <= 30 ? title : title.substring(0, 30) + "…";
    }

    private AiConversationVO toConversationVO(AiConversation conversation) {
        return new AiConversationVO(conversation.getId(), conversation.getTitle(),
                conversation.getCreatedAt(), conversation.getUpdatedAt());
    }

    private AiMessageVO toMessageVO(AiMessage message) {
        return new AiMessageVO(message.getId(), message.getRole(), message.getContent(), message.getCreatedAt());
    }

    private void validatePage(long page, long size) {
        if (page < 1 || size < 1 || size > 50) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "分页参数不合法");
        }
    }
}
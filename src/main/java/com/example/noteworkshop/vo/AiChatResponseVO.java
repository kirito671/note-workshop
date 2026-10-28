package com.example.noteworkshop.vo;

public record AiChatResponseVO(
        Long conversationId,
        AiMessageVO userMessage,
        AiMessageVO assistantMessage
) {
}
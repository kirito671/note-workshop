package com.example.noteworkshop.vo;

import java.util.List;

public record AiConversationDetailVO(
        AiConversationVO conversation,
        List<AiMessageVO> messages
) {
}
package com.example.noteworkshop.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AiProvider {
    public record Message(String role, String content) {
    }

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public AiProvider(
            ObjectMapper objectMapper,
            @Value("${app.ai.api-key:}") String apiKey,
            @Value("${app.ai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${app.ai.model:gpt-4o-mini}") String model,
            @Value("${app.ai.connect-timeout-seconds:10}") int connectTimeoutSeconds,
            @Value("${app.ai.read-timeout-seconds:45}") int readTimeoutSeconds
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutSeconds * 1000);
        requestFactory.setReadTimeout(readTimeoutSeconds * 1000);
        String normalizedBaseUrl = baseUrl == null ? "https://api.openai.com/v1" : baseUrl.replaceAll("/+$", "");
        this.restClient = RestClient.builder()
                .baseUrl(normalizedBaseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public boolean isRemoteConfigured() {
        return StringUtils.hasText(apiKey);
    }

    public String complete(String systemPrompt, List<Message> context, boolean jsonMode) {
        if (!isRemoteConfigured()) {
            throw new IllegalStateException("AI_API_KEY 未配置");
        }

        List<Map<String, String>> messages = new ArrayList<>();
        if (StringUtils.hasText(systemPrompt)) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        for (Message message : context) {
            messages.add(Map.of("role", message.role(), "content", message.content()));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", messages);
        payload.put("temperature", 0.3);
        if (jsonMode) {
            payload.put("response_format", Map.of("type", "json_object"));
        }

        try {
            JsonNode response = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
            String content = response == null ? "" : response.path("choices").path(0).path("message").path("content").asText("");
            if (!StringUtils.hasText(content)) {
                throw new IllegalStateException("AI 服务返回了空内容");
            }
            return content;
        } catch (Exception exception) {
            throw new IllegalStateException("AI 服务调用失败", exception);
        }
    }

    public JsonNode parseJsonContent(String content) {
        String normalized = content.trim();
        if (normalized.startsWith("```")) {
            int firstLine = normalized.indexOf('\n');
            int lastFence = normalized.lastIndexOf("```");
            if (firstLine >= 0 && lastFence > firstLine) {
                normalized = normalized.substring(firstLine + 1, lastFence).trim();
            }
        }
        try {
            return objectMapper.readTree(normalized);
        } catch (Exception exception) {
            throw new IllegalStateException("AI 返回内容不是合法 JSON", exception);
        }
    }
}
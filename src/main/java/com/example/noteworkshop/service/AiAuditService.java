package com.example.noteworkshop.service;

import com.example.noteworkshop.entity.AiAuditLog;
import com.example.noteworkshop.mapper.AiAuditLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiAuditService {
    private static final Logger log = LoggerFactory.getLogger(AiAuditService.class);
    private final AiAuditLogMapper aiAuditLogMapper;

    public AiAuditService(AiAuditLogMapper aiAuditLogMapper) {
        this.aiAuditLogMapper = aiAuditLogMapper;
    }

    @Transactional
    public void record(Long userId, String scene, boolean success, long durationMs, String errorMessage) {
        try {
            AiAuditLog auditLog = new AiAuditLog();
            auditLog.setUserId(userId);
            auditLog.setScene(scene);
            auditLog.setSuccess(success ? 1 : 0);
            auditLog.setDurationMs(Math.max(0, durationMs));
            String normalized = errorMessage == null ? null : errorMessage.trim();
            if (normalized != null && normalized.length() > 500) normalized = normalized.substring(0, 500);
            auditLog.setErrorMessage(normalized);
            aiAuditLogMapper.insert(auditLog);
        } catch (Exception exception) {
            log.error("Failed to persist AI audit log, scene={}", scene, exception);
        }
    }
}
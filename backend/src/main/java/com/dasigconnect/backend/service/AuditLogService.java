package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.entity.AuditLog;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.AuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog record(
            User actor,
            String action,
            String ipAddress,
            String userAgent,
            UUID resourceId,
            Map<String, ?> metadata) {
        AuditLog auditLog = new AuditLog();
        auditLog.setActor(actor);
        auditLog.setAction(action);
        auditLog.setIpAddress(ipAddress);
        auditLog.setUserAgent(userAgent);
        auditLog.setResourceId(resourceId);
        auditLog.setMetadata(toJson(metadata));
        return auditLogRepository.save(auditLog);
    }

    public AuditLog recordSystemAction(String action, UUID resourceId, Map<String, ?> metadata) {
        return record(null, action, null, null, resourceId, metadata);
    }

    private String toJson(Map<String, ?> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Audit metadata must be JSON serializable", ex);
        }
    }
}

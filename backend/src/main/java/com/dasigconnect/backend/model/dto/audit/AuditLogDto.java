package com.dasigconnect.backend.model.dto.audit;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AuditLogDto(
        UUID id,
        Instant timestamp,
        String action,
        String actionLabel,
        AuditLogCategory category,
        String categoryLabel,
        ActorDto actor,
        EntityRefDto entity,
        ClientInfoDto clientInfo,
        String summary,
        Map<String, Object> metadata,
        String rawMetadata,
        List<AuditDiffEntryDto> diffs
) {
    public record ActorDto(
            UUID id,
            String name,
            String email,
            String role,
            String avatarUrl,
            String institutionName
    ) {}

    public record EntityRefDto(
            UUID id,
            AuditEntityType type,
            String typeLabel,
            String label,
            boolean exists,
            String jumpUrl
    ) {}

    public record ClientInfoDto(
            String ipAddress,
            String userAgent
    ) {}

    public record AuditDiffEntryDto(
            String field,
            String fieldLabel,
            String fromValue,
            String toValue
    ) {}
}

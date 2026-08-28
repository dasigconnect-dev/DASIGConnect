package com.dasigconnect.backend.model.dto.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditLogFilterCriteria(
        Instant startDate,
        Instant endDate,
        UUID actorId,
        String actorQuery,
        AuditLogCategory category,
        String action,
        AuditEntityType entityType,
        UUID resourceId,
        String search
) {
    public AuditLogFilterCriteria withDefaults() {
        return this;
    }
}

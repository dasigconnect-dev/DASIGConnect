package com.dasigconnect.backend.model.dto.systemhealth;

import java.time.Instant;

public record ExternalServiceHealthDto(
        String service,
        HealthStatus status,
        String detail,
        Instant checkedAt,
        Instant expiresAt,
        Long secondsUntilExpiry,
        Integer quotaRemaining,
        Integer quotaLimit,
        Instant resetAt) {

    public ExternalServiceHealthDto(
            String service,
            HealthStatus status,
            String detail,
            Instant checkedAt,
            Instant expiresAt,
            Long secondsUntilExpiry) {
        this(service, status, detail, checkedAt, expiresAt, secondsUntilExpiry, null, null, expiresAt);
    }
}

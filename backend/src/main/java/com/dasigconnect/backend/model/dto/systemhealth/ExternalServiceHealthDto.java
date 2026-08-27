package com.dasigconnect.backend.model.dto.systemhealth;

import java.time.Instant;

public record ExternalServiceHealthDto(
        String service,
        HealthStatus status,
        String detail,
        Instant checkedAt,
        Instant expiresAt,
        Long secondsUntilExpiry) {
}

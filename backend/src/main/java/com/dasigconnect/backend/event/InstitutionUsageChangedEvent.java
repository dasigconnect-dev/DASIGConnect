package com.dasigconnect.backend.event;

import java.util.UUID;

public record InstitutionUsageChangedEvent(
        UUID organizationId,
        UUID institutionId,
        long delta) {
}

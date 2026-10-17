package com.dasigconnect.backend.event;

import java.util.UUID;

public record UserSeatUsageChangedEvent(
        UUID organizationId,
        UUID institutionId,
        UUID userId,
        long delta,
        String transition) {
}

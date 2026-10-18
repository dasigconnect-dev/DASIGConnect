package com.dasigconnect.backend.model.dto.subscription;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionReconciliationDto(
        UUID organizationId,
        Instant measuredAt,
        long institutionCount,
        long activeUserCount,
        long liveMediaStorageBytes,
        long ledgerMediaStorageBytes,
        long mediaStorageDriftBytes) {
}

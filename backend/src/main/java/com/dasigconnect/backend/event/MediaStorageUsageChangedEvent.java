package com.dasigconnect.backend.event;

import java.util.UUID;

public record MediaStorageUsageChangedEvent(
        UUID organizationId,
        UUID institutionId,
        UUID userId,
        UUID assetId,
        long deltaBytes,
        String transition) {
}

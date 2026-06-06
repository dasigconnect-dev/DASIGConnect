package com.dasigconnect.backend.repository;

import java.util.UUID;

public record MediaIntegrityTarget(
        UUID assetId,
        UUID institutionId,
        String storageUrl,
        String assetCode) {
}

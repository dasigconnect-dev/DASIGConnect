package com.dasigconnect.backend.model.dto.subscription;

import java.util.UUID;

public record InstitutionUsageDto(
        UUID institutionId,
        String institutionName,
        long activeUsers,
        long mediaStorageBytes,
        long submissions,
        long aiCaptions,
        long aiMediaSuggestions) {
}

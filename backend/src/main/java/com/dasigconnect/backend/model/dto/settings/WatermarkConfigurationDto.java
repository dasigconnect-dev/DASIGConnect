package com.dasigconnect.backend.model.dto.settings;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WatermarkConfigurationDto(
        UUID id,
        UUID institutionId,
        String institutionName,
        boolean enabled,
        boolean isOverride,
        List<WatermarkElementDto> elements,
        Instant updatedAt,
        String updatedBy
) {}

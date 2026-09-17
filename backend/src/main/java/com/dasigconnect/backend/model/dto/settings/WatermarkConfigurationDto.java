package com.dasigconnect.backend.model.dto.settings;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WatermarkConfigurationDto(
        UUID id,
        boolean enabled,
        List<WatermarkElementDto> elements,
        Instant updatedAt,
        String updatedBy
) {}

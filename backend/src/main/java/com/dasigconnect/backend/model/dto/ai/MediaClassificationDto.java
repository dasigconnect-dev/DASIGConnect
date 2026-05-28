package com.dasigconnect.backend.model.dto.ai;

import java.util.List;

public record MediaClassificationDto(
        String category,
        double confidence,
        String description,
        List<String> suggestedTags
) {}

package com.dasigconnect.backend.model.dto.ai;

import java.util.List;

public record ClassificationSuggestionsDto(
        String suggestedCategory,
        List<String> suggestedTags,
        Double confidence,
        int assetCount
) {}

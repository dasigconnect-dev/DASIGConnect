package com.dasigconnect.backend.model.dto.facebook;

import java.time.Instant;
import java.util.UUID;

public record FacebookPostMediaDto(
        UUID id,
        UUID historicalPostId,
        String pageId,
        String graphMediaId,
        UUID mediaAssetId,
        int position,
        String mediaType,
        Integer width,
        Integer height,
        String sourceAltText,
        Instant sourceUrlExpiresAt) {
}

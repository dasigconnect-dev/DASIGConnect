package com.dasigconnect.backend.model.dto.facebook;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record FacebookHistoricalPostDto(
        UUID id,
        String pageId,
        String graphPostId,
        UUID institutionId,
        String message,
        String permalinkUrl,
        Instant createdTime,
        Instant updatedTime,
        String postType,
        String mediaType,
        int attachmentCount,
        boolean published,
        boolean deletedAtSource,
        Map<String, Object> sourceMetadata,
        Instant firstImportedAt,
        Instant lastSyncedAt) {
}

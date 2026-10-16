package com.dasigconnect.backend.model.dto.facebook;

import com.dasigconnect.backend.model.entity.FacebookImportMode;
import com.dasigconnect.backend.model.entity.FacebookImportStatus;
import java.time.Instant;
import java.util.UUID;

public record FacebookImportJobDto(
        UUID id,
        String pageId,
        String pageName,
        UUID scopeInstitutionId,
        Instant dateFrom,
        Instant dateTo,
        FacebookImportMode mode,
        FacebookImportStatus status,
        long postsDiscovered,
        long postsImported,
        long postsUpdated,
        long mediaDiscovered,
        long mediaImported,
        long mediaFailed,
        UUID startedByUserId,
        Instant startedAt,
        Instant heartbeatAt,
        Instant completedAt,
        String errorCode,
        String errorMessage,
        String graphApiVersion,
        Instant createdAt,
        Instant updatedAt) {
}

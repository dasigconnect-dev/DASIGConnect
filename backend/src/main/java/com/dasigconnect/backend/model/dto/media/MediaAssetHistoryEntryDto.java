package com.dasigconnect.backend.model.dto.media;

import java.time.Instant;

/**
 * One entry in a media asset's Activity history — a synthesized "Uploaded" event
 * plus any {@code audit_log} rows recorded against the asset id.
 */
public record MediaAssetHistoryEntryDto(
        String action,
        String actorName,
        String actorEmail,
        Instant occurredAt,
        String summary) {
}

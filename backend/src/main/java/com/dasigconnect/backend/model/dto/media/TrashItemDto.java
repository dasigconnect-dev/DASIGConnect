package com.dasigconnect.backend.model.dto.media;

import com.dasigconnect.backend.model.entity.MediaAsset;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * One soft-deleted (trashed, not yet purged) asset, with its disposal timestamps and how many
 * days remain before the retention job purges it automatically.
 */
public record TrashItemDto(
        MediaAssetSummaryDto asset,
        Instant deletedAt,
        UUID deletedByUserId,
        Instant purgeDueAt,
        long daysUntilPurge) {

    public static TrashItemDto from(MediaAsset asset, int retentionDays, Instant now) {
        Instant deletedAt = asset.getDeletedAt();
        Instant purgeDueAt = deletedAt == null ? null : deletedAt.plus(Duration.ofDays(retentionDays));
        long daysLeft = 0;
        if (purgeDueAt != null) {
            long seconds = Duration.between(now, purgeDueAt).getSeconds();
            daysLeft = seconds <= 0 ? 0 : (long) Math.ceil(seconds / 86_400.0);
        }
        return new TrashItemDto(
                MediaAssetSummaryDto.from(asset),
                deletedAt,
                asset.getDeletedByUserId(),
                purgeDueAt,
                daysLeft);
    }
}

package com.dasigconnect.backend.model.dto.media;

import java.util.List;
import java.util.UUID;

/**
 * Result of a bulk soft-delete. The operation is resilient (partial): deletable assets are
 * removed and assets that cannot be deleted (referenced by an active submission, already
 * gone, or outside the caller's scope) are reported in {@link #skipped} instead of failing
 * the whole batch.
 */
public class MediaAssetBulkDeleteResponseDto {

    private final List<UUID> deletedIds;
    private final int deletedCount;
    private final List<SkippedAsset> skipped;
    private final int skippedCount;

    public MediaAssetBulkDeleteResponseDto(List<UUID> deletedIds) {
        this(deletedIds, List.of());
    }

    public MediaAssetBulkDeleteResponseDto(List<UUID> deletedIds, List<SkippedAsset> skipped) {
        this.deletedIds = deletedIds;
        this.deletedCount = deletedIds.size();
        this.skipped = skipped;
        this.skippedCount = skipped.size();
    }

    public List<UUID> getDeletedIds() {
        return deletedIds;
    }

    public int getDeletedCount() {
        return deletedCount;
    }

    public List<SkippedAsset> getSkipped() {
        return skipped;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    /** A single asset that was not deleted, with a machine code and a human-readable reason. */
    public record SkippedAsset(UUID assetId, String assetCode, String reason, String message) {
    }
}

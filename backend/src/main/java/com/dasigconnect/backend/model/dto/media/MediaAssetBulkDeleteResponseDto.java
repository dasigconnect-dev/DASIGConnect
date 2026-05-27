package com.dasigconnect.backend.model.dto.media;

import java.util.List;
import java.util.UUID;

public class MediaAssetBulkDeleteResponseDto {

    private final List<UUID> deletedIds;
    private final int deletedCount;

    public MediaAssetBulkDeleteResponseDto(List<UUID> deletedIds) {
        this.deletedIds = deletedIds;
        this.deletedCount = deletedIds.size();
    }

    public List<UUID> getDeletedIds() {
        return deletedIds;
    }

    public int getDeletedCount() {
        return deletedCount;
    }
}

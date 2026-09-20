package com.dasigconnect.backend.model.dto.media;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;

public class MediaAssetBulkTrashRequestDto {

    @NotEmpty
    private List<UUID> assetIds;

    public List<UUID> getAssetIds() {
        return assetIds;
    }

    public void setAssetIds(List<UUID> assetIds) {
        this.assetIds = assetIds;
    }
}

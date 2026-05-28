package com.dasigconnect.backend.model.dto.media;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;

public class MediaAssetBulkDeleteRequestDto {

    @NotEmpty
    private List<UUID> assetIds;

    private boolean force;

    public List<UUID> getAssetIds() {
        return assetIds;
    }

    public void setAssetIds(List<UUID> assetIds) {
        this.assetIds = assetIds;
    }

    public boolean isForce() {
        return force;
    }

    public void setForce(boolean force) {
        this.force = force;
    }
}

package com.dasigconnect.backend.model.dto.media;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public class AlbumAddAssetsRequestDto {

    @NotEmpty
    private List<UUID> assetIds;

    public List<UUID> getAssetIds() { return assetIds; }
    public void setAssetIds(List<UUID> assetIds) { this.assetIds = assetIds; }
}

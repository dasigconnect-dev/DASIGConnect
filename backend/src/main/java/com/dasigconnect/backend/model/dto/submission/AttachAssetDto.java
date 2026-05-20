package com.dasigconnect.backend.model.dto.submission;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class AttachAssetDto {

    @NotNull(message = "mediaAssetId is required")
    private UUID mediaAssetId;

    public UUID getMediaAssetId() { return mediaAssetId; }
    public void setMediaAssetId(UUID mediaAssetId) { this.mediaAssetId = mediaAssetId; }
}

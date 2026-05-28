package com.dasigconnect.backend.model.dto.submission;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public class SubmissionMediaOrderDto {

    @NotEmpty(message = "mediaAssetIds is required")
    private List<@NotNull UUID> mediaAssetIds;

    public List<UUID> getMediaAssetIds() { return mediaAssetIds; }
    public void setMediaAssetIds(List<UUID> mediaAssetIds) { this.mediaAssetIds = mediaAssetIds; }
}

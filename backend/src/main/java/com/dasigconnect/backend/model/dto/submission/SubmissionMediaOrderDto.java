package com.dasigconnect.backend.model.dto.submission;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SubmissionMediaOrderDto {

    @NotEmpty(message = "mediaAssetIds is required")
    private List<@NotNull UUID> mediaAssetIds;

    private Map<@NotNull UUID, @Size(max = 500, message = "Media captions must be 500 characters or fewer") String> mediaCaptions;
    private Map<@NotNull UUID, Boolean> skipWatermarks;

    public List<UUID> getMediaAssetIds() { return mediaAssetIds; }
    public void setMediaAssetIds(List<UUID> mediaAssetIds) { this.mediaAssetIds = mediaAssetIds; }

    public Map<UUID, String> getMediaCaptions() { return mediaCaptions; }
    public void setMediaCaptions(Map<UUID, String> mediaCaptions) { this.mediaCaptions = mediaCaptions; }

    public Map<UUID, Boolean> getSkipWatermarks() { return skipWatermarks; }
    public void setSkipWatermarks(Map<UUID, Boolean> skipWatermarks) { this.skipWatermarks = skipWatermarks; }
}

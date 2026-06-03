package com.dasigconnect.backend.model.dto.media;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public class MediaAssetCurationEditDto {

    @NotNull
    private UUID assetId;

    @Size(max = 140)
    private String title;

    @Size(max = 20)
    private List<@Size(max = 50) String> tags;

    public UUID getAssetId() { return assetId; }
    public void setAssetId(UUID assetId) { this.assetId = assetId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
}

package com.dasigconnect.backend.model.dto.submission;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public class AttachAssetDto {

    @NotNull(message = "mediaAssetId is required")
    private UUID mediaAssetId;

    /**
     * Optional short note a reviewing moderator can give when attaching a Library
     * asset the contributor did not originally submit (A10). Stored on the
     * {@code media_added} validation-log row and mirrored to the audit entry.
     */
    @Size(max = 500, message = "Justification must not exceed 500 characters")
    private String justification;

    public UUID getMediaAssetId() { return mediaAssetId; }
    public void setMediaAssetId(UUID mediaAssetId) { this.mediaAssetId = mediaAssetId; }

    public String getJustification() { return justification; }
    public void setJustification(String justification) { this.justification = justification; }
}

package com.dasigconnect.backend.model.dto.exception;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class DirectPostRequestDto {

    private UUID institutionId;
    private String caption;
    private List<UUID> mediaAssetIds;
    private boolean publishImmediately;
    private Instant scheduledAt;
    private String reason;
    private boolean acknowledgedGrH1Conflict;

    public UUID getInstitutionId() { return institutionId; }
    public void setInstitutionId(UUID institutionId) { this.institutionId = institutionId; }

    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }

    public List<UUID> getMediaAssetIds() { return mediaAssetIds; }
    public void setMediaAssetIds(List<UUID> mediaAssetIds) { this.mediaAssetIds = mediaAssetIds; }

    public boolean isPublishImmediately() { return publishImmediately; }
    public void setPublishImmediately(boolean publishImmediately) { this.publishImmediately = publishImmediately; }

    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public boolean isAcknowledgedGrH1Conflict() { return acknowledgedGrH1Conflict; }
    public void setAcknowledgedGrH1Conflict(boolean acknowledgedGrH1Conflict) {
        this.acknowledgedGrH1Conflict = acknowledgedGrH1Conflict;
    }
}

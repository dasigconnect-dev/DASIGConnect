package com.dasigconnect.backend.model.dto.media;

import com.dasigconnect.backend.model.entity.MediaAssetIntegrityCheck;
import java.time.Instant;
import java.util.UUID;

public class MediaIntegrityCheckDto {

    private UUID id;
    private String expectedSha256;
    private String observedSha256;
    private String status;
    private String trigger;
    private String failureReason;
    private Instant checkedAt;

    public static MediaIntegrityCheckDto from(MediaAssetIntegrityCheck check) {
        MediaIntegrityCheckDto dto = new MediaIntegrityCheckDto();
        dto.id = check.getId();
        dto.expectedSha256 = check.getExpectedSha256();
        dto.observedSha256 = check.getObservedSha256();
        dto.status = check.getStatus().name();
        dto.trigger = check.getTriggerType().name();
        dto.failureReason = check.getFailureReason();
        dto.checkedAt = check.getCheckedAt();
        return dto;
    }

    public UUID getId() { return id; }
    public String getExpectedSha256() { return expectedSha256; }
    public String getObservedSha256() { return observedSha256; }
    public String getStatus() { return status; }
    public String getTrigger() { return trigger; }
    public String getFailureReason() { return failureReason; }
    public Instant getCheckedAt() { return checkedAt; }
}

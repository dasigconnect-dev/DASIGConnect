package com.dasigconnect.backend.model.dto.resolution;

import java.time.Instant;
import java.util.UUID;

import com.dasigconnect.backend.model.entity.PublicationAttempt;
import com.dasigconnect.backend.model.entity.Submission;

public class FailedPublicationDto {

    private UUID submissionId;
    private String eventTitle;
    private UUID institutionId;
    private String institutionName;
    private Instant scheduledAt;
    private int retryCount;
    private Instant lastAttemptAt;
    private String lastError;
    private boolean manualPublishInProgress;
    private Instant lastManualPublishAbandonedAt;

    public static FailedPublicationDto from(Submission s, PublicationAttempt lastAttempt) {
        FailedPublicationDto dto = new FailedPublicationDto();
        dto.submissionId = s.getId();
        dto.eventTitle = s.getEventTitle();
        dto.institutionId = s.getInstitution().getId();
        dto.institutionName = s.getInstitution().getName();
        dto.scheduledAt = s.getScheduledAt();
        dto.retryCount = s.getRetryCount();
        dto.manualPublishInProgress = s.getManualPublishStartedAt() != null;
        dto.lastManualPublishAbandonedAt = s.getLastManualPublishAbandonedAt();
        if (lastAttempt != null) {
            dto.lastAttemptAt = lastAttempt.getAttemptedAt();
            dto.lastError = lastAttempt.getErrorDetail();
        }
        return dto;
    }

    public UUID getSubmissionId() { return submissionId; }
    public String getEventTitle() { return eventTitle; }
    public UUID getInstitutionId() { return institutionId; }
    public String getInstitutionName() { return institutionName; }
    public Instant getScheduledAt() { return scheduledAt; }
    public int getRetryCount() { return retryCount; }
    public Instant getLastAttemptAt() { return lastAttemptAt; }
    public String getLastError() { return lastError; }
    public boolean isManualPublishInProgress() { return manualPublishInProgress; }
    public Instant getLastManualPublishAbandonedAt() { return lastManualPublishAbandonedAt; }
}

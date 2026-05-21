package com.dasigconnect.backend.model.dto.submission;

import com.dasigconnect.backend.model.dto.media.MediaAssetSummaryDto;
import com.dasigconnect.backend.model.entity.Submission;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class SubmissionResponseDto {

    private UUID id;
    private UUID contributorId;
    private String contributorEmail;
    private UUID institutionId;
    private String eventTitle;
    private LocalDate eventDate;
    private String caption;
    private String description;
    private String status;
    private Instant scheduledAt;
    private Instant submittedAt;
    private Instant publishedAt;
    private String platformPostId;
    private String validatorRemarks;
    private String rejectionReason;
    private int retryCount;
    private Instant createdAt;
    private Instant updatedAt;
    private List<MediaAssetSummaryDto> mediaAssets;

    public static SubmissionResponseDto from(Submission s, List<MediaAssetSummaryDto> mediaAssets) {
        SubmissionResponseDto dto = new SubmissionResponseDto();
        dto.id = s.getId();
        dto.contributorId = s.getContributor().getId();
        dto.contributorEmail = s.getContributor().getEmail();
        dto.institutionId = s.getInstitution().getId();
        dto.eventTitle = s.getEventTitle();
        dto.eventDate = s.getEventDate();
        dto.caption = s.getCaption();
        dto.description = s.getDescription();
        dto.status = s.getStatus().name();
        dto.scheduledAt = s.getScheduledAt();
        dto.submittedAt = s.getSubmittedAt();
        dto.publishedAt = s.getPublishedAt();
        dto.platformPostId = s.getPlatformPostId();
        dto.validatorRemarks = s.getValidatorRemarks();
        dto.rejectionReason = s.getRejectionReason();
        dto.retryCount = s.getRetryCount();
        dto.createdAt = s.getCreatedAt();
        dto.updatedAt = s.getUpdatedAt();
        dto.mediaAssets = mediaAssets;
        return dto;
    }

    public UUID getId() { return id; }
    public UUID getContributorId() { return contributorId; }
    public String getContributorEmail() { return contributorEmail; }
    public UUID getInstitutionId() { return institutionId; }
    public String getEventTitle() { return eventTitle; }
    public LocalDate getEventDate() { return eventDate; }
    public String getCaption() { return caption; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public Instant getScheduledAt() { return scheduledAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getPlatformPostId() { return platformPostId; }
    public String getValidatorRemarks() { return validatorRemarks; }
    public String getRejectionReason() { return rejectionReason; }
    public int getRetryCount() { return retryCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<MediaAssetSummaryDto> getMediaAssets() { return mediaAssets; }
}

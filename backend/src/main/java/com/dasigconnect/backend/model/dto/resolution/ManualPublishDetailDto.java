package com.dasigconnect.backend.model.dto.resolution;

import com.dasigconnect.backend.model.entity.MediaFileType;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionMediaAsset;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Full post-content payload for the UC-3.4 Manual Publishing Fallback panel.
 * Includes all fields the Administrator needs to copy and post to Facebook.
 */
public class ManualPublishDetailDto {

    private UUID submissionId;
    private String eventTitle;
    private String caption;
    private String status;
    private Instant scheduledAt;
    private String contributorFirstName;
    private String contributorLastName;
    private String contributorEmail;
    private UUID institutionId;
    private String institutionName;
    private List<MediaItem> mediaAssets;
    private boolean manualPublishInProgress;
    private Instant manualPublishStartedAt;

    public static ManualPublishDetailDto from(Submission s, List<SubmissionMediaAsset> junctionRows) {
        ManualPublishDetailDto dto = new ManualPublishDetailDto();
        dto.submissionId = s.getId();
        dto.eventTitle = s.getEventTitle();
        dto.caption = s.getCaption();
        dto.status = s.getStatus().name();
        dto.scheduledAt = s.getScheduledAt();
        dto.contributorFirstName = s.getContributor().getFirstName();
        dto.contributorLastName = s.getContributor().getLastName();
        dto.contributorEmail = s.getContributor().getEmail();
        dto.institutionId = s.getInstitution().getId();
        dto.institutionName = s.getInstitution().getName();
        dto.mediaAssets = junctionRows.stream()
                .sorted(Comparator.comparingInt(SubmissionMediaAsset::getDisplayOrder))
                .map(sma -> new MediaItem(
                        sma.getMediaAsset().getId(),
                        sma.getMediaAsset().getStorageUrl(),
                        sma.getMediaAsset().getFileType(),
                        sma.getMediaAsset().getFileName(),
                        sma.getDisplayOrder()
                ))
                .toList();
        dto.manualPublishInProgress = s.getManualPublishStartedAt() != null;
        dto.manualPublishStartedAt = s.getManualPublishStartedAt();
        return dto;
    }

    public record MediaItem(UUID id, String storageUrl, MediaFileType fileType, String fileName, int displayOrder) {}

    public UUID getSubmissionId() { return submissionId; }
    public String getEventTitle() { return eventTitle; }
    public String getCaption() { return caption; }
    public String getStatus() { return status; }
    public Instant getScheduledAt() { return scheduledAt; }
    public String getContributorFirstName() { return contributorFirstName; }
    public String getContributorLastName() { return contributorLastName; }
    public String getContributorEmail() { return contributorEmail; }
    public UUID getInstitutionId() { return institutionId; }
    public String getInstitutionName() { return institutionName; }
    public List<MediaItem> getMediaAssets() { return mediaAssets; }
    public boolean isManualPublishInProgress() { return manualPublishInProgress; }
    public Instant getManualPublishStartedAt() { return manualPublishStartedAt; }
}

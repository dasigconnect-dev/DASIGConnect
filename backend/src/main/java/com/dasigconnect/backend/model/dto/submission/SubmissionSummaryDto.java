package com.dasigconnect.backend.model.dto.submission;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.dasigconnect.backend.model.entity.Submission;

public class SubmissionSummaryDto {

    private UUID id;
    private String eventTitle;
    private LocalDate eventDate;
    private String caption;
    private String status;
    private Instant scheduledAt;
    private Instant publishedAt;
    private Instant submittedAt;
    private Instant createdAt;
    private UUID institutionId;
    private String institutionName;
    private String contributorEmail;
    private long mediaCount;
    private SubmissionMediaPreviewDto previewMediaAsset;
    private String category;
    private String templateId;
    private boolean fastTrack;
    private String liveEventName;
    private List<String> tags;
    private String albumName;
    private List<String> mediaTags;
    /**
     * Only populated by {@link #fromSnapshot}, for a needs_revision row the
     * frontend renders straight from this summary with no live detail fetch
     * (see ValidationQueueScreen's needs_revision handling) — the ordinary
     * queue-row path ({@link #from}) leaves this null since a row only ever
     * needs {@code previewMediaAsset} + {@code mediaCount}, not the full list.
     */
    private List<SubmissionMediaPreviewDto> mediaAssets;

    public static SubmissionSummaryDto from(Submission s, long mediaCount) {
        return from(s, mediaCount, null);
    }

    public static SubmissionSummaryDto from(
            Submission s,
            long mediaCount,
            SubmissionMediaPreviewDto previewMediaAsset) {
        SubmissionSummaryDto dto = new SubmissionSummaryDto();
        dto.id = s.getId();
        dto.eventTitle = s.getEventTitle();
        dto.eventDate = s.getEventDate();
        dto.caption = s.getCaption();
        dto.status = s.getStatus().name();
        dto.scheduledAt = s.getScheduledAt();
        dto.publishedAt = s.getPublishedAt();
        dto.submittedAt = s.getSubmittedAt();
        dto.createdAt = s.getCreatedAt();
        dto.institutionId = s.getInstitution().getId();
        dto.institutionName = s.getInstitution().getName();
        dto.contributorEmail = s.getContributor().getEmail();
        dto.mediaCount = mediaCount;
        dto.previewMediaAsset = previewMediaAsset;
        dto.category = s.getCategory();
        dto.templateId = s.getTemplateId();
        dto.fastTrack = s.isFastTrack();
        dto.liveEventName = s.getLiveEventName();
        dto.tags = (s.getTags() == null || s.getTags().isBlank())
                ? List.of()
                : Arrays.stream(s.getTags().split(",")).map(String::trim).filter(t -> !t.isEmpty()).toList();
        dto.albumName = s.getAlbumName();
        dto.mediaTags = (s.getMediaTags() == null || s.getMediaTags().isBlank())
                ? List.of()
                : Arrays.stream(s.getMediaTags().split(",")).map(String::trim).filter(t -> !t.isEmpty()).toList();
        return dto;
    }

    /**
     * Builds a Review Queue row for a {@code needs_revision} submission from
     * its frozen {@link SubmissionReviewSnapshot} rather than the live entity
     * columns, so a contributor's in-progress edits/autosaves don't change
     * what the moderator sees until an actual resubmission. Identity/ownership
     * fields (id, status, institution, contributor, submitted/created dates)
     * still come from the live entity — only display fields are frozen.
     */
    public static SubmissionSummaryDto fromSnapshot(Submission s, SubmissionReviewSnapshot snapshot) {
        SubmissionSummaryDto dto = new SubmissionSummaryDto();
        dto.id = s.getId();
        dto.eventTitle = snapshot.getEventTitle();
        dto.eventDate = snapshot.getEventDate();
        dto.caption = snapshot.getCaption();
        dto.status = s.getStatus().name();
        dto.scheduledAt = snapshot.getScheduledAt();
        dto.publishedAt = s.getPublishedAt();
        dto.submittedAt = s.getSubmittedAt();
        dto.createdAt = s.getCreatedAt();
        dto.institutionId = s.getInstitution().getId();
        dto.institutionName = s.getInstitution().getName();
        dto.contributorEmail = s.getContributor().getEmail();
        dto.mediaCount = snapshot.getMediaCount();
        dto.previewMediaAsset = snapshot.getPreviewMediaAsset();
        dto.category = snapshot.getCategory();
        dto.templateId = snapshot.getTemplateId();
        dto.fastTrack = snapshot.isFastTrack();
        dto.liveEventName = snapshot.getLiveEventName();
        dto.tags = snapshot.getTags();
        dto.albumName = snapshot.getAlbumName();
        dto.mediaTags = snapshot.getMediaTags();
        dto.mediaAssets = snapshot.getMediaAssets();
        return dto;
    }

    public UUID getId() {
        return id;
    }

    public String getEventTitle() {
        return eventTitle;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public String getCaption() {
        return caption;
    }

    public String getStatus() {
        return status;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getInstitutionId() {
        return institutionId;
    }

    public String getInstitutionName() {
        return institutionName;
    }

    public String getContributorEmail() {
        return contributorEmail;
    }

    public long getMediaCount() {
        return mediaCount;
    }

    public SubmissionMediaPreviewDto getPreviewMediaAsset() {
        return previewMediaAsset;
    }

    public String getCategory() {
        return category;
    }

    public String getTemplateId() {
        return templateId;
    }

    public boolean isFastTrack() {
        return fastTrack;
    }

    public String getLiveEventName() {
        return liveEventName;
    }

    public List<String> getTags() {
        return tags;
    }

    public String getAlbumName() {
        return albumName;
    }

    public List<String> getMediaTags() {
        return mediaTags;
    }

    public List<SubmissionMediaPreviewDto> getMediaAssets() {
        return mediaAssets;
    }
}

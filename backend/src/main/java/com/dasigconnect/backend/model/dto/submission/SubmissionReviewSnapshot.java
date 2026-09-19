package com.dasigconnect.backend.model.dto.submission;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.dasigconnect.backend.model.entity.MediaAsset;

/**
 * Frozen copy of a submission's reviewable display fields, captured at
 * submit()/resubmit() time and serialized into {@code submissions.review_snapshot}.
 * Deserialized back to render the Review Queue for a {@code needs_revision}
 * submission, so a contributor's in-progress edits/autosaves (which mutate
 * the live {@code Submission} row directly) don't change what the moderator
 * sees until an actual resubmission replaces this snapshot.
 */
public class SubmissionReviewSnapshot {

    private String eventTitle;
    private LocalDate eventDate;
    private String caption;
    private String category;
    private String templateId;
    private boolean fastTrack;
    private String liveEventName;
    private List<String> tags;
    private String albumName;
    private List<String> mediaTags;
    private Instant scheduledAt;
    private long mediaCount;
    private SubmissionMediaPreviewDto previewMediaAsset;
    /**
     * The full ordered media list, not just the first-item preview above.
     * Needed because a {@code needs_revision} row is rendered by the frontend
     * straight from this snapshot with no live detail fetch (see
     * ValidationQueueScreen's {@code openSubmission}) — without this, a
     * submission with e.g. 3 attached images would show only its first one
     * (or, before this field existed, none at all whenever no preview asset
     * was captured), looking exactly like "no media attached" despite
     * {@code SubmissionService.submit()} requiring at least one.
     */
    private List<SubmissionMediaPreviewDto> mediaAssets;

    public static SubmissionReviewSnapshot capture(
            com.dasigconnect.backend.model.entity.Submission submission,
            List<MediaAsset> orderedAssets) {
        SubmissionReviewSnapshot snapshot = new SubmissionReviewSnapshot();
        snapshot.eventTitle = submission.getEventTitle();
        snapshot.eventDate = submission.getEventDate();
        snapshot.caption = submission.getCaption();
        snapshot.category = submission.getCategory();
        snapshot.templateId = submission.getTemplateId();
        snapshot.fastTrack = submission.isFastTrack();
        snapshot.liveEventName = submission.getLiveEventName();
        snapshot.tags = splitCsv(submission.getTags());
        snapshot.albumName = submission.getAlbumName();
        snapshot.mediaTags = splitCsv(submission.getMediaTags());
        snapshot.scheduledAt = submission.getScheduledAt();
        snapshot.mediaCount = orderedAssets.size();
        snapshot.mediaAssets = orderedAssets.stream().map(SubmissionMediaPreviewDto::from).toList();
        snapshot.previewMediaAsset = orderedAssets.isEmpty()
                ? null
                : SubmissionMediaPreviewDto.from(orderedAssets.get(0));
        return snapshot;
    }

    private static List<String> splitCsv(String value) {
        return (value == null || value.isBlank())
                ? List.of()
                : java.util.Arrays.stream(value.split(","))
                        .map(String::trim)
                        .filter(v -> !v.isEmpty())
                        .toList();
    }

    public String getEventTitle() { return eventTitle; }
    public void setEventTitle(String eventTitle) { this.eventTitle = eventTitle; }

    public LocalDate getEventDate() { return eventDate; }
    public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }

    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }

    public boolean isFastTrack() { return fastTrack; }
    public void setFastTrack(boolean fastTrack) { this.fastTrack = fastTrack; }

    public String getLiveEventName() { return liveEventName; }
    public void setLiveEventName(String liveEventName) { this.liveEventName = liveEventName; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public String getAlbumName() { return albumName; }
    public void setAlbumName(String albumName) { this.albumName = albumName; }

    public List<String> getMediaTags() { return mediaTags; }
    public void setMediaTags(List<String> mediaTags) { this.mediaTags = mediaTags; }

    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }

    public long getMediaCount() { return mediaCount; }
    public void setMediaCount(long mediaCount) { this.mediaCount = mediaCount; }

    public SubmissionMediaPreviewDto getPreviewMediaAsset() { return previewMediaAsset; }
    public void setPreviewMediaAsset(SubmissionMediaPreviewDto previewMediaAsset) { this.previewMediaAsset = previewMediaAsset; }

    public List<SubmissionMediaPreviewDto> getMediaAssets() { return mediaAssets; }
    public void setMediaAssets(List<SubmissionMediaPreviewDto> mediaAssets) { this.mediaAssets = mediaAssets; }
}

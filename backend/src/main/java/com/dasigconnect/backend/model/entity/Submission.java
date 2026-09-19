package com.dasigconnect.backend.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "submissions")
public class Submission {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contributor_id", nullable = false)
    private User contributor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @Column(name = "event_title", nullable = false, length = 255)
    private String eventTitle;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(columnDefinition = "text")
    private String caption;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SubmissionStatus status = SubmissionStatus.draft;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    /**
     * The slot as of the moment this submission was approved (set once, in
     * {@code ValidationService.approve()}, never touched afterward) — the
     * anchor point for the Moderator reschedule cap in
     * {@code SubmissionService.reschedule()}: a Moderator may only move a
     * post within 1 day of this original slot, not of wherever it most
     * recently landed. Null for a Fast-Track submission (never has a slot)
     * and for anything approved before this column existed.
     */
    @Column(name = "original_scheduled_at")
    private Instant originalScheduledAt;

    /**
     * Count of Moderator-initiated calendar reschedules via
     * {@code SubmissionService.reschedule()} — capped at 2. Not incremented
     * by an Administrator's reschedule (unrestricted) or by a schedule
     * change made during review, before approval (UC-2.4 A9, a different
     * action). Per-submission, not per-Moderator: it doesn't matter how many
     * different Moderators made the moves, only how many times this post
     * has been moved by the role.
     */
    @Column(name = "moderator_reschedule_count", nullable = false)
    private int moderatorRescheduleCount = 0;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "platform_post_id", length = 255)
    private String platformPostId;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "tags", columnDefinition = "text")
    private String tags;

    @Column(name = "template_id", length = 100)
    private String templateId;

    @Column(name = "album_name", length = 255)
    private String albumName;

    @Column(name = "media_tags", columnDefinition = "text")
    private String mediaTags;

    @Column(name = "fast_track", nullable = false)
    private boolean fastTrack;

    @Column(name = "live_event_name", length = 255)
    private String liveEventName;

    @Column(name = "validator_remarks", columnDefinition = "text")
    private String validatorRemarks;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "manual_publish_started_at")
    private Instant manualPublishStartedAt;

    @Column(name = "last_manual_publish_abandoned_at")
    private Instant lastManualPublishAbandonedAt;

    @Column(name = "published_manual_url", columnDefinition = "text")
    private String publishedManualUrl;

    @Column(name = "published_manual_notes", columnDefinition = "text")
    private String publishedManualNotes;

    @Column(name = "requires_manual_publishing", nullable = false)
    private boolean requiresManualPublishing;

    @Column(name = "token_blocked_at")
    private Instant tokenBlockedAt;

    @Column(name = "token_escalated_24h_at")
    private Instant tokenEscalated24hAt;

    @Column(name = "token_final_failed_at")
    private Instant tokenFinalFailedAt;

    /**
     * JSON snapshot of the reviewable display fields (title, date, caption,
     * category, tags, album, scheduled time, media list) captured at the
     * moment this submission was last submitted or resubmitted for review
     * (see {@code SubmissionService.submit()}). While the submission sits in
     * {@code needs_revision}, the contributor's ongoing edits/autosaves
     * mutate the live columns above directly — this snapshot is what the
     * Review Queue displays instead, so those in-progress edits don't leak
     * into the moderator's view until an actual resubmission overwrites it.
     * Null for submissions that have never been submitted, or that reached
     * needs_revision before this column existed.
     */
    @Column(name = "review_snapshot", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String reviewSnapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getContributor() {
        return contributor;
    }

    public void setContributor(User contributor) {
        this.contributor = contributor;
    }

    public Institution getInstitution() {
        return institution;
    }

    public void setInstitution(Institution institution) {
        this.institution = institution;
    }

    public String getEventTitle() {
        return eventTitle;
    }

    public void setEventTitle(String eventTitle) {
        this.eventTitle = eventTitle;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDate eventDate) {
        this.eventDate = eventDate;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public SubmissionStatus getStatus() {
        return status;
    }

    public void setStatus(SubmissionStatus status) {
        this.status = status;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(Instant scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public Instant getOriginalScheduledAt() {
        return originalScheduledAt;
    }

    public void setOriginalScheduledAt(Instant originalScheduledAt) {
        this.originalScheduledAt = originalScheduledAt;
    }

    public int getModeratorRescheduleCount() {
        return moderatorRescheduleCount;
    }

    public void setModeratorRescheduleCount(int moderatorRescheduleCount) {
        this.moderatorRescheduleCount = moderatorRescheduleCount;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getPlatformPostId() {
        return platformPostId;
    }

    public void setPlatformPostId(String platformPostId) {
        this.platformPostId = platformPostId;
    }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }

    public String getAlbumName() { return albumName; }
    public void setAlbumName(String albumName) { this.albumName = albumName; }

    public String getMediaTags() { return mediaTags; }
    public void setMediaTags(String mediaTags) { this.mediaTags = mediaTags; }

    public boolean isFastTrack() { return fastTrack; }
    public void setFastTrack(boolean fastTrack) { this.fastTrack = fastTrack; }

    public String getLiveEventName() { return liveEventName; }
    public void setLiveEventName(String liveEventName) { this.liveEventName = liveEventName; }

    public String getValidatorRemarks() {
        return validatorRemarks;
    }

    public void setValidatorRemarks(String validatorRemarks) {
        this.validatorRemarks = validatorRemarks;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public Instant getManualPublishStartedAt() {
        return manualPublishStartedAt;
    }

    public void setManualPublishStartedAt(Instant manualPublishStartedAt) {
        this.manualPublishStartedAt = manualPublishStartedAt;
    }

    public Instant getLastManualPublishAbandonedAt() {
        return lastManualPublishAbandonedAt;
    }

    public void setLastManualPublishAbandonedAt(Instant lastManualPublishAbandonedAt) {
        this.lastManualPublishAbandonedAt = lastManualPublishAbandonedAt;
    }

    public String getPublishedManualUrl() {
        return publishedManualUrl;
    }

    public void setPublishedManualUrl(String publishedManualUrl) {
        this.publishedManualUrl = publishedManualUrl;
    }

    public String getPublishedManualNotes() {
        return publishedManualNotes;
    }

    public void setPublishedManualNotes(String publishedManualNotes) {
        this.publishedManualNotes = publishedManualNotes;
    }

    public boolean isRequiresManualPublishing() {
        return requiresManualPublishing;
    }

    public void setRequiresManualPublishing(boolean requiresManualPublishing) {
        this.requiresManualPublishing = requiresManualPublishing;
    }

    public Instant getTokenBlockedAt() {
        return tokenBlockedAt;
    }

    public void setTokenBlockedAt(Instant tokenBlockedAt) {
        this.tokenBlockedAt = tokenBlockedAt;
    }

    public Instant getTokenEscalated24hAt() {
        return tokenEscalated24hAt;
    }

    public void setTokenEscalated24hAt(Instant tokenEscalated24hAt) {
        this.tokenEscalated24hAt = tokenEscalated24hAt;
    }

    public Instant getTokenFinalFailedAt() {
        return tokenFinalFailedAt;
    }

    public void setTokenFinalFailedAt(Instant tokenFinalFailedAt) {
        this.tokenFinalFailedAt = tokenFinalFailedAt;
    }

    public String getReviewSnapshot() {
        return reviewSnapshot;
    }

    public void setReviewSnapshot(String reviewSnapshot) {
        this.reviewSnapshot = reviewSnapshot;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

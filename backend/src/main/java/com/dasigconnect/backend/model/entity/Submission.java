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

    @Column(name = "validator_remarks", columnDefinition = "text")
    private String validatorRemarks;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "manual_publish_started_at")
    private Instant manualPublishStartedAt;

    @Column(name = "published_manual_url", columnDefinition = "text")
    private String publishedManualUrl;

    @Column(name = "published_manual_notes", columnDefinition = "text")
    private String publishedManualNotes;

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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

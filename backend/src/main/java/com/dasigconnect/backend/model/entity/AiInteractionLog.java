package com.dasigconnect.backend.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_interaction_log")
public class AiInteractionLog {

    @Id
    private UUID id;

    /** Null for AI events not tied to a submission (e.g. template drafts). */
    @Column(name = "submission_id")
    private UUID submissionId;

    /** Null when the actor has no institution (Moderator/Admin) and there's no submission. */
    @Column(name = "institution_id")
    private UUID institutionId;

    @Column(name = "interaction_type", nullable = false, length = 30)
    private String interactionType;

    @Column(name = "action_taken", nullable = false, length = 30)
    private String actionTaken;

    @Column(name = "tone_selected", length = 30)
    private String toneSelected;

    /** What the AI proposed, for events that compare it with the final choice (album_match). */
    @Column(name = "suggested_value", columnDefinition = "text")
    private String suggestedValue;

    /** Optional 1-5 quality rating recorded at decision time (RP-03 / REQ-320, REQ-321). */
    @Column(name = "quality_rating")
    private Short qualityRating;

    /** Optional caption drafting duration in milliseconds from opening Post Details to saving/applying (RP-03 / REQ-320). */
    @Column(name = "drafting_duration_ms")
    private Long draftingDurationMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getSubmissionId() { return submissionId; }
    public void setSubmissionId(UUID submissionId) { this.submissionId = submissionId; }
    public UUID getInstitutionId() { return institutionId; }
    public void setInstitutionId(UUID institutionId) { this.institutionId = institutionId; }
    public String getInteractionType() { return interactionType; }
    public void setInteractionType(String interactionType) { this.interactionType = interactionType; }
    public String getActionTaken() { return actionTaken; }
    public void setActionTaken(String actionTaken) { this.actionTaken = actionTaken; }
    public String getToneSelected() { return toneSelected; }
    public void setToneSelected(String toneSelected) { this.toneSelected = toneSelected; }
    public String getSuggestedValue() { return suggestedValue; }
    public void setSuggestedValue(String suggestedValue) { this.suggestedValue = suggestedValue; }
    public Short getQualityRating() { return qualityRating; }
    public void setQualityRating(Short qualityRating) { this.qualityRating = qualityRating; }
    public Long getDraftingDurationMs() { return draftingDurationMs; }
    public void setDraftingDurationMs(Long draftingDurationMs) { this.draftingDurationMs = draftingDurationMs; }
    public Instant getCreatedAt() { return createdAt; }
}

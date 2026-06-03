package com.dasigconnect.backend.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_interaction_log")
public class AiInteractionLog {

    @Id
    private UUID id;

    // Nullable since UC-4.6: search feedback is not tied to a submission.
    @Column(name = "submission_id")
    private UUID submissionId;

    @Column(name = "institution_id", nullable = false)
    private UUID institutionId;

    @Column(name = "interaction_type", nullable = false, length = 30)
    private String interactionType;

    @Column(name = "action_taken", nullable = false, length = 30)
    private String actionTaken;

    @Column(name = "tone_selected", length = 30)
    private String toneSelected;

    // UC-4.6 feedback target (e.g. the asset a search-result thumbs-up refers to).
    @Column(name = "target_asset_id")
    private UUID targetAssetId;

    @Column(name = "result_rank")
    private Integer resultRank;

    @Column(name = "rating")
    private Short rating;

    @Column(name = "query_text")
    private String queryText;

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
    public UUID getTargetAssetId() { return targetAssetId; }
    public void setTargetAssetId(UUID targetAssetId) { this.targetAssetId = targetAssetId; }
    public Integer getResultRank() { return resultRank; }
    public void setResultRank(Integer resultRank) { this.resultRank = resultRank; }
    public Short getRating() { return rating; }
    public void setRating(Short rating) { this.rating = rating; }
    public String getQueryText() { return queryText; }
    public void setQueryText(String queryText) { this.queryText = queryText; }
    public Instant getCreatedAt() { return createdAt; }
}

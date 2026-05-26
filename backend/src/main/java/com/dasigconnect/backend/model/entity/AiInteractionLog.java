package com.dasigconnect.backend.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_interaction_log")
public class AiInteractionLog {

    @Id
    private UUID id;

    @Column(name = "submission_id", nullable = false)
    private UUID submissionId;

    @Column(name = "institution_id", nullable = false)
    private UUID institutionId;

    @Column(name = "interaction_type", nullable = false, length = 30)
    private String interactionType;

    @Column(name = "action_taken", nullable = false, length = 30)
    private String actionTaken;

    @Column(name = "tone_selected", length = 30)
    private String toneSelected;

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
    public Instant getCreatedAt() { return createdAt; }
}

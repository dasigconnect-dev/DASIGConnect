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
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "override_requests")
public class OverrideRequest {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contributor_id", nullable = false)
    private User contributor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "institution_id", nullable = false)
    private Institution institution;

    @Column(name = "requested_slot", nullable = false)
    private Instant requestedSlot;

    @Column(name = "violated_rule", nullable = false, length = 20)
    private String violatedRule;

    @Column(name = "override_reason", nullable = false, columnDefinition = "text")
    private String overrideReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OverrideRequestDecision decision = OverrideRequestDecision.pending;

    @Column(name = "decision_reason", columnDefinition = "text")
    private String decisionReason;

    @Column(name = "suggested_slot")
    private Instant suggestedSlot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Submission getSubmission() { return submission; }
    public void setSubmission(Submission submission) { this.submission = submission; }

    public User getContributor() { return contributor; }
    public void setContributor(User contributor) { this.contributor = contributor; }

    public Institution getInstitution() { return institution; }
    public void setInstitution(Institution institution) { this.institution = institution; }

    public Instant getRequestedSlot() { return requestedSlot; }
    public void setRequestedSlot(Instant requestedSlot) { this.requestedSlot = requestedSlot; }

    public String getViolatedRule() { return violatedRule; }
    public void setViolatedRule(String violatedRule) { this.violatedRule = violatedRule; }

    public String getOverrideReason() { return overrideReason; }
    public void setOverrideReason(String overrideReason) { this.overrideReason = overrideReason; }

    public OverrideRequestDecision getDecision() { return decision; }
    public void setDecision(OverrideRequestDecision decision) { this.decision = decision; }

    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String decisionReason) { this.decisionReason = decisionReason; }

    public Instant getSuggestedSlot() { return suggestedSlot; }
    public void setSuggestedSlot(Instant suggestedSlot) { this.suggestedSlot = suggestedSlot; }

    public User getDecidedBy() { return decidedBy; }
    public void setDecidedBy(User decidedBy) { this.decidedBy = decidedBy; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
}

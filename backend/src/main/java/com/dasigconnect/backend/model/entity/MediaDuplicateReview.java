package com.dasigconnect.backend.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * UC-4.12 Phase 7F: one append-only human duplicate-adjudication record. The latest row per
 * {@code candidateAssetId} is the standing decision; a reversal is simply a newer row.
 */
@Entity
@Table(name = "media_duplicate_reviews")
public class MediaDuplicateReview {

    @Id
    private UUID id;

    @Column(name = "institution_id", nullable = false)
    private UUID institutionId;

    @Column(name = "candidate_asset_id", nullable = false)
    private UUID candidateAssetId;

    @Column(name = "compared_asset_id")
    private UUID comparedAssetId;

    @Column(name = "canonical_asset_id")
    private UUID canonicalAssetId;

    @Column(name = "decision", nullable = false, length = 20)
    private String decision;

    @Column(name = "merged_tags", nullable = false)
    private boolean mergedTags;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at", nullable = false, updatable = false)
    private Instant reviewedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        reviewedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getInstitutionId() { return institutionId; }
    public void setInstitutionId(UUID institutionId) { this.institutionId = institutionId; }
    public UUID getCandidateAssetId() { return candidateAssetId; }
    public void setCandidateAssetId(UUID candidateAssetId) { this.candidateAssetId = candidateAssetId; }
    public UUID getComparedAssetId() { return comparedAssetId; }
    public void setComparedAssetId(UUID comparedAssetId) { this.comparedAssetId = comparedAssetId; }
    public UUID getCanonicalAssetId() { return canonicalAssetId; }
    public void setCanonicalAssetId(UUID canonicalAssetId) { this.canonicalAssetId = canonicalAssetId; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public boolean isMergedTags() { return mergedTags; }
    public void setMergedTags(boolean mergedTags) { this.mergedTags = mergedTags; }
    public UUID getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(UUID reviewedBy) { this.reviewedBy = reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
}

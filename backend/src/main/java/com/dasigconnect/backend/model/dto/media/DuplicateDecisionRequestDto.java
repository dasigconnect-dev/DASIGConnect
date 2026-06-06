package com.dasigconnect.backend.model.dto.media;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/**
 * UC-4.12 Phase 7F: adjudicate a duplicate candidate. {@code decision} is one of
 * {@code duplicate | keep_both | not_duplicate}; {@code canonicalAssetId} is required only when
 * the decision is {@code duplicate} and must be one of the two assets in the pair.
 */
public class DuplicateDecisionRequestDto {

    @NotBlank
    private String decision;

    private UUID canonicalAssetId;

    private boolean mergeTags;

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public UUID getCanonicalAssetId() { return canonicalAssetId; }
    public void setCanonicalAssetId(UUID canonicalAssetId) { this.canonicalAssetId = canonicalAssetId; }
    public boolean isMergeTags() { return mergeTags; }
    public void setMergeTags(boolean mergeTags) { this.mergeTags = mergeTags; }
}

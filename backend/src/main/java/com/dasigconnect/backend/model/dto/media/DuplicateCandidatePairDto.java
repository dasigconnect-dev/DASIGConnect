package com.dasigconnect.backend.model.dto.media;

import java.time.Instant;
import java.util.UUID;

/**
 * UC-4.12 Phase 7F: a duplicate candidate and the asset it is suspected to duplicate, side by
 * side. {@code hammingDistance} is the explainable perceptual-hash signal. For a reviewed pair
 * {@code decision} is set; for a pending pair it is null.
 */
public record DuplicateCandidatePairDto(
        UUID candidateAssetId,
        MediaAssetSummaryDto candidate,
        MediaAssetSummaryDto compared,
        Integer hammingDistance,
        String decision,
        UUID canonicalAssetId,
        boolean mergedTags,
        Instant reviewedAt) {
}

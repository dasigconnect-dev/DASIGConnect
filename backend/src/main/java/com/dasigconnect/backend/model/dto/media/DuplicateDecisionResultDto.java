package com.dasigconnect.backend.model.dto.media;

import com.dasigconnect.backend.model.entity.MediaDuplicateReview;
import java.time.Instant;
import java.util.UUID;

/** UC-4.12 Phase 7F: the recorded outcome of a duplicate adjudication. */
public record DuplicateDecisionResultDto(
        UUID reviewId,
        UUID candidateAssetId,
        String decision,
        UUID canonicalAssetId,
        boolean mergedTags,
        Instant reviewedAt) {

    public static DuplicateDecisionResultDto from(MediaDuplicateReview r) {
        return new DuplicateDecisionResultDto(
                r.getId(),
                r.getCandidateAssetId(),
                r.getDecision(),
                r.getCanonicalAssetId(),
                r.isMergedTags(),
                r.getReviewedAt());
    }
}

package com.dasigconnect.backend.model.dto.media;

import com.dasigconnect.backend.repository.RepositoryHealthCounts;
import java.time.Instant;
import java.util.UUID;

/**
 * UC-4.12 Phase 7C: Repository Health snapshot. {@code scope} is {@code "institution"} or
 * {@code "network"}; {@code institutionId} is null for the administrator network view. Every
 * group maps to a drill-down filter the Media Library understands ({@code ?health=...}).
 */
public record RepositoryHealthDto(
        String scope,
        UUID institutionId,
        Instant generatedAt,
        Totals totals,
        Integrity integrity,
        Processing processing,
        Curation curation,
        Governance governance,
        Retention retention) {

    public record Totals(long activeAssets, long storageBytes, long readyAssets) {
    }

    public record Integrity(
            long checksumCovered,
            double checksumCoveragePct,
            long verified,
            long pending,
            long mismatch,
            long missing,
            long error,
            long failuresTotal,
            long reviewOpen,
            long reviewAcknowledged) {
    }

    public record Processing(long failed, long processing) {
    }

    public record Curation(long curated, long uncurated, double completenessPct, long unorganized) {
    }

    public record Governance(long internalOnly, long duplicateCandidates) {
    }

    public record Retention(long pendingPurge, long approachingPurge) {
    }

    public static RepositoryHealthDto from(String scope, UUID institutionId, RepositoryHealthCounts c) {
        long active = c.getActiveAssets();
        long ready = c.getReadyAssets();
        long curated = c.getMetadataCurated();
        long failures = c.getIntegrityMismatch() + c.getIntegrityMissing() + c.getIntegrityError();
        return new RepositoryHealthDto(
                scope,
                institutionId,
                Instant.now(),
                new Totals(active, c.getStorageBytes(), ready),
                new Integrity(
                        c.getChecksumCovered(),
                        pct(c.getChecksumCovered(), active),
                        c.getIntegrityVerified(),
                        c.getIntegrityPending(),
                        c.getIntegrityMismatch(),
                        c.getIntegrityMissing(),
                        c.getIntegrityError(),
                        failures,
                        c.getReviewOpen(),
                        c.getReviewAcknowledged()),
                new Processing(c.getProcessingFailed(), c.getProcessingPending()),
                new Curation(curated, Math.max(ready - curated, 0), pct(curated, ready), c.getUnorganized()),
                new Governance(c.getInternalOnly(), c.getDuplicateCandidates()),
                new Retention(c.getDeletedPendingPurge(), c.getDeletedApproachingPurge()));
    }

    /** Whole-number-safe percentage rounded to one decimal; 0 when the denominator is 0. */
    private static double pct(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return Math.round((10000.0 * numerator) / denominator) / 100.0;
    }
}

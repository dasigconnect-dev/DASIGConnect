package com.dasigconnect.backend.repository;

/**
 * UC-4.12 Phase 7C: single-row aggregate projection for the Repository Health dashboard.
 * Every count is produced by one filtered aggregate scan of {@code media_assets} so the
 * dashboard never fans out into per-metric queries (HikariCP-5 friendly).
 */
public interface RepositoryHealthCounts {
    long getActiveAssets();
    long getStorageBytes();
    long getReadyAssets();
    long getChecksumCovered();
    long getIntegrityVerified();
    long getIntegrityPending();
    long getIntegrityMismatch();
    long getIntegrityMissing();
    long getIntegrityError();
    long getReviewOpen();
    long getReviewAcknowledged();
    long getProcessingFailed();
    long getProcessingPending();
    long getMetadataCurated();
    long getUnorganized();
    long getInternalOnly();
    long getDuplicateCandidates();
    long getDeletedPendingPurge();
    long getDeletedApproachingPurge();
}

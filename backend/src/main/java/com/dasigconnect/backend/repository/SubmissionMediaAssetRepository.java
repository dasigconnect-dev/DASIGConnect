package com.dasigconnect.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.SubmissionMediaAsset;

public interface SubmissionMediaAssetRepository extends JpaRepository<SubmissionMediaAsset, UUID> {

    List<SubmissionMediaAsset> findBySubmissionIdOrderByDisplayOrderAsc(UUID submissionId);

    /** PublishingSchedulerJob: loads junction rows with mediaAsset eagerly joined so
     *  the lazy proxy does not need a session after the transaction closes. */
    @Query("""
        SELECT sma FROM SubmissionMediaAsset sma
        JOIN FETCH sma.mediaAsset
        WHERE sma.submission.id = :submissionId
        ORDER BY sma.displayOrder ASC
        """)
    List<SubmissionMediaAsset> findBySubmissionIdWithMediaAsset(@Param("submissionId") UUID submissionId);

    Optional<SubmissionMediaAsset> findBySubmissionIdAndMediaAssetId(UUID submissionId, UUID mediaAssetId);

    boolean existsBySubmissionIdAndMediaAssetId(UUID submissionId, UUID mediaAssetId);

    long countBySubmissionId(UUID submissionId);

    void deleteBySubmissionId(UUID submissionId);

    List<SubmissionMediaAsset> findByMediaAssetIdOrderByCreatedAtDesc(UUID mediaAssetId);

    @Query("""
        SELECT COUNT(sma) FROM SubmissionMediaAsset sma
        WHERE sma.mediaAsset.id = :assetId
          AND sma.submission.status IN (
                            com.dasigconnect.backend.model.entity.SubmissionStatus.pending,
                            com.dasigconnect.backend.model.entity.SubmissionStatus.in_review,
                            com.dasigconnect.backend.model.entity.SubmissionStatus.scheduled
          )
        """)
    long countBlockingSubmissionsByAssetId(@Param("assetId") UUID assetId);

    @Query("""
                SELECT COUNT(sma) FROM SubmissionMediaAsset sma
                WHERE sma.mediaAsset.id = :assetId
                    AND sma.submission.status IN (
                            com.dasigconnect.backend.model.entity.SubmissionStatus.draft,
                            com.dasigconnect.backend.model.entity.SubmissionStatus.needs_revision
                    )
                """)
    long countDraftSubmissionsByAssetId(@Param("assetId") UUID assetId);

    @Query("""
        SELECT sma.mediaAsset FROM SubmissionMediaAsset sma
        WHERE sma.submission.id = :submissionId
          AND sma.mediaAsset.deletedAt IS NULL
        ORDER BY sma.displayOrder ASC
        """)
    List<MediaAsset> findMediaAssetsBySubmissionId(@Param("submissionId") UUID submissionId);

    /** UC-4.x consent gate: asset codes attached to a submission that are NOT cleared for public. */
    @Query("""
        SELECT sma.mediaAsset.assetCode FROM SubmissionMediaAsset sma
        WHERE sma.submission.id = :submissionId
          AND sma.mediaAsset.deletedAt IS NULL
          AND (sma.mediaAsset.visibility IS NULL OR sma.mediaAsset.visibility <> 'cleared_for_public')
        ORDER BY sma.displayOrder ASC
        """)
    List<String> findUnclearedAssetCodes(@Param("submissionId") UUID submissionId);
}

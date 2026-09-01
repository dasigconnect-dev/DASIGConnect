package com.dasigconnect.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    boolean existsByMediaAssetId(UUID mediaAssetId);

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

    /**
     * Media Repository visibility: an asset only "publishes" to the Media
     * Repository once it has been used in a submission that has left draft
     * status. Used together with {@link #findAssetIdsWithAnySubmissionLink}
     * to identify assets that are still exclusively attached to an
     * unsubmitted draft — those stay out of the repository entirely.
     */
    @Query("""
        SELECT DISTINCT sma.mediaAsset.id FROM SubmissionMediaAsset sma
        WHERE sma.mediaAsset.id IN :assetIds
          AND sma.submission.status <> com.dasigconnect.backend.model.entity.SubmissionStatus.draft
        """)
    Set<UUID> findAssetIdsUsedBeyondDraft(@Param("assetIds") Collection<UUID> assetIds);

    /** Asset IDs that are attached to at least one submission, regardless of status. */
    @Query("""
        SELECT DISTINCT sma.mediaAsset.id FROM SubmissionMediaAsset sma
        WHERE sma.mediaAsset.id IN :assetIds
        """)
    Set<UUID> findAssetIdsWithAnySubmissionLink(@Param("assetIds") Collection<UUID> assetIds);
}

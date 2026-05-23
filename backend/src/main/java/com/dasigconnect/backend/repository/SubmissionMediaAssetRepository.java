package com.dasigconnect.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dasigconnect.backend.model.entity.SubmissionMediaAsset;

public interface SubmissionMediaAssetRepository extends JpaRepository<SubmissionMediaAsset, UUID> {

    List<SubmissionMediaAsset> findBySubmissionIdOrderByDisplayOrderAsc(UUID submissionId);

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
}

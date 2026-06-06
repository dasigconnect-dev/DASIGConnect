package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.MediaDuplicateReview;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaDuplicateReviewRepository extends JpaRepository<MediaDuplicateReview, UUID> {

    /** Candidate ids that already have at least one decision (so they leave the pending queue). */
    @Query("""
        SELECT DISTINCT r.candidateAssetId FROM MediaDuplicateReview r
        WHERE (:institutionId IS NULL OR r.institutionId = :institutionId)
        """)
    List<UUID> findReviewedCandidateIds(@Param("institutionId") UUID institutionId);

    /** Most recent decisions first; the service keeps the latest per candidate. */
    @Query("""
        SELECT r FROM MediaDuplicateReview r
        WHERE (:institutionId IS NULL OR r.institutionId = :institutionId)
        ORDER BY r.reviewedAt DESC
        """)
    List<MediaDuplicateReview> findRecent(
            @Param("institutionId") UUID institutionId, Pageable pageable);
}

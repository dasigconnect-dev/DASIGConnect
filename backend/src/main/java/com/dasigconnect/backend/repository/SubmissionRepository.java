package com.dasigconnect.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dasigconnect.backend.model.entity.Submission;

/**
 * Extends the base SubmissionRepository created by M1.
 *
 * M1 created the base JpaRepository stub. M4 owns the custom query methods
 * needed by GuardRailService (GR-S1 soft rule check).
 */
public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

    /**
     * GR-S1: Count submissions for a given institution that are scheduled but
     * not yet published (pending + in_review + scheduled states). Soft rule:
     * warn if count >= 3.
     */
    @Query("""
        SELECT COUNT(s) FROM Submission s
        WHERE s.institution.id = :institutionId
        AND s.status IN (
            com.dasigconnect.backend.model.entity.SubmissionStatus.pending,
            com.dasigconnect.backend.model.entity.SubmissionStatus.in_review,
            com.dasigconnect.backend.model.entity.SubmissionStatus.scheduled
        )
    """)
    long countUnpublishedByInstitution(@Param("institutionId") UUID institutionId);

    List<Submission> findAllByInstitutionId(UUID institutionId);

    // UC-1.3 list queries — role-filtered
    List<Submission> findByContributorIdAndInstitutionIdOrderByCreatedAtDesc(UUID contributorId, UUID institutionId);
    List<Submission> findByInstitutionIdOrderByCreatedAtDesc(UUID institutionId);
    List<Submission> findAllByOrderByCreatedAtDesc();

    boolean existsByIdAndInstitutionId(UUID id, UUID institutionId);
    boolean existsByIdAndContributorId(UUID id, UUID contributorId);

    @Query("""
        SELECT s FROM Submission s
        WHERE s.status IN (
            com.dasigconnect.backend.model.entity.SubmissionStatus.pending,
            com.dasigconnect.backend.model.entity.SubmissionStatus.in_review
        )
        AND s.scheduledAt IS NOT NULL
        AND s.scheduledAt >= :windowStart
        AND s.scheduledAt < :windowEnd
        """)
    List<Submission> findApproachingDeadlines(
            @Param("windowStart") java.time.Instant windowStart,
            @Param("windowEnd") java.time.Instant windowEnd);
}

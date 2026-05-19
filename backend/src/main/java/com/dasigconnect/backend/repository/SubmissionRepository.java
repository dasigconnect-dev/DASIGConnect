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

    /**
     * Find all submissions by institution, used by InstitutionService when
     * transitioning institution states.
     */
    List<Submission> findAllByInstitutionId(UUID institutionId);
}

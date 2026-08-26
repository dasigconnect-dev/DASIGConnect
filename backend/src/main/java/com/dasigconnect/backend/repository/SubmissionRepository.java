package com.dasigconnect.backend.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionStatus;

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

    // UC-1.3 "My Submissions" — authored-by-caller, regardless of role
    List<Submission> findByContributorIdOrderByCreatedAtDesc(UUID contributorId);

    boolean existsByInstitutionId(UUID institutionId);
    boolean existsByIdAndInstitutionId(UUID id, UUID institutionId);
    boolean existsByIdAndContributorId(UUID id, UUID contributorId);
    boolean existsByContributorId(UUID contributorId);

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

    // UC-2.4 approval queue — network-wide PENDING + IN_REVIEW sorted by scheduledAt ASC
    @Query("""
        SELECT s FROM Submission s
        WHERE s.status IN (
            com.dasigconnect.backend.model.entity.SubmissionStatus.pending,
            com.dasigconnect.backend.model.entity.SubmissionStatus.in_review
        )
        ORDER BY s.scheduledAt ASC NULLS LAST
        """)
    List<Submission> findValidationQueue();

    // UC-2.4 approval history — network-wide, all post-review statuses, most recently updated first
    @Query("""
        SELECT s FROM Submission s
        WHERE s.status IN (
            com.dasigconnect.backend.model.entity.SubmissionStatus.needs_revision,
            com.dasigconnect.backend.model.entity.SubmissionStatus.missed_review,
            com.dasigconnect.backend.model.entity.SubmissionStatus.scheduled,
            com.dasigconnect.backend.model.entity.SubmissionStatus.publishing,
            com.dasigconnect.backend.model.entity.SubmissionStatus.publish_failed,
            com.dasigconnect.backend.model.entity.SubmissionStatus.published,
            com.dasigconnect.backend.model.entity.SubmissionStatus.published_manual,
            com.dasigconnect.backend.model.entity.SubmissionStatus.admin_direct_post,
            com.dasigconnect.backend.model.entity.SubmissionStatus.direct_post_scheduled,
            com.dasigconnect.backend.model.entity.SubmissionStatus.direct_post_publishing,
            com.dasigconnect.backend.model.entity.SubmissionStatus.direct_post_failed,
            com.dasigconnect.backend.model.entity.SubmissionStatus.rejected
        )
        ORDER BY s.updatedAt DESC
        """)
    List<Submission> findValidationHistory();

    // ── UC-3.1 Publishing Pipeline ─────────────────────────────────────────────

    /** PublishingSchedulerJob: SCHEDULED and DIRECT_POST_SCHEDULED submissions due for publishing. */
    @Query("""
        SELECT s FROM Submission s
        WHERE s.status IN (
            com.dasigconnect.backend.model.entity.SubmissionStatus.scheduled,
            com.dasigconnect.backend.model.entity.SubmissionStatus.direct_post_scheduled
        )
        AND s.scheduledAt BETWEEN :from AND :to
        ORDER BY s.scheduledAt ASC
        """)
    List<Submission> findScheduledInPublishWindow(
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE Submission s
        SET s.status = :claimedStatus
        WHERE s.id = :submissionId
          AND s.status = :expectedStatus
        """)
    int claimForPublishing(
            @Param("submissionId") UUID submissionId,
            @Param("expectedStatus") SubmissionStatus expectedStatus,
            @Param("claimedStatus") SubmissionStatus claimedStatus);

    /** StaleSubmissionDetectorJob (GR-T9): SCHEDULED / DIRECT_POST_SCHEDULED submissions whose slot has passed. */
    @Query("""
        SELECT s FROM Submission s
        WHERE s.status IN (
            com.dasigconnect.backend.model.entity.SubmissionStatus.scheduled,
            com.dasigconnect.backend.model.entity.SubmissionStatus.direct_post_scheduled,
            com.dasigconnect.backend.model.entity.SubmissionStatus.publishing,
            com.dasigconnect.backend.model.entity.SubmissionStatus.direct_post_publishing
        )
        AND s.scheduledAt < :cutoff
        ORDER BY s.scheduledAt ASC
        """)
    List<Submission> findMissedScheduledSubmissions(@Param("cutoff") Instant cutoff);

    /**
     * StaleSubmissionDetectorJob (GR-T9 / UC-2.4 A6): PENDING / IN_REVIEW submissions
     * whose scheduled publication time has already passed — they missed their review
     * window and must be transitioned to MISSED_REVIEW.
     */
    @Query("""
        SELECT s FROM Submission s
        WHERE s.status IN (
            com.dasigconnect.backend.model.entity.SubmissionStatus.pending,
            com.dasigconnect.backend.model.entity.SubmissionStatus.in_review
        )
        AND s.scheduledAt IS NOT NULL
        AND s.scheduledAt < :cutoff
        ORDER BY s.scheduledAt ASC
        """)
    List<Submission> findMissedReviewSubmissions(@Param("cutoff") Instant cutoff);

    /** Resolution Center: PUBLISH_FAILED and DIRECT_POST_FAILED submissions sorted newest-scheduled first. */
    @Query("SELECT s FROM Submission s JOIN FETCH s.institution JOIN FETCH s.contributor WHERE s.id = :id")
    java.util.Optional<Submission> findByIdWithInstitution(@Param("id") UUID id);

    @Query("""
        SELECT s FROM Submission s
        JOIN FETCH s.institution
        WHERE s.status IN (
            com.dasigconnect.backend.model.entity.SubmissionStatus.publish_failed,
            com.dasigconnect.backend.model.entity.SubmissionStatus.direct_post_failed,
            com.dasigconnect.backend.model.entity.SubmissionStatus.missed_review
        )
        ORDER BY s.scheduledAt DESC
        """)
    List<Submission> findPublishFailures();

    @Query("""
        SELECT s FROM Submission s
        JOIN FETCH s.institution
        JOIN FETCH s.contributor
        WHERE s.tokenBlockedAt IS NOT NULL
        AND s.status IN (
            com.dasigconnect.backend.model.entity.SubmissionStatus.scheduled,
            com.dasigconnect.backend.model.entity.SubmissionStatus.direct_post_scheduled
        )
        ORDER BY s.tokenBlockedAt ASC
        """)
    List<Submission> findTokenBlockedScheduledSubmissions();

    /** UC-3.5 Category B: escalated PENDING/IN_REVIEW submissions due within the given window. */
    @Query("""
        SELECT s FROM Submission s
        WHERE s.status IN (
            com.dasigconnect.backend.model.entity.SubmissionStatus.pending,
            com.dasigconnect.backend.model.entity.SubmissionStatus.in_review
        )
        AND s.scheduledAt IS NOT NULL
        AND s.scheduledAt BETWEEN :from AND :to
        ORDER BY s.scheduledAt ASC
        """)
    List<Submission> findEscalatedForTimeout(
            @Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * Calendar API (admin): all submissions that have a calendar position, any
     * status. A scheduled slot OR a publish timestamp counts — the latter covers
     * Live Event / Fast-Track posts that publish without ever reserving a slot.
     */
    @Query("""
        SELECT s FROM Submission s
        WHERE (s.scheduledAt IS NOT NULL OR s.publishedAt IS NOT NULL)
        AND s.status <> com.dasigconnect.backend.model.entity.SubmissionStatus.missed_review
        AND s.status <> com.dasigconnect.backend.model.entity.SubmissionStatus.draft
        ORDER BY COALESCE(s.scheduledAt, s.publishedAt) ASC
        """)
    List<Submission> findAllWithScheduledSlot();

    /**
     * Calendar API (contributor/validator): the caller's OWN authored submissions
     * that are in a workflow state — publish/direct-post failures, still pending or
     * in review, or missed review. These are shown in full only to the author so
     * they can track their own pipeline; other viewers never see them.
     */
    @Query("""
        SELECT s FROM Submission s
        WHERE s.contributor.id = :contributorId
        AND s.status IN (
            com.dasigconnect.backend.model.entity.SubmissionStatus.publish_failed,
            com.dasigconnect.backend.model.entity.SubmissionStatus.direct_post_failed,
            com.dasigconnect.backend.model.entity.SubmissionStatus.pending,
            com.dasigconnect.backend.model.entity.SubmissionStatus.in_review,
            com.dasigconnect.backend.model.entity.SubmissionStatus.missed_review
        )
        AND (s.scheduledAt IS NOT NULL OR s.publishedAt IS NOT NULL)
        ORDER BY COALESCE(s.scheduledAt, s.publishedAt) ASC
        """)
    List<Submission> findOwnCalendarWorkflowSlots(@Param("contributorId") UUID contributorId);

    /**
     * Calendar API (contributor/validator): all institutions' submissions that are in a
     * calendar-visible status only — scheduled, publishing, or published variants.
     * Drafts, pending, in-review, failed, and rejected rows are excluded so they
     * cannot leak cross-institution even in masked form.
     */
    @Query("""
        SELECT s FROM Submission s
        WHERE (s.scheduledAt IS NOT NULL OR s.publishedAt IS NOT NULL)
        AND s.status IN (
            com.dasigconnect.backend.model.entity.SubmissionStatus.scheduled,
            com.dasigconnect.backend.model.entity.SubmissionStatus.direct_post_scheduled,
            com.dasigconnect.backend.model.entity.SubmissionStatus.publishing,
            com.dasigconnect.backend.model.entity.SubmissionStatus.direct_post_publishing,
            com.dasigconnect.backend.model.entity.SubmissionStatus.published,
            com.dasigconnect.backend.model.entity.SubmissionStatus.published_manual,
            com.dasigconnect.backend.model.entity.SubmissionStatus.admin_direct_post
        )
        ORDER BY COALESCE(s.scheduledAt, s.publishedAt) ASC
        """)
    List<Submission> findAllCalendarVisibleSlots();

    /** Calendar API (contributor/validator): institution-scoped submissions with a slot. */
    @Query("""
        SELECT s FROM Submission s
        WHERE s.scheduledAt IS NOT NULL
        AND s.institution.id = :institutionId
        ORDER BY s.scheduledAt ASC
        """)
    List<Submission> findWithScheduledSlotByInstitution(@Param("institutionId") UUID institutionId);

    /** AbandonmentDetectorJob: submissions stuck in manual-publish-started state. */
    @Query("""
        SELECT s FROM Submission s
        WHERE s.manualPublishStartedAt IS NOT NULL
        AND s.manualPublishStartedAt < :cutoff
        """)
    List<Submission> findAbandonedManualPublishes(@Param("cutoff") Instant cutoff);
}

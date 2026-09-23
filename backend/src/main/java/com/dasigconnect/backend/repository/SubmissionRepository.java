package com.dasigconnect.backend.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
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

    /**
     * Server-paged My Submissions query. Bucket expansion is performed by the
     * service and ownership is always constrained to the authenticated author.
     */
    @EntityGraph(attributePaths = {"institution", "contributor"})
    @Query(value = """
        SELECT s FROM Submission s
        WHERE s.contributor.id = :contributorId
          AND s.status IN :statuses
          AND (
              :search = ''
              OR LOCATE(:search, LOWER(COALESCE(s.eventTitle, ''))) > 0
              OR LOCATE(:search, LOWER(COALESCE(s.caption, ''))) > 0
              OR LOCATE(:search, LOWER(COALESCE(s.institution.name, ''))) > 0
              OR LOCATE(:search, LOWER(COALESCE(s.contributor.email, ''))) > 0
              OR LOCATE(:search, LOWER(COALESCE(s.liveEventName, ''))) > 0
              OR (:matchesStatus = true AND s.status IN :searchStatuses)
          )
        ORDER BY s.createdAt DESC, s.id DESC
        """,
        countQuery = """
        SELECT COUNT(s) FROM Submission s
        WHERE s.contributor.id = :contributorId
          AND s.status IN :statuses
          AND (
              :search = ''
              OR LOCATE(:search, LOWER(COALESCE(s.eventTitle, ''))) > 0
              OR LOCATE(:search, LOWER(COALESCE(s.caption, ''))) > 0
              OR LOCATE(:search, LOWER(COALESCE(s.institution.name, ''))) > 0
              OR LOCATE(:search, LOWER(COALESCE(s.contributor.email, ''))) > 0
              OR LOCATE(:search, LOWER(COALESCE(s.liveEventName, ''))) > 0
              OR (:matchesStatus = true AND s.status IN :searchStatuses)
          )
        """)
    Page<Submission> findSubmissionPage(
            @Param("contributorId") UUID contributorId,
            @Param("statuses") List<SubmissionStatus> statuses,
            @Param("search") String search,
            @Param("matchesStatus") boolean matchesStatus,
            @Param("searchStatuses") List<SubmissionStatus> searchStatuses,
            Pageable pageable);

    interface SubmissionStatusCount {
        SubmissionStatus getStatus();
        long getCount();
    }

    @Query("""
        SELECT s.status AS status, COUNT(s) AS count
        FROM Submission s
        WHERE s.contributor.id = :contributorId
        GROUP BY s.status
        """)
    List<SubmissionStatusCount> countStatusesByContributorId(
            @Param("contributorId") UUID contributorId);

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

    // UC-2.4 approval queue — network-wide PENDING + IN_REVIEW + NEEDS_REVISION.
    // Fast-Track submissions (no scheduledAt) sort first as the urgent items
    // UC-1.9 expects; everything else follows by scheduledAt ASC, then by
    // submittedAt as a stable tiebreaker among same-priority items (oldest
    // first). NEEDS_REVISION rows sort after every actionable row regardless
    // of schedule/fast-track — they are back in the contributor's hands and
    // not something a moderator can act on right now, just something they
    // should still be able to see. The service layer renders these rows from
    // Submission.reviewSnapshot (frozen at the last submit()/resubmit()), not
    // the live columns, since the contributor may be actively editing/autosaving them.
    @Query("""
        SELECT s FROM Submission s
        WHERE s.status IN (
            com.dasigconnect.backend.model.entity.SubmissionStatus.pending,
            com.dasigconnect.backend.model.entity.SubmissionStatus.in_review,
            com.dasigconnect.backend.model.entity.SubmissionStatus.needs_revision
        )
        ORDER BY
            CASE WHEN s.status = com.dasigconnect.backend.model.entity.SubmissionStatus.needs_revision THEN 1 ELSE 0 END ASC,
            s.fastTrack DESC, s.scheduledAt ASC NULLS LAST, s.submittedAt ASC
        """)
    List<Submission> findValidationQueue();

    // UC-2.4 approval history — network-wide, all post-review statuses, most recently updated first.
    // NEEDS_REVISION is intentionally excluded here: it now lives in findValidationQueue() above
    // (rendered from its frozen snapshot) so it stays visible in the active queue rather than only
    // the "All" history tab. It re-enters as a fully live PENDING row once resubmitted.
    @Query("""
        SELECT s FROM Submission s
        WHERE s.status IN (
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

    /**
     * Server-paged Review Queue/history query. The service expands the requested
     * view into statuses and selects active (ascending, Fast-Track-first) or
     * history (descending) ordering.
     */
    @EntityGraph(attributePaths = {"institution", "contributor"})
    @Query(value = """
        SELECT s FROM Submission s
        WHERE s.status IN :statuses
          AND (
              :search = ''
              OR LOCATE(:search, LOWER(COALESCE(s.eventTitle, ''))) > 0
              OR LOCATE(:search, LOWER(COALESCE(s.caption, ''))) > 0
              OR LOCATE(:search, LOWER(COALESCE(s.description, ''))) > 0
              OR LOCATE(:search, LOWER(COALESCE(s.tags, ''))) > 0
              OR LOCATE(:search, LOWER(COALESCE(s.institution.name, ''))) > 0
              OR LOCATE(:search, LOWER(COALESCE(s.contributor.email, ''))) > 0
              OR LOCATE(:search, LOWER(CAST(s.eventDate AS String))) > 0
          )
        ORDER BY
          CASE WHEN :activeOrdering = true AND s.fastTrack = true THEN 0 ELSE 1 END ASC,
          CASE WHEN :ascending = true AND :sortMode = 'publish_slot'
                    AND COALESCE(s.scheduledAt, s.publishedAt) IS NULL THEN 0 ELSE 1 END ASC,
          CASE WHEN :ascending = true AND :sortMode = 'publish_slot'
                    THEN COALESCE(s.scheduledAt, s.publishedAt) END ASC,
          CASE WHEN :ascending = false AND :sortMode = 'publish_slot'
                    AND COALESCE(s.scheduledAt, s.publishedAt) IS NULL THEN 1 ELSE 0 END ASC,
          CASE WHEN :ascending = false AND :sortMode = 'publish_slot'
                    THEN COALESCE(s.scheduledAt, s.publishedAt) END DESC,
          CASE WHEN :ascending = true AND :sortMode = 'submitted'
                    THEN COALESCE(s.submittedAt, s.createdAt) END ASC,
          CASE WHEN :ascending = false AND :sortMode = 'submitted'
                    THEN COALESCE(s.submittedAt, s.createdAt) END DESC,
          s.id ASC
        """,
        countQuery = """
        SELECT COUNT(s) FROM Submission s
        WHERE s.status IN :statuses
          AND (
              :search = ''
              OR LOCATE(:search, LOWER(COALESCE(s.eventTitle, ''))) > 0
              OR LOCATE(:search, LOWER(COALESCE(s.caption, ''))) > 0
              OR LOCATE(:search, LOWER(COALESCE(s.description, ''))) > 0
              OR LOCATE(:search, LOWER(COALESCE(s.tags, ''))) > 0
              OR LOCATE(:search, LOWER(COALESCE(s.institution.name, ''))) > 0
              OR LOCATE(:search, LOWER(COALESCE(s.contributor.email, ''))) > 0
              OR LOCATE(:search, LOWER(CAST(s.eventDate AS String))) > 0
          )
        """)
    Page<Submission> findValidationPage(
            @Param("statuses") List<SubmissionStatus> statuses,
            @Param("search") String search,
            @Param("sortMode") String sortMode,
            @Param("activeOrdering") boolean activeOrdering,
            @Param("ascending") boolean ascending,
            Pageable pageable);

    @Query("""
        SELECT s.status AS status, COUNT(s) AS count
        FROM Submission s
        WHERE s.status <> com.dasigconnect.backend.model.entity.SubmissionStatus.draft
        GROUP BY s.status
        """)
    List<SubmissionStatusCount> countValidationStatuses();

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

    /**
     * Also bumps updatedAt -- a bulk JPQL UPDATE bypasses the entity's
     * @PreUpdate lifecycle callback, so without this the column would stay
     * at whatever it was before the claim (e.g. approval time), making it
     * useless for StaleSubmissionDetectorJob to detect a submission stuck in
     * `publishing` after a crash. This matters most for Fast-Track
     * submissions, which have no scheduledAt to check a cutoff against.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE Submission s
        SET s.status = :claimedStatus,
            s.updatedAt = :now
        WHERE s.id = :submissionId
          AND s.status = :expectedStatus
        """)
    int claimForPublishing(
            @Param("submissionId") UUID submissionId,
            @Param("expectedStatus") SubmissionStatus expectedStatus,
            @Param("now") Instant now,
            @Param("claimedStatus") SubmissionStatus claimedStatus);

    /**
     * UC-3.1: atomic reschedule for a Moderator, capped. {@code Submission} has
     * no {@code @Version}/optimistic locking, so a plain read-check-write on
     * {@code moderatorRescheduleCount} (as {@code SubmissionService.reschedule}
     * used to do) lets two concurrent requests for the same submission both pass
     * the cap check before either writes. Same idiom as
     * {@link #claimForPublishing} — the {@code WHERE} clause is the guard, and
     * the affected-row count tells the caller whether it actually won the claim.
     * Returns 0 (not 1) when the count was already at {@code maxCount} or the
     * status changed out from under the caller; either way, the caller should
     * treat that as "someone else changed this first," not silently proceed.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE Submission s
        SET s.scheduledAt = :newSlot,
            s.moderatorRescheduleCount = s.moderatorRescheduleCount + 1
        WHERE s.id = :submissionId
          AND s.status = com.dasigconnect.backend.model.entity.SubmissionStatus.scheduled
          AND s.moderatorRescheduleCount < :maxCount
        """)
    int claimModeratorReschedule(
            @Param("submissionId") UUID submissionId,
            @Param("newSlot") Instant newSlot,
            @Param("maxCount") int maxCount);

    /**
     * UC-3.1: atomic reschedule for an Admin — no cap, but still guarded on
     * {@code status = scheduled} so a concurrent status change (e.g. the post
     * started publishing) is caught rather than silently overwritten.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE Submission s
        SET s.scheduledAt = :newSlot
        WHERE s.id = :submissionId
          AND s.status = com.dasigconnect.backend.model.entity.SubmissionStatus.scheduled
        """)
    int claimAdminReschedule(
            @Param("submissionId") UUID submissionId,
            @Param("newSlot") Instant newSlot);

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
     * StaleSubmissionDetectorJob (GR-T9, added 2026-09-18): a Fast-Track
     * submission stuck in `publishing`/`direct_post_publishing` after a crash
     * mid-publish (FastTrackPublishingListener claimed it, then the app died
     * before markPublished/markFailed ran) has no scheduledAt to check
     * against a cutoff -- findMissedScheduledSubmissions above can never
     * match it. updatedAt is bumped by claimForPublishing's UPDATE at the
     * moment of the claim, so it's used here instead.
     */
    @Query("""
        SELECT s FROM Submission s
        WHERE s.status IN (
            com.dasigconnect.backend.model.entity.SubmissionStatus.publishing,
            com.dasigconnect.backend.model.entity.SubmissionStatus.direct_post_publishing
        )
        AND s.scheduledAt IS NULL
        AND s.updatedAt < :cutoff
        ORDER BY s.updatedAt ASC
        """)
    List<Submission> findStuckFastTrackPublishing(@Param("cutoff") Instant cutoff);

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

    /** T-07: Count upcoming scheduled posts for an institution in a time window. */
    @Query("""
        SELECT COUNT(s) FROM Submission s
        WHERE s.institution.id = :institutionId
          AND s.status IN (
              com.dasigconnect.backend.model.entity.SubmissionStatus.scheduled,
              com.dasigconnect.backend.model.entity.SubmissionStatus.direct_post_scheduled
          )
          AND s.scheduledAt BETWEEN :from AND :to
        """)
    long countUpcomingScheduledByInstitution(
            @Param("institutionId") UUID institutionId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /** T-07 / A6: Find historical published post titles for an institution. */
    @Query("""
        SELECT s.eventTitle FROM Submission s
        WHERE s.institution.id = :institutionId
          AND s.status IN (
              com.dasigconnect.backend.model.entity.SubmissionStatus.published,
              com.dasigconnect.backend.model.entity.SubmissionStatus.published_manual
          )
        ORDER BY s.createdAt DESC
        LIMIT 10
        """)
    List<String> findRecentAndHistoricalPostTitles(@Param("institutionId") UUID institutionId);

    /** T-07 / A6: Find distinct categories used recently across other partner institutions. */
    @Query("""
        SELECT DISTINCT s.category FROM Submission s
        WHERE s.institution.id != :institutionId
          AND s.category IS NOT NULL
          AND s.category != ''
          AND s.status IN (
              com.dasigconnect.backend.model.entity.SubmissionStatus.published,
              com.dasigconnect.backend.model.entity.SubmissionStatus.published_manual,
              com.dasigconnect.backend.model.entity.SubmissionStatus.scheduled
          )
          AND s.createdAt >= :since
        """)
    List<String> findRecentCategoriesFromOtherInstitutions(
            @Param("institutionId") UUID institutionId,
            @Param("since") Instant since);
}

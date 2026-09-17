package com.dasigconnect.backend.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dasigconnect.backend.event.PostPublishedManualEvent;
import com.dasigconnect.backend.event.SubmissionFastTrackRetryEvent;
import com.dasigconnect.backend.event.SubmissionRescheduledEvent;
import com.dasigconnect.backend.exception.GuardRailViolationException;
import com.dasigconnect.backend.exception.SubmissionNotFoundException;
import com.dasigconnect.backend.model.dto.guardrail.GuardRailResult;
import com.dasigconnect.backend.model.dto.resolution.ManualPublishCompleteDto;
import com.dasigconnect.backend.model.dto.submission.RescheduleRequestDto;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionStatus;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.security.JwtUserDetails;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * UC-3.4 manual publishing fallback workflow.
 *
 * Eligible submissions: PUBLISH_FAILED (automated retries exhausted) and
 * SCHEDULED (only when the Facebook Page token is expired/failed, GR-T4 active).
 *
 * Flow: Admin calls start() → publishes on Facebook → calls complete().
 * Abandonment: AbandonmentDetectorJob calls clearAbandoned() for sessions open > 2 hours.
 */
@Service
@Transactional
public class ManualPublishingService {

    private static final Logger log = LoggerFactory.getLogger(ManualPublishingService.class);
    private static final String FACEBOOK_URL_PREFIX = "https://www.facebook.com/";

    private final SubmissionRepository submissionRepository;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final GuardRailService guardRailService;
    private final SlotReservationService slotReservationService;

    @PersistenceContext
    private EntityManager entityManager;

    public ManualPublishingService(
            SubmissionRepository submissionRepository,
            AuditLogService auditLogService,
            ApplicationEventPublisher eventPublisher,
            GuardRailService guardRailService,
            SlotReservationService slotReservationService) {
        this.submissionRepository = submissionRepository;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
        this.guardRailService = guardRailService;
        this.slotReservationService = slotReservationService;
    }

    public void start(UUID submissionId, JwtUserDetails admin) {
        Submission s = loadEligibleForManualPublish(submissionId);
        s.setManualPublishStartedAt(Instant.now());
        submissionRepository.save(s);

        auditLogService.record(
                entityManager.getReference(User.class, admin.userId()),
                "MANUAL_PUBLISH_STARTED",
                null, null,
                submissionId,
                Map.of("submissionStatus", s.getStatus().name())
        );

        log.info("Admin {} started manual publish for submission {}.", admin.userId(), submissionId);
    }

    public void complete(UUID submissionId, ManualPublishCompleteDto dto, JwtUserDetails admin) {
        Submission s = loadEligibleForManualPublish(submissionId);

        if (s.getManualPublishStartedAt() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Manual publish session not started. Call /start first.");
        }

        if (dto.getPostUrl() != null && !dto.getPostUrl().isBlank()) {
            if (!dto.getPostUrl().startsWith(FACEBOOK_URL_PREFIX)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Post URL must start with " + FACEBOOK_URL_PREFIX);
            }
        }

        String priorStatus = s.getStatus().name();
        Instant scheduledAt = s.getScheduledAt();
        Instant publishedAt = Instant.now();

        s.setStatus(SubmissionStatus.published_manual);
        s.setPublishedAt(publishedAt);
        s.setPublishedManualUrl(dto.getPostUrl());
        s.setPublishedManualNotes(dto.getNotes());
        s.setManualPublishStartedAt(null);
        submissionRepository.save(s);

        auditLogService.record(
                entityManager.getReference(User.class, admin.userId()),
                "MANUAL_PUBLISH_COMPLETE",
                null, null,
                submissionId,
                Map.of(
                    "priorStatus",   priorStatus,
                    "scheduledAt",   scheduledAt != null ? scheduledAt.toString() : "",
                    "publishedAt",   publishedAt.toString(),
                    "postUrl",       dto.getPostUrl() != null ? dto.getPostUrl() : "",
                    "notes",         dto.getNotes() != null ? dto.getNotes() : ""
                )
        );

        eventPublisher.publishEvent(new PostPublishedManualEvent(s, dto.getPostUrl()));
        log.info("Admin {} completed manual publish for submission {}.", admin.userId(), submissionId);
    }

    public void cancel(UUID submissionId, JwtUserDetails admin) {
        Submission s = loadEligibleForManualPublish(submissionId);
        String previousStatus = s.getStatus().name();
        s.setManualPublishStartedAt(null);
        submissionRepository.save(s);

        auditLogService.record(
                entityManager.getReference(User.class, admin.userId()),
                "MANUAL_PUBLISH_CANCELLED",
                null, null,
                submissionId,
                Map.of("submissionStatus", previousStatus)
        );

        log.info("Admin {} cancelled manual publish for submission {}.", admin.userId(), submissionId);
    }

    /**
     * Retries a PUBLISH_FAILED submission exactly as it was — no mode change.
     * For a Standard submission this just re-enters PublishingSchedulerJob's
     * window (its scheduledAt is unchanged). Fast-Track submissions never have
     * a scheduledAt, so that cron would never pick them up on its own —
     * SubmissionFastTrackRetryEvent triggers the same immediate-publish path
     * Fast-Track approval normally uses instead.
     */
    public void retry(UUID submissionId, JwtUserDetails admin) {
        Submission s = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new SubmissionNotFoundException(submissionId));
        if (s.getStatus() != SubmissionStatus.publish_failed) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only PUBLISH_FAILED submissions can be retried.");
        }
        s.setStatus(SubmissionStatus.scheduled);
        s.setRetryCount(0);
        s.setManualPublishStartedAt(null);
        submissionRepository.save(s);
        log.info("Admin {} queued retry for submission {}.", admin.userId(), submissionId);

        if (s.isFastTrack()) {
            eventPublisher.publishEvent(new SubmissionFastTrackRetryEvent(s));
        }
    }

    /**
     * Admin-only: overrides a PUBLISH_FAILED submission that was Scheduled into
     * Live Event and retries it immediately — the reverse of the mode change
     * {@link #retryWithNewSchedule} makes. A Moderator can only retry a failed
     * publish in its original mode ({@link #retry}) or fall back to Manual
     * Publish; changing the mode either direction is Admin-only.
     */
    public void retryAsLiveOverride(UUID submissionId, JwtUserDetails admin) {
        if (!"admin".equalsIgnoreCase(admin.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only an Administrator can change a Scheduled submission to Live Event.");
        }
        Submission s = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new SubmissionNotFoundException(submissionId));
        if (s.getStatus() != SubmissionStatus.publish_failed) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only PUBLISH_FAILED submissions can be retried.");
        }
        if (s.isFastTrack()) {
            // Already Live — nothing to override; the caller should use retry() instead.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This submission is already a Live Event.");
        }

        Instant originalSlot = s.getScheduledAt();
        s.setFastTrack(true);
        s.setScheduledAt(null);
        slotReservationService.release(submissionId);
        s.setStatus(SubmissionStatus.scheduled);
        s.setRetryCount(0);
        s.setManualPublishStartedAt(null);
        submissionRepository.save(s);

        auditLogService.record(
                entityManager.getReference(User.class, admin.userId()),
                "PUBLISH_FAILED_RETRY_MODE_OVERRIDE_TO_LIVE",
                null, null,
                submissionId,
                Map.of("originalSlot", originalSlot != null ? originalSlot.toString() : ""));

        log.info("Admin {} overrode submission {} to Live Event on retry.", admin.userId(), submissionId);
        eventPublisher.publishEvent(new SubmissionFastTrackRetryEvent(s));
    }

    /**
     * A8: retries a PUBLISH_FAILED or MISSED_REVIEW submission on a newly chosen
     * slot instead of the original one. Guard rails are re-evaluated against the
     * new slot; a hard violation blocks the move unless the admin supplies an
     * overrideReason.
     *
     * <ul>
     *   <li>PUBLISH_FAILED → SCHEDULED (re-enters the automated publishing flow).</li>
     *   <li>MISSED_REVIEW → PENDING (re-enters the Approval Workflow so it still
     *       gets a proper review rather than skipping to publication).</li>
     * </ul>
     */
    public void retryWithNewSchedule(UUID submissionId, RescheduleRequestDto dto, JwtUserDetails admin) {
        Submission s = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new SubmissionNotFoundException(submissionId));
        boolean missedReview = s.getStatus() == SubmissionStatus.missed_review;
        if (s.getStatus() != SubmissionStatus.publish_failed && !missedReview) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only PUBLISH_FAILED or MISSED_REVIEW submissions can be retried with a new schedule.");
        }

        // Giving a submission a specific future time is inherently incompatible
        // with "Live/immediate" — this always converts it to Scheduled. That's a
        // deliberate publishing-mode override, so only an Admin may do it here too
        // (same rule as the Review Queue inline-edit override) — a Moderator can
        // still freely reschedule an already-Scheduled submission with no mode
        // change happening.
        boolean wasFastTrack = s.isFastTrack();
        if (wasFastTrack && !"admin".equalsIgnoreCase(admin.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only an Administrator can change a Live Event submission to a scheduled time.");
        }

        Instant originalSlot = s.getScheduledAt();
        Instant newSlot = dto.getScheduledAt();

        GuardRailResult guardRailResult = guardRailService.validate(s.getInstitution().getId(), newSlot, s.getId());
        if (guardRailResult.isBlocked()) {
            if (!"admin".equalsIgnoreCase(admin.role())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "That time is blocked by a guard rail. Only an administrator can retry onto a blocked slot.");
            }
            if (dto.getOverrideReason() == null || dto.getOverrideReason().isBlank()) {
                throw new GuardRailViolationException(guardRailResult.getHardBlocks());
            }
            auditLogService.record(
                    entityManager.getReference(User.class, admin.userId()),
                    "MANUAL_PUBLISH_RETRY_OVERRIDE",
                    null, null,
                    submissionId,
                    Map.of(
                        "originalSlot", originalSlot != null ? originalSlot.toString() : "",
                        "newSlot", newSlot.toString(),
                        "overrideReason", dto.getOverrideReason(),
                        "violations", guardRailResult.getHardBlocks().toString()
                    )
            );
        }

        if (missedReview) {
            // Re-enter the Approval Workflow rather than the publishing flow —
            // originalScheduledAt/moderatorRescheduleCount get re-baselined the
            // normal way when this is approved again (ValidationService.approve).
            slotReservationService.reserve(submissionId, s.getInstitution().getId(), newSlot);
            s.setStatus(SubmissionStatus.pending);
            s.setSubmittedAt(Instant.now());
        } else {
            slotReservationService.reserveLockedSlot(submissionId, s.getInstitution().getId(), newSlot);
            s.setStatus(SubmissionStatus.scheduled);
            // This retry establishes a fresh baseline for UC-3.1's Moderator
            // reschedule cap — without resetting these, a submission that had
            // already used up its 2 calendar reschedules (or drifted far from its
            // original slot) before failing to publish would come back from a
            // successful retry still capped out or immediately outside the
            // 1-day window of a now-irrelevant old slot.
            s.setOriginalScheduledAt(newSlot);
            s.setModeratorRescheduleCount(0);
        }
        s.setScheduledAt(newSlot);
        // A schedule and Live/Fast-Track are mutually exclusive (see
        // SubmissionService.applySubmissionEdits) — giving this a time means it
        // is no longer Live, regardless of what it was before.
        if (wasFastTrack) {
            s.setFastTrack(false);
        }
        s.setRetryCount(0);
        s.setManualPublishStartedAt(null);
        submissionRepository.save(s);

        auditLogService.record(
                entityManager.getReference(User.class, admin.userId()),
                missedReview ? "MISSED_REVIEW_RETRY_NEW_SCHEDULE" : "MANUAL_PUBLISH_RETRY_NEW_SCHEDULE",
                null, null,
                submissionId,
                Map.of(
                    "originalSlot", originalSlot != null ? originalSlot.toString() : "",
                    "newSlot", newSlot.toString(),
                    "publishingModeChanged", String.valueOf(wasFastTrack)
                )
        );

        eventPublisher.publishEvent(new SubmissionRescheduledEvent(s, originalSlot, newSlot));
        log.info("Admin {} retried submission {} on new slot {}.", admin.userId(), submissionId, newSlot);
    }

    /** Called by AbandonmentDetectorJob — clears sessions open longer than 2 hours. */
    public void clearAbandoned(Submission s) {
        Instant startedAt = s.getManualPublishStartedAt();
        Instant abandonedAt = Instant.now();

        s.setManualPublishStartedAt(null);
        s.setLastManualPublishAbandonedAt(abandonedAt);
        submissionRepository.save(s);

        auditLogService.recordSystemAction(
                "MANUAL_PUBLISH_ABANDONED",
                s.getId(),
                Map.of(
                    "submissionStatus", s.getStatus().name(),
                    "startedAt",        startedAt != null ? startedAt.toString() : "",
                    "abandonedAt",      abandonedAt.toString()
                )
        );

        log.warn("Cleared abandoned manual publish session for submission {}.", s.getId());
    }

    private Submission loadEligibleForManualPublish(UUID submissionId) {
        Submission s = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new SubmissionNotFoundException(submissionId));
        if (s.getStatus() != SubmissionStatus.publish_failed
                && s.getStatus() != SubmissionStatus.scheduled) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only PUBLISH_FAILED or SCHEDULED submissions are eligible for manual publishing.");
        }
        return s;
    }
}

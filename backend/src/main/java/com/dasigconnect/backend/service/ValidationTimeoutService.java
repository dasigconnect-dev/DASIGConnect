package com.dasigconnect.backend.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dasigconnect.backend.event.RevisionRequestedEvent;
import com.dasigconnect.backend.event.SubmissionApprovedEvent;
import com.dasigconnect.backend.event.SubmissionRejectedEvent;
import com.dasigconnect.backend.model.dto.exception.TimeoutEscalationDto;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionStatus;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.repository.ReviewLockRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.security.JwtUserDetails;

/**
 * UC-3.5 Category B — Validation Timeout Escalation.
 *
 * Handles PENDING/IN_REVIEW submissions whose scheduled time falls within 30 minutes.
 * Three resolution paths: approve as fallback, defer to NEEDS_REVISION, or reject on behalf.
 */
@Service
@Transactional
public class ValidationTimeoutService {

    private static final Logger log = LoggerFactory.getLogger(ValidationTimeoutService.class);

    private static final Set<String> VALID_REJECTION_CODES = Set.of(
            "INCOMPLETE_CONTENT", "INAPPROPRIATE_CONTENT", "WRONG_FORMAT",
            "DUPLICATE_EVENT", "WRONG_INSTITUTION", "OTHER");

    private static final String DEFERRAL_REMARK =
            "Publication deferred by Administrator due to validation timeout. "
            + "The original time slot has been released. "
            + "Please select a new publication time and resubmit as soon as possible.";

    private final SubmissionRepository submissionRepository;
    private final UserRepository userRepository;
    private final ReviewLockRepository reviewLockRepository;
    private final SlotReservationService slotReservationService;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;

    public ValidationTimeoutService(
            SubmissionRepository submissionRepository,
            UserRepository userRepository,
            ReviewLockRepository reviewLockRepository,
            SlotReservationService slotReservationService,
            AuditLogService auditLogService,
            ApplicationEventPublisher eventPublisher) {
        this.submissionRepository = submissionRepository;
        this.userRepository = userRepository;
        this.reviewLockRepository = reviewLockRepository;
        this.slotReservationService = slotReservationService;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
    }

    /** Returns all PENDING/IN_REVIEW submissions whose scheduledAt is within the next 30 minutes. */
    @Transactional(readOnly = true)
    public List<TimeoutEscalationDto> getEscalated() {
        Instant now = Instant.now();
        Instant window = now.plus(30, ChronoUnit.MINUTES);
        return submissionRepository.findEscalatedForTimeout(now, window)
                .stream()
                .map(TimeoutEscalationDto::from)
                .toList();
    }

    /**
     * B(i) — Approve as fallback Validator.
     * Transitions PENDING/IN_REVIEW → SCHEDULED, locks slot, releases review lock if held.
     */
    public void approve(UUID submissionId, JwtUserDetails admin) {
        Submission s = loadEscalated(submissionId);
        Instant originalSlot = s.getScheduledAt();

        if (s.getStatus() == SubmissionStatus.in_review) {
            reviewLockRepository.deleteBySubmissionId(submissionId);
        }
        slotReservationService.confirm(submissionId);

        s.setStatus(SubmissionStatus.scheduled);
        submissionRepository.save(s);

        User adminUser = loadUser(admin.userId());
        auditLogService.record(adminUser, "TIMEOUT_APPROVED_AS_FALLBACK", null, null, submissionId,
                Map.of("originalSlot", originalSlot != null ? originalSlot.toString() : "",
                       "actor", adminUser.getEmail()));

        eventPublisher.publishEvent(new SubmissionApprovedEvent(s));
        log.info("Admin {} approved escalated submission {} as fallback validator.", admin.userId(), submissionId);
    }

    /**
     * B(ii) — Defer: PENDING/IN_REVIEW → NEEDS_REVISION, slot released, system remark added.
     */
    public void defer(UUID submissionId, JwtUserDetails admin) {
        Submission s = loadEscalated(submissionId);
        Instant originalSlot = s.getScheduledAt();

        if (s.getStatus() == SubmissionStatus.in_review) {
            reviewLockRepository.deleteBySubmissionId(submissionId);
        }
        slotReservationService.release(submissionId);

        s.setStatus(SubmissionStatus.needs_revision);
        s.setValidatorRemarks(DEFERRAL_REMARK);
        submissionRepository.save(s);

        User adminUser = loadUser(admin.userId());
        auditLogService.record(adminUser, "TIMEOUT_DEFERRED", null, null, submissionId,
                Map.of("originalSlot", originalSlot != null ? originalSlot.toString() : "",
                       "actor", adminUser.getEmail()));

        eventPublisher.publishEvent(new RevisionRequestedEvent(s, DEFERRAL_REMARK));
        log.info("Admin {} deferred escalated submission {}.", admin.userId(), submissionId);
    }

    /**
     * B(iii) — Reject on behalf: requires a valid reason code; slot released.
     */
    public void reject(UUID submissionId, String reasonCode, String notes, JwtUserDetails admin) {
        validateRejectionCode(reasonCode, notes);
        Submission s = loadEscalated(submissionId);

        if (s.getStatus() == SubmissionStatus.in_review) {
            reviewLockRepository.deleteBySubmissionId(submissionId);
        }
        slotReservationService.release(submissionId);

        String fullReason = buildRejectionReason(reasonCode, notes);
        s.setStatus(SubmissionStatus.rejected);
        s.setRejectionReason(fullReason);
        submissionRepository.save(s);

        User adminUser = loadUser(admin.userId());
        auditLogService.record(adminUser, "TIMEOUT_REJECTED_ON_BEHALF", null, null, submissionId,
                Map.of("reasonCode", reasonCode,
                       "actor", adminUser.getEmail()));

        eventPublisher.publishEvent(new SubmissionRejectedEvent(s, fullReason));
        log.info("Admin {} rejected escalated submission {} for reason {}.", admin.userId(), submissionId, reasonCode);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Submission loadEscalated(UUID submissionId) {
        Submission s = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found."));
        if (s.getStatus() != SubmissionStatus.pending && s.getStatus() != SubmissionStatus.in_review) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Submission is not in an escalated state (must be PENDING or IN_REVIEW).");
        }
        return s;
    }

    private void validateRejectionCode(String reasonCode, String notes) {
        if (reasonCode == null || !VALID_REJECTION_CODES.contains(reasonCode)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Invalid rejection reason code. Valid codes: " + String.join(", ", VALID_REJECTION_CODES));
        }
        if ("OTHER".equals(reasonCode) && (notes == null || notes.trim().isEmpty())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Notes are required when rejection reason is OTHER.");
        }
    }

    private String buildRejectionReason(String reasonCode, String notes) {
        if (notes != null && !notes.trim().isEmpty()) {
            return reasonCode + ": " + notes.trim();
        }
        return reasonCode;
    }

    private User loadUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Authenticated user not found."));
    }
}

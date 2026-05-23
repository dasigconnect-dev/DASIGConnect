package com.dasigconnect.backend.service;

import java.util.List;
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
import com.dasigconnect.backend.model.dto.submission.SubmissionSummaryDto;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionStatus;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.entity.ValidationAction;
import com.dasigconnect.backend.model.entity.ValidationLog;
import com.dasigconnect.backend.repository.SubmissionMediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.repository.ValidationLogRepository;
import com.dasigconnect.backend.security.JwtUserDetails;

@Service
@Transactional
public class ValidationService {

    private static final Logger log = LoggerFactory.getLogger(ValidationService.class);

    // BR-VAL-03 rejection reason codes
    private static final Set<String> VALID_REJECTION_CODES = Set.of(
            "INCOMPLETE_CONTENT", "INAPPROPRIATE_CONTENT", "WRONG_FORMAT",
            "DUPLICATE_EVENT", "WRONG_INSTITUTION", "OTHER");

    private final SubmissionRepository submissionRepository;
    private final SubmissionMediaAssetRepository submissionMediaAssetRepository;
    private final ValidationLogRepository validationLogRepository;
    private final ReviewLockService reviewLockService;
    private final SlotReservationService slotReservationService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ValidationService(
            SubmissionRepository submissionRepository,
            SubmissionMediaAssetRepository submissionMediaAssetRepository,
            ValidationLogRepository validationLogRepository,
            ReviewLockService reviewLockService,
            SlotReservationService slotReservationService,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher) {
        this.submissionRepository = submissionRepository;
        this.submissionMediaAssetRepository = submissionMediaAssetRepository;
        this.validationLogRepository = validationLogRepository;
        this.reviewLockService = reviewLockService;
        this.slotReservationService = slotReservationService;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Returns the validation queue for the caller's institution.
     * PENDING + IN_REVIEW submissions sorted by scheduledAt ASC (SRS Main Flow step 2).
     */
    @Transactional(readOnly = true)
    public List<SubmissionSummaryDto> getQueue(JwtUserDetails caller) {
        List<Submission> submissions = "administrator".equals(caller.role())
                ? submissionRepository.findValidationQueue()
                : submissionRepository.findValidationQueueByInstitution(caller.institutionId());
        return submissions.stream()
                .map(s -> SubmissionSummaryDto.from(s,
                        submissionMediaAssetRepository.countBySubmissionId(s.getId())))
                .toList();
    }

    /**
     * Approves a submission: transitions to SCHEDULED, confirms slot, releases lock.
     * GR-H5: self-review blocked.
     */
    public void approve(UUID submissionId, JwtUserDetails caller) {
        Submission submission = loadSubmissionInScope(submissionId, caller);
        assertNotSelfReview(submission, caller);
        assertReviewableStatus(submission);
        reviewLockService.assertCallerHoldsLock(submissionId, caller);

        submission.setStatus(SubmissionStatus.scheduled);
        submissionRepository.save(submission);

        slotReservationService.confirm(submissionId);
        reviewLockService.release(submissionId, caller);

        User validator = loadUser(caller.userId());
        logAction(submission, validator, ValidationAction.approved, null, null);

        eventPublisher.publishEvent(new SubmissionApprovedEvent(submission));
        log.info("Submission approved: submission={} validator={}", submissionId, caller.userId());
    }

    /**
     * Requests revision: transitions to NEEDS_REVISION, releases slot and lock.
     * BR-VAL-02: remarks must be 10–1000 characters.
     * GR-H5: self-review blocked.
     */
    public void requestRevision(UUID submissionId, String remarks, JwtUserDetails caller) {
        validateRemarks(remarks);
        Submission submission = loadSubmissionInScope(submissionId, caller);
        assertNotSelfReview(submission, caller);
        assertReviewableStatus(submission);
        reviewLockService.assertCallerHoldsLock(submissionId, caller);

        submission.setStatus(SubmissionStatus.needs_revision);
        submission.setValidatorRemarks(remarks);
        submissionRepository.save(submission);

        slotReservationService.release(submissionId);
        reviewLockService.release(submissionId, caller);

        User validator = loadUser(caller.userId());
        logAction(submission, validator, ValidationAction.needs_revision, remarks, null);

        eventPublisher.publishEvent(new RevisionRequestedEvent(submission, remarks));
        log.info("Revision requested: submission={} validator={}", submissionId, caller.userId());
    }

    /**
     * Rejects a submission: transitions to REJECTED, releases slot and lock.
     * BR-VAL-03: valid reason code required; OTHER requires written notes.
     * GR-H5: self-review blocked.
     */
    public void reject(UUID submissionId, String reasonCode, String notes, JwtUserDetails caller) {
        validateRejectionCode(reasonCode, notes);
        Submission submission = loadSubmissionInScope(submissionId, caller);
        assertNotSelfReview(submission, caller);
        assertReviewableStatus(submission);
        reviewLockService.assertCallerHoldsLock(submissionId, caller);

        String fullReason = buildRejectionReason(reasonCode, notes);
        submission.setStatus(SubmissionStatus.rejected);
        submission.setRejectionReason(fullReason);
        submissionRepository.save(submission);

        slotReservationService.release(submissionId);
        reviewLockService.release(submissionId, caller);

        User validator = loadUser(caller.userId());
        logAction(submission, validator, ValidationAction.rejected, null, fullReason);

        eventPublisher.publishEvent(new SubmissionRejectedEvent(submission, fullReason));
        log.info("Submission rejected: submission={} reason={} validator={}", submissionId, reasonCode, caller.userId());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void validateRemarks(String remarks) {
        if (remarks == null || remarks.trim().length() < 10 || remarks.trim().length() > 1000) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Remarks must be between 10 and 1000 characters.");
        }
    }

    private void validateRejectionCode(String reasonCode, String notes) {
        if (reasonCode == null || !VALID_REJECTION_CODES.contains(reasonCode)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Invalid rejection reason code. Valid codes: "
                            + String.join(", ", VALID_REJECTION_CODES));
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

    private Submission loadSubmissionInScope(UUID submissionId, JwtUserDetails caller) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Submission not found."));

        if (!"administrator".equals(caller.role())
                && !submission.getInstitution().getId().equals(caller.institutionId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found.");
        }
        return submission;
    }

    private void assertNotSelfReview(Submission submission, JwtUserDetails caller) {
        if (submission.getContributor().getId().equals(caller.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You cannot review your own submission.");
        }
    }

    private void assertReviewableStatus(Submission submission) {
        if (submission.getStatus() != SubmissionStatus.pending
                && submission.getStatus() != SubmissionStatus.in_review) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Submission is not in a reviewable state.");
        }
    }

    private User loadUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Authenticated user not found."));
    }

    private void logAction(Submission submission, User validator,
            ValidationAction action, String remarks, String rejectionReason) {
        ValidationLog entry = new ValidationLog();
        entry.setSubmission(submission);
        entry.setValidator(validator);
        entry.setAction(action);
        entry.setRemarks(remarks);
        entry.setRejectionReason(rejectionReason);
        validationLogRepository.save(entry);
    }
}

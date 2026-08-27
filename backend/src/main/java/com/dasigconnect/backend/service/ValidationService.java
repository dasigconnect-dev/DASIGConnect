package com.dasigconnect.backend.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dasigconnect.backend.event.RevisionRequestedEvent;
import com.dasigconnect.backend.event.SubmissionApprovedEvent;
import com.dasigconnect.backend.event.SubmissionRejectedEvent;
import com.dasigconnect.backend.model.dto.submission.SubmissionSummaryDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionUpdateDto;
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
import com.fasterxml.jackson.databind.ObjectMapper;

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
    private final SubmissionService submissionService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public ValidationService(
            SubmissionRepository submissionRepository,
            SubmissionMediaAssetRepository submissionMediaAssetRepository,
            ValidationLogRepository validationLogRepository,
            ReviewLockService reviewLockService,
            SlotReservationService slotReservationService,
            SubmissionService submissionService,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper) {
        this.submissionRepository = submissionRepository;
        this.submissionMediaAssetRepository = submissionMediaAssetRepository;
        this.validationLogRepository = validationLogRepository;
        this.reviewLockService = reviewLockService;
        this.slotReservationService = slotReservationService;
        this.submissionService = submissionService;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns the network-wide approval queue: PENDING + IN_REVIEW submissions
     * sorted by scheduledAt ASC (UC-2.4 Main Flow step 2). Administrator and
     * Super Administrator accounts are both network-wide roles.
     */
    @Transactional(readOnly = true)
    public List<SubmissionSummaryDto> getQueue(JwtUserDetails caller) {
        return submissionRepository.findValidationQueue().stream()
                .map(s -> SubmissionSummaryDto.from(s,
                        submissionMediaAssetRepository.countBySubmissionId(s.getId())))
                .toList();
    }

    /**
     * Returns the network-wide approval history: all non-draft submissions that are no
     * longer in the active queue (i.e. not PENDING or IN_REVIEW), sorted newest scheduled first.
     */
    @Transactional(readOnly = true)
    public List<SubmissionSummaryDto> getHistory(JwtUserDetails caller) {
        return submissionRepository.findValidationHistory().stream()
                .map(s -> SubmissionSummaryDto.from(s,
                        submissionMediaAssetRepository.countBySubmissionId(s.getId())))
                .toList();
    }

    /**
     * Approves a submission: transitions to SCHEDULED, confirms slot, releases lock.
     * Live Event Fast-Track submissions never have a slot reservation (see
     * SubmissionService.create()), so slot confirmation is skipped for them —
     * publishing immediately is handled downstream by FastTrackPublishingListener
     * reacting to SubmissionApprovedEvent.
     * A5: self-review is allowed but distinctly flagged in the audit log.
     */
    public void approve(UUID submissionId, JwtUserDetails caller) {
        Submission submission = loadSubmissionInScope(submissionId, caller);
        boolean selfReview = isSelfReview(submission, caller);
        assertReviewableStatus(submission);
        reviewLockService.assertCallerHoldsLock(submissionId, caller);

        String sessionEditDiff = combinedSessionEditDiff(submissionId);
        boolean edited = sessionEditDiff != null;

        submission.setStatus(SubmissionStatus.scheduled);
        submissionRepository.save(submission);

        if (!submission.isFastTrack()) {
            slotReservationService.confirm(submissionId);
        }
        reviewLockService.release(submissionId, caller);

        // The terminal action is always `approved`; when the submission was edited
        // during this review session the combined before/after diff is attached to
        // the same log entry (A10 — "Edited by Admin"), which is what marks it as an
        // edited approval for governance and the edit-&-approve-rate KPI.
        User validator = loadUser(caller.userId());
        logAction(submission, validator, ValidationAction.approved,
                null, null, selfReview, submission.isFastTrack(), sessionEditDiff);

        eventPublisher.publishEvent(new SubmissionApprovedEvent(submission, edited));
        log.info("Submission approved (fastTrack={}, edited={}): submission={} validator={}",
                submission.isFastTrack(), edited, submissionId, caller.userId());
    }

    /**
     * A9: applies a direct inline edit to any editable field of a submission that is
     * currently IN_REVIEW. The submission stays IN_REVIEW — the Administrator must
     * still select a terminal action afterwards. Each edit records its own
     * before/after diff in the audit log (A10), so repeated edits within a review
     * session are all traceable.
     * A5: self-review is allowed but distinctly flagged in the audit log.
     */
    public void edit(UUID submissionId, SubmissionUpdateDto dto, JwtUserDetails caller) {
        Submission submission = loadSubmissionInScope(submissionId, caller);
        boolean selfReview = isSelfReview(submission, caller);
        assertReviewableStatus(submission);
        reviewLockService.assertCallerHoldsLock(submissionId, caller);

        Map<String, Object> before = snapshotEditableFields(submission);
        submission = submissionService.applySubmissionEdits(submission, dto);
        submissionService.assertContentComplete(submission);
        String editDiff = buildEditDiff(before, snapshotEditableFields(submission));

        // Keep the submission IN_REVIEW — no terminal transition here.
        User validator = loadUser(caller.userId());
        logAction(submission, validator, ValidationAction.edited, null, null,
                selfReview, submission.isFastTrack(), editDiff);

        log.info("Submission edited in review (changed={}): submission={} validator={}",
                editDiff != null, submissionId, caller.userId());
    }

    /**
     * Requests revision: transitions to NEEDS_REVISION, releases slot and lock.
     * BR-VAL-02: remarks must be 10–1000 characters.
     * A5: self-review is allowed but distinctly flagged in the audit log.
     */
    public void requestRevision(UUID submissionId, String remarks, JwtUserDetails caller) {
        validateRemarks(remarks);
        Submission submission = loadSubmissionInScope(submissionId, caller);
        boolean selfReview = isSelfReview(submission, caller);
        assertReviewableStatus(submission);
        reviewLockService.assertCallerHoldsLock(submissionId, caller);

        String sessionEditDiff = combinedSessionEditDiff(submissionId);

        submission.setStatus(SubmissionStatus.needs_revision);
        submission.setValidatorRemarks(remarks);
        submissionRepository.save(submission);

        slotReservationService.release(submissionId);
        reviewLockService.release(submissionId, caller);

        User validator = loadUser(caller.userId());
        logAction(submission, validator, ValidationAction.needs_revision, remarks, null,
                selfReview, submission.isFastTrack(), sessionEditDiff);

        eventPublisher.publishEvent(new RevisionRequestedEvent(submission, remarks));
        log.info("Revision requested: submission={} validator={}", submissionId, caller.userId());
    }

    /**
     * Rejects a submission: transitions to REJECTED, releases slot and lock.
     * BR-VAL-03: valid reason code required; OTHER requires written notes.
     * A5: self-review is allowed but distinctly flagged in the audit log.
     */
    public void reject(UUID submissionId, String reasonCode, String notes, JwtUserDetails caller) {
        validateRejectionCode(reasonCode, notes);
        Submission submission = loadSubmissionInScope(submissionId, caller);
        boolean selfReview = isSelfReview(submission, caller);
        assertReviewableStatus(submission);
        reviewLockService.assertCallerHoldsLock(submissionId, caller);

        String sessionEditDiff = combinedSessionEditDiff(submissionId);
        String fullReason = buildRejectionReason(reasonCode, notes);
        submission.setStatus(SubmissionStatus.rejected);
        submission.setRejectionReason(fullReason);
        submissionRepository.save(submission);

        slotReservationService.release(submissionId);
        reviewLockService.release(submissionId, caller);

        User validator = loadUser(caller.userId());
        logAction(submission, validator, ValidationAction.rejected, null, fullReason,
                selfReview, submission.isFastTrack(), sessionEditDiff);

        eventPublisher.publishEvent(new SubmissionRejectedEvent(submission, fullReason));
        log.info("Submission rejected: submission={} reason={} validator={}", submissionId, reasonCode, caller.userId());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void validateRemarks(String remarks) {
        if (remarks == null || remarks.trim().length() < 10 || remarks.trim().length() > 1000) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(422),
                    "Remarks must be between 10 and 1000 characters.");
        }
    }

    private void validateRejectionCode(String reasonCode, String notes) {
        if (reasonCode == null || !VALID_REJECTION_CODES.contains(reasonCode)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(422),
                    "Invalid rejection reason code. Valid codes: "
                            + String.join(", ", VALID_REJECTION_CODES));
        }
        if ("OTHER".equals(reasonCode) && (notes == null || notes.trim().isEmpty())) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(422),
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
        // Administrator and Super Administrator are both network-wide roles, so any
        // submission is in scope for review — no institution comparison is needed.
        return submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Submission not found."));
    }

    private boolean isSelfReview(Submission submission, JwtUserDetails caller) {
        return submission.getContributor().getId().equals(caller.userId());
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

    private Map<String, Object> snapshotEditableFields(Submission submission) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("eventTitle", submission.getEventTitle());
        snapshot.put("eventDate", submission.getEventDate());
        snapshot.put("caption", submission.getCaption());
        snapshot.put("description", submission.getDescription());
        snapshot.put("category", submission.getCategory());
        snapshot.put("tags", submission.getTags());
        snapshot.put("scheduledAt", submission.getScheduledAt());
        return snapshot;
    }

    /** Returns a JSON diff of changed fields, or null if nothing actually changed. */
    private String buildEditDiff(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> diff = new LinkedHashMap<>();
        for (String field : before.keySet()) {
            Object oldValue = before.get(field);
            Object newValue = after.get(field);
            if (!java.util.Objects.equals(oldValue, newValue)) {
                diff.put(field, Map.of(
                        "from", oldValue == null ? "" : oldValue,
                        "to", newValue == null ? "" : newValue));
            }
        }
        if (diff.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(diff);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Failed to serialize edit diff, storing null: {}", e.getMessage());
            return null;
        }
    }

    /**
     * A10: aggregates the before/after diffs of every standalone {@code edited}
     * action taken since the current review lock was acquired into one combined
     * diff, so a terminal action (approve/revise/reject) records the full picture
     * of what the Administrator changed. Returns null when no edit happened this
     * session.
     */
    private String combinedSessionEditDiff(UUID submissionId) {
        List<ValidationLog> logs = validationLogRepository
                .findBySubmissionIdOrderByCreatedAtAsc(submissionId);

        int lastLockIndex = -1;
        for (int i = 0; i < logs.size(); i++) {
            if (logs.get(i).getAction() == ValidationAction.lock_acquired) {
                lastLockIndex = i;
            }
        }

        Map<String, Map<String, Object>> combined = new LinkedHashMap<>();
        for (int i = lastLockIndex + 1; i < logs.size(); i++) {
            ValidationLog entry = logs.get(i);
            if (entry.getAction() != ValidationAction.edited || entry.getEditDiff() == null) {
                continue;
            }
            try {
                Map<String, Map<String, Object>> diff = objectMapper.readValue(
                        entry.getEditDiff(),
                        new com.fasterxml.jackson.core.type.TypeReference<>() {});
                for (Map.Entry<String, Map<String, Object>> field : diff.entrySet()) {
                    Map<String, Object> existing = combined.get(field.getKey());
                    if (existing == null) {
                        combined.put(field.getKey(), new LinkedHashMap<>(field.getValue()));
                    } else {
                        // Keep the earliest "from", advance to the latest "to".
                        existing.put("to", field.getValue().get("to"));
                    }
                }
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                log.warn("Skipping unparseable edit diff for submission {}: {}", submissionId, e.getMessage());
            }
        }

        combined.entrySet().removeIf(e ->
                java.util.Objects.equals(e.getValue().get("from"), e.getValue().get("to")));
        if (combined.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(combined);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Failed to serialize combined session edit diff for submission {}: {}", submissionId, e.getMessage());
            return null;
        }
    }

    private void logAction(Submission submission, User validator,
            ValidationAction action, String remarks, String rejectionReason,
            boolean selfReview, boolean fastTrack, String editDiff) {
        ValidationLog entry = new ValidationLog();
        entry.setSubmission(submission);
        entry.setValidator(validator);
        entry.setAction(action);
        entry.setRemarks(remarks);
        entry.setRejectionReason(rejectionReason);
        entry.setSelfReview(selfReview);
        entry.setFastTrack(fastTrack);
        entry.setEditDiff(editDiff);
        validationLogRepository.save(entry);
    }
}

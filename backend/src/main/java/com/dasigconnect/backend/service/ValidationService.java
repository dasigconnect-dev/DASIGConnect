package com.dasigconnect.backend.service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dasigconnect.backend.event.RevisionRequestedEvent;
import com.dasigconnect.backend.event.SubmissionApprovedEvent;
import com.dasigconnect.backend.event.SubmissionEditedDuringReviewEvent;
import com.dasigconnect.backend.event.SubmissionRejectedEvent;
import com.dasigconnect.backend.model.dto.submission.SubmissionMediaOrderDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionResponseDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionSummaryDto;
import com.dasigconnect.backend.model.dto.submission.SubmissionUpdateDto;
import com.dasigconnect.backend.model.entity.ReviewEditSeverity;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionStatus;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.entity.ValidationAction;
import com.dasigconnect.backend.model.entity.ValidationLog;
import com.dasigconnect.backend.util.CaptionDiffAnalyzer;
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
    private final AuditLogService auditLogService;

    /**
     * A10: fraction of caption words a moderator may change across a whole review
     * session before the edit is classified {@code FLAGGED} rather than
     * {@code QUIET}. Field-injected {@code @Value} — tests set it via
     * {@code ReflectionTestUtils} (a primitive is not filled by {@code @InjectMocks}).
     */
    @Value("${app.review.caption-major-change-ratio:0.30}")
    private double captionMajorChangeRatio;

    public ValidationService(
            SubmissionRepository submissionRepository,
            SubmissionMediaAssetRepository submissionMediaAssetRepository,
            ValidationLogRepository validationLogRepository,
            ReviewLockService reviewLockService,
            SlotReservationService slotReservationService,
            SubmissionService submissionService,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper,
            AuditLogService auditLogService) {
        this.submissionRepository = submissionRepository;
        this.submissionMediaAssetRepository = submissionMediaAssetRepository;
        this.validationLogRepository = validationLogRepository;
        this.reviewLockService = reviewLockService;
        this.slotReservationService = slotReservationService;
        this.submissionService = submissionService;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.auditLogService = auditLogService;
    }

    /**
     * Returns the network-wide approval queue: PENDING + IN_REVIEW submissions
     * sorted by scheduledAt ASC (UC-2.4 Main Flow step 2). Moderator and
     * Admin accounts are both network-wide roles.
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
     * Moderators cannot approve their own submissions; another moderator/admin
     * must make the approval decision.
     */
    public void approve(UUID submissionId, JwtUserDetails caller) {
        Submission submission = loadSubmissionInScope(submissionId, caller);
        boolean selfReview = isSelfReview(submission, caller);
        assertNotSelfApproval(selfReview);
        assertReviewableStatus(submission);
        reviewLockService.assertCallerHoldsLock(submissionId, caller);

        String sessionEditDiff = combinedSessionEditDiff(submissionId);
        ReviewEditSeverity sessionSeverity = combinedSessionSeverity(submissionId);
        boolean edited = sessionEditDiff != null || sessionSeverity != null;

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
                null, null, selfReview, submission.isFastTrack(), sessionEditDiff, null);

        eventPublisher.publishEvent(new SubmissionApprovedEvent(submission, edited));
        if (edited) {
            eventPublisher.publishEvent(
                    new SubmissionEditedDuringReviewEvent(submission, sessionSeverity, sessionEditDiff));
        }
        log.info("Submission approved (fastTrack={}, edited={}): submission={} validator={}",
                submission.isFastTrack(), edited, submissionId, caller.userId());
    }

    /**
     * A9: applies a direct inline edit to any editable field of a submission that is
     * currently IN_REVIEW. The submission stays IN_REVIEW — the Moderator must
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
        submission = submissionService.applySubmissionEdits(submission, dto, caller);
        submissionService.assertContentComplete(submission);
        Map<String, Object> after = snapshotEditableFields(submission);
        String editDiff = buildEditDiff(before, after);
        ReviewEditSeverity severity = editDiff == null ? null
                : classifyFieldEdits(before, after,
                        sessionOriginalCaption(submissionId, (String) before.get("caption")));

        // Keep the submission IN_REVIEW — no terminal transition here. A save that
        // changed nothing (editDiff == null) must not record an `edited` audit row,
        // otherwise a no-op "Save Changes" inflates the edited-approval count (A10).
        if (editDiff != null) {
            User validator = loadUser(caller.userId());
            logAction(submission, validator, ValidationAction.edited, null, null,
                    selfReview, submission.isFastTrack(), editDiff, severity);
        }

        log.info("Submission edited in review (changed={} severity={}): submission={} validator={}",
                editDiff != null, severity, submissionId, caller.userId());
    }

    // ── A9: media edits during review ────────────────────────────────────────
    // The reviewing admin may add/remove/reorder media (and set per-item caption
    // + skip-watermark) on an IN_REVIEW submission. Each mutation records a
    // `ValidationAction.edited` log row so it counts as an edited approval (A10).
    // Content completeness is NOT enforced here — the reviewer may be mid-swap;
    // approve() still enforces it.

    private Submission loadForMediaEdit(UUID submissionId, JwtUserDetails caller) {
        Submission submission = loadSubmissionInScope(submissionId, caller);
        assertReviewableStatus(submission);
        reviewLockService.assertCallerHoldsLock(submissionId, caller);
        return submission;
    }

    private void logMediaEdit(Submission submission, JwtUserDetails caller, ReviewEditSeverity severity) {
        logAction(submission, loadUser(caller.userId()), ValidationAction.edited, null, null,
                isSelfReview(submission, caller), submission.isFastTrack(), "{\"media\":\"updated\"}", severity);
    }

    /**
     * A10: attaching a Library asset the contributor did not originally submit is
     * its own distinct audit event ({@code media_added} / {@code ADDED_MEDIA}),
     * with an optional moderator justification note. Device-file uploads into
     * someone else's submission during review are not permitted — new media must
     * go back to the contributor via Request Revision.
     */
    public SubmissionResponseDto attachReviewLibraryAsset(
            UUID submissionId, UUID mediaAssetId, String justification, JwtUserDetails caller) {
        Submission submission = loadForMediaEdit(submissionId, caller);
        SubmissionResponseDto response = submissionService.attachLibraryAssetTo(submission, mediaAssetId, caller);
        String note = justification == null || justification.isBlank() ? null : justification.trim();
        logAction(submission, loadUser(caller.userId()), ValidationAction.media_added, note, null,
                isSelfReview(submission, caller), submission.isFastTrack(),
                "{\"media\":\"library_asset_added\"}", ReviewEditSeverity.ADDED_MEDIA);
        return response;
    }

    public void detachReviewMedia(UUID submissionId, UUID mediaAssetId, JwtUserDetails caller) {
        Submission submission = loadForMediaEdit(submissionId, caller);
        submissionService.detachAssetFrom(submission, mediaAssetId);
        logMediaEdit(submission, caller, ReviewEditSeverity.FLAGGED);
    }

    public SubmissionResponseDto reorderReviewMedia(UUID submissionId, SubmissionMediaOrderDto dto, JwtUserDetails caller) {
        Submission submission = loadForMediaEdit(submissionId, caller);
        // The Save Changes flow sends this on every save; only log an `edited` row
        // when the request actually changes the order, a caption, or a skip flag.
        boolean noOp = submissionService.isNoOpMediaOrder(submission, dto);
        SubmissionResponseDto response = submissionService.reorderMediaOf(submission, dto);
        if (!noOp) {
            logMediaEdit(submission, caller, ReviewEditSeverity.QUIET);
        }
        return response;
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
        ReviewEditSeverity sessionSeverity = combinedSessionSeverity(submissionId);

        submission.setStatus(SubmissionStatus.needs_revision);
        submission.setValidatorRemarks(remarks);
        submissionRepository.save(submission);

        slotReservationService.release(submissionId);
        reviewLockService.release(submissionId, caller);

        User validator = loadUser(caller.userId());
        logAction(submission, validator, ValidationAction.needs_revision, remarks, null,
                selfReview, submission.isFastTrack(), sessionEditDiff, null);

        eventPublisher.publishEvent(new RevisionRequestedEvent(submission, remarks));
        if (sessionEditDiff != null || sessionSeverity != null) {
            eventPublisher.publishEvent(
                    new SubmissionEditedDuringReviewEvent(submission, sessionSeverity, sessionEditDiff));
        }
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
        ReviewEditSeverity sessionSeverity = combinedSessionSeverity(submissionId);
        String fullReason = buildRejectionReason(reasonCode, notes);
        submission.setStatus(SubmissionStatus.rejected);
        submission.setRejectionReason(fullReason);
        submissionRepository.save(submission);

        slotReservationService.release(submissionId);
        reviewLockService.release(submissionId, caller);

        User validator = loadUser(caller.userId());
        logAction(submission, validator, ValidationAction.rejected, null, fullReason,
                selfReview, submission.isFastTrack(), sessionEditDiff, null);

        eventPublisher.publishEvent(new SubmissionRejectedEvent(submission, fullReason));
        if (sessionEditDiff != null || sessionSeverity != null) {
            eventPublisher.publishEvent(
                    new SubmissionEditedDuringReviewEvent(submission, sessionSeverity, sessionEditDiff));
        }
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
        // Moderator and Admin are both network-wide roles, so any
        // submission is in scope for review — no institution comparison is needed.
        return submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Submission not found."));
    }

    private boolean isSelfReview(Submission submission, JwtUserDetails caller) {
        return submission.getContributor().getId().equals(caller.userId());
    }

    private void assertNotSelfApproval(boolean selfReview) {
        if (selfReview) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Your own submission must be reviewed by another moderator.");
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
                // Store display strings, not raw objects — the diff is human-readable
                // audit data, and this keeps serialization independent of which
                // Jackson date/time modules happen to be registered.
                diff.put(field, Map.of(
                        "from", oldValue == null ? "" : String.valueOf(oldValue),
                        "to", newValue == null ? "" : String.valueOf(newValue)));
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
     * The validation-log rows recorded since the most recent {@code lock_acquired}
     * — i.e. everything the current review session has done so far.
     */
    private List<ValidationLog> logsSinceLock(UUID submissionId) {
        List<ValidationLog> logs = validationLogRepository
                .findBySubmissionIdOrderByCreatedAtAsc(submissionId);
        int lastLockIndex = -1;
        for (int i = 0; i < logs.size(); i++) {
            if (logs.get(i).getAction() == ValidationAction.lock_acquired) {
                lastLockIndex = i;
            }
        }
        return logs.subList(lastLockIndex + 1, logs.size());
    }

    /**
     * The caption as it stood when the current review session began — the earliest
     * {@code caption.from} across this session's edits, or {@code fallback} (the
     * pre-edit value of the edit being classified) when the caption has not been
     * touched yet this session. Used to measure cumulative caption change (A10).
     */
    private String sessionOriginalCaption(UUID submissionId, String fallback) {
        for (ValidationLog entry : logsSinceLock(submissionId)) {
            if (entry.getAction() != ValidationAction.edited || entry.getEditDiff() == null) {
                continue;
            }
            try {
                Map<String, Map<String, Object>> diff = objectMapper.readValue(
                        entry.getEditDiff(),
                        new com.fasterxml.jackson.core.type.TypeReference<>() {});
                Map<String, Object> caption = diff.get("caption");
                if (caption != null) {
                    Object from = caption.get("from");
                    return from == null ? "" : String.valueOf(from);
                }
            } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
                // fall through to the next entry / fallback
            }
        }
        return fallback;
    }

    /**
     * A10: the highest {@link ReviewEditSeverity} recorded across every
     * {@code edited} / {@code media_added} row this review session, or null when
     * nothing was edited. Feeds the terminal-action "edited during review"
     * notification.
     */
    private ReviewEditSeverity combinedSessionSeverity(UUID submissionId) {
        ReviewEditSeverity severity = null;
        for (ValidationLog entry : logsSinceLock(submissionId)) {
            String raw = entry.getEditSeverity();
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                severity = ReviewEditSeverity.max(severity,
                        ReviewEditSeverity.valueOf(raw.trim().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                // unknown legacy value — skip
            }
        }
        return severity;
    }

    /**
     * A10: classifies one {@code edit()} call. Severity is the max across every
     * changed field: {@code scheduledAt} is always {@code FLAGGED}; {@code caption}
     * is {@code FLAGGED} once the cumulative session word-change ratio reaches
     * {@link #captionMajorChangeRatio}; everything else is {@code QUIET}.
     */
    private ReviewEditSeverity classifyFieldEdits(
            Map<String, Object> before, Map<String, Object> after, String sessionOriginalCaption) {
        ReviewEditSeverity severity = null;
        for (String field : before.keySet()) {
            if (java.util.Objects.equals(before.get(field), after.get(field))) {
                continue;
            }
            ReviewEditSeverity fieldSeverity = switch (field) {
                case "scheduledAt" -> ReviewEditSeverity.FLAGGED;
                case "caption" -> {
                    Object current = after.get("caption");
                    double ratio = CaptionDiffAnalyzer.changedWordRatio(
                            sessionOriginalCaption, current == null ? "" : String.valueOf(current));
                    yield ratio >= captionMajorChangeRatio
                            ? ReviewEditSeverity.FLAGGED : ReviewEditSeverity.QUIET;
                }
                default -> ReviewEditSeverity.QUIET;
            };
            severity = ReviewEditSeverity.max(severity, fieldSeverity);
        }
        return severity;
    }

    /**
     * A10: aggregates the before/after diffs of every standalone {@code edited}
     * action taken since the current review lock was acquired into one combined
     * diff, so a terminal action (approve/revise/reject) records the full picture
     * of what the Moderator changed. Returns null when no edit happened this
     * session.
     */
    private String combinedSessionEditDiff(UUID submissionId) {
        Map<String, Map<String, Object>> combined = new LinkedHashMap<>();
        for (ValidationLog entry : logsSinceLock(submissionId)) {
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
            boolean selfReview, boolean fastTrack, String editDiff, ReviewEditSeverity severity) {
        ValidationLog entry = new ValidationLog();
        entry.setSubmission(submission);
        entry.setValidator(validator);
        entry.setAction(action);
        entry.setRemarks(remarks);
        entry.setRejectionReason(rejectionReason);
        entry.setSelfReview(selfReview);
        entry.setFastTrack(fastTrack);
        entry.setEditDiff(editDiff);
        entry.setEditSeverity(severity == null ? null : severity.toDbValue());
        validationLogRepository.save(entry);

        try {
            Map<String, Object> meta = new HashMap<>();
            if (remarks != null && !remarks.isBlank()) meta.put("remarks", remarks);
            if (rejectionReason != null && !rejectionReason.isBlank()) meta.put("rejectionReason", rejectionReason);
            if (selfReview) meta.put("selfReview", true);
            if (fastTrack) meta.put("fastTrack", true);
            if (editDiff != null && !editDiff.isBlank()) meta.put("editDiff", editDiff);
            if (severity != null) meta.put("editSeverity", severity.toDbValue());
            auditLogService.record(validator, action.name(), null, null, submission.getId(), meta);
        } catch (Exception ex) {
            log.warn("Failed to write audit log for validation action {} on submission {}: {}", action, submission.getId(), ex.getMessage());
        }
    }
}

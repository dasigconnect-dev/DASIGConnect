package com.dasigconnect.backend.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dasigconnect.backend.event.SubmissionEditedDuringReviewEvent;
import com.dasigconnect.backend.model.entity.ReviewEditSeverity;
import com.dasigconnect.backend.model.entity.ReviewLock;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionStatus;
import com.dasigconnect.backend.model.entity.User;
import com.dasigconnect.backend.model.entity.ValidationAction;
import com.dasigconnect.backend.model.entity.ValidationLog;
import com.dasigconnect.backend.repository.ReviewLockRepository;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.repository.UserRepository;
import com.dasigconnect.backend.repository.ValidationLogRepository;
import com.dasigconnect.backend.security.JwtUserDetails;

@Service
@Transactional
public class ReviewLockService {

    private static final Logger log = LoggerFactory.getLogger(ReviewLockService.class);
    private static final long LOCK_DURATION_MINUTES = 15;

    private final ReviewLockRepository reviewLockRepository;
    private final SubmissionRepository submissionRepository;
    private final ValidationLogRepository validationLogRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ReviewLockService(
            ReviewLockRepository reviewLockRepository,
            SubmissionRepository submissionRepository,
            ValidationLogRepository validationLogRepository,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher) {
        this.reviewLockRepository = reviewLockRepository;
        this.submissionRepository = submissionRepository;
        this.validationLogRepository = validationLogRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Acquires a review lock for a submission.
     *
     * A5: self-review (validator == contributor) is allowed, not blocked — the
     * resulting ValidationLog entries are flagged via ValidationService/isSelfReview.
     * If the caller already holds the lock, its TTL is renewed and the lock is
     * returned (idempotent keep-alive — the review panel pings this while open so a
     * long edit session does not lose the lock to ReviewLockCleanupJob).
     * If another reviewer holds a valid lock, returns 409.
     * Transitions submission: pending → in_review.
     */
    public ReviewLock acquire(UUID submissionId, JwtUserDetails caller) {
        Submission submission = loadSubmissionInScope(submissionId, caller);
        if (submission.getContributor() != null
                && submission.getContributor().getId() != null
                && submission.getContributor().getId().equals(caller.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You cannot review your own submission. Another Moderator must review it.");
        }

        // Only PENDING submissions can be locked (IN_REVIEW may already be locked by this user)
        if (submission.getStatus() != SubmissionStatus.pending
                && submission.getStatus() != SubmissionStatus.in_review) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Submission is not in a reviewable state.");
        }

        ReviewLock existing = reviewLockRepository.findBySubmissionIdWithLockedBy(submissionId).orElse(null);

        if (existing != null) {
            if (existing.getExpiresAt().isAfter(Instant.now())) {
                // Lock is still valid
                if (existing.getLockedBy().getId().equals(caller.userId())) {
                    // Caller already holds it — renew the TTL (idempotent keep-alive)
                    existing.setExpiresAt(Instant.now().plus(LOCK_DURATION_MINUTES, ChronoUnit.MINUTES));
                    return reviewLockRepository.save(existing);
                }
                // Another reviewer holds it
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This submission is currently being reviewed by another Moderator.");
            }
            // Expired lock — clean it up before acquiring
            expireLock(existing, submission);
        }

        User validator = loadUser(caller.userId());

        ReviewLock lock = new ReviewLock();
        lock.setSubmission(submission);
        lock.setLockedBy(validator);
        lock.setExpiresAt(Instant.now().plus(LOCK_DURATION_MINUTES, ChronoUnit.MINUTES));
        reviewLockRepository.save(lock);

        submission.setStatus(SubmissionStatus.in_review);
        submissionRepository.save(submission);

        logAction(submission, validator, ValidationAction.lock_acquired, null, null);

        log.info("Review lock acquired: submission={} validator={}", submissionId, caller.userId());
        return lock;
    }

    /**
     * Releases a review lock explicitly (called when validator navigates away).
     * Reverts submission in_review → pending if no action was taken.
     */
    public void release(UUID submissionId, JwtUserDetails caller) {
        ReviewLock lock = reviewLockRepository.findBySubmissionId(submissionId).orElse(null);
        if (lock == null) {
            return; // Already released — no-op
        }

        // Only the lock holder (or an admin) can release
        if (!lock.getLockedBy().getId().equals(caller.userId())
                && !"admin".equals(caller.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You do not hold the review lock for this submission.");
        }

        Submission submission = lock.getSubmission();
        User validator = lock.getLockedBy();

        reviewLockRepository.deleteBySubmissionId(submissionId);

        // Revert to pending only if the submission is still in_review
        // (it may already be scheduled/needs_revision/rejected if an action was taken)
        boolean noTerminalAction = submission.getStatus() == SubmissionStatus.in_review;
        if (noTerminalAction) {
            submission.setStatus(SubmissionStatus.pending);
            submissionRepository.save(submission);
        }

        logAction(submission, validator, ValidationAction.lock_released, null, null);
        log.info("Review lock released: submission={} validator={}", submissionId, caller.userId());

        // A10: if the moderator changed the contributor's submission but walked away
        // without approving/revising/rejecting, the contributor still needs to know.
        // The terminal actions publish this event themselves.
        if (noTerminalAction) {
            publishEditedIfSessionHadEdits(submission, submissionId);
        }
    }

    /**
     * Read-only lookup of the current active lock for a submission, if any.
     * Does not acquire, extend, or otherwise mutate anything — used by the
     * frontend to restore lock UI state after a page refresh.
     */
    @Transactional(readOnly = true)
    public Optional<ReviewLock> getActiveLock(UUID submissionId) {
        return reviewLockRepository.findBySubmissionIdWithLockedBy(submissionId)
                .filter(lock -> lock.getExpiresAt().isAfter(Instant.now()));
    }

    /**
     * Called by ReviewLockCleanupJob every minute.
     * Finds all expired locks, reverts in_review → pending, and cleans up.
     */
    public void releaseExpiredLocks() {
        List<ReviewLock> expired = reviewLockRepository.findByExpiresAtBefore(Instant.now());
        if (expired.isEmpty()) return;

        log.info("ReviewLockCleanupJob: releasing {} expired lock(s).", expired.size());

        for (ReviewLock lock : expired) {
            Submission submission = lock.getSubmission();
            expireLock(lock, submission);
        }
    }

    /**
     * Asserts that the caller holds an active review lock for the submission.
     * Admins bypass this check (they act as the override role); moderators must
     * hold the lock. Throws 403 if no active lock exists or another reviewer holds it.
     */
    public void assertCallerHoldsLock(UUID submissionId, JwtUserDetails caller) {
        if ("admin".equals(caller.role())) return;

        ReviewLock lock = reviewLockRepository.findBySubmissionId(submissionId).orElse(null);
        if (lock == null || lock.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You must acquire the review lock before taking action on this submission.");
        }
        if (!lock.getLockedBy().getId().equals(caller.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You do not hold the review lock for this submission.");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void expireLock(ReviewLock lock, Submission submission) {
        reviewLockRepository.delete(lock);

        boolean noTerminalAction = submission.getStatus() == SubmissionStatus.in_review;
        if (noTerminalAction) {
            submission.setStatus(SubmissionStatus.pending);
            submissionRepository.save(submission);
        }

        logAction(submission, lock.getLockedBy(), ValidationAction.lock_expired, null, null);
        log.info("Review lock expired: submission={} validator={}",
                submission.getId(), lock.getLockedBy().getId());

        if (noTerminalAction) {
            publishEditedIfSessionHadEdits(submission, submission.getId());
        }
    }

    /**
     * A10: notify the contributor when a review session that changed their
     * submission ended without a terminal action (explicit release or lock
     * expiry). Severity is the highest recorded across this session's
     * {@code edited} / {@code media_added} rows; null (no edits) publishes nothing.
     */
    private void publishEditedIfSessionHadEdits(Submission submission, UUID submissionId) {
        List<ValidationLog> logs = validationLogRepository
                .findBySubmissionIdOrderByCreatedAtAsc(submissionId);
        int lastLockIndex = -1;
        for (int i = 0; i < logs.size(); i++) {
            if (logs.get(i).getAction() == ValidationAction.lock_acquired) {
                lastLockIndex = i;
            }
        }
        ReviewEditSeverity severity = null;
        for (int i = lastLockIndex + 1; i < logs.size(); i++) {
            String raw = logs.get(i).getEditSeverity();
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
        if (severity != null) {
            eventPublisher.publishEvent(
                    new SubmissionEditedDuringReviewEvent(submission, severity, null));
        }
    }

    private void logAction(Submission submission, User validator,
            ValidationAction action, String remarks, String rejectionReason) {
        ValidationLog entry = new ValidationLog();
        entry.setSubmission(submission);
        entry.setValidator(validator);
        entry.setAction(action);
        entry.setRemarks(remarks);
        entry.setRejectionReason(rejectionReason);
        entry.setSelfReview(submission.getContributor().getId().equals(validator.getId()));
        validationLogRepository.save(entry);
    }

    private Submission loadSubmissionInScope(UUID submissionId, JwtUserDetails caller) {
        // Moderator and Admin are both network-wide roles, so any
        // submission is in scope for review — no institution comparison is needed.
        return submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Submission not found."));
    }

    private User loadUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Authenticated user not found."));
    }
}

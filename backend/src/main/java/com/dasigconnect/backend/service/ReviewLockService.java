package com.dasigconnect.backend.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
    private static final long LOCK_DURATION_MINUTES = 30;

    private final ReviewLockRepository reviewLockRepository;
    private final SubmissionRepository submissionRepository;
    private final ValidationLogRepository validationLogRepository;
    private final UserRepository userRepository;

    public ReviewLockService(
            ReviewLockRepository reviewLockRepository,
            SubmissionRepository submissionRepository,
            ValidationLogRepository validationLogRepository,
            UserRepository userRepository) {
        this.reviewLockRepository = reviewLockRepository;
        this.submissionRepository = submissionRepository;
        this.validationLogRepository = validationLogRepository;
        this.userRepository = userRepository;
    }

    /**
     * Acquires a review lock for a submission.
     *
     * GR-H5: blocks self-review (validator == contributor).
     * If the caller already holds the lock, returns the existing lock (idempotent).
     * If another validator holds a valid lock, returns 409.
     * Transitions submission: pending → in_review.
     */
    public ReviewLock acquire(UUID submissionId, JwtUserDetails caller) {
        Submission submission = loadSubmissionInScope(submissionId, caller);

        // GR-H5: self-validation blocked
        if (submission.getContributor().getId().equals(caller.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You cannot review your own submission.");
        }

        // Only PENDING submissions can be locked (IN_REVIEW may already be locked by this user)
        if (submission.getStatus() != SubmissionStatus.pending
                && submission.getStatus() != SubmissionStatus.in_review) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Submission is not in a reviewable state.");
        }

        ReviewLock existing = reviewLockRepository.findBySubmissionId(submissionId).orElse(null);

        if (existing != null) {
            if (existing.getExpiresAt().isAfter(Instant.now())) {
                // Lock is still valid
                if (existing.getLockedBy().getId().equals(caller.userId())) {
                    // Caller already holds it — idempotent
                    return existing;
                }
                // Another validator holds it
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This submission is currently being reviewed by another validator.");
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
                && !"administrator".equals(caller.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You do not hold the review lock for this submission.");
        }

        Submission submission = lock.getSubmission();
        User validator = lock.getLockedBy();

        reviewLockRepository.deleteBySubmissionId(submissionId);

        // Revert to pending only if the submission is still in_review
        // (it may already be scheduled/needs_revision/rejected if an action was taken)
        if (submission.getStatus() == SubmissionStatus.in_review) {
            submission.setStatus(SubmissionStatus.pending);
            submissionRepository.save(submission);
        }

        logAction(submission, validator, ValidationAction.lock_released, null, null);
        log.info("Review lock released: submission={} validator={}", submissionId, caller.userId());
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
     * Administrators bypass this check.
     * Throws 403 if no active lock exists or if another validator holds it.
     */
    public void assertCallerHoldsLock(UUID submissionId, JwtUserDetails caller) {
        if ("administrator".equals(caller.role())) return;

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

        if (submission.getStatus() == SubmissionStatus.in_review) {
            submission.setStatus(SubmissionStatus.pending);
            submissionRepository.save(submission);
        }

        logAction(submission, lock.getLockedBy(), ValidationAction.lock_expired, null, null);
        log.info("Review lock expired: submission={} validator={}",
                submission.getId(), lock.getLockedBy().getId());
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

    private Submission loadSubmissionInScope(UUID submissionId, JwtUserDetails caller) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Submission not found."));

        // Administrators can access any submission; validators are institution-scoped
        if (!"administrator".equals(caller.role())
                && !submission.getInstitution().getId().equals(caller.institutionId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Submission not found.");
        }

        return submission;
    }

    private User loadUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Authenticated user not found."));
    }
}

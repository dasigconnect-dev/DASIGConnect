package com.dasigconnect.backend.service;

import java.time.Instant;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dasigconnect.backend.event.TokenPublishingSuspendedEvent;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionStatus;
import com.dasigconnect.backend.repository.SubmissionRepository;

/**
 * Encapsulates transactional state mutations for publishing scheduled jobs.
 * This separates transactional DB writes from non-transactional scheduled jobs
 * (e.g. StaleSubmissionDetectorJob, TokenPublishingEscalationJob) to avoid
 * partial commits caused by Spring AOP self-invocation limitations.
 */
@Service
public class PublishingStateService {

    private final SubmissionRepository submissionRepository;
    private final SlotReservationService slotReservationService;
    private final FacebookPublisherService facebookPublisherService;
    private final ApplicationEventPublisher eventPublisher;

    public PublishingStateService(
            SubmissionRepository submissionRepository,
            SlotReservationService slotReservationService,
            FacebookPublisherService facebookPublisherService,
            ApplicationEventPublisher eventPublisher) {
        this.submissionRepository = submissionRepository;
        this.slotReservationService = slotReservationService;
        this.facebookPublisherService = facebookPublisherService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public List<Submission> findAndMarkFailed(Instant cutoff) {
        List<Submission> missed = submissionRepository.findMissedScheduledSubmissions(cutoff);
        missed.removeIf(s -> s.getTokenBlockedAt() != null);
        for (Submission s : missed) {
            boolean isDirectPost = s.getStatus() == SubmissionStatus.direct_post_scheduled
                    || s.getStatus() == SubmissionStatus.direct_post_publishing;
            s.setStatus(isDirectPost ? SubmissionStatus.direct_post_failed : SubmissionStatus.publish_failed);
        }
        submissionRepository.saveAll(missed);
        return missed;
    }

    @Transactional
    public List<Submission> findAndMarkStuckFastTrackFailed(Instant cutoff) {
        List<Submission> stuck = submissionRepository.findStuckFastTrackPublishing(cutoff);
        stuck.removeIf(s -> s.getTokenBlockedAt() != null);
        for (Submission s : stuck) {
            s.setStatus(s.getStatus() == SubmissionStatus.direct_post_publishing
                    ? SubmissionStatus.direct_post_failed : SubmissionStatus.publish_failed);
        }
        submissionRepository.saveAll(stuck);
        return stuck;
    }

    @Transactional
    public List<Submission> findAndMarkMissedReview(Instant cutoff) {
        List<Submission> missed = submissionRepository.findMissedReviewSubmissions(cutoff, Instant.now());
        for (Submission s : missed) {
            s.setStatus(SubmissionStatus.missed_review);
            slotReservationService.release(s.getId());
        }
        submissionRepository.saveAll(missed);
        return missed;
    }

    @Transactional
    public void escalateAfterTwentyFourHours(Submission submission) {
        if (submission.getTokenEscalated24hAt() != null) {
            return;
        }
        submission.setTokenEscalated24hAt(Instant.now());
        submissionRepository.save(submission);
        facebookPublisherService.recordAttempt(
                submission,
                1,
                "failed",
                FacebookPublisherService.TOKEN_EXPIRED_24H_PREFIX
                        + ": Facebook token still not reauthorized after 24 hours.",
                null);
        eventPublisher.publishEvent(new TokenPublishingSuspendedEvent(
                submission,
                TokenPublishingSuspendedEvent.Stage.ESCALATION_24H,
                "Facebook token still not reauthorized after 24 hours."));
    }

    @Transactional
    public void failAfterFortyEightHours(Submission submission) {
        if (submission.getTokenFinalFailedAt() == null) {
            submission.setTokenFinalFailedAt(Instant.now());
            submissionRepository.save(submission);
            facebookPublisherService.recordAttempt(
                    submission,
                    1,
                    "failed",
                    FacebookPublisherService.TOKEN_EXPIRED_48H_PREFIX
                            + ": Facebook token still not reauthorized after 48 hours.",
                    null);
            eventPublisher.publishEvent(new TokenPublishingSuspendedEvent(
                    submission,
                    TokenPublishingSuspendedEvent.Stage.FINAL_FAILURE,
                    "Facebook token still not reauthorized after 48 hours."));
        }
        facebookPublisherService.markFailed(
                submission,
                "Facebook Page Access Token was not reauthorized within 48 hours.");
    }
}


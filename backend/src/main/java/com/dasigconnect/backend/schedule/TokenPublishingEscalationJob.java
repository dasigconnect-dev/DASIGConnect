package com.dasigconnect.backend.schedule;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.dasigconnect.backend.event.TokenPublishingSuspendedEvent;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionMediaAsset;
import com.dasigconnect.backend.repository.SubmissionRepository;
import com.dasigconnect.backend.service.FacebookPublisherService;
import com.dasigconnect.backend.service.PublishingQueryService;
import com.dasigconnect.backend.service.ScheduledJobHealthService;

/**
 * UC-3.2 A3: escalates submissions suspended by Facebook token expiry.
 * Runs separately from the missed-window detector so expired-token pauses do
 * not become PUBLISH_FAILED until the 48-hour SRS threshold.
 */
@Component
public class TokenPublishingEscalationJob {

    private static final Logger log = LoggerFactory.getLogger(TokenPublishingEscalationJob.class);

    private static final Duration ESCALATE_AFTER = Duration.ofHours(24);
    private static final Duration FAIL_AFTER = Duration.ofHours(48);

    private final SubmissionRepository submissionRepository;
    private final PublishingQueryService publishingQueryService;
    private final FacebookPublisherService facebookPublisherService;
    private final ApplicationEventPublisher eventPublisher;
    private final ScheduledJobHealthService scheduledJobHealthService;

    public TokenPublishingEscalationJob(
            SubmissionRepository submissionRepository,
            PublishingQueryService publishingQueryService,
            FacebookPublisherService facebookPublisherService,
            ApplicationEventPublisher eventPublisher,
            ScheduledJobHealthService scheduledJobHealthService) {
        this.submissionRepository = submissionRepository;
        this.publishingQueryService = publishingQueryService;
        this.facebookPublisherService = facebookPublisherService;
        this.eventPublisher = eventPublisher;
        this.scheduledJobHealthService = scheduledJobHealthService;
    }

    @Scheduled(cron = "0 */5 * * * *", zone = "UTC")
    public void run() {
        Instant startedAt = Instant.now();
        try {
            List<Submission> blocked = submissionRepository.findTokenBlockedScheduledSubmissions();
            if (blocked.isEmpty()) {
                scheduledJobHealthService.recordSuccess("TokenPublishingEscalationJob", startedAt);
                return;
            }

            if (facebookPublisherService.hasUsableActiveToken()) {
                retryBlockedSubmissions(blocked);
                scheduledJobHealthService.recordSuccess("TokenPublishingEscalationJob", startedAt);
                return;
            }

            Instant now = Instant.now();
            for (Submission submission : blocked) {
                Instant blockedAt = submission.getTokenBlockedAt();
                if (blockedAt == null) {
                    continue;
                }

                Duration blockedFor = Duration.between(blockedAt, now);
                if (blockedFor.compareTo(FAIL_AFTER) >= 0) {
                    failAfterFortyEightHours(submission);
                } else if (blockedFor.compareTo(ESCALATE_AFTER) >= 0) {
                    escalateAfterTwentyFourHours(submission);
                }
            }
            scheduledJobHealthService.recordSuccess("TokenPublishingEscalationJob", startedAt);
        } catch (Exception ex) {
            log.error("TokenPublishingEscalationJob failed: {}", ex.getMessage(), ex);
            scheduledJobHealthService.recordFailure("TokenPublishingEscalationJob", startedAt, ex);
        }
    }

    private void retryBlockedSubmissions(List<Submission> blocked) {
        for (Submission submission : blocked) {
            try {
                Submission claimed = publishingQueryService.claimForPublishing(submission).orElse(null);
                if (claimed == null) {
                    continue;
                }
                List<SubmissionMediaAsset> mediaLinks =
                        publishingQueryService.loadMediaLinksForSubmission(claimed.getId());
                facebookPublisherService.publishMediaLinks(claimed, mediaLinks);
            } catch (Exception ex) {
                log.error("Token-blocked submission {} retry failed: {}",
                        submission.getId(), ex.getMessage(), ex);
            }
        }
    }

    private void escalateAfterTwentyFourHours(Submission submission) {
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

    private void failAfterFortyEightHours(Submission submission) {
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

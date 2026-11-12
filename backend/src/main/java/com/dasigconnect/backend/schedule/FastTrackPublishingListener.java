package com.dasigconnect.backend.schedule;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.dasigconnect.backend.event.SubmissionApprovedEvent;
import com.dasigconnect.backend.event.SubmissionFastTrackRetryEvent;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionMediaAsset;
import com.dasigconnect.backend.service.FacebookPublisherService;
import com.dasigconnect.backend.service.PublishingQueryService;

/**
 * UC-3.2 A5: Fast-Track submissions skip the scheduler window and publish
 * immediately after approval commits. Also handles a failed Fast-Track
 * publish being retried without a mode change (SubmissionFastTrackRetryEvent)
 * — PublishingSchedulerJob's cron never picks these up on its own, since its
 * query requires a non-null scheduledAt and Fast-Track submissions never have
 * one.
 *
 * Transaction boundary (2026-09-18 fix): {@code publishIfFastTrack} is
 * deliberately NOT wrapped in a transaction at this level, mirroring
 * PublishingSchedulerJob's own documented rule -- FacebookPublisherService
 * must never be called while holding an open DB transaction/connection. Each
 * PublishingQueryService call still gets its own short, separately-committed
 * transaction via the Spring proxy (a different bean, so no self-invocation
 * issue).
 *
 * Async execution: publishing is dispatched asynchronously to a background worker
 * so neither approval nor retry blocks the HTTP request thread during Graph API
 * calls and backoff retries.
 */
@Component
public class FastTrackPublishingListener {

    private static final Logger log = LoggerFactory.getLogger(FastTrackPublishingListener.class);

    private final PublishingQueryService publishingQueryService;
    private final FacebookPublisherService facebookPublisherService;

    public FastTrackPublishingListener(
            PublishingQueryService publishingQueryService,
            FacebookPublisherService facebookPublisherService) {
        this.publishingQueryService = publishingQueryService;
        this.facebookPublisherService = facebookPublisherService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubmissionApproved(SubmissionApprovedEvent event) {
        publishIfFastTrack(event.submission());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFastTrackRetry(SubmissionFastTrackRetryEvent event) {
        publishIfFastTrack(event.submission());
    }

    private void publishIfFastTrack(Submission submission) {
        if (!submission.isFastTrack()) {
            return;
        }
        if (!facebookPublisherService.isConfigured()) {
            log.warn("Fast-Track submission {} cannot publish because Facebook is not configured.", submission.getId());
            facebookPublisherService.markFailed(
                    submission,
                    "Fast-Track publishing is unavailable because Facebook is not configured.");
            return;
        }

        try {
            Submission claimed = publishingQueryService.claimForPublishing(submission).orElse(null);
            if (claimed == null) {
                log.info("Fast-Track submission {} was already claimed for publishing.", submission.getId());
                return;
            }
            List<SubmissionMediaAsset> mediaLinks =
                    publishingQueryService.loadMediaLinksForSubmission(claimed.getId());
            // Called outside any transaction — Facebook API must not hold a DB connection
            facebookPublisherService.publishMediaLinks(claimed, mediaLinks);
        } catch (Exception ex) {
            log.error("Fast-Track publishing failed for submission {}: {}",
                    submission.getId(), ex.getMessage(), ex);
            facebookPublisherService.markFailed(submission, "Fast-Track publishing failed: " + ex.getMessage());
        }
    }
}

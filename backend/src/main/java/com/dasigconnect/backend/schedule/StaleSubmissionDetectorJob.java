package com.dasigconnect.backend.schedule;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.dasigconnect.backend.event.PublishFailedEvent;
import com.dasigconnect.backend.event.SubmissionMissedReviewEvent;
import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.service.PublishingStateService;
import com.dasigconnect.backend.service.ScheduledJobHealthService;
import org.springframework.context.ApplicationEventPublisher;

/**
 * GR-T9: Fires every 5 minutes.
 *
 * <ol>
 *   <li>Finds SCHEDULED submissions whose slot has already passed by more than 5
 *       minutes without being picked up by the PublishingSchedulerJob (e.g. the
 *       server was down during the window), transitions them to PUBLISH_FAILED,
 *       and emits {@link PublishFailedEvent}.</li>
 *   <li>Finds a Fast-Track submission stuck in PUBLISHING for more than 5 minutes
 *       (claimed by FastTrackPublishingListener, then the app crashed before
 *       markPublished/markFailed ran) -- these have no scheduledAt for the sweep
 *       above to match, so they'd otherwise stay stuck forever with no recovery
 *       path. Transitions them to PUBLISH_FAILED too, added 2026-09-18.</li>
 *   <li>Finds PENDING / IN_REVIEW submissions whose scheduled publication time
 *       has passed while still unreviewed (UC-2.4 A6), transitions them to
 *       MISSED_REVIEW, releases the reserved slot, and emits
 *       {@link SubmissionMissedReviewEvent} so moderators are notified.</li>
 * </ol>
 */
@Component
public class StaleSubmissionDetectorJob {

    private static final Logger log = LoggerFactory.getLogger(StaleSubmissionDetectorJob.class);

    private final PublishingStateService publishingStateService;
    private final ApplicationEventPublisher eventPublisher;
    private final ScheduledJobHealthService scheduledJobHealthService;

    public StaleSubmissionDetectorJob(
            PublishingStateService publishingStateService,
            ApplicationEventPublisher eventPublisher,
            ScheduledJobHealthService scheduledJobHealthService) {
        this.publishingStateService = publishingStateService;
        this.eventPublisher = eventPublisher;
        this.scheduledJobHealthService = scheduledJobHealthService;
    }

    @Scheduled(cron = "0 */5 * * * *", zone = "UTC")
    public void run() {
        Instant startedAt = Instant.now();
        Instant cutoff = startedAt.minus(5, ChronoUnit.MINUTES);

        // Each sweep is isolated so one failing does not skip the other, but a
        // failure in either is reported as the job's health status.
        Exception failure = null;

        try {
            List<Submission> missed = publishingStateService.findAndMarkFailed(cutoff);
            if (!missed.isEmpty()) {
                log.warn("StaleSubmissionDetectorJob: {} missed submission(s) transitioned to PUBLISH_FAILED.", missed.size());
                for (Submission s : missed) {
                    eventPublisher.publishEvent(new PublishFailedEvent(s, "Publish window missed — server was unavailable during the scheduled time."));
                }
            }
        } catch (Exception ex) {
            log.error("StaleSubmissionDetectorJob (publish-failed sweep) failed: {}", ex.getMessage(), ex);
            failure = ex;
        }

        try {
            List<Submission> stuckFastTrack = publishingStateService.findAndMarkStuckFastTrackFailed(cutoff);
            if (!stuckFastTrack.isEmpty()) {
                log.warn("StaleSubmissionDetectorJob: {} stuck Fast-Track submission(s) transitioned to PUBLISH_FAILED.", stuckFastTrack.size());
                for (Submission s : stuckFastTrack) {
                    eventPublisher.publishEvent(new PublishFailedEvent(s, "Publishing got stuck (likely a server restart mid-publish) and was not resolved within 5 minutes."));
                }
            }
        } catch (Exception ex) {
            log.error("StaleSubmissionDetectorJob (stuck Fast-Track sweep) failed: {}", ex.getMessage(), ex);
            if (failure == null) {
                failure = ex;
            }
        }

        try {
            List<Submission> missedReview = publishingStateService.findAndMarkMissedReview(cutoff);
            if (!missedReview.isEmpty()) {
                log.warn("StaleSubmissionDetectorJob: {} unreviewed submission(s) transitioned to MISSED_REVIEW.", missedReview.size());
                for (Submission s : missedReview) {
                    eventPublisher.publishEvent(new SubmissionMissedReviewEvent(s));
                }
            }
        } catch (Exception ex) {
            log.error("StaleSubmissionDetectorJob (missed-review sweep) failed: {}", ex.getMessage(), ex);
            if (failure == null) {
                failure = ex;
            }
        }

        if (failure == null) {
            scheduledJobHealthService.recordSuccess("StaleSubmissionDetectorJob", startedAt);
        } else {
            scheduledJobHealthService.recordFailure("StaleSubmissionDetectorJob", startedAt, failure);
        }
    }
}

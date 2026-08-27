package com.dasigconnect.backend.schedule;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.dasigconnect.backend.model.entity.Submission;
import com.dasigconnect.backend.model.entity.SubmissionMediaAsset;
import com.dasigconnect.backend.service.FacebookPublisherService;
import com.dasigconnect.backend.service.PublishingQueryService;

/**
 * GR-T5: Fires every minute. Finds SCHEDULED submissions whose slot falls
 * within the current publish window (now-5min to now) and attempts to publish
 * each one to the DASIG Facebook Page.
 *
 * Transaction boundary: submissions and their media assets are loaded through
 * PublishingQueryService (a separate @Service bean so the @Transactional proxy
 * is in the call path). The Facebook API call happens outside any transaction
 * to respect the HikariCP 5-connection limit.
 */
@Component
public class PublishingSchedulerJob {

    private static final Logger log = LoggerFactory.getLogger(PublishingSchedulerJob.class);

    private final PublishingQueryService publishingQueryService;
    private final FacebookPublisherService facebookPublisherService;
    private final com.dasigconnect.backend.service.ScheduledJobHealthService scheduledJobHealthService;

    public PublishingSchedulerJob(
            PublishingQueryService publishingQueryService,
            FacebookPublisherService facebookPublisherService,
            com.dasigconnect.backend.service.ScheduledJobHealthService scheduledJobHealthService) {
        this.publishingQueryService = publishingQueryService;
        this.facebookPublisherService = facebookPublisherService;
        this.scheduledJobHealthService = scheduledJobHealthService;
    }

    @Scheduled(cron = "0 * * * * *", zone = "UTC")
    public void run() {
        Instant startedAt = Instant.now();
        try {
        if (!facebookPublisherService.isConfigured()) {
            scheduledJobHealthService.recordSuccess("PublishingSchedulerJob", startedAt);
            return;
        }

        Instant now = Instant.now();
        Instant windowStart = now.minus(5, ChronoUnit.MINUTES);

        List<Submission> due = publishingQueryService.loadDueSubmissions(windowStart, now);
        if (due.isEmpty()) {
            scheduledJobHealthService.recordSuccess("PublishingSchedulerJob", startedAt);
            return;
        }

        log.info("PublishingSchedulerJob: {} submission(s) due for publishing.", due.size());

        for (Submission submission : due) {
            try {
                Submission claimed = publishingQueryService.claimForPublishing(submission)
                        .orElse(null);
                if (claimed == null) {
                    log.info("PublishingSchedulerJob: submission {} was already claimed for publishing.",
                            submission.getId());
                    continue;
                }

                List<SubmissionMediaAsset> assets = publishingQueryService.loadMediaLinksForSubmission(claimed.getId());
                // Called outside any transaction — Facebook API must not hold a DB connection
                facebookPublisherService.publishMediaLinks(claimed, assets);
            } catch (Exception ex) {
                log.error("Unexpected error publishing submission {}: {}",
                        submission.getId(), ex.getMessage(), ex);
            }
        }
        scheduledJobHealthService.recordSuccess("PublishingSchedulerJob", startedAt);
        } catch (Exception ex) {
            scheduledJobHealthService.recordFailure("PublishingSchedulerJob", startedAt, ex);
            throw ex;
        }
    }
}

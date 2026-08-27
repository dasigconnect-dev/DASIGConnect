package com.dasigconnect.backend.schedule;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dasigconnect.backend.repository.ScheduledJobRunRepository;
import com.dasigconnect.backend.service.ScheduledJobHealthService;

/**
 * Prunes {@code scheduled_job_runs} so the System Health audit table does not
 * grow without bound (PublishingSchedulerJob alone writes one row per minute).
 * Runs daily at 03:15 UTC and keeps the last {@code app.schedule.job-run-retention-days}
 * days of history.
 */
@Component
public class ScheduledJobRunRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobRunRetentionJob.class);

    private final ScheduledJobRunRepository scheduledJobRunRepository;
    private final ScheduledJobHealthService scheduledJobHealthService;

    @Value("${app.schedule.job-run-retention-days:30}")
    private int retentionDays = 30;

    public ScheduledJobRunRetentionJob(
            ScheduledJobRunRepository scheduledJobRunRepository,
            ScheduledJobHealthService scheduledJobHealthService) {
        this.scheduledJobRunRepository = scheduledJobRunRepository;
        this.scheduledJobHealthService = scheduledJobHealthService;
    }

    @Scheduled(cron = "0 15 3 * * *", zone = "UTC")
    @Transactional
    public void pruneOldRuns() {
        Instant startedAt = Instant.now();
        try {
            Instant cutoff = startedAt.minus(retentionDays, ChronoUnit.DAYS);
            int removed = scheduledJobRunRepository.deleteOlderThan(cutoff);
            if (removed > 0) {
                log.info("ScheduledJobRunRetentionJob: pruned {} job-run row(s) older than {} days.",
                        removed, retentionDays);
            }
            scheduledJobHealthService.recordSuccess("ScheduledJobRunRetentionJob", startedAt);
        } catch (Exception ex) {
            log.error("ScheduledJobRunRetentionJob failed: {}", ex.getMessage(), ex);
            scheduledJobHealthService.recordFailure("ScheduledJobRunRetentionJob", startedAt, ex);
        }
    }
}

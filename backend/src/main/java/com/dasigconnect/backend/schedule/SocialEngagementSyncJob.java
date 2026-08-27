package com.dasigconnect.backend.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

import com.dasigconnect.backend.service.ScheduledJobHealthService;
import com.dasigconnect.backend.service.SocialEngagementSyncService;

/** UC-3.4: periodically syncs Facebook engagement metrics for published posts. */
@Component
public class SocialEngagementSyncJob {

    private static final Logger log = LoggerFactory.getLogger(SocialEngagementSyncJob.class);

    private final SocialEngagementSyncService syncService;
    private final ScheduledJobHealthService scheduledJobHealthService;

    public SocialEngagementSyncJob(
            SocialEngagementSyncService syncService,
            ScheduledJobHealthService scheduledJobHealthService) {
        this.syncService = syncService;
        this.scheduledJobHealthService = scheduledJobHealthService;
    }

    @Scheduled(cron = "${app.social-engagement.sync-cron:0 */15 * * * *}", zone = "UTC")
    public void syncPendingEngagement() {
        Instant startedAt = Instant.now();
        try {
            int synced = syncService.syncPending();
            if (synced > 0) {
                log.info("SocialEngagementSyncJob: synced engagement for {} submissions.", synced);
            }
            scheduledJobHealthService.recordSuccess("SocialEngagementSyncJob", startedAt);
        } catch (Exception ex) {
            log.warn("SocialEngagementSyncJob failed: {}", ex.getMessage());
            scheduledJobHealthService.recordFailure("SocialEngagementSyncJob", startedAt, ex);
        }
    }
}

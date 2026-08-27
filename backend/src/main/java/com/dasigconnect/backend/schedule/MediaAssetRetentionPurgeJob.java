package com.dasigconnect.backend.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.dasigconnect.backend.service.MediaAssetRetentionService;

@Component
public class MediaAssetRetentionPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(MediaAssetRetentionPurgeJob.class);

    private final MediaAssetRetentionService retentionService;
    private final com.dasigconnect.backend.service.ScheduledJobHealthService scheduledJobHealthService;

    public MediaAssetRetentionPurgeJob(
            MediaAssetRetentionService retentionService,
            com.dasigconnect.backend.service.ScheduledJobHealthService scheduledJobHealthService) {
        this.retentionService = retentionService;
        this.scheduledJobHealthService = scheduledJobHealthService;
    }

    @Scheduled(cron = "${app.media-assets.purge-cron:0 30 2 * * *}", zone = "UTC")
    public void purgeExpiredDeletedAssets() {
        java.time.Instant startedAt = java.time.Instant.now();
        try {
            int purged = retentionService.purgeExpiredDeletedAssets();
            if (purged > 0) {
                log.info("MediaAssetRetentionPurgeJob: purged {} deleted media assets.", purged);
            }
            scheduledJobHealthService.recordSuccess("MediaAssetRetentionPurgeJob", startedAt);
        } catch (Exception ex) {
            log.warn("MediaAssetRetentionPurgeJob failed: {}", ex.getMessage());
            scheduledJobHealthService.recordFailure("MediaAssetRetentionPurgeJob", startedAt, ex);
        }
    }
}

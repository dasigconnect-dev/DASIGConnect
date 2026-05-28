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

    public MediaAssetRetentionPurgeJob(MediaAssetRetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @Scheduled(cron = "${app.media-assets.purge-cron:0 30 2 * * *}", zone = "UTC")
    public void purgeExpiredDeletedAssets() {
        try {
            int purged = retentionService.purgeExpiredDeletedAssets();
            if (purged > 0) {
                log.info("MediaAssetRetentionPurgeJob: purged {} deleted media assets.", purged);
            }
        } catch (Exception ex) {
            log.warn("MediaAssetRetentionPurgeJob failed: {}", ex.getMessage());
        }
    }
}

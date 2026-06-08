package com.dasigconnect.backend.schedule;

import com.dasigconnect.backend.service.FacebookPageAudienceService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * UC-4.8 Phase 5b: stores one page-audience snapshot per day so follower growth has a series.
 * Idempotent enough for a time series (each run appends at most one snapshot); overlap-guarded.
 */
@Component
public class FacebookPageAudienceSyncJob {

    private static final Logger log = LoggerFactory.getLogger(FacebookPageAudienceSyncJob.class);

    private final FacebookPageAudienceService audienceService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public FacebookPageAudienceSyncJob(FacebookPageAudienceService audienceService) {
        this.audienceService = audienceService;
    }

    @Scheduled(cron = "${app.facebook.audience.cron:0 30 7 * * *}", zone = "UTC")
    public void syncDailyAudience() {
        if (!running.compareAndSet(false, true)) {
            log.info("FacebookPageAudienceSyncJob skipped because a previous run is still active.");
            return;
        }
        try {
            if (audienceService.syncAudience()) {
                log.info("FacebookPageAudienceSyncJob: stored a page-audience snapshot.");
            }
        } catch (Exception ex) {
            log.warn("FacebookPageAudienceSyncJob failed: {}", ex.getMessage());
        } finally {
            running.set(false);
        }
    }
}

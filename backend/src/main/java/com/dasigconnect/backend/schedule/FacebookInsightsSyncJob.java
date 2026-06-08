package com.dasigconnect.backend.schedule;

import com.dasigconnect.backend.service.FacebookInsightsSyncService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FacebookInsightsSyncJob {

    private static final Logger log = LoggerFactory.getLogger(FacebookInsightsSyncJob.class);

    private final FacebookInsightsSyncService syncService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public FacebookInsightsSyncJob(FacebookInsightsSyncService syncService) {
        this.syncService = syncService;
    }

    @Scheduled(cron = "${app.facebook.insights.cron:0 15 */6 * * *}", zone = "UTC")
    public void syncDuePostMetrics() {
        if (!running.compareAndSet(false, true)) {
            log.info("FacebookInsightsSyncJob skipped because a previous run is still active.");
            return;
        }
        try {
            var result = syncService.syncDueMetrics();
            if (result.synced() > 0) {
                log.info("FacebookInsightsSyncJob: synced metrics for {} published post(s).", result.synced());
            } else if (result.failed() > 0) {
                log.warn("FacebookInsightsSyncJob: {} due post(s), all {} fetch(es) failed. {}",
                        result.due(), result.failed(), result.reason());
            }
        } catch (Exception ex) {
            log.warn("FacebookInsightsSyncJob failed: {}", ex.getMessage());
        } finally {
            running.set(false);
        }
    }
}

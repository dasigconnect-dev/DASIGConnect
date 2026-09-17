package com.dasigconnect.backend.schedule;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.dasigconnect.backend.service.MediaStorageService;
import com.dasigconnect.backend.service.ScheduledJobHealthService;

/**
 * UC-2.5: watermarked-derivative images generated at publish time
 * ({@code MediaStorageService.generatedWatermarkPath}) live entirely outside
 * the {@code media_assets} retention/purge lifecycle — nothing else ever
 * cleans them up, and they accumulate in R2 forever otherwise. Facebook only
 * ever fetches the URL once, at publish time, so a short retention window
 * is safe.
 */
@Component
public class GeneratedWatermarkPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(GeneratedWatermarkPurgeJob.class);

    private final MediaStorageService mediaStorage;
    private final ScheduledJobHealthService scheduledJobHealthService;
    private final int retentionDays;

    public GeneratedWatermarkPurgeJob(
            MediaStorageService mediaStorage,
            ScheduledJobHealthService scheduledJobHealthService,
            @Value("${app.watermark.generated-retention-days:3}") int retentionDays) {
        this.mediaStorage = mediaStorage;
        this.scheduledJobHealthService = scheduledJobHealthService;
        this.retentionDays = Math.max(retentionDays, 1);
    }

    @Scheduled(cron = "${app.watermark.purge-cron:0 45 2 * * *}", zone = "UTC")
    public void purgeExpiredGeneratedWatermarks() {
        Instant startedAt = Instant.now();
        try {
            int purged = mediaStorage.purgeExpiredGeneratedWatermarks(Duration.ofDays(retentionDays));
            if (purged > 0) {
                log.info("GeneratedWatermarkPurgeJob: purged {} generated watermark object(s).", purged);
            }
            scheduledJobHealthService.recordSuccess("GeneratedWatermarkPurgeJob", startedAt);
        } catch (Exception ex) {
            log.error("GeneratedWatermarkPurgeJob failed: {}", ex.getMessage(), ex);
            scheduledJobHealthService.recordFailure("GeneratedWatermarkPurgeJob", startedAt, ex);
        }
    }
}

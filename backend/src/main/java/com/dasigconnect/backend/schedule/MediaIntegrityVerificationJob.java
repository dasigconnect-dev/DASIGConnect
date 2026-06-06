package com.dasigconnect.backend.schedule;

import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.MediaIntegrityTarget;
import com.dasigconnect.backend.service.MediaIntegrityService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MediaIntegrityVerificationJob {

    private static final Logger log = LoggerFactory.getLogger(MediaIntegrityVerificationJob.class);
    private static final int MAX_BATCH_SIZE = 25;

    private final MediaAssetRepository mediaAssetRepository;
    private final MediaIntegrityService mediaIntegrityService;
    private final int batchSize;
    private final int verifiedIntervalDays;
    private final int failureRetryHours;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public MediaIntegrityVerificationJob(
            MediaAssetRepository mediaAssetRepository,
            MediaIntegrityService mediaIntegrityService,
            @Value("${app.media-integrity.batch-size:25}") int batchSize,
            @Value("${app.media-integrity.verified-interval-days:30}") int verifiedIntervalDays,
            @Value("${app.media-integrity.failure-retry-hours:24}") int failureRetryHours) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.mediaIntegrityService = mediaIntegrityService;
        this.batchSize = Math.min(Math.max(batchSize, 1), MAX_BATCH_SIZE);
        this.verifiedIntervalDays = Math.max(verifiedIntervalDays, 1);
        this.failureRetryHours = Math.max(failureRetryHours, 1);
    }

    @Scheduled(cron = "${app.media-integrity.cron:0 0 3 * * *}", zone = "UTC")
    public void verifyDueAssets() {
        if (!running.compareAndSet(false, true)) {
            log.info("MediaIntegrityVerificationJob skipped because a previous run is still active.");
            return;
        }
        try {
            Instant now = Instant.now();
            List<MediaIntegrityTarget> due = mediaAssetRepository.findDueIntegrityTargets(
                    now.minus(verifiedIntervalDays, ChronoUnit.DAYS),
                    now.minus(failureRetryHours, ChronoUnit.HOURS),
                    PageRequest.of(0, batchSize));
            if (due.isEmpty()) {
                return;
            }

            log.info("MediaIntegrityVerificationJob: verifying {} due asset(s).", due.size());
            for (MediaIntegrityTarget target : due) {
                try {
                    mediaIntegrityService.verifyScheduled(target);
                } catch (Exception ex) {
                    log.warn("Scheduled integrity verification failed for asset {}: {}",
                            target.assetId(), ex.getMessage());
                }
            }
        } finally {
            running.set(false);
        }
    }
}

package com.dasigconnect.backend.schedule;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.dasigconnect.backend.event.EmbeddingFailureDigestEvent;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.service.ScheduledJobHealthService;

/**
 * T-12: Weekly scan of EMBEDDING_FAILED assets.
 * If failed assets exist, dispatches a summary digest notification to the
 * Admin.
 */
@Component
public class EmbeddingFailureDigestJob {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingFailureDigestJob.class);

    private final MediaAssetRepository mediaAssetRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ScheduledJobHealthService scheduledJobHealthService;

    public EmbeddingFailureDigestJob(
            MediaAssetRepository mediaAssetRepository,
            ApplicationEventPublisher eventPublisher,
            ScheduledJobHealthService scheduledJobHealthService) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.eventPublisher = eventPublisher;
        this.scheduledJobHealthService = scheduledJobHealthService;
    }

    @Scheduled(cron = "${app.schedule.embedding-digest-cron:0 0 9 * * MON}")
    public void scanFailedEmbeddings() {
        Instant startedAt = Instant.now();
        log.info("Running EmbeddingFailureDigestJob scan");
        try {
            long failedCount = mediaAssetRepository.countFailedAssets();
            if (failedCount > 0) {
                log.info("Found {} failed media asset embeddings — generating T-12 digest alert", failedCount);
                List<String> sampleNames = mediaAssetRepository.findSampleFailedFilenames();
                eventPublisher.publishEvent(new EmbeddingFailureDigestEvent(failedCount, sampleNames));
            }
            scheduledJobHealthService.recordSuccess("EmbeddingFailureDigestJob", startedAt);
        } catch (Exception ex) {
            log.error("EmbeddingFailureDigestJob failed: {}", ex.getMessage(), ex);
            scheduledJobHealthService.recordFailure("EmbeddingFailureDigestJob", startedAt, ex);
        }
    }
}

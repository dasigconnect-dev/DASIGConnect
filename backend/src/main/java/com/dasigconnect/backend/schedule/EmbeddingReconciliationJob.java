package com.dasigconnect.backend.schedule;

import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.service.MediaProcessingQueueService;
import com.dasigconnect.backend.service.ScheduledJobHealthService;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Re-enqueues active assets whose processing is incomplete. The durable queue
 * owns provider calls, leases, retries, and dead-letter handling.
 */
@Component
public class EmbeddingReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingReconciliationJob.class);

    private final MediaAssetRepository mediaAssetRepository;
    private final MediaProcessingQueueService queueService;
    private final ScheduledJobHealthService healthService;
    private final boolean aiConfigured;

    public EmbeddingReconciliationJob(
            MediaAssetRepository mediaAssetRepository,
            MediaProcessingQueueService queueService,
            ScheduledJobHealthService healthService,
            @Value("${anthropic.api.key:}") String anthropicApiKey,
            @Value("${voyage.api.key:}") String voyageApiKey) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.queueService = queueService;
        this.healthService = healthService;
        this.aiConfigured = !anthropicApiKey.isBlank() || !voyageApiKey.isBlank();
    }

    @Scheduled(fixedDelayString = "${app.media-processing.reconcile-delay-ms:300000}")
    public void reconcile() {
        if (!aiConfigured) return;
        Instant startedAt = Instant.now();
        try {
            List<MediaAsset> pending = mediaAssetRepository.findNeedingEmbedding();
            for (MediaAsset asset : pending) queueService.enqueue(asset.getId());
            if (!pending.isEmpty()) {
                log.info("EmbeddingReconciliationJob: enqueued {} incomplete assets", pending.size());
            }
            healthService.recordSuccess("EmbeddingReconciliationJob", startedAt);
        } catch (Exception error) {
            log.error("EmbeddingReconciliationJob failed: {}", error.getMessage(), error);
            healthService.recordFailure("EmbeddingReconciliationJob", startedAt, error);
        }
    }
}

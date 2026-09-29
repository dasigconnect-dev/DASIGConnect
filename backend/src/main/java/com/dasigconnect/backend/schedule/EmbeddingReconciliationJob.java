package com.dasigconnect.backend.schedule;

import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.external.VoyageAIClient;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.service.MediaProcessingQueueService;
import com.dasigconnect.backend.service.ScheduledJobHealthService;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
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
    private final VoyageAIClient voyageAIClient;
    private final boolean retrievalConfigured;
    private final boolean imageEmbeddingConfigured;
    private final int backfillBatchSize;
    private final int backfillPerInstitution;

    public EmbeddingReconciliationJob(
            MediaAssetRepository mediaAssetRepository,
            MediaProcessingQueueService queueService,
            ScheduledJobHealthService healthService,
            VoyageAIClient voyageAIClient,
            @Value("${anthropic.api.key:}") String anthropicApiKey,
            @Value("${voyage.api.key:}") String voyageApiKey,
            @Value("${app.media-processing.backfill-batch-size:10}") int backfillBatchSize,
            @Value("${app.media-processing.backfill-per-institution:2}") int backfillPerInstitution) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.queueService = queueService;
        this.healthService = healthService;
        this.voyageAIClient = voyageAIClient;
        this.retrievalConfigured = !voyageApiKey.isBlank();
        this.imageEmbeddingConfigured = !voyageApiKey.isBlank();
        this.backfillBatchSize = Math.max(1, Math.min(backfillBatchSize, 25));
        this.backfillPerInstitution = Math.max(1, Math.min(backfillPerInstitution, 10));
    }

    @Scheduled(fixedDelayString = "${app.media-processing.reconcile-delay-ms:300000}")
    public void reconcile() {
        if (!retrievalConfigured) return;
        Instant startedAt = Instant.now();
        try {
            int availableSlots = queueService.availableBackfillSlots(backfillBatchSize);
            if (availableSlots == 0) {
                log.info("EmbeddingReconciliationJob: queue at configured capacity; backfill deferred");
                healthService.recordSuccess("EmbeddingReconciliationJob", startedAt);
                return;
            }
            List<MediaAsset> draftImages = imageEmbeddingConfigured
                    ? mediaAssetRepository.findDraftImagesMissingImageEmbedding(
                            PageRequest.of(0, availableSlots))
                    : List.of();
            for (MediaAsset asset : draftImages) queueService.enqueueImageOnly(asset.getId());
            int classificationSlots = availableSlots - draftImages.size();
            List<MediaAsset> pending = classificationSlots > 0
                    ? mediaAssetRepository.findNeedingProcessingVersion(
                            MediaProcessingQueueService.PROCESSING_VERSION,
                            PageRequest.of(0, classificationSlots))
                    : List.of();
            for (MediaAsset asset : pending) queueService.enqueue(asset.getId());
            int backfillSlots = imageEmbeddingConfigured
                    ? classificationSlots - pending.size()
                    : 0;
            List<MediaAsset> backfill = backfillSlots > 0
                    ? mediaAssetRepository.findFairReadyImagesNeedingCurrentEmbeddings(
                            voyageAIClient.multimodalModelName(),
                            MediaProcessingQueueService.IMAGE_EMBEDDING_VERSION,
                            voyageAIClient.modelName(),
                            MediaProcessingQueueService.SEMANTIC_EMBEDDING_VERSION,
                            MediaProcessingQueueService.PROCESSING_VERSION,
                            backfillPerInstitution,
                            PageRequest.of(0, backfillSlots))
                    : List.of();
            for (MediaAsset asset : backfill) queueService.enqueueBackfill(asset.getId());
            if (!draftImages.isEmpty() || !pending.isEmpty() || !backfill.isEmpty()) {
                log.info("EmbeddingReconciliationJob: enqueued {} draft images, {} incomplete assets, "
                                + "and {} institution-fair historical backfills",
                        draftImages.size(), pending.size(), backfill.size());
            }
            healthService.recordSuccess("EmbeddingReconciliationJob", startedAt);
        } catch (Exception error) {
            log.error("EmbeddingReconciliationJob failed: {}", error.getMessage(), error);
            healthService.recordFailure("EmbeddingReconciliationJob", startedAt, error);
        }
    }
}

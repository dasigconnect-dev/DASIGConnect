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
        if (!retrievalConfigured && !queueService.isEnrichmentConfigured()) return;
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
            for (MediaAsset asset : draftImages) queueService.enqueueDraftAnalysis(asset.getId());
            int draftClassificationSlots = availableSlots - draftImages.size();
            java.util.Set<java.util.UUID> queuedDraftIds = draftImages.stream()
                    .map(MediaAsset::getId)
                    .collect(java.util.stream.Collectors.toSet());
            List<MediaAsset> draftClassifications = draftClassificationSlots > 0
                    && queueService.isEnrichmentConfigured()
                    ? mediaAssetRepository.findDraftImagesMissingClassification(
                            PageRequest.of(0, draftClassificationSlots))
                            .stream()
                            .filter(asset -> !queuedDraftIds.contains(asset.getId()))
                            .toList()
                    : List.of();
            for (MediaAsset asset : draftClassifications) {
                queueService.enqueueEnrichmentIfConfigured(asset.getId());
            }
            int historicalClassificationSlots = draftClassificationSlots - draftClassifications.size();
            int historicalClassificationLimit = Math.min(
                    historicalClassificationSlots,
                    Math.max(1, availableSlots / 2));
            List<MediaAsset> historicalClassifications = historicalClassificationLimit > 0
                    && queueService.isEnrichmentConfigured()
                    ? mediaAssetRepository.findFairReadyImagesMissingClassification(
                            MediaProcessingQueueService.ENRICHMENT_VERSION,
                            backfillPerInstitution,
                            PageRequest.of(0, historicalClassificationLimit))
                    : List.of();
            for (MediaAsset asset : historicalClassifications) {
                queueService.enqueueEnrichmentIfConfigured(asset.getId());
            }
            int processingSlots = historicalClassificationSlots - historicalClassifications.size();
            List<MediaAsset> pending = processingSlots > 0
                    ? mediaAssetRepository.findNeedingProcessingVersion(
                            MediaProcessingQueueService.PROCESSING_VERSION,
                            PageRequest.of(0, processingSlots))
                    : List.of();
            for (MediaAsset asset : pending) queueService.enqueue(asset.getId());
            int backfillSlots = imageEmbeddingConfigured
                    ? processingSlots - pending.size()
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
            if (!draftImages.isEmpty() || !draftClassifications.isEmpty()
                    || !historicalClassifications.isEmpty()
                    || !pending.isEmpty() || !backfill.isEmpty()) {
                log.info("EmbeddingReconciliationJob: enqueued {} draft image vectors, "
                                + "{} draft classifications, {} historical classifications, "
                                + "{} incomplete assets, "
                                + "and {} institution-fair historical backfills",
                        draftImages.size(), draftClassifications.size(), historicalClassifications.size(),
                        pending.size(), backfill.size());
            }
            healthService.recordSuccess("EmbeddingReconciliationJob", startedAt);
        } catch (Exception error) {
            log.error("EmbeddingReconciliationJob failed: {}", error.getMessage(), error);
            healthService.recordFailure("EmbeddingReconciliationJob", startedAt, error);
        }
    }
}

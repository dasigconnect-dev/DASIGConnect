package com.dasigconnect.backend.schedule;

import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaProcessingJob;
import com.dasigconnect.backend.model.entity.MediaProcessingJobType;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionMediaAssetRepository;
import com.dasigconnect.backend.service.AIClassificationService;
import com.dasigconnect.backend.service.MediaProcessingQueueService;
import com.dasigconnect.backend.service.MediaImageEmbeddingService;
import com.dasigconnect.backend.service.MediaRetrievalEmbeddingService;
import com.dasigconnect.backend.service.MediaSemanticEmbeddingService;
import com.dasigconnect.backend.service.ScheduledJobHealthService;
import com.dasigconnect.backend.service.SubmissionMediaContextService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MediaProcessingWorker {

    private static final Logger log = LoggerFactory.getLogger(MediaProcessingWorker.class);

    private final MediaProcessingQueueService queue;
    private final MediaAssetRepository mediaAssetRepository;
    private final AIClassificationService classificationService;
    private final MediaImageEmbeddingService imageEmbeddingService;
    private final MediaRetrievalEmbeddingService retrievalEmbeddingService;
    private final MediaSemanticEmbeddingService semanticEmbeddingService;
    private final ScheduledJobHealthService healthService;
    private final SubmissionMediaContextService contextService;
    private final SubmissionMediaAssetRepository submissionMediaAssetRepository;
    private final int batchSize;
    private final boolean enrichmentConfigured;
    private final boolean imageEmbeddingConfigured;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.dasigconnect.backend.service.MediaAiTelemetryService mediaAiTelemetry;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.dasigconnect.backend.service.MediaSearchCacheService mediaSearchCache;

    public MediaProcessingWorker(
            MediaProcessingQueueService queue,
            MediaAssetRepository mediaAssetRepository,
            AIClassificationService classificationService,
            MediaImageEmbeddingService imageEmbeddingService,
            MediaRetrievalEmbeddingService retrievalEmbeddingService,
            MediaSemanticEmbeddingService semanticEmbeddingService,
            ScheduledJobHealthService healthService,
            SubmissionMediaContextService contextService,
            SubmissionMediaAssetRepository submissionMediaAssetRepository,
            @Value("${app.media-processing.batch-size:2}") int batchSize,
            @Value("${anthropic.api.key:}") String anthropicApiKey,
            @Value("${voyage.api.key:}") String voyageApiKey) {
        this.queue = queue;
        this.mediaAssetRepository = mediaAssetRepository;
        this.classificationService = classificationService;
        this.imageEmbeddingService = imageEmbeddingService;
        this.retrievalEmbeddingService = retrievalEmbeddingService;
        this.semanticEmbeddingService = semanticEmbeddingService;
        this.healthService = healthService;
        this.contextService = contextService;
        this.submissionMediaAssetRepository = submissionMediaAssetRepository;
        this.batchSize = Math.max(1, Math.min(batchSize, 10));
        this.enrichmentConfigured = !anthropicApiKey.isBlank();
        this.imageEmbeddingConfigured = !voyageApiKey.isBlank();
    }

    @Scheduled(fixedDelayString = "${app.media-processing.poll-delay-ms:1000}")
    public void processBatch() {
        Instant startedAt = Instant.now();
        String workerId = UUID.randomUUID().toString();
        try {
            List<MediaProcessingJob> jobs = queue.claimBatch(
                    workerId, batchSize, enrichmentConfigured, imageEmbeddingConfigured);
            if (jobs.isEmpty()) return;
            for (MediaProcessingJob job : jobs) process(job, workerId);
            healthService.recordSuccess("MediaProcessingWorker", startedAt);
        } catch (Exception error) {
            log.error("MediaProcessingWorker failed: {}", error.getMessage(), error);
            healthService.recordFailure("MediaProcessingWorker", startedAt, error);
        }
    }

    private void process(MediaProcessingJob job, String workerId) {
        if (mediaAiTelemetry != null) {
            mediaAiTelemetry.record("QUEUE_DELAY",
                    Math.max(0, Duration.between(job.getCreatedAt(), Instant.now()).toMillis()),
                    "SUCCESS", job.getAssetId(), job.getSubmissionId(),
                    job.getAttemptCount(), 0, 0);
        }
        try {
            if (job.getJobType() == MediaProcessingJobType.BUILD_SUBMISSION_CONTEXT) {
                contextService.rebuild(job.getSubmissionId());
                queue.complete(job, workerId);
                return;
            }
            MediaAsset asset = mediaAssetRepository.findActiveById(job.getAssetId()).orElse(null);
            if (asset == null || asset.getFileType() == null) {
                queue.complete(job, workerId);
                return;
            }
            if (job.getJobType() == MediaProcessingJobType.EMBED_IMAGE_ONLY) {
                if (!asset.getFileType().isImage()) {
                    queue.complete(job, workerId);
                    return;
                }
                if (!imageEmbeddingService.generateOrReuse(asset.getId(), asset.getStorageUrl())) {
                    throw new IllegalStateException("Image embedding did not complete");
                }
                queue.complete(job, workerId);
                return;
            }
            if (job.getJobType() == MediaProcessingJobType.EMBED_SEMANTIC_ONLY) {
                if (!semanticEmbeddingService.generateOrReuse(asset.getId())) {
                    throw new IllegalStateException("Semantic embedding did not complete");
                }
                submissionMediaAssetRepository.findSubmissionIdsByMediaAssetId(asset.getId())
                        .forEach(queue::enqueueSubmissionContext);
                queue.complete(job, workerId);
                return;
            }
            if (job.getJobType() == MediaProcessingJobType.CLASSIFY_AND_EMBED
                    || job.getJobType() == MediaProcessingJobType.ENRICH_MEDIA) {
                if (!classificationService.enrichAsset(asset.getId(), asset.getStorageUrl())) {
                    throw new IllegalStateException("Optional Claude enrichment did not complete");
                }
                queue.complete(job, workerId);
                return;
            }
            if (!retrievalEmbeddingService.generateOrReuse(
                    asset.getId(), asset.getStorageUrl(), asset.getFileType().isImage())) {
                throw new IllegalStateException("Voyage retrieval embeddings did not complete");
            }
            mediaAssetRepository.markProcessingReady(asset.getId(), job.getProcessingVersion());
            if (mediaSearchCache != null) mediaSearchCache.invalidateAll();
            if (mediaAiTelemetry != null && asset.getCreatedAt() != null) {
                mediaAiTelemetry.record("READY_LATENCY",
                        Math.max(0, Duration.between(asset.getCreatedAt(), Instant.now()).toMillis()),
                        "SUCCESS", asset.getId(), null, job.getAttemptCount(), 0, 0);
            }
            submissionMediaAssetRepository.findSubmissionIdsByMediaAssetId(asset.getId())
                    .forEach(queue::enqueueSubmissionContext);
            queue.complete(job, workerId);
            if (enrichmentConfigured && asset.getFileType().isImage()) {
                try {
                    queue.enqueueEnrichment(asset.getId());
                } catch (Exception error) {
                    log.warn("Failed to enqueue optional enrichment for asset {}: {}",
                            asset.getId(), error.getMessage());
                }
            }
        } catch (Exception error) {
            if (mediaAiTelemetry != null
                    && job.getJobType() == MediaProcessingJobType.RETRIEVAL_EMBEDDINGS) {
                mediaAiTelemetry.record("READY_LATENCY", 0, "FAILURE",
                        job.getAssetId(), job.getSubmissionId(), job.getAttemptCount(), 0, 0);
            }
            log.warn("Media processing attempt {} failed for asset {}: {}",
                    job.getAttemptCount(), job.getAssetId(), error.getMessage());
            queue.fail(job, workerId, error);
        }
    }
}

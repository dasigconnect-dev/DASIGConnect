package com.dasigconnect.backend.schedule;

import com.dasigconnect.backend.model.entity.MediaAsset;
import com.dasigconnect.backend.model.entity.MediaProcessingJob;
import com.dasigconnect.backend.model.entity.MediaProcessingJobType;
import com.dasigconnect.backend.repository.MediaAssetRepository;
import com.dasigconnect.backend.repository.SubmissionMediaAssetRepository;
import com.dasigconnect.backend.service.AIClassificationService;
import com.dasigconnect.backend.service.MediaProcessingQueueService;
import com.dasigconnect.backend.service.ScheduledJobHealthService;
import com.dasigconnect.backend.service.SubmissionMediaContextService;
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
    private final ScheduledJobHealthService healthService;
    private final SubmissionMediaContextService contextService;
    private final SubmissionMediaAssetRepository submissionMediaAssetRepository;
    private final int batchSize;
    private final boolean aiConfigured;

    public MediaProcessingWorker(
            MediaProcessingQueueService queue,
            MediaAssetRepository mediaAssetRepository,
            AIClassificationService classificationService,
            ScheduledJobHealthService healthService,
            SubmissionMediaContextService contextService,
            SubmissionMediaAssetRepository submissionMediaAssetRepository,
            @Value("${app.media-processing.batch-size:2}") int batchSize,
            @Value("${anthropic.api.key:}") String anthropicApiKey,
            @Value("${voyage.api.key:}") String voyageApiKey) {
        this.queue = queue;
        this.mediaAssetRepository = mediaAssetRepository;
        this.classificationService = classificationService;
        this.healthService = healthService;
        this.contextService = contextService;
        this.submissionMediaAssetRepository = submissionMediaAssetRepository;
        this.batchSize = Math.max(1, Math.min(batchSize, 10));
        this.aiConfigured = !anthropicApiKey.isBlank() || !voyageApiKey.isBlank();
    }

    @Scheduled(fixedDelayString = "${app.media-processing.poll-delay-ms:5000}")
    public void processBatch() {
        Instant startedAt = Instant.now();
        String workerId = UUID.randomUUID().toString();
        try {
            List<MediaProcessingJob> jobs = queue.claimBatch(workerId, batchSize, aiConfigured);
            if (jobs.isEmpty()) return;
            for (MediaProcessingJob job : jobs) process(job, workerId);
            healthService.recordSuccess("MediaProcessingWorker", startedAt);
        } catch (Exception error) {
            log.error("MediaProcessingWorker failed: {}", error.getMessage(), error);
            healthService.recordFailure("MediaProcessingWorker", startedAt, error);
        }
    }

    private void process(MediaProcessingJob job, String workerId) {
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
            if (!classificationService.processAsset(asset.getId(), asset.getStorageUrl())) {
                throw new IllegalStateException("AI media processing did not complete");
            }
            mediaAssetRepository.markProcessingReady(asset.getId(), job.getProcessingVersion());
            submissionMediaAssetRepository.findSubmissionIdsByMediaAssetId(asset.getId())
                    .forEach(queue::enqueueSubmissionContext);
            queue.complete(job, workerId);
        } catch (Exception error) {
            log.warn("Media processing attempt {} failed for asset {}: {}",
                    job.getAttemptCount(), job.getAssetId(), error.getMessage());
            queue.fail(job, workerId, error);
        }
    }
}

package com.dasigconnect.backend.service;

import com.dasigconnect.backend.model.entity.MediaProcessingJob;
import com.dasigconnect.backend.model.entity.MediaProcessingJobStatus;
import com.dasigconnect.backend.repository.MediaProcessingJobRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MediaProcessingQueueService {

    public static final String PROCESSING_VERSION = "media-ai-v2";
    public static final String IMAGE_EMBEDDING_VERSION = "image-embedding-v1";
    public static final String CONTEXT_VERSION = "submission-context-v1";
    private static final int MAX_ERROR_LENGTH = 500;
    private static final Logger log = LoggerFactory.getLogger(MediaProcessingQueueService.class);

    private final MediaProcessingJobRepository repository;
    private final int maxAttempts;
    private final Duration leaseDuration;
    private final int maxJobsPerInstitutionPerBatch;
    private final int maxQueueDepth;

    public MediaProcessingQueueService(
            MediaProcessingJobRepository repository,
            @Value("${app.media-processing.max-attempts:5}") int maxAttempts,
            @Value("${app.media-processing.lease-seconds:300}") long leaseSeconds,
            @Value("${app.media-processing.max-jobs-per-institution-per-batch:2}") int maxJobsPerInstitutionPerBatch,
            @Value("${app.media-processing.max-queue-depth:100}") int maxQueueDepth) {
        this.repository = repository;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.leaseDuration = Duration.ofSeconds(Math.max(30, leaseSeconds));
        this.maxJobsPerInstitutionPerBatch = Math.max(1, Math.min(maxJobsPerInstitutionPerBatch, 10));
        this.maxQueueDepth = Math.max(10, maxQueueDepth);
    }

    public void enqueue(UUID assetId) {
        repository.enqueue(assetId, PROCESSING_VERSION, maxAttempts);
    }

    public void enqueueAfterCommit(UUID assetId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            enqueueSafely(assetId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                enqueueSafely(assetId);
            }
        });
    }

    public void enqueueImageOnly(UUID assetId) {
        repository.enqueueImageOnly(assetId, IMAGE_EMBEDDING_VERSION, maxAttempts);
    }

    public void enqueueImageOnlyAfterCommit(UUID assetId) {
        runAfterCommit(() -> enqueueImageOnlySafely(assetId));
    }

    public void enqueueSubmissionContext(UUID submissionId) {
        repository.enqueueSubmissionContext(submissionId, CONTEXT_VERSION, maxAttempts);
    }

    public void enqueueSubmissionContextAfterCommit(UUID submissionId) {
        runAfterCommit(() -> enqueueSubmissionContextSafely(submissionId));
    }

    public List<MediaProcessingJob> claimBatch(
            String workerId,
            int requestedBatchSize,
            boolean includeAiJobs,
            boolean includeImageJobs) {
        Instant now = Instant.now();
        int batchSize = Math.max(1, Math.min(requestedBatchSize, 10));
        repository.claimBatch(workerId, now, now.plus(leaseDuration), batchSize,
                maxJobsPerInstitutionPerBatch, includeAiJobs, includeImageJobs);
        return repository.findByClaimedByAndStatusOrderByCreatedAtAsc(
                workerId, MediaProcessingJobStatus.PROCESSING);
    }

    public void complete(MediaProcessingJob job, String workerId) {
        repository.complete(job.getId(), workerId, Instant.now());
    }

    public void fail(MediaProcessingJob job, String workerId, Throwable error) {
        Instant now = Instant.now();
        boolean exhausted = job.getAttemptCount() >= job.getMaxAttempts();
        long delaySeconds = Math.min(3600L, 60L << Math.min(6, Math.max(0, job.getAttemptCount() - 1)));
        repository.releaseAfterFailure(
                job.getId(),
                workerId,
                exhausted ? MediaProcessingJobStatus.DEAD.name() : MediaProcessingJobStatus.RETRY.name(),
                exhausted ? now : now.plusSeconds(delaySeconds),
                sanitize(error),
                now);
    }

    public int availableBackfillSlots(int requested) {
        long available = Math.max(0L, (long) maxQueueDepth - repository.countActiveJobs());
        return (int) Math.min(Math.max(0, requested), available);
    }

    public List<MediaProcessingJob> deadLetters(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        return repository.findByStatusOrderByUpdatedAtDesc(
                MediaProcessingJobStatus.DEAD, PageRequest.of(0, limit));
    }

    public void retryDead(UUID jobId) {
        if (repository.retryDead(jobId, Instant.now()) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Media processing job is not in the dead-letter state.");
        }
    }

    private static String sanitize(Throwable error) {
        String message = error == null ? "Media processing failed" : error.getMessage();
        if (message == null || message.isBlank()) message = error.getClass().getSimpleName();
        message = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }

    private void enqueueSafely(UUID assetId) {
        try {
            enqueue(assetId);
        } catch (Exception error) {
            log.warn("Failed to enqueue media processing for asset {}: {}", assetId, error.getMessage());
        }
    }

    private void enqueueSubmissionContextSafely(UUID submissionId) {
        try {
            enqueueSubmissionContext(submissionId);
        } catch (Exception error) {
            log.warn("Failed to enqueue media context for submission {}: {}", submissionId, error.getMessage());
        }
    }

    private void enqueueImageOnlySafely(UUID assetId) {
        try {
            enqueueImageOnly(assetId);
        } catch (Exception error) {
            log.warn("Failed to enqueue image embedding for asset {}: {}", assetId, error.getMessage());
        }
    }

    private static void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}

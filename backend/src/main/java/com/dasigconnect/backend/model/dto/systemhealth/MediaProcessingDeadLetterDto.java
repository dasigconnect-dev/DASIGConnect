package com.dasigconnect.backend.model.dto.systemhealth;

import com.dasigconnect.backend.model.entity.MediaProcessingJob;
import java.time.Instant;
import java.util.UUID;

public record MediaProcessingDeadLetterDto(
        UUID id,
        UUID assetId,
        UUID submissionId,
        String jobType,
        String processingVersion,
        int attemptCount,
        int maxAttempts,
        String lastError,
        Instant updatedAt) {

    public static MediaProcessingDeadLetterDto from(MediaProcessingJob job) {
        return new MediaProcessingDeadLetterDto(
                job.getId(),
                job.getAssetId(),
                job.getSubmissionId(),
                job.getJobType().name(),
                job.getProcessingVersion(),
                job.getAttemptCount(),
                job.getMaxAttempts(),
                job.getLastError(),
                job.getUpdatedAt());
    }
}

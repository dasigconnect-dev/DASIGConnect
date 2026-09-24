package com.dasigconnect.backend.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "media_processing_jobs")
public class MediaProcessingJob {

    @Id
    private UUID id;

    @Column(name = "asset_id")
    private UUID assetId;

    @Column(name = "submission_id")
    private UUID submissionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 40)
    private MediaProcessingJobType jobType;

    @Column(name = "processing_version", nullable = false, length = 50)
    private String processingVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MediaProcessingJobStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "claimed_by", length = 100)
    private String claimedBy;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public UUID getId() { return id; }
    public UUID getAssetId() { return assetId; }
    public UUID getSubmissionId() { return submissionId; }
    public MediaProcessingJobType getJobType() { return jobType; }
    public String getProcessingVersion() { return processingVersion; }
    public MediaProcessingJobStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public int getMaxAttempts() { return maxAttempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public String getClaimedBy() { return claimedBy; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
}

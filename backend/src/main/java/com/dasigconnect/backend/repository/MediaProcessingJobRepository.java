package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.MediaProcessingJob;
import com.dasigconnect.backend.model.entity.MediaProcessingJobStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface MediaProcessingJobRepository extends JpaRepository<MediaProcessingJob, UUID> {

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO media_processing_jobs
            (asset_id, job_type, processing_version, max_attempts)
        VALUES (:assetId, 'CLASSIFY_AND_EMBED', :processingVersion, :maxAttempts)
        ON CONFLICT (asset_id, job_type, processing_version)
        DO UPDATE SET
            status = 'PENDING', attempt_count = 0,
            next_attempt_at = NOW(), lease_until = NULL, claimed_by = NULL,
            last_error = NULL, completed_at = NULL, updated_at = NOW()
        WHERE media_processing_jobs.status = 'COMPLETED'
        """, nativeQuery = true)
    int enqueue(@Param("assetId") UUID assetId,
                @Param("processingVersion") String processingVersion,
                @Param("maxAttempts") int maxAttempts);

    @Modifying
    @Transactional
    @Query(value = """
        WITH candidates AS (
            SELECT id
            FROM media_processing_jobs
            WHERE (
                status IN ('PENDING', 'RETRY') AND next_attempt_at <= :now
            ) OR (
                status = 'PROCESSING' AND lease_until < :now
            )
            ORDER BY next_attempt_at, created_at
            FOR UPDATE SKIP LOCKED
            LIMIT :batchSize
        )
        UPDATE media_processing_jobs job
        SET status = 'PROCESSING',
            attempt_count = attempt_count + 1,
            claimed_by = :workerId,
            lease_until = :leaseUntil,
            updated_at = :now
        FROM candidates
        WHERE job.id = candidates.id
        """, nativeQuery = true)
    int claimBatch(@Param("workerId") String workerId,
                   @Param("now") Instant now,
                   @Param("leaseUntil") Instant leaseUntil,
                   @Param("batchSize") int batchSize);

    List<MediaProcessingJob> findByClaimedByAndStatusOrderByCreatedAtAsc(
            String claimedBy, MediaProcessingJobStatus status);

    @Modifying
    @Transactional
    @Query(value = """
        UPDATE media_processing_jobs
        SET status = 'COMPLETED', completed_at = :completedAt,
            lease_until = NULL, claimed_by = NULL, last_error = NULL,
            updated_at = :completedAt
        WHERE id = :id AND claimed_by = :workerId AND status = 'PROCESSING'
        """, nativeQuery = true)
    int complete(@Param("id") UUID id,
                 @Param("workerId") String workerId,
                 @Param("completedAt") Instant completedAt);

    @Modifying
    @Transactional
    @Query(value = """
        UPDATE media_processing_jobs
        SET status = :status, next_attempt_at = :nextAttemptAt,
            lease_until = NULL, claimed_by = NULL, last_error = :lastError,
            updated_at = :updatedAt
        WHERE id = :id AND claimed_by = :workerId AND status = 'PROCESSING'
        """, nativeQuery = true)
    int releaseAfterFailure(@Param("id") UUID id,
                            @Param("workerId") String workerId,
                            @Param("status") String status,
                            @Param("nextAttemptAt") Instant nextAttemptAt,
                            @Param("lastError") String lastError,
                            @Param("updatedAt") Instant updatedAt);
}

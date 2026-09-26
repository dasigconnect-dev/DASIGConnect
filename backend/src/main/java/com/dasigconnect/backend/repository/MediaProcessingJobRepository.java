package com.dasigconnect.backend.repository;

import com.dasigconnect.backend.model.entity.MediaProcessingJob;
import com.dasigconnect.backend.model.entity.MediaProcessingJobStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
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
        WHERE media_processing_jobs.status IN ('COMPLETED', 'DEAD')
        """, nativeQuery = true)
    int enqueue(@Param("assetId") UUID assetId,
                @Param("processingVersion") String processingVersion,
                @Param("maxAttempts") int maxAttempts);

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO media_processing_jobs
            (asset_id, job_type, processing_version, max_attempts)
        VALUES (:assetId, 'EMBED_IMAGE_ONLY', :processingVersion, :maxAttempts)
        ON CONFLICT (asset_id, job_type, processing_version) WHERE asset_id IS NOT NULL
        DO UPDATE SET
            status = 'PENDING', attempt_count = 0,
            next_attempt_at = NOW(), lease_until = NULL, claimed_by = NULL,
            last_error = NULL, completed_at = NULL, updated_at = NOW()
        WHERE media_processing_jobs.status = 'COMPLETED'
        """, nativeQuery = true)
    int enqueueImageOnly(@Param("assetId") UUID assetId,
                         @Param("processingVersion") String processingVersion,
                         @Param("maxAttempts") int maxAttempts);

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO media_processing_jobs
            (submission_id, job_type, processing_version, max_attempts)
        VALUES (:submissionId, 'BUILD_SUBMISSION_CONTEXT', :processingVersion, :maxAttempts)
        ON CONFLICT (submission_id, job_type, processing_version) WHERE submission_id IS NOT NULL
        DO UPDATE SET
            status = CASE WHEN media_processing_jobs.status = 'PROCESSING'
                          THEN 'PROCESSING' ELSE 'PENDING' END,
            attempt_count = CASE WHEN media_processing_jobs.status = 'PROCESSING'
                                 THEN media_processing_jobs.attempt_count ELSE 0 END,
            next_attempt_at = CASE WHEN media_processing_jobs.status = 'PROCESSING'
                                   THEN media_processing_jobs.next_attempt_at ELSE NOW() END,
            lease_until = CASE WHEN media_processing_jobs.status = 'PROCESSING'
                               THEN media_processing_jobs.lease_until ELSE NULL END,
            claimed_by = CASE WHEN media_processing_jobs.status = 'PROCESSING'
                              THEN media_processing_jobs.claimed_by ELSE NULL END,
            last_error = CASE WHEN media_processing_jobs.status = 'PROCESSING'
                              THEN media_processing_jobs.last_error ELSE NULL END,
            completed_at = NULL,
            rerun_requested = media_processing_jobs.status = 'PROCESSING',
            updated_at = NOW()
        WHERE media_processing_jobs.status IN ('PROCESSING', 'COMPLETED', 'DEAD')
        """, nativeQuery = true)
    int enqueueSubmissionContext(@Param("submissionId") UUID submissionId,
                                 @Param("processingVersion") String processingVersion,
                                 @Param("maxAttempts") int maxAttempts);

    @Modifying
    @Transactional
    @Query(value = """
        WITH eligible_jobs AS (
            SELECT job.id,
                   COALESCE(asset.institution_id, submission.institution_id, job.id) AS institution_key,
                   CASE WHEN job.job_type = 'EMBED_IMAGE_ONLY' AND EXISTS (
                       SELECT 1
                       FROM submission_media_assets selected_media
                       JOIN submissions selected_submission
                         ON selected_submission.id = selected_media.submission_id
                       WHERE selected_media.media_asset_id = job.asset_id
                         AND selected_submission.status = 'draft'
                   ) THEN 0 ELSE 1 END AS interactive_priority,
                   job.next_attempt_at,
                   job.created_at
            FROM media_processing_jobs job
            LEFT JOIN media_assets asset ON asset.id = job.asset_id
            LEFT JOIN submissions submission ON submission.id = job.submission_id
            WHERE (
                (
                    job.status IN ('PENDING', 'RETRY') AND job.next_attempt_at <= :now
                ) OR (
                    job.status = 'PROCESSING' AND job.lease_until < :now
                )
            )
              AND (
                  job.job_type = 'BUILD_SUBMISSION_CONTEXT'
                  OR (:includeAiJobs = TRUE AND job.job_type = 'CLASSIFY_AND_EMBED')
                  OR (:includeImageJobs = TRUE AND job.job_type = 'EMBED_IMAGE_ONLY')
              )
        ), ranked_candidates AS (
            SELECT eligible.id,
                   eligible.interactive_priority,
                   eligible.next_attempt_at,
                   eligible.created_at,
                   ROW_NUMBER() OVER (
                       PARTITION BY eligible.institution_key
                       ORDER BY eligible.interactive_priority,
                                eligible.next_attempt_at,
                                eligible.created_at
                   ) AS institution_rank
            FROM eligible_jobs eligible
        ), candidates AS (
            SELECT job.id
            FROM media_processing_jobs job
            JOIN ranked_candidates ranked ON ranked.id = job.id
            WHERE ranked.institution_rank <= :perInstitutionLimit
            ORDER BY ranked.interactive_priority,
                     ranked.next_attempt_at,
                     ranked.created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
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
                   @Param("batchSize") int batchSize,
                   @Param("perInstitutionLimit") int perInstitutionLimit,
                   @Param("includeAiJobs") boolean includeAiJobs,
                   @Param("includeImageJobs") boolean includeImageJobs);

    @Query(value = """
        SELECT job.*
        FROM media_processing_jobs job
        WHERE job.claimed_by = :workerId
          AND job.status = :status
        ORDER BY CASE WHEN job.job_type = 'EMBED_IMAGE_ONLY' AND EXISTS (
            SELECT 1
            FROM submission_media_assets selected_media
            JOIN submissions selected_submission
              ON selected_submission.id = selected_media.submission_id
            WHERE selected_media.media_asset_id = job.asset_id
              AND selected_submission.status = 'draft'
        ) THEN 0 ELSE 1 END,
        job.created_at
        """, nativeQuery = true)
    List<MediaProcessingJob> findClaimedBatchInPriorityOrder(
            @Param("workerId") String workerId,
            @Param("status") String status);

    @Modifying
    @Transactional
    @Query(value = """
        UPDATE media_processing_jobs
        SET status = CASE WHEN rerun_requested THEN 'PENDING' ELSE 'COMPLETED' END,
            attempt_count = CASE WHEN rerun_requested THEN 0 ELSE attempt_count END,
            next_attempt_at = CASE WHEN rerun_requested THEN :completedAt ELSE next_attempt_at END,
            completed_at = CASE WHEN rerun_requested THEN NULL ELSE :completedAt END,
            lease_until = NULL, claimed_by = NULL, last_error = NULL,
            rerun_requested = FALSE,
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
            rerun_requested = FALSE,
            updated_at = :updatedAt
        WHERE id = :id AND claimed_by = :workerId AND status = 'PROCESSING'
        """, nativeQuery = true)
    int releaseAfterFailure(@Param("id") UUID id,
                            @Param("workerId") String workerId,
                            @Param("status") String status,
                            @Param("nextAttemptAt") Instant nextAttemptAt,
                            @Param("lastError") String lastError,
                            @Param("updatedAt") Instant updatedAt);

    @Query("""
        SELECT COUNT(job) FROM MediaProcessingJob job
        WHERE job.status IN (
            com.dasigconnect.backend.model.entity.MediaProcessingJobStatus.PENDING,
            com.dasigconnect.backend.model.entity.MediaProcessingJobStatus.PROCESSING,
            com.dasigconnect.backend.model.entity.MediaProcessingJobStatus.RETRY
        )
        """)
    long countActiveJobs();

    List<MediaProcessingJob> findByStatusOrderByUpdatedAtDesc(
            MediaProcessingJobStatus status, Pageable pageable);

    @Modifying
    @Transactional
    @Query(value = """
        UPDATE media_processing_jobs
        SET status = 'PENDING', attempt_count = 0, next_attempt_at = :now,
            lease_until = NULL, claimed_by = NULL, last_error = NULL,
            completed_at = NULL, rerun_requested = FALSE, updated_at = :now
        WHERE id = :id AND status = 'DEAD'
        """, nativeQuery = true)
    int retryDead(@Param("id") UUID id, @Param("now") Instant now);
}

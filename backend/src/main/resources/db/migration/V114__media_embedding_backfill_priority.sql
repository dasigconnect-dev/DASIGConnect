ALTER TABLE media_processing_jobs
    ADD COLUMN IF NOT EXISTS priority SMALLINT NOT NULL DEFAULT 10;

ALTER TABLE media_processing_jobs
    DROP CONSTRAINT IF EXISTS chk_media_processing_job_priority;

ALTER TABLE media_processing_jobs
    ADD CONSTRAINT chk_media_processing_job_priority
        CHECK (priority BETWEEN 0 AND 1000);

CREATE INDEX IF NOT EXISTS idx_media_processing_jobs_priority_claim
    ON media_processing_jobs (status, priority, next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'RETRY', 'PROCESSING');

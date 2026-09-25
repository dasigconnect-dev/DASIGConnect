ALTER TABLE media_processing_jobs
    DROP CONSTRAINT IF EXISTS chk_media_processing_job_type;

ALTER TABLE media_processing_jobs
    ADD CONSTRAINT chk_media_processing_job_type
        CHECK (job_type IN (
            'CLASSIFY_AND_EMBED',
            'EMBED_IMAGE_ONLY',
            'BUILD_SUBMISSION_CONTEXT'
        ));

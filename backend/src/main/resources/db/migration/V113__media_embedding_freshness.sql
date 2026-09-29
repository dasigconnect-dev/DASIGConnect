ALTER TABLE media_assets
    ADD COLUMN IF NOT EXISTS semantic_revision BIGINT NOT NULL DEFAULT 0;

ALTER TABLE media_asset_embeddings
    ADD COLUMN IF NOT EXISTS source_input_hash CHAR(64),
    ADD COLUMN IF NOT EXISTS source_revision BIGINT,
    ADD COLUMN IF NOT EXISTS processing_version VARCHAR(50);

ALTER TABLE media_processing_jobs
    DROP CONSTRAINT IF EXISTS chk_media_processing_job_type;

ALTER TABLE media_processing_jobs
    ADD CONSTRAINT chk_media_processing_job_type
        CHECK (job_type IN (
            'RETRIEVAL_EMBEDDINGS',
            'EMBED_SEMANTIC_ONLY',
            'ENRICH_MEDIA',
            'CLASSIFY_AND_EMBED',
            'EMBED_IMAGE_ONLY',
            'BUILD_SUBMISSION_CONTEXT'
        ));

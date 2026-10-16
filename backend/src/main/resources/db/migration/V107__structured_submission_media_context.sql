ALTER TABLE media_assets
    ADD COLUMN IF NOT EXISTS observed_scenes TEXT[],
    ADD COLUMN IF NOT EXISTS observed_activities TEXT[],
    ADD COLUMN IF NOT EXISTS people_count_range VARCHAR(30),
    ADD COLUMN IF NOT EXISTS equipment_signals TEXT[],
    ADD COLUMN IF NOT EXISTS recognition_signals TEXT[],
    ADD COLUMN IF NOT EXISTS ocr_text TEXT[],
    ADD COLUMN IF NOT EXISTS visible_dates TEXT[],
    ADD COLUMN IF NOT EXISTS event_hypotheses JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS temporal_classification VARCHAR(30),
    ADD COLUMN IF NOT EXISTS possible_expiration VARCHAR(80),
    ADD COLUMN IF NOT EXISTS visual_quality_signals TEXT[],
    ADD COLUMN IF NOT EXISTS composition_signals TEXT[];

CREATE TABLE IF NOT EXISTS submission_media_contexts (
    submission_id UUID PRIMARY KEY REFERENCES submissions(id) ON DELETE CASCADE,
    institution_id UUID NOT NULL REFERENCES institutions(id),
    context_version BIGINT NOT NULL DEFAULT 1,
    asset_set_hash CHAR(64) NOT NULL,
    ready_asset_count INTEGER NOT NULL DEFAULT 0,
    processing_asset_count INTEGER NOT NULL DEFAULT 0,
    failed_asset_count INTEGER NOT NULL DEFAULT 0,
    observed_scenes JSONB NOT NULL DEFAULT '[]'::jsonb,
    observed_objects JSONB NOT NULL DEFAULT '[]'::jsonb,
    observed_activities JSONB NOT NULL DEFAULT '[]'::jsonb,
    people_count_ranges JSONB NOT NULL DEFAULT '[]'::jsonb,
    equipment_signals JSONB NOT NULL DEFAULT '[]'::jsonb,
    recognition_signals JSONB NOT NULL DEFAULT '[]'::jsonb,
    ocr_text JSONB NOT NULL DEFAULT '[]'::jsonb,
    event_hypotheses JSONB NOT NULL DEFAULT '[]'::jsonb,
    temporal_signals JSONB NOT NULL DEFAULT '{}'::jsonb,
    quality_signals JSONB NOT NULL DEFAULT '[]'::jsonb,
    composition_signals JSONB NOT NULL DEFAULT '[]'::jsonb,
    context_text TEXT NOT NULL DEFAULT '',
    status VARCHAR(20) NOT NULL,
    model_version VARCHAR(50) NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_submission_media_context_status
        CHECK (status IN ('EMPTY', 'PARTIAL', 'READY'))
);

CREATE INDEX IF NOT EXISTS idx_submission_media_contexts_institution
    ON submission_media_contexts (institution_id, updated_at DESC);

ALTER TABLE submission_media_contexts ENABLE ROW LEVEL SECURITY;
CREATE POLICY submission_media_contexts_tenant_isolation ON submission_media_contexts
    USING (
        institution_id = nullif(current_setting('app.current_institution_id', true), '')::UUID
        OR current_setting('app.current_role', true) = 'super_administrator'
    );

ALTER TABLE media_processing_jobs ALTER COLUMN asset_id DROP NOT NULL;
ALTER TABLE media_processing_jobs
    ADD COLUMN IF NOT EXISTS submission_id UUID REFERENCES submissions(id) ON DELETE CASCADE,
    ADD COLUMN IF NOT EXISTS rerun_requested BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE media_processing_jobs DROP CONSTRAINT IF EXISTS uq_media_processing_job;
ALTER TABLE media_processing_jobs DROP CONSTRAINT IF EXISTS chk_media_processing_job_type;
ALTER TABLE media_processing_jobs
    ADD CONSTRAINT chk_media_processing_job_type
        CHECK (job_type IN ('CLASSIFY_AND_EMBED', 'BUILD_SUBMISSION_CONTEXT')),
    ADD CONSTRAINT chk_media_processing_job_target
        CHECK ((asset_id IS NOT NULL) <> (submission_id IS NOT NULL));

CREATE UNIQUE INDEX IF NOT EXISTS uq_media_processing_asset_job
    ON media_processing_jobs (asset_id, job_type, processing_version)
    WHERE asset_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_media_processing_submission_job
    ON media_processing_jobs (submission_id, job_type, processing_version)
    WHERE submission_id IS NOT NULL;

DROP POLICY IF EXISTS media_processing_jobs_scoped_insert ON media_processing_jobs;
CREATE POLICY media_processing_jobs_scoped_insert ON media_processing_jobs
    FOR INSERT
    WITH CHECK (
        (asset_id IS NOT NULL AND EXISTS (
            SELECT 1 FROM media_assets asset
            WHERE asset.id = media_processing_jobs.asset_id
              AND (
                  asset.uploader_id = nullif(current_setting('app.current_user_id', true), '')::UUID
                  OR asset.institution_id = nullif(current_setting('app.current_institution_id', true), '')::UUID
              )
        ))
        OR
        (submission_id IS NOT NULL AND EXISTS (
            SELECT 1 FROM submissions submission
            WHERE submission.id = media_processing_jobs.submission_id
              AND (
                  submission.contributor_id = nullif(current_setting('app.current_user_id', true), '')::UUID
                  OR submission.institution_id = nullif(current_setting('app.current_institution_id', true), '')::UUID
              )
        ))
    );

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON TABLE submission_media_contexts FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON TABLE submission_media_contexts FROM authenticated;
    END IF;
END $$;

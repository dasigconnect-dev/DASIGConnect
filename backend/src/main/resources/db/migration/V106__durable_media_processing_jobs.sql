ALTER TABLE media_assets
    ADD COLUMN IF NOT EXISTS ai_processing_version VARCHAR(50);

CREATE TABLE IF NOT EXISTS media_processing_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id UUID NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE,
    job_type VARCHAR(40) NOT NULL,
    processing_version VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 5,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    lease_until TIMESTAMPTZ,
    claimed_by VARCHAR(100),
    last_error VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    CONSTRAINT uq_media_processing_job UNIQUE (asset_id, job_type, processing_version),
    CONSTRAINT chk_media_processing_job_type
        CHECK (job_type IN ('CLASSIFY_AND_EMBED')),
    CONSTRAINT chk_media_processing_job_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'RETRY', 'COMPLETED', 'DEAD')),
    CONSTRAINT chk_media_processing_job_attempts
        CHECK (attempt_count >= 0 AND max_attempts > 0)
);

CREATE INDEX IF NOT EXISTS idx_media_processing_jobs_claim
    ON media_processing_jobs (status, next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'RETRY', 'PROCESSING');

CREATE INDEX IF NOT EXISTS idx_media_processing_jobs_asset
    ON media_processing_jobs (asset_id, created_at DESC);

ALTER TABLE media_processing_jobs ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS media_processing_jobs_system_read_write ON media_processing_jobs;
CREATE POLICY media_processing_jobs_system_read_write ON media_processing_jobs
    FOR ALL
    USING (
        COALESCE(current_setting('app.current_role', true), '') = ''
        OR current_setting('app.current_role', true) IN ('administrator', 'super_administrator')
    )
    WITH CHECK (
        COALESCE(current_setting('app.current_role', true), '') = ''
        OR current_setting('app.current_role', true) IN ('administrator', 'super_administrator')
    );

DROP POLICY IF EXISTS media_processing_jobs_scoped_insert ON media_processing_jobs;
CREATE POLICY media_processing_jobs_scoped_insert ON media_processing_jobs
    FOR INSERT
    WITH CHECK (
        EXISTS (
            SELECT 1
            FROM media_assets asset
            WHERE asset.id = media_processing_jobs.asset_id
              AND (
                  asset.uploader_id = nullif(current_setting('app.current_user_id', true), '')::UUID
                  OR asset.institution_id = nullif(current_setting('app.current_institution_id', true), '')::UUID
              )
        )
    );

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON TABLE media_processing_jobs FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON TABLE media_processing_jobs FROM authenticated;
    END IF;
END $$;

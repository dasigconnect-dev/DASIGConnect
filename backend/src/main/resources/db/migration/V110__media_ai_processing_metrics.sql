CREATE TABLE IF NOT EXISTS media_ai_processing_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stage VARCHAR(40) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    asset_id UUID REFERENCES media_assets(id) ON DELETE SET NULL,
    submission_id UUID REFERENCES submissions(id) ON DELETE SET NULL,
    attempt_number INTEGER NOT NULL DEFAULT 1,
    provider_call_count INTEGER NOT NULL DEFAULT 0,
    duplicate_call_avoided_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_media_ai_metric_stage CHECK (stage IN (
        'R2_UPLOAD', 'DB_REGISTRATION', 'QUEUE_DELAY',
        'VOYAGE_IMAGE_EMBEDDING', 'CLAUDE_CLASSIFICATION',
        'VOYAGE_SEMANTIC_EMBEDDING', 'READY_LATENCY',
        'AI_SUGGESTION_QUERY'
    )),
    CONSTRAINT chk_media_ai_metric_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE', 'REUSED')),
    CONSTRAINT chk_media_ai_metric_values CHECK (
        duration_ms >= 0 AND attempt_number >= 1
        AND provider_call_count >= 0 AND duplicate_call_avoided_count >= 0
    )
);

CREATE INDEX IF NOT EXISTS idx_media_ai_metrics_stage_created
    ON media_ai_processing_metrics (stage, created_at DESC);

ALTER TABLE media_ai_processing_metrics ENABLE ROW LEVEL SECURITY;

CREATE POLICY media_ai_processing_metrics_admin_only ON media_ai_processing_metrics
    FOR ALL
    USING (
        COALESCE(current_setting('app.current_role', true), '') = ''
        OR current_setting('app.current_role', true) IN ('administrator', 'super_administrator')
    )
    WITH CHECK (
        COALESCE(current_setting('app.current_role', true), '') = ''
        OR current_setting('app.current_role', true) IN ('administrator', 'super_administrator')
    );

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON TABLE media_ai_processing_metrics FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON TABLE media_ai_processing_metrics FROM authenticated;
    END IF;
END $$;

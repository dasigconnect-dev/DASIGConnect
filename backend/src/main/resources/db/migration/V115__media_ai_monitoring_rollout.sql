ALTER TABLE media_ai_processing_metrics
    DROP CONSTRAINT IF EXISTS chk_media_ai_metric_outcome;

ALTER TABLE media_ai_processing_metrics
    ADD CONSTRAINT chk_media_ai_metric_outcome CHECK (outcome IN (
        'SUCCESS', 'FAILURE', 'REUSED',
        'READY', 'PROCESSING', 'EMBEDDING_FAILED',
        'NO_INDEXED_CANDIDATES', 'NO_RELEVANT_MATCHES', 'ERROR'
    ));

CREATE TABLE IF NOT EXISTS media_ai_ranking_shadow_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id UUID NOT NULL REFERENCES institutions(id) ON DELETE CASCADE,
    submission_id UUID REFERENCES submissions(id) ON DELETE SET NULL,
    legacy_result_count INTEGER NOT NULL,
    hybrid_result_count INTEGER NOT NULL,
    overlap_count INTEGER NOT NULL,
    top_result_changed BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_media_ai_shadow_counts CHECK (
        legacy_result_count >= 0
        AND hybrid_result_count >= 0
        AND overlap_count >= 0
        AND overlap_count <= legacy_result_count
        AND overlap_count <= hybrid_result_count
    )
);

CREATE INDEX IF NOT EXISTS idx_media_ai_shadow_created
    ON media_ai_ranking_shadow_metrics (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_media_ai_shadow_institution_created
    ON media_ai_ranking_shadow_metrics (institution_id, created_at DESC);

ALTER TABLE media_ai_ranking_shadow_metrics ENABLE ROW LEVEL SECURITY;

CREATE POLICY media_ai_ranking_shadow_metrics_admin_only
    ON media_ai_ranking_shadow_metrics
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
        REVOKE ALL ON TABLE media_ai_ranking_shadow_metrics FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON TABLE media_ai_ranking_shadow_metrics FROM authenticated;
    END IF;
END $$;

-- UC-4.8 Phase 5: Facebook engagement and insights time-series.
-- Rows are append-only snapshots fetched from Graph API for published submissions.

CREATE TABLE IF NOT EXISTS facebook_post_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id UUID NOT NULL REFERENCES submissions(id) ON DELETE CASCADE,
    facebook_post_id TEXT NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reactions INT NOT NULL DEFAULT 0,
    comments INT NOT NULL DEFAULT 0,
    shares INT NOT NULL DEFAULT 0,
    reach INT,
    impressions INT,
    raw_post JSONB,
    raw_insights JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_fb_metrics_submission_fetched
    ON facebook_post_metrics (submission_id, fetched_at DESC);

CREATE INDEX IF NOT EXISTS ix_fb_metrics_post_fetched
    ON facebook_post_metrics (facebook_post_id, fetched_at DESC);

ALTER TABLE facebook_post_metrics ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS facebook_post_metrics_tenant_isolation
    ON facebook_post_metrics;

CREATE POLICY facebook_post_metrics_tenant_isolation
    ON facebook_post_metrics
    USING (
        EXISTS (
            SELECT 1
            FROM submissions s
            WHERE s.id = facebook_post_metrics.submission_id
              AND (
                  s.institution_id = nullif(current_setting('app.current_institution_id', true), '')::uuid
                  OR current_setting('app.current_role', true) = 'administrator'
              )
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1
            FROM submissions s
            WHERE s.id = facebook_post_metrics.submission_id
              AND (
                  s.institution_id = nullif(current_setting('app.current_institution_id', true), '')::uuid
                  OR current_setting('app.current_role', true) = 'administrator'
              )
        )
    );

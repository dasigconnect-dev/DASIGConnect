CREATE TABLE IF NOT EXISTS scheduled_job_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_name VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    duration_ms BIGINT,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_scheduled_job_runs_status CHECK (status IN ('SUCCESS', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_scheduled_job_runs_job_started
    ON scheduled_job_runs (job_name, started_at DESC);

ALTER TABLE scheduled_job_runs ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS scheduled_job_runs_admin_read ON scheduled_job_runs;
CREATE POLICY scheduled_job_runs_admin_read ON scheduled_job_runs
    FOR SELECT
    USING (current_setting('app.current_role', true) IN ('administrator', 'super_administrator'));

DROP POLICY IF EXISTS scheduled_job_runs_system_insert ON scheduled_job_runs;
CREATE POLICY scheduled_job_runs_system_insert ON scheduled_job_runs
    FOR INSERT
    WITH CHECK (true);

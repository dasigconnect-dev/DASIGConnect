-- V123: Task completion telemetry for measuring first-attempt completion and time-on-task per role (Priority #2)
CREATE TABLE IF NOT EXISTS task_completion_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_role VARCHAR(32) NOT NULL,
    task_name VARCHAR(64) NOT NULL,
    target_entity_id UUID,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    duration_ms BIGINT,
    attempt_number INT NOT NULL DEFAULT 1,
    first_attempt_success BOOLEAN NOT NULL DEFAULT TRUE,
    error_count INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'STARTED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_task_completion_logs_user_task
    ON task_completion_logs (user_id, task_name, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_task_completion_logs_role_status
    ON task_completion_logs (user_role, status);

CREATE TABLE IF NOT EXISTS task_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(32) NOT NULL,
    task_type VARCHAR(64) NOT NULL,
    submission_id UUID REFERENCES submissions(id) ON DELETE SET NULL,
    event VARCHAR(20) NOT NULL CHECK (event IN ('started', 'completed', 'abandoned')),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    client_session_id VARCHAR(128)
);

CREATE INDEX IF NOT EXISTS idx_task_events_user_task
    ON task_events (user_id, task_type, occurred_at DESC);


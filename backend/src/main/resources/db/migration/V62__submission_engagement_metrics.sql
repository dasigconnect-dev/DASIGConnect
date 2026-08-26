-- UC-3.4: per-post Facebook engagement metrics (reach, reactions, comments, shares).
-- fetched_at IS NULL means the post has not yet been synced ("engagement data pending", A2).
CREATE TABLE submission_engagement_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id UUID NOT NULL UNIQUE REFERENCES submissions(id) ON DELETE CASCADE,
    reach BIGINT,
    reactions BIGINT,
    comments_count BIGINT,
    shares BIGINT,
    fetched_at TIMESTAMPTZ,
    last_attempt_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_submission_engagement_metrics_fetched_at ON submission_engagement_metrics (fetched_at);

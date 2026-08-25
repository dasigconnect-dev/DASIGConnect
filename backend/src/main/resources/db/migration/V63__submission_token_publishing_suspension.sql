ALTER TABLE submissions
    ADD COLUMN IF NOT EXISTS token_blocked_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS token_escalated_24h_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS token_final_failed_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_submissions_token_blocked
    ON submissions (token_blocked_at)
    WHERE token_blocked_at IS NOT NULL;

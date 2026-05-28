-- UC-3.4 A2: track the most recent abandonment timestamp so the
-- Resolution Center can surface a note about the abandoned attempt.
ALTER TABLE submissions
    ADD COLUMN IF NOT EXISTS last_manual_publish_abandoned_at TIMESTAMPTZ;

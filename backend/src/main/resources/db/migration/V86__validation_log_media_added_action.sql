-- A10 follow-up:
--  1. Re-assert validation_logs.edit_severity (V85 was recorded in
--     flyway_schema_history on some environments without the column landing —
--     IF NOT EXISTS makes this a safe no-op where V85 did apply cleanly).
--  2. Allow the new `media_added` action — the chk_validation_logs_action CHECK
--     (last set in V70) predates it, so attaching a Library asset during review
--     would otherwise fail the constraint.

ALTER TABLE validation_logs
    ADD COLUMN IF NOT EXISTS edit_severity VARCHAR(20);

COMMENT ON COLUMN validation_logs.edit_severity IS
    'quiet | flagged | added_media — how prominently a moderator review-edit is surfaced (A10).';

ALTER TABLE validation_logs DROP CONSTRAINT IF EXISTS chk_validation_logs_action;
ALTER TABLE validation_logs ADD CONSTRAINT chk_validation_logs_action CHECK (action IN (
    'approved', 'edited', 'media_added', 'edited_and_approved', 'needs_revision', 'rejected',
    'lock_acquired', 'lock_released', 'lock_expired'
));

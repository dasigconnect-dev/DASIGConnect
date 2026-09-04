-- A10: governance tier for moderator edits made during review.
-- Populated only on `edited` / `media_added` rows; NULL for lifecycle rows
-- (lock_acquired / lock_released / approved / rejected / needs_revision).
ALTER TABLE validation_logs
    ADD COLUMN edit_severity VARCHAR(20);

COMMENT ON COLUMN validation_logs.edit_severity IS
    'quiet | flagged | added_media — how prominently a moderator review-edit is surfaced (A10).';

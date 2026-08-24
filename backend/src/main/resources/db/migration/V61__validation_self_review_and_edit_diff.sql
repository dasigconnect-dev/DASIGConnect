-- UC-2.4: allow (but audit-flag) self-review, and support Edit & Approve diffs.
ALTER TABLE validation_logs
    ADD COLUMN is_self_review BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN edit_diff JSONB;

ALTER TABLE validation_logs DROP CONSTRAINT chk_validation_logs_action;
ALTER TABLE validation_logs ADD CONSTRAINT chk_validation_logs_action CHECK (action IN (
    'approved', 'edited_and_approved', 'needs_revision', 'rejected',
    'lock_acquired', 'lock_released', 'lock_expired'
));

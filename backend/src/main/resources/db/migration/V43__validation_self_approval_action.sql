-- Allow explicit audit records for validator-authored submissions that skip
-- manual peer review and move directly to scheduled.

ALTER TABLE validation_logs
    DROP CONSTRAINT IF EXISTS chk_validation_logs_action;

ALTER TABLE validation_logs
    ADD CONSTRAINT chk_validation_logs_action CHECK (action IN (
        'approved', 'self_approved', 'needs_revision', 'rejected',
        'lock_acquired', 'lock_released', 'lock_expired'
    ));

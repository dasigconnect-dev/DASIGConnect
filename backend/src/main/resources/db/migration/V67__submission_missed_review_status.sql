-- UC-2.4: MISSED_REVIEW status for submissions whose scheduled publication time
-- passed while still PENDING/IN_REVIEW (GR-T9), plus a standalone 'edited'
-- validation-log action for the decoupled Edit workflow.

ALTER TABLE submissions
    DROP CONSTRAINT IF EXISTS chk_submissions_status;

ALTER TABLE submissions
    ADD CONSTRAINT chk_submissions_status CHECK (
        status IN (
            'draft',
            'pending',
            'in_review',
            'needs_revision',
            'missed_review',
            'scheduled',
            'publishing',
            'publish_failed',
            'published',
            'published_manual',
            'admin_direct_post',
            'direct_post_scheduled',
            'direct_post_publishing',
            'direct_post_failed',
            'rejected'
        )
    );

ALTER TABLE validation_logs DROP CONSTRAINT IF EXISTS chk_validation_logs_action;
ALTER TABLE validation_logs ADD CONSTRAINT chk_validation_logs_action CHECK (action IN (
    'approved', 'edited', 'edited_and_approved', 'needs_revision', 'rejected',
    'lock_acquired', 'lock_released', 'lock_expired'
));

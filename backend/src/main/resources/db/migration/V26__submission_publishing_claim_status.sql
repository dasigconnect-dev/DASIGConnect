ALTER TABLE submissions
    DROP CONSTRAINT IF EXISTS chk_submissions_status;

ALTER TABLE submissions
    ADD CONSTRAINT chk_submissions_status CHECK (
        status IN (
            'draft',
            'pending',
            'in_review',
            'needs_revision',
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

DROP INDEX IF EXISTS idx_submissions_scheduled_at;
CREATE INDEX idx_submissions_scheduled_at ON submissions(scheduled_at)
    WHERE status IN ('scheduled', 'publishing', 'pending', 'in_review', 'direct_post_scheduled', 'direct_post_publishing');

DROP INDEX IF EXISTS idx_submissions_due_for_publish;
CREATE INDEX idx_submissions_due_for_publish ON submissions(scheduled_at)
    WHERE status IN ('scheduled', 'direct_post_scheduled');

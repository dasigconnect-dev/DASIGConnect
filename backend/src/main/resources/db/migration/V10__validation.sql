-- UC-2.1: Content Validation and Approval
-- Immutable validation action records and active review locks

CREATE TABLE validation_logs (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id    UUID         NOT NULL REFERENCES submissions(id),
    validator_id     UUID         NOT NULL REFERENCES users(id),
    action           VARCHAR(30)  NOT NULL,
    remarks          TEXT,
    rejection_reason VARCHAR(100),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_validation_logs_action CHECK (action IN (
        'approved', 'needs_revision', 'rejected',
        'lock_acquired', 'lock_released', 'lock_expired'
    ))
);

CREATE INDEX idx_validation_logs_submission ON validation_logs(submission_id, created_at DESC);

CREATE TABLE review_locks (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id UUID        NOT NULL UNIQUE REFERENCES submissions(id),
    locked_by     UUID        NOT NULL REFERENCES users(id),
    acquired_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_review_locks_expiry ON review_locks(expires_at)
    WHERE expires_at IS NOT NULL;

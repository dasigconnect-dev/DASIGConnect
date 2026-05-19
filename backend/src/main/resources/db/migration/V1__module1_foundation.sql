CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE institutions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL DEFAULT 'onboarding',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_institutions_status CHECK (status IN ('onboarding', 'active', 'inactive_no_validator'))
);

CREATE UNIQUE INDEX idx_institutions_code ON institutions(code);
CREATE INDEX idx_institutions_status ON institutions(status);

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255),
    role VARCHAR(20) NOT NULL,
    institution_id UUID REFERENCES institutions(id),
    account_state VARCHAR(30) NOT NULL DEFAULT 'pending',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_users_role CHECK (role IN ('contributor', 'validator', 'administrator')),
    CONSTRAINT chk_users_account_state CHECK (
        account_state IN ('pending', 'pending_email_undelivered', 'active', 'expired', 'inactive')
    )
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_institution ON users(institution_id);

CREATE TABLE invitation_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash VARCHAR(64) NOT NULL,
    recipient_email VARCHAR(255) NOT NULL,
    assigned_role VARCHAR(20) NOT NULL,
    institution_id UUID NOT NULL REFERENCES institutions(id),
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_invitation_tokens_assigned_role CHECK (assigned_role IN ('contributor', 'validator'))
);

CREATE INDEX idx_invitation_tokens_hash ON invitation_tokens(token_hash);
CREATE INDEX idx_invitation_tokens_institution ON invitation_tokens(institution_id);

CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_password_reset_user ON password_reset_tokens(user_id);
CREATE INDEX idx_password_reset_hash ON password_reset_tokens(token_hash);

CREATE TABLE account_lockouts (
    user_id UUID PRIMARY KEY REFERENCES users(id),
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    last_attempt_at TIMESTAMPTZ
);

CREATE TABLE audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id UUID REFERENCES users(id),
    action VARCHAR(50) NOT NULL,
    ip_address INET,
    user_agent TEXT,
    resource_id UUID,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_actor ON audit_log(actor_id, created_at DESC);
CREATE INDEX idx_audit_log_resource ON audit_log(resource_id, created_at DESC);

CREATE TABLE submissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contributor_id UUID NOT NULL REFERENCES users(id),
    institution_id UUID NOT NULL REFERENCES institutions(id),
    event_title VARCHAR(255) NOT NULL,
    event_date DATE NOT NULL,
    caption TEXT,
    description TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'draft',
    scheduled_at TIMESTAMPTZ,
    submitted_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    platform_post_id VARCHAR(255),
    validator_remarks TEXT,
    rejection_reason TEXT,
    retry_count INT NOT NULL DEFAULT 0,
    manual_publish_started_at TIMESTAMPTZ,
    published_manual_url TEXT,
    published_manual_notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_submissions_status CHECK (
        status IN (
            'draft',
            'pending',
            'in_review',
            'needs_revision',
            'scheduled',
            'publish_failed',
            'published',
            'published_manual',
            'admin_direct_post',
            'rejected'
        )
    )
);

CREATE INDEX idx_submissions_contributor ON submissions(contributor_id);
CREATE INDEX idx_submissions_institution ON submissions(institution_id);
CREATE INDEX idx_submissions_status ON submissions(status);
CREATE INDEX idx_submissions_scheduled_at ON submissions(scheduled_at)
    WHERE status IN ('scheduled', 'pending', 'in_review');
CREATE INDEX idx_submissions_due_for_publish ON submissions(scheduled_at)
    WHERE status = 'scheduled';
CREATE INDEX idx_submissions_manual_workflow ON submissions(manual_publish_started_at)
    WHERE manual_publish_started_at IS NOT NULL AND status = 'publish_failed';

CREATE TABLE slot_reservations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id UUID NOT NULL REFERENCES submissions(id),
    institution_id UUID NOT NULL REFERENCES institutions(id),
    scheduled_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'held',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_slot_reservations_status CHECK (status IN ('held', 'locked', 'released'))
);

CREATE INDEX idx_slot_reservations_slot ON slot_reservations(scheduled_at, institution_id)
    WHERE status != 'released';

ALTER TABLE institutions ENABLE ROW LEVEL SECURITY;
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE invitation_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE password_reset_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE account_lockouts ENABLE ROW LEVEL SECURITY;
ALTER TABLE submissions ENABLE ROW LEVEL SECURITY;
ALTER TABLE slot_reservations ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;

CREATE POLICY institutions_tenant_isolation ON institutions
    USING (
        id = nullif(current_setting('app.current_institution_id', true), '')::UUID
        OR current_setting('app.current_role', true) = 'administrator'
    );

CREATE POLICY users_tenant_isolation ON users
    USING (
        institution_id = nullif(current_setting('app.current_institution_id', true), '')::UUID
        OR current_setting('app.current_role', true) = 'administrator'
    );

CREATE POLICY invitation_tokens_tenant_isolation ON invitation_tokens
    USING (
        institution_id = nullif(current_setting('app.current_institution_id', true), '')::UUID
        OR current_setting('app.current_role', true) = 'administrator'
    );

CREATE POLICY password_reset_tokens_tenant_isolation ON password_reset_tokens
    USING (
        EXISTS (
            SELECT 1 FROM users u
            WHERE u.id = password_reset_tokens.user_id
              AND (
                  u.institution_id = nullif(current_setting('app.current_institution_id', true), '')::UUID
                  OR current_setting('app.current_role', true) = 'administrator'
              )
        )
    );

CREATE POLICY account_lockouts_tenant_isolation ON account_lockouts
    USING (
        EXISTS (
            SELECT 1 FROM users u
            WHERE u.id = account_lockouts.user_id
              AND (
                  u.institution_id = nullif(current_setting('app.current_institution_id', true), '')::UUID
                  OR current_setting('app.current_role', true) = 'administrator'
              )
        )
    );

CREATE POLICY submissions_tenant_isolation ON submissions
    USING (
        institution_id = nullif(current_setting('app.current_institution_id', true), '')::UUID
        OR current_setting('app.current_role', true) = 'administrator'
    );

CREATE POLICY slot_reservations_tenant_isolation ON slot_reservations
    USING (
        institution_id = nullif(current_setting('app.current_institution_id', true), '')::UUID
        OR current_setting('app.current_role', true) = 'administrator'
    );

CREATE POLICY audit_log_tenant_isolation ON audit_log
    USING (
        current_setting('app.current_role', true) = 'administrator'
        OR EXISTS (
            SELECT 1 FROM users u
            WHERE u.id = audit_log.actor_id
              AND u.institution_id = nullif(current_setting('app.current_institution_id', true), '')::UUID
        )
    );

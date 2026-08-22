ALTER TABLE users
    ADD COLUMN IF NOT EXISTS is_super_administrator BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS super_admin_transfer_requested_by UUID REFERENCES users(id);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS super_admin_transfer_expires_at TIMESTAMPTZ;

ALTER TABLE users
    ADD CONSTRAINT chk_users_super_admin_transfer_pending CHECK (
        (super_admin_transfer_requested_by IS NULL AND super_admin_transfer_expires_at IS NULL)
        OR (super_admin_transfer_requested_by IS NOT NULL AND super_admin_transfer_expires_at IS NOT NULL)
    );

ALTER TABLE invitation_tokens
    ALTER COLUMN institution_id DROP NOT NULL;

ALTER TABLE invitation_tokens
    DROP CONSTRAINT IF EXISTS chk_invitation_tokens_assigned_role;

ALTER TABLE invitation_tokens
    ADD CONSTRAINT chk_invitation_tokens_assigned_role CHECK (
        assigned_role IN ('contributor', 'validator', 'administrator')
    );

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_single_active_super_admin
    ON users (is_super_administrator)
    WHERE is_super_administrator = TRUE
      AND role = 'administrator'
      AND account_state = 'active';

-- Repair legacy data created before UC-1.4 role consolidation.
-- Older databases may still contain role='validator'; the current enum only
-- accepts contributor, administrator, and super_administrator.
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_role;
ALTER TABLE invitation_tokens DROP CONSTRAINT IF EXISTS chk_invitation_tokens_assigned_role;

UPDATE users
SET role = 'administrator'
WHERE role = 'validator';

-- Keep the legacy boolean aligned with the authoritative role value.
UPDATE users
SET is_super_administrator = (role = 'super_administrator')
WHERE is_super_administrator IS DISTINCT FROM (role = 'super_administrator');

UPDATE invitation_tokens
SET assigned_role = 'administrator'
WHERE assigned_role = 'validator';

ALTER TABLE users ADD CONSTRAINT chk_users_role
    CHECK (role IN ('contributor', 'administrator', 'super_administrator'));

ALTER TABLE invitation_tokens ADD CONSTRAINT chk_invitation_tokens_assigned_role
    CHECK (assigned_role IN ('contributor', 'administrator'));

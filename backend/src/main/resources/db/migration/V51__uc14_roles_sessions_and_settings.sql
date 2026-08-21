-- UC-1.4 approved actor migration. Preserve existing responsibilities:
-- legacy validator -> administrator; legacy administrator -> super administrator.
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_role;
ALTER TABLE invitation_tokens DROP CONSTRAINT IF EXISTS chk_invitation_tokens_assigned_role;

-- Make the legacy role conversion retry-safe. Network administrators have no
-- institution, while legacy validators/new administrators are institution
-- scoped. This also repairs an institution-scoped administrator that was
-- accidentally promoted by a previous retry of this migration.
UPDATE users
SET role = 'administrator'
WHERE role IN ('validator', 'super_administrator')
  AND institution_id IS NOT NULL;

UPDATE users
SET role = 'super_administrator'
WHERE role = 'administrator'
  AND institution_id IS NULL;
UPDATE invitation_tokens SET assigned_role = 'administrator' WHERE assigned_role = 'validator';

-- Existing RLS policies used the legacy administrator value for network-wide
-- access. Move those checks to the new super_administrator value without
-- weakening institution isolation for the new Administrator actor.
DO $$
DECLARE
    policy_row RECORD;
    updated_using TEXT;
BEGIN
    FOR policy_row IN
        SELECT p.polname, n.nspname, c.relname, pg_get_expr(p.polqual, p.polrelid) AS using_expression
        FROM pg_policy p
        JOIN pg_class c ON c.oid = p.polrelid
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE pg_get_expr(p.polqual, p.polrelid) LIKE '%app.current_role%administrator%'
    LOOP
        updated_using := replace(policy_row.using_expression,
            '= ''administrator''', '= ''super_administrator''');
        EXECUTE format('ALTER POLICY %I ON %I.%I USING (%s)',
            policy_row.polname, policy_row.nspname, policy_row.relname, updated_using);
    END LOOP;
END $$;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS display_name VARCHAR(150),
    ADD COLUMN IF NOT EXISTS notify_in_app BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS notify_email BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS session_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE users ADD CONSTRAINT chk_users_role
    CHECK (role IN ('contributor', 'administrator', 'super_administrator'));
ALTER TABLE invitation_tokens ADD CONSTRAINT chk_invitation_tokens_assigned_role
    CHECK (assigned_role IN ('contributor', 'administrator'));

-- Some development databases may already have this table from an earlier
-- Hibernate schema update. Preserve that table and let Flyway add the indexes
-- and RLS policy it owns instead of failing startup.
CREATE TABLE IF NOT EXISTS page_settings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id UUID NULL REFERENCES institutions(id) ON DELETE CASCADE,
    watermark_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    watermark_text VARCHAR(150),
    facebook_page_id VARCHAR(255),
    updated_by UUID REFERENCES users(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_page_settings_network
    ON page_settings ((institution_id IS NULL)) WHERE institution_id IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_page_settings_institution
    ON page_settings (institution_id) WHERE institution_id IS NOT NULL;

ALTER TABLE page_settings ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS page_settings_authorized_access ON page_settings;
CREATE POLICY page_settings_authorized_access ON page_settings
    USING (
        current_setting('app.current_role', true) = 'super_administrator'
        OR (
            current_setting('app.current_role', true) = 'administrator'
            AND institution_id = nullif(current_setting('app.current_institution_id', true), '')::UUID
        )
    )
    WITH CHECK (
        current_setting('app.current_role', true) = 'super_administrator'
        OR (
            current_setting('app.current_role', true) = 'administrator'
            AND institution_id = nullif(current_setting('app.current_institution_id', true), '')::UUID
        )
    );

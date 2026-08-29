ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_role;
ALTER TABLE invitation_tokens DROP CONSTRAINT IF EXISTS chk_invitation_tokens_assigned_role;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'users'
          AND column_name = 'is_super_administrator'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'users'
          AND column_name = 'is_admin'
    ) THEN
        ALTER TABLE users RENAME COLUMN is_super_administrator TO is_admin;
    END IF;
END $$;

UPDATE users
SET role = 'admin'
WHERE role = 'super_administrator'
   OR (role = 'administrator' AND institution_id IS NULL);

UPDATE users
SET role = 'moderator'
WHERE role = 'administrator'
   OR role = 'validator';

UPDATE invitation_tokens
SET assigned_role = 'admin'
WHERE assigned_role = 'administrator'
  AND institution_id IS NULL;

UPDATE invitation_tokens
SET assigned_role = 'moderator'
WHERE assigned_role IN ('administrator', 'validator');

UPDATE users
SET is_admin = (role = 'admin' AND is_admin)
WHERE is_admin IS DISTINCT FROM (role = 'admin' AND is_admin);

DROP INDEX IF EXISTS idx_users_single_active_super_admin;
CREATE UNIQUE INDEX idx_users_single_active_admin_owner
    ON users ((role))
    WHERE role = 'admin'
      AND account_state = 'active'
      AND is_admin = TRUE;

DO $$
DECLARE
    policy_record RECORD;
    policy_expression TEXT;
    using_expression TEXT;
    check_expression TEXT;
BEGIN
    FOR policy_record IN
        SELECT schemaname, tablename, policyname, cmd, roles, qual, with_check
        FROM pg_policies
        WHERE schemaname = 'public'
          AND (
              qual LIKE '%app.current_role%'
              OR with_check LIKE '%app.current_role%'
          )
    LOOP
        using_expression := policy_record.qual;
        check_expression := policy_record.with_check;

        IF using_expression IS NOT NULL THEN
            using_expression := replace(using_expression, '''super_administrator''', '''admin''');
            using_expression := replace(using_expression, '''administrator''', '''admin''');
        END IF;

        IF check_expression IS NOT NULL THEN
            check_expression := replace(check_expression, '''super_administrator''', '''admin''');
            check_expression := replace(check_expression, '''administrator''', '''admin''');
        END IF;

        EXECUTE format('DROP POLICY IF EXISTS %I ON %I.%I',
                policy_record.policyname,
                policy_record.schemaname,
                policy_record.tablename);

        policy_expression := format('CREATE POLICY %I ON %I.%I',
                policy_record.policyname,
                policy_record.schemaname,
                policy_record.tablename);

        IF policy_record.cmd IS NOT NULL AND policy_record.cmd <> 'ALL' THEN
            policy_expression := policy_expression || ' FOR ' || policy_record.cmd;
        END IF;

        IF policy_record.roles IS NOT NULL AND policy_record.roles <> '{public}'::name[] THEN
            policy_expression := policy_expression || ' TO ' || array_to_string(policy_record.roles, ', ');
        END IF;

        IF using_expression IS NOT NULL THEN
            policy_expression := policy_expression || ' USING (' || using_expression || ')';
        END IF;

        IF check_expression IS NOT NULL THEN
            policy_expression := policy_expression || ' WITH CHECK (' || check_expression || ')';
        END IF;

        EXECUTE policy_expression;
    END LOOP;
END $$;

ALTER TABLE users ADD CONSTRAINT chk_users_role
    CHECK (role IN ('contributor', 'moderator', 'admin'));

ALTER TABLE invitation_tokens ADD CONSTRAINT chk_invitation_tokens_assigned_role
    CHECK (assigned_role IN ('contributor', 'moderator', 'admin'));

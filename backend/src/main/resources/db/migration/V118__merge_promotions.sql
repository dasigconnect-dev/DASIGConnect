-- 1. Add new unified columns
ALTER TABLE users ADD COLUMN IF NOT EXISTS pending_promotion_role VARCHAR(20);
ALTER TABLE users ADD COLUMN IF NOT EXISTS pending_promotion_requested_by UUID REFERENCES users(id);
ALTER TABLE users ADD COLUMN IF NOT EXISTS pending_promotion_expires_at TIMESTAMP WITH TIME ZONE;

-- 2. Migrate existing pending admin promotions
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'users'
          AND column_name = 'admin_promotion_expires_at'
    ) THEN
        EXECUTE $sql$
            UPDATE users
            SET pending_promotion_role = 'admin',
                pending_promotion_requested_by = admin_promotion_requested_by,
                pending_promotion_expires_at = admin_promotion_expires_at
            WHERE admin_promotion_expires_at IS NOT NULL
        $sql$;
    END IF;
END
$$;

-- 3. Migrate existing pending moderator promotions
-- Preserve an already-migrated administrator promotion if both legacy formats exist.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'users'
          AND column_name = 'moderator_promotion_expires_at'
    ) THEN
        EXECUTE $sql$
            UPDATE users
            SET pending_promotion_role = 'moderator',
                pending_promotion_requested_by = moderator_promotion_requested_by,
                pending_promotion_expires_at = moderator_promotion_expires_at
            WHERE moderator_promotion_expires_at IS NOT NULL
              AND pending_promotion_role IS NULL
        $sql$;
    END IF;
END
$$;

-- 4. Drop old columns
ALTER TABLE users DROP COLUMN IF EXISTS admin_promotion_requested_by;
ALTER TABLE users DROP COLUMN IF EXISTS admin_promotion_expires_at;
ALTER TABLE users DROP COLUMN IF EXISTS moderator_promotion_requested_by;
ALTER TABLE users DROP COLUMN IF EXISTS moderator_promotion_expires_at;


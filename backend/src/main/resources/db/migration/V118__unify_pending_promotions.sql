-- V118__unify_pending_promotions.sql

ALTER TABLE users 
    ADD COLUMN IF NOT EXISTS pending_promotion_role VARCHAR(20),
    ADD COLUMN IF NOT EXISTS pending_promotion_requested_by UUID REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS pending_promotion_expires_at TIMESTAMPTZ;

-- Migrate existing pending admin promotions
UPDATE users
SET pending_promotion_role = 'admin',
    pending_promotion_requested_by = admin_promotion_requested_by,
    pending_promotion_expires_at = admin_promotion_expires_at
WHERE admin_promotion_requested_by IS NOT NULL;

-- Migrate existing pending moderator promotions (overwrites if somehow both are set, though shouldn't be)
UPDATE users
SET pending_promotion_role = 'moderator',
    pending_promotion_requested_by = moderator_promotion_requested_by,
    pending_promotion_expires_at = moderator_promotion_expires_at
WHERE moderator_promotion_requested_by IS NOT NULL;

-- Drop old columns and indices/constraints
DROP INDEX IF EXISTS idx_users_admin_promotion_pending;
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_moderator_promotion_pending;

ALTER TABLE users
    DROP COLUMN admin_promotion_requested_by,
    DROP COLUMN admin_promotion_expires_at,
    DROP COLUMN moderator_promotion_requested_by,
    DROP COLUMN moderator_promotion_expires_at;

-- Add new constraint and index
ALTER TABLE users
    ADD CONSTRAINT chk_users_pending_promotion CHECK (
        (pending_promotion_requested_by IS NULL AND pending_promotion_expires_at IS NULL AND pending_promotion_role IS NULL)
        OR (pending_promotion_requested_by IS NOT NULL AND pending_promotion_expires_at IS NOT NULL AND pending_promotion_role IS NOT NULL)
    );

CREATE INDEX idx_users_pending_promotion_expires 
    ON users (pending_promotion_expires_at) 
    WHERE pending_promotion_requested_by IS NOT NULL;


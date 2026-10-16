-- 1. Add new unified columns
ALTER TABLE users ADD COLUMN pending_promotion_role VARCHAR(20);
ALTER TABLE users ADD COLUMN pending_promotion_requested_by UUID REFERENCES users(id);
ALTER TABLE users ADD COLUMN pending_promotion_expires_at TIMESTAMP WITH TIME ZONE;

-- 2. Migrate existing pending admin promotions
UPDATE users 
SET pending_promotion_role = 'admin',
    pending_promotion_requested_by = admin_promotion_requested_by,
    pending_promotion_expires_at = admin_promotion_expires_at
WHERE admin_promotion_expires_at IS NOT NULL;

-- 3. Migrate existing pending moderator promotions
-- If a user somehow had BOTH, this logically overwrites the admin promotion with the moderator one
-- However, we will use a WHERE clause to avoid overwriting if admin is already set, or just overwrite.
-- Let's just migrate where pending_promotion_role is null.
UPDATE users 
SET pending_promotion_role = 'moderator',
    pending_promotion_requested_by = moderator_promotion_requested_by,
    pending_promotion_expires_at = moderator_promotion_expires_at
WHERE moderator_promotion_expires_at IS NOT NULL 
  AND pending_promotion_role IS NULL;

-- 4. Drop old columns
ALTER TABLE users DROP COLUMN admin_promotion_requested_by;
ALTER TABLE users DROP COLUMN admin_promotion_expires_at;
ALTER TABLE users DROP COLUMN moderator_promotion_requested_by;
ALTER TABLE users DROP COLUMN moderator_promotion_expires_at;


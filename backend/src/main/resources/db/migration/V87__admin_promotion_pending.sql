-- Pending Administrator promotion (UC-1.1 A[x+2] — "Promote an existing
-- Contributor or Moderator to Administrator, pending their confirmation").
--
-- A promotion to the admin role is no longer applied immediately: the target
-- keeps their current role until they explicitly confirm. A row is a "pending
-- promotion" while admin_promotion_requested_by IS NOT NULL AND
-- admin_promotion_expires_at > now(). Expired rows stop counting toward the
-- 3-admin cap automatically (the cap queries filter on expires_at), so no
-- cleanup job is required for correctness.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS admin_promotion_requested_by UUID REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS admin_promotion_expires_at   TIMESTAMPTZ;

-- Fast lookup / count of live pending promotions for the cap check.
CREATE INDEX IF NOT EXISTS idx_users_admin_promotion_pending
    ON users (admin_promotion_expires_at)
    WHERE admin_promotion_requested_by IS NOT NULL;

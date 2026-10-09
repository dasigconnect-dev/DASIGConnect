UPDATE users SET pending_promotion_role = LOWER(pending_promotion_role) WHERE pending_promotion_role IS NOT NULL;
ALTER TABLE users ADD CONSTRAINT chk_users_pending_promotion_role CHECK (pending_promotion_role IN ('admin', 'moderator'));

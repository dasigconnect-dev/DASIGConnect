ALTER TABLE users 
    ADD COLUMN IF NOT EXISTS moderator_promotion_requested_by UUID REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS moderator_promotion_expires_at TIMESTAMPTZ;

ALTER TABLE users
    ADD CONSTRAINT chk_users_moderator_promotion_pending CHECK (
        (moderator_promotion_requested_by IS NULL AND moderator_promotion_expires_at IS NULL)
        OR (moderator_promotion_requested_by IS NOT NULL AND moderator_promotion_expires_at IS NOT NULL)
    );

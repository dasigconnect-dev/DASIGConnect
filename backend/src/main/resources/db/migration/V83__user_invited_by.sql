-- Records who issued a user's invitation, so a moderator can tidy up (delete)
-- a contributor they invited that never activated — even after the invitation
-- token itself has been cancelled/removed. Nullable: legacy rows and
-- self-registered accounts have no inviter.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS invited_by_user_id UUID REFERENCES users(id);

CREATE INDEX IF NOT EXISTS idx_users_invited_by
    ON users (invited_by_user_id);

-- Track who issued each invitation so a moderator can only resend / cancel
-- the invitations they sent themselves (admins may still manage any).
-- Nullable: legacy rows have no recorded sender.
ALTER TABLE invitation_tokens
    ADD COLUMN IF NOT EXISTS created_by_user_id UUID REFERENCES users(id);

CREATE INDEX IF NOT EXISTS idx_invitation_tokens_created_by
    ON invitation_tokens (created_by_user_id);

-- V81: personal-data erasure ("right to be forgotten").
--
-- Every FK to users(id) is RESTRICT and audit_log is append-only, so a user
-- who has ever acted cannot be row-deleted. Erasure instead anonymises the
-- users row in place and records when/by whom it happened. Prior audit_log
-- entries keep pointing at the now-scrubbed account.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS purged_at         TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS purged_by_user_id UUID REFERENCES users(id);

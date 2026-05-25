-- V13: Ensure facebook_page_tokens table exists.
-- Guards with IF NOT EXISTS so this migration is safe whether V12 created the
-- table fully, partially, or not at all.

CREATE TABLE IF NOT EXISTS facebook_page_tokens (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    page_id           VARCHAR(50) NOT NULL UNIQUE,
    encrypted_token   TEXT        NOT NULL,
    expires_at        TIMESTAMPTZ,
    is_active         BOOLEAN     NOT NULL DEFAULT true,
    last_validated_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_fb_tokens_page_id
    ON facebook_page_tokens (page_id);

CREATE INDEX IF NOT EXISTS idx_fb_tokens_page_id_active
    ON facebook_page_tokens (page_id, is_active);

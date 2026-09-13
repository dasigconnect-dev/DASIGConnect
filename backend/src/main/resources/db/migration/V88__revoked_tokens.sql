-- Persisted single-token revocation (logout). Previously held in an
-- in-process ConcurrentHashMap (JWTService.blacklistedTokens), which meant a
-- logged-out token became valid again after any backend restart, and would
-- never be revoked at all on a second instance. Stores a SHA-256 hash of the
-- raw JWT (never the token itself), same convention as invitation_tokens /
-- password_reset_tokens.
CREATE TABLE IF NOT EXISTS revoked_tokens (
    id UUID PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Lookup is always "is this hash present and not yet expired" — index the
-- hash (already unique) and expires_at for the cleanup job's delete.
CREATE INDEX IF NOT EXISTS idx_revoked_tokens_expires_at
    ON revoked_tokens (expires_at);

CREATE TABLE IF NOT EXISTS post_templates (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    institution_id UUID REFERENCES institutions(id) ON DELETE SET NULL,
    name VARCHAR(100) NOT NULL,
    target VARCHAR(255) NOT NULL,
    category VARCHAR(100) NOT NULL,
    caption TEXT NOT NULL,
    tags TEXT,
    source_submission_id UUID REFERENCES submissions(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_post_templates_owner
    ON post_templates (owner_user_id, created_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS ux_post_templates_owner_name
    ON post_templates (owner_user_id, LOWER(name));

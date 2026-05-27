ALTER TABLE media_assets
    ADD COLUMN IF NOT EXISTS deleted_by_user_id UUID REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS purged_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_media_assets_deleted_retention
    ON media_assets(deleted_at)
    WHERE deleted_at IS NOT NULL AND purged_at IS NULL;

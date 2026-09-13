ALTER TABLE media_assets
ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_media_assets_institution_content_hash ON media_assets (institution_id, content_hash)
WHERE deleted_at IS NULL
    AND content_hash IS NOT NULL;
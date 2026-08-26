ALTER TABLE media_assets
    ADD COLUMN IF NOT EXISTS media_album_id UUID REFERENCES media_albums(id);

CREATE INDEX IF NOT EXISTS idx_media_assets_media_album_id
    ON media_assets (media_album_id);

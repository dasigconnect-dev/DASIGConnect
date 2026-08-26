CREATE TABLE IF NOT EXISTS media_albums (
    id UUID PRIMARY KEY,
    institution_id UUID NOT NULL REFERENCES institutions(id),
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_media_albums_institution_name
    ON media_albums (institution_id, LOWER(name));

ALTER TABLE media_assets
    ADD COLUMN IF NOT EXISTS media_album_id UUID REFERENCES media_albums(id);

CREATE INDEX IF NOT EXISTS idx_media_assets_media_album_id
    ON media_assets (media_album_id);

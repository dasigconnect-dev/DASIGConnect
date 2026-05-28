-- V4 created asset_tags with columns (asset_id, tag, source) which do not match
-- the AssetTag entity mapping (media_asset_id, label). Drop and recreate with the
-- correct schema. No application data depends on the V4 layout since the entity
-- could never read it with the wrong column names.

DROP TABLE IF EXISTS asset_tags CASCADE;

CREATE TABLE asset_tags (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    media_asset_id UUID        NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE,
    label          VARCHAR(50) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_asset_tags_media_asset_label UNIQUE (media_asset_id, label)
);

CREATE INDEX idx_asset_tags_media_asset_id ON asset_tags(media_asset_id);

ALTER TABLE asset_tags ENABLE ROW LEVEL SECURITY;

CREATE POLICY asset_tags_institution_isolation ON asset_tags
    USING (
        EXISTS (
            SELECT 1 FROM media_assets ma
            WHERE ma.id = asset_tags.media_asset_id
              AND (
                  ma.institution_id = nullif(current_setting('app.current_institution_id', true), '')::UUID
                  OR current_setting('app.current_role', true) = 'administrator'
              )
        )
    );

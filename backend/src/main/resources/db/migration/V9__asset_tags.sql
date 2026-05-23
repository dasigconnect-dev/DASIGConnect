CREATE TABLE asset_tags (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    media_asset_id UUID         NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE,
    label          VARCHAR(50)  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (media_asset_id, label)
);

ALTER TABLE asset_tags ENABLE ROW LEVEL SECURITY;

-- Tenant isolation: only assets belonging to the caller's institution are visible
CREATE POLICY asset_tags_institution_isolation ON asset_tags
    USING (
        media_asset_id IN (
            SELECT id FROM media_assets
            WHERE institution_id = current_setting('app.institution_id', true)::uuid
        )
    );

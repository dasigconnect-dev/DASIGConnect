-- Track lightweight provenance for media AI classification and embedding.
-- This keeps media recommendation debuggable without building a full dataset platform.

ALTER TABLE media_assets
    ADD COLUMN IF NOT EXISTS ai_classified_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS ai_classification_model VARCHAR(100),
    ADD COLUMN IF NOT EXISTS embedding_generated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS embedding_model VARCHAR(100);

ALTER TABLE asset_tags
    ADD COLUMN IF NOT EXISTS source VARCHAR(20) NOT NULL DEFAULT 'manual';

ALTER TABLE asset_tags
    DROP CONSTRAINT IF EXISTS chk_asset_tags_source;

ALTER TABLE asset_tags
    ADD CONSTRAINT chk_asset_tags_source
        CHECK (source IN ('ai_generated', 'manual'));

CREATE INDEX IF NOT EXISTS idx_media_assets_ai_classified_at
    ON media_assets(ai_classified_at)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_media_assets_embedding_generated_at
    ON media_assets(embedding_generated_at)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_asset_tags_source
    ON asset_tags(source);

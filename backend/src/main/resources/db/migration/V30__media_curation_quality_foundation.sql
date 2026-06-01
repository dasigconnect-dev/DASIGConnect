-- Capstone 2 / Phase 2 foundation (UC-4.2, UC-4.3, UC-4.4)
-- Adds human-curation fields plus deterministic quality / duplicate metadata.
-- Columns are nullable so existing READY assets remain visible and unchanged.

ALTER TABLE media_assets
    ADD COLUMN title TEXT,
    ADD COLUMN curated_at TIMESTAMPTZ,
    ADD COLUMN blur_score NUMERIC,
    ADD COLUMN perceptual_hash BIGINT,
    ADD COLUMN duplicate_of_id UUID REFERENCES media_assets(id) ON DELETE SET NULL;

CREATE INDEX ix_media_assets_import_batch ON media_assets(import_batch_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_media_assets_duplicate_of ON media_assets(duplicate_of_id) WHERE duplicate_of_id IS NOT NULL;
CREATE INDEX ix_media_assets_perceptual_hash ON media_assets(perceptual_hash) WHERE perceptual_hash IS NOT NULL;

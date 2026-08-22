ALTER TABLE submission_media_assets
    ADD COLUMN IF NOT EXISTS caption VARCHAR(500);

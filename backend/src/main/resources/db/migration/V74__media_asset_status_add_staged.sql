-- V73 introduced the STAGED media-asset status but the status CHECK constraint
-- from V25 still only allowed PROCESSING/READY/FAILED/DELETED, so staging an
-- upload failed with chk_media_assets_status. Widen it.

ALTER TABLE media_assets
    DROP CONSTRAINT IF EXISTS chk_media_assets_status;

ALTER TABLE media_assets
    ADD CONSTRAINT chk_media_assets_status
        CHECK (status IN ('STAGED', 'PROCESSING', 'READY', 'FAILED', 'DELETED'));

-- Enable pg_trgm for ILIKE substring search on media asset filenames
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- GIN trigram index for fast ILIKE search on file_name (supports ≤2s for up to 1,000 assets)
CREATE INDEX IF NOT EXISTS idx_media_assets_file_name_trgm
    ON media_assets USING GIN (file_name gin_trgm_ops)
    WHERE deleted_at IS NULL;

-- GIN trigram index on asset_code for code-based search
CREATE INDEX IF NOT EXISTS idx_media_assets_asset_code_trgm
    ON media_assets USING GIN (asset_code gin_trgm_ops)
    WHERE deleted_at IS NULL;

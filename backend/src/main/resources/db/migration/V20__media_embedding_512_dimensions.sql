-- Align media asset embeddings with voyage-3-lite's 512-dimensional output.
-- Existing incompatible vectors are cleared so the reconciliation job can
-- regenerate them with the current model.
DROP INDEX IF EXISTS idx_media_assets_embedding;

ALTER TABLE media_assets
    ALTER COLUMN embedding TYPE VECTOR(512)
    USING NULL::VECTOR(512);

CREATE INDEX IF NOT EXISTS idx_media_assets_embedding
    ON media_assets USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100)
    WHERE embedding IS NOT NULL;

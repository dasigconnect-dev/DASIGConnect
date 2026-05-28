-- Add dual embedding storage for AI media suggestions while keeping the legacy
-- media_assets.embedding column available during migration.

ALTER TABLE media_assets
    ADD COLUMN IF NOT EXISTS asset_type TEXT,
    ADD COLUMN IF NOT EXISTS visible_objects TEXT[],
    ADD COLUMN IF NOT EXISTS specific_subjects TEXT[],
    ADD COLUMN IF NOT EXISTS visual_style TEXT[],
    ADD COLUMN IF NOT EXISTS dominant_colors TEXT[],
    ADD COLUMN IF NOT EXISTS possible_use_cases TEXT[],
    ADD COLUMN IF NOT EXISTS ai_tags TEXT[],
    ADD COLUMN IF NOT EXISTS excluded_categories TEXT[],
    ADD COLUMN IF NOT EXISTS reclassified_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS status TEXT;

UPDATE media_assets
SET status = 'READY'
WHERE deleted_at IS NULL
  AND embedding IS NOT NULL
  AND status IS NULL;

UPDATE media_assets
SET status = 'DELETED'
WHERE deleted_at IS NOT NULL
  AND status IS NULL;

UPDATE media_assets
SET status = 'PROCESSING'
WHERE status IS NULL;

ALTER TABLE media_assets
    ALTER COLUMN status SET DEFAULT 'PROCESSING';

ALTER TABLE media_assets
    DROP CONSTRAINT IF EXISTS chk_media_assets_status;

ALTER TABLE media_assets
    ADD CONSTRAINT chk_media_assets_status
        CHECK (status IN ('PROCESSING', 'READY', 'FAILED', 'DELETED'));

CREATE TABLE IF NOT EXISTS media_asset_embeddings (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id       UUID        NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE,
    embedding_type TEXT        NOT NULL CHECK (embedding_type IN ('image', 'semantic', 'keyframe')),
    embedding      VECTOR(1024) NOT NULL,
    model          TEXT        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO media_asset_embeddings (asset_id, embedding_type, embedding, model)
SELECT id, 'semantic', embedding, COALESCE(embedding_model, 'legacy-media_assets.embedding')
FROM media_assets
WHERE embedding IS NOT NULL
ON CONFLICT DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_media_asset_embeddings_vector
    ON media_asset_embeddings
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

CREATE INDEX IF NOT EXISTS idx_media_asset_embeddings_asset_type
    ON media_asset_embeddings(asset_id, embedding_type);

CREATE UNIQUE INDEX IF NOT EXISTS uq_media_asset_embedding_type
    ON media_asset_embeddings(asset_id, embedding_type);

ALTER TABLE media_asset_embeddings ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS media_asset_embeddings_institution_isolation ON media_asset_embeddings;

CREATE POLICY media_asset_embeddings_institution_isolation
    ON media_asset_embeddings
    USING (
        EXISTS (
            SELECT 1
            FROM media_assets ma
            WHERE ma.id = media_asset_embeddings.asset_id
              AND ma.deleted_at IS NULL
              AND (
                  ma.institution_id = nullif(current_setting('app.current_institution_id', true), '')::UUID
                  OR current_setting('app.current_role', true) = 'administrator'
              )
        )
    );

CREATE INDEX IF NOT EXISTS idx_media_assets_status_ready
    ON media_assets(institution_id, status, created_at DESC)
    WHERE deleted_at IS NULL;

-- V3: media_assets, asset_tags, submission_media_assets tables
-- Required for UC-1.3 (submission media) and UC-2.2 (media repository)
-- pgvector extension already enabled in V1

CREATE TABLE media_assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id UUID NOT NULL REFERENCES institutions(id),
    uploader_id UUID NOT NULL REFERENCES users(id),
    asset_code VARCHAR(50) NOT NULL UNIQUE,
    storage_url TEXT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(10) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    ai_category VARCHAR(50),
    ai_confidence NUMERIC(5,4),
    ai_description TEXT,
    embedding VECTOR(1024),
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_media_assets_file_type CHECK (file_type IN ('jpeg', 'png', 'webp', 'gif', 'mp4', 'mov', 'webm'))
);

CREATE UNIQUE INDEX idx_media_assets_asset_code ON media_assets(asset_code);
CREATE INDEX idx_media_assets_institution ON media_assets(institution_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_media_assets_uploader ON media_assets(uploader_id);
CREATE INDEX idx_media_assets_ai_category ON media_assets(institution_id, ai_category) WHERE deleted_at IS NULL;
CREATE INDEX idx_media_assets_embedding ON media_assets USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100) WHERE embedding IS NOT NULL;

ALTER TABLE media_assets ENABLE ROW LEVEL SECURITY;
CREATE POLICY media_assets_tenant_isolation ON media_assets
    USING (
        institution_id = nullif(current_setting('app.current_institution_id', true), '')::UUID
        OR current_setting('app.current_role', true) = 'administrator'
    );

CREATE TABLE asset_tags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id UUID NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE,
    tag VARCHAR(100) NOT NULL,
    source VARCHAR(20) NOT NULL DEFAULT 'custom',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_asset_tags_source CHECK (source IN ('ai_generated', 'custom')),
    CONSTRAINT uq_asset_tags_asset_tag UNIQUE (asset_id, tag)
);

CREATE INDEX idx_asset_tags_asset ON asset_tags(asset_id);

ALTER TABLE asset_tags ENABLE ROW LEVEL SECURITY;
CREATE POLICY asset_tags_tenant_isolation ON asset_tags
    USING (
        EXISTS (
            SELECT 1 FROM media_assets ma
            WHERE ma.id = asset_tags.asset_id
              AND (
                  ma.institution_id = nullif(current_setting('app.current_institution_id', true), '')::UUID
                  OR current_setting('app.current_role', true) = 'administrator'
              )
        )
    );

CREATE TABLE submission_media_assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id UUID NOT NULL REFERENCES submissions(id) ON DELETE CASCADE,
    media_asset_id UUID NOT NULL REFERENCES media_assets(id),
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_submission_media_asset UNIQUE (submission_id, media_asset_id)
);

CREATE INDEX idx_submission_media_submission ON submission_media_assets(submission_id);
CREATE INDEX idx_submission_media_asset ON submission_media_assets(media_asset_id);

ALTER TABLE submission_media_assets ENABLE ROW LEVEL SECURITY;
CREATE POLICY submission_media_assets_tenant_isolation ON submission_media_assets
    USING (
        EXISTS (
            SELECT 1 FROM submissions s
            WHERE s.id = submission_media_assets.submission_id
              AND (
                  s.institution_id = nullif(current_setting('app.current_institution_id', true), '')::UUID
                  OR current_setting('app.current_role', true) = 'administrator'
              )
        )
    );

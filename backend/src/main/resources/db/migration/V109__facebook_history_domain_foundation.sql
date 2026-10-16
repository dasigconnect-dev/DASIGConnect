ALTER TABLE media_assets
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(30) NOT NULL DEFAULT 'USER_UPLOAD',
    ADD COLUMN IF NOT EXISTS source_page_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS source_post_id VARCHAR(150),
    ADD COLUMN IF NOT EXISTS source_media_id VARCHAR(150),
    ADD COLUMN IF NOT EXISTS system_managed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS imported_at TIMESTAMPTZ;

ALTER TABLE media_assets DROP CONSTRAINT IF EXISTS chk_media_assets_source_type;
ALTER TABLE media_assets ADD CONSTRAINT chk_media_assets_source_type
    CHECK (source_type IN ('USER_UPLOAD', 'FACEBOOK_IMPORT'));

ALTER TABLE media_assets DROP CONSTRAINT IF EXISTS chk_media_assets_facebook_provenance;
ALTER TABLE media_assets ADD CONSTRAINT chk_media_assets_facebook_provenance
    CHECK (
        source_type <> 'FACEBOOK_IMPORT'
        OR (
            source_page_id IS NOT NULL
            AND source_post_id IS NOT NULL
            AND source_media_id IS NOT NULL
            AND system_managed = TRUE
            AND imported_at IS NOT NULL
        )
    );

CREATE UNIQUE INDEX IF NOT EXISTS uq_media_assets_facebook_source
    ON media_assets (source_page_id, source_media_id)
    WHERE source_type = 'FACEBOOK_IMPORT' AND deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_facebook_page_tokens_id_page
    ON facebook_page_tokens (id, page_id);

CREATE TABLE facebook_import_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    facebook_page_token_id UUID NOT NULL REFERENCES facebook_page_tokens(id),
    scope_institution_id UUID REFERENCES institutions(id),
    page_id VARCHAR(100) NOT NULL,
    page_name VARCHAR(255) NOT NULL,
    date_from TIMESTAMPTZ NOT NULL,
    date_to TIMESTAMPTZ NOT NULL,
    mode VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'QUEUED',
    after_cursor TEXT,
    posts_discovered BIGINT NOT NULL DEFAULT 0,
    posts_imported BIGINT NOT NULL DEFAULT 0,
    posts_updated BIGINT NOT NULL DEFAULT 0,
    media_discovered BIGINT NOT NULL DEFAULT 0,
    media_imported BIGINT NOT NULL DEFAULT 0,
    media_failed BIGINT NOT NULL DEFAULT 0,
    started_by_user_id UUID NOT NULL REFERENCES users(id),
    started_at TIMESTAMPTZ,
    heartbeat_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    error_code VARCHAR(80),
    error_message VARCHAR(500),
    graph_api_version VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_facebook_import_job_dates CHECK (date_to >= date_from),
    CONSTRAINT fk_facebook_import_job_page_token
        FOREIGN KEY (facebook_page_token_id, page_id)
        REFERENCES facebook_page_tokens(id, page_id),
    CONSTRAINT chk_facebook_import_job_mode
        CHECK (mode IN ('PREVIEW', 'BACKFILL', 'INCREMENTAL', 'RESYNC')),
    CONSTRAINT chk_facebook_import_job_status
        CHECK (status IN ('QUEUED', 'RUNNING', 'PAUSED', 'COMPLETED',
                          'COMPLETED_WITH_ERRORS', 'FAILED', 'CANCELLED')),
    CONSTRAINT chk_facebook_import_job_counts CHECK (
        posts_discovered >= 0 AND posts_imported >= 0 AND posts_updated >= 0
        AND media_discovered >= 0 AND media_imported >= 0 AND media_failed >= 0
    )
);

CREATE INDEX idx_facebook_import_jobs_page_status
    ON facebook_import_jobs (page_id, status, created_at DESC);
CREATE INDEX idx_facebook_import_jobs_scope
    ON facebook_import_jobs (scope_institution_id, created_at DESC)
    WHERE scope_institution_id IS NOT NULL;

CREATE TABLE facebook_historical_posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    page_id VARCHAR(100) NOT NULL,
    graph_post_id VARCHAR(150) NOT NULL,
    institution_id UUID REFERENCES institutions(id),
    message TEXT,
    permalink_url TEXT,
    created_time TIMESTAMPTZ NOT NULL,
    updated_time TIMESTAMPTZ,
    post_type VARCHAR(50),
    media_type VARCHAR(50),
    attachment_count INTEGER NOT NULL DEFAULT 0,
    is_published BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted_at_source BOOLEAN NOT NULL DEFAULT FALSE,
    source_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    first_imported_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_synced_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_facebook_historical_post UNIQUE (page_id, graph_post_id),
    CONSTRAINT uq_facebook_historical_post_id_page UNIQUE (id, page_id),
    CONSTRAINT chk_facebook_historical_post_attachments CHECK (attachment_count >= 0)
);

CREATE INDEX idx_facebook_historical_posts_page_created
    ON facebook_historical_posts (page_id, created_time DESC);
CREATE INDEX idx_facebook_historical_posts_institution
    ON facebook_historical_posts (institution_id, created_time DESC)
    WHERE institution_id IS NOT NULL;

CREATE TABLE facebook_post_media (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    facebook_historical_post_id UUID NOT NULL,
    page_id VARCHAR(100) NOT NULL,
    graph_media_id VARCHAR(150) NOT NULL,
    media_asset_id UUID REFERENCES media_assets(id),
    position INTEGER NOT NULL,
    media_type VARCHAR(50) NOT NULL,
    width INTEGER,
    height INTEGER,
    source_alt_text TEXT,
    source_url_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_facebook_post_media_page
        FOREIGN KEY (facebook_historical_post_id, page_id)
        REFERENCES facebook_historical_posts(id, page_id) ON DELETE CASCADE,
    CONSTRAINT uq_facebook_post_media_graph
        UNIQUE (facebook_historical_post_id, graph_media_id),
    CONSTRAINT uq_facebook_post_media_position
        UNIQUE (facebook_historical_post_id, position),
    CONSTRAINT chk_facebook_post_media_position CHECK (position >= 0),
    CONSTRAINT chk_facebook_post_media_dimensions CHECK (
        (width IS NULL OR width > 0) AND (height IS NULL OR height > 0)
    )
);

CREATE INDEX idx_facebook_post_media_page
    ON facebook_post_media (page_id, facebook_historical_post_id, position);
CREATE INDEX idx_facebook_post_media_asset
    ON facebook_post_media (media_asset_id)
    WHERE media_asset_id IS NOT NULL;

CREATE TABLE facebook_engagement_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    facebook_historical_post_id UUID NOT NULL,
    page_id VARCHAR(100) NOT NULL,
    fetched_at TIMESTAMPTZ NOT NULL,
    reactions_total BIGINT,
    reaction_breakdown JSONB NOT NULL DEFAULT '{}'::jsonb,
    comments_count BIGINT,
    shares_count BIGINT,
    reach BIGINT,
    impressions BIGINT,
    post_clicks BIGINT,
    link_clicks BIGINT,
    photo_views BIGINT,
    video_views BIGINT,
    follower_count BIGINT,
    organic_metrics JSONB NOT NULL DEFAULT '{}'::jsonb,
    paid_metrics JSONB NOT NULL DEFAULT '{}'::jsonb,
    metric_availability JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_facebook_engagement_snapshot_page
        FOREIGN KEY (facebook_historical_post_id, page_id)
        REFERENCES facebook_historical_posts(id, page_id) ON DELETE CASCADE,
    CONSTRAINT uq_facebook_engagement_snapshot
        UNIQUE (facebook_historical_post_id, fetched_at),
    CONSTRAINT chk_facebook_engagement_nonnegative CHECK (
        (reactions_total IS NULL OR reactions_total >= 0)
        AND (comments_count IS NULL OR comments_count >= 0)
        AND (shares_count IS NULL OR shares_count >= 0)
        AND (reach IS NULL OR reach >= 0)
        AND (impressions IS NULL OR impressions >= 0)
        AND (post_clicks IS NULL OR post_clicks >= 0)
        AND (link_clicks IS NULL OR link_clicks >= 0)
        AND (photo_views IS NULL OR photo_views >= 0)
        AND (video_views IS NULL OR video_views >= 0)
        AND (follower_count IS NULL OR follower_count >= 0)
    )
);

CREATE INDEX idx_facebook_engagement_snapshots_post_fetched
    ON facebook_engagement_snapshots (facebook_historical_post_id, fetched_at DESC);
CREATE INDEX idx_facebook_engagement_snapshots_page_fetched
    ON facebook_engagement_snapshots (page_id, fetched_at DESC);

ALTER TABLE facebook_import_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE facebook_historical_posts ENABLE ROW LEVEL SECURITY;
ALTER TABLE facebook_post_media ENABLE ROW LEVEL SECURITY;
ALTER TABLE facebook_engagement_snapshots ENABLE ROW LEVEL SECURITY;

CREATE POLICY facebook_import_jobs_admin_system_access ON facebook_import_jobs
    FOR ALL
    USING (COALESCE(current_setting('app.current_role', true), '') IN ('', 'admin'))
    WITH CHECK (COALESCE(current_setting('app.current_role', true), '') IN ('', 'admin'));

CREATE POLICY facebook_historical_posts_admin_system_access ON facebook_historical_posts
    FOR ALL
    USING (COALESCE(current_setting('app.current_role', true), '') IN ('', 'admin'))
    WITH CHECK (COALESCE(current_setting('app.current_role', true), '') IN ('', 'admin'));

CREATE POLICY facebook_post_media_admin_system_access ON facebook_post_media
    FOR ALL
    USING (COALESCE(current_setting('app.current_role', true), '') IN ('', 'admin'))
    WITH CHECK (COALESCE(current_setting('app.current_role', true), '') IN ('', 'admin'));

CREATE POLICY facebook_engagement_snapshots_admin_system_access ON facebook_engagement_snapshots
    FOR ALL
    USING (COALESCE(current_setting('app.current_role', true), '') IN ('', 'admin'))
    WITH CHECK (COALESCE(current_setting('app.current_role', true), '') IN ('', 'admin'));

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON TABLE facebook_import_jobs, facebook_historical_posts,
            facebook_post_media, facebook_engagement_snapshots FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON TABLE facebook_import_jobs, facebook_historical_posts,
            facebook_post_media, facebook_engagement_snapshots FROM authenticated;
    END IF;
END $$;

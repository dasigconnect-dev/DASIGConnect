-- Capstone 2 / Phase 1 (UC-4.1, UC-4.2 infra)
-- Folders (single-parent hierarchy, Google Drive model), Albums (many-to-many curated
-- collections, Google Photos model), and import batches for the bounded ingestion queue.
-- See docs/md/CAPSTONE2_PHASE1_PLAN.md and docs/adr/0004-folders-vs-albums-modeling.md.
-- All new media_assets columns are nullable; no backfill required.

-- ── Folders: where an asset lives (single parent) ──────────────────────────────
CREATE TABLE media_folders (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id   UUID        NOT NULL REFERENCES institutions(id),
    parent_folder_id UUID        REFERENCES media_folders(id) ON DELETE CASCADE,
    name             TEXT        NOT NULL,
    created_by       UUID        NOT NULL REFERENCES users(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_media_folders_institution ON media_folders(institution_id);
CREATE INDEX ix_media_folders_parent ON media_folders(parent_folder_id);

-- ── Import batches: one row per bulk upload (UC-4.2 ingestion queue) ────────────
CREATE TABLE media_import_batches (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id UUID        NOT NULL REFERENCES institutions(id),
    uploaded_by    UUID        NOT NULL REFERENCES users(id),
    asset_count    INT         NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_media_import_batches_institution ON media_import_batches(institution_id);

-- ── Albums: curated collections across folders (many-to-many) ──────────────────
CREATE TABLE media_albums (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id UUID        NOT NULL REFERENCES institutions(id),
    name           TEXT        NOT NULL,
    description    TEXT,
    source         TEXT        NOT NULL DEFAULT 'manual',   -- manual | ai_suggested (Phase 2)
    cover_asset_id UUID        REFERENCES media_assets(id) ON DELETE SET NULL,
    created_by     UUID        NOT NULL REFERENCES users(id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_media_albums_source CHECK (source IN ('manual', 'ai_suggested'))
);
CREATE INDEX ix_media_albums_institution ON media_albums(institution_id);

-- Junction (surrogate id + unique, mirroring submission_media_assets)
CREATE TABLE album_assets (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    album_id      UUID        NOT NULL REFERENCES media_albums(id) ON DELETE CASCADE,
    asset_id      UUID        NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE,
    display_order INT         NOT NULL DEFAULT 0,
    added_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_album_asset UNIQUE (album_id, asset_id)
);
CREATE INDEX ix_album_assets_album ON album_assets(album_id);
CREATE INDEX ix_album_assets_asset ON album_assets(asset_id);

-- ── media_assets: folder pointer + batch provenance (nullable, no backfill) ─────
ALTER TABLE media_assets ADD COLUMN folder_id       UUID REFERENCES media_folders(id) ON DELETE SET NULL;
ALTER TABLE media_assets ADD COLUMN import_batch_id UUID REFERENCES media_import_batches(id);
CREATE INDEX ix_media_assets_folder ON media_assets(folder_id) WHERE deleted_at IS NULL;

-- ── RLS (institution-scoped; both app + DB layers required) ────────────────────
ALTER TABLE media_folders ENABLE ROW LEVEL SECURITY;
CREATE POLICY media_folders_tenant_isolation ON media_folders
    USING (
        institution_id = nullif(current_setting('app.current_institution_id', true), '')::uuid
        OR current_setting('app.current_role', true) = 'administrator'
    );

ALTER TABLE media_import_batches ENABLE ROW LEVEL SECURITY;
CREATE POLICY media_import_batches_tenant_isolation ON media_import_batches
    USING (
        institution_id = nullif(current_setting('app.current_institution_id', true), '')::uuid
        OR current_setting('app.current_role', true) = 'administrator'
    );

ALTER TABLE media_albums ENABLE ROW LEVEL SECURITY;
CREATE POLICY media_albums_tenant_isolation ON media_albums
    USING (
        institution_id = nullif(current_setting('app.current_institution_id', true), '')::uuid
        OR current_setting('app.current_role', true) = 'administrator'
    );

ALTER TABLE album_assets ENABLE ROW LEVEL SECURITY;
CREATE POLICY album_assets_tenant_isolation ON album_assets
    USING (
        EXISTS (
            SELECT 1 FROM media_albums a
            WHERE a.id = album_assets.album_id
              AND (
                  a.institution_id = nullif(current_setting('app.current_institution_id', true), '')::uuid
                  OR current_setting('app.current_role', true) = 'administrator'
              )
        )
    );

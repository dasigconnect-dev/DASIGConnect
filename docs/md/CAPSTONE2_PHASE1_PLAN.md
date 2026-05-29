# Capstone 2 — Phase 1 Implementation Plan

> **Phase 1 (scope §7):** Folders/Albums (UC-4.1) + bounded ingestion-queue **infra** (UC-4.2).
> **Risk:** Low. **Target:** first demoable release.
> **Method:** design-by-feature → build-by-feature, vertical slices (migration + backend +
> frontend + tests together). Build on the **dev Supabase project** (Flyway at v27 → next is **V28**).
> Auto-grouping/clustering itself is **Phase 2** — Phase 1 only builds the queue that makes a
> 200-asset dump safe.

---

## 1. Deliverables

Modeled on Google Drive (folders) + Google Photos (albums), AI-advanced. See **ADR-0004**.

1. **UC-4.1a Folders** — nested, single-parent hierarchy per institution (where an asset
   *lives*); assign asset to a folder (`folder_id`); bulk move / tag / delete. Contributor
   can organize ~100 assets in ≤2 min; foldered retrieval ≤2 s.
2. **UC-4.1b Albums** — many-to-many curated collections (an asset can be in several albums
   without moving). Manual album CRUD + add/remove assets in Phase 1; **Phase 2 AI
   auto-grouping writes albums with `source = ai_suggested`** into the same structure.
3. **UC-4.2 ingestion-queue infra** — replace fire-and-forget `@Async` enrichment with a
   bounded worker so a 200-asset dump cannot blow up threads / the HikariCP-5 pool.

## 2. V28 migration — `V28__media_folders_and_import_batches.sql`

Grounded in scope §5.1; add columns nullable, RLS in the same migration.

```sql
CREATE TABLE media_folders (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id   UUID NOT NULL REFERENCES institutions(id),
    parent_folder_id UUID REFERENCES media_folders(id) ON DELETE CASCADE,
    name             TEXT NOT NULL,
    created_by       UUID NOT NULL REFERENCES users(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_media_folders_institution ON media_folders(institution_id);
CREATE INDEX ix_media_folders_parent ON media_folders(parent_folder_id);

CREATE TABLE media_import_batches (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id UUID NOT NULL REFERENCES institutions(id),
    uploaded_by    UUID NOT NULL REFERENCES users(id),
    asset_count    INT  NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_media_import_batches_institution ON media_import_batches(institution_id);

-- Albums: many-to-many curated collections (Google Photos model)
CREATE TABLE media_albums (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id UUID NOT NULL REFERENCES institutions(id),
    name           TEXT NOT NULL,
    description    TEXT,
    source         TEXT NOT NULL DEFAULT 'manual',   -- manual | ai_suggested (Phase 2)
    cover_asset_id UUID REFERENCES media_assets(id) ON DELETE SET NULL,
    created_by     UUID NOT NULL REFERENCES users(id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_media_albums_source CHECK (source IN ('manual', 'ai_suggested'))
);
CREATE INDEX ix_media_albums_institution ON media_albums(institution_id);

CREATE TABLE album_assets (              -- surrogate id + unique, mirroring submission_media_assets
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    album_id      UUID NOT NULL REFERENCES media_albums(id) ON DELETE CASCADE,
    asset_id      UUID NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE,
    display_order INT  NOT NULL DEFAULT 0,
    added_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_album_asset UNIQUE (album_id, asset_id)
);
CREATE INDEX ix_album_assets_album ON album_assets(album_id);
CREATE INDEX ix_album_assets_asset ON album_assets(asset_id);

ALTER TABLE media_assets ADD COLUMN folder_id       UUID REFERENCES media_folders(id) ON DELETE SET NULL;
ALTER TABLE media_assets ADD COLUMN import_batch_id UUID REFERENCES media_import_batches(id);
-- (no suggested_album_id: album membership is the album_assets junction, source flag on the album)

CREATE INDEX ix_media_assets_folder ON media_assets(folder_id) WHERE deleted_at IS NULL;

-- RLS (scope through institution_id; both layers required, per backend guide)
ALTER TABLE media_folders ENABLE ROW LEVEL SECURITY;
CREATE POLICY media_folders_tenant_isolation ON media_folders
    USING (institution_id = nullif(current_setting('app.current_institution_id', true), '')::uuid
           OR current_setting('app.current_role', true) = 'administrator');

ALTER TABLE media_import_batches ENABLE ROW LEVEL SECURITY;
CREATE POLICY media_import_batches_tenant_isolation ON media_import_batches
    USING (institution_id = nullif(current_setting('app.current_institution_id', true), '')::uuid
           OR current_setting('app.current_role', true) = 'administrator');

ALTER TABLE media_albums ENABLE ROW LEVEL SECURITY;
CREATE POLICY media_albums_tenant_isolation ON media_albums
    USING (institution_id = nullif(current_setting('app.current_institution_id', true), '')::uuid
           OR current_setting('app.current_role', true) = 'administrator');

ALTER TABLE album_assets ENABLE ROW LEVEL SECURITY;
CREATE POLICY album_assets_tenant_isolation ON album_assets
    USING (EXISTS (SELECT 1 FROM media_albums a WHERE a.id = album_assets.album_id
                   AND (a.institution_id = nullif(current_setting('app.current_institution_id', true), '')::uuid
                        OR current_setting('app.current_role', true) = 'administrator')));
```

> `folder_id` (single-parent) uses `ON DELETE SET NULL` so deleting a folder never
> orphans/FK-breaks assets. Albums are many-to-many via `album_assets` (CASCADE so deleting
> an album just removes the links, never the assets). No backfill needed (all new columns
> nullable). Run `flyway:validate` before PR.

## 3. Backend (Controller → Service → Repository, DTOs at the boundary)

New files under `com.dasigconnect.backend`:

| Layer | File | Responsibility |
|-------|------|----------------|
| entity | `MediaFolder`, `MediaAlbum`, `AlbumAsset`, `MediaImportBatch` | JPA entities; add `folderId`/`importBatchId` to `MediaAsset` |
| repository | `MediaFolderRepository`, `MediaAlbumRepository`, `AlbumAssetRepository`, `MediaImportBatchRepository` | institution-scoped queries (pass `institutionId` explicitly) |
| service | `MediaFolderService` | create/rename/move/delete folder; assign asset (single `folder_id`); bulk move/tag/delete; audit-log each (sets up UC-4.11 habit) |
| service | `MediaAlbumService` | create/rename/delete album; add/remove assets (many-to-many); set cover; audit-log each |
| service | `MediaIngestionQueueService` + `IngestionExecutorConfig` | bounded `ThreadPoolTaskExecutor` (~2 workers, bounded queue, CallerRuns); enqueue enrichment; idempotency key per asset |
| controller | `MediaFolderController` (`/api/v1/media-folders`) | folder CRUD + `POST /{id}/assets` (assign), bulk ops; `@PreAuthorize` per role |
| controller | `MediaAlbumController` (`/api/v1/media-albums`) | album CRUD + `POST /{id}/assets` / `DELETE /{id}/assets/{assetId}` (add/remove) |
| dto | folder + album + batch request/response DTOs | never expose entities |

**Ingestion-queue refactor:** the current per-upload `@Async` Claude+Voyage path becomes
an enqueue onto `MediaIngestionQueueService`. Worker drains at a controlled rate; acquires a
DB connection **per micro-batch**, never across an external call; asset flips `PROCESSING →
READY` only after required embeddings are stored; `FAILED`-after-N is the dead-letter. The
existing `EmbeddingReconciliationJob` remains the idempotent retry net. Batch Voyage **text**
embeddings where multiple assets are queued; image multimodal stays 1-per. Guard bursts with
a `Semaphore`/`RateLimiter`.

## 4. Frontend (vertical slice — invoke `/ui-ux-pro-max`)

- **Folder tree** (left rail) in `MediaRepositoryScreen`: create/rename/delete, click to
  filter by folder; drag-or-button **assign to folder** + **bulk move** on selected assets
  (reuse `usePersistentSelection`).
- **Albums view**: list albums, open an album (its assets), create album, add/remove
  selected assets, set cover. AI-suggested albums (Phase 2) surface here with a badge.
- `mediaApi.ts` additions for folder + album CRUD, assign, add/remove, bulk endpoints.
- Loading/empty/error states; foldered list + album grid views.

## 5. Task breakdown (vertical slices, dependency order)

Each is one card / one branch (`feat/uc41-...`, `feat/uc42-queue`):

1. ✅ **V28 migration + entities + repos** (done 2026-05-30; applied to dev DB → v28; RLS in-migration; compiles, boots clean).
2. **Folder CRUD backend slice** + `MediaFolderControllerTest`, `MediaFolderServiceTest`.
3. **Folder UI slice** (tree, create/rename/delete, list by folder).
4. **Assign + bulk move/tag/delete slice** (backend + UI + tests).
5. **Album backend slice** (CRUD + add/remove/cover, many-to-many) + tests.
6. **Album UI slice** (album grid, open album, add/remove selected, cover).
7. **Ingestion-queue infra** (executor config + service + enrichment refactor) + tests.
8. **Spike — 200-asset dump** (D5): measure wall-clock to all-`READY`, max concurrent
   Hikari connections (must stay ≤5, 0 timeouts), `FAILED` count. Record the throughput
   number in `docs/eval/`.

## 6. Tests & Definition of Done

- Per-card DoD from `CAPSTONE2_PROCESS.md §5` (skill invoked, tests green, migration applied
  on dev, RLS verified, docs updated).
- Keep the suite green (currently 273). Add focused tests per slice.
- Phase-1 acceptance: create/move/delete folders works end-to-end in the browser; a 200-asset
  enqueue completes to `READY` with **zero dropped DB connections**.

## 7. Decisions

**Resolved (see ADR-0004):**
- **Folders vs albums** — *two distinct concepts.* Folders = single-parent hierarchy
  (`folder_id`, Google Drive model). Albums = many-to-many curated collections
  (`album_assets` junction, Google Photos model). Best for scalability + maintainability,
  and gives AI auto-grouping (Phase 2) a home (`media_albums.source = 'ai_suggested'`).
- **Folder membership** — single `folder_id` (an asset lives in one folder). Multi-context
  discovery is served by albums + tags + AI search, not multi-parent folders.

**Still tunable (no schema impact):**
- **Folder nesting depth** — default soft cap ~8 levels, enforced in `MediaFolderService`.
- **Worker count / queue size** — start ~2 workers; tune against the spike (card 8) result.

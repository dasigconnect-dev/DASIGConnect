# Capstone 2 - Phase 7 Digital Preservation and Repository Health Plan

> **Scope:** UC-4.12 Digital Preservation and Repository Health.
> **Purpose:** Move the Media Library from governed storage to a preservation-aware
> digital resource management system.
> **Delivery method:** Feature-driven Kanban flow using vertical slices.
> **Priority:** Phase 7A-7C may proceed while Phase 5 waits for Meta App Review because
> integrity monitoring has no dependency on Facebook insights.
> **Implementation status (2026-06-06):** Phase 7A-7G **all implemented** — UC-4.12 is feature
> complete. V34-V38 cover fixity, review workflow, rights records, lineage, and duplicate review;
> 7C and 7G added no migration. (The only outstanding item is D1's ≥50-pair human-verified set —
> a manual review pass using the 7F workspace, not an automatable step.)
> **Next Flyway version:** V41. Never reuse an applied migration version. V39 is occupied by
> `V39__seed_lerah_caones_contributor.sql` in the connected dev DB, and V40 is
> `V40__facebook_post_metrics.sql`.

---

## 1. Outcome

Phase 7 must let DASIG answer five operational questions for every managed resource:

1. Is the stored file still present and unchanged?
2. Who owns it, and where may it be used?
3. Is this the original, a replacement, or a derived version?
4. Does it require curation, duplicate review, or corrective action?
5. Can its metadata and history be exported without locking DASIG into this application?

This phase complements the existing repository capabilities:

- persistent asset codes;
- folders, collections, and import batches;
- AI and human metadata;
- duplicate and quality signals;
- hybrid natural-language search;
- retention and controlled purge;
- visibility/consent gating;
- immutable media audit history.

It does not replace those features or introduce autonomous AI decisions.

## 2. Core Design Decisions

### 2.1 File fixity is separate from visual similarity

- Keep `perceptual_hash` for exact/near-duplicate image detection.
- Add SHA-256 for bit-level file integrity.
- A pHash match means two images look similar; a SHA-256 match means the file bytes are
  unchanged. These signals must never be used interchangeably.

### 2.2 Original files are immutable

- Metadata may be edited without replacing the stored original.
- Crops, compressed copies, collages, and replacements become new assets linked through
  lineage records.
- No endpoint overwrites the original storage object in place.

### 2.3 Integrity checks are short, bounded jobs

- Select a small batch of candidate asset IDs in a short database transaction.
- Download each Supabase object and stream its SHA-256 calculation with no database
  transaction open.
- Persist the result in a separate short transaction.
- Keep the scheduler idempotent and configurable; default batch size should not exceed 25.
- A failed download records `missing` or `error`; it must not delete or silently replace data.

### 2.4 Human governance remains authoritative

- AI may suggest metadata or safety review, but rights clearance, duplicate adjudication,
  canonical-version selection, and repair decisions require a user action.
- Existing assets grandfathered as `cleared_for_public` remain usable. New rights
  requirements apply progressively and must not freeze the existing publishing workflow.

## 3. Proposed Data Model

### 3.1 Fixity fields on `media_assets`

Add:

- `content_sha256 CHAR(64)`
- `checksum_generated_at TIMESTAMPTZ`
- `integrity_status TEXT`
  - `pending`
  - `verified`
  - `mismatch`
  - `missing`
  - `error`
- `integrity_checked_at TIMESTAMPTZ`
- `integrity_failure_reason TEXT`

The stored checksum is the expected ingest checksum. Scheduled checks compare newly
calculated bytes against it; they never rewrite the expected value after a mismatch.

### 3.2 Integrity-check history

Create `media_asset_integrity_checks`:

- `id`
- `institution_id`
- `asset_id`
- `expected_sha256`
- `observed_sha256`
- `status`
- `trigger` (`ingest`, `scheduled`, `manual`, `repair_verification`)
- `failure_reason`
- `checked_at`

Apply RLS and explicit institution filtering. This table is append-only evidence.

### 3.3 Rights and consent record

Create one current `media_asset_rights` record per asset:

- `institution_id`, `asset_id`
- `rights_holder`
- `rights_basis` (`owned`, `consent`, `licensed`, `public_domain`, `unknown`)
- `license_uri`
- `consent_reference`
- `clearance_date`
- `expires_at`
- `permitted_channels`
- `restrictions`
- `supporting_document_url`
- `verified_by`, `verified_at`

The existing `visibility` field remains the fast publishing gate. A later enforcement step
may require a verified, non-expired rights record before changing a new asset to
`cleared_for_public`.

### 3.4 Asset lineage

Create `media_asset_relations`:

- `institution_id`
- `parent_asset_id`
- `child_asset_id`
- `relation_type`
  - `new_version`
  - `derived_from`
  - `replacement_for`
  - `component_of`
- `created_by`, `created_at`

Reject self-links, cross-institution links, and cycles. Preserve every related asset's own
asset code, audit history, metadata, and fixity record.

### 3.5 Duplicate decisions

Create append-only `media_duplicate_reviews` records for:

- candidate and canonical asset IDs;
- decision (`duplicate`, `keep_both`, `not_duplicate`);
- optional metadata/collection merge choices;
- reviewer and timestamp.

Do not physically delete a duplicate as part of the review decision. Deletion remains a
separate audited action using the existing retention workflow.

## 4. Vertical Slices

### Phase 7A - SHA-256 ingest fixity

- [x] V34 migration for fixity fields and integrity-check history.
- [x] Generate checksums asynchronously after new Media Library and submission uploads.
- [x] Allow existing active assets to be verified manually; bounded automatic backfill is
  part of Phase 7B.
- [x] Add integrity fields to asset detail DTOs.
- [x] Display `Pending`, `Verified`, `Mismatch`, `Missing`, or `Error` in the existing
  Asset Detail sidebar.
- [x] Add authenticated manual verification and integrity-history endpoints.

**Acceptance:** a newly uploaded asset receives an expected SHA-256 value and a verified
ingest-check record without holding a database connection during storage download.

**Verification (2026-06-06):**

- V34 validated and applied to the Supabase development database; schema version is 34.
- Backend production compilation passed.
- 60 focused backend tests passed for the slice; the complete backend suite also passed
  with 396 tests and 7 intentional skips.
- Frontend production build passed.
- The existing Asset Detail sidebar is reused in both Media Library and Collections; no
  separate detail page or full-page takeover was introduced.

### Phase 7B - Scheduled monitoring and repair workflow

- [x] Add `MediaIntegrityVerificationJob` with configurable cadence and a hard maximum
  batch size of 25.
- [x] Prioritize never-checked assets, then due failures, then oldest verified assets.
- [x] Record every result in the append-only history table without holding a database
  transaction during the storage download.
- [x] Notify active institution validators and network administrators when a failure first
  appears or changes; suppress duplicate alerts for 24 hours.
- [x] Add review states (`OPEN`, `ACKNOWLEDGED`, `RESOLVED`) and an audited validator/admin
  acknowledgement endpoint.
- [x] Show recent integrity history, recheck, acknowledgement, and replacement-upload handoff
  in the existing shared Asset Detail sidebar.
- [x] Preserve the original when replacement upload starts. Explicit lineage linkage remains
  Phase 7E.

**Acceptance:** a controlled missing/tampered test object is detected and surfaced without
modifying the expected checksum.

**Verification (2026-06-06):**

- V35 validated and applied to the Supabase development database; schema version is 35.
- Complete backend suite passed with 402 tests and 7 intentional skips.
- Frontend production build and scoped Phase 7B ESLint checks passed.
- Scheduler candidate selection is bounded to 25 and configurable through
  `MEDIA_INTEGRITY_*` environment variables.
- Alert, deduplication, review acknowledgement, tenant access, and immutable-baseline behavior
  have focused automated coverage.

### Phase 7C - Repository Health dashboard

Institution-scoped and administrator network views showing:

- [x] active assets and storage volume;
- [x] checksum coverage and integrity failures (by status and review state);
- [x] processing failures;
- [x] metadata-completeness percentage;
- [x] unorganized and uncurated assets;
- [x] internal-only assets (expiring-rights deferred to Phase 7D);
- [x] duplicate candidates (perceptual-hash flagged; human duplicate-review backlog is Phase 7F);
- [x] deleted assets pending and approaching purge.

Implemented with **one filtered aggregate scan** of `media_assets`
(`MediaAssetRepository.aggregateRepositoryHealth`, `RepositoryHealthCounts` projection) behind a
short in-process TTL cache (`RepositoryHealthService`), exposed at
`GET /api/v1/media-repository/health` (validator = own institution; admin = network or a chosen
institution). Each tile drills into the Media Library via a new `?health=<key>` list filter that
shows a dismissible filter chip. No migration was required (read-only over existing columns), so
the next free Flyway version remains **V36**.

**Acceptance:** every actionable health count links to the affected asset set and respects tenant
scope. ✅ met.

**Verification (2026-06-06):**

- Backend `RepositoryHealthServiceTest` (5) covers scope resolution (admin network / admin
  institution / validator own-institution / cross-institution 403), percentage math, and TTL
  caching. Full suite green: **408 tests, 0 failures, 7 intentional skips**.
- The aggregate is a single native query; its Postgres-specific SQL (`FILTER`, `::bigint`,
  `CAST(... AS uuid)`) is validated at app boot against Supabase like the other native queries.
- Frontend production build passed; scoped ESLint clean on the new health feature, the
  `useMediaAssets` health param, `mediaApi`, and `MediaRepositoryScreen`.
- Frontend is a thin orchestrator (`RepositoryHealthScreen`) composing reusable
  `HealthSection`, `HealthMetricCard`, and `CoverageMeter` components; tokens reused from the
  `--med-*` system.

### Phase 7D - Rights and consent records

- [x] V36 migration for `media_asset_rights` (one current record per asset, RLS, expiry index).
- [x] Editable rights metadata in the shared Asset Detail sidebar (`AssetRightsSection`).
- [x] Derived `incomplete`, `expired`, `expiring_soon`, and `cleared` states.
- [x] Audit every rights change (`MEDIA_ASSET_RIGHTS_UPDATED`).
- [x] Block changing a NEW asset to `cleared_for_public` when its rights record is missing,
  incomplete, or expired (422); already-cleared assets are grandfathered.

Endpoints: `GET|PUT /api/v1/media-assets/{id}/rights` (`MediaRightsService`, tenant-scoped).
The clearance gate lives in `MediaAssetService.changeVisibility` and fires only on the
`→ cleared_for_public` transition — see **ADR-0006** (grandfathering by construction, no dated
cutoff). Validity is a pure function on the entity (`permitsPublicClearance`), shared by the gate
and the rights DTO.

**Acceptance:** a user can explain who owns an asset, why it may be used, where it may be
published, and when permission expires. ✅ met.

**Verification (2026-06-06):**

- V36 added; next free Flyway version is now **V37**. (V36 is validated at app boot against
  Supabase like the other migrations.)
- Backend `MediaRightsServiceTest` (6) covers empty/present reads, upsert + audit, derived
  states, cross-tenant hiding, and basis/expiry validation; `MediaAssetServiceTest` adds the
  422-on-clearance-without-valid-rights gate test. Full suite green: **415 tests, 0 failures,
  7 skips**.
- Frontend production build + scoped ESLint clean; the Asset Detail sidebar gains a
  self-contained `AssetRightsSection` (loads/saves its own data by assetId — no prop threading
  through either host screen); visibility-change handlers now surface the backend 422 gate
  message.

### Phase 7E - Versioning and lineage

- [x] V37 migration for `media_asset_relations` (RLS; self-link, type, and uniqueness constraints).
- [x] Link versions/derivatives via `POST /api/v1/media-assets/{id}/relations`; the workflow
  uploads a separate asset (existing upload path) and links it — the original is never overwritten.
- [x] Show original/version/derivative relationships in the Asset Detail sidebar
  (`AssetLineageSection`, self-contained).
- [x] Preserve the original and prohibit in-place file replacement (a relation is a link between
  independent assets, each keeping its own code, fixity, and history).

Endpoints: `GET|POST /api/v1/media-assets/{id}/relations` (`MediaLineageService`). The service
rejects self-links, cross-institution links, duplicate edges (409), and **cycles** (bounded
forward BFS from the proposed child; 409 if it reaches the parent). Relation creation is audited
(`MEDIA_ASSET_RELATION_ADDED`). Relation types: `new_version`, `replacement_for`, `derived_from`,
`component_of`.

> **Design note:** a one-click "upload-and-auto-link new version" convenience layers on top of the
> existing direct-to-Supabase upload flow and the Phase 7B replacement handoff; the current slice
> links an already-uploaded asset, which already satisfies immutable-original + reconstructable
> lineage. Wiring the replacement handoff to auto-create a `replacement_for` edge is a thin
> follow-up.

**Acceptance:** a replacement or derivative is independently identifiable and its complete
lineage is reconstructable. ✅ met.

**Verification (2026-06-06):**

- V37 added; next free Flyway version is now **V38**.
- Backend `MediaLineageServiceTest` (8) covers lineage read, link + audit, self-link/invalid-type/
  cross-institution (400), duplicate/cycle (409), and cross-tenant hiding (404). Full suite green:
  **423 tests, 0 failures, 7 skips**.
- Frontend production build + scoped ESLint clean; `AssetLineageSection` is self-contained
  (loads/saves its own data by assetId) and reuses the `--med-*` tokens.

### Phase 7F - Duplicate Review workspace

- [x] Present deterministic duplicate candidates (perceptual-hash `duplicate_of_id`) side by side
  with the explainable Hamming distance.
- [x] Support `Mark duplicate`, `Keep both`, and `Not duplicate`.
- [x] Let the reviewer select the canonical asset and optionally merge the duplicate's tags into it.
  (Collection-membership merge is a documented follow-up.)
- [x] Record the decision (V38 `media_duplicate_reviews`, append-only) and audit event
  (`MEDIA_DUPLICATE_REVIEWED`); decisions never delete an asset.
- [ ] Finish D1 with a real, human-verified set of ≥50 pairs. **Not done — this is a human task,
  not an automatable one.** This workspace is the tool that produces those verified labels; D1
  finalization is gated on an actual review pass and must not be fabricated.

Endpoints: `GET /api/v1/media-duplicates` (pending/reviewed, tenant-scoped) and
`POST /api/v1/media-duplicates/{candidateId}/decision` (`MediaDuplicateReviewService`). The
pending queue is flagged candidates without a decision; reversal is a new append-only row
(latest-per-candidate wins). Frontend: a dedicated `/media-repository/duplicates` workspace
(`DuplicateReviewScreen` + `DuplicatePairCard`), reachable from the Media Library header and the
Repository Health "Duplicate candidates" tile.

**Acceptance:** duplicate decisions are explainable (Hamming distance + side-by-side),
reversible through a new decision (append-only, latest wins), and never auto-delete an asset.
✅ met (workspace); D1 human-labeling pass still outstanding.

**Verification (2026-06-06):**

- V38 added; the next preservation-free Flyway version was V39 at the time. The current project
  next free version is **V43** after the Phase 5/5b Facebook insights extensions landed (V40/V41
  content insights, V42 page audience).
- Backend `MediaDuplicateReviewServiceTest` (7) covers pending pair build (with Hamming),
  duplicate decision + audit, tag-merge, canonical-not-in-pair (400), keep-both, invalid decision
  (400), and cross-tenant hiding (404). Full suite green: **430 tests, 0 failures, 7 skips**.
- Frontend production build clean; scoped ESLint clean on the new duplicates feature
  (`App.tsx` retains two pre-existing lint findings unrelated to this slice).

### Phase 7G - Standards mapping and export

- [x] Documented Dublin Core mapping published in `docs/md/dublin-core-mapping.md` (identifier ->
  `asset_code`, title, creator -> uploader **and** rights holder distinguished, date ->
  `created_at`, subject -> manual tags + category, rights -> rights record + visibility,
  relation -> lineage, format -> file type + size).
- [x] Institution-scoped CSV and JSON exports (`GET /api/v1/media-repository/export?format=csv|json`).
- [x] Includes fixity, rights, provenance, lineage, and lifecycle state (`pres_*` columns).
- [x] Each export logged as an audited admin event (`MEDIA_REPOSITORY_EXPORTED`).

`MediaRepositoryExportService` aggregates with batched queries (no per-asset fan-out), builds the
full body in a read-only transaction (no DB connection held while the response streams), and
**omits `storage_url` and every signed/private URL or credential**. JSON embeds the Dublin Core
mapping for downstream interpretation. Export buttons live in the Repository Health header.

**Acceptance:** DASIG can export a portable inventory without exposing storage credentials,
private service URLs, or cross-tenant records. ✅ met (the export DTO has no URL/credential field
and is tenant-scoped; a test asserts the signed URL never appears in the output).

**Verification (2026-06-06):**

- No migration (read-only export); at the time the next free preservation version remained V39.
  The current project next free version is **V43** after V40/V41 content insights and V42 page audience.
- Backend `MediaRepositoryExportServiceTest` (4): CSV headers + asset code + **no storage URL**,
  JSON envelope/mapping/count, invalid-format 400, validator cross-institution 403. Full suite
  green: **434 tests, 0 failures, 7 skips**.
- Frontend build + scoped ESLint clean; CSV/JSON download wired from the Repository Health header.

## 5. API Surface

Planned endpoints:

- `POST /api/v1/media-assets/{id}/integrity-check`
- `GET /api/v1/media-assets/{id}/integrity-history`
- `GET /api/v1/media-repository/health`
- `GET|PUT /api/v1/media-assets/{id}/rights`
- `POST /api/v1/media-assets/{id}/versions`
- `POST /api/v1/media-assets/{id}/relations`
- `GET /api/v1/media-assets/{id}/relations`
- `POST /api/v1/media-duplicates/{candidateId}/decision`
- `GET /api/v1/media-repository/export?format=csv|json`

Controllers remain thin, services own business rules, repositories take explicit
`institutionId`, and responses use DTOs only.

## 6. Evaluation

**Status (2026-06-06):** the Phase 7A-7B (fixity/integrity) portion of D7 is frozen and
measured — `docs/eval/D7_preservation_set.csv` (32 assets, 2 tenants) drives
`MediaIntegrityVerificationD7Test`, which asserts the fixity targets and emits
`docs/eval/D7_results.md` (PASS: 100% coverage, 100% missing/mismatch/error detection, 0 silent
baseline overwrites, alert dedup, no cross-tenant rows). The rights/lineage/duplicate/export
fixtures below are added as Phases 7D-7G land. A complementary live disposable-dev-bucket
spot-check is recommended before defense to exercise the Supabase Storage path end-to-end.

Add dataset **D7 - Preservation and Governance Verification Set**:

- at least 30 active assets across supported file types;
- at least one controlled missing object;
- at least one controlled byte-mismatch object in a disposable test bucket;
- at least five rights records including complete, incomplete, and expired examples;
- at least five version/derivative relationships;
- at least ten duplicate-review decisions.

Targets:

- 100% of active D7 assets receive SHA-256 ingest fixity.
- 100% of controlled missing/mismatch cases are detected.
- 0 expected checksums are silently overwritten after a mismatch.
- 100% of integrity, rights, lineage, and duplicate decisions are audited.
- Repository Health totals match direct database counts for every tested institution.
- 0 cross-tenant records in health, history, rights, lineage, review, or export tests.
- Metadata export contains all required Dublin Core mapping fields for at least 95% of
  human-curated assets.

## 7. Kanban Order

Recommended pull order:

1. Phase 7A - fixity foundation. Complete.
2. Phase 7B - monitoring and repair. Complete.
3. Phase 7C - Repository Health. Complete.
4. Phase 7D - rights records. Complete.
5. Phase 7E - versioning and lineage. Complete.
6. Phase 7F - duplicate review. Complete (D1 human-labeling pass still outstanding).
7. Phase 7G - standards export. Complete.

**All Phase 7 slices are implemented.** Remaining preservation work is non-code: the D1
human-verified duplicate set (via the 7F workspace) and browser E2E once V34-V38 are applied to
the dev database.

## 8. Explicit Boundaries

- No full archival-storage replication platform in this capstone.
- No automatic legal determination of copyright or consent.
- No destructive automatic duplicate merge.
- No overwrite-in-place versioning.
- No public sharing portal or cross-institution rights transfer.
- No PREMIS-complete archival implementation; use PREMIS concepts for fixity/event history
  and document the mapping honestly.

## 9. Definition of Done

- Migration applies cleanly to the Capstone 2 dev database with RLS.
- External storage reads occur outside database transactions.
- Focused service/controller/tenant-isolation tests pass.
- Scheduled jobs are bounded and idempotent.
- Existing upload, publishing, retention, and visibility workflows remain functional.
- Frontend uses the existing Asset Detail sidebar and Media Library layout.
- D7 fixtures and results are committed under `docs/eval/`.
- Scope, process, ADRs, and implementation status are updated with each completed slice.

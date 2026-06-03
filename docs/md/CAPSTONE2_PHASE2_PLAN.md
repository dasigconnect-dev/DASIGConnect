# Capstone 2 — Phase 2 Implementation Plan

> **Phase 2 (scope §7):** AI auto-grouping + quality/duplicate filtering (UC-4.2/4.4) + curation (UC-4.3).
> **Builds on:** Phase 1 folders/albums + bounded ingestion queue.
> **Current state:** Phase 1 is user-tested. Phase 2 foundation, deterministic quality/duplicate analysis, AI-suggested collections, batch curation edit-before-confirm, and prompt-based collection review are implemented locally.

---

## 1. Goal

Turn a bulk upload into a managed import batch:

1. Record every multi-file upload under a `media_import_batches` row.
2. Store curation and quality signals on each asset.
3. Compute deterministic quality and duplicate indicators during ingestion.
4. Suggest albums from import-batch clusters, using the Phase 1 `media_albums.source = 'ai_suggested'` path.
5. Let a contributor review the batch and confirm/edit metadata.
6. Let a contributor describe a desired collection in natural language, review matched assets,
   and create the collection only after explicit selection.

## 2. Vertical Slices

1. ✅ **Phase 2 foundation: import-batch upload wiring + metadata columns**
   - `POST /api/v1/media-assets/import-batches`
   - upload registration carries `importBatchId`
   - `media_assets.title`, `curated_at`, `blur_score`, `perceptual_hash`, `duplicate_of_id`
   - frontend upload modal creates one batch for the selected files and retries failed files under the same batch

2. ✅ **Deterministic quality analyzer (UC-4.4)**
   - compute `blur_score` after upload for image assets
   - keep uploads non-blocking by running inside the bounded ingestion queue
   - expose score/status in detail DTOs; review surface remains a later UI slice

3. ✅ **pHash/dHash duplicate detection foundation (UC-4.4)**
   - compute 64-bit perceptual hash for image assets
   - compare within the same institution
   - set `duplicate_of_id` only for likely exact/near duplicates
   - threshold still needs tuning against `docs/eval/D1_duplicate_pairs.csv`

4. ✅ **AI auto-grouping into suggested collections (UC-4.2)**
   - **image-embedding cosine clustering** (union-find single-link, threshold 0.82, tunable)
     over the batch's `media_asset_embeddings` (type `image`); assets without an image
     embedding fall back to AI-category grouping so nothing is dropped
   - each cluster named by **one best-effort Claude call** on the cluster's already-extracted
     metadata (text only — no image re-scan, scan-once respected); deterministic
     category-based name on failure/blank
   - in-run + DB name de-duplication (`uniqueName`) so distinct clusters are never lost
   - create `media_albums` rows with `source = 'ai_suggested'`, attach via `album_assets`,
     audited as `AI_ALBUM_SUGGESTED`
   - backend auto-grouping endpoint remains available, but the Media Library UI now routes users
     to the review-first **AI Builder** instead of auto-creating collections
   - *Note:* threshold 0.82 is a starting value — tune against a labelled grouping set toward
     the ≥70%-useful (D3) target

5. ✅ **Batch curation UI (UC-4.3) — review, edit, confirm**
   - show a review modal for the latest completed import batch
   - display thumbnail, AI category/tags/description, blur score, duplicate warning, and processing state
   - support refresh while AI metadata is still processing
   - support title/tag edits before confirmation
   - support confirm-all that saves current edits and sets `curated_at` on the batch assets
   - latest fix: batch actions now recover from loaded assets' `importBatchId`, so `Review batch`
     and `Confirm all` do not disappear just because the page reloaded or the in-memory upload
     state was lost
   - fully curated batches no longer keep the review strip visible after refresh

6. ✅ **Prompt-based AI Collection Builder (Phase 2 closeout / Phase 3 bridge)**
   - replaces the Media Library auto-create collection UX with a review-first flow
   - user writes a prompt, receives candidate assets, checks/unchecks results, edits name/description,
     then creates the collection explicitly
   - backend exposes `POST /api/v1/media-albums/suggestions/prompt` as a read-only suggestion endpoint
   - frontend has `PromptCollectionBuilderModal`; Media Library redirects to the Collections AI Builder
   - current matching is metadata-ranked with frontend fallback; Phase 3 upgrades retrieval to full
     hybrid search over lexical + semantic + multimodal photo vectors

## Latest handoff - 2026-06-03

- Batch curation edit-before-confirm is implemented.
- Backend: `POST /api/v1/media-assets/import-batches/{id}/curate` now accepts optional per-asset
  edits (`assetId`, `title`, `tags`) and rejects edits for assets outside the selected batch.
- Backend: `GET /api/v1/media-assets/import-batches` lists recent upload batch groups with
  registered/ready/curated counts.
- Frontend: `Review batch` now opens a list of recent upload batch groups first; selecting a batch
  opens the photos uploaded through that batch. `BatchCurationModal` then allows title and
  comma-separated tag edits; `Save edits and confirm` persists them and marks the batch curated.
- Media Library list responses include `curatedAt`, and the review strip now targets the latest
  uncurated/partially curated batch so fully curated batches stop prompting after refresh.
- Verification: focused backend tests (`MediaAssetServiceTest`, `MediaAssetControllerTest`) and
  frontend `npm.cmd run build` passed.
- Retest path: upload 5 images, wait until all are `READY`, open `Review batch`, select the recent
  batch group, edit two titles/tags, confirm, reload Media Library, and verify the fully curated batch
  is marked curated in the batch list / no longer drives the review strip.
- 2026-06-03 update: Prompt-based AI Collection Builder added as a human-confirmed alternative
  to automatic collection generation. The old Media Library button no longer auto-creates collections.
- Next development slice: Phase 3 NL/hybrid media search (`CAPSTONE2_PHASE3_PLAN.md`) unless
  duplicate/quality review affordances are prioritized first.

## 3. Done Criteria

- 200-asset D5 run finishes without HikariCP connection exhaustion.
- duplicate precision/recall measured against D1.
- suggested collections are visible in Collections with an AI-suggested badge.
- contributors can review a batch and mark assets curated.
- focused backend tests, frontend build, and docs are updated with each slice.

## 4. Notes

- Quality and duplicate detection must be deterministic first; Claude is optional for best-of-burst ranking only.
- Embeddings are for visual similarity/grouping, not duplicate truth.
- No external API call should hold a database connection.

## 5. Known cleanups / pre-merge guards

- 🔴 **`V29__seed_cit_u_validator_contributor.sql` is DEV-ONLY — remove or gate before merging to `main`.**
  It seeds known-password accounts via Flyway, which runs on every environment. Safe on the dev
  Supabase branch; a prod risk the moment it merges. Keep it for dev login for now; delete it (or
  move it to a dev-only Flyway location/profile) as part of the merge-to-main checklist.
- ✅ **Auto-grouping upgraded to the scoped UC-4.2 design** (image-embedding clustering + Claude
  naming, category fallback). `MediaAlbumService.clusterAssets`/`similarityClusters` +
  `ClaudeVisionClient.suggestAlbumName`. Remaining: tune the 0.82 cosine threshold against a
  labelled grouping set for the ≥70%-useful (D3) target.
- 🟡 **Duplicate threshold (Hamming ≤6) — PRELIMINARY tuning done; needs a real dataset before changing.**
  `DuplicateCandidateMiningD1Test` mined **266 real candidate pairs from 83 dev-library images**; 15 were
  AI-inspected (`docs/eval/D1_duplicate_pairs.csv`) and `DuplicateThresholdTuningD1Test` produced the curve
  (`docs/eval/D1_tuning_results.md`): **best F1 = 1.0 at Hamming ≤1; precision collapses to 0.20 at the
  current ≤6** because same-composition studio shots (different cookies) sit at Hamming 2–5 — a genuine
  pHash precision limit. ⚠️ **Do NOT change `DUPLICATE_HAMMING_THRESHOLD` off this**: n = 1 true duplicate
  and the dev library is product graphics + stock, not a real event dump. Rebuild D1 from a real ≥50-pair
  event set (multiple true dupes + real distinct_same_event negatives), re-run, then set the threshold.
  Labels in D1 are AI-inspected — verify before defense.
- ✅ **Blur analysis is resolution-capped** (`MAX_BLUR_ANALYSIS_DIM = 512`) so the Laplacian pass
  cannot OOM on large photos during the 200-asset dump.

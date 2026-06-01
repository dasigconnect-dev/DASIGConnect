# Capstone 2 — Phase 2 Implementation Plan

> **Phase 2 (scope §7):** AI auto-grouping + quality/duplicate filtering (UC-4.2/4.4) + curation (UC-4.3).
> **Builds on:** Phase 1 folders/albums + bounded ingestion queue.
> **Current state:** Phase 1 is user-tested for upload flow; Phase 2 starts with batch metadata and schema foundation before adding analyzers and review UI.

---

## 1. Goal

Turn a bulk upload into a managed import batch:

1. Record every multi-file upload under a `media_import_batches` row.
2. Store curation and quality signals on each asset.
3. Compute deterministic quality and duplicate indicators during ingestion.
4. Suggest albums from import-batch clusters, using the Phase 1 `media_albums.source = 'ai_suggested'` path.
5. Let a contributor review the batch and confirm/edit metadata.

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
   - exposed as the Media Library action **Generate AI Collections**
   - *Note:* threshold 0.82 is a starting value — tune against a labelled grouping set toward
     the ≥70%-useful (D3) target

5. ✅ **Batch curation UI (UC-4.3) — first pass**
   - show a review modal for the latest completed import batch
   - display thumbnail, AI category/tags/description, blur score, duplicate warning, and processing state
   - support refresh while AI metadata is still processing
   - support confirm-all and set `curated_at` on the batch assets
   - edit-selected title/tag workflow remains the next refinement

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

# Handoff - 2026-05-28 (Session 9)

## What Was Done This Session

- **AI Media Library upgrade docs were reviewed and corrected.**
  - Updated `docs/md/ai-media-library-upgrade.md` with the main pre-implementation fixes:
    - removed duplicate artwork examples,
    - added `ai_tags` to the Claude JSON shape,
    - clarified `ai_caption` / `ai_tags` storage,
    - fixed `embedding_type` vs `type`,
    - clarified Voyage image vs text embedding usage,
    - added `ma.status = 'READY'` and `deleted_at IS NULL` filtering,
    - added unique `(asset_id, embedding_type)` index and upsert guidance,
    - expanded soft-delete/hard-delete cleanup,
    - added migration safety notes.

- **AI Media Library backend implementation started from the MD basis.**
  - Added dual embedding support with `media_asset_embeddings`.
  - Added media asset status lifecycle: `PROCESSING`, `READY`, `FAILED`, `DELETED`.
  - Claude classification now returns richer structured media metadata including `ai_tags`.
  - Voyage integration separates multimodal image embedding from text/semantic embedding.
  - AI suggestion search now excludes deleted/non-ready media assets.
  - Soft delete cleanup now clears embeddings/tags and marks media deleted.
  - Added Flyway migration `V25__media_asset_dual_embeddings.sql`.

- **Submit Content UX updates.**
  - Submission wizard step order changed to:
    1. Post Details
    2. Media Assets
    3. Preferred Schedule
  - New and loaded drafts open on Post Details first so title/caption/tags exist before AI media suggestion.
  - Back navigation no longer asks to save when the draft is already saved.
  - Back navigation still prompts when there are unsaved draft changes.
  - Local file input can reselect the same batch after selection.
  - Media Repository upload modal now supports selecting/dropping multiple local assets and uploads them sequentially with aggregate progress.

- **Duplicate Facebook publish bug diagnosed and fixed.**
  - Supabase showed duplicate successful publication attempts:
    - `2c405cb4-1d0d-4e52-a7be-8a9c0fd92a73`
    - `d3ab4d3b-af1a-420c-b76c-a6f71802bb7a`
  - Root cause: `PublishingSchedulerJob` could pick the same `scheduled` submission in overlapping scheduler runs or app instances before the first run updated it to `published`.
  - Fix: added an atomic publish-claim step before Facebook API calls:
    - `scheduled -> publishing`
    - `direct_post_scheduled -> direct_post_publishing`
  - Only the process that successfully updates the DB row can publish. Later overlapping runs skip the row.
  - Immediate direct posts now use the same claim flow to avoid racing the scheduler.
  - Added Flyway migration `V26__submission_publishing_claim_status.sql`.

## Files Changed This Session

**Backend:**
- `backend/src/main/java/com/dasigconnect/backend/model/entity/SubmissionStatus.java`
- `backend/src/main/java/com/dasigconnect/backend/repository/SubmissionRepository.java`
- `backend/src/main/java/com/dasigconnect/backend/schedule/PublishingSchedulerJob.java`
- `backend/src/main/java/com/dasigconnect/backend/service/PublishingQueryService.java`
- `backend/src/main/java/com/dasigconnect/backend/service/FacebookPublisherService.java`
- `backend/src/main/java/com/dasigconnect/backend/service/DirectPostService.java`
- `backend/src/main/resources/db/migration/V26__submission_publishing_claim_status.sql`
- `backend/src/test/java/com/dasigconnect/backend/schedule/PublishingSchedulerJobTest.java`
- `backend/src/test/java/com/dasigconnect/backend/service/PublishingQueryServiceTest.java`

**AI Media backend work from this session:**
- `backend/src/main/resources/db/migration/V25__media_asset_dual_embeddings.sql`
- `backend/src/main/java/com/dasigconnect/backend/model/entity/MediaAssetEmbedding.java`
- `backend/src/main/java/com/dasigconnect/backend/model/entity/MediaAssetEmbeddingType.java`
- `backend/src/main/java/com/dasigconnect/backend/model/entity/MediaAssetStatus.java`
- `backend/src/main/java/com/dasigconnect/backend/repository/MediaAssetEmbeddingRepository.java`
- plus updates to Claude/Voyage clients, media asset services, recommendation service, retention service, and related tests.

**Frontend:**
- `frontend/src/api/submissionApi.ts`
- `frontend/src/components/media/UploadMediaTab.tsx`
- `frontend/src/features/media-repository/components/UploadModal.tsx`
- `frontend/src/features/submission/SubmissionScreen.tsx`

**Docs:**
- `docs/md/ai-media-library-upgrade.md`
- `HANDOFF.md`
- `CLAUDE.md`
- `TASKS.md`

## Verification

- Backend full test suite:
  - `.\mvnw.cmd test`
  - Result: **273 tests, 0 failures, 0 errors**
- Focused publish-claim tests:
  - `.\mvnw.cmd test "-Dtest=PublishingQueryServiceTest,PublishingSchedulerJobTest"`
  - Result: **4 tests, 0 failures**
- Frontend production build:
  - `npm.cmd run build`
  - Result: **passed**
  - Existing Vite chunk-size warning remains.

## Important Notes

- Existing duplicate Facebook posts already created for the two affected submissions must be cleaned manually from Facebook if needed. The backend fix prevents future duplicate publishes but cannot remove already-created posts.
- Apply Flyway migrations on backend restart before browser E2E:
  - `V25__media_asset_dual_embeddings.sql`
  - `V26__submission_publishing_claim_status.sql`
- During AI media implementation, do **not** delete the old `media_assets.embedding` column immediately. The migration strategy keeps old and new embedding paths compatible while search is moved to `media_asset_embeddings`.
- For AI features in Submit Content, saving a draft first is intentional because the backend needs a stable `submissionId` and saved media/context before running AI caption and AI media suggestion flows.

## What's Next

1. Restart backend so Flyway applies V25 and V26.
2. Run browser E2E for:
   - Submit Content step order and draft-exit prompt behavior.
   - Multi-file local upload from Submit Content and Media Repository.
   - AI Caption after saving draft.
   - AI Media Suggestions after title/caption/tags exist and draft is saved.
   - Scheduled Facebook publishing once, confirming only one `publication_attempts.result = 'success'` row per submission.
3. In Supabase, optionally audit current duplicates:
   ```sql
   SELECT submission_id, result, COUNT(*)
   FROM publication_attempts
   GROUP BY submission_id, result
   HAVING COUNT(*) > 1;
   ```

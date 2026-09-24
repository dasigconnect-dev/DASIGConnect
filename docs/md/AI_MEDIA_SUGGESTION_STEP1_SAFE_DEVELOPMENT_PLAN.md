# AI Media Suggestions Step 1 Safe Development Plan

## 1. Document Status

- **Purpose:** Upgrade the submission media step so every image added through Upload Files or My Library is persisted and processed progressively, and AI Suggestions can activate from visual context without requiring the user to move to another step and return.
- **Primary constraint:** Preserve all established submission, media-library, review, authorization, caching, publishing, and UI behavior.
- **Scope:** Submission composer media attachments, background image intelligence, multi-image context aggregation, visual recommendation activation, freshness controls, scale controls, and observability.
- **Not in scope:** Redesigning the composer, changing submission status transitions, changing review permissions, replacing Claude or Voyage, or enabling Facebook-history ranking before its separate import foundation is ready.
- **Implementation rule:** Deliver this work as small, independently releasable phases. Every phase must retain a feature-flagged path back to the existing recommendation behavior.

### Implementation Progress

- **Visual activation slice:** Implemented on `feature/ai-media-step1-visual-suggestions`.
- Every persisted selected image participates through its stored IMAGE and SEMANTIC embeddings.
- Visual-only retrieval does not call the Voyage text-embedding endpoint.
- Text-only recommendation behavior remains available and unchanged at the API boundary.
- Hybrid requests blend selected-media semantic context with the current text query.
- Fresh uploads receive bounded visual-only retries while asynchronous embeddings become ready.
- **Phase 1 - Safe Media Autosave:** Implemented on `feature/ai-media-phase1-autosave`.
- Saved drafts now serialize narrow upload, library-attach, detach, and final-order mutations without running a complete draft save per file.
- Device uploads reconcile temporary picker items to server assets while preserving order, captions, watermark flags, and the established UI.
- Removal during an in-flight upload either prevents registration or compensates by detaching the registered asset.
- Conflict responses for already-attached library assets are reconciled through the server snapshot instead of duplicating the attachment.
- Ambiguous upload failures re-read the draft and match newly registered assets by original filename and size before allowing a retry, preventing duplicate uploads after lost responses.
- The existing full `saveDraft()` path remains the compatibility fallback after automatic media persistence fails.
- Composer end-to-end coverage verifies upload, library attachment, detach, in-flight removal, transient failure fallback, ordinary autosave, submission, and revision behavior.
- **Phase 2 - Durable Progressive Processing:** Implemented on `feature/ai-media-phase2-durable-processing`.
- Image processing now uses versioned, idempotent database jobs with short claim leases, bounded batches, exponential retry delays, and a terminal dead-letter state.
- Upload and draft-submission transitions only enqueue work; Claude and Voyage calls run later without holding the originating request transaction or database connection.
- Retries inspect persisted classification, IMAGE embedding, and SEMANTIC embedding stages so successful provider work is not repeated.
- The existing reconciliation schedule now repairs missing queue work instead of directly launching unbounded asynchronous provider calls.
- Submission detail responses expose additive ready, processing, and failed media counts, and asset responses expose the completed processing version.
- Queue rows are not granted to Supabase `anon` or `authenticated` roles, and enqueueing remains scoped to the authenticated uploader or institution.
- Regression coverage verifies queue idempotency, bounded claims, dead-letter behavior, worker completion and retry, staged-media promotion, and missing-stage-only processing.
- **Phase 3 - Structured Image and Event Context:** Implemented on `feature/ai-media-phase3-structured-context`.
- Claude Vision classification now records evidence-first scene, activity, people-count, equipment, recognition, OCR, visible-date, event-hypothesis, temporal, quality, and composition signals for each image.
- A versioned, institution-scoped submission media context deterministically aggregates every ready attached image while reporting processing and failed counts as partial state.
- Context rebuild jobs are triggered after attach, detach, reorder, and media-processing completion; an order-sensitive asset hash skips unchanged rebuilds.
- Durable rerun markers prevent attachment changes from being lost when they occur while a context rebuild is already processing.
- Database-only context jobs can run without AI provider credentials, while provider-dependent jobs remain unclaimed until credentials are configured.
- Context generation remains internal and asynchronous, so uploads, draft saves, and established UI behavior are unchanged and never wait for context completion.
- Regression coverage verifies multi-image aggregation, incomplete-state handling, provider-independent context processing, bounded claims, and stable context queue versioning.
- **Phase 4 - Visual Activation:** Implemented on `feature/ai-media-phase4-visual-activation`.
- Visual-only suggestions activate from any persisted selected image and reuse all available attached IMAGE and SEMANTIC embeddings without spending text-embedding tokens.
- Selected-image embedding queries now independently require the submission institution, active READY assets, and institution-scoped candidate results.
- Visual and text vector candidates, plus metadata fallback candidates, now follow the established Media Repository rule that excludes assets private to unsubmitted drafts.
- Visual-only requests return an empty result while attachment or embedding work is pending, allowing the existing bounded frontend retry to refresh with genuine visual matches instead of settling on generic metadata.
- Attached assets remain excluded, while text and metadata fallback behavior remains available when text context exists.
- `AI_MEDIA_VISUAL_SUGGESTIONS_ENABLED` provides a deployment rollback path to the established text recommendation behavior without a code rollback or UI change.
- Regression coverage verifies all-selected-image retrieval, no text-token use for visual-only requests, pending-embedding behavior, role authorization, and feature-flag fallback.
- **Phase 5 - Hybrid Ranking and Diversity:** Implemented on `feature/ai-media-phase5-hybrid-ranking`.
- Hybrid ranking combines selected-media visual and semantic similarity with aggregated event context, draft metadata, freshness, visual quality, and sequence-complement signals while renormalizing unavailable inputs.
- Deterministic `hybrid-v1` result versioning and evidence-based match reasons make ranking behavior observable without changing the existing suggestion UI.
- Bounded maximal-marginal-relevance reranking lowers repeated or near-duplicate results while retaining the established eight-result limit.
- `AI_MEDIA_HYBRID_RANKING_ENABLED` defaults off for rollback safety, while `AI_MEDIA_HYBRID_SHADOW_ENABLED` compares legacy and hybrid top results before activation.
- Institution validation is applied before a stored submission context contributes to ranking; candidate retrieval and authorization remain unchanged.
- Regression coverage verifies context, quality, and sequence ranking; near-duplicate diversification; version output; and preservation of the legacy path while the feature flag is disabled.
- **Phase 6 - Freshness, Scale, and Backfill:** Implemented on `feature/ai-media-phase6-freshness-scale`.
- Explicitly expired assets and parseable past expiration dates are excluded from vector and fallback candidates, while unknown legacy dates remain eligible and old evergreen media receives no age penalty.
- Bounded submission-link usage statistics add a small recent-reuse penalty without changing candidate eligibility or requiring a large usage-data migration.
- `media-ai-v2` refreshes structured classification and semantic embeddings through the existing durable queue; reconciliation selects at most a configured batch and pauses when the queue reaches its configured capacity.
- Worker claims apply a per-institution batch ceiling so one large institution cannot monopolize a processing poll, while the existing lease, retry, exponential backoff, and dead-letter behavior remains intact.
- Administrators can inspect and retry dead media-processing jobs through additive system-health endpoints, with retries written to the audit log.
- Hybrid ranking now requires both the global environment flag and the institution rollout flag; the new institution setting defaults off and is changed only through an administrator endpoint.
- Regression coverage verifies expiration handling, evergreen retention, reuse penalties, versioned reclassification, bounded backfill, queue backpressure, institution-fair claim parameters, dead-letter recovery, rollout fallback, and existing worker behavior.

## 2. Verified Current Baseline

The plan is based on the current implementation on `dev`:

- `MediaAssetsPicker.tsx` combines Upload Files, My Library, AI Suggestions, and the selected-media strip.
- `UploadMediaTab.tsx` currently validates files and creates local object-URL items. It does not upload immediately.
- `SubmissionScreen.tsx#handlePickerChange` stores new device files in `form.files` and new library selections in `pendingAssetIds`.
- `SubmissionScreen.tsx#saveDraft` creates or updates the draft, attaches pending library assets, uploads local files, synchronizes order/captions/watermark flags, and refreshes caches.
- The existing 60-second autosave updates only a draft that already has an ID. It deliberately does not auto-create a new draft.
- `useAiMediaSuggestions.ts` activates only when title, caption, category, and tags provide at least ten characters of text context.
- `AIRecommendationService#suggestMedia` loads attached assets but uses only metadata from up to three of them in a text query. Candidate retrieval searches `SEMANTIC` embeddings.
- Image uploads are classified and embedded asynchronously by `AIClassificationService`.
- `media_asset_embeddings` already supports dual `IMAGE` and `SEMANTIC` embeddings with pgvector search.
- `media_assets.content_hash` already exists and should be used for duplicate-processing control.
- Suggestion access currently permits Administrators and Moderators network-wide and Contributors only for their own submission and institution. This must remain unchanged.

## 3. Problem Statement

The current activation requires text context that is normally supplied outside the media step. A user can therefore add media, discover that suggestions are unavailable, leave the step to enter text, and return to see suggestions.

The target behavior is:

```text
Add any image through Upload Files or My Library
    -> persist the attachment automatically
    -> process each previously unseen image once
    -> progressively combine all ready selected images
    -> prepare visual and event context in the background
    -> activate AI Suggestions from ready visual context
    -> enrich the same suggestions later when text context becomes available
```

No single image, including the first image, is treated as the permanent query. The selected image set is an evolving context.

## 4. Non-Negotiable Compatibility Invariants

1. Keep the existing composer layout, tabs, selected-media strip, styling, labels, loaders, error components, and responsive behavior.
2. Keep the current submission state machine and all submit-time validation.
3. Autosaving an attachment must not submit a post, reserve a schedule, publish media, or change a submission out of `DRAFT` or `NEEDS_REVISION`.
4. Do not silently create an invalid draft. The established draft-creation boundary and required institution selection for an Administrator remain in force.
5. Keep upload type and 50 MB size validation unless a separate approved requirement changes it.
6. Preserve media order, per-image captions, watermark flags, album behavior, and manual-publishing rules for mixed image/video posts.
7. Preserve My Library caching, filtering, pagination, and institution scoping.
8. Preserve Moderator restrictions in the review editor. This plan must not give Moderators device-upload capability where it is currently prohibited.
9. Never trust client-supplied institution IDs or asset ownership. Resolve scope from the authenticated user and submission on the backend.
10. Never hold a database transaction or Hikari connection while calling Claude, Voyage, storage, or another external service.
11. A failed AI operation must not prevent upload, attachment, draft saving, removal, reordering, or submission.
12. Existing API fields remain compatible. New request and response fields must be optional and additive.

## 5. Required User Behavior

### 5.1 Device Upload

After a valid draft ID exists:

1. The user selects one or more valid files.
2. Items appear immediately through the existing selected-media UI.
3. A serialized media queue uploads and attaches each file in the background.
4. The local item is reconciled with the returned asset ID without changing its visible order.
5. Classification and embeddings continue asynchronously.
6. Successfully attached images begin contributing to visual context as soon as their image embeddings are ready.

If no draft ID exists, preserve the current draft-creation rules. The media queue may wait for the existing explicit/step-transition draft save, but it must not bypass validation or create an institution-less draft.

### 5.2 My Library Selection

After selection:

1. Attach the existing asset immediately through the existing submission attachment API.
2. Treat HTTP conflict for an already-attached asset as an idempotent success after confirming current submission state.
3. Reconcile the response into the normal submission cache.
4. Reuse the asset's existing classification and embeddings. Do not call Claude or Voyage again merely because it was attached to another submission.

### 5.3 Removal and Reordering

- Removing a persisted item queues the existing detach operation.
- Removing a device item whose upload has not started cancels it locally.
- Removing an item while upload is in progress marks the operation cancelled; if the upload finishes, compensate by detaching the returned asset.
- Reordering keeps using the established media-order endpoint after all referenced items have asset IDs.
- Media mutations for one submission must be serialized to prevent attach, detach, and reorder races.

### 5.4 AI Suggestions Activation

Use these modes:

| Available context | Mode | Behavior |
|---|---|---|
| No ready image and insufficient text | `IDLE` | Preserve the current guidance state |
| At least one ready attached image | `VISUAL` | Suggest from all ready selected-image signals |
| Sufficient text but no ready image | `SEMANTIC` | Preserve the current text recommendation path |
| Ready images and sufficient text | `HYBRID` | Combine visual, semantic, metadata, freshness, and later performance signals |
| Some images still processing | Current best mode | Use ready images and refresh the prepared result when context version advances |

One failed or unsupported image must not block recommendations from the remaining ready images.

## 6. Safe Frontend Architecture

Introduce a focused hook, tentatively `useSubmissionMediaAutosave`, owned by `SubmissionScreen` rather than individual tabs. This preserves one source of truth for Upload Files, My Library, selected order, removal, and caches.

Responsibilities:

- Accept the same `SubmissionMediaItem` changes emitted by `MediaAssetsPicker`.
- Require a valid editable submission ID before server mutation.
- Serialize media mutations per submission.
- Track local operation states by stable `clientId`: `queued`, `uploading`, `attaching`, `saved`, `failed`, or `cancelled`.
- Reconcile temporary upload items to returned asset IDs without duplicating cards.
- Use an operation ID/idempotency key so React rerenders, retries, and Strict Mode cannot repeat work.
- Cancel obsolete requests with `AbortController` where the API supports cancellation.
- Update React Query caches using the server response, then invalidate only the affected detail/list keys.
- Leave the existing 60-second form autosave responsible for non-media form data.

Do not trigger one complete `saveDraft()` per file. That would repeatedly update unrelated fields, reorder media, and create race conditions. Media attachment autosave must use the existing narrow attach/upload/detach APIs.

Update `useAiMediaSuggestions` so its request key includes:

```text
submissionId
attached ready image IDs or mediaContextVersion
normalized text context
rankingVersion
```

The hook must retain request cancellation and stale-response protection. It should not clear a still-valid previous result during a background refresh unless the submission or institution changes.

## 7. Background Processing Architecture

### 7.1 Per-Asset Processing

Continue the established process-once pipeline:

```text
REGISTERED/STAGED
    -> CLASSIFYING
    -> IMAGE EMBEDDING
    -> SEMANTIC EMBEDDING
    -> READY
```

- Use `content_hash`, processing status, and model/version metadata to prevent duplicate classification.
- An existing My Library asset reuses stored intelligence.
- A retry resumes only the missing stage when possible. Embedding reconciliation must not unnecessarily rerun Claude classification.
- Keep external calls outside database transactions.

Extend the structured visual analysis conservatively with:

- scene and setting;
- visible objects;
- observable activities;
- approximate people-count range;
- device/equipment signals;
- awards/certificates/trophies;
- stage, audience, classroom, laboratory, outdoor, or exhibition signals;
- OCR text and visible dates;
- probable event types with confidence;
- temporal classification and possible expiration;
- visual quality and composition signals.

The model must describe observable evidence first. Event types such as hackathon, workshop, startup competition, awarding, or seminar are hypotheses, not facts.

### 7.2 Submission-Level Context

Create a versioned aggregate derived from all currently attached images that are ready. Do not send every image back to Claude whenever selection changes.

Recommended stored shape:

```text
submission_id
institution_id
context_version
asset_set_hash
ready_asset_count
processing_asset_count
failed_asset_count
observed_scenes JSONB
observed_objects JSONB
observed_activities JSONB
event_hypotheses JSONB
temporal_signals JSONB
context_text
status
model_version
generated_at
```

Aggregation should normally use stored per-asset metadata. A multi-image Claude call is optional, bounded, and allowed only when deterministic aggregation is insufficient. Cache it by `asset_set_hash` and model version.

### 7.3 Reliable Work Queue

Do not create an unbounded `@Async` task for every file. Add a database-backed job table or equivalent durable queue with:

- unique job key for asset/context version;
- job type and status;
- bounded attempt count;
- next-attempt timestamp;
- short claim lease;
- last sanitized error;
- creation and completion timestamps.

Workers claim small batches in short transactions, release the connection, call external services, then open a new short transaction to persist results. Concurrency must respect the five-connection Hikari limit. Start with one or two AI workers and configure the number through environment settings.

## 8. Visual and Multi-Image Retrieval

### 8.1 Candidate Generation

Use existing stored `IMAGE` embeddings directly. Comparing an attached asset vector with repository vectors does not require a new Voyage call.

For a small selected set:

1. Retrieve nearest neighbors for each ready selected image.
2. Retrieve candidates for an aggregate/representative visual context.
3. Union and deduplicate candidate IDs.
4. Exclude attached, deleted, archived, expired, non-ready, and unauthorized assets before reranking.

For a large selected set, cluster embeddings and use at most three to five representative images. This prevents query count from growing linearly with every selected photo while still representing the set.

All pgvector queries must:

- include `institution_id` explicitly;
- use `embedding_type = IMAGE` for visual retrieval;
- filter candidate status before nearest-neighbor ordering;
- apply a database `LIMIT`;
- use the existing HNSW cosine index or a verified equivalent;
- return projections rather than loading the whole library into Java.

### 8.2 Reranking

Initial visual mode:

```text
score =
  0.40 * aggregate visual relevance
+ 0.25 * best selected-image similarity
+ 0.15 * event-context compatibility
+ 0.10 * metadata/tag compatibility
+ 0.05 * freshness
+ 0.05 * quality
```

Initial hybrid mode:

```text
score =
  0.30 * visual relevance
+ 0.25 * text semantic relevance
+ 0.15 * event-context compatibility
+ 0.10 * metadata/tag compatibility
+ 0.10 * freshness
+ 0.05 * quality
+ 0.05 * sequence complementarity
```

Weights must be configuration/version data, not scattered constants. Missing signals are redistributed proportionally. Historical Facebook performance is not included until imported metrics are normalized and validated.

After scoring, apply Maximal Marginal Relevance or an equivalent deterministic pass so the returned eight assets are useful and not near-duplicates.

## 9. Freshness and Outdated-Media Controls

Old does not always mean invalid. A logo, campus scene, or generic event photo can remain useful, while a dated registration poster can become invalid quickly.

Add or derive:

```text
temporal_type: EVERGREEN | EVENT_SPECIFIC | ANNOUNCEMENT | POSTER | UNKNOWN
captured_at
event_date
valid_from
valid_until
rights_expire_at
detected_dates
last_used_at
usage_count
```

Apply hard exclusions before ranking:

- soft-deleted or archived asset;
- processing state other than `READY`;
- expired rights;
- explicit `valid_until` in the past;
- visible year/date that conflicts with a clearly dated current submission;
- asset already attached to the submission.

Apply soft penalties for age, recent repeated use, and high usage count. Use media-type-specific decay rather than one global age rule. Evergreen branding receives no automatic age penalty; announcements and posters receive aggressive decay.

Temporal metadata backfill must be gradual and feature-flagged. Unknown legacy values must remain eligible under the old behavior until confidently classified, preventing a migration from unexpectedly emptying the library.

## 10. API Evolution

Keep the current endpoint:

```http
POST /api/v1/ai/submissions/{submissionId}/suggest-media
```

The backend must load attached assets from the submission. It must not rely on client-supplied asset IDs for authorization or source of truth.

Optional additive request fields:

```json
{
  "eventTitle": "...",
  "caption": "...",
  "category": "...",
  "tags": ["..."],
  "mediaContextVersion": 4
}
```

Optional additive response metadata may include `recommendationRunId`, `mode`, `rankingVersion`, `contextVersion`, and processing counts. Existing result fields and status code remain compatible.

Add a narrow status endpoint only if the current submission detail cannot provide the required processing counts without expensive joins:

```http
GET /api/v1/ai/submissions/{submissionId}/media-context
```

It must use the same authorization service as `suggest-media` and reveal no cross-institution data.

## 11. Database Migration Rules

- Allocate the next unused Flyway version at implementation time; do not assume a migration number from this document.
- Never modify existing migrations, including the dual-embedding and content-hash migrations.
- Add indexes and institution isolation policies in the same migration as new tables.
- Make new columns nullable or safely defaulted during rollout.
- Backfill in bounded batches after deployment, not in one long migration transaction.
- Do not rebuild all embeddings during application startup.
- Store model/ranking/context versions so old and new results can coexist during rollout.

Suggested new tables are `submission_media_context` and `ai_media_processing_jobs`. Freshness fields may be added to `media_assets` only after verifying they do not duplicate existing provenance fields.

## 12. Role and Tenant Rules

| Capability | Contributor | Moderator | Administrator |
|---|---|---|---|
| Add device media in contributor composer | Existing behavior | No new permission | Existing composer behavior |
| Select existing library media | Own institution | Existing network-wide review behavior | Existing network-wide behavior |
| Request suggestions | Own editable submission | Existing authorized submission | Existing authorized submission |
| View processing details | Own editable submission | Existing authorized submission | Existing authorized submission |
| Reprocess/backfill assets | No | No | Explicit administrative operation only |

For Contributors, enforce both submission ownership and institution membership. For network-wide roles, derive the recommendation institution from the submission, never from a request parameter.

## 13. Scalability and Cost Controls

1. Classify and embed each unique asset once per model version.
2. Reuse stored image vectors for visual retrieval; pgvector search consumes database compute, not Claude/Voyage tokens.
3. Cache prepared suggestions by institution, submission, context version, normalized text hash, and ranking version.
4. Invalidate only when attached media, relevant text, eligibility, or ranking version changes.
5. Batch reconciliation and backfill work.
6. Limit candidate retrieval in SQL before Java reranking.
7. Use representative image clusters for large selections.
8. Add per-institution upload and AI-processing quotas without changing normal MVP limits initially.
9. Apply bounded retries with exponential backoff and a dead-letter state.
10. Track provider calls and cost by operation without logging prompts containing sensitive content.
11. Generate and serve thumbnails for result grids; do not download full-resolution files for ranking UI.
12. Keep the existing recommendation algorithm available when new context, providers, or workers are unavailable.

## 14. Development Phases

### Phase 0 - Baseline, Contracts, and Feature Flags

- Record current upload, attach, detach, reorder, autosave, and suggestion behavior.
- Add characterization tests before changing behavior.
- Define feature flags for media autosave, visual activation, context aggregation, freshness filtering, and hybrid ranking.
- Define latency, failure, empty-result, provider-call, and cache-hit metrics.
- No user-visible behavior change.

**Exit gate:** Existing tests pass and baseline metrics can distinguish old and new paths.

### Phase 1 - Safe Media Autosave

- Add the serialized frontend media-mutation queue.
- Immediately attach My Library selections after a valid draft exists.
- Immediately upload device files after a valid draft exists.
- Reconcile temporary items to server assets while preserving order and metadata.
- Implement cancellation/compensation for removal races.
- Keep full `saveDraft()` as a compatibility fallback.

**Exit gate:** Rapid add/remove/reorder, network failure, retry, reload, and duplicate-selection tests pass without duplicate attachments or data loss.

### Phase 2 - Durable Progressive Processing

- Add idempotent durable processing jobs and bounded workers.
- Record model and processing versions.
- Improve retry so only missing stages rerun.
- Expose aggregate processing counts through an additive contract.
- Do not change recommendation ranking yet.

**Exit gate:** Upload succeeds during provider failure; reconciliation later reaches `READY`; duplicate jobs do not duplicate provider work.

### Phase 3 - Structured Image and Event Context

- Extend classification output with observable scene/activity/event/temporal signals.
- Add the versioned submission media context.
- Rebuild it when an attachment becomes ready, is attached, detached, or materially reclassified.
- Aggregate stored metadata first; use optional multi-image analysis only when justified.
- Keep context internal initially to avoid unapproved UI changes.

**Exit gate:** Context includes all ready selected images, handles contradictory evidence, and never blocks established media operations.

### Phase 4 - Visual Activation

- Add institution-scoped IMAGE-embedding candidate queries.
- Activate suggestions when any attached image is ready.
- Use all ready images through candidate union and representative clustering.
- Exclude selected assets and preserve semantic fallback.
- Update frontend request keys and background refresh behavior.

**Exit gate:** A user can add images and receive relevant suggestions without entering Step 2 text or navigating away and back.

### Phase 5 - Hybrid Ranking and Diversity

- Combine visual, semantic, event-context, metadata, freshness, quality, and sequence signals.
- Add ranking versioning and deterministic match reasons.
- Apply diversity reranking.
- Run the new algorithm in shadow mode and compare it with the current result set before enabling it.

**Exit gate:** Relevance improves without unacceptable latency, empty-result regression, or repeated near-duplicate results.

### Phase 6 - Freshness, Scale, and Backfill

- Add temporal eligibility and usage features.
- Backfill legacy assets in bounded batches.
- Add quotas, worker backpressure, dead-letter operations, and administrative recovery.
- Load-test large institution libraries and concurrent uploads.
- Enable per institution only after metrics meet thresholds.

**Exit gate:** Large-volume tests remain within database, storage, provider, and latency budgets; old but evergreen assets remain discoverable.

### Phase 7 - Historical Performance Integration

- Begin only after the separate Facebook-history import plan provides reliable institution-scoped normalized metrics.
- Add performance as a capped reranking signal, never a candidate-eligibility substitute.
- Use engagement rates and Bayesian shrinkage rather than raw counts.
- Shadow-test for popularity feedback loops and category bias.

**Exit gate:** Performance weighting improves retained recommendations without reducing relevance or repeatedly promoting the same assets.

## 15. Expected Files

Exact names may be adjusted to existing package conventions, but expected changes include:

### Frontend

- `frontend/src/features/submission/SubmissionScreen.tsx`
- `frontend/src/components/media/MediaAssetsPicker.tsx`
- `frontend/src/components/media/AiSuggestedMediaTab.tsx`
- `frontend/src/hooks/useAiMediaSuggestions.ts`
- `frontend/src/api/aiApi.ts`
- `frontend/src/api/submissionApi.ts`
- New focused media-autosave hook and tests

`UploadMediaTab.tsx`, `MediaLibraryTab.tsx`, and existing CSS should change only if a functional integration requires it. No visual restyling is approved by this plan.

### Backend

- `AIRecommendationController.java`
- `AIRecommendationService.java`
- `AIClassificationService.java`
- `MediaAssetService.java`
- `SubmissionService.java` only where idempotent attachment behavior requires it
- `MediaAssetEmbeddingRepository.java`
- `SubmissionMediaAssetRepository.java`
- AI request/response DTOs
- New media-context/job entities, repositories, services, and scheduled worker
- New additive Flyway migration(s)

### Tests

- `AIRecommendationServiceTest.java`
- `AIClassificationServiceTest.java`
- `MediaAssetServiceTest.java`
- Submission controller/service attachment tests
- Frontend tests for queue serialization, cancellation, retries, activation modes, and stale responses

## 16. Required Test Matrix

### Established Features

- Save existing draft with and without media.
- Submit a valid draft.
- Edit `NEEDS_REVISION` media.
- Upload supported image and video types.
- Reject oversized and unsupported files.
- Select, remove, and reorder My Library assets.
- Preserve media captions and watermark flags.
- Preserve mixed image/video manual-publishing behavior.
- Preserve My Library cache and tab state.
- Preserve review-editor media restrictions.

### Autosave and Concurrency

- Add one and many device images.
- Select one and many library images.
- Mix device and library images.
- Remove before upload, during upload, and after attachment.
- Reorder while another upload is pending.
- Retry timeout and transient server failure.
- Reload after partial completion.
- Double click, Strict Mode, and duplicate response delivery.
- Two browser tabs editing the same draft.
- Institution switch during pending media operations.

### AI and Processing

- One ready image activates visual mode.
- All ready selected images influence candidates.
- Processing images do not block ready images.
- Failed classification or embedding does not block upload or draft saving.
- Text-only path retains current behavior.
- Hybrid mode activates when both signals exist.
- Attached assets never appear as suggestions.
- Expired media is excluded; evergreen old media remains eligible.
- Provider timeout falls back safely.
- An old response cannot overwrite a newer context version.

### Authorization

- Contributor cannot attach or retrieve another institution's asset.
- Contributor cannot request suggestions for another user's submission.
- Moderator and Administrator retain only their established network-wide behavior.
- Every new table/query/cache/job is institution-scoped.
- A forged asset ID, institution ID, or context version cannot bypass scope.

### Scale

- Concurrent multi-file uploads with bounded workers.
- Large institution library using indexed, limited pgvector queries.
- Large selected-media set using representative clustering.
- Backfill interrupted and resumed safely.
- Provider rate-limit response and retry exhaustion.
- Hikari usage remains below the configured five-connection limit.

## 17. Observability and Acceptance Metrics

Track at minimum:

- attachment autosave success/failure and p95 duration;
- duplicate operations suppressed;
- queued, processing, failed, and dead-letter jobs;
- Claude and Voyage calls per unique uploaded image;
- processing time from attachment to recommendation-ready;
- visual, semantic, and hybrid suggestion counts;
- suggestion request p50/p95 latency;
- cache-hit and fallback rates;
- empty-result and provider-failure rates;
- shown, accepted, removed-before-submit, and retained-at-submit recommendations;
- cross-institution result count, which must always be zero.

Initial release gates should be set from Phase 0 measurements rather than invented values. Any phase that materially worsens established upload success, draft reliability, authorization, or suggestion latency remains disabled.

## 18. Rollout and Rollback

1. Deploy additive schema and dormant code first.
2. Enable logging/shadow processing for test institutions.
3. Enable media autosave independently from visual ranking.
4. Enable visual activation independently from freshness and historical performance.
5. Compare shadow and current recommendations before changing the default.
6. Expand by institution only after acceptance tests and metrics pass.

Rollback must require only disabling feature flags. Do not make rollback depend on dropping columns, deleting embeddings, or reverting irreversible migrations. Data written by the new path must remain readable or safely ignorable by the old path.

## 19. Definition of Done

This development is complete only when:

- every image added through Upload Files or My Library is safely persisted after a valid draft exists;
- media processing starts without waiting for the AI Suggestions tab/button;
- all ready selected images contribute to a versioned context;
- visual suggestions work without Step 2 text;
- later text enriches the same flow without requiring back-and-forth navigation;
- duplicate processing and unnecessary provider calls are prevented;
- outdated or expired media is controlled without excluding useful evergreen assets;
- failures degrade to established upload and recommendation behavior;
- institution isolation and current role permissions are proven by tests;
- existing UI and submission behavior remain intact;
- load, rollback, migration, and observability checks pass.

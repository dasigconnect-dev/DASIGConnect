# Media Library Voyage Retrieval Implementation Plan

## 1. Purpose

This is the authoritative implementation plan for the next DASIGConnect media
AI development. It covers only:

1. Efficient Media Library image uploads.
2. Voyage-generated image and semantic embeddings.
3. Meaning-based search through the existing Media Library search bar.
4. More accurate Media Library suggestions inside submission creation.
5. Backfill, evaluation, monitoring, and safe rollout.

This plan does not include Facebook historical import. That development remains
deferred until after consultation and authorized Page access.

## 2. Non-Negotiable Constraints

- Preserve the current UI, layout, controls, loaders, text, and responsive behavior.
- Prefer data-processing and request-lifecycle changes over visual changes.
- Preserve direct browser-to-Cloudflare-R2 uploads through presigned URLs.
- Preserve Spring Boot, PostgreSQL, pgvector, the durable media job queue, and
  institution/role authorization.
- Never expose one institution's media through another institution's search or
  suggestions.
- Do not block an upload request while waiting for Voyage.
- Do not make Claude Vision a prerequisite for an asset to become searchable or
  recommendable.
- Do not mix vectors from incompatible models in one similarity query.
- Do not replace a committed Flyway migration; create a new migration when a
  schema change is required.
- Every phase must preserve existing features and include regression tests.

## 3. Verified Current Architecture

### 3.1 Media Library upload

The current upload flow is:

```text
React Media Repository
-> calculate SHA-256 content hash
-> request a presigned upload URL
-> upload the file directly to Cloudflare R2
-> register the media asset in Spring Boot
-> save mandatory manual tags and album information
-> enqueue durable media processing
```

Relevant implementation:

- `frontend/src/features/media-repository/MediaRepositoryScreen.tsx`
- `frontend/src/features/media-repository/components/UploadModal.tsx`
- `frontend/src/api/mediaApi.ts`
- `backend/src/main/java/com/dasigconnect/backend/service/MediaAssetService.java`
- `backend/src/main/java/com/dasigconnect/backend/service/MediaStorageService.java`

The direct-to-R2 architecture and SHA-256 duplicate check must remain.

### 3.2 Current AI processing

The current full image path is:

```text
Claude classification
-> Voyage image embedding
-> Voyage semantic text embedding
-> asset READY
```

This creates an unnecessary critical dependency on Claude. The target pipeline
uses Voyage for retrieval embeddings and keeps Claude outside the critical
upload/search/suggestion path.

### 3.3 Current Media Library search

- The standard search field performs keyword matching against fields such as
  filename, title, asset code, uploader, and tags.
- Meaning-based search exists as a separate semantic toggle and explicit search.
- Semantic search creates a Voyage query embedding and uses pgvector, then
  appends bounded keyword fallback results.

The target keeps the current search control but unifies keyword and semantic
retrieval behind it after evaluation.

### 3.4 Current submission suggestions

- Selected submission images are autosaved and attached before recommendation.
- All persisted selected image IDs can provide visual context.
- Candidate retrieval is institution-scoped and excludes inaccessible,
  draft-only, attached, deleted, and expired assets.
- A missing selected-image embedding causes bounded frontend polling.
- A visual-only request deliberately returns no generic metadata fallback.

## 4. DASIG Central Visayas Evidence

The 64 screenshots in `docs/img` contain approximately 61 individual post
captures and three Page/gallery overview captures. Their approximate primary
formats are:

| Primary format | Approximate count |
| --- | ---: |
| Event documentation and photo collages | 36 |
| Promotional posters, calls, and infographics | 14 |
| Online or hybrid meeting captures | 6 |
| Birthday and holiday greetings | 5 |

Observed subjects include startup bootcamps, accelerator programs, innovation
training, pitching, awards, technology commercialization, intellectual
property, mentoring, consortium planning, institutional visits, partnerships,
speakers, workshops, participant groups, and registration calls.

### Retrieval consequences

- Many event photos are visually similar: people, chairs, screens, laptops, and
  institutional logos. Text metadata is required to distinguish the event.
- Posters are text-heavy and time-sensitive. Expired calls must not be
  recommended for reuse.
- Repeated logos, colors, and templates must not overpower event relevance.
- Facebook collages combine several visual roles. Original attachments must be
  embedded individually when historical import is eventually implemented.
- A useful sequence may contain a cover, activity, speaker, audience, pitch,
  awarding, and closing group photo.

The screenshots are taxonomy evidence, not the production embedding corpus.
Original Media Library files must be used for retrieval evaluation.

## 5. Target Embedding Architecture

The first safe implementation retains two embedding signals, both generated by
Voyage and stored separately.

### 5.1 IMAGE embedding

Purpose: visual and scene similarity.

```text
Original image bytes
-> voyage-multimodal-3.5
-> input_type=document
-> 1024-dimensional IMAGE vector
```

This signal represents people, group activities, presentation scenes, laptops,
awards, rooms, posters, composition, and other visible content.

Regenerate it only when:

- the underlying file changes;
- the image embedding model changes; or
- the image processing version changes.

### 5.2 SEMANTIC embedding

Purpose: topic and meaning retrieval.

Build the semantic input only from trusted saved metadata:

- display title;
- cleaned filename;
- album name;
- mandatory manual tags;
- content format when available;
- temporal/reusability classification when available.

```text
Trusted metadata text
-> Voyage text embedding model
-> input_type=document
-> 1024-dimensional SEMANTIC vector
```

Do not include uploader email, asset code, storage path, or unrelated technical
metadata. Regenerate this vector when relevant metadata changes.

### 5.3 Query embeddings

- A text search uses `input_type=query` with the same text model used for stored
  SEMANTIC vectors.
- Image retrieval initially reuses the established IMAGE-vector comparison.
- A later experiment may generate multimodal query vectors, but it must not be
  adopted until measured against the established baseline.

### 5.4 Model compatibility

Store model name, embedding type, processing version, and source-input hash.
Only compare vectors produced in compatible embedding spaces.

## 6. Metadata Vocabulary

The existing album and tag controls remain the source of trusted context. The
following vocabulary should guide tags and future validation without requiring
a UI redesign.

### Format

- `event-photo`
- `event-collage`
- `promotional-poster`
- `instructional-infographic`
- `online-meeting`
- `greeting-card`

### Visual role

- `group-photo`
- `speaker`
- `audience`
- `workshop`
- `presentation-screen`
- `pitching`
- `awarding`
- `mentoring`
- `classroom`
- `site-visit`
- `panel-discussion`
- `partnership-photo`

### Topic

- `startup`
- `innovation`
- `entrepreneurship`
- `commercialization`
- `incubation`
- `bootcamp`
- `mentoring`
- `intellectual-property`
- `investment`
- `technology-transfer`
- `consortium`
- `ecosystem-building`

### Lifecycle

- `reusable`
- `time-bound`
- `expired`

Do not infer sensitive traits or identify people from appearance. Names may be
retained only when supplied by trusted official metadata and required by the
feature.

## 7. Phased Development

## Phase 0 - Stabilize Existing Processing

### Goal

Make the current durable processing pipeline reliable before changing ranking.

### Tasks

1. Finalize and verify the media-job completion timestamp fix.
2. Recover STAGED draft images that have no image embedding job.
3. Verify the configured database credentials before backend restart.
4. Confirm that Flyway contains no duplicate migration versions.
5. Verify queue completion, retry, lease, rerun, and dead-letter behavior.
6. Record IMAGE and SEMANTIC coverage by asset status and institution.
7. Confirm that new jobs no longer fail because of SQL type or conflict-target errors.

### Completion criteria

- New jobs reach `COMPLETED` without completion-query errors.
- Missing draft-image jobs are recovered.
- The backend can reconnect using the current environment configuration.
- Tests cover recovery, retry, completion, and tenant-safe candidate retrieval.

## Phase 1 - Voyage-Only Media Library Ingestion

### Goal

Generate both retrieval vectors without making Claude a prerequisite.

### Tasks

1. Create a versioned media retrieval operation that produces IMAGE and
   SEMANTIC embeddings using Voyage.
2. Build semantic input from the trusted metadata listed in Section 5.2.
3. Preserve direct-to-R2 upload and immediate registration responses.
4. Run provider calls only from the durable background worker.
5. Never hold a database connection during R2 download or Voyage calls.
6. Store each successful stage independently so retries reuse completed work.
7. Mark retrieval processing complete only after the required vectors are stored.
8. Keep optional Claude enrichment separate and non-blocking.
9. Add telemetry for queue delay, image embedding latency, semantic embedding
   latency, reuse, failure, and total upload-to-searchable time.

### Completion criteria

- Upload requests do not wait for Voyage.
- Claude failure cannot prevent a valid image from becoming searchable.
- Every successful image upload eventually receives current IMAGE and SEMANTIC vectors.
- Retrying one failed stage does not repeat a successful provider call.

## Phase 2 - Metadata Invalidation and Efficient Reprocessing

### Goal

Regenerate only the vector affected by a file or metadata change.

### Tasks

1. Compute and persist a normalized semantic-input hash.
2. Requeue SEMANTIC embedding after tag, album, display-title, format, or
   lifecycle changes.
3. Do not regenerate IMAGE embedding for metadata-only edits.
4. Regenerate IMAGE embedding when the file or image model/version changes.
5. Deduplicate repeated enqueue requests.
6. Preserve institution scope while reprocessing.

### Completion criteria

- Metadata changes become searchable after background processing.
- Unchanged vectors are reused.
- Concurrent edits cannot leave an older job result as the current embedding.

## Phase 3 - Historical Embedding Backfill

### Goal

Make existing eligible Media Library images participate in search and suggestions.

### Tasks

1. Inventory missing, stale-model, and failed embeddings.
2. Prioritize current draft selections and new uploads over historical assets.
3. Backfill in bounded batches with per-institution fairness.
4. Skip deleted, unsupported, and permanently invalid assets.
5. Keep videos out of this image-focused phase.
6. Track coverage and failure counts until the eligible corpus is complete.

### Completion criteria

- Every eligible READY image has current IMAGE and SEMANTIC embeddings.
- Backfill does not starve user-triggered jobs.
- Failed assets have actionable error state and bounded retries.

## Phase 4 - Unified Media Library Search

### Goal

Use semantic embeddings through the existing search field without redesigning it.

### Target behavior

```text
Search text
-> exact keyword/tag retrieval
-> Voyage semantic query embedding
-> pgvector semantic retrieval
-> authorization and lifecycle filters
-> rank fusion
-> paginated results
```

### Tasks

1. Preserve exact filename, title, tag, and asset-code matching.
2. Use semantic retrieval for meaning-based matches.
3. Merge keyword and semantic ranks using rank fusion rather than directly
   adding uncalibrated score types.
4. Preserve keyword fallback during Voyage failure.
5. Enforce institution visibility in SQL and service authorization.
6. Require at least two non-whitespace characters.
7. Debounce normal typing around 400 ms and cancel obsolete requests.
8. Cache short-lived query results by user scope, institution scope, normalized
   query, model version, and filter state.
9. Invalidate affected cached searches after upload, tag, title, lifecycle, or
   deletion changes.
10. Keep the current semantic toggle during validation; remove or repurpose it
    only with explicit UI approval.

### Completion criteria

- The current search field can find conceptually relevant assets.
- Exact lookup remains reliable.
- Voyage failure does not break basic search.
- No result crosses institution or role boundaries.

## Phase 5 - Submission AI Suggestions

### Goal

Rank Media Library assets using every selected image and optional submission text.

### Target flow

```text
Selected persisted images
+ optional title, caption, category, and tags
-> visual and semantic candidate retrieval
-> tenant, visibility, attachment, and lifecycle filters
-> ranking and diversity
-> up to eight suggestions
```

### Initial ranking baseline

```text
45% semantic similarity
35% visual similarity
10% selected-image coverage
5% metadata agreement
5% freshness/reuse suitability
```

These are experimental starting weights. They must not be treated as final until
the evaluation phase.

### Tasks

1. Use all persisted selected image IDs, not only the first image.
2. Allow image-only activation; text remains optional context.
3. Exclude already attached, deleted, inaccessible, draft-only, and expired assets.
4. Prevent repeated branding and near-identical group photos from dominating the result set.
5. Keep previous usable results visible during background refresh where safe.
6. Distinguish these outcomes:
   - selected media is still being saved;
   - embedding is processing;
   - embedding failed;
   - no indexed candidates exist;
   - candidates exist but none pass relevance threshold;
   - suggestions are ready.
7. Do not show a genuine no-match message for processing failure or timeout.

### Completion criteria

- Upload Files and My Library selections activate the same recommendation path.
- Multiple selected photos affect ranking and coverage.
- Processing failure is diagnosable and is not mislabeled as no match.
- Results remain institution-safe and temporally appropriate.

## Phase 6 - DASIG-Specific Evaluation and Tuning

### Goal

Measure whether the retrieval architecture is accurate enough for actual DASIG media.

### Dataset

Use original Media Library files. Do not embed Facebook screenshots as if they
were original post attachments. Build at least 30 representative query scenarios,
including:

- startup bootcamp;
- pitching competition;
- technology commercialization;
- speaker presentation;
- workshop participants;
- awarding;
- partnership meeting;
- registration poster;
- online consortium meeting;
- group event photo.

Each scenario needs human relevance labels from the project team or stakeholder.

### Metrics

- top-result relevant/highly relevant rate;
- Precision@5;
- NDCG@10;
- suggestion acceptance rate;
- no-result rate;
- upload-to-searchable latency;
- semantic search latency;
- suggestion-ready latency.

### Initial targets

- At least 70% of evaluated first suggestions are relevant or highly relevant.
- Keyword search remains available during Voyage failure.
- Search response is under one second after a query embedding is available under
  the agreed test environment.
- No cross-institution result appears in any test.

### Experiments

Compare at minimum:

1. IMAGE similarity only.
2. SEMANTIC similarity only.
3. Current weighted IMAGE + SEMANTIC ranking.
4. Rank-fused retrieval.
5. A later single multimodal image-plus-text vector only if justified by results.

Do not adopt new weights or a new vector strategy without recorded evaluation results.

## Phase 7 - Monitoring and Controlled Rollout

### Goal

Detect cost, latency, quality, and reliability regressions after deployment.

### Tasks

1. Track queue depth, age, retries, dead letters, and completion rate.
2. Track Voyage provider calls, reuse, duration, and failure rate.
3. Track embedding coverage by institution and model/version.
4. Track semantic searches with no result.
5. Track suggestion impressions, acceptance, and dismissal.
6. Compare legacy and new ranking in shadow mode before enabling new output.
7. Roll out with the existing institution feature flag.
8. Preserve a rollback path until evaluation targets remain stable.

### Completion criteria

- Administrators can identify stuck or failed processing.
- Deployment can revert to the previous ranking without deleting embeddings.
- Telemetry contains no image bytes, captions, personal names, or other content.

## 8. Upload Efficiency Rules

- Keep browser upload concurrency bounded at approximately three files.
- Hash and upload files outside React render work.
- Keep direct R2 upload; do not proxy large files through Spring Boot.
- Register each successful object independently so one failed file does not lose
  completed uploads.
- Prioritize newly uploaded and currently selected assets in the worker queue.
- Batch provider requests only when retry and per-asset status remain traceable.
- Never retry authentication, validation, or unsupported-file failures as if they
  were transient provider failures.
- Apply bounded exponential backoff to retryable provider/network failures.
- Cache by content hash and model/input version only when tenant isolation and
  metadata differences remain correct.

## 9. Security and Privacy Rules

- Apply institution and role scope before similarity ordering and before returning results.
- Do not depend only on frontend filtering.
- Do not expose storage credentials or private object URLs to AI providers.
- Continue using prepared image bytes for Voyage requests.
- Do not infer or store sensitive personal traits from faces.
- Do not add face recognition or person identification.
- Do not use uploaded content to train an external model unless separately approved.
- Do not log vectors, image bytes, captions, or full provider payloads.

## 10. Required Tests

### Backend

- Upload registration queues the correct job after commit.
- IMAGE and SEMANTIC stages are independently idempotent.
- Metadata edits invalidate only SEMANTIC input.
- File/model changes invalidate IMAGE input.
- Retry reuses successful stages.
- Dead-letter and manual retry work.
- Backfill prioritizes selected draft and new assets.
- Keyword fallback survives Voyage failure.
- Search and suggestions enforce institution and role scope.
- Expired assets do not appear in suggestions.
- Multiple selected images affect coverage scoring.

### Frontend

- Existing Media Repository UI remains unchanged.
- Upload progress and cancellation remain functional.
- Search debounces and cancels stale requests.
- Existing content remains visible during safe background refresh.
- Processing, failure, no-candidate, no-match, and ready states are not conflated.
- Upload Files and My Library activate the same suggestion behavior.

### Regression

- Existing upload, albums, duplicate detection, tags, deletion, restore, and
  pagination continue to work.
- Submission draft save, media order, review, and publication behavior remain unchanged.
- Existing authorization tests continue to pass.

## 11. Pull Request Sequence

Use one focused PR per phase:

1. `phase/media-ai-0-processing-stability`
2. `phase/media-ai-1-voyage-ingestion`
3. `phase/media-ai-2-metadata-invalidation`
4. `phase/media-ai-3-embedding-backfill`
5. `phase/media-ai-4-unified-search`
6. `phase/media-ai-5-submission-ranking`
7. `phase/media-ai-6-evaluation`
8. `phase/media-ai-7-monitoring-rollout`

After each merged PR:

```text
checkout dev
pull origin/dev
create the next phase branch
implement only the next phase
run focused and full regression tests
push and create the next PR
```

## 12. Definition of Done

This development is complete only when:

- every eligible Media Library image receives reliable Voyage retrieval embeddings;
- Claude is not required for upload, search, or suggestion readiness;
- the existing Media Library search field supports semantic and exact retrieval;
- submission suggestions work from uploaded or library-selected images;
- all selected images can influence ranking;
- expired and unauthorized assets cannot be returned;
- old assets are backfilled without starving new uploads;
- measured DASIG-specific relevance meets the agreed validation target;
- queue, provider, coverage, latency, and adoption telemetry are available; and
- existing Media Library and submission features pass regression testing.

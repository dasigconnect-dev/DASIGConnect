# AI Media Library Processing Decision

**Phase:** 9 - Media Library Processing Review

**Decision date:** September 25, 2026

**Status:** Accepted for the current implementation

**Scope:** Review only; no runtime, database, API, or UI behavior is changed by this phase.

## 1. Decision Summary

Keep Claude Vision classification mandatory before a Media Library image becomes `READY`.

Do not remove, bypass, defer, or make Claude user-triggered in the current pipeline. Do not mark a library asset `READY` after only its Voyage image embedding succeeds.

The existing direct-upload path may continue generating an image embedding while an asset is `STAGED`. That path supports Step 1 AI Suggestions without exposing draft media as Media Library candidates and does not replace the full library enrichment pipeline.

A two-stage library lifecycle is technically possible, but it must be treated as a separate architecture change after:

1. Phase 10 proves that enrichment-dependent behavior remains covered by regression tests.
2. Phase 11 supplies production-like latency, queue-delay, failure-rate, and provider-cost measurements.
3. A separate schema and API design distinguishes recommendation readiness from full enrichment readiness.

## 2. Current Verified Pipeline

### Media Library upload

```text
Upload registered
  -> media_assets.status = PROCESSING
  -> CLASSIFY_AND_EMBED job queued after transaction commit
  -> Claude Vision classification
  -> structured metadata and AI tags persisted
  -> Voyage image embedding generated or reused
  -> Voyage semantic embedding generated from metadata
  -> media_assets.status = READY
  -> submission media contexts queued for rebuild when applicable
```

Evidence:

- `MediaAssetService.upload()` creates image assets as `PROCESSING` and enqueues durable work after commit (`backend/src/main/java/com/dasigconnect/backend/service/MediaAssetService.java`, lines 730-787).
- `MediaProcessingWorker.process()` routes `CLASSIFY_AND_EMBED` work through `AIClassificationService.processAsset()` and marks the processing version ready only after it succeeds (`backend/src/main/java/com/dasigconnect/backend/schedule/MediaProcessingWorker.java`, lines 77-107).
- `AIClassificationService.processAsset()` currently runs Claude first, then the Voyage image embedding, then the Voyage semantic embedding, and finally sets `READY` (`backend/src/main/java/com/dasigconnect/backend/service/AIClassificationService.java`, lines 75-139).
- Durable jobs use bounded batches, institution fairness, leases, retries with backoff, and dead-letter handling (`backend/src/main/java/com/dasigconnect/backend/service/MediaProcessingQueueService.java`, lines 23-125).
- `EmbeddingReconciliationJob` re-enqueues incomplete processing versions without bypassing the durable queue (`backend/src/main/java/com/dasigconnect/backend/schedule/EmbeddingReconciliationJob.java`, lines 45-63).

### Direct submission upload

```text
Draft image attached as STAGED
  -> EMBED_IMAGE_ONLY job
  -> Voyage image embedding generated or reused
  -> asset remains private to the draft workflow
  -> selected image can provide visual context for AI Suggestions
```

The `EMBED_IMAGE_ONLY` branch calls only `MediaImageEmbeddingService.generateOrReuse()` and does not change the asset's status or run semantic enrichment (`MediaProcessingWorker.java`, lines 89-98; `MediaImageEmbeddingService.java`, lines 29-68).

## 3. What Claude Produces

`AIClassificationService.persistClassification()` stores these Claude-derived fields:

- category and confidence
- description and asset type
- visible objects and specific subjects
- visual style and dominant colors
- possible use cases
- AI tags and excluded categories
- observed scenes and activities
- people-count range
- equipment and recognition signals
- OCR text and visible dates
- event hypotheses
- temporal classification and possible expiration
- visual-quality and composition signals
- classification timestamp and model

Source: `backend/src/main/java/com/dasigconnect/backend/service/AIClassificationService.java`, lines 258-286.

The worker records the processing version only after the complete pipeline succeeds through `MediaAssetRepository.markProcessingReady()` (`MediaProcessingWorker.java`, lines 100-107).

Claude does not create the image embedding. `MediaImageEmbeddingService` prepares the image and calls Voyage AI for the `IMAGE` embedding. Claude metadata is used to build the text sent to Voyage for the separate `SEMANTIC` embedding.

## 4. Verified Feature Dependencies

| Feature | Claude dependency | Current behavior if metadata is absent |
|---|---|---|
| Media Library category label | Direct | `aiCategory` is returned in media DTOs and displayed on media cards and suggestion results. |
| AI-generated tags | Direct | Claude suggestions are persisted as `ai_generated` asset tags; only manual tags remain without classification. |
| Semantic Media Library search | Strong | Voyage semantic vectors are generated from Claude metadata plus stable asset metadata and tags. Keyword fallback remains, but semantic quality and recall are reduced. |
| Text-based media suggestions | Strong | Semantic candidate retrieval and metadata boosts use category, description, and AI tags. |
| Image-based similarity | None for the vector itself | Voyage can generate an `IMAGE` vector directly from the image. Candidate eligibility still currently requires library status `READY`. |
| Temporal safety | Direct | Expired/time-bound media filtering uses `temporalClassification` and `possibleExpiration`. Missing values remain eligible. |
| Event-context ranking | Direct | Scenes, activities, equipment, recognition, OCR, and event hypotheses populate submission media context. |
| Visual-quality ranking | Direct | Hybrid ranking uses Claude visual-quality signals when available. |
| Photo-sequence diversity | Partial | Composition, scene, activity, equipment, recognition, event, and category terms help calculate complementarity and near-duplicate diversity. |
| AI caption generation | Enhancement | Caption generation still receives the selected image URLs, but its supplemental media context loses category, description, subjects, style, colors, use cases, and AI tags. |
| Media detail/repository UI | Direct but non-blocking at render time | Category and AI tags disappear when absent; the existing UI does not synthesize replacements. |

Key code references:

- Recommendation metadata, temporal, quality, event-context, and sequence signals: `backend/src/main/java/com/dasigconnect/backend/service/AIRecommendationService.java`, lines 630-703, 735-915, and 1076-1146.
- Caption metadata: `backend/src/main/java/com/dasigconnect/backend/service/CaptionGenerationService.java`, lines 148-183.
- Submission context aggregation: `backend/src/main/java/com/dasigconnect/backend/service/SubmissionMediaContextService.java`, lines 51-125.
- Category/tag presentation: `frontend/src/components/media/MediaAssetCard.tsx`, `frontend/src/features/media-repository/components/AssetDetailPanel.tsx`, and `frontend/src/features/submission/components/SimilarMediaPanel.tsx`.
- Semantic repository search: `backend/src/main/java/com/dasigconnect/backend/service/MediaAssetService.java`, lines 212-294.

## 5. Answers to the Phase 9 Questions

### Does every uploaded library image need Claude metadata immediately?

Under the current contract, yes. A library image is not `READY` until classification and both embeddings complete. Multiple user-facing and ranking features expect that a `READY` image has completed enrichment.

This is an architectural requirement of the current implementation, not a limitation of Voyage image embeddings. Pure visual similarity could work before Claude finishes if a separate readiness state existed.

### Can recommendation readiness happen before full enrichment?

Technically yes, but not safely with the current status model and queries.

Current candidate queries require `media_assets.status = 'READY'`, including the image-vector recommendation query in `MediaAssetEmbeddingRepository.findTopSimilarToAssetsWithScore()` and repository/semantic search queries. Marking an image `READY` after only image embedding would incorrectly imply that classification, semantic embedding, temporal analysis, and context signals had also completed.

### Can Claude enrichment be asynchronous?

It already is asynchronous from the upload request. Upload returns after database registration; a scheduled durable worker performs external provider calls later.

### Can Claude enrichment be lower priority?

Not with the current single `CLASSIFY_AND_EMBED` job. A lower-priority stage would require separate job types, claim ordering, independent retry state, and queue-health reporting.

### Can Claude enrichment be user-triggered or submission-triggered?

Not without changing established behavior. User-triggered enrichment would make category/tag/search quality inconsistent across the library. Submission-triggered enrichment could delay caption and context workflows at the point of use and create provider bursts. Neither mode is approved in this phase.

## 6. Options Considered

### Option A - Retain the full Claude-first pipeline

**Decision:** Adopt now.

Benefits:

- Preserves the meaning of `READY`.
- Preserves semantic search, temporal safety, caption context, and hybrid ranking.
- Uses the existing durable retry/dead-letter architecture.
- Requires no migration, API contract change, or UI change.

Cost:

- Voyage image embeddings wait behind Claude classification for library uploads.
- A Claude outage prevents a new library image from becoming a recommendation candidate.

### Option B - Reorder image embedding before Claude but retain one `READY` state

**Decision:** Reject as a standalone change.

Generating the image vector earlier would not improve library recommendations because candidate queries still require `READY`. Changing `READY` earlier would misrepresent incomplete enrichment.

### Option C - Introduce recommendation-ready and enrichment-ready stages

**Decision:** Defer for measured design after Phases 10 and 11.

This is the only safe route to expose a library image to visual recommendations before Claude finishes. It requires explicit state separation rather than weakening the existing `READY` contract.

### Option D - Make Claude manual or submission-triggered

**Decision:** Reject for the current MVP.

It would produce inconsistent library quality and move latency into contributor workflows. There is no current evidence that the provider cost or delay justifies that regression.

## 7. Required Design Before Any Future Split

If Phase 11 measurements justify a two-stage pipeline, a later design must address all of the following before implementation:

1. Add independent, auditable states for image-embedding readiness, structured enrichment, and semantic-embedding readiness. Do not overload `media_assets.status`.
2. Split `CLASSIFY_AND_EMBED` into idempotent durable stages with independent versions, attempts, errors, and dead letters.
3. Update candidate SQL to admit only recommendation-ready, institution-authorized, non-deleted, non-draft library assets.
4. Preserve the existing rule that staged draft assets may be query inputs but never library candidates.
5. Define ranking behavior when temporal, quality, event, category, and sequence metadata is unavailable. Missing signals must not be interpreted as positive signals.
6. Prevent time-bound media with unknown enrichment status from bypassing temporal safeguards without an explicit product decision.
7. Keep semantic search restricted to assets whose semantic embedding matches the current enrichment version.
8. Rebuild submission media context after enrichment completes and invalidate recommendation caches where required.
9. Define frontend behavior for recommendation-ready but not fully enriched assets without redesigning the existing UI unless separately approved.
10. Add metrics for stage latency, queue delay, provider calls, retries, dead letters, suggestion relevance, and partially enriched result rates.

## 8. Risks of Changing Claude Behavior Now

| Risk | Impact |
|---|---|
| `READY` becomes ambiguous | Existing code may treat partially processed assets as fully enriched. |
| Temporal metadata is missing | Old or event-specific assets may remain eligible when they should be excluded. |
| Semantic vectors are absent/stale | Repository search and text-driven suggestions lose quality or return inconsistent results. |
| Caption context is reduced | Generated captions receive less information about selected media. |
| Hybrid signals disappear | Event, quality, composition, and sequence ranking become weaker. |
| Queue reconciliation repeats work | The current processing version checks assume the full pipeline is one unit. |
| UI labels become inconsistent | Some library cards have category/tags while visually ready peers do not. |
| Provider failures become hidden | A visual-ready state could mask failed enrichment unless failures are modeled separately. |

## 9. Validation and Measurement Needed

No latency or cost values are asserted in this document because they are not available from source code alone.

Phase 11 should measure at minimum:

- upload registration latency
- queue wait time by job type and institution
- Voyage image-embedding duration and failure rate
- Claude classification duration, failure rate, and cost
- Voyage semantic-embedding duration and failure rate
- time to first usable image recommendation
- time to full `READY`
- retries and dead-letter counts per stage
- provider calls per uploaded image
- relevance of visual-only versus fully enriched recommendations

## 10. Final Decision

The Media Library retains this production contract:

```text
Claude classification
  -> Voyage image embedding
  -> Voyage semantic embedding
  -> READY
```

No Claude behavior is removed or reduced in Phase 9. The current asynchronous durable pipeline remains in place. A two-stage recommendation/enrichment lifecycle may be reconsidered only through a separate reviewed implementation after regression coverage and measurements demonstrate a clear benefit.

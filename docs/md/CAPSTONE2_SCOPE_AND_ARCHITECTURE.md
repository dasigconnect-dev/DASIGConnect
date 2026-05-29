# DASIGConnect Capstone 2 — Scope & Architecture

> **Status:** Draft for panel review and revised-proposal input
> **Builds on:** Capstone 1 (UC-1.1 → UC-3.5, all delivered)
> **Theme:** Turn the four modules (Media, AI, Analytics, Submission) into a single closed feedback loop.

---

## 1. Vision — The Closed Loop

Capstone 1 built four capable but largely *independent* subsystems. Capstone 2's thesis is that they should feed each other:

```
        ┌──────────────────────────────────────────────────────────┐
        │                                                            │
        ▼                                                            │
  Media Repository  ──embeddings──►  AI Search / Suggestions         │
        │                                   │                        │
        │                                   ▼                        │
        │                          Content Submission                │
        │                                   │                        │
        │                                   ▼                        │
        │                          Facebook Publishing               │
        │                                   │                        │
        │                                   ▼                        │
        └────────── ranking boost ◄── Engagement Analytics ──────────┘
              (posts that performed well rank higher next time)
```

Every photo already becomes a pair of vectors; every published post already has a Facebook post ID. Capstone 2 closes the loop: **vectors organize and quality-rank the library, and engagement data flows back to coach the next submission.**

This single narrative is the defense story. Each feature below is one arc of that loop.

---

## 2. Relationship to Capstone 1

Capstone 2 is additive. It does **not** rework UC-1.1 → UC-3.5. It extends:

- **UC-2.2 (Media Repository)** → intelligent repository (folders, auto-grouping, curation, quality, NL search).
- **UC-2.4 (Analytics)** → engagement analytics + feedback loop.
- **UC-3.2 / UC-3.3 (AI)** → measurable AI accuracy loop + caption prompt mode.
- **UC-1.3 (Submission)** → pre-submit AI advisor.

The existing `media_asset_embeddings` (dual image + semantic, 1024-dim), Claude Vision classification, `AiInteractionLog`, `publication_attempts`, and the `PROCESSING → READY → FAILED` asset status model are the foundations everything reuses.

---

## 3. Decisions Resolved

These were open product questions; resolved here with rationale (adjust if the panel disagrees):

| # | Decision | Resolution | Rationale |
|---|---|---|---|
| 1 | Curation flow | **AI-assisted, human-confirmed; bulk-review for dumps** — *not* mandatory per-photo | Forcing a title per photo kills the "tired contributor dumps 200 photos" scenario. AI pre-fills title/tags so every asset is findable even if the user skips; dumps get one batch-review screen, not 200 forms. |
| 2 | "Posted/uploaded" in search | **Uploaded time** = `media_assets.created_at` | One table, one timestamp, no Facebook dependency. (Published-time search can be a later filter.) |
| 3 | Collage | **Client-side, template-based single composed image** exported and re-uploaded as a new asset | Multi-photo Facebook posts already exist (carousel). A true collage is a frontend canvas job — no backend/AI cost. |
| 4 | Consent / visibility flag | **In scope** | Government system with photos of students/minors. Per-asset visibility gate is a responsible-AI feature panels reward, at low cost. |
| 5 | Content-safety check | **In scope as a deliberate expansion** | Capstone 1 scoped *out* automated moderation. Capstone 2 adds a lightweight upload-time safety flag for a government page. Must be stated as an intentional scope change. |

---

## 4. New Use Cases (Module 4)

Written in the measurable-objective style of the Capstone 1 proposal so they can drop into a revised SRS.

### Intelligent Media Repository

**UC-4.1 — Folders & Albums.**
Manual organization layer. A nested folder/album structure per institution (`media_folders`, nullable `parent_folder_id`), with assets assignable to a folder, plus bulk move/tag/delete operations. Target: a contributor can organize 100 assets into albums in ≤2 minutes; retrieval of a foldered asset in ≤2 s.

**UC-4.2 — Bulk Ingestion & AI Auto-Grouping.**
Handle a single contributor uploading many assets at once ("event dump") without degrading the system, and auto-suggest album groupings. On bulk upload, assets are recorded as one **import batch**, enqueued to a bounded processing queue, and — once embedded — clustered by image-embedding similarity into suggested albums, each named by a single Claude call on a representative image. Target: ingest and enrich **200 assets in one batch** with zero dropped DB connections (HikariCP ≤5 respected), and ≥70% of contributors agree the auto-suggested groupings are "useful."

**UC-4.3 — AI-Assisted Curation at Upload.**
Every asset receives an AI-suggested title, description, and tags (already produced by Claude classification) surfaced for human confirmation. Single uploads get an inline confirm; batches get a bulk-review screen (confirm-all / fix-the-few). Manual tags are weighted above AI tags in retrieval (already implemented in `AIRecommendationService` ranking). Target: ≥80% of assets carry at least one human-confirmed or human-edited tag.

**UC-4.4 — Quality & Duplicate Filtering.**
Two *independent* mechanisms — they solve different problems, do not conflate them:
- **Exact / near-duplicate detection → perceptual hash, not embeddings.** Compute a 64-bit pHash/dHash at ingestion; flag pairs within a small Hamming distance (start ~≤6, tune on the labeled set). Cheap, deterministic, correct. Embedding cosine measures *semantic* similarity and will false-flag distinct shots from the same event as "duplicates."
- **Quality scoring → deterministic + optional AI.** Resolution/aspect checks and blur detection (Laplacian variance — needs a hand-rolled kernel over `BufferedImage`, since there is no OpenCV in the stack; threshold tuned, not fixed). Optional Claude ranking of a *small* set for "best photo of a burst."
- **"Visually similar / best-of-burst" grouping** (a distinct feature) is where image-embedding cosine *is* the right tool.

Target: ≥90% precision **and** recall on exact-and-near duplicates over a labeled set of ≥50 image pairs (see §9).

**UC-4.5 — Natural-Language / Hybrid Media Search.**
A **two-stage retrieve-then-rank** design (candidate generation → ranking), driven by a search box. Two query paths:
- **Semantic / cross-modal** ("a person holding a glass discussing with students", "photos related to hackathon"). Embed the prompt with the model that matches the target space — `voyage-multimodal-3.5` (text input) to search `image` vectors, `voyage-4-lite` to search `semantic` vectors. **Never compare a `voyage-4-lite` text vector to multimodal `image` vectors** (different spaces). Retrieval is **hybrid**: Postgres lexical search **+** pgvector dense search, fused with **Reciprocal Rank Fusion** so exact keywords (asset codes, proper nouns) and paraphrase both work. **Note:** V16 provides only `pg_trgm` trigram GIN indexes on `file_name`/`asset_code` (substring ILIKE) — there is *no* `tsvector` column yet. The lexical stage is therefore **new V28+ work**: either add a `tsvector` generated column + GIN index over title/tags/description for ranked full-text (`ts_rank`), or extend the existing `pg_trgm` path. Do not assume tsvector already exists.
- **Temporal / structured** ("photos uploaded last Monday 3–5 pm") → one Claude call parses the prompt into `{ semantic_text, date_range, time_of_day, category, media_type }`. Filters are applied with **over-fetch + post-filter** (retrieve a wide candidate set, then filter) to avoid pgvector's filtered-ANN recall collapse. Time-of-day is converted from Philippine local (UTC+8) to the UTC `created_at`.

Target: relevant result in the top 3 for ≥70% of queries on a 20-query golden set (§9).

### AI

**UC-4.6 — AI Feedback & Accuracy Loop.**
Thumbs up/down (and "applied / dismissed") on suggestions, search results, and captions, surfaced as precision metrics in analytics. This is what lets the defense report *numbers* ("top-3 search relevance: X%"). **Schema reality:** the existing `ai_interaction_log` (V18) has `submission_id NOT NULL` and only `interaction_type` / `action_taken` / `tone_selected` — it cannot record *search* feedback (search is not tied to a submission) and has no rating/target columns. UC-4.6 therefore requires a V28+ migration to (a) make `submission_id` nullable, (b) add a nullable target reference (e.g. `target_asset_id` / `result_rank`), and (c) optionally a `rating` field — *then* reuse the entity. Target: instrument all four AI surfaces (search, media suggestion, caption, advisor) and report measured precision/acceptance.

**UC-4.7 — Caption Prompt Mode + Best-N image selection.**
Expose an explicit "prompt mode" toggle (the intent-detection path already exists in `ClaudeVisionClient.buildPrompt()`), and pre-rank attached images by quality (UC-4.4) so Claude receives the most informative shots rather than the first 4. Target: maintain the existing ≥60% caption acceptance while supporting >4 attached images via pre-selection.

*(Content-safety flag — deliberate Capstone-1 scope expansion — rides on the existing upload-time Claude call: a safety verdict stored on the asset, low-confidence/flagged assets routed to a review state.)*

### Analytics

**UC-4.8 — Facebook Engagement Insights Sync.**
A `FacebookInsightsSyncJob` periodically pulls metrics for each published post (joined via **`submissions.platform_post_id`** — the actual published-post ID column from V1; note `publication_attempts` stores only `photo_ids_staged`, *not* the final post ID) into a time-series table: reactions, comments, shares (covered by existing `pages_read_engagement`), and — if `read_insights` is added — reach/impressions. Idempotent, respects the Graph API 200 calls/hour budget. Target: metrics for ≥95% of published posts refreshed within the sync window.

**UC-4.9 — Engagement → Media Ranking Feedback Loop.** *(Anchor #1)*
Assets reused in well-performing posts get a *light* ranking nudge in future suggestions/search. Honest constraints baked into the design:
- **Attribution is post-level, not per-photo** — Facebook reports engagement per *post*, and a post carries several assets, so credit is necessarily spread across all assets in that post. State this; do not imply per-photo causality.
- **Small-sample noise handled with empirical-Bayes shrinkage** — each asset's score is shrunk toward the institution mean in proportion to how little engagement data it has, plus **time decay** so old hits fade and the loop cannot ossify into rich-get-richer.
- **Computed by pipeline, served as a feature** — an idempotent `AssetPerformanceJob` rolls UC-4.8 metrics into a stored, shrunk, decayed `performance_score`; `AIRecommendationService` consumes it as one *low-weight tie-breaker* among the existing semantic/category/tag/recency signals — never as a primary signal.

Target: demonstrate boosted assets rise in rank for matching contexts, with the score shown to be shrinkage-stable at low sample sizes.

**UC-4.10 — Pre-Submit AI Advisor.**
Before submit, a `SubmissionAdvisorService` returns concrete suggestions (best time to post, caption length, photo-type guidance). Designed so it cannot manufacture false confidence:
- **Recommendation and narration are separated (RAG discipline).** A deterministic layer computes verified aggregates *with sample sizes*; the LLM only *phrases* them and is forbidden from doing the math or inventing numbers.
- **Claims are gated by sample size** (same empirical-Bayes threshold as UC-4.9): a bucket with too little data shows as "limited data," not as a confident multiplier. Leads with heuristics until volume justifies statistics.
- Read-only, best-effort; **never blocks submission**.

Target: ≥60% of contributors rate advisor suggestions "useful."

### Governance & Provenance

**UC-4.11 — Media Provenance & Audit Trail.**
*Verified gap (see §10): media operations are currently **not** recorded in the immutable `AuditLog` — `AuditLogService` is wired into 10 other services but not `MediaAssetService`/`MediaAssetRetentionService`.* This UC closes the provenance pillar that makes the repository "digital, not just storage." Wire `AuditLogService` into every state-changing media operation — **upload, metadata edit/curation, tag add/remove, soft-delete, restore, retention purge, visibility/consent change, and reclassification** — each writing an immutable audit row with actor, asset, action, and before/after where applicable. Reuses the existing `AuditLog` entity/service and audit-on-state-change pattern; no new table. Calls stay outside the external-API critical path and never roll back the business action (same try-catch discipline as the T1 notification block). Target: 100% of state-changing media operations produce an audit record; every asset's full history is reconstructable from `audit_log`.

### Cross-cutting

- **Consent / visibility flag (UC-4.x):** per-asset `visibility` (`internal_only` / `cleared_for_public`); submission to publishing blocked unless cleared.
- **Mobile-first, connection-resilient upload:** the core dump scenario happens on a phone on event wifi — explicitly scope responsive upload UX and resilient (resumable/retry) uploads.
- **Demonstrable scale numbers:** load-test the 200-asset dump and report throughput.
- **AI cost governance:** per-institution processing budget, caching of classifications, graceful degradation (metadata-only fallback already exists).

---

## 5. Architecture Additions

All aligned to the backend guide: Controller→Service→Repository, DTOs only at the boundary, Flyway (next free version is **V28** — V27 `institution_status_refactor` already exists on disk), RLS on every institution-scoped table, short transactions, **no DB connection held across an external API call**, `@Async` / bounded executor for enrichment, audit log on state changes.

### 5.1 Schema (new Flyway migrations, V28+)

```sql
-- Folders / albums (UC-4.1)
CREATE TABLE media_folders (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id   UUID NOT NULL REFERENCES institutions(id),
    parent_folder_id UUID REFERENCES media_folders(id) ON DELETE CASCADE,
    name             TEXT NOT NULL,
    created_by       UUID NOT NULL,
    created_at       TIMESTAMPTZ DEFAULT now()
);
ALTER TABLE media_assets ADD COLUMN folder_id UUID REFERENCES media_folders(id) ON DELETE SET NULL;

-- Import batches + processing queue (UC-4.2)  — the bounded ingestion queue
CREATE TABLE media_import_batches (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institution_id UUID NOT NULL REFERENCES institutions(id),
    uploaded_by    UUID NOT NULL,
    asset_count    INT  NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ DEFAULT now()
);
ALTER TABLE media_assets ADD COLUMN import_batch_id UUID REFERENCES media_import_batches(id);
ALTER TABLE media_assets ADD COLUMN suggested_album_id UUID;          -- cluster grouping
-- (processing state reuses existing media_assets.status PROCESSING/READY/FAILED)

-- Curation + consent (UC-4.3, UC-4.x)
ALTER TABLE media_assets ADD COLUMN title          TEXT;             -- human-confirmable
ALTER TABLE media_assets ADD COLUMN curated_at     TIMESTAMPTZ;
ALTER TABLE media_assets ADD COLUMN visibility     TEXT DEFAULT 'internal_only';  -- internal_only | cleared_for_public
ALTER TABLE media_assets ADD COLUMN safety_verdict TEXT;             -- ok | review | blocked

-- Quality / duplicate (UC-4.4)
ALTER TABLE media_assets ADD COLUMN blur_score      NUMERIC;
ALTER TABLE media_assets ADD COLUMN perceptual_hash BIGINT;            -- pHash/dHash for near-dup (NOT embeddings)
ALTER TABLE media_assets ADD COLUMN duplicate_of_id UUID REFERENCES media_assets(id) ON DELETE SET NULL;

-- Engagement time-series (UC-4.8)
CREATE TABLE facebook_post_metrics (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id      UUID NOT NULL REFERENCES submissions(id),
    facebook_post_id   TEXT NOT NULL,
    fetched_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    reactions          INT, comments INT, shares INT,
    reach              INT, impressions INT                          -- nullable until read_insights granted
);
CREATE INDEX ix_fb_metrics_submission ON facebook_post_metrics(submission_id, fetched_at DESC);

-- Asset performance score (UC-4.9) — pipeline-computed (shrunk + decayed), served as a feature; never written in the request hot path
ALTER TABLE media_assets ADD COLUMN performance_score NUMERIC DEFAULT 0;
```

> Enable RLS on `media_folders`, `media_import_batches`, and `facebook_post_metrics` in the same migration that creates them (scope through `institution_id`, or through the parent row for metrics — note the metrics policy needs a join to `submissions`). **Backfill landmines:** `visibility` must **grandfather existing assets to `cleared_for_public`** — defaulting them to `internal_only` would instantly freeze the live submission/publishing flow; only *new* uploads default to `internal_only`. `folder_id` and `duplicate_of_id` use `ON DELETE SET NULL` so deleting a folder or a duplicate's original never orphans or FK-violates assets. Add columns nullable-then-backfill, never with a default that hides existing READY assets — same caution noted in `ai-media-library-upgrade.md`.

### 5.2 Bulk Ingestion Queue (the scalability core)

Today each upload fires `@Async` and makes 3 external calls (Claude + 2× Voyage). A 200-asset dump = ~600 simultaneous calls → thread/memory blowup, rate-limit rejections. Replace fire-and-forget with a **bounded queue worker**:

- Assets enter as `status = PROCESSING` immediately; upload returns instantly (browser→Supabase direct upload is already non-blocking).
- A fixed-size `ThreadPoolTaskExecutor` (**~2 workers, bounded queue**) drains work at a controlled rate — *not* an unbounded async pool. Worker count is deliberately small: with HikariCP capped at 5, background workers must not starve request threads, and each worker acquires/releases a connection **per micro-batch** (never across an external call).
- **Idempotency key per asset** so a re-enqueue or double-fire can't double-process; a terminal **`FAILED`-after-N-attempts** state acts as the dead-letter queue.
- **Batch Voyage text embeddings** (multiple inputs per request — real client change: parse all `data[]` entries and map back, respect per-batch token limits); image multimodal stays 1-per.
- A `Semaphore`/Guava `RateLimiter` guards Claude and Voyage bursts.
- The existing `EmbeddingReconciliationJob` is the idempotent retry safety net (`status = FAILED`/`PROCESSING` re-picked).
- Asset flips to `READY` only after metadata + required embeddings are stored.

This is an evolution of the existing status model, not a new subsystem — and it's the headline "how we handle scale" result (report measured throughput).

### 5.3 Hybrid Search — two-stage retrieve-then-rank (UC-4.5)

```
prompt
  │
  ├─ Stage 1: RETRIEVE (cheap, wide — ~100 candidates)
  │     ├─ lexical:  Postgres tsvector full-text  ──┐
  │     ├─ dense:    pgvector ANN (correct space)  ─┤── Reciprocal Rank Fusion ─► candidate set
  │     └─ if temporal/structured: Claude parses {date_range, time_of_day (UTC+8→UTC), category, type}
  │                                 applied as over-fetch + post-filter (NOT a pre-filter into the ANN)
  │
  └─ Stage 2: RANK (rich features on the small set)
        transparent scoring fn (semantic + category + tags + recency + light performance_score) ─► top 8
```

Correctness rules:
- **Match the embedding space:** text query → `voyage-multimodal-3.5` to search `image` vectors, `voyage-4-lite` to search `semantic` vectors. Never cross spaces.
- **Don't pre-filter into the ANN index** — pgvector's filtered-ANN recall collapses. Over-fetch a wide candidate set, then apply SQL filters; or use pgvector ≥0.8 iterative scan / partial indexes if recall is still short.
- **Role/tenant filter** applied during retrieval (administrator = network, validator = institution, contributor = own), consistent with RLS and the permission filter pattern in `ai-media-library-upgrade.md`.

### 5.4 Insights Sync + Feedback Loop (UC-4.8 / UC-4.9)

- `FacebookInsightsSyncJob` (`@Scheduled`, idempotent): for each published submission with a non-null `submissions.platform_post_id`, GET `/{post-id}?fields=reactions.summary(true),comments.summary(true),shares` (+ `/insights` if `read_insights` granted) → insert a `facebook_post_metrics` row. Commit before/after the external call — never hold a connection across it.
- A periodic `AssetPerformanceJob` rolls metrics up to `media_assets.performance_score`, which `AIRecommendationService` adds as a re-rank signal. Cheap, idempotent, fully inside existing patterns.

### 5.5 Pre-Submit Advisor (UC-4.10)

`SubmissionAdvisorService.advise(submissionId)`: read aggregated `facebook_post_metrics` for the institution/category → build a stats blob → one Claude call combining stats + draft → return structured suggestions DTO. Heuristic-only when data is sparse. Read-only, best-effort, never blocks submit.

---

## 6. External Constraints & Honest Risks

| Risk | Reality | Mitigation |
|---|---|---|
| **Meta App Review** for `read_insights` (reach/impressions) | `pages_read_engagement` (reactions/comments/shares) is *already* in your permission set. Reach/impressions need `read_insights` + review for public use. | In **Development mode**, admins/devs/testers of the app can read insights for pages they manage — sufficient for the capstone on the DASIG page. Build engagement metrics first; treat reach/impressions as "best effort / future." State the review gate explicitly (consistent with the proposal's existing Dev-mode limitation). |
| Insight metric deprecation | Meta periodically deprecates granular insight metrics. | Reactions/comments/shares are stable; degrade gracefully on missing metrics. |
| AI cost / rate limits | More Claude + Voyage calls. | Bounded queue, batched embeddings, caching, per-institution budget, metadata-only fallback. |
| Render / Supabase free-tier | Memory + 5-connection pool. | Bounded queue is exactly the fix; never hold connections across external calls. |
| Advisor cold-start | Sparse history early. | Heuristics first, Claude phrasing on top; don't promise sharp insight from few posts. |

---

## 7. Suggested Phasing

| Phase | Items | Effort | Risk |
|---|---|---|---|
| 1 | Folders/albums (UC-4.1) + bounded ingestion queue (UC-4.2 infra) | Med | Low |
| 2 | AI auto-grouping + quality/dup filtering (UC-4.2/4.4) + curation (UC-4.3) | Med | Low |
| 3 | NL/hybrid search (UC-4.5) + AI feedback loop (UC-4.6) | Med | Low |
| 4 | Caption prompt mode + best-N (UC-4.7); consent/safety flags; media audit trail (UC-4.11) | Low | Low |
| 5 | Facebook insights sync + engagement analytics (UC-4.8) | Med | **Med (App Review)** |
| 6 | Engagement→media ranking (UC-4.9) + pre-submit advisor (UC-4.10) | Med | Med (cold-start) |
| — | Collage builder (client-side canvas) | Low–Med | Low — slot anywhere |

---

## 8. Explicitly Out of Scope (gold-plating cut first)

- Scheduled/emailed stakeholder **PDF reports** (CSV export already exists) — first to drop.
- AI-generated images, autonomous publishing decisions (unchanged from Capstone 1).
- Cross-platform publishing beyond the DASIG Facebook page (unchanged).
- Full public Facebook visibility / Meta Business Verification (organizational decision, no code change required — unchanged).

---

## 9. Evaluation & Measurement

Every `Target:` in §4 is only defensible if it is measured against a fixed, pre-built dataset. This section defines those datasets and the methodology. **Build the datasets before building the features** — they double as regression fixtures and as the source of the numbers reported at defense. All sets are versioned in-repo (`docs/eval/`) so results are reproducible.

### 9.1 Measurement principles

- **Freeze the dataset before tuning.** Label the duplicate pairs and write the golden queries *first*; tune thresholds/weights against them; never relabel to flatter a result. Report the dataset version with every number.
- **Separate "did the model run" from "was it right."** Pipeline-health metrics (throughput, dropped connections, FAILED rate) are deterministic and reported from logs/DB. Quality metrics (precision, relevance, acceptance) come from the labeled sets and from `ai_interaction_log` (UC-4.6).
- **Acceptance ≠ accuracy.** A user clicking "apply caption" is an *acceptance* signal, not ground truth. Report both, and never present acceptance as precision.
- **Small samples get honest treatment.** Where n is small (advisor cold-start, per-asset performance), report n alongside the metric and apply the same empirical-Bayes shrinkage used in UC-4.9/4.10 — do not quote a ratio computed from a handful of events as if it were stable.

### 9.2 Datasets (built once, versioned in `docs/eval/`)

| ID | Dataset | Size | Used by | How built |
|----|---------|------|---------|-----------|
| **D1** | Labeled duplicate-pair set | ≥50 image pairs, each labeled `exact` / `near` / `distinct-same-event` / `unrelated` | UC-4.4 | Hand-label from a real event dump; deliberately include hard negatives (different shots of the same scene) to prove pHash ≠ embeddings |
| **D2** | Search golden set | 20 queries, each with a hand-marked relevant-asset list | UC-4.5 | Mix of semantic ("people at a hackathon"), keyword (asset code / proper noun), and temporal ("uploaded last Monday 3–5pm") queries over a fixed seeded library |
| **D3** | Grouping-usefulness survey | ≥10 contributors, the 200-asset batch (D5) | UC-4.2, UC-4.3 | Show auto-suggested albums; record % who rate them "useful"; record % of assets that end up with a human-confirmed/edited tag |
| **D4** | Caption acceptance log | rolling, from `ai_interaction_log` | UC-4.7 | Measured live: `action_taken='applied'` over total caption suggestions, on >4-image submissions |
| **D5** | 200-asset load batch | exactly 200 assets | UC-4.2 (§5.2) | A single real-or-synthetic event dump used for the throughput + zero-dropped-connection run |
| **D6** | Advisor usefulness survey | ≥10 contributors | UC-4.10 | Post-submit thumbs-rating of advisor suggestions |

### 9.3 Per-target methodology

| UC | Target (from §4) | Metric & formula | Source |
|----|------------------|------------------|--------|
| 4.2 | Ingest 200 assets, **0 dropped DB connections**, HikariCP ≤5 | wall-clock to all-`READY`; max concurrent Hikari connections (Hikari metrics); count of connection-acquisition timeouts (must be 0); `FAILED`-after-retry count | D5 + Hikari/JVM logs |
| 4.2/4.3 | ≥70% find groupings useful; ≥80% assets carry a human tag | survey % ; `COUNT(assets with ≥1 manual/edited tag) / COUNT(assets)` | D3 |
| 4.4 | ≥90% **precision and recall** on exact+near dup | precision = TP/(TP+FP), recall = TP/(TP+FN) over D1 at the chosen Hamming threshold; report the precision-recall curve across thresholds (start ≤6) | D1 |
| 4.5 | relevant result in **top-3 for ≥70%** of queries | Recall@3 = (queries with ≥1 relevant in top 3) / 20; also report MRR and Recall@8 | D2 |
| 4.6 | instrument all 4 AI surfaces; report precision/acceptance | per surface: acceptance = `applied / (applied+dismissed)`; thumbs = `up/(up+down)` | `ai_interaction_log` (post-V28 schema) |
| 4.7 | maintain ≥60% caption acceptance with >4 images | acceptance on the >4-image subset vs. baseline | D4 |
| 4.8 | metrics for ≥95% of published posts refreshed in-window | `COUNT(posts with a metrics row newer than window) / COUNT(published posts with platform_post_id)` | `facebook_post_metrics` join `submissions` |
| 4.9 | boosted assets rise in rank; score shrinkage-stable | A/B the same query with/without `performance_score`; show rank delta; plot score vs. sample size to show shrinkage damps low-n volatility | seeded library + `AssetPerformanceJob` output |
| 4.10 | ≥60% rate advisor "useful" | survey % ; separately report what fraction of advisor claims were heuristic vs. statistically-backed (sample-size gated) | D6 |
| 4.11 | 100% of state-changing media ops audited; history reconstructable | `COUNT(media ops with an audit_log row) / COUNT(media ops)` = 1.0; spot-check that an asset's full lifecycle replays from `audit_log` | `audit_log` filtered to media entity types |

### 9.4 What "good" looks like at defense

A single results table: dataset version, the §4 target, the measured number, and pass/fail. The two anchor results to lead with are **(a) the 200-asset throughput run with zero dropped connections** (proves the scalability thesis) and **(b) the top-3 search relevance %** (proves the AI thesis). Everything else is supporting evidence for the closed-loop narrative in §1.

---

## 10. Digital Media Repository — Maturity & Gap Audit

> **Why this section exists:** the panel emphasized this is a *digital* media repository, not merely a media repository. The distinction is lifecycle management — an asset is *described, governed, discoverable, traceable, and preserved*, not just stored. This audit was **verified against the code on 2026-05-30** (`media_assets` schema V4/V22/V23/V25, `MediaAsset` entity, `MediaAssetService`, `MediaAssetController`, `MediaAssetRetentionService`, audit-log usage). Legend: ✅ built · 🟡 planned in Capstone 2 (UC-4.x) · 🔴 missing **and** unplanned.

### 10.1 Plain media repository vs. digital media repository

| Pillar | Plain repository | Digital media repository |
|---|---|---|
| Core unit | A file | A managed asset = file + metadata + state |
| Metadata | filename, size, type | descriptive + technical + administrative + structural |
| Identity | filename / path | persistent identifier independent of filename |
| Discoverability | browse / filename search | meaning-based search, facets, controlled vocab |
| Curation | none | ingest → describe → review → organize lifecycle |
| Governance / rights | all-or-nothing access | per-asset access, consent/clearance, visibility |
| Provenance | none | who did what, when; what AI inferred and when |
| Preservation | files until manual delete | retention, integrity, controlled disposal, versioning |
| Quality | stores anything | duplicate detection, quality signals |

**One line:** a media repository stores files; a digital media repository manages assets across their lifecycle.

### 10.2 Pillar-by-pillar status (verified)

| Pillar | Status | Evidence / what's missing |
|---|---|---|
| Persistent identity | ✅ | `asset_code` `VARCHAR(50) UNIQUE`, generated + collision-checked (`generateAssetCode()`) |
| Descriptive metadata (AI) | ✅ | `ai_category`, `ai_description`, `ai_tags[]`, `asset_type`, `visible_objects[]`, `specific_subjects[]`, `visual_style[]`, `dominant_colors[]`, `possible_use_cases[]`, `ai_confidence` |
| Descriptive metadata (manual) | 🟡 | Manual **tags** only (`asset_tags`, `source` manual/ai). No human-confirmable **title** (no `title` column); no editable description/category |
| Technical / administrative metadata | ✅ | `file_type`, `file_size_bytes`, `storage_url`, `uploader_id`, `institution_id`, `created_at` |
| Discoverability (semantic) | ✅ | `media_asset_embeddings` dual image/semantic 1024-dim, pgvector HNSW, AI suggestions; trigram filename/code search (V16) |
| Discoverability (NL / temporal / hybrid) | 🟡 | UC-4.5 — tsvector lexical + RRF + Claude temporal parse all unbuilt |
| Lifecycle state | ✅ | `status` PROCESSING/READY/FAILED/DELETED |
| Preservation / disposal | ✅ | soft-delete (`deleted_at`, `deleted_by_user_id`), retention purge (`purged_at`, `MediaAssetRetentionService`, `MediaAssetRetentionPurgeJob`, 30-day) |
| AI provenance | ✅ | `ai_classified_at`, `ai_classification_model`, `embedding_generated_at`, `embedding_model`, `reclassified_at`, `asset_tags.source` |
| Tenant governance (RLS) | ✅ | RLS on `media_assets`, `asset_tags`, `media_asset_embeddings`, institution-scoped |
| Curation review (human-confirmed) | 🟡 | UC-4.3 — no `curated_at`, no "human reviewed" flag, no title/description edit. Today: add/remove tags only |
| Organization (folders / albums / batches) | 🟡 | UC-4.1/4.2 — no `folder_id`, `media_folders`, `import_batch_id`. Library is a flat list |
| Quality / duplicate management | 🟡 | UC-4.4 — no `perceptual_hash`, `blur_score`, `duplicate_of_id` |
| Rights / consent governance | 🟡 | UC-4.x — no `visibility` (internal_only/cleared_for_public), no `safety_verdict`. **Highest-value gap for a government system holding minors' photos** |
| **Media audit trail** | 🟡 | **Now scoped as UC-4.11.** `AuditLogService` is **not yet** called by `MediaAssetService`/`MediaAssetRetentionService`; UC-4.11 wires it into upload/edit/tag/delete/restore/purge/visibility/reclassify. Reuses existing `AuditLog`, no new table |
| **Versioning / lineage** | 🔴 | No version chain or derived-asset lineage (e.g., collage → child asset). Not planned — decide whether to scope a lightweight lineage story |
| Metadata-standard mapping (Dublin Core/PREMIS) | 🔴 | Purely conceptual; nothing maps fields to a recognized schema. Cheap to *articulate* even if not enforced |

### 10.3 Shortest path to defensibly "digital"

The system already sits well past plain storage (persistent IDs, rich AI metadata, semantic search, lifecycle + retention, RLS) but the **governance** and **human-curation** pillars are mostly unbuilt. Three changes convert it from "managed storage" to "governed digital repository" — prioritize these:

1. **Per-asset `visibility` / consent gate** (🟡 UC-4.x) — the single most panel-relevant feature for a government page. Backfill existing assets to `cleared_for_public` (see §5.1 backfill note); new uploads default `internal_only`.
2. **Media events in the audit log** (🟡 now UC-4.11) — wire `AuditLogService` into upload/delete/purge/tag/reclassify. Low effort, closes the provenance gap, reuses existing infrastructure.
3. **Human-confirmable `title` + `curated_at` flag** (🟡 UC-4.3) — lets the repository show *human-reviewed* metadata, not just AI guesses; directly supports the UC-4.3 ≥80% curated-tag target.

Optional defense polish (low/no code): name the feature "Digital Media Repository" in the UI, and state the Dublin Core mapping (`title`/`creator`/`date`/`subject`/`rights` ↔ `title`/`uploader`/`created_at`/`ai_tags`/`visibility`).

---

*Foundations reused: `media_asset_embeddings` (image + semantic, 1024-dim), `ClaudeVisionClient` classification, `VoyageAIClient` (`voyage-4-lite` text + `voyage-multimodal-3.5` image), `AiInteractionLog`, `publication_attempts`, the `PROCESSING→READY→FAILED` status model, and `EmbeddingReconciliationJob`. The published Facebook post ID is `submissions.platform_post_id` (V1), and lexical search currently relies on `pg_trgm` (V16), not `tsvector`. Media operations are **not** currently written to `AuditLog` — UC-4.11 closes this gap by reusing the existing audit infrastructure (see §10.2).*

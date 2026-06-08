# Capstone 2 - Phase 3 Implementation Plan

> **Phase 3 scope:** Natural-language / hybrid media search (UC-4.5) + AI feedback loop (UC-4.6).
> **Builds on:** Phase 1 folders/collections, Phase 2 import batches, curation, dual embeddings, and prompt-based collection review.
> **Current status (2026-06-06):** Phase 3 implemented locally end-to-end. 3A-3D backend
> (search-document migration, lexical + semantic + multimodal retrieval, RRF + deterministic
> re-rank behind `/api/v1/media-assets/search`) plus a **strict relevance cutoff** so unrelated
> photos no longer pad results. 3E frontend search experience with a **Google-style related-search
> autocomplete** (recent, AI tags, Claude keywords/description snippets, filenames, uploaders,
> collections, folders, smart phrases) over a backend autocomplete endpoint; the Collection search
> reuses the same hybrid lifecycle. 3F search feedback logging is wired (`/ai/feedback/search`).
> D2 golden-set re-run after the strict cutoff: **Recall@3 = MRR = Recall@8 = 1.000**.
> **Phase 3 is complete; Phase 4 (UC-4.7, visibility gate, UC-4.11), Phase 5 (UC-4.8 Facebook
> insights), and Phase 7 are also implemented. The next unstarted phase is Phase 6 (UC-4.9/4.10).**

---

## 1. Research Finding

The best-fit architecture for DASIGConnect photo search is not "vector search only" and not
"keyword search only." The evidence points to a hybrid retrieve-then-rank system:

1. **Lexical retrieval** for exact matches: asset code, event names, people/institution names,
   filenames, manual tags, and user-confirmed titles.
2. **Semantic text retrieval** for meaning: prompt text embedded into the same semantic vector
   space as AI descriptions/tags/use-cases.
3. **Cross-modal photo retrieval** for visual intent: prompt text embedded using the same
   multimodal model family used for image document vectors, so text prompts can retrieve photos.
4. **Rank fusion** using Reciprocal Rank Fusion (RRF), because full-text scores and vector
   scores have different scales and should not be naively added.
5. **Deterministic re-ranking** over the fused candidate set using tenant-safe metadata:
   manual tag boost, title match, AI category/use-case match, recency, duplicate/quality gates,
   and later a small performance-score tie-breaker.

Primary sources checked:

- Voyage AI multimodal embeddings support text and image/video inputs in a shared retrieval
  workflow, with explicit `input_type = query | document` guidance:
  <https://docs.voyageai.com/docs/multimodal-embeddings>
- Supabase recommends hybrid search by combining full-text search and semantic search, then
  fusing results with RRF:
  <https://supabase.com/docs/guides/ai/hybrid-search>
- pgvector supports cosine distance (`<=>`), cosine similarity as `1 - distance`, and HNSW
  indexes using `vector_cosine_ops`:
  <https://github.com/pgvector/pgvector/blob/master/README.md>
- pgvector warns that approximate-index filtering is applied after index scan; narrow filters
  can reduce returned matches unless over-fetching, iterative scans, partial indexes, or exact
  filter indexes are used:
  <https://github.com/pgvector/pgvector/blob/master/README.md#filtering>
- PostgreSQL full-text search supports web-style user queries via `websearch_to_tsquery` and
  ranked results via `ts_rank` / `ts_rank_cd`:
  <https://www.postgresql.org/docs/17/textsearch-controls.html>
- RRF was originally proposed as a simple rank-combination method that can outperform
  individual systems; modern search platforms also use `1 / (rank + k)` fusion:
  <https://colab.ws/articles/10.1145/1571941.1572114> and
  <https://learn.microsoft.com/en-us/azure/search/hybrid-search-ranking>

## 2. Selected Architecture

```text
User query
  |
  v
Stage 0 - intent router
  - Parse obvious filters in code: date, time range, media type, category, institution scope.
  - Remove filler words from semantic prompt: "find", "show", "photos", "media", etc.
  - Escalate to Claude only for complex multi-condition natural language.
  |
  v
Stage 1 - parallel candidate retrieval
  - Full-text: title + file_name + asset_code + manual tags + AI tags + AI description + search_keywords.
  - Semantic vector: Voyage text query embedding -> semantic/document embedding rows.
  - Cross-modal vector: Voyage multimodal text query embedding -> image embedding rows.
  - Optional trigram fallback for partial filenames/codes while tsvector coverage is incomplete.
  |
  v
Stage 2 - RRF fusion
  - Merge lexical_rank, semantic_rank, image_rank, and trigram_rank.
  - Use RRF instead of raw score addition.
  - Starting value: rrf_k = 60; tune on D2.
  |
  v
Stage 3 - deterministic re-rank and explanation
  - Boost manual tags/title over AI tags.
  - Apply duplicate/quality gates.
  - Add recency and category/use-case match.
  - Emit match reasons for UI transparency.
  |
  v
Top results + feedback controls
```

## 3. Vertical Slices

1. **Phase 3A - Search index migration** - started locally
   - Add a generated/stored search document or `tsvector` field for media assets.
   - Include title, filename, asset code, institution name, uploader email, manual tags, AI tags,
     AI description, category, asset type, subjects, visible objects, and possible use cases.
   - Add a GIN index for full-text search.
   - Keep existing trigram filename/code search as fallback.

2. **Phase 3B - Backend hybrid search endpoint** - implemented locally
   - Add `POST /api/v1/media-assets/search` or extend the existing list endpoint only if the
     contract stays clean.
   - Return DTOs only: asset summary, normalized score, source ranks, confidence, and match reasons.
   - Enforce tenant scope in every retrieval branch.
   - Do not hold a DB connection while calling Voyage or Claude.

3. **Phase 3C - Query embedding and retrieval** - implemented locally
   - Use Voyage text embeddings for semantic text metadata search.
   - Use Voyage multimodal query embeddings for text-to-photo visual search.
   - Never compare vectors across incompatible spaces.
   - Over-fetch per branch, then filter/re-rank.

4. **Phase 3D - RRF + deterministic re-ranker** - implemented locally as first pass
   - Fuse lexical, semantic, image, and trigram candidate lists using RRF.
   - Re-rank the fused top set with transparent local features.
   - Return match reasons such as `Title match`, `Manual tag`, `Visual similarity`,
     `AI description`, `Uploaded date`, or `Asset code`.

   Current implementation notes:
   - `/api/v1/media-assets/search` calls Voyage before database retrieval and uses
     `Propagation.NOT_SUPPORTED`, so no request transaction holds a connection during external API calls.
   - Lexical candidates come from `media_asset_search_documents`.
   - Semantic candidates search `media_asset_embeddings` rows with `embedding_type = semantic`.
   - Visual candidates search `media_asset_embeddings` rows with `embedding_type = image` using a
     multimodal text query embedding.
   - RRF currently uses `k = 60`, then applies small deterministic boosts for title, asset code,
     filename, category/tags, and AI description/object/use-case matches.
   - If Voyage is unavailable or unconfigured, the endpoint falls back to lexical search instead
     of returning "no matching media."
   - **Strict relevance cutoff (2026-06-06):** vector hits below a minimum cosine similarity are
     dropped before fusion, so cosine nearest-neighbor can no longer pad results with unrelated
     photos. A result survives only via a genuine lexical match or a sufficiently-similar
     semantic/visual match; otherwise the search returns no results. Tunable:
     `app.search.semantic-min-similarity` (0.45), `app.search.image-min-similarity` (0.22).
     D2 verified Recall@3/MRR/Recall@8 stay 1.0 while low-relevance queries return only their
     genuine matches.

5. **Phase 3E - Frontend search experience** - implemented locally
   - Search box in Media Library that supports natural-language prompts.
   - Keep filters visible and predictable: institution, media type, date, category/tags.
   - Results should remain asset-grid based, with search explanations and feedback controls.
   - Reuse the existing right-side asset detail panel; do not introduce another takeover layout.
   - **Related-search autocomplete (Google-style):** typing the search bar shows a ranked
     dropdown of real suggestions (recent searches, AI tags, filenames, uploaders, collections,
     folders) plus real NL "smart phrases". Keyboard nav (↑/↓/Enter/Esc), outside-click close,
     250 ms debounce, ≤8 deduped results. Reusable `FilterBar` owns it; shared
     `SearchSuggestionsDropdown`, `useSearchSuggestions`/`useRemoteSearchSuggestions`,
     `useDebouncedValue`, and `recentSearches` (localStorage). The Collection reuses the same
     dropdown over its own assets (client-side corpus).
   - **Backend autocomplete endpoint:** `GET /api/v1/media-assets/search/suggestions?q=&scope=&institutionId=&limit=`
     returns whole-library, tenant-scoped suggestions via one read-only
     `MediaAssetSearchRepository.suggest` UNION query; ranked exact→starts-with→contains,
     de-duped in `MediaAssetSearchService.suggest`. No external calls, no migration. The Media Library
     uses this endpoint; the Collection uses the client-side corpus.
   - **Suggestion sources (all real data, no fabricated rows):** manual/AI tags + `ai_category`
     (`tag`), Claude-detected subjects/objects/use-cases + `ai_tags` array (`keyword`), short
     `ai_description` snippets — a ~6-word window around the match, not the whole sentence
     (`description`), filenames (`file`), uploader emails (`uploader`), collections (`collection`),
     folders (`folder`), recent searches (`recent`), and NL smart phrases (`phrase`). A gated
     real-Supabase smoke test (`MediaSearchSuggestionsSmokeTest`, `-Dsmoke.suggest=true`) covers
     the native query.

6. **Phase 3F - Feedback logging** - implemented locally
   - Extend `ai_interaction_log` or add a scoped feedback table so search is not tied to a
     submission.
   - Capture query hash/text, asset id, rank, action (`viewed`, `selected`, `thumbs_up`,
     `thumbs_down`, `added_to_collection`, `used_in_post`), and role/institution scope.
   - Feed metrics into D2 Recall@3/MRR reporting and later ranking tuning.
   - Implemented: `POST /ai/feedback/search` (`AiFeedbackService`); the search-result cards expose
     thumbs up/down (UC-4.6) and an implicit `selected` signal on open. The shared
     `SearchResultMeta` component renders these in both the Media Library and Collection.

## Latest handoff - 2026-06-06

- **Search is now strict.** Vector branches gain a minimum-similarity floor; unrelated photos no
  longer appear when a query is run. D2 re-run: Recall@3 = MRR = Recall@8 = 1.000; low-relevance
  queries return only genuine matches (e.g. "OWASP Juice Shop" -> 2 results, not a padded page).
- **Related-search autocomplete shipped** on the media search bar (Media Library via backend
  endpoint, Collection via client-side corpus), drawing on the full Claude metadata: tags,
  category, subjects/objects/use-cases, AI tags, and short description snippets, plus filenames,
  uploaders, collections, folders, recent searches, and smart phrases. Keyboard nav, debounce,
  dedupe, ≤8 results.
- **Collections / Media Library UX overhaul** landed alongside: folder CRUD modal + hover
  slide-in actions, reusable list-view metadata columns, collection multi-select mirroring the
  Media Library, collection search reusing the hybrid lifecycle, and the AI Collection Builder
  extracted to a reusable container.
- **Verification:** focused backend tests green; gated real-Supabase smoke test for the suggestion
  query passes; frontend build + targeted ESLint clean. Backend running on the dev project.
- **Commits:** `36864c5` (suggestions slice + overhaul), `0086ea1` (Claude metadata sources),
  `4e9bfe6` (description snippets), `7a89993` (strict relevance cutoff).
- **Next:** Phase 5 (UC-4.8 Facebook engagement insights) is now implemented (insights client/sync/
  job, V40/V41 metrics, content-insights + admin sync endpoint); the next unstarted phase is Phase 6
  (UC-4.9 engagement→ranking + UC-4.10 advisor). Reach/impressions stay best-effort until the
  refreshed Page token / `read_insights` is validated. Optional Phase 3 follow-ups: tune the similarity
  floors / suggestion limits on real data; verify D2 with human (non-AI) labels before defense.

## 4. Implementation Rules

- **Human control stays intact.** Search and prompt-based collection building suggest assets;
  they do not auto-create posts or collections.
- **Manual metadata outranks AI guesses.** Human-confirmed title/tags should carry higher
  re-rank weight than AI tags.
- **RLS plus application scope.** All SQL branches must pass `institutionId` explicitly unless
  the administrator is intentionally using network scope.
- **No cross-space vector comparisons.** Semantic text vectors search semantic rows; multimodal
  query vectors search image/multimodal rows.
- **Over-fetch before filtering.** Avoid filtered-ANN recall loss by retrieving a wider set,
  then applying role/date/type/category filters and deterministic re-rank.
- **Explain every result.** The UI should expose why an asset matched so users can trust or
  reject it quickly.

## 5. Evaluation

Primary metric: **D2 Recall@3 >= 70%** across 20 frozen search queries.

Secondary metrics:

- MRR across D2.
- Recall@8 across D2.
- Median search latency <= 2 seconds for a 1,000-asset institution library.
- Zero cross-tenant results in controller/service tests.
- Search feedback captured for all result actions once UC-4.6 schema is in place.

Before implementation, expand `docs/eval/D2_search_golden_set.csv` from the current seed rows
to 20 labeled queries:

- 8 semantic/cross-modal queries.
- 6 keyword/exact queries.
- 4 temporal/date queries.
- 2 mixed queries requiring structured parse + semantic retrieval.

## 6. Open Decisions

- Whether to implement the full-text document as a generated `tsvector` column on
  `media_assets` or as a materialized/search-side table populated from assets + tags.
- Whether Phase 3 should update the existing `GET /media-assets` contract or introduce a
  separate search endpoint to avoid overloading list/browse behavior.
- Whether to include video keyframe embeddings in Phase 3 or leave video search metadata-only
  until a later slice.

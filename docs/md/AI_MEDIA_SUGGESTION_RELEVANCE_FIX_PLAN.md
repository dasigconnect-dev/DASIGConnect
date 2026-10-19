# AI Media Suggestion Relevance Fix Plan

## Objective

Improve the relevance of AI Media Suggestion results after the structured Gemini metadata phase. Preserve the current UI, endpoint contracts, institution authorization, R2 upload flow, Gemini enrichment, Voyage models, pgvector storage, and background-processing architecture.

## Confirmed Problems

1. Metadata fallback assigns every candidate a synthetic semantic score of `0.35`. A same-format boost can raise an unrelated asset above the `0.40` result threshold.
2. The result threshold is applied after format, metadata, freshness, and reuse boosts. Auxiliary signals can therefore make a weak visual or semantic match appear relevant.
3. Ranking helpers still search mostly legacy category and description fields. The structured V2 fields are persisted and embedded but are underused by deterministic matching, explanations, and diversity.
4. Attached asset IDs are excluded, but another asset with the same content hash can still be recommended.
5. Hybrid ranking remains rollout-controlled. The fix must improve both legacy and hybrid paths without silently enabling a feature flag.

## Phase 1 - Retrieval Correctness

- Require a real visual or semantic similarity signal to meet the relevance floor before auxiliary boosts are applied.
- Remove the artificial fallback semantic score.
- Keep metadata fallback conservative: return only candidates whose real metadata evidence reaches the existing threshold.
- Exclude assets whose content hash duplicates an attached asset.
- Add regression tests for low-similarity candidates, metadata fallback, and duplicate exclusion.

## Phase 2 - Structured Metadata Alignment

- Add V2 type, subtype, purpose, primary subject, topics, setting, activities, objects, detected title, and bounded OCR text to deterministic asset terms.
- Build selected-media query context from normalized persisted metadata rather than legacy fields alone.
- Use V2 fields in match explanations and diversity comparison.
- Add unit tests proving relevant V2 metadata affects ranking without introducing unsupported matches.

## Phase 3 - Evaluation and Rollout

- Run the existing offline ranking evaluator against human-reviewed DASIG examples.
- Compare image-only, semantic-only, hybrid, and reciprocal-rank-fusion strategies using top-result relevance and precision-at-k.
- Keep hybrid rollout flags unchanged until evidence supports enabling them for an institution.
- Document provider-backed measurements separately; deterministic unit tests do not prove real-world accuracy.

## Out of Scope

- Media Library or submission UI redesign.
- Gemini prompt or metadata schema changes.
- Voyage model, dimensions, or vector column changes.
- R2/storage changes.
- Facebook history import.
- Automatic production backfills.

## Acceptance Criteria

- A candidate cannot pass solely because of format, freshness, reuse, or a fabricated fallback score.
- Exact-content duplicates of selected media are not returned.
- Structured V2 metadata participates in bounded query and ranking evidence.
- Existing institution isolation and authorization remain unchanged.
- Existing endpoint and frontend contracts remain unchanged.
- Focused and full backend tests pass.

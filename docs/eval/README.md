# Capstone 2 Evaluation Datasets

These are the **frozen, versioned datasets** behind every `Target:` in
`docs/md/CAPSTONE2_SCOPE_AND_ARCHITECTURE.md` §9. Build them **before** building the
features they measure — they double as regression fixtures and as the source of the
numbers reported at defense.

## Rules (from §9.1)

- **Freeze before tuning.** Label/define each set first; tune thresholds and weights
  against it; never relabel to flatter a result.
- **Version every result.** When you report a number, cite the dataset file + commit.
- **Acceptance ≠ accuracy.** A user clicking "apply" is acceptance, not ground truth.
- **Report `n` for small samples** and apply the same empirical-Bayes shrinkage used in
  UC-4.9/4.10.

## Datasets

| ID | File | Used by | What it measures |
|----|------|---------|------------------|
| D1 | `D1_duplicate_pairs.csv` | UC-4.4 | precision & recall of exact/near-duplicate detection (pHash) |
| D2 | `D2_search_golden_set.csv` | UC-4.5 | top-3 relevance of NL/hybrid search |
| D3 | survey (not a file) | UC-4.2/4.3 | % who find auto-grouping useful; % assets with a human tag |
| D4 | `ai_interaction_log` (live) | UC-4.7 | caption acceptance on >4-image submissions |
| D5 | the 200-asset load batch | UC-4.2 (§5.2) | throughput + zero dropped DB connections |
| D6 | survey (not a file) | UC-4.10 | % who rate advisor suggestions useful |
| D7 | `D7_preservation_set.csv` | UC-4.12 | checksum coverage, corruption detection, immutable baseline, tenant isolation |

Surveys (D3, D6) are run live; record their results in a dated results file when collected.

## How to fill D1 (duplicate pairs)

1. Take a real event dump (the same one used for D5 is ideal).
2. Pick ≥50 image pairs. **Deliberately include hard negatives** — different shots of the
   same scene/event that are NOT duplicates — to prove pHash ≠ embeddings.
3. Label each pair: `exact` | `near` | `distinct_same_event` | `unrelated`.
4. Leave `hamming_distance` blank until you compute pHash; fill it during tuning to find
   the threshold that maximizes precision AND recall (start ≤6).

## How to fill D2 (search golden set)

1. Seed a fixed library (record which seed/commit).
2. Write 20 queries spread across `semantic`, `keyword`, `temporal`, and `mixed` types.
3. For each, hand-mark the asset codes that are genuinely relevant.
4. Report Recall@3 (primary), plus MRR and Recall@8.

Suggested mix for Phase 3:

- 8 semantic/cross-modal queries, e.g. "students presenting a robotics prototype".
- 6 keyword/exact queries, e.g. asset code, event name, organization acronym, uploader name.
- 4 temporal queries, e.g. "photos uploaded last Monday 3-5pm".
- 2 mixed queries, e.g. "unposted awarding photos from last Monday with people holding certificates".

Freeze D2 before tuning RRF weights or re-ranker boosts.

## How to fill D7 (preservation & governance set)

1. The frozen scenario matrix lives in `D7_preservation_set.csv`: ≥30 assets across two
   institutions and multiple file types, each tagged with a `scenario`
   (`verified` / `mismatch` / `missing` / `error` / `recover` / `repeat_failure`) and its
   `expected_final_status`. **Freeze the matrix before tuning** any integrity cadence/threshold.
2. `MediaIntegrityVerificationD7Test` reads that CSV as the authoritative set, drives the real
   `MediaIntegrityService` + `MediaIntegrityRecordService` against controllable storage, asserts
   the §9.3 (4.12) targets, and writes `D7_results.md`. It is the repeatable regression gate
   (externals stubbed, in the same spirit as the D5 spike).
3. Targets proven: 100% checksum coverage on active assets; 100% detection of controlled
   missing/byte-mismatch/read-error fixtures; **0 expected checksums overwritten after a
   mismatch**; alert dedup on repeated identical failures; and no cross-tenant integrity rows.
4. **Complementary live spot-check (recommended before defense):** plant one genuinely missing
   object and one byte-mutated object in a *disposable* dev bucket, run a manual recheck, and
   confirm detection end-to-end through Supabase Storage. The harness proves the engine logic;
   the live run proves the storage path. Record the live run as a dated note in `D7_results.md`.

# ADR-0001: pHash, not embeddings, for duplicate detection
- Status: Accepted
- Date: 2026-05-30
- Deciders: DASIGConnect team

## Context
UC-4.4 must flag exact and near-duplicate images so the library does not fill with
copies of the same shot. We already compute image embeddings (`media_asset_embeddings`,
1024-dim) for semantic search, so the tempting shortcut is to reuse embedding cosine
similarity for duplicate detection too.

This conflates two different problems:
- **Duplicate detection** = "are these the same image?" (pixel-level near-identity).
- **Semantic similarity** = "are these about the same thing?" (meaning).

Embedding cosine measures the second. Two distinct photos from the same event (same
room, same people, different moment) are semantically near-identical and would be
false-flagged as duplicates — destroying the contributor's distinct shots.

## Decision
Use a **perceptual hash** (pHash/dHash, 64-bit) computed at ingestion, and flag pairs
within a small **Hamming distance** (start ≤6, tuned against dataset D1). Keep this fully
separate from the embedding pipeline. Embedding cosine is reserved for the *distinct*
"visually similar / best-of-burst" grouping feature, where it is the correct tool.

## Consequences
- Cheap, deterministic, explainable — good for a defense ("we can show the exact
  threshold and its precision/recall on D1").
- Requires a hand-rolled pHash over `BufferedImage` (no OpenCV in the stack).
- A new `media_assets.perceptual_hash BIGINT` column (V28+) and `duplicate_of_id`.
- Threshold must be tuned, not hardcoded; D1 must include hard negatives
  (`distinct_same_event`) to validate that pHash does not false-flag them.

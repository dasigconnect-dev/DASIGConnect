# ADR-0005: SHA-256 file fixity is separate from perceptual hash, verified by a bounded job that never overwrites the baseline
- Status: Accepted
- Date: 2026-06-06
- Deciders: DASIGConnect team

## Context
UC-4.12 (Phase 7) turns the Media Library from governed storage into a
preservation-aware repository. DASIG must be able to prove that a stored file is still
**present and byte-for-byte unchanged**, not merely that it still *looks* like the original.

The library already computes `perceptual_hash` for near-duplicate image detection (ADR-0001).
It is tempting to reuse that single signal for "has this file changed?" — but pHash answers a
different question. Two visually identical images (re-encoded, resized, recompressed) collide
under pHash while having completely different bytes; a one-byte corruption that flips a header
or truncates a file is invisible to pHash. Conflating the two would let real corruption pass as
"unchanged" and flag legitimate edits as integrity failures.

Verification also has operational constraints: the bytes live in Supabase Storage, so checking
fixity means downloading each object. HikariCP is capped at **5 connections** (Supabase Session
Pooler) and the project rule is **no DB connection held across an external/storage call**. A
naive "scan everything every night" job would either hold connections during downloads or
stampede storage.

## Decision
Treat **file fixity (SHA-256)** as a first-class signal that is **independent of perceptual
similarity**:

- Store an expected `content_sha256` baseline at ingest (V34), alongside `integrity_status`
  (`PENDING`/`VERIFIED`/`MISMATCH`/`MISSING`/`ERROR`) and an append-only
  `media_asset_integrity_checks` history table. `perceptual_hash` is untouched and keeps its
  duplicate-detection role; the two are **never** used interchangeably.
- The expected checksum is the **ingest baseline and is immutable**. A scheduled check compares
  freshly downloaded bytes against it and records the outcome — it **never rewrites the expected
  value after a mismatch**. Overwriting the baseline would erase the evidence that corruption
  occurred. (DB constraint: `content_sha256 ~ '^[0-9a-f]{64}$'`.)
- Verification runs as a **short, bounded, idempotent job** (`MediaIntegrityVerificationJob`,
  reusing the ADR-0002 discipline): select a small batch of candidate asset IDs in a short
  transaction, **download and stream the SHA-256 with no DB transaction open**, then persist the
  result in a separate short transaction. Hard cap **25 assets/run**, configurable via
  `MEDIA_INTEGRITY_*`. Priority order: never-checked `PENDING`, then due failures, then oldest
  verified.
- A failed download records `MISSING` or `ERROR` — it **never deletes or silently replaces**
  the asset or its baseline.
- Failures drive an operational review lifecycle (V35: `NONE`/`OPEN`/`ACKNOWLEDGED`/`RESOLVED`)
  with validator/admin acknowledgement; alerts fire only when a failure is **new or changed**
  and are de-duplicated for 24 hours so a persistent failure does not spam every run.
- Originals stay immutable: a replacement upload starts a **new asset** rather than overwriting
  the stored object (explicit lineage linkage is deferred to Phase 7E).

## Consequences
- DASIG gets defensible preservation evidence: pHash answers "do these look alike?", SHA-256
  answers "are these the exact same bytes?", and the two can disagree without ambiguity.
- The immutable-baseline rule makes the D7 target measurable: **0 expected checksums silently
  overwritten after a mismatch** is provable from the history table.
- Bounded batching means a large library is verified progressively (throughput vs. coverage is a
  tuning knob via batch size/cadence), not in one storage stampede, and never starves the
  5-connection pool.
- The append-only history table plus the `OPEN/ACKNOWLEDGED/RESOLVED` states give Phase 7C the
  Repository Health aggregates and drill-downs without further schema work.
- Cost accepted: SHA-256 is computed at ingest and on every scheduled recheck (a full streamed
  download each time); for large video objects this is the dominant cost of the job and is the
  reason the batch cap is small and configurable.

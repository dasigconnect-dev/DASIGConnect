# ADR-0006: The rights-clearance gate fires on the visibility transition, grandfathering by construction
- Status: Accepted
- Date: 2026-06-06
- Deciders: DASIGConnect team

## Context
UC-4.12 Phase 7D adds structured rights/consent records (`media_asset_rights`) and a rule:
a **new** asset must have a complete, non-expired rights record before it may be cleared for
public publishing, while **existing** already-public assets stay usable (the standing
grandfathering rule from the visibility rollout, ADR-adjacent V33). The hard question is how to
distinguish "new" from "grandfathered" without a brittle, ever-drifting cutoff.

Two tempting-but-bad options:
- **A created-at / launch-date cutoff.** "Assets created before 2026-06-06 are grandfathered."
  This needs a magic date baked into code, breaks on backfills and data imports, and has to be
  reasoned about forever.
- **A nightly enforcement sweep** that demotes already-cleared assets lacking rights. This
  retroactively breaks the live publishing workflow the plan explicitly says must keep working.

`visibility` (`internal_only` / `cleared_for_public`) is already the fast publishing gate, and
it is changed through exactly one service path (`MediaAssetService.changeVisibility`).

## Decision
Enforce the rights requirement **only on the `→ cleared_for_public` state transition**, not on
the asset's existence or as a background sweep:

- The gate runs in `changeVisibility` and fires only when `newVisibility == cleared_for_public`
  **and** the asset was not already `cleared_for_public`. It loads the asset's rights record and
  rejects with **422** (naming the asset code) unless the record `permitsPublicClearance(today)`
  — complete (named holder, basis ≠ `unknown`) and not expired.
- Because the check is on the transition, an already-cleared asset never re-enters the gate:
  re-saving it, editing metadata, or running any job that doesn't flip visibility leaves it
  untouched. Grandfathering is therefore a **property of the design**, not a dated exception list.
- New uploads default to `internal_only` (existing behavior), so they must pass the gate the
  first time someone clears them — applying the rights requirement progressively, exactly where
  a human makes the publish-readiness decision.
- Validity is a pure function on the rights entity (`isComplete` + `isExpired`), reused verbatim
  by the Repository Health "expiring rights" view and the rights DTO's derived state, so the gate
  and the dashboards can never disagree about what "cleared" means.

## Consequences
- No magic cutoff date, no backfill landmine, no retroactive breakage of the live workflow — the
  three failure modes of the rejected options all disappear.
- The rule is trivially testable: stub the rights lookup and assert 422-on-transition vs.
  pass-with-valid-record (done in `MediaAssetServiceTest`), with no time travel.
- Tradeoff: assets cleared *before* Phase 7D can remain public with no rights record. That is the
  intended grandfathering, but it means "100% of public assets have rights" is **not** guaranteed
  retroactively — only "100% of *newly* cleared assets do." Closing the historical gap is a
  deliberate, separate curation task (surfaced as a Repository Health count), never an automatic
  demotion.
- The gate depends on `changeVisibility` remaining the single clearance path. Any future
  bulk-visibility endpoint must route through the same check.

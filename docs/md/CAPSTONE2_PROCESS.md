# DASIGConnect Capstone 2 — Development Process

> **Status:** Adopted 2026-05-30 for the inter-semester period and Capstone 2.
> **Companion to:** `CAPSTONE2_SCOPE_AND_ARCHITECTURE.md` (the *what*); this is the *how*.

---

## 1. Methodology — Feature-Driven Incremental Delivery (Kanban flow)

**We do not use fixed, time-boxed sprints.** For a 2–3 person student team with
**irregular availability** (exams, other courses, the semester break), velocity-based Scrum
is counter-productive: capacity swings too much to forecast, fixed boxes manufacture
"failed sprint" pressure, and the work has hard dependencies (queue before grouping,
embeddings before search, insights-sync before the feedback loop) that don't chunk into
equal calendar boxes.

Instead we use **Feature-Driven incremental delivery, pulled through a Kanban flow** — the
unit of progress is a **feature/phase**, not a date. We've already done the hard part of
FDD: an overall model + a decomposed feature list (UC-4.1–4.11) + a plan-by-feature (§7).

- **Milestone = a §7 phase, treated as a demoable release.** Each phase ends with something
  we can show the adviser; that is our defense-progress evidence.
- **Board:** `Backlog → To Do → In Progress → Review → Done`, one card per UC or sub-task.
- **WIP limit ≈ 2** in *In Progress* — finish work before starting more.
- **Vertical slices, not horizontal layers.** For each UC, build migration + backend +
  frontend + test *together* so every phase produces a demoable slice — never "all backend
  done, nothing to show."
- **Reviews are pinned to adviser checkpoints** (every ~2–3 weeks when we meet), not to
  arbitrary calendar dates. Those meetings *are* our reviews — an externally-imposed rhythm
  that matches reality.
- **Optional soft heartbeat:** during a full-capacity in-session stretch, we may aim to
  close ~3 cards in ~2 weeks — as a loose target, **not** a committed sprint with velocity
  tracking. Over the break we drop it entirely and just flow.

State this in the revised SRS as *"iterative-incremental, feature-driven delivery
(Kanban-based Agile)."*

### Phase-as-release backbone (from scope §7, dependency-ordered)

| Milestone | Delivers | Gate |
|-----------|----------|------|
| Phase 1 | Folders/albums (UC-4.1) + bounded ingestion queue infra (UC-4.2) | first demoable release |
| Phase 2 | AI auto-grouping + quality/dup filtering (UC-4.2/4.4) + curation (UC-4.3) | — |
| Phase 3 | NL/hybrid search (UC-4.5) + AI feedback loop (UC-4.6) | — |
| Phase 4 | Caption prompt mode + best-N (UC-4.7); consent/safety flags; media audit trail (UC-4.11) | — |
| Phase 5 | Facebook insights sync + engagement analytics (UC-4.8) | **App-Review gated** |
| Phase 6 | Engagement→media ranking (UC-4.9) + pre-submit advisor (UC-4.10) | — |

## 2. Standing — sanctioned advance development

Capstone 1 is **presented and submitted**; its system is the frozen production deliverable.
Module 4 implements the **suggestions the panel gave during that defense** — these are
*requested* improvements, not speculative scope. We are therefore doing **advance
development ahead of any formal revised-document request**, deliberately de-risked so it
is safe and so a future document submission is a formality rather than a rewrite:

- **Isolated environment.** All Capstone 2 work runs against the **dev Supabase project**;
  the submitted production project is never touched (see §3 and ADR-0003). This removes the
  "we might break the submitted system" risk entirely.
- **Docs as the alignment layer.** `CAPSTONE2_SCOPE_AND_ARCHITECTURE.md`,
  this process doc, the ADRs, and the eval specs *are* the design of record. If the
  professor later asks for a revised SRS/SDD, we populate it from these — no big changes,
  because the architecture is already settled and verified against code.
- **Keep docs in sync as we build.** Every feature updates the scope/gap-audit and adds an
  ADR for non-obvious decisions, so the documentation never drifts from the implementation.

### Parallel work to keep moving (any time)

1. **Eval datasets:** build D1 (≥50 labeled duplicate pairs) and D2 (20-query golden set)
   in `docs/eval/` — they double as regression fixtures and defense numbers.
2. **De-risk spikes:** the bounded ingestion queue (§5.2) and hybrid search + RRF (§5.3) —
   the two architecturally risky pieces; the 200-asset queue run is the D5 throughput proof.
3. **External lead time:** if reach/impressions are wanted, start the **Meta App Review for
   `read_insights` early** — its latency is outside our control.

## 3. Environments

- **Production (Capstone 1):** frozen and demoable. No Capstone 2 migrations run here.
- **Development (Capstone 2):** dedicated Supabase project `dasigconnect-dev` + bucket
  `dasigconnect-media-dev`, selected via a `dev` Spring profile / separate `backend/.env`.
  Baseline Flyway at version 0; HikariCP stays at 5. See **ADR-0003**.

## 4. Engineering hygiene (every UC)

> **Mandatory before writing any code — invoke the matching skill first:**
> - **Backend task** (controller, service, repository, Flyway migration, JWT/security,
>   RLS, external API client, scheduler, etc.) → invoke **`/dasigconnect-backend`** first.
> - **Frontend / UI task** (React component, screen, styling, layout, accessibility) →
>   invoke **`/ui-ux-pro-max`** first.
> - A task that touches both → invoke **both** (backend skill for the API slice, UI skill
>   for the screen slice). This is part of every vertical slice.

- **Branch per UC**, PR into the integration branch; keep the mainline releasable.
- **Migrations:** Flyway only, **V28+**, never reuse a version number. Add columns
  nullable-then-backfill; never a default that hides existing READY assets. Watch the
  `visibility` backfill landmine (grandfather existing assets to `cleared_for_public`).
- **RLS** on every new institution-scoped table, in the same migration that creates it.
- **No DB connection held across an external API call** (Claude/Voyage/Facebook).
- **Tests alongside features** — keep the suite green (currently 273 passing).
- **ADRs** for non-obvious decisions in `docs/adr/` (append-only; supersede, don't edit).

## 5. Definition of Done (per card)

- Started from the correct skill (`/dasigconnect-backend` and/or `/ui-ux-pro-max`).
- Code + tests merged via PR; suite green.
- Migration applied cleanly on the dev project; RLS verified.
- Metric instrumented where the UC has a §9 target.
- ADR written if a non-obvious decision was made.
- Scope/handoff docs updated.

## 6. Cadence summary

| When | Mode | Artifact produced |
|------|------|-------------------|
| Now (advance development) | Feature-driven flow on the dev project; advance one §7 phase at a time, vertical slices; keep docs in sync | a demoable release per phase milestone + aligned docs |
| In parallel | Eval datasets, de-risk spikes, Meta App Review | D1/D2 datasets, spike findings, Meta review submitted |
| If a revised document is requested | Populate SRS/SDD from the existing scope/process/ADR/eval docs | revised document with no architectural rewrite |
| Each non-obvious decision | — | a new ADR |

**Rule of thumb:** progress is measured by *"which phase milestone is demoable,"* not by
*"did we hit the 2-week box."*

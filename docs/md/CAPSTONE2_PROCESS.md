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
FDD: an overall model + a decomposed feature list (UC-4.1–4.12) + a plan-by-feature (§7).

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
| Phase 7 | Digital preservation + Repository Health (UC-4.12) | 7A-7C may proceed while Phase 5 is App-Review gated |

### Current handoff - 2026-06-07

- Phases 1-4 are implemented locally on the Capstone 2 dev project.
- Phase 3 delivers hybrid search, strict relevance filtering, related-search autocomplete,
  and search feedback; D2 Recall@3 is recorded as 1.0.
- Phase 4 delivers best-N caption support, visibility/consent gating, submission enforcement,
  and the media audit-history surface. `safety_verdict` remains optional.
- Phase 5 Facebook insights remains the next dependency-ordered phase. `read_insights` has been
  added in Facebook Developers; the backend OAuth flow now requests it, so the next operational
  step is re-authorizing the Page token and confirming the refreshed token includes the scope.
- Phase 5 first implementation slice is in progress: V39 adds `facebook_post_metrics`,
  `FacebookInsightsSyncJob` stores append-only Graph snapshots, and analytics exposes the latest
  engagement summary. Reach/impressions stay null until the refreshed Page token carries
  `read_insights`.
- Phase 6 depends on the engagement data produced by Phase 5.
- Phase 7A-7G are **all implemented** — UC-4.12 is feature complete. V34-V38 cover fixity, the
  integrity review workflow, rights records, lineage, and duplicate review (7C and 7G added no
  migration). New uploads receive asynchronous SHA-256 verification; a bounded daily job backfills
  and rechecks due assets; new/changed failures notify validators and administrators.
- The shared Asset Detail sidebar includes recent check history, manual recheck,
  validator/admin acknowledgement, replacement-upload handoff, an editable rights/consent
  record (`AssetRightsSection`), and an asset lineage panel (`AssetLineageSection`).
- Phase 7C ships the tenant-scoped **Repository Health dashboard** at
  `GET /api/v1/media-repository/health` (one aggregate query + short cache) with metric tiles
  that drill into the Media Library via a `?health=<key>` filter. Eval D7's fixity portion is
  frozen and measured (`D7_results.md`).
- Phase 7D adds structured rights records (`GET|PUT /api/v1/media-assets/{id}/rights`) with a
  clearance gate: a NEW asset cannot be cleared for public use without a complete, non-expired
  rights record; already-cleared assets are grandfathered (ADR-0006).
- Phase 7E adds asset lineage (`GET|POST /api/v1/media-assets/{id}/relations`): originals stay
  immutable and versions/derivatives are separate linked assets; the service rejects self-links,
  cross-institution links, duplicates, and cycles.
- Phase 7F adds the duplicate review workspace (`GET /api/v1/media-duplicates`,
  `POST /api/v1/media-duplicates/{candidateId}/decision`): side-by-side candidates with Hamming
  distance, mark-duplicate/keep-both/not-duplicate, canonical selection, optional tag merge,
  append-only and never auto-deleting. **D1's ≥50-pair human-verified set is still outstanding** —
  this workspace is the tool to produce it; do not fabricate those labels.
- Phase 7G adds the portable inventory export (`GET /api/v1/media-repository/export?format=csv|json`)
  with a documented Dublin Core mapping (`docs/md/dublin-core-mapping.md`), fixity/rights/lineage/
  provenance/lifecycle fields, audited; it omits storage URLs/credentials and is tenant-scoped.
- **UC-4.12 (Phase 7) is feature complete.** Remaining preservation work is non-code: the D1
  human-verified duplicate set and browser E2E after V34-V38 are applied to the dev database. With
  Phase 7 done, the dependency-ordered next code phase is **Phase 5 (UC-4.8 Facebook insights)**.
  `read_insights` is now part of the Facebook app setup; implementation should verify the refreshed
  Page token before syncing reach/impression metrics.

### Media Library UX hardening - 2026-06-07

These are UC-2.2 Media Library polish items added on top of Phase 7, all on
`capstone2-advance-development` and pushed to origin:

- **Full-library browse fix.** The grid only ever showed the first 25 assets (unpaginated fetch
  hitting the list default). `listAllMediaAssets` now pages the endpoint (100/page) so the whole
  library loads; client-side folder/tag filters need the full set. Thousand-scale libraries would
  still want server-side pagination (future).
- **Upload-time duplicate handling.** Drive-style: when a selected filename already exists in the
  current location, an **Upload options** modal (`useUploadOptions` hook + `UploadOptionsModal`)
  asks **Replace** (upload as a `replacement_for` linked new version + soft-delete the superseded
  asset; originals immutable) or **Keep both**. It fires on selection (earliest a browser can see
  files — the OS picker can't be intercepted). A complementary byte-level
  `POST /media-assets/check-duplicates` (SHA-256) endpoint also exists; it depends on the Phase 7A
  `content_sha256` baseline being populated. Near-duplicate (different bytes) still routes through
  perceptual-hash flagging + the 7F workspace.
- **Trash / recovery (UC-2.2).** Soft-delete → 30-day retention → auto-purge is now surfaced as a
  Trash: `GET /media-assets/trash`, `POST /{id}/restore`, `POST /{id}/purge` (`MediaTrashService`).
  Role-scoped (contributor=own, validator=institution, admin=All + per-institution). Any user can
  Restore or Delete-forever items in their trash. Restore is lossless — soft-delete no longer drops
  embeddings (removed only at purge). Trash lives in the Media Library **left sidebar**
  (`FolderSidebar`), not the header.
- **Bulk selection and progress UX.** Any page that supports multi-select now exposes **Select all**
  after the first selected item: Media Library, Collection contents, Trash, Submit Content's library
  picker, AI Suggestions, and the prompt Collection Builder. Upload and delete flows show animated
  busy dots plus live completed/total and remaining counts while files/assets are processed.
- **Delete-all safety for used media.** Media Library and Collection bulk delete now process assets
  one at a time. A normal delete still returns 409 when an asset is referenced by draft, pending,
  in-review, scheduled, or otherwise active submission records. The frontend pauses the queue on that
  asset and asks **Skip and continue** or **Force delete and continue**. Force retries with
  `force=true`, writes the force flag to audit metadata, and warns that affected submissions may show
  missing media, fail validation/publishing, and require replacement. Trash **Delete forever** remains
  an immediate permanent purge path, not a skip/force path, because the item is already outside the
  active Media Library.
- **Shared sub-page top nav.** `MediaSubNav` gives Trash, Duplicate Review, and Repository Health a
  consistent top bar (Back + logo + `Media Library > {page}` breadcrumb + role/avatar); it also
  imports `media-repository.css` so a direct load of a sub-page ships the `--med-*` tokens.
- **App.tsx lint cleanup:** route-driven/auth-bootstrap `setState`-in-effect deferred via
  `queueMicrotask`; useless regex escape removed. 0 errors.

> **Action required before browser testing any of the above:** restart the backend
> (`./mvnw.cmd spring-boot:run`) so Flyway applies **V34-V38** on the dev DB. Until then the new
> endpoints 404 and the three sub-pages show empty/error states inside their (now correct) chrome.

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
4. **Preservation lane:** Phase 7A-7C may run while Meta review is pending because fixity and
   Repository Health depend only on the existing media/storage lifecycle. Build and freeze D7
   before tuning dashboard thresholds or reporting preservation metrics.

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
- **Migrations:** Flyway only; the next free version is currently **V39**. Never reuse a
  version number. Add columns
  nullable-then-backfill; never a default that hides existing READY assets. Watch the
  `visibility` backfill landmine (grandfather existing assets to `cleared_for_public`).
- **RLS** on every new institution-scoped table, in the same migration that creates it.
- **No DB connection held across an external API call** (Claude/Voyage/Facebook).
- **Tests alongside features** — keep the suite green (currently 273 passing).
- **Frontend componentization (no spaghetti):** before adding UI to a page, ask *"is this
  feature, container, or repeated block worth extracting as a reusable component?"* If a
  block has its own state/logic, is reused, or makes the page hard to scan — extract it.
  - Keep **screens/pages as thin orchestrators**: data wiring + composition, not deep markup.
  - Put reusable pieces in `components/` (shared) or the feature's `components/` folder;
    one component = one responsibility; props in, callbacks out (no business logic buried
    in JSX).
  - **No hardcoded/duplicated UI blocks** copy-pasted across pages — lift them into a
    component. No giant single-file screens doing everything.
  - Reuse the existing design tokens/CSS classes; don't re-style ad hoc per page.
  - *Worked example:* `AlbumsScreen.tsx` is a thin orchestrator (data + handlers) composing
    `features/albums/components/` (`AlbumCard`, `AlbumAssetTile`, `CreateAlbumModal`,
    `AssetPickerModal`) with a shared `isImage` helper — follow this shape for new screens.
- **ADRs** for non-obvious decisions in `docs/adr/` (append-only; supersede, don't edit).

## 5. Definition of Done (per card)

- Started from the correct skill (`/dasigconnect-backend` and/or `/ui-ux-pro-max`).
- Frontend: reusable blocks extracted into components; the page stays a thin orchestrator
  (no spaghetti, no hardcoded/duplicated UI blocks).
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

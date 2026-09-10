# UC-1.5 Post Drafting & Templates

**Use Case ID:** UC-1.5

**Use Case Name:** Post Drafting & Templates

**Actor(s):** Contributor, Administrator, Moderator. Any of the three uses the same composer. Administrators and Moderators additionally get a **Posting As** institution selector (`isAdminComposer = role === "moderator" || role === "admin"`); Contributors' posts are always attributed to their own institution.

**Precondition(s):** The actor holds a valid, authenticated ACTIVE session (UC-1.4). A Contributor's session is institution-scoped; an Administrator/Moderator session is network-wide and must pick an institution to post as (defaults to the protected network institution, "DASIG Central Visayas").

## Main Flow

1. The actor opens the composer — either **New Post**, or an existing item from the "My Submissions" list to resume editing (A1) or view its status. The list filters are `drafts` · `action-needed` · `submitted` · `published` · `failed` · `all`.
2. The composer is a three-step wizard: **Media → Post Details → Organize & Schedule**. Forward progress requires the current step's *Required* readiness items to be met; backward navigation is always allowed (A8). Entered data persists across step navigation within the session.

### Step 1 — Media

3. The actor attaches media (device upload, library pick, AI-suggested media, per-asset captions, per-asset watermark opt-out) per UC-1.7. Advancing from Media to Details, when Details is already complete and no draft exists yet, silently creates the draft (see Autosave below).

### Step 2 — Post Details

4. *(Administrator/Moderator only)* A **Posting As** dropdown, defaulting to DASIG Central Visayas. On an unsaved composer the selection switches freely. On a **saved** draft, changing it clears the preferred schedule (the reserved slot is per-institution), keeps the selected media, prompts a confirm if a schedule was set, and requires a re-save to actually move the draft (server-side `SubmissionService.maybeRehomeSubmission`).
5. The actor enters **event title** and **event date**. *(There is no event-category field in the composer — it was removed by design; the backend still serves `lookups.categories` but nothing sets it.)*
6. The actor writes the **caption** directly or via **Suggest Caption** (AI, UC-1.6) with an optional tone/instruction prompt; the AI can reference the attached media and returns 1–3 variants to select, edit, or discard. The caption has a **hard character cap** (`CAPTION_WORD_LIMIT` = 2000) — pasting past it trims with a warning. Caption length within a comfortable range is *also* a non-blocking Recommended readiness check.
7. Highlighting caption text opens a contextual **fancy Unicode styling** dropdown (UC-1.6).
8. The actor adds **hashtags** — suggested pills or free-typed — inserted into the caption at the cursor (or end).
9. The actor may pick a **template** shown beside the form; applying it pre-fills the caption structure and tags and requires at least one media file already attached. See Templates below.

### Step 3 — Organize & Schedule

10. The actor organises media into an **album** and adds media tags (UC-1.7). On submit the backend reconciles the album — freshly uploaded assets are filed into the album resolved from the post's album name, **auto-creating one from the event title if none is set**; library picks keep their own album; all attached assets receive the post's media tags.
11. The actor chooses **Set a Schedule** (default) with peak-hour suggestions (UC-1.8), or **Live Event Fast-Track**, which hides the scheduling fields. Guard-rail behaviour depends on the network switch (see below).
12. The actor may open a **Facebook-style preview** — a persistent centre-panel toggle available on any step, not just Step 3.
13. The actor clicks **Submit** to send for approval (UC-1.9). Submit is disabled while any blocking readiness item is unmet; if only Recommended items remain, Submit is allowed after a non-blocking confirmation showing the readiness score (A7).

## Readiness Checklist (all steps)

A right-panel checklist updates live, and produces a **readiness score out of 100** (Required weighted 75%, Recommended 25%), a **grade** ("Ready to submit" / "Incomplete"), a description line, and a **dial (ReadinessRing)**. Each checklist row is **clickable** — it jumps to the relevant step/field. Required rows show ✓ / ✗; Recommended rows show ✓ / ⚠ with an explanatory sub-line.

**Required (blocking):**
- Event title present
- Event date present
- Caption non-empty
- ≥1 media attachment
- **File requirements** — every attached file within the size limit and an accepted format
- Album assigned
- **Schedule** — *conditional on the guard-rail switch:*
  - **Guard rails ON:** a preferred slot that is in the future, within the 8:00 AM–8:00 PM publish window, and not blocked by a guard-rail conflict. (Fast-Track: no slot needed.)
  - **Guard rails OFF:** a preferred slot is optional; if one is chosen it just can't be in the past. The publish-window and slot-conflict checks are skipped.

**Recommended (non-blocking):**
- Caption length within a comfortable range
- At least one hashtag in the caption
- At least one media tag
- At least one per-media caption
- A template used (or intentionally skipped — always passes for Fast-Track)

## Scheduling Guard Rails (network-wide switch)

Guard rails (±30-minute spacing, ≤6 posts/day network-wide, ≥2h lead time, 8:00 AM–8:00 PM publish window) govern the one shared DASIG publishing calendar. An Administrator toggles enforcement in **Settings → Page → Scheduling Guard Rails**; the switch is stored on the no-institution `page_settings` row and served to the composer via `GET /api/v1/submissions/lookups` (`guardrailsEnforced`). When **on**, a valid future in-window slot is required to submit a Standard post and `SlotReservationService` runs the full guard-rail check. When **off**, a preferred schedule and the publish window are non-blocking, and `SubmissionService.submit` / `SlotReservationService.reserve` skip guard-rail validation — a future date is still enforced if one is set, and Fast-Track is unaffected either way.

## Templates

- **Built-in templates** are hard-coded frontend constants (`postTemplates` — "Event Announcement", etc., with `[EVENT TITLE]`-style placeholder captions), not admin-managed.
- **Custom templates** are personal: `POST /api/v1/post-templates` from **Save as Template** (name, caption, tags, target, category, `sourceSubmissionId`). `GET /post-templates` returns only the caller's own (`ownerUserId`) — templates are not shared across an institution. Name is deduped per owner (409 on conflict); the caller can delete their own via `DELETE /post-templates/{id}`.
- The composer shows both lists together. Applying a template requires ≥1 media file and sets the caption + tags.

## Autosave & explicit save

- **Save as Draft** is a persistent action on every step, independent of Next/Submit gating.
- Once a draft has an id, edits **autosave silently** after a short delay. The *first* save is always explicit (Save Draft, Submit, or advancing to the Media step with Details already complete) — a draft is never auto-created.

## Alternative Flows

- **A1 — Edit Existing Draft:** The composer loads a `draft` at Step 1 with all reached steps accessible by backward navigation.
- **A2 — Discard Draft:** Confirmation prompt, then `DELETE /api/v1/submissions/{id}` — allowed only in `draft` status; also purges orphaned draft uploads and the slot reservation.
- **A3 — Template Not Selected:** The flow proceeds normally; "Template used" simply stays a ⚠ Recommended item.
- **A4 — Draft Returned for Revision:** A `needs_revision` draft reopens with the reviewer's remarks. Remarks are parsed into **per-field comments**; the affected fields pulse, and the actor can mark each field "addressed". Re-submitting from `needs_revision` is allowed (submit accepts `draft` or `needs_revision`). A `rejected` submission shows the rejection reason and is terminal (read-only). *Reviewer remarks exist only for these two outcomes — `requestRevision` sets `validatorRemarks`, `reject` sets `rejectionReason`; approval sets neither.*
- **A5 — Dirty-State Navigation Warning:** Closing or navigating away with unsaved modifications prompts for confirmation.
- **A6 — Incomplete Readiness at Save:** The actor may Save as Draft with unmet Required or Recommended items at any step; the checklist persists as a reminder.
- **A7 — Submit Despite Recommended Warnings:** With all Required items met but Recommended items unmet, Submit is allowed after a non-blocking confirmation showing the readiness score.
- **A8 — Navigate Backward Between Steps:** Always allowed; forward progression requires the current step's Required items. All entered data persists within the session.
- **A9 — Withdraw a Submitted Post:** `POST /api/v1/submissions/{id}/withdraw` returns a still-`pending` submission to `draft` (refused once `in_review`); writes a `SUBMISSION_WITHDRAWN` audit entry. The draft then re-enters this flow.

## Postcondition(s)

A draft post record exists in `draft` state with event title/date, caption, hashtags, attached media (with any per-asset captions and watermark opt-outs), the resolved album + media tags, template metadata (if used), and schedule type (Standard or Fast-Track) — ready for further editing or submission (UC-1.9). A withdrawn or revision-returned submission re-enters this flow in `draft` / `needs_revision`.

---

_Verified against the running code as of 2026-09-10. Primary sources: `frontend/src/features/submission/SubmissionScreen.tsx` + `utils.ts` (`getReadinessChecklist`, `getPreviewValidation`), `SubmissionController` / `SubmissionService` (`create`, `update`, `submit`, `delete`, `withdraw`, media/album reconciliation), `SubmissionLookupsDto`, `PostTemplate*` (entity/controller/service), `GuardRailSettingsService` + `SlotReservationService` + `GuardRailService`, `ValidationService` (`requestRevision`, `reject`), `PageSettings*`, migration `V89__page_settings_guardrails_toggle.sql`._

**Changes from the prior draft of this UC, made this session:**
- Actors: added Moderator (the code gives moderators the full composer and Posting-As selector).
- Removed the "event category" field from Step 2 — it isn't in the composer (decision: not needed).
- Readiness checklist rewritten to the actual 7 Required / 5 Recommended items, plus the previously-undocumented score / grade / dial and clickable rows.
- Schedule readiness/blocking is now explicitly tied to the network guard-rail switch (new this session): future-date always enforced; schedule-required and the 8:00 AM–8:00 PM window only when guard rails are on.
- Documented what the prior draft omitted entirely: personal Save-as-Template + delete, built-in vs custom templates, autosave, withdraw (A9), the media/album reconciliation on submit, the per-field revision UI, and that reviewer remarks appear only on request-revision or reject.

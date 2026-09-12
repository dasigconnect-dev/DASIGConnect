# UC-1.9 Draft Submission

**Use Case ID:** UC-1.9

**Use Case Name:** Draft Submission

**Actor(s):** Contributor, Moderator, Administrator — all three use the same composer (UC-1.5) and the same `submit()` endpoint; there is no separate "Moderator acting as Contributor" mode.

**Precondition(s):** A draft exists in `draft` (or `needs_revision`, for a resubmission — see A3) state, with at least one valid media attachment (UC-1.7) assigned to an album, and — for a Standard (non-Fast-Track) draft — a selected scheduled date/time (UC-1.8).

## Main Flow

1. The actor reviews the completed draft in the composer, including caption, attached media, and scheduled time.
2. The actor clicks **Submit for Approval**. The frontend first re-checks locally that an event title, event date, caption, media, album, and (for Standard drafts) a schedule are all present — missing anything jumps back to the relevant step with an error toast, without a server round-trip.
3. The system performs final server-side validation (`SubmissionService.submit` → `assertContentComplete`): event title, event date, **and album assignment** are checked alongside the caption (non-empty, ≤3000 chars — UC-1.6) and media (≥1 attached asset) the prior draft mentioned. **For Standard drafts only**, the scheduled slot is re-validated against guard rails (conflict prevention, daily cap, lead-time, publish window) — skipped entirely for Fast-Track, which has no slot to check.
4. If validation passes, the draft transitions from `draft` (or `needs_revision`) to **`pending`** — not "PENDING_APPROVAL"; that's not a status value in this codebase (see `SubmissionStatus`) — and further edits are blocked (`update()`/`createSignedUploadUrl()` etc. all reject a non-draft/non-needs_revision status).
5. The system notifies **Moderators** — not Administrators — of the new submission (T-01: in-app notification + Messenger DM to every moderator; no email for this one). Admins are not notified on a standard submission at all.
6. The submission appears in the network-wide **Approval Queue** (UC-2.4 — `ValidationQueueScreen`, reachable by both Moderators and Admins, not Administrator-exclusive), and the actor's own draft list reflects its new `pending` status.

## Alternative Flows

- **A1 — Validation Failure at Submission:** confirmed. Any of the checks in step 3 fails before the status is ever changed or any media is reconciled, so the draft simply **remains** in `draft`/`needs_revision` (there's no draft→pending→draft round-trip) with a specific `400`/`409`/`422` message depending on what failed.
- **A2 — Withdraw Submission:** confirmed, and more specific than described: withdrawal (`SubmissionService.withdraw`) is blocked not just once a Moderator/Admin "begins reviewing" informally, but the instant a `ReviewLock` row exists for the submission (opening it in the queue for review creates the lock) — `409 Conflict`, "This submission is already under review and can no longer be withdrawn." Withdrawing is also only valid from `pending` specifically (not `in_review`) — returns the draft to `draft` and clears `submittedAt`.
- **A3 — Resubmission After Revision:** confirmed. A `needs_revision` submission is editable again (UC-1.5 A4) and resubmits through the exact same `submit()` flow — the state-machine comment in `SubmissionService` literally reads "NEEDS_REVISION → PENDING (re-submit)".
- **A4 — Concurrent Slot Conflict:** confirmed, and now fully wired up (2026-09-13). The backend re-runs `GuardRailService.validate` at submit time and rejects a newly-conflicting slot with `409 Conflict` ("Guard rail violation: …") — confirmed by `submit_blockedGuardRail_returns409`. `handleSubmit`'s catch block now recognizes that specific case (`isConflictError` + a "guard rail" message) and instead of the generic error toast, closes the confirm dialog and jumps the actor back to the **Organize & Schedule** step with a toast telling them to pick a new time — matching "prompts the actor to select a new time" directly. Any other submit-time error (e.g. wrong status) still gets the generic toast.
- **A5 — Live Event Fast-Track Submission:** confirmed, including the fix. Scheduling-slot validation is skipped entirely for Fast-Track (`!fastTrack && …` guards); the immediate high-priority notification (T-11, in-app + email + Messenger) goes to **all Moderators**, not "all Administrators" (`NotificationEventListener.onFastTrackSubmission` → `allModerators()` — wording issue, not a bug). **"Sorted to the top if Fast-Track" is now implemented (2026-09-13)** on both ends: the backend's `findValidationQueue` orders `s.fastTrack DESC, s.scheduledAt ASC NULLS LAST, s.submittedAt ASC`, and `ValidationQueueScreen`'s own re-sort puts `fastTrack` items first ahead of the existing date-based comparator — but only in the active queue view (`filter !== "all"`); the "All" history tab, which mixes in already-resolved submissions, sorts by date only, since prioritizing Fast-Track doesn't mean anything for a resolved item.

## Postcondition(s)

The submission exists in `pending`, visible in the Approval Queue (reachable by Moderators and Admins), with content locked until approved, rejected, or returned for revision, and — for Fast-Track — sorted to the top of the active queue. Confirmed accurate.

---

## Corrections from the prior draft of this UC

- **Actors:** added Administrator — same composer, same endpoint, as UC-1.5 through UC-1.8.
- **Status name:** the actual enum value is `pending`, not `"PENDING_APPROVAL"`.
- **"Administrator" → "Moderator" throughout the notification/queue language.** This use case's Main Flow step 4 ("notifies the Administrator"), A5 ("all Administrators"), and the postcondition/queue-visibility framing all name Administrator where the running code notifies and expects **Moderators** (network-wide reviewers) — Administrators have access to the same queue but aren't the primary notified/reviewing role. Same terminology gap already on record from other UC-1.x docs (moderator was renamed from validator and made network-wide 2026-08-29).
- **Final validation also checks album assignment**, not just caption + media — `assertContentComplete` treats a missing album the same as a missing caption or missing media (UC-1.7's postcondition already requires this; the prior UC-1.9 draft just didn't mention it).
- **A4 and A5's gaps are now fixed (2026-09-13)** — see both sections above. A4: `SubmissionScreen.handleSubmit` now redirects to Schedule on a guard-rail conflict specifically. A5: `SubmissionRepository.findValidationQueue` and `ValidationQueueScreen`'s sort both now put Fast-Track submissions first in the active queue.

---

_Verified against the running code as of 2026-09-13 (A4/A5 fixes landed the same day). Primary sources: `SubmissionService` (`submit`, `withdraw`, `assertContentComplete`, `assertEditableStatus`), `SubmissionStatus` enum, `SubmissionRepository.findValidationQueue` (`ORDER BY s.fastTrack DESC, s.scheduledAt ASC NULLS LAST, s.submittedAt ASC`), `NotificationEventListener` (`onSubmissionPending` / T-01, `onFastTrackSubmission` / T-11, both `allModerators()`), `SubmissionServiceTest` (`submit_blockedGuardRail_returns409`, `submit_withoutCaption_returns422`, `submit_withoutMedia_returns422`, withdraw tests), `frontend/src/features/submission/SubmissionScreen.tsx` (`handleSubmit`'s local pre-checks and the guard-rail-conflict redirect), `frontend/src/features/validation/ValidationQueueScreen.tsx` (the Fast-Track-first queue sort, `isAllMode`-gated)._

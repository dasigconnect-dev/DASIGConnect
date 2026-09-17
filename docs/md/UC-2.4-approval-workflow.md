# UC-2.4 Approval Workflow

**Use Case ID:** UC-2.4

**Use Case Name:** Approval Workflow

**Actor(s):** Moderator, Administrator — both are network-wide roles. `ValidationService`'s own class-level comment names this UC directly: "Returns the network-wide approval queue... Moderator and Admin accounts are both network-wide roles." Not institution-scoped for either role (see Main Flow step 2 correction).

**Precondition(s):** The actor is authenticated with an active session. At least one submission is in `pending` status in the Approval Queue for the flow to have something to act on — confirmed accurate, though the queue itself is defined as `pending ∪ in_review` (see step 1).

## Main Flow

1. The actor navigates to the Approval Queue, structured into **All, Pending, In Review, and Failed** tabs (`QueueFilter = "pending" | "in_review" | "all" | "failed"`) — matches the draft's four-tab claim exactly.
2. **Corrected:** the system displays submissions **network-wide**, not scoped to "the Moderator's assigned institution." `ValidationService.getQueue()` calls `submissionRepository.findValidationQueue()` with zero institution filtering — every Moderator sees every institution's queue, identically to Admin. Moderator became a network-wide, institutionless role project-wide on 2026-08-29; there is no per-institution queue view for either role.
3. The actor opens a submission and clicks Review. `ReviewLockService.acquire()` transitions `pending → in_review` and creates a `ReviewLock` scoped to that reviewer (60-minute TTL, renewed by a keep-alive ping while the panel is open).
4. The Submission Detail panel displays the submitted media, AI tags, submitter information, event details, scheduled publication date/time, and caption — confirmed present. **"Internal notes" is not a real field** — `Submission` has no such column; the closest matches are `description` (contributor-authored context) and `validatorRemarks` (an *output* of Request Revision, not something pre-existing to show before review starts).
5. The actor reviews the content for completeness, accuracy, and appropriateness.
6. The actor may optionally edit allowed submission fields before taking a terminal action (A9/A10 below).
7. The actor then chooses Approve, Request Revision, or Reject.
8. Approve transitions the submission to `scheduled`, or publishes immediately for Live Event Fast-Track submissions (`FastTrackPublishingListener` reacting to `SubmissionApprovedEvent`) — confirmed accurate.
9. Request Revision transitions the submission to `needs_revision` and releases the slot reservation (`slotReservationService.release`) — confirmed accurate.
10. Reject transitions the submission to `rejected` and releases the slot reservation — confirmed accurate.
11. The system records actor identity, action, timestamp, remarks/edits/rejection notes in an immutable `ValidationLog` row (`ValidationAction`: `approved`, `edited`, `media_added`, `needs_revision`, `rejected`, `lock_acquired`, `lock_released`, `lock_expired`) — confirmed accurate, and more granular than the draft implies (media additions get their own distinct audit action, separate from a generic edit).

## Alternative Flows

- **A1 — No Submissions in Queue:** confirmed. Empty state reads "No submissions in this view."
- **A2 — Request Revision Without Valid Remarks:** confirmed. `validateRemarks()` enforces **10–1000 characters**, rejecting with `422` outside that range.
- **A3 — Concurrent Review Attempt:** confirmed. A second reviewer sees "This submission is currently being reviewed by Moderator {email}" and the panel renders read-only until the lock is released or expires.
- **A4 — Review Abandoned:** confirmed exactly. `ReviewLockCleanupJob` (every minute) and explicit `release()` both revert `in_review → pending` only if no terminal action was taken in that session.
- **A5 — Self-Submission Review — wrong as drafted.** The draft frames this as conditional ("allowed only if current governance permits it"). It is **unconditionally blocked**, with no governance toggle anywhere: `ReviewLockService.acquire()` throws `403` the instant `submission.getContributor().getId().equals(caller.userId())`, before a review can even begin — so the submitter can never even acquire the lock, let alone act. `ValidationService.approve()`'s own javadoc confirms this is deliberate and absolute: "Moderators cannot approve their own submissions; another moderator/admin must make the approval decision." **Internal inconsistency worth a cleanup note:** `requestRevision()` and `reject()` still carry a stale comment claiming "self-review is allowed but distinctly flagged in the audit log" — inconsistent with `approve()`'s comment and with actual behavior, since all three actions require holding a lock the submitter can never acquire. The `isSelfReview`/`selfReview` audit-flagging machinery is effectively dead code now, not a live governance mechanism.
- **A6 — Submission Approaching Publish Time Without Review:** confirmed real. `ValidationDeadlineNotificationJob` raises the approaching-deadline warning; `StaleSubmissionDetectorJob` (GR-T9) transitions an unreviewed submission whose scheduled time has passed to `missed_review`.
- **A7 — Media Assets Fail to Load: not implemented as described.** There is no `onError` handling anywhere in the validation feature's frontend, and no dedicated "retry loading / proceed with available content / request revision / reject" recovery flow for a broken media asset. If an image fails to load, the browser just shows it broken; the reviewer's only options are the same three terminal actions available for any submission (no special "proceed with available content" affordance exists).
- **A8 — Failed Submission Handling:** confirmed accurate, and matches the Publishing Mode design documented in CLAUDE.md (2026-09-15): Admin can recover `publish_failed` submissions via manual publish/retry and can change publishing mode in either direction; a Moderator can also retry (same mode) or reschedule a non-Fast-Track item, but cannot change publishing mode or force Scheduled→Live — so "Moderators do not receive admin-only recovery controls" is accurate, though Moderators do have real (non-admin-only) recovery controls of their own, not zero capability here.
- **A9 — Edit Scope and Confirmation — overstated.** Edits are validated against media and schedule rules (guard rails, the 10-media-per-submission cap, A10 severity classification) before save. **"Watermark rules" validation does not exist** — the only watermark-related capability during review is the per-asset skip-watermark toggle itself, which is a setting, not a validation rule being checked.
- **A10 — Edit Audit Trail:** confirmed. `ValidationLog.editDiff` stores the before/after diff; edit severity (`QUIET` / `FLAGGED` / `ADDED_MEDIA`) is classified per the A10 governance tiers.
- **A11 — Contributor Notification of Edit — mechanism differs from the description.** The draft implies the edit indicator lives *inside* the approval notification. It's actually a **separate notification** (`submission_edited_in_review`, fired by `SubmissionEditedDuringReviewEvent`) delivered alongside the base approval notice, not a flag embedded within it. The base "approved and scheduled" notification/email never mentions edits at all on its own. The edit notification's wording varies by severity (`ADDED_MEDIA` / `FLAGGED` / `QUIET`), and only `FLAGGED`/`ADDED_MEDIA` trigger an email (not every edit).

## Postcondition(s)

- **On Approve:** submission is `scheduled` or published immediately (Fast-Track); slot confirmed (non-Fast-Track) or bypassed (Fast-Track); Contributor notified via the base approval notification, plus a separate edit notification if the session included edits; action and any edit diff recorded in `ValidationLog`. Confirmed accurate, modulo the "separate notification, not an indicator within it" correction above.
- **On Request Revision:** submission is `needs_revision`; slot released; Contributor notified with the moderator's remarks (in-app + email); action recorded. Confirmed accurate.
- **On Reject:** submission is `rejected` (terminal); slot released; Contributor notified with reason code + notes (in-app + email); no resubmission of that submission is possible — confirmed, `rejected` has no further status transition anywhere in `ValidationService`.

## Corrections from the prior draft of this UC

- **Moderator scoping is wrong.** Not institution-scoped — network-wide, identical to Admin. See Main Flow step 2.
- **A5's self-review framing is wrong.** Not conditionally governed — unconditionally blocked at lock acquisition, with an inconsistent stale comment on `requestRevision`/`reject` that should be cleaned up (see A5).
- **A7 (media-load-failure recovery) does not exist.** No `onError` handling, no retry/proceed affordance anywhere in this feature's frontend.
- **A9 overstates watermark validation.** No watermark *rule* is checked at edit time — only the skip-watermark toggle exists.
- **A11's mechanism is a separate notification, not an embedded indicator.**
- **Step 4's "internal notes" field doesn't exist** on `Submission`.
- Rejection reason codes, for completeness (not in the draft): `INCOMPLETE_CONTENT`, `INAPPROPRIATE_CONTENT`, `WRONG_FORMAT`, `DUPLICATE_EVENT`, `WRONG_INSTITUTION`, `OTHER` (notes required when `OTHER`).

---

_Verified against the running code as of 2026-09-17. Primary sources: `ValidationService` (`getQueue`, `getHistory`, `approve`, `requestRevision`, `reject`, `isSelfReview`/`assertNotSelfApproval`, `validateRemarks`, `validateRejectionCode`, `VALID_REJECTION_CODES`), `ReviewLockService` (`acquire`, `release`, `releaseExpiredLocks`, `expireLock`), `ValidationAction` enum, `SubmissionStatus` enum, `ValidationDeadlineNotificationJob`, `StaleSubmissionDetectorJob` (GR-T9), `NotificationEventListener` (`onSubmissionApproved`, `onSubmissionRejected`, `onRevisionRequested`, `onSubmissionEditedDuringReview`), `frontend/src/features/validation/ValidationQueueScreen.tsx` (`QueueFilter`, `REVIEWABLE_STATUSES`, the concurrent-lock read-only message, the empty-state copy), and the Publishing Mode design note in CLAUDE.md's Key Design Decisions (2026-09-15, for A8). Not independently re-verified this pass: pixel-exact review panel field layout beyond the fields named in the draft, and A6's exact "priority flag" UI presentation._

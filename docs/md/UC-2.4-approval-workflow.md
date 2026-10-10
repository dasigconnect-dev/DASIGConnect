# Use Case: UC-2.4 Approval Workflow

## Use Case ID
UC-2.4

## Use Case Name
Approval Workflow

## Actor(s)
Administrator, Moderator

## Precondition(s)
The actor is authenticated with an active session. The Validation Queue is defined as the union of pending, in_review, and needs_revision submissions network-wide.

## Main Flow
1. The actor navigates to the **Validation Queue** from the main navigation menu.
2. The screen displays a list of submission cards and a row of eight tabs across the top to filter the view: **All, Pending, In Review, Needs Revision, Scheduled, Published, Rejected, and Failed**.
3. The queue displays submissions from across the entire network (no per-institution scoping for Moderators or Administrators). The actor may use the search bar or sort controls to locate a specific post.
4. The actor clicks on a submission card from the list to open it for review.
5. Behind the scenes, the system acquires a **15-minute review lock** scoped to the actor and transitions the submission from pending to **in_review**.
6. The screen splits into a detailed review interface:
   - **Facebook Post Preview:** A realistic visual preview of how the post will appear on Facebook, showing the attached media, caption, tags, and scheduled time.
   - **Submission Details Panel:** A collapsible sidebar displaying metadata such as the contributor's name, event details, publishing mode (e.g., Scheduled vs. Live Event), and a button to view the full Review History (audit log).
7. The actor reviews the content for completeness, accuracy, and appropriateness. If minor adjustments are needed, the actor can optionally edit the media, caption, or schedule directly within the review interface before making a final decision (A9, A10).
8. The actor selects one of three terminal action buttons:
   - **Approve:** Transitions the submission to scheduled, or publishes it immediately if it is a Live Event Fast-Track submission.
   - **Request Revision:** Opens a dialog prompting for mandatory remarks (10-1000 characters). Transitions the submission to needs_revision and releases its reserved slot.
   - **Reject:** Opens a dialog prompting for a reason code and notes. Transitions the submission to rejected and releases its reserved slot.
9. The system processes the decision, securely logs the action (and any edits made) to the audit log, notifies the original contributor, releases the review lock, and returns the actor to the queue list.

## Technical Flow
1. The actor navigates to the Validation Queue, structured into eight tabs: All, Pending, In Review, Needs Revision, Scheduled, Published, Rejected, and Failed.
2. The system displays submissions network-wide — Moderators and Administrators see the identical queue, with no per-institution scoping for either role.
3. The actor opens a submission and clicks Review. The system transitions it from pending to in_review and acquires a review lock scoped to that reviewer, with a 15-minute duration that renews while the panel remains open.
4. The Submission Detail panel displays the submitted media, AI tags, submitter information, event details, scheduled publication date/time, and caption.
5. The actor reviews the content for completeness, accuracy, and appropriateness.
6. The actor may optionally edit allowed submission fields before taking a terminal action (A9, A10).
7. The actor selects one of three terminal actions: Approve, Request Revision, or Reject.
8. **Approve** transitions the submission to scheduled, or publishes it immediately for Live Event Fast-Track submissions.
9. **Request Revision** transitions the submission to needs_revision and releases the slot reservation.
10. **Reject** transitions the submission to rejected and releases the slot reservation.
11. The system records the actor's identity, action, timestamp, and any remarks, edits, or rejection notes in an immutable log. Edits that add media are recorded as a distinct action from other field edits.

## Alternative Flow(s)
**A1 — No Submissions in Queue:** The system displays an empty state for that view.
**A2 — Request Revision Without Valid Remarks:** Remarks must be between 10 and 1,000 characters; the system rejects anything outside that range.
**A3 — Concurrent Review Attempt:** A second reviewer sees who currently holds the review lock, and the panel renders read-only until the lock is released or expires.
**A4 — Review Abandoned:** If no terminal action is taken within the lock's active period, the submission automatically reverts from in_review to pending and the lock releases.
**A5 — Self-Submission Review:** Unconditionally blocked. A reviewer cannot acquire a review lock on their own submission under any circumstance — another Moderator or Administrator must make the decision. There is no self-approval path, governed or otherwise.
**A6 — Submission Approaching Publish Time Without Review:** The system raises an approaching-deadline warning; if the scheduled time passes without a review decision, the submission automatically transitions to missed_review.
**A7 — Media Asset Load Failure:** If a media asset fails to load within the review panel, the system displays a "failed to load" placeholder with a Retry action that re-requests the asset. The reviewer is not blocked while this is unresolved — Request Revision and Reject remain available throughout.
**A8 — Failed Submission Handling:** An Administrator may recover a publish_failed submission via manual publish, retry, or by changing its publishing mode in either direction. A Moderator may retry (within the same publishing mode) or reschedule a non-Fast-Track submission, but cannot change its publishing mode or convert a Scheduled submission to Fast-Track.
**A9 — Edit Scope and Confirmation:** Edits to media and scheduling are validated against guard rails and the 10-media-per-submission limit before saving. There is no separate watermark validation rule at edit time; the only watermark-related capability available during review is the existing per-asset skip-watermark setting.
**A10 — Edit Audit Trail:** The system stores a before/after diff of any edited fields, classified by severity (Quiet, Flagged, or Added Media), alongside the standard action log entry. This tracking spans the entire review cycle — from the last terminal action up to the next — so an edit is correctly counted even across an interrupted session where the review lock is released and reacquired before a final decision, and correctly resets after each terminal action.
**A11 — Contributor Notification of Edit:** When a submission is edited during review, the Contributor receives a separate notification specific to the edit, in addition to the base approval notification — the base notification itself never mentions edits. The edit notification's wording reflects its severity tier; only Flagged and Added Media edits trigger an email, while a Quiet edit notifies in-app only.

## Postcondition(s)
- **On Approve:** the submission is scheduled, or published immediately for Fast-Track; the slot is confirmed (non-Fast-Track) or bypassed (Fast-Track); the Contributor is notified via the base approval notification, plus a separate edit notification if the review session included edits; the action and any edit diff are recorded.
- **On Request Revision:** the submission is needs_revision; the slot is released; the Contributor is notified with the reviewer's remarks (in-app and email); the action is recorded.
- **On Reject:** the submission is rejected (terminal); the slot is released; the Contributor is notified with the reason code and any notes (in-app and email); no resubmission of that submission is possible.

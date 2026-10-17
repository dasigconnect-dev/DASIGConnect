### Use Case ID
UC-1.9

### Use Case Name
Draft Submission 

### Actor(s)
Contributor, Moderator, Administrator — all three use the same composer (UC-1.5) and the same submission endpoint. 

### Precondition(s)
A submission exists in draft, needs_revision, or rejected state, with at least one valid media attachment assigned to an album. A Standard post must also have a selected future scheduled date/time. Fast-Track posts do not require a scheduled time or slot reservation. 

### Main Flow
1. The actor reviews the completed draft in the composer, where the Submission Gate shows `Required: 7 of 7 ✓ — Ready to submit` (separate from optional `Tips: X of 5 applied`) and promotes **Submit for Approval** as the primary, always-visible action on every screen size.
2. The actor clicks Submit for Approval. The frontend first re-checks locally that all 7 required items (event title, event date, caption, media, file requirements, album, and — for Standard drafts — a schedule) are satisfied — missing anything jumps back to the relevant step/field via the Submission Gate with an error toast, without a server round-trip. Optional quality tips never block submission.
3. The system performs final server-side validation: event title, event date, album assignment, caption presence and length, and at least one attached media asset are required. For Standard drafts, the scheduled slot is re-validated against the enabled guard-rail rules. Conflicting slots, insufficient lead time, out-of-window times, and slots beyond the 30-day horizon are rejected. The six-post daily threshold produces a soft warning and does not block submission. Fast-Track skips scheduled-slot validation entirely.
4. If validation passes, the submission transitions from draft, needs_revision, or rejected to pending, and further edits are blocked. When resubmitting a rejected post, the previous rejection reason is cleared.
5. The system notifies Moderators of the new submission (in-app notification + DM to every Moderator). Administrators are not notified on a standard submission.
6. The submission appears in the network-wide Approval Queue (UC-2.4), and the actor's own draft list reflects its new pending status.

### Alternative Flow(s)
**A1 — Validation Failure at Submission:** Any failed check in step 3 occurs before the status changes or any media is reconciled, so the draft remains in draft/needs_revision with a specific error message describing what failed.

**A2 — Withdraw Submission:** Withdrawal is blocked the instant a review lock exists for the submission (opening it in the queue for review creates the lock), returning a conflict error. Withdrawing successfully sets the submission back to draft status and fires an audit event.

### Postcondition(s)
The submission is locked from further edits, marked as pending, and available to Moderators in the Approval Queue. Its assigned media assets are locked and cannot be deleted from the media library while it is pending. All involved actors' views are updated.

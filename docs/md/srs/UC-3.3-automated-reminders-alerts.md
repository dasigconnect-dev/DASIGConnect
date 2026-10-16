Use Case ID
UC-3.3
Use Case Name
Automated Reminders & Alerts 
Actor(s)
Contributor, Moderator, Administrator. 
Precondition(s)
A triggering event has occurred (submission state change, publishing outcome, token health change, or a scheduling gap). 
Main Flow
1. The system detects a triggering event matching one of the defined notification triggers.
2. The system determines the recipient set for that trigger based on its category, not a single fixed role:
   - Review-related triggers (a new submission awaiting review, a submission returned to the queue) notify every active Moderator and every active Administrator — both roles, together, since either can act on the Approval Queue.
   - Content-outcome triggers (approved, rejected, revision requested, edited, published, publish failed) notify the submitting Contributor.
   - System/infrastructure triggers (token health, background job failures) notify Administrators only, since Moderators have no access to system configuration.
3. The system dispatches the notification via in-app (always), email (for triggers configured to include it), and Facebook Messenger (only for a recipient who has linked their personal account, per the recipient's individual opt-in).
4. The recipient's in-app notification badge updates in real time via a live connection, without requiring a page refresh, and without polling.
5. The recipient may click a notification to navigate directly to the relevant submission, calendar entry, or settings screen.
6. The recipient may view their full notification history, with read and unread state tracked per recipient.
Alternative Flow(s)
- A1 — Email Delivery Failure: If an email fails to send, the system retries independently of the in-app notification, which is dispatched immediately regardless of the email outcome.
- A2 — Notification Read State Sync: Reading a notification through any entry point updates its read state consistently across every active session for that recipient.
- A3 — High-Volume Notification Handling: The system does not currently consolidate a burst of same-type notifications into a single summary; each triggering event generates its own individual notification. This is a known simplification, not a defect — a recipient receiving many notifications of the same type in a short window sees them individually rather than grouped.
- A4 — Facebook Messenger Delivery: For a trigger configured to include Messenger, delivery additionally goes to any recipient in that trigger's set who has personally linked their Facebook account. Messenger delivery is opt-in per individual, not per trigger or per role — two Administrators could have different Messenger delivery outcomes for the identical event, based solely on whether each has linked their own account.
- A5 — Messenger Not Linked: A recipient who has not linked their account is silently skipped for Messenger delivery on that trigger; their in-app and (where applicable) email delivery proceed normally and without error.
Postcondition(s)
Every recipient in the applicable set for a triggering event is notified via in-app delivery at minimum, with email and Messenger delivered according to that trigger's configuration and each individual recipient's Messenger opt-in status. Notification read state is accurate and consistent across all of a recipient's active sessions. 
=========================================
## Implementation Status

- ✅ **Implemented:** Real-time in-app delivery via Server-Sent Events (SSE) with no polling.
- ✅ **Implemented:** Cross-session read state synchronization (A2) via dedicated SSE `read` events.
- ✅ **Implemented:** Messenger and Email dispatch integrations. Messenger correctly skips unlinked users.
- ✅ **Implemented:** Review-related triggers properly fan out to both Moderators and Administrators (Fixed in PR).
- ❌ **Not Implemented:** None.
- ⚠️ **Discrepancies:** None.
- 🔍 **Undocumented Code:** None. 

## Technical Notes & Bug Findings
- **Role Fan-out Bug:** Initially, `onSubmissionPending` and `onFastTrackSubmission` only dispatched notifications to Moderators, missing Administrators completely. A `reviewers()` helper was introduced in `NotificationEventListener` to combine both roles as explicitly mandated by the use case.

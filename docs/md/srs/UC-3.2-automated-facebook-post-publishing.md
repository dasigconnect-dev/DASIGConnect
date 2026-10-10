Use Case ID
UC-3.2
Use Case Name
Automated Facebook Post Publishing 
Actor(s)
System 
Precondition(s)
At least one submission is scheduled with a slot that has arrived, or a Live Event Fast-Track submission has just been approved (UC-2.4). 
Main Flow
1. A background scheduler runs every minute, querying for due submissions whose slot falls within a 5-minute lookback window, accounting for brief server or scheduling delay. A Fast-Track submission bypasses this query entirely (A5).
2. Each due submission is claimed — transitioned to an in-progress publishing state via an atomic database operation — before any external API call is made. If a submission has already been claimed by another scheduler pass, it is skipped. This claim is the actual mechanism preventing the same post from being published twice by two overlapping scheduler runs.
3. The system fetches and decrypts the active Facebook Page token. If expired, see A3.
4. The submission's media is inspected: image-only submissions use the two-step photo path (step 5); video-only submissions use the single-call path (step 6); mixed image-and-video submissions are immediately marked failed with no retry attempted at all.
5. Photo publishing: each image is staged unpublished in a batch sequence. Each image includes its own per-asset caption, if one was set during drafting. Watermarking is resolved and applied immediately before staging, using whatever configuration is active at that instant. Once every image has been successfully staged, a single closing call finalizes the feed post using the submission's main caption and attaches all the staged photos. 
6. Video publishing (single call): the video and its caption are submitted in one call, with no staging step.
7. On success: the submission is marked published (or its direct-post equivalent), the platform's post identifier and publish timestamp are recorded, the Contributor is notified, and the calendar reflects the successful publication.
8. On failure: up to 3 attempts run with increasing delay between them. Exhausting all attempts marks the submission as failed, notifies the Administrator, and makes it available for manual recovery (UC-2.4 A12).
9. A separate job runs every 5 minutes, sweeping for submissions whose scheduled time has passed without being published — see A2 for how this outcome differs depending on the submission's prior state.
Alternative Flow(s)
- A1 — Photo Staging Failure: If any individual image fails during the batch staging process, or if the final feed post call fails, the entire publish attempt aborts. A cleanup routine immediately runs to delete any previously staged photos from Facebook to prevent orphaned media. The submission proceeds through the standard retry sequence like any other publish failure. 
- A2 — Missed Slot Detection: The 5-minute sweep produces two distinct outcomes depending on the submission's prior state, not one:
  - A submission that was already approved and scheduled, but whose scheduler pass never actually ran (for example, the system was briefly unavailable), transitions to the standard failed state and follows the normal manual-recovery path (UC-2.4 A12).
  - A submission that was never reviewed at all before its scheduled time passed transitions instead to a distinct Missed Review state, releasing its slot reservation. This is not treated as a publishing failure, since it was never approved to publish in the first place — recovery sends it back into the Approval Workflow from the beginning (UC-2.4), not through the Failed tab's manual-publish recovery. The calendar displays this state with its own distinct color, separate from both a successful publish and a genuine publish failure.
- A3 — API Token Expiry: An expired token blocks publishing without consuming a retry attempt, since the underlying issue is the connected Page's authentication, not the post itself. The submission is suspended and Administrator alerts escalate in stages — an initial warning, a 24-hour escalation, and a final notice — culminating in the submission transitioning to the failed state and becoming available for manual recovery if the token is not reauthorized within 48 hours. Resolving the token itself is UC-3.5's territory, not this use case's.
- A4 — Development Mode Visibility: During the pilot period, posts published through the system are visible only to users holding a role on the connected Meta Developer Application. This is expected behavior under Facebook's Development Mode, not a system defect, and requires no code change to resolve — only DASIG's completion of Meta Business Verification, which is an organizational step outside this system's scope.
- A5 — Live Event Fast-Track Immediate Publish: An approved Fast-Track submission publishes immediately upon approval, independent of the scheduled-time-based scheduler entirely, since a Fast-Track submission has no scheduled slot for that scheduler to ever match against.
- A6 — Mixed-Media Submission: Detected immediately and routed straight to the failed state with no retry attempted, since Facebook does not support combining images and video in a single automated post.
Postcondition(s)
On success, the submission is published with the platform's post identifier recorded, the Contributor notified, and the calendar updated accordingly. Analytics reflects a new publication within roughly 60 seconds in typical use. Submissions whose scheduled time passes unpublished are detected within 5 minutes and resolved into one of two distinct outcomes depending on whether they had already been approved: a standard publish failure with manual recovery available, or a return to the Approval Workflow if they were never reviewed at all. Token expiry suspends publishing with escalating Administrator alerts, culminating in failure and manual recovery if unresolved within 48 hours. 
=========================================
## Implementation Status

- ✅ **Implemented:** The background scheduler (1 minute and 5 minute sweeps) logic is successfully implemented.
- ✅ **Implemented:** The atomic database claim via `PublishingQueryService` is fully functional.
- ✅ **Implemented:** The API Token Expiry escalation matches the new multi-stage alerts logic (24-hour, 48-hour).
- ✅ **Implemented:** Missed Review state with slot release is fully separated from normal publication failures.
- ✅ **Implemented:** Fast-Track immediate publishing operates properly outside the main scheduler window.
- ✅ **Implemented:** Photo Staging Flow correctly uses batch staging and cleanup as required by the Facebook Graph API.

## Technical Notes & Bug Findings
- **Transaction Boundary Bugs:** Found missing transaction proxies in `StaleSubmissionDetectorJob` and `TokenPublishingEscalationJob` that expose the system to partial database commits (see Bug Report).

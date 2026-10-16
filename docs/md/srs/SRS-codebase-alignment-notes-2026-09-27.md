# SRS-to-Codebase Alignment Notes

**Review date:** 2026-09-27  
**Source SRS:** `DASIGConnect_Software Requirements Specifications (5).md`  
**Purpose:** Record changes needed to align the SRS with the implemented system. This is a review checklist, not a replacement SRS.

## Recommendation about the guides

Do **not** create a new business use case for the guides.

The implemented guides are contextual onboarding tours. They explain existing use cases and do not create a new business goal, actor goal, data transaction, or workflow state. They should be documented as a cross-cutting usability feature and referenced from the affected existing use cases:

- **UC-1.5 Post Drafting & Templates:** submission list tour, composer tour, Save Draft guide, readiness checklist, media/details/schedule navigation, AI/Templates/Fancy Text panels, Live Event, preview, and submit steps.
- **UC-1.6 AI & Text Tools Integration:** the composer tour opens the AI caption and Fancy Text panels in a view-only state.
- **UC-1.7 Media Attachment Rules:** the composer tour explains device upload, library selection, AI Suggestions, media ordering, album matching, and media tags.
- **UC-1.9 Draft Submission:** the Save Draft and Send for Approval steps.
- **UC-2.4 Approval Workflow:** review queue, review history, lock, decisions, failed-publication recovery, and moderator edit guides.
- **Manual Publishing Fallback / Resolution Center:** the failed-publication guide explains retry and manual publish recovery. Older implementation notes may use a different UC label for this capability; under the authoritative SRS numbering it is documented as a publishing and exception-handling workflow without assigning it UC-3.4, because UC-3.4 is **Social Engagement Analytics**.
- **Section 3.4.8 Usability:** add one cross-cutting requirement for contextual, view-only onboarding tours.

The guides currently implemented are:

1. Submission list guide.
2. Submission composer guide.
3. Save Draft guide.
4. Validation queue guide.
5. Review/decision guide.
6. Failed-publication guide.
7. Moderator edit guide.

The guides are account-level, not browser-level:

- `toursEnabled` is stored on the user account.
- `tourSeenScreens` is stored on the user account.
- Dismissing a guide prevents it from automatically reappearing on another browser/device.
- Users can disable guides and reset them.
- The guide opens panels or wizard steps for explanation but is view-only and restores the user's original view when closed.

Suggested SRS requirement:

> The system shall provide optional, contextual, view-only onboarding tours for supported screens. A tour shall not mutate the submission, review decision, or workflow state. The system shall persist the user's guide-enabled preference and dismissed-screen identifiers per account, synchronize those preferences through the authenticated API, and provide a way to disable or reset the tours.

This belongs under usability and as small extensions to the existing use-case descriptions, not as “UC-3.7 Onboarding Guides.”

## High-priority factual corrections

### 1. Storage and deployment architecture

Update the introduction, scope, constraints, assumptions, software interfaces, and storage NFRs:

- Media files are stored in **Cloudflare R2**, not Supabase Storage.
- Supabase is used for PostgreSQL and pgvector.
- The browser uploads directly to R2 through a backend-issued presigned URL.
- New media read URLs go through the backend media proxy; the browser does not read R2 directly.
- Production requires `BACKEND_PUBLIC_BASE_URL`.
- The current deployment model is Vercel frontend plus Railway backend; references to Render should be checked and replaced if they still describe the production target.
- The stated free-tier object-storage limits should be verified against the actual R2 plan rather than copied from Supabase Storage.

### 2. Role and access model

Update the user-characteristics table, access matrix, use-case actors, and tenant-isolation section:

- Roles are **Contributor, Moderator, and Administrator**. Do not use “Validator” as an active role.
- Moderators are network-wide and institutionless. They are not limited to their own institution.
- Contributors are institution-scoped.
- Administrators are network-wide, with institution drilldown where the feature supports it.
- The Admin Owner is a distinct governance flag. It is required for Admin Owner transfer and some existing-Admin actions; it is not a fourth role.
- All three roles can access Analytics, with different scopes and export permissions.

### 3. Scheduling guard rails

The current SRS describes per-institution quotas in several places. Reconcile this with the implemented network-wide scheduling rules:

- Guard rails are a network-wide runtime switch controlled by an Administrator.
- Standard scheduled posts require a future slot and a slot reservation.
- When enabled, the implemented rules include the ±30-minute spacing rule, a maximum of six posts per day, a minimum two-hour lead time, and the 8:00 AM–8:00 PM posting window.
- Fast-Track/Live Event submissions bypass slot selection and scheduled-slot guard rails.
- An Administrator can explicitly override a hard slot block; normal reservations are protected by the database exclusion constraint.
- Update the guard-rail business-rule tables and any “per-institution quota” wording.

### 4. AI caption behavior

The current AI use case and AI business rules still describe the earlier multi-variant behavior. Update them to:

- The endpoint returns **one caption** for the selected tone, not 1–3 variants.
- Supported tones are professional, community, and energetic.
- The optional instruction prompt is capped at 280 characters.
- Up to four images may be sent; large images are downscaled in memory.
- The Claude request timeout is 30 seconds.
- The caption request limit is 30 requests per hour per user.
- Requested word-count parsing has a separate 2,000-word safety ceiling.
- The stored caption limit is 3,000 characters; this is different from the 280-character AI instruction limit.
- The AI prompt no longer uses a submission category from the composer because the composer does not collect one.
- Fancy Text is a button-opened panel with five styles plus Plain; it is not automatically activated by selecting text.

### 5. Media attachment and recommendation behavior

Update UC-1.7 and the media business rules:

- Uploads use backend-issued R2 presigned URLs.
- The accepted formats and 50 MB per-file limit remain, but do not describe the browser as uploading to Supabase.
- A draft may temporarily contain mixed images and video; automated Facebook publishing later fails that submission and exposes it in the Failed tab for recovery.
- Every asset belongs to an album. There is no supported “unfiled” state.
- Per-asset captions are limited to 500 characters.
- Skip-watermark is available for image assets through the media-caption flow.
- Album Auto-Match now combines tag overlap with visual similarity to the closest existing asset. It can auto-apply a confident match, show up to three ambiguous candidates, or leave the field for manual selection.
- AI media suggestions require enough event context, use a 0.40 score floor, and show up to eight results. Confirm the final wording against the living UC-1.7 document before copying the old SRS threshold/top-five values.
- Manually added media tags are searchable.

### 6. Media history and deletion

The SRS calls this “Media History Tracking”; the living SRS-style document calls it **UC-2.3 Media History & Asset Lifecycle**. Update the description to include:

- Used In links combine current relationships with historical reuse/audit information.
- Deleted submissions/assets display placeholders such as `[Submission Deleted]` and `[Asset Deleted]`.
- Deletion is hard-blocked for active workflow references.
- Draft/needs-revision references require a force confirmation.
- The 30-day retention period is an internal purge buffer; there is no user-facing trash or restore screen.
- Duplicate detection is exact SHA-256 content-hash detection, not visual-similarity duplicate detection.

### 7. Approval workflow

The current SRS contains several stale approval rules:

- The review lock TTL is **15 minutes**, not 30 or 60 minutes.
- Moderators and Administrators share the network-wide queue.
- Self-submission review is always blocked.
- Live Events are sorted above ordinary scheduled work in the active queue.
- Reject is not necessarily final; a rejected submission can be edited and resubmitted.
- Rejection reasons are: inappropriate content, out of scope, duplicate event, no longer relevant, rights/privacy, wrong institution, and other.
- `INCOMPLETE_CONTENT` and `WRONG_FORMAT` are revision-type problems and should not be presented as rejection codes.
- Review edits have before/after diffs and severity tiers. The edit-detection window spans the full review cycle, including lock release/reacquisition, until a terminal action.
- Review & Save is a confirmation flow with Undo and Restore Original.
- The AI writing check is advisory only and never blocks saving or approval.
- Edit-related notifications are separate from the base approval notification.
- The approve flow rejects an expired scheduled slot unless an Administrator uses the audited publish-now override.
- The media-load failure state has a retry action and does not prevent the reviewer from deciding.

### 8. Notifications and reminders

Reconcile the notification trigger matrix with the implementation:

- Review-related notifications go to active Moderators and Administrators, not Administrators only.
- Messenger delivery is available to eligible Moderators and Administrators who link their account; it is not Admin-only.
- The system has more notification event types than T-01–T-12. The SRS can retain the named trigger matrix, but should say it is not an exhaustive enum list.
- Read and Mark All Read changes are pushed through SSE to other active sessions.
- The navbar unread badge has its own SSE subscription and keeps polling only as a dropped-connection fallback.
- There is no general high-volume digest system. The weekly empty-schedule/content-idea behavior is narrow and event-specific.
- Replace any placeholder citation such as `[PLACEHOLDER: confirm correct UC/extension-flow citation...]`.

### 9. Analytics

The Social Engagement Analytics section is materially out of date:

- Contributors see institution-wide analytics, not only their own submissions.
- Moderators see the whole network and cannot filter down to an institution.
- Administrators can use institution drilldown for the Admin-only views.
- The dashboard does not have the old category filter.
- The summary endpoint uses a 60-second cache; workflow metrics are not guaranteed to be live to the second.
- The dashboard refresh interval is five minutes, not 60 seconds.
- `operational-health` and `ai-performance` exports are Admin-only; the other supported reports are available to all three roles subject to scope.
- CSV export protects against spreadsheet formula injection by neutralizing leading `=`, `+`, `-`, `@`, tab, and carriage-return values.
- The operational-health view includes an explicit 95% on-time publication target.

### 10. Automated publishing

Update UC-3.2 and the publishing business rules:

- Scheduler claims a submission atomically before calling Facebook, preventing duplicate publishing across overlapping runs or instances.
- Fast-Track publishing is asynchronous after approval and bypasses the scheduled cron query.
- Watermarking is applied at publish time using the configuration active then, not at approval.
- Image publishing stages photos and then creates the feed post; each media asset can carry its own caption.
- Mixed image/video posts fail automated publishing and are recovered from the Failed tab.
- The stale detector distinguishes an approved scheduled post that missed publishing from a post that was never reviewed (`missed_review`).
- Stuck Fast-Track publishing has a recovery sweep even though it has no scheduled slot.
- Failed photo cleanup records unresolved Facebook photo IDs and exposes them in the Failed tab.
- A successful retry onto a new schedule resets the original-slot/reschedule baseline.
- Hashtags already present in the caption are not appended a second time.

### 11. System health and exception handling

Update UC-3.5:

- System Health tracks 15 background jobs, not only the four originally named.
- Administrators can manually re-run jobs and the action is audit-logged.
- Publishing Scheduler and Token Publishing Escalation run asynchronously when manually triggered; the other jobs return synchronously.
- Storage checks include a live R2 scan with a database estimate fallback.
- The operational metrics include missed-review rate in addition to the previously listed metrics.
- Token reauthorization updates the token; publishing resumes when the escalation job later observes a usable token. Do not state that the OAuth callback itself directly resumes all suspended publishing.

### 12. Audit log

Update UC-3.6 and its requirements:

- The audit log is Admin-only.
- It is append-only at the database policy level.
- Export CSV values are formula-injection hardened.
- Routine page views and self-service preference changes are intentionally not audit events. Keep the audit scope focused on workflow decisions, destructive actions, and security/governance events.

## Non-functional and platform corrections

Review these requirements against actual behavior and remove absolute claims that the code does not guarantee:

- HikariCP maximum pool size is five for the Supabase Session Pooler.
- The 50 MB limit applies to the current upload path; the earlier “25 MB image / 500 MB video” distinction is not available on the current free-tier setup.
- AI and external API timing statements should be targets, not guaranteed response times.
- Publishing accuracy is measured as on-time publication within ±5 minutes; the implemented Admin metric compares the result with a 95% target.
- Retention needs to distinguish database records, R2 objects, generated watermark derivatives, and the lack of a user-facing restore function.
- Add the account-level onboarding-tour requirement under Usability.
- Verify that the “Supabase free-tier 1 GB file storage” and “Render” statements are not left in any assumptions, dependencies, or deployment sections.

## Numbering and structure

The authoritative use-case numbering is the UC-1.1–UC-3.6 series represented by the living documents in `docs/md/`. Older planning and implementation notes may contain superseded labels, including UC-2.3 for Notifications, UC-2.4 for Analytics, and earlier UC-3.2–UC-3.5 groupings. Those references are retained only as historical context and must not be used for new SRS requirements. The onboarding guides remain cross-cutting usability documentation and do not require a new use-case number.

## Recommended editing order

1. Correct architecture, roles, deployment, storage, and numbering notes.
2. Correct UC-1.5 through UC-1.9 because the composer and guides depend on them.
3. Correct UC-2.1 through UC-2.4, especially review lock, rejection, and media lifecycle rules.
4. Correct UC-3.1 through UC-3.6 and the business-rule tables.
5. Add the cross-cutting onboarding-tour requirement under Usability and add one sentence to each affected use case.
6. Recalculate the table of contents and update the change-history entry after the wording is approved.

## Living reference documents

Use these code-verified documents as the detailed source while editing the SRS:

- `docs/md/UC-1.5-post-drafting-templates.md`
- `docs/md/UC-1.6-ai-text-tools.md`
- `docs/md/UC-1.7-media-attachment-rules.md`
- `docs/md/UC-1.8-engagement-helpers.md`
- `docs/md/UC-1.9-draft-submission.md`
- `docs/md/UC-2.1-library-uploads-albums.md`
- `docs/md/UC-2.2-semantic-search-filtering.md`
- `docs/md/UC-2.3-media-history-asset-lifecycle.md`
- `docs/md/UC-2.4-approval-workflow.md`
- `docs/md/UC-3.1-master-calendar-visibility.md`
- `docs/md/UC-3.2-automated-facebook-post-publishing.md`
- `docs/md/UC-3.3-automated-reminders-alerts.md`
- `docs/md/UC-3.4-social-engagement-analytics.md`
- `docs/md/UC-3.5-system-health-analytics.md`
- `docs/md/UC-3.6-audit-log-review.md`

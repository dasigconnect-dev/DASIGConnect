# **DASIGConnect — Objectives-Based Development Plan**

**Based on:** Project Proposal (Weeks 7–8 Guide) \+ SRS v1.6 **Team Code:** 2526-sem2-it332-38 **Purpose:** Translate every General and Specific Objective from the proposal into concrete, measurable development deliverables

---

## **How to Read This Document**

Each General Objective from the proposal is broken down into: \- The **features/subsystems** that must be built to achieve it \- The **specific objectives** it satisfies (numbered as in the proposal) \- The **acceptance condition** — the exact testable state that proves the objective is met \- The **development tasks** required \- The **success metric** from the proposal (the measurable target) \- A **realistic duration estimate**

This is not a sprint plan. It is a features-to-objectives traceability document that tells the team: *“If we build these things and they pass these tests, our proposal objectives are achieved.”*

---

---

# **GENERAL OBJECTIVE 1**

## **Foundation, Access Control, and Content Submission**

*“To increase content submission completeness to a ≥95% form completion rate on first submission attempt, through a secure, multi-organization submission portal with role-based access control supporting at least three (3) DASIG member institutions, to be delivered by the end of Module 1.”*

**Success Metric:** ≥95% form completion rate on first submission attempt during pilot testing **Delivery Target:** End of Module 1

---

### **Specific Objective 1.1**

*“To enable independent content submission from at least three distinct DASIG member institutions without coordinator intervention, by defining and implementing distinct access roles for Contributor, Validator, and Administrator.”*

**What must be built:** \- Three distinct user roles (Contributor, Validator, Administrator) enforced at every API endpoint via RBAC \- Each role routes to a different dashboard on login: \- Contributor → /workspace/{institution\_code}/dashboard \- Validator → /workspace/{institution\_code}/dashboard (different view) \- Administrator → /admin/dashboard \- Institutions are completely independent — a Contributor at CIT-U cannot see, access, or interfere with submissions from Silliman \- The submission form is accessible to Contributors without any Administrator involvement in the routing or form access

**Development Tasks:** \- \[ \] Design the users table with role ENUM('CONTRIBUTOR', 'VALIDATOR', 'ADMINISTRATOR') and institution\_id foreign key \- \[ \] Implement JWT with role and institution\_id as verified claims \- \[ \] Build Spring Security role-based access on every /api/\*\* endpoint (403 on role mismatch) \- \[ \] Build TenantScopeInterceptor that reads institution\_id from JWT and attaches to request context \- \[ \] Implement five-layer tenant isolation (JWT binding → interceptor → query filter → RLS → server-derived institution\_id) \- \[ \] Build three distinct dashboard shells in React with role-based routing \- \[ \] Write integration test: Contributor at institution A attempts to read submissions of institution B → must return HTTP 404 (not 403, per SRS Section 3.4.6.4)

**Acceptance Condition:** Three separate Contributor accounts from three different institutions can each independently log in, reach their own workspace, and submit content — none of them able to see each other’s submissions or media — and no Administrator action was required for any submission to be created.

**Duration Estimate:** 1.5 weeks (overlaps with 1.2 and 1.3)

---

### **Specific Objective 1.2**

*“To ensure institution-level data isolation and eliminate unauthorized cross-institution access, by implementing a secure authentication and onboarding mechanism that provisions isolated workspaces per institution, with role-based routing to either the institutional workspace or the network admin console upon login.”*

**What must be built:**

**Authentication system (UC-1.1):** \- Invitation token system: cryptographically random 32-byte token, only SHA-256 hash stored in DB, raw token transmitted once via email, 24-hour expiry, single-use enforced \- Account activation flow: user clicks link → sets password (8+ chars, uppercase, lowercase, digit, special char, bcrypt cost ≥12) → account transitions PENDING → ACTIVE \- Session management: JWT with 8-hour inactivity timeout, resets on every API call \- Session warning banner at 15 minutes before expiry → silent token refresh \- 401 intercept on expired session → Session Expired Modal → re-auth without page navigation → resume from last auto-saved draft \- Account lockout: 5 failed attempts in 15 minutes → 15-minute lockout → email alert \- Forgot password: time-limited reset token (1 hour), invalidates all active sessions on change \- Batch Contributor invite: up to 50 emails per batch, per-address validation, failure report for invalid entries

**Institution onboarding (UC-1.2):** \- Administrator creates institution record → status ONBOARDING \- System provisions isolated workspace with RLS scoping all data to institution\_id \- Administrator creates first Validator account → invitation email dispatched \- Institution transitions ONBOARDING → ACTIVE when first Validator activates their account (event-driven, no manual Admin action) \- If ONBOARDING \> 7 days → reminder notification to Administrator \- If all Validators become inactive → institution transitions ACTIVE → INACTIVE\_NO\_VALIDATOR → all pending submissions escalated to Administrator

**Data isolation (RLS):** \- PostgreSQL Row-Level Security enabled on: users, submissions, assets, notifications, audit\_logs \- RLS policies reference app.current\_institution\_id session variable set at start of each request \- Administrator has institution\_id \= NULL, bypasses all tenant filters by design \- Cross-institution access by direct URL/API → HTTP 404 (existence not revealed)

**Development Tasks:** \- \[ \] institutions table: id, name, institution\_code (unique), status ENUM('ONBOARDING','ACTIVE','INACTIVE\_NO\_VALIDATOR'), created\_at \- \[ \] users table with institution\_id FK, role, state ENUM(PENDING, ACTIVE, INACTIVE, EXPIRED, PENDING\_EMAIL\_UNDELIVERED), invitation\_token\_hash, invitation\_token\_expires\_at, password\_hash, failed\_login\_count, lockout\_until \- \[ \] InvitationService: CSPRNG token generation, SHA-256 hash storage, email dispatch (Resend), token expiry cleanup job (GR-T8, hourly) \- \[ \] AuthService: bcrypt password verification, JWT issuance, lockout enforcement \- \[ \] Account state machine enforcement in service layer (reject invalid transitions with 409\) \- \[ \] RLS policies for all institution-scoped tables \- \[ \] Frontend: Institution Management panel (Admin creates workspace, registers Validator email) \- \[ \] Frontend: Activation page (password complexity real-time validation, all 5 rules shown inline) \- \[ \] Frontend: Forgot password flow (neutral “if account exists” response to prevent enumeration) \- \[ \] Frontend: Session Warning Banner with silent refresh behavior \- \[ \] Frontend: Session Expired Modal with context-aware message (“Your draft has been saved — log back in to continue”)

**Acceptance Condition:** An Administrator can onboard a new institution, provision a Validator, have that Validator activate their account via email, batch-invite 10 Contributors, and all Contributors can activate and log in — each reaching their own isolated workspace. No Contributor can access any data from any other institution by any URL manipulation or API call.

**Duration Estimate:** 2 weeks (heaviest part of Module 1\)

---

### **Specific Objective 1.3**

*“Develop the content submission form with server-enforced validation on the following mandatory fields: event title, event date, description, caption, at least one tag, and at least one media upload (photo or video). The form shall reject submissions missing any mandatory field with field-level error messaging, achieving a verified ≥95% form completion rate on submitted records during pilot testing.”*

**What must be built:**

**Submission form (UC-1.3):** \- Fields: Event Title (mandatory), Event Date (mandatory), Description (optional), Facebook Caption (mandatory), Tags (mandatory — AI-assigned or manual), Media Upload (mandatory — at least one photo or video) \- File validation: MIME type AND magic bytes checked (not just file extension), accepted formats: JPEG, PNG, WebP, GIF, MP4, MOV, WebM, max 25 MB per file \- Maximum 10 media assets per submission (device upload \+ repository picker combined) \- Asset Picker Modal: browse institution media library, search by filename/tag/event, select and attach existing assets by asset\_id (no re-upload) \- Auto-save: every 60 seconds when form content has changed, silent, updates “Draft saved at \[time\]” indicator \- Manual save: “Save as Draft” button, identical behavior to auto-save \- Submit button: triggers server-side re-validation of all mandatory fields AND guard rail compliance before state transition DRAFT → PENDING

**Self-service scheduling (within UC-1.3):** \- Integrated Master Calendar read-only view in the submission form showing all network-wide slots \- Contributors see institution badges and times across all institutions (for slot awareness), but only their own institution’s submission content \- Slot selection triggers real-time guard rail enforcement: \- **GR-H1 (Hard):** No two posts within ±30 minutes → blocks selection, suggests nearest available slots \- **GR-H2 (Hard):** Scheduled time ≥2 hours in the future → blocks selection \- **GR-H3 (Hard):** Scheduled time ≤30 days in the future → blocks selection \- **GR-S1 (Soft):** ≤3 scheduled-but-unpublished posts per institution → warning, can proceed \- **GR-S2 (Soft):** ≤6 posts per calendar day network-wide → warning, can proceed \- Slot reservation: tentative hold on selected slot (held through DRAFT and PENDING states, released on rejection/revision, permanent on approval) \- Atomic slot reservation: database-level unique constraint prevents two Contributors reserving the same slot simultaneously \- Slot released automatically after 7 days of DRAFT inactivity (GR-T2 cron job)

**Submission state machine:** \- DRAFT: saved but unsubmitted, visible only to the Contributor who created it \- PENDING: submitted for validation, visible in Validator’s queue, slot reservation held \- NEEDS\_REVISION: revision requested, slot released, Contributor must re-edit and pick new slot \- REJECTED: terminal state, slot released, new submission required

**Development Tasks:** \- \[ \] submissions table: id, institution\_id FK, contributor\_id FK, event\_title, event\_date, description, caption, state, scheduled\_at, first\_pending\_at, created\_at, updated\_at \- \[ \] slot\_reservations table with unique constraint on (scheduled\_at, page\_id) — enforces atomic reservation \- \[ \] assets table: id, institution\_id FK, uploader\_id FK, filename, storage\_url, mime\_type, file\_size, asset\_type ENUM('IMAGE','VIDEO'), ai\_category, confidence\_score, ai\_description, embedding vector(1024), reconciliation\_attempt\_count, created\_at, deleted\_at \- \[ \] submission\_assets join table: submission\_id FK, asset\_id FK \- \[ \] File upload endpoint: MIME \+ magic bytes validation, Supabase Storage upload, asset record creation \- \[ \] Submission CRUD API: create DRAFT, auto-save PATCH, submit (DRAFT → PENDING), delete draft \- \[ \] Guard rail validation service: all five rules evaluated on slot selection and re-evaluated on submit \- \[ \] Master Calendar read API: returns all slot reservations and published states across all institutions (timing only for cross-institution, full detail for own institution) \- \[ \] GR-T2 cron job: runs daily, finds DRAFTs with slot reservation and updated\_at \< NOW() \- 7 days, releases slot \- \[ \] Frontend: Full submission form with all fields, auto-save timer, character counter on caption \- \[ \] Frontend: Master Calendar embedded in submission form (read-only, conflict visualization) \- \[ \] Frontend: Guard rail feedback UI (hard block with alternatives, soft warning dismissible) \- \[ \] Frontend: Asset Picker Modal (search, filter by AI tag, select multiple, checkmark already-attached) \- \[ \] Frontend: Field-level error messages on submit attempt with missing mandatory fields \- \[ \] Frontend: “Draft saved at \[time\]” indicator updating every auto-save cycle \- \[ \] Server-side: mandatory field re-validation on every submit call (not client-side-only)

**Acceptance Condition:** A Contributor can open the form, attach media, fill all fields, select a valid time slot, see guard rail blocks if they pick a conflicting time, and submit — the submission appears in the Validator’s queue. If they try to submit with a missing field, each missing field shows an inline error and the submission is blocked. Auto-save fires every 60 seconds and a session expiry mid-form does not lose any data.

**How This Achieves the ≥95% Metric:** The server-enforced mandatory field validation with field-level error messaging (not just a generic “form incomplete” banner) directly guides Contributors to complete all fields before submission. The auto-save and session recovery system ensures no work is lost, reducing the friction that causes incomplete abandonment. Measure this metric during pilot testing by counting: submissions where all\_required\_fields\_present \= TRUE at PENDING transition ÷ total PENDING transitions × 100%.

**Duration Estimate:** 1.5 weeks

---

---

# **GENERAL OBJECTIVE 2**

## **Validation Workflow, Notification System, and Analytics**

*“To eliminate unapproved content publication and reduce submission-status notification delay, through a content validation, notification, and resource management subsystem that (a) enforces a 100% administrator-reviewed approval step before any post is published, (b) reduces notification delivery time to within 5 minutes of state change in ≥95% of cases, and (c) improves media asset accessibility through a searchable library indexed by institution and event, achieving a System Usability Scale (SUS) score of ≥70 from at least 10 pilot users by the end of Module 2.”*

**Success Metrics:** \- 100% admin-reviewed approval before any publication \- ≤5 minutes email notification delivery in ≥95% of cases \- ≤30 seconds in-app notification delivery \- SUS ≥70 (note: SRS v1.6 updated this to ≥68 citing Bangor et al. 2009 — discuss with adviser which threshold to report) \- Media library search returns results within 2 seconds for up to 1,000 assets

**Delivery Target:** End of Module 2

---

### **Specific Objective 2.1**

*“Implement a validation interface that eliminates unapproved or erroneous posts by enforcing a 100% administrator-reviewed approval step, with the ability to approve, request revisions with remarks, or reject submissions before publication.”*

**What must be built (UC-2.1):** \- Validation Queue accessible to Validators: shows all PENDING submissions from their institution, sorted by Scheduled Publication Date (earliest first) \- Review lock: when Validator opens a submission, it transitions PENDING → IN\_REVIEW and acquires a lock preventing concurrent review by another Validator \- 30-minute review lock inactivity timeout → auto-reverts IN\_REVIEW → PENDING, releases lock \- Three validation actions: \- **Approve:** PENDING/IN\_REVIEW → SCHEDULED, slot reservation becomes permanent, Contributor notified \- **Request Revision:** → NEEDS\_REVISION, slot released, remarks mandatory (10–1,000 characters), Contributor notified with full remarks \- **Reject:** → REJECTED (terminal), slot released, reason code required (6 options), optional written notes, Contributor notified \- Submission Detail Panel: media carousel with full-resolution preview and inline video playback, AI tags, revision history for re-submissions, all metadata \- GR-H5: self-validation blocked (Validator cannot validate their own submission) → auto-routes to another Validator or escalates to Admin \- GR-T1: if submission reaches ≤30 minutes before scheduled publish time without validation → auto-escalate to Administrator AND all institution Validators simultaneously

**How “100% approval before publication” is technically guaranteed:** \- The publishing cron job (UC-3.1 Step 5\) queries ONLY for state \= 'SCHEDULED' \- A submission can only reach SCHEDULED state through an explicit Validator (or fallback Administrator) Approve action \- There is no code path that publishes a PENDING or IN\_REVIEW submission — this is GR-H4 \- This is enforced in the cron query itself, not just in application logic

**Development Tasks:** \- \[ \] Validation Queue API: GET /api/validation/queue?state=PENDING scoped to Validator’s institution \- \[ \] Review lock: submission\_reviews table with submission\_id, validator\_id, locked\_at, expires\_at; lock acquired on “Review” click, released on action or timeout \- \[ \] Review lock timeout job: runs every 5 minutes, finds expired locks (expires\_at \< NOW()), reverts IN\_REVIEW → PENDING \- \[ \] Approve endpoint: validates actor is a Validator for the submission’s institution, transitions to SCHEDULED, makes slot permanent \- \[ \] Request Revision endpoint: validates remarks length (10–1,000 chars), transitions to NEEDS\_REVISION, releases slot, records remarks in submission\_revision\_history \- \[ \] Reject endpoint: validates reason code from enum, transitions to REJECTED, releases slot \- \[ \] GR-H5 enforcement: on Review click, check submission.contributor\_id \!= current\_user.id; if match, route to next Validator or escalate \- \[ \] GR-T1 cron job: runs every minute, finds state IN ('PENDING','IN\_REVIEW') AND scheduled\_at \< NOW() \+ 30 min, triggers escalation \- \[ \] Frontend: Validation Queue with sort selector, state tabs (PENDING / ALL), status badges \- \[ \] Frontend: Submission Detail Panel — media carousel, video inline player, AI tags display, revision history accordion \- \[ \] Frontend: Approve / Request Revision / Reject action buttons with confirmation modals \- \[ \] Frontend: Remarks textarea with real-time character counter (10 min / 1,000 max) \- \[ \] Frontend: Reject reason dropdown with 6 options, OTHER requires written notes

**Acceptance Condition:** Every submission that appears on the DASIG Facebook Page was approved by a Validator or fallback Administrator. Zero posts published without passing through state \= 'SCHEDULED'. Validate this by querying SELECT COUNT(\*) FROM submissions WHERE state \= 'PUBLISHED' AND id NOT IN (SELECT submission\_id FROM submission\_revision\_history WHERE action \= 'APPROVED') — result must be 0\.

**Duration Estimate:** 1.5 weeks

---

### **Specific Objective 2.2**

*“To improve media asset retrieval speed to ≤2 seconds and increase asset reuse across submissions, by building a centralized digital media repository that (a) stores uploaded photos and videos with per-institution isolation enforced via row-level security, (b) supports search by filename, tag, and uploader for libraries up to 1,000 assets, and (c) enables asset reuse via a one-click ‘Use in new post’ action, with reuse tracked in submission records.”*

**What must be built (UC-2.2):** \- Media Library: grid view of all institution assets (thumbnail, asset code e.g. IMG·A01, title, upload date, AI category tag, file size) \- Search by filename, AI tag, event name, uploader — results within 2 seconds for up to 1,000 assets \- AI tag filter chips with per-category asset count, multi-select, clearable \- Sort: Newest / Oldest / Name / Size; Grid / List view toggle \- Asset Detail Panel (side-panel, no page navigation): large preview, all metadata, AI tags with confidence scores, “Used In” history with submission state badges and jump links \- Direct upload to library (not attached to a submission) — same MIME \+ magic bytes validation \- “Use in New Social Media Post” — opens submission form with asset pre-loaded \- “Add to Existing Draft” — appends to a selected DRAFT from dropdown, toast notification \- Asset deletion: three-path logic: \- Blocked if asset referenced in PENDING / IN\_REVIEW / SCHEDULED submission \- Warning \+ confirmation if referenced in DRAFT or NEEDS\_REVISION submission \- Free delete if only terminal-state references or no references \- Administrator “Network View” toggle: see assets across all institutions, session-scoped, audit-logged

**How the ≤2 second search is achieved:** \- Database indexes on assets.institution\_id, assets.ai\_category, assets.filename (ILIKE index using pg\_trgm extension for substring search) \- uploaded\_by joined to users.name with an index on assets.uploader\_id \- For pilot scale (up to 1,000 assets per institution), a well-indexed query with LIMIT 50 pagination will comfortably return in ≤2 seconds; measure this in load testing before pilot

**Development Tasks:** \- \[ \] Enable pg\_trgm extension in Supabase for ILIKE search performance \- \[ \] Create GIN index on assets.filename for trigram search \- \[ \] Media Library API: paginated asset list with combined search \+ filter query \- \[ \] Asset Detail API: full metadata, “Used In” list from submission\_assets joined to submissions \- \[ \] Asset reuse API: “Use in New Post” (creates DRAFT with asset pre-attached), “Add to Draft” (appends asset to specified DRAFT if under 10-asset limit) \- \[ \] Asset deletion API: pre-delete check against active submission references, three-path response \- \[ \] asset\_code generation: stable, human-readable codes (IMG·A01, VID·V01) assigned at upload time \- \[ \] Frontend: Media Library grid with thumbnail rendering, asset code display, category tag chips \- \[ \] Frontend: Search \+ filter bar with live results, “Showing N of M” count \- \[ \] Frontend: Asset Detail Panel (slide-in from right, no page navigation lost) \- \[ \] Frontend: “Used In” list with color-coded submission state badges \- \[ \] Frontend: Deletion flow with appropriate warning/confirmation per path \- \[ \] Frontend: Admin “Network View” toggle with session-scope behavior \- \[ \] Performance test: query 1,000 assets with a search term → must return in ≤2 seconds

**Acceptance Condition:** A Contributor can search the institution media library for “awarding” and receive matching assets within 2 seconds. They can click one and choose “Add to Existing Draft.” The draft’s media list shows the asset attached. The asset’s “Used In” block shows the draft with status badge. No asset from another institution appears in any search result.

**Duration Estimate:** 1 week

---

### **Specific Objective 2.3**

*“To reduce contributor response time and minimize workflow delays, by ensuring email notifications reach contributors within 5 minutes of any submission status change (Pending → Approved, Needs Revision, or Rejected), targeting a SUS score of at least 70.”*

**What must be built (UC-2.3 — email channel):** \- Email notifications via Resend for state changes: T2 (Approved), T3 (Needs Revision — includes full remarks in body), T4 (Rejected — includes reason code and notes), T7 (publish failed — to Contributor), T10 (override denied — includes Admin reason), T12 (token expiry warning — to Admin), T13 (token validation failure — to Admin) \- All email notifications include a direct deep-link to the affected submission \- Retry on failure: up to 3 retries at 1-minute intervals, logged on all retries exhausted \- Delivery target: ≤5 minutes in ≥95% of cases

**How the SUS ≥70 target connects to this objective:** The SUS score is a usability measure of the entire system — it is not achieved by this objective alone. However, timely notifications directly reduce the friction and confusion that drive SUS scores down. Contributors who wait hours to find out their submission was revised become frustrated users. The notification system is the most direct operational contributor to perceived usability.

**SUS evaluation plan:** \- Administer the standard 10-item SUS questionnaire to at least 10 pilot participants (Contributors, Validators, Administrators combined) \- Participants complete evaluation tasks: submit content, validate a submission, access the calendar \- Score per role (Contributor, Validator, Administrator) in addition to aggregate \- A score below 70 (or below 68 per Bangor et al., as stated in SRS v1.6) triggers a mandatory usability review cycle before full deployment

**Development Tasks (email-specific):** \- \[ \] Resend account registered, API key stored in Render env \- \[ \] NotificationService.sendEmail(recipient, template, data) with retry logic (3 retries, 1-minute intervals) \- \[ \] Email templates for T2, T3, T4, T7, T10, T12, T13 with deep-link to submission or resolution panel \- \[ \] T3 email template must include the full Validator remarks in the body \- \[ \] T4 email template must include the rejection reason code (human-readable label) and written notes \- \[ \] Notification delivery failure logged in audit log with EMAIL\_FAILED status \- \[ \] Delivery timestamp recorded on each sent notification for SLA monitoring \- \[ \] Report: COUNT(emails WHERE delivered\_at \- triggered\_at \<= 5 min) / COUNT(all emails) — must be ≥95%

**Duration Estimate:** 0.5 weeks (built alongside 2.4)

---

### **Specific Objective 2.4**

*“To reduce in-app notification latency to within 30 seconds and improve revision transparency for contributors, by implementing (a) a real-time notification badge triggered within 30 seconds of any submission state change, and (b) a remarks field on the validation interface that captures and surfaces administrator feedback during the ‘Request Revision’ action.”*

**What must be built (UC-2.3 — in-app channel \+ UC-2.1 remarks):** \- Server-Sent Events (SSE) connection per authenticated user for real-time notification delivery \- Notification bell badge: increments per unread notification, server-persisted read-state (not localStorage) \- Notification is marked read only when clicked (navigates to linked view) or “Mark All Read” — not on panel open \- 50 most recent notifications in panel, “View all” link for paginated history, 90-day display retention \- Batching: multiple events within 5-second window → single badge increment, separate panel entries \- High-volume consolidation: \>5 PUBLISH\_FAILED in 1 hour → single consolidated Admin alert (GR-T6) \- All 17 trigger events dispatched per the notification trigger matrix in UC-2.3 \- Remarks field on Revision Request: mandatory, 10–1,000 characters, character count indicator, button disabled until minimum met \- Revision history visible on Submission Detail Panel for all re-submissions: previous remarks, action timestamps, Validator identity per cycle

**Development Tasks:** \- \[ \] notifications table: id, recipient\_id FK, event\_type, message, deep\_link, read\_at, created\_at \- \[ \] SSE endpoint: GET /api/notifications/stream — authenticated, per-user event stream \- \[ \] NotificationService: event bus subscription, push to SSE stream on each triggered event \- \[ \] All 17 trigger points wired to NotificationService: UC-1.2, UC-1.3, UC-2.1, UC-3.1, UC-3.5 all emit notification events \- \[ \] Read-state API: PATCH /api/notifications/:id/read, PATCH /api/notifications/read-all \- \[ \] Batching logic: buffer events per recipient over 5-second window, emit single SSE push with count \- \[ \] submission\_revision\_history table: submission\_id FK, validator\_id FK, action, remarks, created\_at \- \[ \] Frontend: Notification bell with badge count, panel with 50 most recent items \- \[ \] Frontend: Per-notification deep-link navigation on click \- \[ \] Frontend: “View all notifications” paginated history \- \[ \] Frontend: Revision History accordion on Submission Detail Panel (visible only for re-submissions) \- \[ \] Delivery SLA test: trigger a state change → measure time until SSE event received by client → must be ≤30 seconds

**Acceptance Condition:** A Contributor submits a post. A Validator requests revision with remarks “Please add more context about the event date and venue.” Within 30 seconds, the Contributor sees a notification badge increment. They click the notification, are taken directly to their submission, and see the Validator’s full remarks in a highlighted remarks panel. They also receive an email within 5 minutes with the same remarks in the body.

**Duration Estimate:** 1 week (in-app \+ email combined)

---

### **Specific Objective 2.5**

*“Create a social media analytics dashboard that improves administrator visibility by tracking posting frequency (posts/month), submission-to-publish duration (days), and content completeness rate (%), targeting a minimum of 4 posts per month and a completeness rate of at least 95%.”*

**What must be built (UC-2.4):** \- Three KPI tiles with sparklines and period-over-period deltas: \- **AVG POSTING DELAY:** AVG(published\_at − first\_pending\_at) for PUBLISHED and PUBLISHED\_MANUAL submissions within selected period \- **CONTENT COMPLETENESS:** COUNT(posts with all required fields) / COUNT(all published posts) × 100% — ADMIN\_DIRECT\_POST excluded \- **TOTAL POSTS PUBLISHED:** PUBLISHED \+ PUBLISHED\_MANUAL counts; ADMIN\_DIRECT\_POST shown as separate line item \- Time range selector: 7d / 30d / 90d / YTD \- 12-week sparklines: blank (not zero) for weeks before system deployment, tooltip on earliest data point \- Posts by Institution bar chart (Admin only): sorted descending by post count, period-over-period delta \- AI Performance metrics (scoped by role): caption acceptance rate, tag manual-correction rate \- Operational Health metrics (Admin only): validation timeout rate, override request rate, publishing success rate, Administrator workload \- Full Report per KPI: per-day breakdown table \+ submission-level data table with CSV export \- Auto-refresh every 60 seconds, manual refresh button

**How the ≥4 posts/month and ≥95% completeness targets are evidenced:** \- The TOTAL POSTS PUBLISHED tile directly shows posts per institution per month \- The CONTENT COMPLETENESS tile directly shows the completeness rate against the 95% target \- Both are visible to Administrators on the network-wide dashboard and to Validators/Contributors on their institution-scoped view \- These are the exact numbers that will be compared in the pre- vs post-implementation KPI evaluation

**Development Tasks:** \- \[ \] Analytics query: AVG posting delay grouped by period (first\_pending\_at to published\_at) \- \[ \] Analytics query: Content completeness — check event\_title IS NOT NULL AND event\_date IS NOT NULL AND caption IS NOT NULL AND media\_count \>= 1 \- \[ \] Analytics query: Total posts published by state (PUBLISHED vs PUBLISHED\_MANUAL vs ADMIN\_DIRECT\_POST) \- \[ \] Analytics query: Posts by institution for Admin bar chart \- \[ \] AI metrics query: caption acceptance rate from ai\_caption\_interactions table, tag correction rate from submission\_ai\_tags \- \[ \] Operational health queries: timeout rate (escalated\_submissions / total\_submissions), override rate, publishing success rate, Admin action count per week \- \[ \] ai\_caption\_interactions table: submission\_id FK, action ENUM('use','use\_then\_edited','edit','re-generate','dismiss'), tone\_selected, timestamp \- \[ \] Analytics API: role-scoped response (Contributor sees own data, Validator sees institution, Admin sees network) \- \[ \] CSV export endpoint per metric with date range \+ institution filter \- \[ \] Frontend: KPI tiles with sparkline charts (Chart.js or recharts), delta badges (↑/↓ %) \- \[ \] Frontend: Posts by Institution horizontal bar chart (Admin only) \- \[ \] Frontend: AI Performance section with insufficient-data guard (\< 20 events → dimmed state) \- \[ \] Frontend: Operational Health section (Admin only) \- \[ \] Frontend: Full Report drill-down with per-day table and submission-level table, CSV download

**Acceptance Condition:** An Administrator opens the Analytics Dashboard and sees: (a) the average posting delay compared to last month, (b) the content completeness rate for the current period, (c) the total posts published this month per institution. They click “Download CSV” on the AVG POSTING DELAY Full Report and receive a file with per-day breakdown scoped to their selected time range.

**Duration Estimate:** 1 week

---

---

# **GENERAL OBJECTIVE 3**

## **Scheduling, Automated Publishing, and AI-Assisted Content Support**

*“To improve posting timeliness and reduce caption drafting effort, through an automated scheduling, publishing, and AI-assisted content subsystem that (a) increases scheduled post publish success rate to ≥95% within ±5 minutes of the assigned time, (b) achieves a ≥60% contributor acceptance or accept-with-edits rate on AI caption suggestions generated for ≥90% of uploaded images, and (c) attains an average AI suggestion quality rating of ≥4.0/5.0 across at least 20 evaluation submissions, by the end of Module 3.”*

**Success Metrics:** \- ≥95% automated publish success rate within ±5 minutes \- AI caption suggestions generated for ≥90% of uploaded images where API is available \- ≥60% contributor acceptance or accept-with-edits rate on caption suggestions \- Average AI suggestion quality rating ≥4.0/5.0 across ≥20 evaluation submissions \- AI image classification accuracy ≥80% on 50-image held-out evaluation set \- ≥70% of contributors rating top media recommendation as “relevant” or “highly relevant”

**Delivery Target:** End of Module 3

---

### **Specific Objective 3.1**

*“To increase scheduling efficiency and eliminate publication time conflicts across member institutions, by implementing a visual calendar scheduling module that enables administrators to assign publication dates and times to approved content, with built-in conflict detection and a consolidated master scheduling view.”*

**What must be built (UC-3.1 calendar portion):** \- Master Calendar: week/month toggle, all slot reservations and publication states across all institutions, color-coded by institution \- Calendar card states: PENDING (grey dashed), SCHEDULED (institution color), PUBLISHED (green), PUBLISHED\_MANUAL (teal), PUBLISH\_FAILED (red), ADMIN\_DIRECT\_POST (orange) \- Contributor/Validator view: read-only, timing and institution badge visible network-wide, content of other institutions’ posts hidden \- Administrator view: fully editable, can drag to reschedule SCHEDULED posts with guard rail re-validation \- Admin drag-reschedule: if new slot violates hard rule → warning modal with violation detail, Admin must enter written reason to override → immutable audit log entry

**Development Tasks:** \- \[ \] Master Calendar API: returns all active slot reservations \+ submission states, role-scoped detail levels \- \[ \] Reschedule API (Admin only): PATCH /api/submissions/:id/reschedule with new scheduled\_at, re-validates all guard rails, records audit entry \- \[ \] Frontend: Calendar component (week/month view, drag-and-drop for Admin, state-colored cards) \- \[ \] Frontend: Keyboard-accessible “Reschedule” button alternative to drag (opens date-time picker dialog) — WCAG 2.1 AA requirement \- \[ \] Frontend: Guard rail override confirmation modal with written reason field \- \[ \] Guard rail re-validation on reschedule: same rules as initial slot selection (GR-H1, H2, H3)

**Acceptance Condition:** The Master Calendar shows all scheduled posts from all institutions. A Contributor from CIT-U can see that 10:00 AM Tuesday is taken by Silliman (institution badge visible) and selects 2:00 PM instead. The Administrator can drag the 2:00 PM post to 3:00 PM, is warned about a conflict if another post is at 3:15 PM, and can override with a written reason.

**Duration Estimate:** 0.5 weeks (calendar UI; publishing pipeline is 3.2)

---

### **Specific Objective 3.2**

*“Integrate the Facebook Graph API using Standard Access to automatically publish approved and scheduled content to the connected Facebook Page, achieving a publish success rate of ≥95% within ±5 minutes of the scheduled time across at least 20 test publications during pilot testing.”*

**What must be built (UC-3.1 publishing pipeline):** \- Publishing scheduler: cron job fires every minute, queries state \= 'SCHEDULED' AND scheduled\_at \<= NOW() AND scheduled\_at \>= NOW() \- INTERVAL '5 minutes' \- Two-step photo publishing (image-only submissions): \- Step 1: POST /{PAGE\_ID}/photos with url \= asset\_storage\_url, published \= false → receives photo\_id per image \- Step 2: POST /{PAGE\_ID}/feed with message \= caption, attached\_media \= \[{media\_fbid: photo\_id}, ...\], published \= true → receives platform\_post\_id \- If Step 2 fails after all retries: attempt DELETE of all staged photo\_ids (cleanup), record any undeleted photo\_ids in audit log \- Single-call video publishing (video-only submissions): POST /{PAGE\_ID}/videos with file\_url, description, published \= true \- Mixed media (images \+ video): not supported for automated publishing in pilot → transitions directly to PUBLISH\_FAILED, Admin alerted for manual fallback \- Exponential backoff retry: 3 retries at 5s, 25s, 125s (GR-T5) \- On success: submission → PUBLISHED, platform\_post\_id stored, Contributor notified (T5), calendar card → green \- On all retries failed: submission → PUBLISH\_FAILED, Admin notified (T7), calendar card → red \- Stale submission detector: cron job fires every 5 minutes, finds state \= 'SCHEDULED' AND scheduled\_at \< NOW() \- 5 min → transitions to PUBLISH\_FAILED (GR-T9) \- Facebook Page Access Token management: encrypted at rest with AES-256-GCM, key in Render env

**How the ≥95% / ±5 minutes metric is measured:** Count of submissions that transitioned to PUBLISHED within 5 minutes of scheduled\_at ÷ total SCHEDULED submissions attempted. Track published\_at \- scheduled\_at for each publication. Report this as the primary publishing performance KPI. Note: missed windows due to Render free-tier dormancy do not count against the SLA if resolved via manual fallback within 30 minutes (per SRS constraint mitigation).

**Development Tasks:** \- \[ \] Facebook Page Access Token: store encrypted (AES-256-GCM), TokenEncryptionService.encryptToken() / .decryptToken() \- \[ \] publishing\_attempts table: submission\_id FK, attempt\_number, attempted\_at, result ENUM('SUCCESS','FAILED'), error\_detail, photo\_ids\_staged \- \[ \] Publishing scheduler cron (every minute): query SCHEDULED submissions in window, process each \- \[ \] Two-step photo publish: loop each image → stage → collect photo\_ids → single feed post \- \[ \] Orphaned photo cleanup: on Step 2 failure, DELETE each staged photo\_id, log uncleaned IDs \- \[ \] Video publish: single-call method, same retry logic \- \[ \] Exponential backoff retry loop with 5s / 25s / 125s delays \- \[ \] Stale submission detector cron (every 5 minutes): SCHEDULED → PUBLISH\_FAILED for missed windows \- \[ \] GR-T4: daily token health check API call → INVALID → suspend publishing, alert Admin (T13) \- \[ \] GR-T3: token expiry \< 7 days → warn Admin (T12) \- \[ \] Token re-authentication flow: OAuth 2.0 callback → new long-lived token → encrypt → store \- \[ \] Keep-alive mechanism for Render free tier (either UptimeRobot ping or Render Cron Job service) \- \[ \] 20 manual test publications documented during pilot to measure ±5 minute accuracy

**Acceptance Condition:** 20 test posts are scheduled across the pilot period. At least 19 of them (95%) publish to the DASIG Facebook Page within 5 minutes of their scheduled time. Each published post transitions to PUBLISHED state with the platform\_post\_id stored and the calendar card turning green.

**Duration Estimate:** 1.5 weeks

---

### **Specific Objective 3.3**

*“To ensure uninterrupted publishing workflow continuity in the event of API access constraints, by implementing a manual publishing fallback mechanism that enables administrators to directly publish prepared content.”*

**What must be built (UC-3.4 \+ UC-3.5 Category A):** \- Manual Publishing Fallback Panel in the Resolution Center: appears on PUBLISH\_FAILED submissions and on SCHEDULED submissions when token health is failed \- Three-step workflow: (1) Copy content (caption copy button, individual image downloads, video download), (2) Open DASIG Facebook Page in new tab (preserves DASIGConnect session), (3) Record details (optional live post URL with format validation, optional notes) \- “Mark as Published” → submission → PUBLISHED\_MANUAL, audit log entry, Contributor \+ Validators notified (T6), calendar card → teal \- 2-hour abandonment timer: if Admin clicks “Publish Manually” but never clicks “Mark as Published” → auto-reset, logged as abandoned \- Resolution Center API Failures tab: lists all PUBLISH\_FAILED submissions, failure detail (error type, retry history), three action buttons (Retry / Publish Manually / Investigate Token) \- Retry action: transitions PUBLISH\_FAILED → SCHEDULED, resets retry counter, cron picks up in next cycle

**Development Tasks:** \- \[ \] Resolution Center API: GET /api/resolution/failures returns all PUBLISH\_FAILED submissions with failure details \- \[ \] Retry endpoint: POST /api/resolution/:id/retry → PUBLISH\_FAILED → SCHEDULED \- \[ \] Manual publish workflow API: POST /api/resolution/:id/manual-publish/start (records start timestamp), POST /api/resolution/:id/manual-publish/complete (records PUBLISHED\_MANUAL, URL, notes), POST /api/resolution/:id/manual-publish/cancel \- \[ \] Abandonment timer: scheduled job checks for manual\_publish\_started\_at \< NOW() \- 2 hours and auto-resets \- \[ \] PUBLISHED\_MANUAL state: record published\_manual\_at, platform\_post\_url (optional), admin\_notes (optional) \- \[ \] Immutable audit log entry on Mark as Published: all fields per SRS UC-3.4 Step 7 \- \[ \] Frontend: Resolution Center layout with tab navigation \- \[ \] Frontend: Manual Publishing Fallback Panel with numbered 3-step workflow, copy button, download links \- \[ \] Frontend: Optional URL field with https://www.facebook.com/\* format validation, error on invalid \- \[ \] Frontend: In-progress state with abandon timer countdown visible

**Acceptance Condition:** A PUBLISH\_FAILED submission appears in the Resolution Center. The Administrator clicks “Publish Manually,” downloads the image, opens Facebook in a new tab, creates the post manually, returns to DASIGConnect, pastes the URL, and clicks “Mark as Published.” The submission state changes to PUBLISHED\_MANUAL, the calendar card turns teal, and the Contributor receives an in-app notification within 30 seconds.

**Duration Estimate:** 0.5 weeks

---

### **Specific Objective 3.4**

*“Integrate an AI-powered caption generation module using a vision-capable language model (Claude with vision) that produces a suggested caption for ≥90% of uploaded images within 10 seconds of upload completion, with contributors able to accept, edit, or discard each suggestion. Contributor acceptance or accept-with-edits rate shall be ≥60% across at least 20 evaluation submissions.”*

**What must be built (UC-3.2):**

**Caption generation pipeline:** \- On-demand trigger: “Suggest Caption” button active only when at least one image is in the media list \- Sends to Anthropic Claude Vision API: base64-encoded image(s) \+ event\_title \+ current AI tags \- System prompt enforces exactly 3 variants (Professional / Community / Energetic), JSON-only output, 80–280 characters each, 5 safety constraints, English language, Philippine government-affiliated academic context \- Parses JSON response: \[{tone: "professional", caption: "..."}, ...\] \- Suggestion panel with tone label, caption text, character count, “Apply” button per variant \- Non-blocking disclaimer: “AI-generated — review carefully before using.” \- Four actions: Use (apply as-is), Edit (apply then modify), Re-generate (fresh API call), Dismiss (close panel) \- Re-generate: 3-second client-side debounce, replaces previous variants \- Rate limit: 30 requests/hour/user for caption generation \+ re-generate actions \- Graceful degradation: if API unavailable, “Suggest Caption” button hidden (no error shown) \- Video-only submissions: button hidden, tooltip explains image requirement

**Interaction logging (for the ≥60% acceptance metric):** \- ai\_caption\_interactions table logs every suggestion event \- Five action states: use, use\_then\_edited (applied then modified before submit), edit, re-generate, dismiss \- use\_then\_edited detection: on submit, compare caption field value to the applied variant — if different, update log from use to use\_then\_edited \- Acceptance rate \= COUNT(action IN ('use', 'use\_then\_edited')) / COUNT(all suggestion events) × 100% \- Target: ≥60% across ≥20 evaluation submissions

**How the ≥90% generation coverage target is measured:** \- Track: COUNT(images uploaded where 'Suggest Caption' was clicked and response received within 10 seconds) ÷ COUNT(all images uploaded) \- Note: the SRS phrases this as “for ≥90% of uploaded images” — this means the button must generate when clicked, not auto-generate on every upload. The 90% target is for availability when the feature is invoked.

**Development Tasks:** \- \[ \] CaptionService.generateVariants(images\[\], event\_title, tags\[\]) → calls Claude Vision, parses JSON, returns 3 variants \- \[ \] System prompt template stored in version-controlled file (not hardcoded), matches Classification Prompt Parameter Specification in SRS UC-3.2 \- \[ \] Rate limiter: per-user counter in Redis or DB (ai\_rate\_limits table with user\_id, window\_start, call\_count) \- \[ \] ai\_caption\_interactions table with all required fields \- \[ \] use\_then\_edited detection on submit: compare caption\_field\_value \!= applied\_variant\_text \- \[ \] API endpoint: POST /api/ai/caption with auth, rate limit check, Claude Vision call, response parse, log entry \- \[ \] Frontend: “Suggest Caption” button with loading indicator during API call \- \[ \] Frontend: Suggestion panel with 3 variant cards, tone labels, character counts, Apply buttons \- \[ \] Frontend: Re-generate button with 3-second debounce, loading state, replaces previous results \- \[ \] Frontend: Rate limit UI: buttons disabled with “Available again at \[HH:MM\]” when limit reached \- \[ \] Frontend: Button hidden for video-only submissions, tooltip on hover \- \[ \] Pilot evaluation: AI suggestion quality rating survey item (1–5 Likert scale) in supplementary UAT questionnaire

**Acceptance Condition:** A Contributor uploads a photo of a CIT-U awarding ceremony. Within 10 seconds of clicking “Suggest Caption,” three caption variants appear (one professional-tone, one community-tone, one energetic-tone). All three are in English, 80–280 characters, and factually relevant to the image. The Contributor edits the professional variant and submits. The interaction is logged as use\_then\_edited. Across 20 such evaluation submissions, ≥12 (60%) are logged as use or use\_then\_edited.

**Duration Estimate:** 1 week

---

### **Specific Objective 3.5**

*“To automate image organization and achieve a classification accuracy of ≥80% on a held-out evaluation set of 50 manually-labeled images, by developing an AI-powered image tagging feature that assigns uploaded images to a predefined set of at least 8 categories (e.g., Awarding Ceremony, Group Photo, Laboratory, Campus Life, Faculty Portrait, Sports Event, Document, Outdoor Shot).”*

**What must be built (UC-3.3 Stage 1):**

**Classification pipeline (auto-triggered on upload):** \- Fires asynchronously for every uploaded image (not on-demand) — non-blocking \- Sends base64-encoded image to Anthropic Claude Vision with classification prompt \- Prompt enforces: one category from the 8-category taxonomy, confidence score (0.00–1.00), image description (1–3 factual sentences in English), JSON-only output, always returns closest category (no null), safety constraint on personal information \- Stores: ai\_category, confidence\_score, ai\_description in assets table \- AI Tags panel in submission form: shows category \+ confidence percentage (e.g., “Group Photo — 91%”), editable (accept / change category from dropdown / add custom tag / remove) \- Tags in media library: displayed in Asset Detail Panel with confidence scores \- Classification failures: ai\_category \= NULL, asset flagged for GR-T7 hourly reconciliation \- Video assets: skip classification entirely (Claude Vision is image-only), display “Auto-tagging not available for video” in tags panel

**Evaluation plan for ≥80% accuracy:** \- Assemble 50-image held-out evaluation set: 6–7 images per category, real DASIG-style Philippine academic event photos \- Do NOT use these 50 images during prompt development \- After prompt is finalized, run all 50 images through the classification endpoint \- Compare ai\_category to manually assigned ground truth label \- Accuracy \= correct\_classifications / 50 × 100% \- Report results with acknowledgment that 50 images is a minimum baseline; recommend expanding to 160+ images before full deployment accuracy claims

**Development Tasks:** \- \[ \] ClassificationService.classify(image\_bytes) → calls Claude Vision with classification prompt, parses JSON \- \[ \] Classification prompt template in version-controlled file with all 8 categories, JSON output format, confidence score, image description, safety constraints \- \[ \] Wire ClassificationService into image upload handler (asynchronous, non-blocking — upload completes regardless) \- \[ \] assets table: ai\_category, confidence\_score, ai\_description columns \- \[ \] GR-T7 reconciliation job: hourly, finds embedding IS NULL AND asset\_type \= 'IMAGE' AND created\_at \< NOW() \- 1 hour, re-runs Stage 1 \+ Stage 2 pipeline \- \[ \] Frontend: AI Tags panel showing category \+ confidence badge, editable dropdown, custom tag field \- \[ \] Frontend: “Processing…” badge while classification in progress, “Auto-tagging unavailable” if failed \- \[ \] Evaluate 50-image held-out set after prompt finalization, document results

**Acceptance Condition:** A Contributor uploads a photo of students receiving awards. Within 10 seconds, the AI Tags panel shows “Awarding Ceremony — 88%.” The Contributor can change this to “Group Photo” if they disagree. On the 50-image evaluation set, at least 40 of 50 images (80%) are assigned the correct category.

**Duration Estimate:** 0.5 weeks (classification pipeline; embedding is part of 3.6)

---

### **Specific Objective 3.6**

*“Develop an intelligent media recommendation feature that, upon entry of an event title and tags during submission, returns the top 5 related images from the institution’s repository within 3 seconds, with ≥70% of contributors rating the top recommendation as ‘relevant’ or ‘highly relevant’ in a usability test with at least 10 participants.”*

**What must be built (UC-3.3 Stage 2):**

**Embedding generation pipeline (runs after Stage 1):** \- Immediately after ClassificationService returns image\_description, send description to Voyage AI voyage-3-lite model \- Receives 1024-dimensional float vector, stored in assets.embedding vector(1024) (pgvector) \- Asynchronous, non-blocking relative to the Contributor’s form

**Semantic media recommendation:** \- Triggered as Contributor types event title, with 500ms inactivity debounce, minimum 3 characters \- Generates a text embedding of the event title using Voyage AI (text-to-embedding, same model) \- Executes pgvector cosine distance search against all institution assets with stored embeddings: sql   SELECT asset\_id, title, thumbnail\_url, (embedding \<=\> $query\_vector) AS distance   FROM assets   WHERE institution\_id \= $contrib\_institution\_id     AND embedding IS NOT NULL     AND asset\_id NOT IN ($already\_attached)   ORDER BY distance ASC   LIMIT 5   HAVING distance \<= 0.35 \- Returns within 3 seconds (pgvector IVFFlat index required for performance) \- Displays Recommended Media panel: up to 5 cards, thumbnail, title, category tag, “Add to Submission” button \- Panel refreshes on 10+ character title change (same 500ms debounce) \- Interaction logging: recommendation\_shown, asset\_previewed, asset\_added, asset\_ignored with cosine distance per event

**How the ≥70% relevance metric is measured:** \- During pilot UAT, present a supplementary survey item for each session where recommendations were shown: “Was the top recommended asset relevant to your submission?” (1-Relevant, 2-Highly Relevant, 3-Somewhat Relevant, 4-Not Relevant) \- Relevance rate \= COUNT(ratings 1 or 2\) / COUNT(all survey responses) × 100% \- Minimum 10 participants required

**Development Tasks:** \- \[ \] Enable pgvector extension (CREATE EXTENSION vector) \- \[ \] assets.embedding vector(1024) column \- \[ \] IVFFlat index: CREATE INDEX ON assets USING ivfflat (embedding vector\_cosine\_ops) WITH (lists \= 100\) \- \[ \] EmbeddingService.embedDescription(text) → Voyage AI API call, returns 1024-dim vector \- \[ \] EmbeddingService.embedTitle(text) → used for recommendation query \- \[ \] Wire EmbeddingService after ClassificationService completes (sequential, both async) \- \[ \] Recommendation API: POST /api/ai/recommend with {title, attached\_asset\_ids, institution\_id} → cosine search → top 5 \- \[ \] Frontend: Recommended Media panel with loading state, thumbnail cards, “Add to Submission” / “Added” button states \- \[ \] Frontend: 500ms debounce on title input, panel hides if title \< 3 chars \- \[ \] Frontend: Panel refresh on 10+ char change \- \[ \] Recommendation interaction logging to ai\_recommendation\_interactions table \- \[ \] UAT relevance survey administered to 10+ participants with panel interactions observed

**Acceptance Condition:** A Contributor types “CIT-U Innovation Award 2025” in the event title field. Within 3 seconds, 3 images from the institution’s library appear in the Recommended Media panel — all showing past awarding or group photo events. They click “Add to Submission” on the first one. The asset is appended to their media list without uploading. In the UAT survey, at least 7 of 10 participants rate the top recommendation as “relevant” or “highly relevant.”

**Duration Estimate:** 0.5 weeks

---

### **Specific Objective 3.7**

*“To validate that DASIGConnect meets acceptable usability standards (Brooke, 1996\) for its intended users, by administering a System Usability Scale (SUS) survey among designated DASIG contributors and administrators, targeting a minimum SUS score of 70.”*

**What must be built / executed:**

This objective is an evaluation task, not a development task — but it requires development to support it:

**SUS Evaluation Plan:** \- Instrument: Standard 10-item SUS questionnaire (Brooke, 1996), alternating positive/negative phrasing \- Participants: Minimum 10 pilot users, drawn from Contributors, Validators, and Administrators combined \- Evaluation tasks covering all three primary workflows: 1\. Submit a content post with media (tests Module 1\) 2\. Validate a submission and request revision (tests Module 2\) 3\. Access the Master Calendar and identify an available time slot (tests Modules 1 \+ 3\) \- Scoring: standard SUS formula (sum of odd items \- 5\) \+ (25 \- sum of even items) × 2.5, sum all 10 results \- Report: overall aggregate score \+ per-role breakdown (Contributor / Validator / Administrator) \- Threshold: ≥70 (or ≥68 per Bangor et al. 2009 as noted in SRS v1.6 — align with adviser) \- If score falls below threshold: mandatory usability review cycle, targeted UI revision, re-evaluation before full deployment

**Preparation tasks (built during Module 2 and 3):** \- \[ \] Accessible UI throughout: WCAG 2.1 AA compliance on all primary pages (labels, alt text, color not sole differentiator, keyboard navigability, visible focus indicators) \- \[ \] Status badges include text labels (not color only) — e.g., “PENDING” text shown alongside yellow badge \- \[ \] All interactive elements keyboard-navigable \- \[ \] Drag-and-drop (Admin calendar reschedule) has keyboard-accessible alternative (Reschedule button \+ date-time picker dialog) \- \[ \] Prepare SUS questionnaire in digital form (Google Forms, Typeform, or printed) \- \[ \] Prepare task scenario cards for evaluation participants \- \[ \] Schedule evaluation session with at least 10 DASIG pilot users

**Acceptance Condition:** At least 10 participants complete the SUS questionnaire after performing the three evaluation tasks. The aggregate SUS score is ≥70. Per-role scores are documented. Any score below 70 triggers an identified list of UI improvements with a re-evaluation plan.

**Duration Estimate:** Evaluation execution is 1–2 days; UI preparation runs across all modules

---

---

# **CROSS-CUTTING DEVELOPMENT CONCERNS**

## **Concerns That Affect All Objectives**

### **Immutable Audit Log (affects 2.1, 2.5, 3.2, 3.3, 3.7)**

Every state-changing action across the entire system must be recorded in an audit log that cannot be edited or deleted. This is not optional — it is cited in the SRS security and maintainability NFRs.

* audit\_logs table: id, actor\_id FK, actor\_role, action\_type, resource\_type, resource\_id, institution\_id, old\_state, new\_state, detail JSONB, ip\_address, timestamp

* Audit entries written for: all submission state transitions, all validation actions \+ remarks, all Admin override actions, all direct posts, all guard rail threshold changes, all token re-authentications, all cross-institution Admin actions, all manual publishes, all AI feature interactions

* Audit log is append-only: no UPDATE or DELETE on audit\_logs — enforced via RLS (USING (false) for UPDATE/DELETE)

### **Graceful AI Degradation (affects 3.4, 3.5, 3.6)**

All AI features must degrade silently — the submission workflow is never blocked: \- Caption generation unavailable → button hidden, no error \- Image classification unavailable → “Processing…” → “Auto-tagging unavailable”, form still works \- Embedding generation fails → asset stored without embedding, flagged for GR-T7 reconciliation \- Recommendation returns no results → panel hidden, no error \- Video assets → skip classification entirely, no error

### **Guard Rail Configuration Panel (affects 1.3, and all guard rail enforcement)**

Guard rail thresholds (GR-H1 30 min buffer, GR-H2 2 hours, GR-H3 30 days, GR-S1 3 posts, GR-S2 6 posts/day) must be configurable by the Administrator without code changes. Store thresholds in a guard\_rail\_config table with one record per rule. Changes logged in audit log with old/new values. The Guard Rail Configuration panel lives in Admin Settings.

### **Pre- and Post-Implementation KPI Baseline (affects 2.5, and all metrics)**

Before pilot deployment, the team must collect baseline data from the DASIG Facebook Page’s public posting history: \- Average posting delay: days between event occurrence and Facebook post publication (estimated from post content) \- Content completeness: presence of at least one photo AND caption ≥50 characters (externally observable) \- Posting frequency: posts per month over the past 6 months

This baseline is compared against the post-implementation KPI values from the analytics dashboard. The comparison is the core evidence for the project’s research questions. **Assign one member to collect and document this baseline data before pilot deployment begins.**

---

# **MASTER OBJECTIVES-TO-FEATURES TRACEABILITY TABLE**

| Proposal Objective | Feature(s) Built | UC Reference | Success Metric | Evidence Source |
| :---- | :---- | :---- | :---- | :---- |
| GO1 — ≥95% form completion rate | Submission form with server-enforced mandatory fields \+ field-level errors | UC-1.3 | ≥95% first-attempt completion | submissions table: completeness flag at PENDING transition |
| SO1.1 — Three-institution independent submission | RBAC, role-based routing, isolated workspaces | UC-1.1, UC-1.2 | 3 institutions independently operational | Integration test: cross-institution 404 |
| SO1.2 — Secure auth \+ institution isolation | JWT \+ 5-layer tenant isolation \+ invitation token flow | UC-1.1, UC-1.2 | Zero cross-institution data leaks | RLS integration test suite |
| SO1.3 — Mandatory field form validation | Server-enforced fields, magic bytes validation, field error messages | UC-1.3 | ≥95% first-attempt completion | Pilot submission records |
| GO2a — 100% admin-reviewed approval | GR-H4: cron publishes SCHEDULED only; Validator approval required for SCHEDULED | UC-2.1, UC-3.1 | 0 unauthorized publications | Query: published submissions without approval record |
| GO2b — ≤5 min email notification | Resend email integration with retry, all 17 triggers | UC-2.3 | ≥95% emails within 5 min | notifications delivery timestamps |
| GO2c — Searchable media library | pgvector/pg\_trgm search, AI tag filter chips, ≤2 second results | UC-2.2 | ≤2 sec for 1,000 assets | Load test results |
| GO2 — SUS ≥70 | Full system usability \+ WCAG AA compliance | UC-2.4, all | SUS ≥70 (10+ participants) | SUS questionnaire results |
| SO2.1 — Validation interface | Validation Queue, review lock, 3 actions with remarks | UC-2.1 | 100% approval before publish | Audit log: all published submissions have approval record |
| SO2.2 — Media repository | Media library, AI tags, asset reuse, ≤2 sec search | UC-2.2 | ≤2 sec search, asset reuse tracked | Performance test \+ “Used In” counts |
| SO2.3 — Email notifications \+ SUS | Resend email, T2–T4 state change emails | UC-2.3 | ≤5 min in ≥95% cases | Notification delivery log |
| SO2.4 — In-app notifications \+ remarks | SSE real-time push, remarks field, revision history | UC-2.3, UC-2.1 | ≤30 sec in-app delivery | SSE latency measurement |
| SO2.5 — Analytics dashboard | KPI tiles, sparklines, posting frequency, completeness rate | UC-2.4 | ≥4 posts/month, ≥95% completeness | Dashboard KPI values vs targets |
| GO3a — ≥95% publish success ±5 min | Publishing scheduler, 2-step photo method, retry logic, stale detection | UC-3.1 | ≥95% within ±5 min | published\_at \- scheduled\_at distribution |
| GO3b — ≥60% caption acceptance | Caption generation, interaction logging, acceptance rate KPI | UC-3.2 | ≥60% use/use\_then\_edited | ai\_caption\_interactions table |
| GO3c — AI quality rating ≥4.0/5.0 | Caption generation \+ UAT survey | UC-3.2 | ≥4.0/5.0 on ≥20 submissions | Supplementary survey results |
| SO3.1 — Visual calendar \+ conflict detection | Master Calendar, drag-reschedule, guard rail re-validation | UC-3.1 | Zero scheduling conflicts | Guard rail enforcement test |
| SO3.2 — Facebook Graph API publishing | 2-step photo publish, video publish, exponential backoff | UC-3.1 | ≥95% within ±5 min on 20 test pubs | Publishing attempt log |
| SO3.3 — Manual publishing fallback | Resolution Center, 3-step manual workflow, PUBLISHED\_MANUAL | UC-3.4 | Fallback available for every PUBLISH\_FAILED | Resolution Center functionality test |
| SO3.4 — AI caption generation | Claude Vision API, 3 variants, interaction logging | UC-3.2 | ≥90% generation rate, ≥60% acceptance | ai\_caption\_interactions \+ availability log |
| SO3.5 — AI image classification | Claude Vision classification, 8 categories, confidence scores | UC-3.3 Stage 1 | ≥80% accuracy on 50-image eval set | Held-out evaluation results |
| SO3.6 — Media recommendation | Voyage AI embeddings, pgvector cosine search, top 5 in ≤3 sec | UC-3.3 Stage 2 | ≥70% “relevant” rating from 10 participants | UAT relevance survey |
| SO3.7 — SUS evaluation | WCAG compliance, SUS questionnaire administration | All modules | SUS ≥70 (10+ participants) | SUS scoring results |


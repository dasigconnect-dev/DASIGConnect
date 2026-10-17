#

## **CEBU INSTITUTE OF TECHNOLOGY**

**UNIVERSITY**

COLLEGE OF COMPUTER STUDIES

# **Software Test Document**

## _for_

## **DASIGConnect**

---

# **Change History** {#change-history}

| Version | Date | Author(s) | Description of Changes | Reason |
| :--- | :--- | :--- | :--- | :--- |
| 1.0 | 10/11/2026 | Chris Daniel Cabatana, Jaylord Bayonas, Lerah Caones, Mark Anton Camoro, Richemmae V. Bigno | Initial draft — comprehensive test plan, 25 use-case-aligned test cases (`UC-1.1`–`UC-3.6`), execution logs, test results matrix, and incident reports based on the Software Requirements Specification (`SRS` v6.0). | Initial baseline for MVP system testing. |
| 2.0 | 10/16/2026 | Lerah A. Caones | Re-executed the full automated suite on the dev baseline `247522c` (deployed release v3.0.1 plus test/lint fixes): backend 899 tests in 93 classes, 0 failures; frontend lint 0 errors; production build pass; Playwright 33 tests, 30 pass / 2 fail / 1 flaky. Added 2.4 Test Types and Procedures; added Cases 26–30 (Media Trash, runtime guard-rail configuration with `GR-T1`/`GR-T6`, durable media-AI queue, mobile operability, and acceptance tests for refactoring priorities RP-01–RP-05); replaced A.2 with an expected-versus-actual results matrix from the 16 Oct run; re-verified incidents (INC-01, INC-02, INC-04 resolved; INC-11–INC-14 opened); aligned tools to Flyway V1–V122, Gemini, Resend, and Railway. | VF-01–VF-05 (MVP Validation Findings, 12 Oct 2026): every refactoring priority RP-01–RP-05 needs an acceptance test and a measurable pass condition, and Findings #2 and #4 require role-task and mobile-viewport tests that v1.0 did not have. Results also had to reflect code merged after validation (PRs #325–#402). |
| 2.1 | 10/17/2026 | Richemmae V. Bigno | Hardened `SystemHealthService.escape()` against CSV formula injection (`CWE-1236`) by neutralizing `=+-@\t\r` trigger characters with a leading apostrophe (`'`) and verified with unit test; synced regression test incident statuses by moving INC-01 through INC-05 to Resolved; updated UC-3.4 live content quality telemetry and UC-3.5 CSV sanitization specifications; restored full documentation repository (`docs/md`). | Regression incident closure, security hardening of System Health CSV exports (`INC-05`), and documentation synchronization following demo readiness refactoring. |

---

# **Table of Contents** {#table-of-contents}

- [Change History](#change-history)
- [Table of Contents](#table-of-contents)
- [1. Introduction](#1-introduction)
  - [1.1. System Overview](#11-system-overview)
  - [1.2. Test Approach](#12-test-approach)
  - [1.3. Definitions and Acronyms](#13-definitions-and-acronyms)
- [2. Test Plan](#2-test-plan)
  - [2.1 Features to be Tested](#21-features-to-be-tested)
  - [2.2 Features not to be Tested](#22-features-not-to-be-tested)
  - [3.3 Testing Tools and Environment](#33-testing-tools-and-environment)
- [3. Test Cases](#3-test-cases)
  - [3.1 Case-1: Administrator Account Management & 3-Admin Cap Enforcement (UC-1.1)](#31-case-1-administrator-account-management--3-admin-cap-enforcement-uc-11)
  - [3.2 Case-2: Admin Promotion Proposal & Admin Owner Transfer Governance (UC-1.1 A7, A8)](#32-case-2-admin-promotion-proposal--admin-owner-transfer-governance-uc-11-a7-a8)
  - [3.3 Case-3: Institution Lifecycle & Protected Default Institution Safeguards (UC-1.2)](#33-case-3-institution-lifecycle--protected-default-institution-safeguards-uc-12)
  - [3.4 Case-4: Contributor Account Provisioning & Footprint-Aware Deletion (UC-1.3)](#34-case-4-contributor-account-provisioning--footprint-aware-deletion-uc-13)
  - [3.5 Case-5: Authentication, Account Lockout, Password Policy & DB Session Revocation (UC-1.4)](#35-case-5-authentication-account-lockout-password-policy--db-session-revocation-uc-14)
  - [3.6 Case-6: Three-Step Post Composer, Readiness Checklist & Post Templates (UC-1.5)](#36-case-6-three-step-post-composer-readiness-checklist--post-templates-uc-15)
  - [3.7 Case-7: AI Template Synthesis from Top-Performing Facebook Posts (UC-1.5 A10)](#37-case-7-ai-template-synthesis-from-top-performing-facebook-posts-uc-15-a10)
  - [3.8 Case-8: AI Caption Generation, Writing Check & Fancy Unicode Text Styling (UC-1.6)](#38-case-8-ai-caption-generation-writing-check--fancy-unicode-text-styling-uc-16)
  - [3.9 Case-9: Media Attachment Rules, AI-Suggested Media & Album Auto-Match (UC-1.7)](#39-case-9-media-attachment-rules-ai-suggested-media--album-auto-match-uc-17)
  - [3.10 Case-10: Peak-Hour Engagement Recommendations & Guard-Rail Filtering (UC-1.8)](#310-case-10-peak-hour-engagement-recommendations--guard-rail-filtering-uc-18)
  - [3.11 Case-11: Draft Submission, Guard-Rail Re-Validation, Withdrawal & Fast-Track Prioritization (UC-1.9)](#311-case-11-draft-submission-guard-rail-re-validation-withdrawal--fast-track-prioritization-uc-19)
  - [3.12 Case-12: Moderator Account Management & Lateral Role Changes (UC-1.10)](#312-case-12-moderator-account-management--lateral-role-changes-uc-110)
  - [3.13 Case-13: Standalone Media Library Uploads, Nested Albums & Manual Tag Floor (UC-2.1)](#313-case-13-standalone-media-library-uploads-nested-albums--manual-tag-floor-uc-21)
  - [3.14 Case-14: Semantic Media Search, Keyword Fallback & Asset Detail Panel (UC-2.2)](#314-case-14-semantic-media-search-keyword-fallback--asset-detail-panel-uc-22)
  - [3.15 Case-15: Media History Tracking, Tiered Deletion Precedence & Duplicate Hash Detection (UC-2.3)](#315-case-15-media-history-tracking-tiered-deletion-precedence--duplicate-hash-detection-uc-23)
  - [3.16 Case-16: Approval Workflow, 15-Minute Review Lock & Self-Review Prevention (UC-2.4)](#316-case-16-approval-workflow-15-minute-review-lock--self-review-prevention-uc-24)
  - [3.17 Case-17: Edit-During-Review Diff Tracking, Restore Original & Late Publish Override (UC-2.4 A9–A15)](#317-case-17-edit-during-review-diff-tracking-restore-original--late-publish-override-uc-24-a9a15)
  - [3.18 Case-18: Automated Brand Watermarking, Aspect-Ratio Rendering & Opt-Out (UC-2.5)](#318-case-18-automated-brand-watermarking-aspect-ratio-rendering--opt-out-uc-25)
  - [3.19 Case-19: Master Calendar Visibility, Moderator Reschedule Caps & GiST Conflict Integrity (UC-3.1)](#319-case-19-master-calendar-visibility-moderator-reschedule-caps--gist-conflict-integrity-uc-31)
  - [3.20 Case-20: Automated Facebook Publishing, Atomic Claiming & Retry/Cleanup Pipeline (UC-3.2)](#320-case-20-automated-facebook-publishing-atomic-claiming--retrycleanup-pipeline-uc-32)
  - [3.21 Case-21: Missed Slot Sweep (`PUBLISH_FAILED` vs. `MISSED_REVIEW`) & Manual Publishing Fallback (UC-3.2 A2, UC-2.4 A8)](#321-case-21-missed-slot-sweep-publish_failed-vs-missed_review--manual-publishing-fallback-uc-32-a2-uc-24-a8)
  - [3.22 Case-22: Real-Time SSE Notifications, Cross-Session Read Sync & Messenger Delivery (UC-3.3)](#322-case-22-real-time-sse-notifications-cross-session-read-sync--messenger-delivery-uc-33)
  - [3.23 Case-23: Role-Scoped Social Engagement Analytics & CSV Formula-Injection Sanitization (UC-3.4)](#323-case-23-role-scoped-social-engagement-analytics--csv-formula-injection-sanitization-uc-34)
  - [3.24 Case-24: System Health Analytics, 16 Tracked Jobs, On-Demand Re-Run & Token Re-Auth (UC-3.5)](#324-case-24-system-health-analytics-16-tracked-jobs-on-demand-re-run--token-re-auth-uc-35)
  - [3.25 Case-25: Immutable Audit Log Review, Structured Diffs & Multi-Institution Tenant Isolation (UC-3.6, SRS 3.5)](#325-case-25-immutable-audit-log-review-structured-diffs--multi-institution-tenant-isolation-uc-36-srs-35)
- [Appendix (Test Logs)](#appendix-test-logs)
  - [A.1 Log for Test n](#a1-log-for-test-n)
  - [A.2 Test Results](#a2-test-results)
  - [A.3 Incident Report](#a3-incident-report)

---

# **1. Introduction** {#1-introduction}

## **1.1. System Overview** {#11-system-overview}

**DASIGConnect** is a centralized, web-based **Social Media Content Workflow and Scheduling Management System** developed as a capstone project (Team Code: `2526-sem2-it332-38`) for the **DOST Acadême–Science and Innovation Group (DASIG)** under the Department of Science and Technology (DOST) Region 7. The system replaces informal, ad hoc social media coordination across member Higher Education Institutions (HEIs)—such as Cebu Institute of Technology – University (CIT-U), Silliman University, and partner institutions—with a structured, traceable, role-based digital workflow integrated with the official DASIG Facebook Page.

DASIGConnect is engineered around a three-layer operational model designed to eliminate the administrative coordination bottleneck:

1. **Layer 1 — Self-Service (Contributors):** Institutional representatives draft event posts, attach media, utilize advisory AI tools, and self-schedule content within software-enforced guard rails.
2. **Layer 2 — Automation (System Rules and Cron Jobs):** Deterministic scheduling guard rails (30-minute conflict buffer, 2-hour minimum lead time, 30-day maximum horizon, 8:00 AM–8:00 PM posting window, 6-post daily cap, and 3-post active institutional quota) and 16 background scheduled jobs handle conflict prevention, automated Facebook Graph API publishing, stale-submission sweeps, embedding reconciliation, and multi-channel notifications.
3. **Layer 3 — Governance & Exception Handling (Moderators and Administrators):** Network-wide Moderators and Administrators triage and review the Validation Queue (`Approve`, `Request Revision`, `Reject`, or `Edit & Approve`), while Administrators retain exclusive authority over system governance, guard-rail overrides, manual publishing fallback, token re-authentication, watermark configuration, System Health, and the append-only Audit Log.

Architecturally, DASIGConnect operates as a three-tier client-server application:

- **Presentation Tier:** A React 19 + TypeScript Single-Page Application (SPA) built with Vite, Tailwind CSS v4, Radix UI / shadcn/ui, FullCalendar, Axios, and Server-Sent Events (SSE), deployed on Vercel.
- **Application Tier:** A stateless Spring Boot 4.0.6 (Java 21) REST API (`/api/v1`) deployed on Railway/Render, enforcing JWT authentication (8-hour TTL with database-backed revocation), Role-Based Access Control (RBAC), tenant scoping, state machines, and background cron scheduling.
- **Data & Storage Tier:** Supabase PostgreSQL with the `pgvector` extension (`VECTOR(1024)` cosine similarity) and Row-Level Security (RLS) policies for multi-institution tenant isolation, paired with Cloudflare R2 S3-compatible object storage using presigned `PUT` uploads and backend-proxied reads (`/api/v1/media-files/**`).
- **External Integrations:** Meta Facebook Graph API (`v25.0`) and Messenger Platform webhook, Anthropic Claude Vision API (`claude-haiku-4-5`), Voyage AI Embedding API (`voyage-4-lite`, 1024 dimensions), and SMTP/Resend transactional email delivery.

---

## **1.2. Test Approach** {#12-test-approach}

Testing for DASIGConnect follows a multi-layered **Verification and Validation (V&V)** strategy aligned with the project's Agile/Scrum 2-week sprint methodology, verifying every functional use case (`UC-1.1` through `UC-3.6`), business rule (`BR-ACC` through `BR-AI`), guard rail (`GR-H1`–`GR-T9`), non-functional requirement (`SRS Section 3.4`), and multi-tenant security boundary (`SRS Section 3.5`).

1. **Unit & Service-Layer Testing (White-Box):**
   - **Scope:** Business logic, state transitions, guard-rail calculations, account caps, password complexity validation, review lock lifecycles, watermark coordinate scaling, and CSV sanitization across all Spring Boot `@Service` classes.
   - **Technique:** Isolated unit tests using **JUnit 5 (Jupiter)**, **Mockito** (`@ExtendWith(MockitoExtension.class)`), and **AssertJ**. External APIs (Facebook Graph API, Anthropic Claude, Voyage AI, Cloudflare R2, SMTP) are mocked to deterministically test both normal execution and failure/timeout branches (`503`, `504`, `429`, exponential backoff).
2. **API Controller & Security Slice Testing (Grey-Box):**
   - **Scope:** REST endpoint contracts under `/api/v1/**`, request payload validation (`400 Bad Request`, `422 Unprocessable Entity`), conflict handling (`409 Conflict`, `423 Locked`), JWT filter authentication (`401 Unauthorized`), and role-based authorization (`@PreAuthorize` returning `403 Forbidden` or `404 Not Found` for cross-tenant isolation).
   - **Technique:** Spring `@WebMvcTest` slices importing `SecurityConfig` and executed via `MockMvc`.
3. **Database Migration, Concurrency & Tenant Isolation Testing (Integration):**
   - **Scope:** Flyway schema migrations (`V1`–`V104+`), PostgreSQL Row-Level Security (RLS) policies (`app.current_institution_id`, `app.current_role`), append-only audit log enforcement (`V78` RLS blocking `UPDATE`/`DELETE`), atomic conditional claims (`claimForPublishing`, `claimModeratorReschedule`), and the PostgreSQL GiST exclusion constraint (`V95`/`V96`) preventing concurrent `±30-minute` slot reservation races.
   - **Technique:** Repository/service integration tests and live SQL verification against PostgreSQL / H2 test contexts.
4. **End-to-End (E2E), UI Workflow & Performance Regression Testing (Black-Box):**
   - **Scope:** Full browser-level user journeys across Contributor, Moderator, and Administrator personas, including workspace bootstrap, SSE real-time notification badge updates (`INC`, `READ_SYNC`), debounced media search, 3-step post drafting, validation queue tab switching, FullCalendar event loading, analytics rendering, and bundle size budget verification.
   - **Technique:** Automated headless Chromium browser tests using **Playwright (`@playwright/test`)** against the production-mode Vite preview build (`npm run test:e2e`, `npm run test:performance`, `npm run check:bundle`) plus structured manual walkthroughs of visual/canvas interactions.
5. **Defect Tracking & Regression Verification:**
   - Any discrepancy between the SRS (`docs/md/srs/SRS.md` and `docs/md/srs/UC-*.md`) and system behavior is logged as an Incident in **Appendix A.3**, classified by severity, root-caused to the specific service/controller/component, and re-tested upon resolution.

---

## **1.3. Definitions and Acronyms** {#13-definitions-and-acronyms}

| Term / Acronym                                                                                                                                       | Definition                                                                                                                                                                                                                                                        |
| :--------------------------------------------------------------------------------------------------------------------------------------------------- | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **ACTIVE / PENDING / EXPIRED / INACTIVE / CANCELLED**                                                                                                | User account lifecycle states governed by the User Account State Machine (`SRS Section 1.4`).                                                                                                                                                                     |
| **Admin Owner**                                                                                                                                      | Governance designation (`is_admin_owner = true`) held by exactly one active Administrator at a time; required for actions targeting existing Admin accounts, erasing personal data, switching the connected Facebook Page, and transferring ownership (`UC-1.1`). |
| **Administrator**                                                                                                                                    | Network-wide user role (`role = admin`, `institution_id = NULL`) with full governance, approval, scheduling override, exception handling, System Health, Watermark, and Audit Log authority.                                                                      |
| **Contributor**                                                                                                                                      | Institution-scoped user role (`role = contributor`) responsible for drafting posts, uploading media, and self-scheduling content within guard rails for their assigned member HEI.                                                                                |
| **CWE-1236**                                                                                                                                         | Common Weakness Enumeration for Improper Neutralization of Formula Elements in a CSV File (Formula Injection), mitigated by prepending an apostrophe (`'`) to cells starting with `=`, `+`, `-`, `@`, `\t`, or `\r`.                                              |
| **DASIG**                                                                                                                                            | DOST Acadême–Science and Innovation Group under DOST Region 7.                                                                                                                                                                                                    |
| **DASIG Central Visayas**                                                                                                                            | Protected network-default institution seeded at deployment; exempt from deactivation and permanent deletion (`UC-1.2 A6, A8`).                                                                                                                                    |
| **DRAFT / PENDING / IN_REVIEW / NEEDS_REVISION / SCHEDULED / PUBLISHING / PUBLISHED / PUBLISHED_MANUAL / PUBLISH_FAILED / MISSED_REVIEW / REJECTED** | Submission lifecycle states governed by the content workflow state machine (`SRS Section 1.3 & 3.2`).                                                                                                                                                             |
| **E2E**                                                                                                                                              | End-to-End testing conducted through a real browser environment (Playwright + Chromium).                                                                                                                                                                          |
| **Fast-Track (Live Event)**                                                                                                                          | Submission mode (`UC-1.5`, `UC-1.9 A5`, `UC-3.2 A5`) that bypasses scheduled-slot guard rails, sorts to the top of the active Validation Queue, and publishes asynchronously immediately upon approval.                                                           |
| **GiST**                                                                                                                                             | Generalized Search Tree index in PostgreSQL, used in migration `V95`/`V96` to enforce database-level exclusion constraints on overlapping `±30-minute` slot reservations.                                                                                         |
| **Guard Rail (`GR-H*`, `GR-S*`, `GR-T*`)**                                                                                                           | System-enforced business rule classified as Hard (`GR-H1..H4`, blocks action), Soft (`GR-S1..S2`, warns actor), or Trigger (`GR-T1..T9`, autonomous cron action).                                                                                                 |
| **HEI**                                                                                                                                              | Higher Education Institution participating in the DASIG network (e.g., CIT-U, Silliman University).                                                                                                                                                               |
| **JWT**                                                                                                                                              | JSON Web Token used for stateless 8-hour session authentication (`HS256`), carrying `role`, `session_version`, and `institution_id` (for Contributors).                                                                                                           |
| **Moderator**                                                                                                                                        | Network-wide reviewer role (`role = moderator`, `institution_id = NULL`, formerly termed `Validator`) with access to the network-wide Validation Queue, Master Calendar (with capped rescheduling), and Contributor invitation management (`UC-1.10`).            |
| **MockMvc**                                                                                                                                          | Spring Test framework component allowing HTTP controller testing with full Spring Security filter chains without starting an external servlet container.                                                                                                          |
| **pgvector**                                                                                                                                         | PostgreSQL extension storing 1024-dimensional Voyage AI embeddings (`VECTOR(1024)`) for cosine-distance (`<=>`) semantic similarity search.                                                                                                                       |
| **R2**                                                                                                                                               | Cloudflare R2 S3-compatible object storage used for raw media assets and temporary watermarked derivatives.                                                                                                                                                       |
| **RBAC**                                                                                                                                             | Role-Based Access Control enforced server-side via Spring Security `@PreAuthorize` and route gates.                                                                                                                                                               |
| **RLS**                                                                                                                                              | PostgreSQL Row-Level Security enforcing multi-institution tenant isolation at the database engine layer (`SRS Section 3.5`).                                                                                                                                      |
| **SSE**                                                                                                                                              | Server-Sent Events (`GET /api/v1/notifications/stream`) used for real-time in-app notification delivery and cross-session read-state synchronization (`UC-3.3`).                                                                                                  |
| **SRS**                                                                                                                                              | Software Requirements Specification (`docs/md/srs/SRS.md`).                                                                                                                                                                                                       |
| **STD**                                                                                                                                              | Software Test Document (this document).                                                                                                                                                                                                                           |
| **SUS**                                                                                                                                              | System Usability Scale questionnaire targeting a minimum score of `≥68` (`SRS Section 3.4.8`).                                                                                                                                                                    |
| **Two-Step Publishing**                                                                                                                              | Verified Facebook Graph API photo publishing flow: Step 1 stages unpublished photos (`POST /{PAGE_ID}/photos` with `published=false`), and Step 2 publishes a single feed post (`POST /{PAGE_ID}/feed` with `attached_media`).                                    |

---

# **2. Test Plan** {#2-test-plan}

## **2.1 Features to be Tested** {#21-features-to-be-tested}

All functional requirements across the three development modules (`UC-1.1` through `UC-3.6`), scheduling guard rails (`GR-H1`–`GR-T9`), application business rules (`BR-ACC`–`BR-AI`), and non-functional requirements defined in `README.md`, `docs/md/srs/SRS.md`, and `docs/md/srs/UC-*.md` are in scope for testing:

| Module / Category                                | SRS Reference                               | Feature / Capability Under Test                                                                                                                                                                                                                                                                                                                                        | Target Test Cases    |
| :----------------------------------------------- | :------------------------------------------ | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :------------------- |
| **Module 1: User Access & Content Creation**     | `UC-1.1`, `BR-ACC-04`                       | Administrator account invitations (72h SHA-256 hashed tokens), uniform 3-Admin cap (`ACTIVE` + pending invites + pending promotions), Admin Owner-gated deactivation/reactivation/deletion/erasure, propose/confirm Admin promotion (`72h` expiry), and Admin Owner transfer (`24h` expiry).                                                                           | `Case-1`, `Case-2`   |
| **Module 1: User Access & Content Creation**     | `UC-1.2`, `BR-ACC-01`                       | Institution creation, editing, deactivation (blocked if Contributors remain), reactivation, Contributor reassignment, permanent deletion with confirmation code, and protection of `"DASIG Central Visayas"`.                                                                                                                                                          | `Case-3`             |
| **Module 1: User Access & Content Creation**     | `UC-1.3`, `BR-ACC-01..04`                   | Contributor invitation (Admin & Moderator), 72h token activation, re-invite block on `INACTIVE` accounts, Moderator scope restriction to personally sent invites, and footprint-aware deletion (`USER_REMOVED` vs. `USER_DELETED`) / Right-to-be-Forgotten erasure (`USER_ANONYMIZED`).                                                                                | `Case-4`             |
| **Module 1: User Access & Content Creation**     | `UC-1.4`, `BR-ACC-02..07`                   | JWT login (8h TTL), 5-attempt / 15-minute account lockout, 12-char password complexity policy, single-token logout (`revoked_tokens`) vs. account-wide session invalidation (`session_version`), session expiry modal, and 10-minute Facebook Messenger link code.                                                                                                     | `Case-5`             |
| **Module 1: User Access & Content Creation**     | `UC-1.5`, `BR-SUB-03`                       | 3-step composer (`Media → Post Details → Organize & Schedule`), 0–100 Readiness Checklist (75% Required, 25% Recommended), built-in & personal templates, **Generate from Top Posts** (`UC-1.5 A10`), autosave, dirty-state warning, and `guardrails_enforced` runtime switch.                                                                                         | `Case-6`, `Case-7`   |
| **Module 1: User Access & Content Creation**     | `UC-1.6`, `BR-AI-01..03`, `BR-AI-07`        | On-demand Claude Vision caption generation (3 tones, 280-char prompt cap, 30 req/hr rate limit, 3,000-char caption ceiling), advisory AI **Check writing** (`ProofreadService`), and Fancy Unicode text styling (5 styles + Plain revert).                                                                                                                             | `Case-8`             |
| **Module 1: User Access & Content Creation**     | `UC-1.7`, `BR-SUB-01..02`, `BR-AI-05..06`   | Device upload validation (7 formats, `≤50 MB`, `≤10` assets/post), presigned R2 upload, per-asset caption (`≤500 chars`), per-photo watermark opt-out, AI-Suggested Media (`≥0.40` similarity floor, top 8), and AI Album Auto-Match (`≥0.55` confident, `0.32–0.54` ambiguous).                                                                                       | `Case-9`             |
| **Module 1: User Access & Content Creation**     | `UC-1.8`                                    | Historical Facebook engagement peak-hour slot recommendations (last 100 posts, `≥20` samples or Tue/Wed/Thu 6:00 PM fallback), guard-rail pre-filtering, and inline soft-warning display.                                                                                                                                                                              | `Case-10`            |
| **Module 1: User Access & Content Creation**     | `UC-1.9`, `GR-H1..H3`, `GR-S1..S2`          | Draft submission completeness gate, submit-time guard-rail re-validation (`409` slot conflict redirects to Step 3), `pending` withdrawal (blocked once locked), resubmission from `needs_revision` or `rejected`, and Fast-Track top-of-queue sorting.                                                                                                                 | `Case-11`            |
| **Module 1: User Access & Content Creation**     | `UC-1.10`                                   | Network-wide Moderator account invitation, deactivation, reactivation, footprint-aware deletion, erasure, and immediate lateral role change (`Contributor ↔ Moderator`) with review-lock release.                                                                                                                                                                      | `Case-12`            |
| **Module 2: Media Library & Approval Pipeline**  | `UC-2.1`                                    | Standalone Media Library upload (all 3 roles), text-based Album Auto-Match (`≥0.6` confident, `≥0.3` ambiguous), nested parent-child albums with cycle guard, mandatory `≥1` manual tag floor (`A9`), and image `PROCESSING → READY` vs. video immediate `READY` lifecycle.                                                                                            | `Case-13`            |
| **Module 2: Media Library & Approval Pipeline**  | `UC-2.2`, `BR-MED-01`                       | Media Library grid/list browsing, Voyage + pgvector semantic search with keyword fallback, manual/AI tag searchability, institution filter for Moderator/Admin, editable `display_title`, and `Add to Existing Draft`.                                                                                                                                                 | `Case-14`            |
| **Module 2: Media Library & Approval Pipeline**  | `UC-2.3`, `BR-MED-02`                       | Asset Detail Panel `Used In` history (live links + historical `MEDIA_ASSET_REUSED` audit rows + deleted placeholders), 3-tier deletion precedence (`409` active block, `force=true` draft warning, 30-day soft-delete purge), and SHA-256 content-hash duplicate detection.                                                                                            | `Case-15`            |
| **Module 2: Media Library & Approval Pipeline**  | `UC-2.4`, `BR-APP-01..03`                   | Network-wide Validation Queue (8 tabs), 15-minute `ReviewLock` acquisition/expiry, unconditional self-submission review block (`A5`), Approve, Request Revision (`10–1,000` chars), Reject (7 standardized codes), inline edit diff severity (`Quiet`/`Flagged`/`Added Media`), `Restore original`, and Late Publish Override (`A15`).                                 | `Case-16`, `Case-17` |
| **Module 2: Media Library & Approval Pipeline**  | `UC-2.5`                                    | Network-wide Watermark Configuration canvas (up to 3 elements, `1:1`/`4:5`/`16:9` aspect ratios), publish-time proportional rendering on photos, default text fallback when unconfigured, non-blocking publish fallback on render error (`PUBLISH_WATERMARK_FAILED`), and 3-day derivative purge (`GeneratedWatermarkPurgeJob`).                                       | `Case-18`            |
| **Module 3: Scheduling, Publishing & Analytics** | `UC-3.1`, `BR-PUB-01`, `GR-H1`              | Role-scoped Master Calendar visibility (Contributor masking of other HEIs vs. full view of own HEI + DASIG Central Visayas + own in-flight posts), Moderator reschedule caps (`≤2` reschedules, `±1 day` of `originalScheduledAt`), Admin override justification, atomic reschedule claims, and PostgreSQL GiST exclusion constraint (`V95`/`V96`).                    | `Case-19`            |
| **Module 3: Scheduling, Publishing & Analytics** | `UC-3.2`, `BR-PUB-02..05`, `GR-T5`, `GR-T9` | Minute-level `PublishingSchedulerJob` with atomic `scheduled → publishing` claim, 2-step photo publishing with orphan cleanup (`photoIdsCleanupFailed`), single-call video publishing, 3-retry exponential backoff (`5s, 25s, 125s`), Fast-Track async publishing, 5-minute stale sweep (`publish_failed` vs. `missed_review`), and 3-step Manual Publishing Fallback. | `Case-20`, `Case-21` |
| **Module 3: Scheduling, Publishing & Analytics** | `UC-3.3`, `BR-NOT-01..04`                   | Event-driven notifications across 29 triggers (`T-01`–`T-12`+), reviewer fan-out to both Moderators and Administrators, real-time SSE badge & cross-session `read`/`read-all` sync, independent email retry logging, and opt-in Facebook Messenger delivery.                                                                                                           | `Case-22`            |
| **Module 3: Scheduling, Publishing & Analytics** | `UC-3.4`, `BR-ANA-01..06`                   | Role-scoped Analytics dashboard (Contributor institution-wide, Moderator network-wide unfiltered, Admin network-wide with institution drilldown + Operational Health & AI Performance reports), 5-minute auto-refresh, and CSV export with CWE-1236 formula-injection sanitization.                                                                                    | `Case-23`            |
| **Module 3: Scheduling, Publishing & Analytics** | `UC-3.5`                                    | Admin-only System Health dashboard: PostgreSQL & Cloudflare R2 storage capacity probes (`80%` Warning, `95%` Critical), 6 external service checks, 16 tracked background jobs with manual **Re-run** (sync vs. async), 14 operational/AI metrics (including missed-review rate), CSV snapshot export, and Facebook OAuth token re-authentication.                      | `Case-24`            |
| **Module 3: Scheduling, Publishing & Analytics** | `UC-3.6`, `SRS 3.5`                         | Admin-only Audit Log with 11 categories, 8 entity types, structured before/after diff inspector, 5,000-row sanitized CSV export, DB-enforced append-only RLS (`V78`), and 4-layer Multi-Institution Tenant Isolation (`404 Not Found` on cross-tenant access).                                                                                                         | `Case-25`            |

---

## **2.2 Features not to be Tested** {#22-features-not-to-be-tested}

Based on `README.md`, `docs/md/srs/SRS.md` (Sections 1.2, 2.3, and 3.3), and the scope boundaries documented in `docs/md/srs/UC-*.md`, the following features are explicitly excluded from testing:

1. **Integration with Non-Facebook Social Media Platforms (`SRS 1.2`, `SRS 2.3`):** Publishing or analytics integration with Instagram, X/Twitter, LinkedIn, TikTok, or YouTube is out of scope; DASIGConnect is scoped exclusively to the official DASIG Facebook Page.
2. **Automated Publishing of Mixed-Media (Image + Video) Posts (`SRS 1.2`, `UC-1.7 A7`, `UC-3.2 A6`):** Due to structural Facebook Graph API constraints, combining photos and videos in a single automated feed call is not supported. Only the detection of mixed-media submissions and their routing to `PUBLISH_FAILED` for Manual Publishing Fallback is tested.
3. **AI Classification, Vector Embedding, and Watermarking for Video Assets (`UC-2.1`, `UC-2.5`, `GR-T7`):** Video uploads (`MP4`, `MOV`, `WebM`) bypass Claude/Gemini image classification, Voyage AI vector embedding, and Java2D watermarking by design, transitioning immediately to `READY` and publishing unwatermarked.
4. **Generative AI Image Creation & Autonomous Content Moderation (`SRS 1.2`):** AI tools in DASIGConnect are strictly advisory; the system does not generate synthetic images or autonomously approve/publish Contributor posts without human review.
5. **Public Facebook Post Visibility Under Live Mode / Meta Business Verification (`README.md`, `SRS 1.2`, `UC-3.2 A4`):** During development and capstone evaluation, the Meta Developer Application operates in **Development Mode** (posts visible to app role holders). Transitioning to Live Mode requires organizational Meta Business Verification by DOST/DASIG and is outside software testing scope.
6. **Contributor-Initiated Guard-Rail Override Requests (`SRS 3.3.1.2`):** Hard guard-rail violations (`GR-H1..H3`) strictly block Contributor slot selection and submission; only Administrators can override a hard guard rail during their own actions.
7. **Self-Service Trash / Restore UI for Soft-Deleted Media Assets (`UC-2.3 A3`):** The 30-day soft-delete window (`MediaAssetRetentionPurgeJob`) is an internal safety buffer prior to permanent R2/DB purge, not a user-facing recycle bin or self-service undo screen.
8. **Per-Channel and Per-Event Notification Opt-Out Toggles (`BR-NOT-04`):** Granular notification preference toggles are out of scope for the pilot; all in-app and email triggers are mandatory for their target roles, with only Facebook Messenger delivery operating as an individual opt-in via account linking (`UC-1.4 A8`).
9. **Initial Seeded Admin Provisioning & Unreachable Admin Owner Recovery (`UC-1.1 Preconditions & A11`):** The initial Admin Owner account is seeded at deployment, and recovery from an unreachable Admin Owner is an organizational governance procedure outside application scope.
10. **Internal Uptime & SLA of Third-Party Cloud Providers:** Internal availability of Meta's Graph API servers, Anthropic's Claude inference cluster, Voyage AI's embedding cluster, Supabase's managed PostgreSQL infrastructure, and Cloudflare R2's global storage network are not tested; only DASIGConnect's resilience, timeouts, retries, and graceful degradation when calling these interfaces are tested.

---

## **3.3 Testing Tools and Environment** {#33-testing-tools-and-environment}

_(Note: Numbered `3.3` per the CIT-U CCS Software Test Document template Table of Contents; corresponds to Section `2.3` of the Test Plan.)_

### **Hardware Environment**

- **Development & Test Execution Workstation:**
  - **Processor:** Multi-core x64 CPU (Intel Core i5/i7 or AMD Ryzen 5/7, ≥4 cores)
  - **Memory:** 16 GB RAM minimum
  - **Storage:** NVMe SSD with ≥20 GB free disk space
  - **Network:** Broadband internet connection (for Supabase, Cloudflare R2, and external API sandbox connectivity)
  - **Display:** Desktop (`1920×1080` / `≥1280px`) and emulated responsive mobile/tablet viewports (`≤900px`, `<768px`)

### \*\* \*\*

| Layer / Category                        | Tool / Technology                                                     | Version / Configuration                                                                                          | Purpose in Testing                                                                                                                                                                              |
| :-------------------------------------- | :-------------------------------------------------------------------- | :--------------------------------------------------------------------------------------------------------------- | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Operating System**                    | Microsoft Windows 11 / Linux (Railway/Render container)               | 64-bit                                                                                                           | Local test execution and cloud deployment runtime (`java.awt.headless=true` for server-side Java2D watermarking).                                                                               |
| **Backend Runtime**                     | Java SE Development Kit (JDK) & Spring Boot                           | Java 21, Spring Boot `4.0.6`, Maven Wrapper (`./mvnw`)                                                           | Execution of REST API server, business services, Spring Security RBAC, and 16 `@Scheduled` cron jobs.                                                                                           |
| **Backend Unit & Slice Testing**        | JUnit 5 (Jupiter), Mockito, AssertJ, Spring Boot Test (`MockMvc`)     | `spring-boot-starter-test`, `spring-boot-starter-security-test`, `spring-boot-starter-webmvc-test`               | Automated unit testing of services (`SubmissionServiceTest`, `ValidationServiceTest`, `UserServiceTest`, `MediaAssetServiceTest`, etc.) and HTTP/RBAC controller slices (`@WebMvcTest`).        |
| **In-Memory / Relational Test DB**      | H2 Database & Supabase PostgreSQL + `pgvector`                        | H2 (test scope), PostgreSQL 15+ (`VECTOR(1024)`, GiST, RLS), Flyway (`V1`–`V104+`), HikariCP (max pool size `5`) | Schema migration verification, repository testing, GiST slot-conflict exclusion testing, and 4-layer tenant isolation verification.                                                             |
| **Frontend Runtime & Build**            | Node.js, React, TypeScript, Vite                                      | Node.js `18+`, React `19.2.6`, TypeScript `~6.0.2`, Vite `^8.0.12`                                               | Compilation (`tsc -b && vite build --mode test`) and local preview server (`http://127.0.0.1:4173` / `http://localhost:5173`).                                                                  |
| **Frontend E2E & Performance Testing**  | Playwright Test & Headless Chromium                                   | `@playwright/test ^1.63.0`, `scripts/run-playwright.mjs`, `scripts/check-bundle-budgets.mjs`                     | End-to-end UI workflow testing, network request deduplication, SSE stream verification, and bundle budget enforcement (`npm run test:e2e`, `npm run test:performance`, `npm run check:bundle`). |
| **Static Analysis & Linting**           | ESLint & TypeScript Compiler                                          | ESLint `^10.3.0`, `typescript-eslint ^8.59.2`                                                                    | Static type checking and code quality enforcement (`npm run lint`).                                                                                                                             |
| **Object Storage & External Sandboxes** | Cloudflare R2, Meta Graph API `v25.0`, Claude Vision, Voyage AI, SMTP | AWS SDK S3 `2.28.29`, Meta Development Mode Page, `claude-haiku-4-5`, `voyage-4-lite`                            | Presigned media upload/proxy streaming, 2-step photo & single-call video publishing verification, AI caption/proofread/embedding tests, and email delivery logging.                             |

---

# **3. Test Cases** {#3-test-cases}

## **3.1 Case-1: Administrator Account Management & 3-Admin Cap Enforcement (UC-1.1)** {#31-case-1-administrator-account-management--3-admin-cap-enforcement-uc-11}

### **3.1.1 Purpose**

Verify that an active Administrator can invite a new Administrator using a 72-hour single-use SHA-256 hashed invitation token, that the uniform **3-Admin cap** (`ACTIVE` Admins + pending Admin invitations + pending Admin promotions `≤ 3`) is strictly enforced across invitation, activation, promotion, and reactivation gates (`UC-1.1 Main Flow & A2`), and that Admin Owner authority is required to deactivate, reactivate, delete, or erase an existing Administrator account (`UC-1.1 A3–A6`).

### **3.1.2 Inputs**

- **Authenticated Actor 1:** Admin Owner (`admin.owner@dasig.org`, `role = admin`, `isAdminOwner = true`, `state = ACTIVE`).
- **Authenticated Actor 2:** Peer Administrator (`admin.peer@dasig.org`, `role = admin`, `isAdminOwner = false`, `state = ACTIVE`).
- **New Admin Invitee Email:** `admin.third@dasig.org` ( takes slot 3 of 3) and `admin.fourth@dasig.org` (exceeds cap of 3).
- **Activation Payload:** Raw 64-character hex token from email, password `"DasigAdmin#2026!Secure"`.

### **3.1.3 Expected Outputs & Pass/Fail Criteria**

- **Expected Outputs:**
  1. Inviting `admin.third@dasig.org` succeeds (`HTTP 200/201`), creating a `PENDING` user with `institution_id = NULL` and storing only the SHA-256 token hash.
  2. Attempting to invite `admin.fourth@dasig.org` while 2 active Admins and 1 pending Admin invite exist is rejected (`HTTP 409/422`) with an Admin limit reached error (`UC-1.1 A2`).
  3. Activating `admin.third@dasig.org` re-validates the cap, transitions the account to `ACTIVE`, and logs `INVITATION_ACCEPTED`.
  4. Peer Administrator (`isAdminOwner = false`) attempting to deactivate `admin.third@dasig.org` is rejected with `HTTP 403 Forbidden`.
  5. Admin Owner (`isAdminOwner = true`) deactivating `admin.third@dasig.org` succeeds, transitions status to `INACTIVE`, increments `session_version` to revoke all active JWT sessions, and records `USER_STATUS_UPDATED`.
  6. Attempting to re-invite an `INACTIVE` Admin is rejected (`HTTP 409 Conflict`), directing the actor to use Reactivation (`UC-1.1 A4`).
- **Pass/Fail Criteria:** **PASS** if all 6 assertions hold and audit log entries match `UC-1.1` postconditions; **FAIL** otherwise.

### **3.1.4 Test Procedure**

1. Authenticate as `admin.owner@dasig.org` and navigate to **Admin Management**.
2. Submit **Invite Admin** for `admin.third@dasig.org`; verify `PENDING` record creation and hashed token persistence.
3. Submit **Invite Admin** for `admin.fourth@dasig.org`; verify the 3-Admin cap blocks the request (`UC-1.1 A2`).
4. Complete the activation link flow for `admin.third@dasig.org` with a valid password; verify transition to `ACTIVE`.
5. Authenticate as Peer Admin (`admin.peer@dasig.org`) and attempt `Deactivate` on `admin.third@dasig.org`; verify `403 Forbidden`.
6. As Admin Owner, execute `Deactivate` on `admin.third@dasig.org`; verify status becomes `INACTIVE`, sessions are revoked, and re-inviting `admin.third@dasig.org` is blocked in favor of `Reactivate`.

---

## **3.2 Case-2: Admin Promotion Proposal & Admin Owner Transfer Governance (UC-1.1 A7, A8)** {#32-case-2-admin-promotion-proposal--admin-owner-transfer-governance-uc-11-a7-a8}

### **3.2.1 Purpose**

Verify the two-step propose/confirm workflow for promoting an existing Moderator or Contributor to Administrator (`UC-1.1 A7`) and the Admin Owner transfer workflow (`UC-1.1 A8`) restricted strictly to an existing active Administrator target (preventing demotion of the outgoing Owner).

### **3.2.2 Inputs**

- **Proposing Actor:** Admin Owner (`admin.owner@dasig.org`, `isAdminOwner = true`).
- **Target 1 (Promotion):** Active Moderator (`mod.one@dasig.org`, `role = moderator`, `institution_id = NULL`).
- **Target 2 (Admin-to-Admin Owner Transfer):** Active Peer Admin (`admin.peer@dasig.org`, `role = admin`, `isAdminOwner = false`).

### **3.2.3 Expected Outputs & Pass/Fail Criteria**

- **Expected Outputs:**
  1. Proposing `mod.one@dasig.org` for Admin promotion reserves a slot against the 3-Admin cap, creates a pending 72-hour promotion request without immediately changing `mod.one`'s role, and logs `ADMIN_PROMOTION_REQUESTED`.
  2. When `mod.one@dasig.org` confirms the promotion, the system re-checks the 3-Admin cap, promotes the account to `admin` (`isAdminOwner = false`), revokes active sessions, and logs `ADMIN_PROMOTION_CONFIRMED`.
  3. Attempting to initiate an Admin Owner Transfer to a Moderator target (`mod.one@dasig.org`) is rejected (`HTTP 400 Bad Request`), enforcing that only active Administrators can receive Admin Owner status.
  4. When Admin Owner initiates an **Admin Owner Transfer** to an existing Administrator (`admin.peer@dasig.org`), initiation logs `ADMIN_TRANSFER_REQUESTED` with a 24-hour expiry; upon confirmation by `admin.peer@dasig.org`, `admin.peer` becomes `admin` with `isAdminOwner = true`, the outgoing Owner relinquishes `isAdminOwner = false` while remaining an `admin`, outgoing active sessions are revoked, and `ADMIN_TRANSFER_CONFIRMED` is logged.
- **Pass/Fail Criteria:** **PASS** if promotion propose/confirm/decline/cancel, Moderator-target transfer rejection (400), and Admin-to-Admin Owner transfer succeed per specification; **FAIL** otherwise.

### **3.2.4 Test Procedure**

1. As Admin Owner (with `< 3` total occupied Admin slots), propose promoting `mod.one@dasig.org` to Admin.
2. Verify `ADMIN_PROMOTION_REQUESTED` is written and a second concurrent promotion proposal for `mod.one@dasig.org` is rejected.
3. Log in as `mod.one@dasig.org`, confirm the promotion, and verify role elevation to `admin` and session invalidation.
4. Attempt Admin Owner transfer initiation to a Moderator target; verify rejection with `HTTP 400 Bad Request`.
5. Test Admin Owner transfer initiation to an existing Administrator target (`admin.peer@dasig.org`) and confirm via `POST /api/v1/users/admin-transfer/confirm`; verify `admin.peer@dasig.org` becomes Admin Owner while outgoing Owner remains Administrator.

---

## **3.3 Case-3: Institution Lifecycle & Protected Default Institution Safeguards (UC-1.2)** {#33-case-3-institution-lifecycle--protected-default-institution-safeguards-uc-12}

### **3.3.1 Purpose**

Verify institution creation, duplicate detection, Contributor reassignment, deactivation gating, permanent deletion rules, read-only Moderator visibility, and unconditional protection of the seeded default institution `"DASIG Central Visayas"` (`UC-1.2 Main Flow & A1–A8`).

### **3.3.2 Inputs**

- **Actors:** Administrator (`admin.owner@dasig.org`) and Moderator (`mod.one@dasig.org`).
- **New Institution Payload:** Name `"Cebu Institute of Technology - University"`, Code `"CITU"`, Domain `"cit.edu"`.
- **Protected Institution:** `"DASIG Central Visayas"`.

### **3.3.3 Expected Outputs & Pass/Fail Criteria**

- **Expected Outputs:**
  1. Moderator viewing **Institution Management** sees all institutions in read-only mode; direct API attempts by Moderator to create/edit/deactivate/delete an institution return `HTTP 403 Forbidden` (`UC-1.2 A7`).
  2. Administrator creating `"CITU"` succeeds (`ACTIVE` status); attempting to create another institution with duplicate name, code, or domain returns `HTTP 409 Conflict` (`UC-1.2 A5`).
  3. Attempting to deactivate `"CITU"` while an active Contributor is assigned is blocked (`HTTP 409/422`) until the Contributor is reassigned to another `ACTIVE` institution (`UC-1.2 A2, A4`).
  4. Attempting to deactivate or permanently delete `"DASIG Central Visayas"` is unconditionally rejected (`UC-1.2 A6, A8`).
  5. Permanent deletion (`UC-1.2 A8`) of an eligible empty institution succeeds only when the exact institution code is confirmed and no active/pending Contributors, submissions of any state, or non-deleted media assets exist; logs `INSTITUTION_DELETED`.
- **Pass/Fail Criteria:** **PASS** if all creation, duplicate, reassignment, protection, and permanent deletion guards behave as specified; **FAIL** otherwise.

### **3.3.4 Test Procedure**

1. Log in as Moderator, open **Institution Management**, verify action controls are hidden, and send a `POST /api/v1/institutions` request; assert `403 Forbidden`.
2. Log in as Administrator, create institution `"CITU"`, then repeat with the same code `"CITU"`; assert duplicate rejection.
3. Assign a Contributor to `"CITU"` and attempt to deactivate `"CITU"`; assert rejection. Reassign the Contributor to another active institution (verifying historical submissions retain their original institution attribution), then deactivate `"CITU"`.
4. Attempt to deactivate and delete `"DASIG Central Visayas"`; assert both actions are blocked.
5. Create a footprint-free test institution `"TEST-HEI"`, confirm its code in the Permanent Delete dialog, and verify removal and `INSTITUTION_DELETED` audit logging.

---

## **3.4 Case-4: Contributor Account Provisioning & Footprint-Aware Deletion (UC-1.3)** {#34-case-4-contributor-account-provisioning--footprint-aware-deletion-uc-13}

### **3.4.1 Purpose**

Verify Contributor invitation by both Administrators and Moderators, institution-status validation (`BR-ACC-01`), Moderator ownership scoping on sent invitations (`UC-1.3 A5, A6`), footprint-aware account deletion (`USER_REMOVED` vs. hard-delete), and Admin Owner Right-to-be-Forgotten erasure (`UC-1.3 A7`).

### **3.4.2 Inputs**

- **Actors:** Administrator (`admin.owner@dasig.org`), Moderator A (`mod.a@dasig.org`), Moderator B (`mod.b@dasig.org`).
- **Invitees:** `contrib.citu@cit.edu` (invited by Moderator A to `"CITU"`), `contrib.fresh@cit.edu` (no historical footprint).

### **3.4.3 Expected Outputs & Pass/Fail Criteria**

- **Expected Outputs:**
  1. Moderator A can invite `contrib.citu@cit.edu` to any `ACTIVE` institution with role restricted to `contributor`; inviting to an `INACTIVE` institution is rejected (`BR-ACC-01`).
  2. Moderator B attempting to resend, cancel, or delete Moderator A's pending invitation is rejected (`HTTP 403 Forbidden`), whereas Moderator A succeeds (`UC-1.3 A5, A6`).
  3. Once `contrib.citu@cit.edu` activates their account and creates a submission or uploads media (establishing a historical footprint), deactivating (`INACTIVE`) and then deleting the account does **not** hard-delete the database row; instead, it persists as an anonymized inactive record with a `USER_REMOVED` audit entry (`UC-1.3 A6`).
  4. Deleting `contrib.fresh@cit.edu` (cancelled/expired with zero footprint) hard-deletes the row.
  5. Admin Owner executing **Erase** (`UC-1.3 A7`) on an `INACTIVE` Contributor anonymizes PII in place and logs `USER_ANONYMIZED`.
- **Pass/Fail Criteria:** **PASS** if Moderator scoping, institution-status checks, footprint-preserving soft-removal, and PII erasure all match `UC-1.3`; **FAIL** otherwise.

### **3.4.4 Test Procedure**

1. Authenticate as Moderator A and invite `contrib.citu@cit.edu` to `"CITU"`.
2. Authenticate as Moderator B and attempt to cancel or resend `contrib.citu@cit.edu`'s invite; assert `403 Forbidden`.
3. Activate `contrib.citu@cit.edu`, create a draft submission, then as Administrator deactivate and delete `contrib.citu@cit.edu`; verify the row remains in the database anonymized at rest with `USER_REMOVED` logged.
4. Invite and cancel `contrib.fresh@cit.edu` (zero footprint), then delete the account; verify hard deletion.
5. As Admin Owner, invoke `Erase` on an `INACTIVE` account and verify `USER_ANONYMIZED` audit logging.

---

## **3.5 Case-5: Authentication, Account Lockout, Password Policy & DB Session Revocation (UC-1.4)** {#35-case-5-authentication-account-lockout-password-policy--db-session-revocation-uc-14}

### **3.5.1 Purpose**

Verify login authentication, JWT claims scoping, 5-attempt / 15-minute account lockout (`UC-1.4 A1`), password complexity enforcement (`UC-1.4 A5, A6`), single-device logout (`revoked_tokens`, `UC-1.4 A10`) vs. account-wide session invalidation (`session_version`, `UC-1.4 A5`), session-expiry warning banner/modal (`UC-1.4 A7`), and 10-minute Facebook Messenger link-code generation (`UC-1.4 A8`).

### **3.5.2 Inputs**

- **Accounts:** Active Contributor (`contrib.citu@cit.edu`), Active Moderator (`mod.a@dasig.org`), `PENDING` user, `INACTIVE` user.
- **Passwords Tested:**
  - Weak passwords: `"Short1!"` (`<12` chars), `"DasigWelcome123!"` (contains forbidden fragment `"dasig"`/`"welcome"`/`"123"`), `"contrib.citu@cit.eduA1!"` (contains email).
  - Valid password: `"Str0ng#Pass_987!"` (`≥12` chars, upper, lower, digit, special, no whitespace or forbidden fragments).

### **3.5.3 Expected Outputs & Pass/Fail Criteria**

- **Expected Outputs:**
  1. Valid login returns an 8-hour JWT: Contributor token includes `institution_id`; Moderator and Admin tokens have no `institution_id` claim (`UC-1.4 Main Flow`).
  2. Five consecutive failed login attempts lock the account for 15 minutes (`HTTP 423 Locked`, `UC-1.4 A1`).
  3. Login attempts by `PENDING`, `EXPIRED`, or `INACTIVE` accounts are rejected with state-specific guidance (`UC-1.4 A2–A4`).
  4. Password reset (`UC-1.4 A5`) rejects weak/fragment-containing passwords, accepts `"Str0ng#Pass_987!"`, increments `users.session_version`, and invalidates all existing JWTs across all devices.
  5. Authenticated password change (`UC-1.4 A6`) updates the password without incrementing `session_version`, keeping other active sessions valid.
  6. Single-session logout (`POST /api/v1/auth/logout`, `UC-1.4 A10`) inserts the SHA-256 token hash into `revoked_tokens`, rejecting subsequent requests with that token while leaving other device sessions active.
  7. Generating a Facebook Messenger link code (`UC-1.4 A8`) succeeds for Moderator and Administrator (10-minute code) and returns `HTTP 403 Forbidden` for Contributor.
- **Pass/Fail Criteria:** **PASS** if all authentication, lockout, password policy, DB-persisted token revocation, and Messenger link-code role checks pass; **FAIL** otherwise.

### **3.5.4 Test Procedure**

1. Attempt 5 failed logins for `contrib.citu@cit.edu`; verify the 5th attempt triggers a 15-minute lockout (`423 Locked`).
2. Log in with valid Contributor and Moderator credentials on two separate sessions (Session 1 and Session 2); inspect decoded JWT payloads for `institution_id` and `session_version`.
3. Call `POST /api/v1/auth/logout` on Session 1; verify Session 1's JWT is rejected by `JWTService.extractClaims` (`revoked_tokens`) while Session 2 remains valid.
4. Execute unauthenticated Password Reset (`A5`) testing both invalid fragments (`"dasig"`, `"123"`) and valid password `"Str0ng#Pass_987!"`; verify Session 2 is immediately invalidated via `session_version` mismatch.
5. As Contributor and Moderator, test `POST /api/v1/auth/messenger-link-code` (or Messenger connection endpoint); verify Moderator receives a 10-minute code and Contributor receives `403`.

---

## **3.6 Case-6: Three-Step Post Composer, Readiness Checklist & Post Templates (UC-1.5)** {#36-case-6-three-step-post-composer-readiness-checklist--post-templates-uc-15}

### **3.6.1 Purpose**

Verify the 3-step post composer wizard (`Step 1: Media → Step 2: Post Details → Step 3: Organize & Schedule`), the live 0–100 Readiness Checklist (75% Required, 25% Recommended), built-in and personal post templates, **Posting As** institution switching for Moderators/Administrators, autosave, and the network-wide `guardrails_enforced` runtime toggle (`UC-1.5 Main Flow & A1–A9`).

### **3.6.2 Inputs**

- **Actor:** Contributor (`contrib.citu@cit.edu`) and Administrator (`admin.owner@dasig.org`).
- **Draft Content:**
  - Media: 1 valid JPEG image (`event-banner.jpg`, `1.8 MB`).
  - Event Title: `"CIT-U Regional Innovation Summit 2026"`, Event Date: `"2026-10-20"`.
  - Template: Built-in `"Event Announcement"`, plus custom personal template `"My Lab Spotlight"`.
  - Schedule: Future slot in `Asia/Manila` time (`Set a Schedule` vs. `Live Event Fast-Track`).

### **3.6.3 Expected Outputs & Pass/Fail Criteria**

- **Expected Outputs:**
  1. Forward step navigation requires the current step's _Required_ readiness items; backward navigation is always permitted and preserves entered state (`UC-1.5 A8`).
  2. **Save as Draft** works on any step even with unmet Required items (`UC-1.5 A6`); once saved, subsequent edits autosave silently after a short debounce.
  3. Opening the **Templates** drawer/bottom-sheet on Step 2 and applying `"Event Announcement"` populates the caption placeholders and tags; saving a custom template via **Save current caption** scopes it to `ownerUserId` and rejects duplicate template names for the same owner with `HTTP 409 Conflict`.
  4. For Administrator/Moderator, changing the **Posting As** institution on a saved draft prompts confirmation, clears the previously selected preferred schedule, preserves attached media, and re-homes the draft on save.
  5. When `guardrails_enforced = false` (toggled in **Settings → Page**), Standard drafts still require a future scheduled date and create a `SlotReservation`, but skip the `8:00 AM–8:00 PM` window, `±30-min` spacing, `≤6/day`, and `≥2h` lead-time checks (`UC-1.5`).
  6. Deleting a draft or `rejected` submission (`UC-1.5 A2`) cascades cleanup of `validation_logs`/`review_locks` (`V103`) and writes `SUBMISSION_DELETED`.
- **Pass/Fail Criteria:** **PASS** if wizard gating, Readiness score calculation (0–100), template CRUD, Posting As re-homing, guard-rail toggle behavior, and draft/rejected deletion all succeed; **FAIL** otherwise.

### **3.6.4 Test Procedure**

1. Open **New Post** as Contributor; verify Step 1 blocks advancing to Step 2 until at least one valid media asset is attached.
2. Attach `event-banner.jpg`, advance to Step 2, open the **Templates** panel, apply `"Event Announcement"`, and save a personal template `"My Lab Spotlight"`; attempt to save a second template with the same name and assert `409 Conflict`.
3. Verify the **Readiness Checklist** updates live as Required items (75% weight) and Recommended items (25% weight: comfortable caption length, `≥1` hashtag, `≥1` media tag, `≥1` per-media caption, template used) are completed.
4. Click **Submit** when all Required items are met but one Recommended item is missing; verify the non-blocking confirmation dialog displays the readiness score (`UC-1.5 A7`).
5. As Administrator, toggle **Scheduling Guard Rails** OFF in **Settings → Page** and verify a Standard draft can be scheduled at `9:30 PM` (outside `8 AM–8 PM`) as long as the timestamp is in the future; re-enable Guard Rails afterward.

---

## **3.7 Case-7: AI Template Synthesis from Top-Performing Facebook Posts (UC-1.5 A10)** {#37-case-7-ai-template-synthesis-from-top-performing-facebook-posts-uc-15-a10}

### **3.7.1 Purpose**

Verify the **Generate from Top Posts** feature (`POST /api/v1/ai/templates/from-top-posts`, `UC-1.5 A10`), including engagement ranking (`reactions + 2×comments + 3×shares`), minimum engaged-post threshold (`≥8` posts), fallback to local `submission_engagement_metrics`, and per-user rate limiting (`10 requests/hour`).

### **3.7.2 Inputs**

- **Actor:** Authenticated Contributor or Administrator in Step 2 (**Post Details**) → **Templates** panel.
- **Dataset:**
  - Scenario A: `< 8` engaged posts available.
  - Scenario B: `≥ 11` engaged posts available (top 6 + bottom 3 contrast sent to Claude).
  - Scenario C: 11th request within 1 rolling hour by the same user.

### **3.7.3 Expected Outputs & Pass/Fail Criteria**

- **Expected Outputs:**
  1. In Scenario A (`< 8` posts with engagement), the endpoint returns `available: false` with an explanatory reason without calling Claude (`UC-1.5 A10a`).
  2. In Scenario B (`≥ 8` engaged posts), the system ranks the last 100 posts by `reactions + 2×comments + 3×shares`, sends the top 6 (and bottom 3 as contrast) to Claude outside any DB transaction, and returns a draft template containing `name`, placeholder `caption` (`≤3,000` chars), up to 4 `tags`, and 2–4 `"why this works"` insights without auto-saving server-side.
  3. Clicking **Save Template** persists the template via `POST /api/v1/post-templates` under category `"AI Suggested"` without overwriting the composer's active caption.
  4. In Scenario C, exceeding 10 requests/hour returns `HTTP 429 Too Many Requests` (`UC-1.5 A10c`).
- **Pass/Fail Criteria:** **PASS** if minimum sample guard, ranking formula, non-destructive save, and `10/hr` rate limit all function per `UC-1.5 A10`; **FAIL** otherwise.

### **3.7.4 Test Procedure**

1. Open the **Post Templates** panel on an editable draft and click **Generate from Top Posts** with `< 8` engaged posts seeded; verify `available: false` and zero Claude API invocations.
2. Seed `12` published posts with varying reactions, comments, and shares; trigger **Generate from Top Posts** and verify the returned placeholder template, insights, and source post previews.
3. Click **Save Template** and verify it appears under **My templates** without replacing the current draft caption.
4. Trigger 10 consecutive requests within an hour and verify the 11th returns `429`.

---

## **3.8 Case-8: AI Caption Generation, Writing Check & Fancy Unicode Text Styling (UC-1.6)** {#38-case-8-ai-caption-generation-writing-check--fancy-unicode-text-styling-uc-16}

### **3.8.1 Purpose**

Verify on-demand AI caption generation (`POST /api/v1/ai/caption`), prompt length & word-count validation, image downscaling (`>5 MB`), `30/hour` rate limiting, interaction telemetry logging (`use` / `use_then_edited` / `dismiss` / `re_generate`), advisory AI proofreading (**Check writing**, `POST /api/v1/ai/proofread`), `3,000`-character caption cap, and Fancy Unicode text styling (`UC-1.6 Main Flow & A1–A7`).

### **3.8.2 Inputs**

- **Saved Draft:** Draft ID `sub-101` with 1 attached image (`6.2 MB` high-res JPEG).
- **AI Caption Prompt:** `"Highlight student robotics pitch winners in 120 words"` (`≤280` chars), Tone: `energetic`.
- **Invalid Prompts:** 285-character prompt (`>280` chars); prompt requesting `"Write a 2500 word essay"` (`>2,000` words).
- **Fancy Text Selection:** Substring `"DASIG Innovation"` styled as `Bold Sans-Serif` then `Italic Serif` then `Plain`.

### **3.8.3 Expected Outputs & Pass/Fail Criteria**

- **Expected Outputs:**
  1. Images `>5 MB` are downscaled in-memory (`70% → 50% → 35% → 25% → 15%`) before base64 submission to Claude (`SRS 3.1.2`, `UC-1.6`).
  2. Valid caption request returns 1 caption in the selected tone within the 30-second timeout; inserting and subsequently editing the caption logs `use_then_edited` (`BR-AI-03`).
  3. Prompt `>280` characters disables the Generate button client-side and returns `HTTP 400` server-side (`UC-1.6 A5`); requested word count `>2,000` returns `HTTP 400`.
  4. Exceeding 30 caption requests/hour returns `HTTP 429` with reset headers (`BR-AI-07`); AI timeout (`504`) or unavailability (`503`) degrades gracefully and auto-recovers to idle after `~5s` without blocking drafting (`UC-1.6 A1, A2`).
  5. Clicking **Check writing** invokes `POST /api/v1/ai/proofread` and displays advisory grammar/clarity suggestions that can be applied or dismissed.
  6. **Fancy Text** transforms `"DASIG Innovation"` into Unicode Mathematical Alphanumeric symbols, reverts to plain before applying a new style, leaves emoji/punctuation/accents untouched (`UC-1.6 A7`), and reverts cleanly to standard ASCII when `Plain` is selected (`UC-1.6 A6`).
  7. Captions exceeding `3,000` code points are trimmed on paste and rejected server-side (`400` on save, `422` on submit).
- **Pass/Fail Criteria:** **PASS** if all 7 expectations hold; **FAIL** otherwise.

### **3.8.4 Test Procedure**

1. Open saved draft `sub-101` on Step 2 (**Post Details**) and click **Suggest Caption**.
2. Test a 285-char prompt and a `"2500 words"` prompt; verify client/server `400` validation.
3. Submit the valid `energetic` prompt with the `6.2 MB` image attached; verify downscaling, caption insertion, subsequent manual edit, and telemetry log entry (`use_then_edited`).
4. Click **Check writing** and verify proofreading suggestions render non-blockingly.
5. Highlight `"DASIG Innovation"`, open **Fancy text**, apply `Bold Sans-Serif`, switch to `Italic Serif`, and revert via `Plain`.
6. Paste a `3,100`-character string into the caption field; verify client-side trimming to `3,000` code points and backend enforcement (`SubmissionService.MAX_CAPTION_CHARS = 3000`).

---

## **3.9 Case-9: Media Attachment Rules, AI-Suggested Media & Album Auto-Match (UC-1.7)** {#39-case-9-media-attachment-rules-ai-suggested-media--album-auto-match-uc-17}

### **3.9.1 Purpose**

Verify device upload format/magic-byte/size validation (`7` formats, `≤50 MB`, `≤10` assets/post), presigned R2 upload flow, per-asset caption (`≤500` chars) and photo-only `Skip watermark` toggle, **AI-Suggested Media** (`≥10` chars context, `≥0.40` similarity floor, top 8), and **Album Auto-Match** (`≥0.55` confident auto-apply, `0.32–0.54` ambiguous top-3 candidates, `<0.32` no-match) (`UC-1.7 Main Flow & A1–A10`).

### **3.9.2 Inputs**

- **Files Tested:**
  - Valid: `photo1.jpg` (`2 MB`), `clip.mp4` (`15 MB`).
  - Invalid: `malware.exe` renamed to `photo.jpg` (invalid magic bytes), `huge-video.mp4` (`55 MB` > `50 MB`), `document.pdf` (unsupported format), 11th media attachment.
- **Event Context for AI Suggestions:** Title `"Regional Robotics & AI Symposium"`, Caption `"Students showcasing autonomous drones"`.

### **3.9.3 Expected Outputs & Pass/Fail Criteria**

- **Expected Outputs:**
  1. `document.pdf`, `huge-video.mp4` (`>50 MB`), and disguised `photo.jpg` (magic-byte mismatch) are rejected (`HTTP 400`) with zero asset records created (`UC-1.7 A1, A2`, `BR-SUB-01`).
  2. Attempting to attach an 11th asset is blocked by the 10-asset-per-submission ceiling (`BR-SUB-02`).
  3. Attaching both `photo1.jpg` and `clip.mp4` is permitted during drafting with a mixed-media manual-publishing warning (`UC-1.7 A7`).
  4. In the Media Caption modal, per-asset captions are capped at `500` characters, and `"Skip watermark for this image"` is shown for `photo1.jpg` but hidden for `clip.mp4`.
  5. Once combined event text reaches `≥10` characters, **AI-Suggested Media** queries institution-scoped pgvector embeddings and returns up to 8 assets scoring `≥0.40` (falling back to metadata suggestions if the embedding service fails, `UC-1.7 A5, A6`).
  6. On Step 3, clicking **Auto-Match** (`POST /api/v1/ai/submissions/{id}/suggest-album`) auto-applies the root album with an `"AI-matched"` badge when score `≥0.55`, lists up to 3 ranked candidates inline when `0.32 ≤ score < 0.55` (`UC-1.7 A8`), and leaves the field for manual entry with a notice when `<0.32` (`UC-1.7 A8a`).
- **Pass/Fail Criteria:** **PASS** if file validation, 10-asset cap, per-asset caption/watermark controls, AI media suggestions, and tiered Album Auto-Match all pass; **FAIL** otherwise.

### **3.9.4 Test Procedure**

1. In Step 1 of the composer, attempt to upload `document.pdf`, a `55 MB` MP4, and a text file renamed to `.png`; verify each is rejected.
2. Upload `photo1.jpg`, open the Media Caption modal, enter a 500-char caption, and check `"Skip watermark for this image"`.
3. Upload `clip.mp4` to the same draft; verify the mixed-media inline warning appears and the watermark checkbox is absent on the video asset.
4. Enter `<10` characters in Step 2 and verify AI-Suggested Media stays hidden; enter the full robotics title/caption (`≥10` chars) and verify up to 8 ranked suggestions (`score ≥ 0.40`) appear.
5. In Step 3, test **Auto-Match** against albums yielding confident (`≥0.55`), ambiguous (`0.32–0.54`), and low (`<0.32`) similarity scores; verify the combobox behavior for each tier.

---

## **3.10 Case-10: Peak-Hour Engagement Recommendations & Guard-Rail Filtering (UC-1.8)** {#310-case-10-peak-hour-engagement-recommendations--guard-rail-filtering-uc-18}

### **3.10.1 Purpose**

Verify peak-hour posting slot recommendations (`UC-1.8`), including historical Facebook engagement bucketing (last 100 posts, `≥20` samples), fallback to Tuesday/Wednesday/Thursday 6:00 PM (`Asia/Manila`) when `<20` samples exist (`UC-1.8 A1`), exclusion of hard-blocked guard-rail slots (`UC-1.8 A2`), silent hiding on Graph API failure (`UC-1.8 A3`), and inline soft-warning rendering (`UC-1.8 A4`).

### **3.10.2 Inputs**

- **Scenario 1 (Historical):** `25` historical published posts with reactions, comments, and shares within `8:00 AM–8:00 PM` PHT; an existing scheduled post at `Tuesday 6:00 PM` (creating a `±30-min` `GR-H1` conflict).
- **Scenario 2 (Fallback):** Only `5` historical posts (`< 20` samples).
- **Scenario 3 (Manual Soft Warning):** Actor manually picks a valid slot on a calendar day that already has `6` scheduled posts (`GR-S2` Daily Volume Cap) or when the institution already has `3` active scheduled posts (`GR-S1`).

### **3.10.3 Expected Outputs & Pass/Fail Criteria**

- **Expected Outputs:**
  1. In Scenario 1, the system buckets the last 100 posts by `(dayOfWeek, hour)` in `Asia/Manila`, excludes the hard-blocked `Tuesday 6:00 PM` slot (`UC-1.8 A2`), and displays up to 3 conflict-free recommended future slots over the next 30 days with a `"Top pick"` badge.
  2. In Scenario 2 (`<20` samples), the system falls back to **Tuesday / Wednesday / Thursday 6:00 PM** labeled as `"Best-practice guidance"` (`UC-1.8 A1`).
  3. For a **Live Event Fast-Track** draft, the scheduling and recommendation panel is completely hidden (`UC-1.8 Preconditions`).
  4. In Scenario 3, manually selecting a slot that triggers `GR-S1` or `GR-S2` displays an inline soft warning beneath the date/time fields while allowing the actor to proceed (`UC-1.8 A4`).
- **Pass/Fail Criteria:** **PASS** if historical ranking, `<20`-sample fallback, hard-block filtering, Fast-Track hiding, and soft-warning inline display all match `UC-1.8`; **FAIL** otherwise.

### **3.10.4 Test Procedure**

1. Open Step 3 (**Organize & Schedule**) on a Standard draft with `<20` historical posts; verify the Tue/Wed/Thu 6:00 PM fallback tiles and `"Best-practice guidance"` notice appear.
2. Seed `25` engaged historical posts with peak activity on Tuesdays at 6:00 PM, reserve a conflicting slot at next Tuesday 6:00 PM, and reload Step 3; verify next Tuesday 6:00 PM is excluded from the recommended tiles.
3. Switch the draft mode to **Live Event Fast-Track**; verify the scheduling panel disappears.
4. Switch back to **Set a Schedule** and manually enter a time on a day that exceeds the `GR-S1` or `GR-S2` soft threshold; verify the inline warning renders and does not block saving.

---

## **3.11 Case-11: Draft Submission, Guard-Rail Re-Validation, Withdrawal & Fast-Track Prioritization (UC-1.9)** {#311-case-11-draft-submission-guard-rail-re-validation-withdrawal--fast-track-prioritization-uc-19}

### **3.11.1 Purpose**

Verify final server-side completeness validation at submission (`assertContentComplete`), submit-time guard-rail conflict handling (`409 Conflict` redirecting to Step 3, `UC-1.9 A4`), notification dispatch to reviewers (`T-01` vs. `T-11`), withdrawal from `pending` vs. locked `in_review` (`UC-1.9 A2`), resubmission from `needs_revision` or `rejected` (`UC-1.9 A3`), and Fast-Track top-of-queue prioritization (`UC-1.9 A5`).

### **3.11.2 Inputs**

- **Standard Draft (`sub-std`):** Complete title, date, caption (`≤3,000` chars), 1 media asset, album `"CIT-U Events"`, scheduled slot `T + 4 hours`.
- **Fast-Track Draft (`sub-ft`):** Complete fields, mode = `Live Event Fast-Track` (no scheduled slot).
- **Competing Submission:** Another user claims `T + 4 hours` (or within `±30` minutes) moments before `sub-std` clicks confirm.

### **3.11.3 Expected Outputs & Pass/Fail Criteria**

- **Expected Outputs:**
  1. If a concurrent reservation claims a slot within `±30` minutes of `sub-std` before submit completes, `POST /api/v1/submissions/{id}/submit` returns `HTTP 409 Conflict`, closes the confirmation modal, and redirects the composer to Step 3 (**Organize & Schedule**) with a prompt to pick a new time (`UC-1.9 A4`).
  2. With a valid conflict-free slot, submitting `sub-std` transitions status `draft → pending`, locks content from further editing, and dispatches notification `T-01`.
  3. Calling `POST /api/v1/submissions/{id}/withdraw` while `sub-std` is `pending` (and unlocked) transitions it back to `draft` and logs `SUBMISSION_WITHDRAWN`; once a reviewer opens `sub-std` (`ReviewLock` exists, status `in_review`), withdrawal is rejected with `HTTP 409 Conflict` (`UC-1.9 A2`).
  4. Submitting `sub-ft` (Fast-Track) skips slot validation, dispatches urgent notification `T-11` (in-app + email + Messenger), and sorts `sub-ft` to the top of the active Validation Queue (`fastTrack DESC, scheduledAt ASC NULLS LAST, submittedAt ASC`, `UC-1.9 A5`).
- **Pass/Fail Criteria:** **PASS** if completeness validation, `409` conflict step-redirect, withdrawal lock guard, and Fast-Track queue sorting all pass; **FAIL** otherwise.

### **3.11.4 Test Procedure**

1. Open `sub-std` in the composer, simulate a concurrent slot reservation at the same time slot, and click **Submit for Approval**; verify `409 Conflict` returns the user to Step 3.
2. Select a free future slot (`≥2h` lead time, within `8 AM–8 PM`), submit `sub-std`, and verify transition to `pending`.
3. Click **Withdraw** while `sub-std` has no `ReviewLock`; verify transition back to `draft`. Re-submit `sub-std`, acquire a `ReviewLock` as a Moderator, and attempt **Withdraw** as the Contributor; assert `409 Conflict`.
4. Submit `sub-ft` in **Live Event Fast-Track** mode and inspect the Validation Queue (`Pending` tab); verify `sub-ft` is ordered above all non-Fast-Track submissions.

---

## **3.12 Case-12: Moderator Account Management & Lateral Role Changes (UC-1.10)** {#312-case-12-moderator-account-management--lateral-role-changes-uc-110}

### **3.12.1 Purpose**

Verify Administrator lifecycle management of network-wide (institutionless) Moderator accounts (`UC-1.10 Main Flow & A1–A9`) and immediate lateral role changes between `Contributor` and `Moderator` (`UC-1.10 A11`), including automatic release of active `ReviewLock` rows and session invalidation.

### **3.12.2 Inputs**

- **Actor:** Administrator (`admin.owner@dasig.org`).
- **Target Account:** Active Moderator `mod.active@dasig.org` currently holding an active 15-minute `ReviewLock` on submission `sub-std`.
- **Target Institution for Demotion:** Active institution `"CITU"`.

### **3.12.3 Expected Outputs & Pass/Fail Criteria**

- **Expected Outputs:**
  1. Inviting a Moderator creates a network-wide (`institution_id = NULL`) `PENDING` account with a 72-hour hashed token; upon activation, the user gains network-wide review and Contributor-invitation privileges (`UC-1.10 Main Flow`).
  2. Changing `mod.active@dasig.org`'s role laterally from `Moderator → Contributor` (`UC-1.10 A11`) requires specifying an `ACTIVE` target institution (`"CITU"`), takes effect immediately with no confirmation step, increments `session_version` (forcing re-login), automatically releases `mod.active`'s active `ReviewLock` on `sub-std` (returning `sub-std` to `pending`), and writes `USER_ROLE_CHANGED`.
  3. Changing the account laterally from `Contributor → Moderator` clears `institution_id` (`NULL`), revokes active sessions, and restores network-wide Moderator privileges.
- **Pass/Fail Criteria:** **PASS** if Moderator provisioning, lateral `Contributor ↔ Moderator` transitions, session revocation, and automatic review-lock release all succeed; **FAIL** otherwise.

### **3.12.4 Test Procedure**

1. As Administrator, invite `mod.active@dasig.org` via **Moderator Management** and complete activation; verify `role = moderator` and `institution_id = NULL`.
2. As `mod.active@dasig.org`, open `sub-std` in the Validation Queue to acquire an active `ReviewLock` (`status = in_review`).
3. As Administrator, change `mod.active@dasig.org`'s role to `Contributor` assigned to `"CITU"`; verify `sub-std`'s lock is immediately released, `mod.active`'s JWT is invalidated, and `USER_ROLE_CHANGED` is logged.
4. Change `mod.active@dasig.org` back to `Moderator` and verify `institution_id` is cleared to `NULL`.

---

## **3.13 Case-13: Standalone Media Library Uploads, Nested Albums & Manual Tag Floor (UC-2.1)** {#313-case-13-standalone-media-library-uploads-nested-albums--manual-tag-floor-uc-21}

### **3.13.1 Purpose**

Verify standalone Media Library uploads across all three roles, text-based Album Auto-Match (`≥0.6` confident, `≥0.3` ambiguous), nested parent-child album creation and cycle-guarded re-parenting (`UC-2.1 A8`), the mandatory `≥1` actor-entered (`source: "manual"`) tag floor (`UC-2.1 A9`), cross-institution asset movement (`UC-2.1 A10`), and asynchronous image `PROCESSING → READY` transition vs. immediate video `READY` status.

### **3.13.2 Inputs**

- **Upload Payload:** Image `summit-stage.png` (`3.1 MB`) and video `keynote-recap.mp4` (`18 MB`), manual tag `"summit2026"`, nested album path `"2026 Events / Q4 / Regional Summit"`.
- **Album Hierarchy:** Parent Album `A` → Child Album `B` → Grandchild Album `C`.

### **3.13.3 Expected Outputs & Pass/Fail Criteria**

- **Expected Outputs:**
  1. Standalone upload without at least one manual tag or without an album is rejected (`HTTP 400`).
  2. Specifying the nested folder path `"2026 Events / Q4 / Regional Summit"` creates any missing parent/child album segments in one operation; attempting to re-parent Album `A` under its own descendant Album `C` is blocked by the cycle guard (`UC-2.1 A8`).
  3. On upload confirmation, `summit-stage.png` starts in `PROCESSING` status and transitions to `READY` once async AI classification and Voyage embedding complete; `keynote-recap.mp4` is marked `READY` immediately without queuing classification/embedding (`UC-2.1 Main Flow`).
  4. Even after `summit-stage.png` receives 10 AI-generated tags (`source = "ai_generated"`), attempting to remove its last manual tag (`"summit2026"`, `source = "manual"`) is rejected (`HTTP 400`, remove `×` hidden in UI) (`UC-2.1 A9`).
  5. Attempting to set an asset's `albumId` to `null` ("unfiled") is rejected (`HTTP 400`, `UC-2.1 A5`).
- **Pass/Fail Criteria:** **PASS** if nested album cycle prevention, `PROCESSING → READY` transitions, non-null album enforcement, and the `source: "manual"` tag floor all pass; **FAIL** otherwise.

### **3.13.4 Test Procedure**

1. Open the **Media Library** and upload `summit-stage.png` and `keynote-recap.mp4` with tag `"summit2026"` into nested path `"2026 Events / Q4 / Regional Summit"`.
2. Verify `keynote-recap.mp4` is immediately `READY` while `summit-stage.png` shows `"Processing…"` and transitions to `READY` after async enrichment.
3. Open `summit-stage.png` in the **Asset Detail Panel** after AI tags populate; attempt to delete `"summit2026"` (the sole manual tag) via UI and `DELETE` API; assert `400 Bad Request`. Add a second manual tag `"citu"`, then delete `"summit2026"`; assert success.
4. Create album hierarchy `A → B → C` and attempt to move `A` inside `C`; assert cycle-guard rejection.

---

## **3.14 Case-14: Semantic Media Search, Keyword Fallback & Asset Detail Panel (UC-2.2)** {#314-case-14-semantic-media-search-keyword-fallback--asset-detail-panel-uc-22}

### **3.14.1 Purpose**

Verify Media Library browsing, meaning-based semantic search (`Voyage AI` query embedding + `pgvector` cosine similarity) with keyword fallback (`UC-2.2 Main Flow`), searchability of newly added manual tags (`UC-2.2 A3`), inline display-title editing (`display_title`), Network View institution filtering for Moderators/Administrators, and `Add to Existing Draft` (`UC-2.2 A4, A5`).

### **3.14.2 Inputs**

- **Search Queries:**
  - Semantic query: `"students receiving trophy on stage"` (matches image description/embedding even without exact keyword overlap).
  - Custom manual tag query: `"custom-hackathon-tag-99"`.
- **Title Rename:** Click-to-edit asset title to `"CIT-U Grand Champions Trophy Photo"`.

### **3.14.3 Expected Outputs & Pass/Fail Criteria**

- **Expected Outputs:**
  1. Meaning-based search embeds the query via Voyage AI (`voyage-4-lite`, `1024` dims) and returns ranked matching assets via pgvector; if the embedding service is unreachable, search falls back seamlessly to keyword matching across filename, `display_title`, asset code, uploader email, AI metadata, and tags (`UC-2.2 Main Flow`).
  2. Adding manual tag `"custom-hackathon-tag-99"` to an asset makes that asset immediately discoverable via keyword search (`UC-2.2 A3`).
  3. Editing the title in the **Asset Detail Panel** updates `display_title` (`POST /api/v1/media-assets/{id}/title` / `PATCH`) without altering the original `file_name` or R2 storage key (`UC-2.2 Main Flow`).
  4. **Use in New Social Media Post** and **Add to Existing Draft** are available to all three roles (`Contributor`, `Moderator`, `Administrator`); attaching multiple assets stops cleanly at the 10-asset-per-submission ceiling (`UC-2.2 A5`).
- **Pass/Fail Criteria:** **PASS** if semantic/keyword search, manual tag indexing, non-destructive display-title editing, and draft attachment all succeed; **FAIL** otherwise.

### **3.14.4 Test Procedure**

1. Navigate to **Media Library**, enter semantic query `"students receiving trophy on stage"`, and verify relevant award photos rank at the top.
2. Open an asset's **Asset Detail Panel**, add manual tag `"custom-hackathon-tag-99"`, and search for `"custom-hackathon-tag-99"`; verify the asset is returned immediately.
3. Click the asset's title in the detail panel, rename it to `"CIT-U Grand Champions Trophy Photo"`, press Enter, and verify the updated title persists while `file_name` and the `/api/v1/media-files/<key>` proxy URL remain unchanged.
4. Click **Add to Existing Draft** on a draft that already has 9 media attachments while selecting 2 library assets; verify the 10th attaches and the 11th triggers the 10-asset limit notice (`UC-2.2 A5`).

---

## **3.15 Case-15: Media History Tracking, Tiered Deletion Precedence & Duplicate Hash Detection (UC-2.3)** {#315-case-15-media-history-tracking-tiered-deletion-precedence--duplicate-hash-detection-uc-23}

### **3.15.1 Purpose**

Verify the **Used In** relationship history in the Asset Detail Panel (`UC-2.3 Main Flow`), the 3-tier asset deletion precedence (`UC-2.3 A1–A3`, `BR-MED-02`), placeholder rendering for deleted records, and SHA-256 content-hash duplicate detection upon upload (`UC-2.3 A4`).

### **3.15.2 Inputs**

- **Asset 1 (`asset-active`):** Referenced in a `PENDING` or `SCHEDULED` submission.
- **Asset 2 (`asset-draft`):** Referenced only in a `DRAFT` or `NEEDS_REVISION` submission.
- **Asset 3 (`asset-terminal`):** Referenced only in a `PUBLISHED` or `REJECTED` submission (or unreferenced).
- **Duplicate Upload:** Re-uploading the exact byte-identical `summit-stage.png` within the same institution.

### **3.15.3 Expected Outputs & Pass/Fail Criteria**

- **Expected Outputs:**
  1. Attempting to delete `asset-active` is hard-blocked (`HTTP 409 Conflict`) and displays the conflicting active submission (`UC-2.3 A1`).
  2. Attempting to delete `asset-draft` without confirmation returns a warning prompt; confirming with `force=true` soft-deletes the asset and surfaces a broken-reference warning on the affected draft (`UC-2.3 A2`).
  3. Deleting `asset-terminal` succeeds with standard confirmation, marks the asset soft-deleted for the 30-day retention buffer (`MediaAssetRetentionPurgeJob`), and preserves historical reuse rows (`[Asset Deleted]` / `[Submission Deleted]` placeholders, `UC-2.3 A3`).
  4. Uploading a file whose SHA-256 content hash matches an existing non-deleted asset in the same institution triggers the duplicate-detection modal offering **"Upload Anyway"**, **"Cancel"**, or **"Use Existing"** (`UC-2.3 A4`).
- **Pass/Fail Criteria:** **PASS** if all 3 deletion tiers, `Used In` history placeholders, and SHA-256 duplicate prompts function per `UC-2.3`; **FAIL** otherwise.

### **3.15.4 Test Procedure**

1. Attempt to delete `asset-active` from the Media Library; assert `409 Conflict` listing the active submission.
2. Attempt to delete `asset-draft`; verify the broken-reference warning modal requires `force=true` confirmation.
3. Delete `asset-terminal`; verify it disappears from the active library grid while its historical submission still displays `[Asset Deleted]` in audit/history views.
4. Upload a byte-for-byte copy of `summit-stage.png` to `"CITU"`; verify the SHA-256 duplicate prompt appears and clicking **"Use Existing"** opens the existing asset in the repository.

---

## **3.16 Case-16: Approval Workflow, 15-Minute Review Lock & Self-Review Prevention (UC-2.4)** {#316-case-16-approval-workflow-15-minute-review-lock--self-review-prevention-uc-24}

### **3.16.1 Purpose**

Verify the network-wide Validation Queue (`UC-2.4`), 15-minute `ReviewLock` acquisition, concurrent read-only lock contention (`UC-2.4 A3`), automatic lock expiry cleanup (`UC-2.4 A4`, `BR-APP-01`), unconditional self-submission review blocking (`UC-2.4 A5`), and terminal decisions (`Approve`, `Request Revision` with `10–1,000` char remarks, and `Reject` with standardized reason codes `BR-APP-02, BR-APP-03`).

### **3.16.2 Inputs**

- **Reviewers:** Moderator 1 (`mod.a@dasig.org`), Moderator 2 (`mod.b@dasig.org`), Administrator (`admin.owner@dasig.org`).
- **Submissions:**
  - `sub-citu-1`: Submitted by Contributor `contrib.citu@cit.edu`.
  - `sub-mod-self`: Submitted by Moderator 1 (`mod.a@dasig.org`).
- **Revision Remarks:** `"Too short"` (`9` chars — invalid) vs. `"Please add the official DOST Region 7 partner acknowledgment and registration URL."` (`82` chars — valid).
- **Rejection Payload:** Code `INCOMPLETE_CONTENT` (deprecated/invalid for reject) vs. `DUPLICATE_EVENT` (valid) and `OTHER` (requires written notes).

### **3.16.3 Expected Outputs & Pass/Fail Criteria**

- **Expected Outputs:**
  1. Moderator 1 opening `sub-citu-1` transitions it `pending → in_review` and acquires a 15-minute `ReviewLock` (`BR-APP-01`).
  2. Moderator 2 opening `sub-citu-1` while Moderator 1 holds the lock sees who holds the lock and views the panel in read-only mode (`UC-2.4 A3`).
  3. If Moderator 1 takes no action for 15 minutes, `ReviewLockCleanupJob` releases the lock and reverts `sub-citu-1` from `in_review` to `pending` (`UC-2.4 A4`).
  4. Moderator 1 attempting to acquire a review lock on or approve/reject their own submission (`sub-mod-self`) is unconditionally blocked (`HTTP 403/409`, `UC-2.4 A5`).
  5. **Request Revision** rejects remarks `<10` chars or `>1,000` chars (`400/422`, `UC-2.4 A2`), and with valid remarks transitions `sub-citu-1` to `needs_revision`, releases its `SlotReservation`, and notifies the Contributor (in-app + email).
  6. **Reject** rejects `INCOMPLETE_CONTENT`/`WRONG_FORMAT` with `HTTP 422` (directing to Request Revision), requires written notes when reason is `OTHER`, and with a valid code (`DUPLICATE_EVENT`) transitions the post to `rejected` and releases its slot (`BR-APP-03`).
- **Pass/Fail Criteria:** **PASS** if lock exclusivity, 15-min timeout, self-review block, revision length bounds, and rejection reason validation all pass; **FAIL** otherwise.

### **3.16.4 Test Procedure**

1. Log in as Moderator 1, navigate to **Validation Queue**, and click **Review** on `sub-citu-1`; verify `in_review` state and 15-minute lock creation.
2. Log in as Moderator 2 in a second browser session and open `sub-citu-1`; verify action buttons are disabled and Moderator 1 is shown as the active reviewer.
3. As Moderator 1, attempt to review `sub-mod-self` (authored by Moderator 1); assert self-review block (`UC-2.4 A5`).
4. On `sub-citu-1`, click **Request Revision** with a 9-character remark (assert blocked), then submit the 82-character remark; verify transition to `needs_revision` and slot release.
5. On another pending submission, test **Reject** with `OTHER` and empty notes (assert blocked), then with `DUPLICATE_EVENT`; verify transition to `rejected` and verify the Contributor can later edit and resubmit (`UC-1.5 A4`, `UC-2.4 Postconditions`).

---

## **3.17 Case-17: Edit-During-Review Diff Tracking, Restore Original & Late Publish Override (UC-2.4 A9–A15)** {#317-case-17-edit-during-review-diff-tracking-restore-original--late-publish-override-uc-24-a9a15}

### **3.17.1 Purpose**

Verify inline editing during review (`UC-2.4 A9`), whole-cycle edit diff tracking across lock releases (`UC-2.4 A10`), severity classification (`Quiet`, `Flagged`, `Added Media`) and separate Contributor edit notification (`UC-2.4 A11`), **Review & Save** / **Restore original** safeguards (`UC-2.4 A14`), and **Late Publish Override** when a submission's scheduled slot passes during review (`UC-2.4 A15`).

### **3.17.2 Inputs**

- **Submission Under Review (`sub-edit`):** Locked by Moderator 1; original caption `"CIT-U hosts regional coding challenge."`
- **Edits Made:** Caption substantially rewritten (`>20%` change → `Flagged` severity) and 1 library photo added (`Added Media`), followed by lock release, lock re-acquisition, and final **Approve**.
- **Expired-Slot Submission (`sub-late`):** Scheduled slot `scheduled_at` has passed (`< NOW()`) while held under an active `ReviewLock`.

### **3.17.3 Expected Outputs & Pass/Fail Criteria**

- **Expected Outputs:**
  1. Saving an inline caption edit runs the advisory AI writing check (`POST /api/v1/ai/proofread`) and displays a **Review & Save** diff summary with Undo; clicking **Restore original** reverts fields back to the Contributor's submitted values.
  2. Releasing the review lock after editing, re-acquiring the lock later in the same review cycle, and clicking **Approve** with no further edits still records `edited = true` and preserves the full before/after diff across the entire review cycle (`UC-2.4 A10`).
  3. On approval of an edited submission, the Contributor receives the base approval notification (`T-02`) **plus** a separate edit notification (`UC-2.4 A11`: `Quiet` → in-app only; `Flagged` / `Added Media` → in-app + email, plus in-app alert to other Admins).
  4. While `sub-late` holds an active `ReviewLock`, `StaleSubmissionDetectorJob` skips marking it `missed_review`; clicking **Approve** normally returns `HTTP 409 Conflict` (slot passed), requiring either selecting a new future slot or (Administrator only) invoking `?publishNow=true`, which sets the slot to `NOW()`, publishes, and logs `LATE_PUBLISH_OVERRIDE`.
- **Pass/Fail Criteria:** **PASS** if cycle-wide diff persistence, `Restore original`, severity-tiered notifications, and `LATE_PUBLISH_OVERRIDE` all behave per `UC-2.4`; **FAIL** otherwise.

### **3.17.4 Test Procedure**

1. Lock `sub-edit` as Moderator 1, modify the caption significantly, verify **Review & Save** diff and AI proofread suggestions, save the edit, then test **Restore original** and re-apply the edit.
2. Release the lock on `sub-edit`, re-open `sub-edit` to acquire a new lock, and click **Approve** without making additional edits; verify the audit log and Contributor notifications record the edit made before the lock release.
3. Lock `sub-late` whose `scheduled_at` is 2 minutes in the future, wait until `scheduled_at < NOW()`, verify the `"slot passed"` banner appears and `StaleSubmissionDetectorJob` does not steal the locked item, then as Administrator approve with `Publish Now` (`?publishNow=true`) and verify `LATE_PUBLISH_OVERRIDE` in the audit log.

---

## **3.18 Case-18: Automated Brand Watermarking, Aspect-Ratio Rendering & Opt-Out (UC-2.5)** {#318-case-18-automated-brand-watermarking-aspect-ratio-rendering--opt-out-uc-25}

### **3.18.1 Purpose**

Verify the Administrator Watermark Configuration canvas (`1:1`, `4:5`, `16:9` aspect-ratio previews, up to 3 image/text/shape elements), publish-time proportional rendering on photo assets, built-in default text watermark fallback (`UC-2.5 A1`), per-asset watermark opt-out (`skipWatermark = true`, `UC-2.5 A5`), non-blocking fallback on render failure (`UC-2.5 A3`), and 3-day derivative cleanup (`GeneratedWatermarkPurgeJob`).

### **3.18.2 Inputs**

- **Watermark Config:** 3 elements — DASIG PNG logo (`bottom-right`, `12%` width), text `"DASIG Region 7"` (`bottom-left`), and accent rectangle bar.
- **Submission Media:** Photo 1 (`skipWatermark = false`), Photo 2 (`skipWatermark = true`), and Video 1 (`MP4`).
- **Corrupted Asset Scenario:** Photo 3 with unreadable/corrupted image stream at publish time.

### **3.18.3 Expected Outputs & Pass/Fail Criteria**

- **Expected Outputs:**
  1. Saving the watermark configuration in **Page Settings → Watermark** stores percentage-based coordinates and element properties network-wide; updating the config between a post's approval and its scheduled publish time applies the _updated_ config at publish time (`UC-2.5 Main Flow & A2`).
  2. At publish time, Photo 1 is rendered with the proportional watermark overlay into `generated/watermarked/` while the original asset in `media_assets` remains unmodified; Photo 2 (`skipWatermark = true`) and Video 1 publish untouched (`UC-2.5 A4, A5`).
  3. If no watermark config exists in the database, a built-in default text watermark is applied unless watermarking is explicitly disabled (`UC-2.5 A1`).
  4. When Photo 3 fails Java2D rendering, the publisher falls back to publishing the original unwatermarked photo without failing the post, writes `PUBLISH_WATERMARK_FAILED` to the audit log, and dispatches an in-app + email alert to all Administrators (`UC-2.5 A3`).
  5. `GeneratedWatermarkPurgeJob` deletes generated R2 objects under `generated/watermarked/` older than `3 days` (`app.watermark.generated-retention-days`).
- **Pass/Fail Criteria:** **PASS** if live publish-time rendering, opt-out honoring, render-failure resilience + audit/alerting, and derivative purge all pass; **FAIL** otherwise.

### **3.18.4 Test Procedure**

1. Log in as Administrator, open **Watermark Configuration**, add 1 logo, 1 text, and 1 shape element, preview across `1:1`, `4:5`, and `16:9` toggles, and save.
2. Approve a scheduled submission containing Photo 1 (`skipWatermark = false`) and Photo 2 (`skipWatermark = true`), modify the watermark text before the scheduled slot arrives, and trigger publishing; verify Photo 1 uses the updated watermark and Photo 2 is unwatermarked.
3. Simulate a rendering exception on Photo 3 during publish; verify the post still transitions to `PUBLISHED` using the original image, `PUBLISH_WATERMARK_FAILED` is logged, and Admins receive notifications.
4. Trigger `GeneratedWatermarkPurgeJob` with derivatives older than 3 days; verify R2 cleanup.

---

## **3.19 Case-19: Master Calendar Visibility, Moderator Reschedule Caps & GiST Conflict Integrity (UC-3.1)** {#319-case-19-master-calendar-visibility-moderator-reschedule-caps--gist-conflict-integrity-uc-31}

### **3.19.1 Purpose**

Verify role-scoped Master Calendar visibility (`UC-3.1 Main Flow`), Moderator drag-reschedule caps (`≤2` reschedules per submission and `±1 day` of `originalScheduledAt`, `UC-3.1 A1`), Administrator hard guard-rail override with justification (`UC-3.1 A2`), atomic concurrent reschedule protection (`UC-3.1 A3`), and database-level PostgreSQL GiST exclusion constraint integrity (`V95`/`V96`, `UC-3.1 A4`).

### **3.19.2 Inputs**

- **Actors:** Contributor from `"CITU"`, Moderator (`mod.a@dasig.org`), Administrator (`admin.owner@dasig.org`).
- **Calendar Events:**
  - `sub-citu-sched`: `SCHEDULED` post from `"CITU"` originally approved at `2026-10-25 10:00 AM` (`originalScheduledAt`).
  - `sub-su-sched`: `SCHEDULED` post from `"Silliman University"` at `2026-10-25 02:00 PM`.
  - `sub-dasig-sched`: `SCHEDULED` post from `"DASIG Central Visayas"` at `2026-10-25 04:00 PM`.
  - `sub-citu-pending`: `PENDING` in-flight post authored by the `"CITU"` Contributor.

### **3.19.3 Expected Outputs & Pass/Fail Criteria**

- **Expected Outputs:**
  1. **Contributor Calendar View:** Renders full detail (title, caption, media) for `"CITU"` posts, `"DASIG Central Visayas"` posts, and the Contributor's own in-flight submissions (`sub-citu-pending`), while masking `"Silliman University"`'s `sub-su-sched` so caption, description, and contributor identity are withheld (`UC-3.1 Main Flow`).
  2. **Moderator & Administrator Calendar View:** Renders all network-wide events in full detail.
  3. **Moderator Reschedule Caps (`UC-3.1 A1`):** Moderator can reschedule `sub-citu-sched` twice within `±1 day` of `2026-10-25 10:00 AM` (providing mandatory written justification each time); a 3rd reschedule attempt—or any move beyond `±24 hours` from `originalScheduledAt`—is hard-blocked (`HTTP 403/422`).
  4. **Guard-Rail Conflict & GiST Exclusion (`UC-3.1 A2, A4`):** Moving `sub-citu-sched` to `2026-10-25 02:15 PM` (within `±30` minutes of `sub-su-sched`) is blocked for Moderator (`403`) and rejected at the DB layer by the `slot_reservations` GiST exclusion constraint (`V95`) for normal requests, **except** when an Administrator explicitly supplies an override justification, which sets `admin_override = true` (`V96`) and logs the override in the audit log.
  5. Two concurrent reschedule requests for `sub-citu-sched` execute via atomic conditional `UPDATE`; only one succeeds while the second receives `HTTP 409 Conflict` (`UC-3.1 A3`).
- **Pass/Fail Criteria:** **PASS** if role masking, Moderator `2-count` / `±1-day` anchor caps, atomic race protection, and GiST exclusion + Admin override exemption all pass; **FAIL** otherwise.

### **3.19.4 Test Procedure**

1. Fetch `GET /api/v1/calendar/events` as `"CITU"` Contributor; verify `sub-su-sched` is masked while `sub-citu-sched`, `sub-dasig-sched`, and `sub-citu-pending` include full details.
2. As Moderator, drag `sub-citu-sched` from `10:00 AM` to `11:00 AM` (Reschedule #1) and then to `12:30 PM` (Reschedule #2) with justification; attempt Reschedule #3 or a move to `2026-10-27` (`> ±1 day` from original slot) and assert hard block.
3. Attempt to reschedule a post into `02:15 PM` (conflicting with `sub-su-sched` at `02:00 PM`) as Moderator; assert `403`. Repeat as Administrator with an explicit override justification; verify `admin_override = true` succeeds and writes an audit log entry.
4. Dispatch two parallel reschedule `PATCH` requests for the same submission; assert one returns `200 OK` and the other returns `409 Conflict`.

---

## **3.20 Case-20: Automated Facebook Publishing, Atomic Claiming & Retry/Cleanup Pipeline (UC-3.2)** {#320-case-20-automated-facebook-publishing-atomic-claiming--retrycleanup-pipeline-uc-32}

### **3.20.1 Purpose**

Verify `PublishingSchedulerJob` (1-minute cadence, 5-minute lookback), atomic `scheduled → publishing` claiming to prevent double-publishing, 2-step multi-photo publishing (`/{PAGE_ID}/photos` → `/{PAGE_ID}/feed`), single-call video publishing (`/{PAGE_ID}/videos`), orphaned staged-photo cleanup (`photoIdsCleanupFailed`), 3-retry exponential backoff (`5s, 25s, 125s`, `GR-T5`), and Fast-Track immediate asynchronous publishing (`UC-3.2 Main Flow & A1, A5, A6`).

### **3.20.2 Inputs**

- **Submission 1 (`sub-photos`):** `SCHEDULED` due now, 2 JPEG photos with per-asset captions and main caption containing `#DASIG7`.
- **Submission 2 (`sub-video`):** `SCHEDULED` due now, 1 MP4 video.
- **Submission 3 (`sub-mixed`):** `SCHEDULED` due now, 1 JPEG photo + 1 MP4 video.
- **Simulated API Failure (`sub-fail`):** 2-photo submission where Photo 1 stages (`photo_id_1`), Photo 2 fails, and subsequent retries fail.

### **3.20.3 Expected Outputs & Pass/Fail Criteria**

- **Expected Outputs:**
  1. Overlapping scheduler passes cannot double-publish `sub-photos` because `claimForPublishing` atomically transitions `scheduled → publishing` (`UPDATE ... WHERE status = 'scheduled'`) before any Facebook Graph API request is made (`UC-3.2 Main Flow`).
  2. `sub-photos` stages both watermarked images with `published=false` and their per-asset captions, then publishes one `/feed` post without duplicating `#DASIG7` in the message, transitions to `PUBLISHED`, releases its `SlotReservation`, and notifies the Contributor (`T-04`).
  3. `sub-video` publishes via a single `POST /{PAGE_ID}/videos` call and transitions to `PUBLISHED`.
  4. `sub-mixed` (image + video) is immediately marked `PUBLISH_FAILED` with zero Graph API retries (`UC-3.2 A6`).
  5. On `sub-fail`, when Photo 2 staging fails, the system attempts `DELETE /{photo_id_1}` to clean up the orphaned photo (`UC-3.2 A1`), retries up to 3 times with backoff (`5s, 25s, 125s`), and upon exhaustion transitions to `PUBLISH_FAILED`, surfaces any undeleted `unresolvedPhotoIds` (`photoIdsCleanupFailed`) in the Failed tab, and alerts Administrators (`T-06`).
- **Pass/Fail Criteria:** **PASS** if atomic claim, 2-step photo publish, single-call video publish, mixed-media immediate fail, retry backoff, and orphan cleanup tracking all pass; **FAIL** otherwise.

### **3.20.4 Test Procedure**

1. Mark `sub-photos` and `sub-video` due (`scheduled_at = NOW() - 1 min`) and invoke `PublishingSchedulerJob` concurrently from two threads; verify each submission is claimed and published exactly once.
2. Verify `sub-photos` executes two `/photos` calls (`published=false`) followed by one `/feed` call, records `facebookPostId`, releases its `SlotReservation`, and does not duplicate existing hashtags.
3. Run `PublishingSchedulerJob` on `sub-mixed`; verify immediate transition to `PUBLISH_FAILED` without retries.
4. Simulate a Graph API failure after Photo 1 stages on `sub-fail` where `DELETE /{photo_id_1}` also fails; verify 3 retries execute, status becomes `PUBLISH_FAILED`, `photoIdsCleanupFailed` records `photo_id_1`, and `T-06` is dispatched.

---

## **3.21 Case-21: Missed Slot Sweep (`PUBLISH_FAILED` vs. `MISSED_REVIEW`) & Manual Publishing Fallback (UC-3.2 A2, UC-2.4 A8)** {#321-case-21-missed-slot-sweep-publish_failed-vs-missed_review--manual-publishing-fallback-uc-32-a2-uc-24-a8}

### **3.21.1 Purpose**

Verify that `StaleSubmissionDetectorJob` (every 5 minutes, `GR-T9`) distinguishes between an already-approved `SCHEDULED` submission that missed its publish window (`→ PUBLISH_FAILED`) and an unreviewed `PENDING` submission whose slot passed (`→ MISSED_REVIEW`), and verify the 3-step **Manual Publishing Fallback** recovery workflow (`UC-2.4 A8`, `BR-PUB-04, BR-PUB-05`).

### **3.21.2 Inputs**

- **Stale Scheduled Submission (`sub-stale-sched`):** `status = scheduled`, `scheduled_at = NOW() - 10 minutes`.
- **Stale Unreviewed Submission (`sub-stale-pend`):** `status = pending`, `scheduled_at = NOW() - 10 minutes`, no active `ReviewLock`.
- **Stuck Fast-Track Submission (`sub-stuck-ft`):** `status = publishing`, `scheduled_at = NULL`, `updated_at = NOW() - 10 minutes`.
- **Manual Fallback Input:** Facebook Post URL `"https://www.facebook.com/DASIGRegion7/posts/123456789012345"`, manual publish timestamp, and notes `"Published manually during Graph API maintenance."`

### **3.21.3 Expected Outputs & Pass/Fail Criteria**

- **Expected Outputs:**
  1. `StaleSubmissionDetectorJob` transitions `sub-stale-sched` (`scheduled`) to `PUBLISH_FAILED` and surfaces it in the Validation Queue's **Failed** tab (`UC-3.2 A2`).
  2. `StaleSubmissionDetectorJob` transitions `sub-stale-pend` (`pending`) to `MISSED_REVIEW`, releases its `SlotReservation`, and routes it back into the Approval Workflow rather than the Manual Publishing Fallback flow (`UC-3.2 A2`).
  3. `findStuckFastTrackPublishing` detects `sub-stuck-ft` (`publishing` with `scheduled_at IS NULL` and `updated_at < NOW() - 5 min`) and recovers it to `PUBLISH_FAILED`.
  4. In the **Failed** tab, an Administrator completing the 3-step **Manual Publishing Fallback** (Download watermarked media → Copy caption → Paste valid Facebook Post URL & confirm) transitions `sub-stale-sched` to `PUBLISHED_MANUAL`, releases its slot reservation, writes a complete audit entry (`BR-PUB-04`), notifies the Contributor (`T-05`), and includes the post in Analytics KPIs (`BR-PUB-05`).
  5. If a failed submission is retried onto a new schedule (`retryWithNewSchedule`), both `originalScheduledAt` and `moderatorRescheduleCount` reset to the new baseline (`UC-3.1 A1`).
- **Pass/Fail Criteria:** **PASS** if `PUBLISH_FAILED` vs. `MISSED_REVIEW` separation, stuck Fast-Track recovery, manual fallback completion, and reschedule-baseline reset all pass; **FAIL** otherwise.

### **3.21.4 Test Procedure**

1. Seed `sub-stale-sched`, `sub-stale-pend`, and `sub-stuck-ft` past the 5-minute cutoff and run `StaleSubmissionDetectorJob`.
2. Verify `sub-stale-sched` and `sub-stuck-ft` transition to `PUBLISH_FAILED` while `sub-stale-pend` transitions to `MISSED_REVIEW` with its slot released.
3. Open `sub-stale-sched` in the Validation Queue **Failed** tab as Administrator, execute the 3-step Manual Publishing Fallback with the Facebook URL, and verify transition to `PUBLISHED_MANUAL` and `T-05` notification.
4. On another `PUBLISH_FAILED` submission that previously had `moderatorRescheduleCount = 2`, execute **Retry with New Schedule** and verify `moderatorRescheduleCount` resets to `0` and `originalScheduledAt` updates to the new slot.

---

## **3.22 Case-22: Real-Time SSE Notifications, Cross-Session Read Sync & Messenger Delivery (UC-3.3)** {#322-case-22-real-time-sse-notifications-cross-session-read-sync--messenger-delivery-uc-33}

### **3.22.1 Purpose**

Verify the event-driven notification system (`UC-3.3`, `BR-NOT-01..04`), including recipient set determination (review triggers notifying **both** Moderators and Administrators), real-time SSE badge delivery without polling, cross-session `read` / `read-all` synchronization (`UC-3.3 A2`), independent email retry on failure (`UC-3.3 A1`), and opt-in Facebook Messenger DM delivery (`UC-3.3 A4, A5`).

### **3.22.2 Inputs**

- **Active Recipients:**
  - Moderator 1 (`mod.linked@dasig.org` — Messenger linked, open in Browser Tab A and Browser Tab B).
  - Administrator 1 (`admin.unlinked@dasig.org` — Messenger **not** linked).
  - Contributor 1 (`contrib.citu@cit.edu`).
- **Triggering Events:** New Fast-Track submission (`T-11`) by Contributor 1, followed by Moderator 1 clicking a notification in Tab A and clicking **Mark All Read**.

### **3.22.3 Expected Outputs & Pass/Fail Criteria**

- **Expected Outputs:**
  1. Submitting the Fast-Track post dispatches `T-11` to **every active Moderator and every active Administrator** (`UC-3.3 Main Flow`).
  2. Both Moderator 1 and Administrator 1 receive instant in-app SSE `notification` events (`GET /api/v1/notifications/stream`), incrementing their navbar bell badges in real time without page refresh (`BR-NOT-01, BR-NOT-02`).
  3. Moderator 1 (Messenger linked) receives the outbound Facebook Messenger DM (`UC-3.3 A4`), whereas Administrator 1 (Messenger not linked) is silently skipped for Messenger while still receiving in-app and email notifications (`UC-3.3 A5`).
  4. When Moderator 1 marks a notification read (or clicks **Mark All Read**) in Browser Tab A, the backend pushes an SSE `read` / `read-all` event to Browser Tab B, synchronizing the unread badge count across both active sessions (`UC-3.3 A2`).
  5. Simulated SMTP failure logs the failed attempt and retries up to 3 times independently without delaying or blocking the in-app SSE notification (`UC-3.3 A1`).
- **Pass/Fail Criteria:** **PASS** if Moderator+Admin fan-out, real-time SSE badge updates, cross-tab read sync, Messenger opt-in filtering, and email failure isolation all pass; **FAIL** otherwise.

### **3.22.4 Test Procedure**

1. Open two simultaneous authenticated browser tabs (Tab A and Tab B) for `mod.linked@dasig.org` and one tab for `admin.unlinked@dasig.org`.
2. Submit a Fast-Track post as `contrib.citu@cit.edu`; verify both `mod.linked` and `admin.unlinked` receive the in-app SSE notification within `<5` seconds and only `mod.linked` receives a Messenger dispatch.
3. Click the unread notification in Tab A of `mod.linked`; verify Tab B's unread badge decrements automatically via SSE `read` sync without refreshing the page.
4. Simulate an SMTP exception during a `T-02` approval notification; verify the in-app notification still arrives immediately and `email_delivery_logs` records the retry state.

---

## **3.23 Case-23: Role-Scoped Social Engagement Analytics & CSV Formula-Injection Sanitization (UC-3.4)** {#323-case-23-role-scoped-social-engagement-analytics--csv-formula-injection-sanitization-uc-34}

### **3.23.1 Purpose**

Verify role-scoped access and data filtering on the `/analytics` dashboard for Contributors, Moderators, and Administrators (`UC-3.4 Main Flow & A4`, `BR-ANA-01..06`), live workflow KPI calculation vs. cached Facebook engagement metrics, inline export error retry (`UC-3.4 A3`), and CWE-1236 CSV Formula-Injection sanitization on exported CSV files.

### **3.23.2 Inputs**

- **Actors:** Contributor (`contrib.citu@cit.edu`), Moderator (`mod.a@dasig.org`), Administrator (`admin.owner@dasig.org`).
- **Malicious Submission Title:** `"=HYPERLINK(\"http://evil.example/leak\", \"Click Here\")"` and `"+SUM(1,1)"` in a published `"CITU"` submission.
- **Export Metrics Requested:** `posts-by-institution`, `facebook-engagement`, `operational-health`, `ai-performance`.

### **3.23.3 Expected Outputs & Pass/Fail Criteria**

- **Expected Outputs:**
  1. All three roles can access `/analytics` (`UC-3.4 Main Flow`):
     - **Contributor:** Strictly scoped to their own institution (`"CITU"`); requesting another institution's analytics returns `HTTP 403/404` (`UC-3.4 A4`).
     - **Moderator:** Sees network-wide metrics across all institutions unfiltered; institution drilldown and Admin-only reports (`operational-health`, `ai-performance`) return `HTTP 403 Forbidden`.
     - **Administrator:** Sees network-wide metrics, multi-institution filters, Validator/Contributor workload drilldowns, `operational-health` (including the `≥95%` On-Time Publication target), and `ai-performance`.
  2. Exporting a report to CSV (`GET /api/v1/analytics/export-csv`) prepends a leading apostrophe (`'=HYPERLINK...`, `'+SUM(1,1)`) to any cell starting with `=`, `+`, `-`, `@`, `\t`, or `\r`, neutralizing spreadsheet formula execution (`CWE-1236`).
  3. If a CSV download fails due to a network error, the Report Modal displays an inline error footer and relabels the button to **"Retry Download"** while preserving active filters (`UC-3.4 A3`).
- **Pass/Fail Criteria:** **PASS** if role scoping, `403` guards on Admin-only reports, inline export retry, and CSV formula sanitization all pass; **FAIL** otherwise. _(Note: Static placeholder values in the non-admin "Content Quality & Compliance" card are tracked in `INC-04`.)_

### **3.23.4 Test Procedure**

1. Log in as Contributor, Moderator, and Administrator sequentially and inspect `/analytics`; verify institution filter visibility and test direct API calls to `operational-health` and `ai-performance` as Moderator/Contributor (assert `403 Forbidden`).
2. Seed a published submission with title `"=HYPERLINK(\"http://evil.example/leak\", \"Click Here\")"`, export the analytics CSV as Administrator, and inspect the raw CSV text; verify the cell is escaped with a leading apostrophe (`"'=HYPERLINK...`).
3. Intercept the CSV export request in the browser to force a `500` error; verify the inline error message and **"Retry Download"** button appear in `FullReportModal` without losing selected filters.

---

## **3.24 Case-24: System Health Analytics, 16 Tracked Jobs, On-Demand Re-Run & Token Re-Auth (UC-3.5)** {#324-case-24-system-health-analytics-16-tracked-jobs-on-demand-re-run--token-re-auth-uc-35}

### **3.24.1 Purpose**

Verify the Administrator-only **System Health** screen (`/admin/system-health`, `UC-3.5`), including PostgreSQL (`pg_database_size`) and Cloudflare R2 storage capacity probes (`80%` Warning, `95%` Critical), 6 external integration checks, 16 tracked background jobs with staleness thresholds (`> 2×` expected interval), synchronous vs. asynchronous manual job **Re-run** (`POST /api/v1/system-health/jobs/{jobKey}/run`), 14 operational & AI telemetry metrics (including missed-review rate), CSV snapshot export (`UC-3.5 A4`), and Facebook OAuth token re-authentication (`UC-3.5 A5`).

### **3.24.2 Inputs**

- **Actors:** Moderator (`mod.a@dasig.org` — unauthorized) and Administrator (`admin.owner@dasig.org` — authorized).
- **Manual Job Triggers:**
  - Synchronous DB job: `ReviewLockCleanupJob` (`review_lock_cleanup`).
  - Asynchronous external-API job: `PublishingSchedulerJob` (`publishing_scheduler`).

### **3.24.3 Expected Outputs & Pass/Fail Criteria**

- **Expected Outputs:**
  1. Moderator or Contributor requests to `/api/v1/system-health/**` return `HTTP 403 Forbidden` (`UC-3.5 Preconditions`).
  2. The System Health response returns:
     - **Storage Capacity:** Database bytes and R2 bucket bytes (with DB-estimate fallback if live R2 scan fails) evaluated against `80%` (`WARNING`) and `95%` (`UNHEALTHY`) thresholds (`UC-3.5 A2`).
     - **6 External Services:** Facebook Graph API, Anthropic Claude Vision, Voyage AI, Email Service Provider, Database Health, and Media Object Storage Health (missing keys or failed probes render as `UNAVAILABLE`, never a false healthy state, `UC-3.5 A1`).
     - **16 Tracked Background Jobs:** Status (`HEALTHY`, `WARNING`, `UNHEALTHY`, `UNAVAILABLE`, `SCHEDULED`).
     - **14 Operational & AI Metrics:** 6 core workflow metrics (including `missedReviewRate` flagged `WARNING` above `10%`) + 8 AI telemetry metrics.
     - **Overall Page Rollup:** Worst status across all sections with item counts.
  3. Clicking **Re-run** on `ReviewLockCleanupJob` executes synchronously, returns fresh status immediately, and writes `BACKGROUND_JOB_RUN` to the audit log; clicking **Re-run** on `PublishingSchedulerJob` or `TokenPublishingEscalationJob` executes asynchronously (`CompletableFuture.runAsync`) and returns an immediate `"started"` acknowledgment so the HTTP request never times out during retry backoff (`UC-3.5 Main Flow`).
  4. Exporting the System Health CSV snapshot writes an audit log entry (`UC-3.5 A4`), and Facebook OAuth re-authentication updates the encrypted token and logs `TOKEN_REAUTHORIZED` (`UC-3.5 A5`).
- **Pass/Fail Criteria:** **PASS** if RBAC, 4 health sections, `UNAVAILABLE` vs. `UNHEALTHY` semantics, sync/async manual job re-runs, and audit logging all match `UC-3.5`; **FAIL** otherwise.

### **3.24.4 Test Procedure**

1. Attempt to access `/api/v1/system-health` as a Moderator; assert `403 Forbidden`.
2. Open **System Health** as Administrator; verify all 2 storage cards, 6 service rows, 16 background job rows, and 14 operational/AI metric cards render with valid statuses and rollup counts.
3. Click **Re-run** on `ReviewLockCleanupJob` (verify synchronous completion) and on `PublishingSchedulerJob` (verify immediate async `"started"` response and `BACKGROUND_JOB_RUN` audit log entry).
4. Export the System Health Snapshot CSV and verify audit logging.

---

## **3.25 Case-25: Immutable Audit Log Review, Structured Diffs & Multi-Institution Tenant Isolation (UC-3.6, SRS 3.5)** {#325-case-25-immutable-audit-log-review-structured-diffs--multi-institution-tenant-isolation-uc-36-srs-35}

### **3.25.1 Purpose**

Verify the Administrator-only **Audit Log** (`/admin/audit-log`, `UC-3.6`), including server-side filtering across 11 categories and 8 entity types, structured before/after diff rendering, deleted-entity badge handling (`UC-3.6 A2`), 5,000-row sanitized CSV export (`UC-3.6 A4`), database-level append-only immutability (`V78` RLS policy), and the 4-layer **Multi-Institution Tenant Isolation** security boundary (`SRS Section 3.5`).

### **3.25.2 Inputs**

- **Actors:**
  - Contributor A at `"CITU"` (`contrib.citu@cit.edu`).
  - Contributor B at `"Silliman University"` (`contrib.su@su.edu.ph`).
  - Administrator (`admin.owner@dasig.org`).
- **Cross-Tenant Attack Vectors:**
  - Contributor A requests `GET /api/v1/submissions/{sub-su-id}`, `PUT /api/v1/submissions/{sub-su-id}`, and `GET /api/v1/media-assets/{asset-su-id}` belonging to `"Silliman University"`.
  - Contributor A sends `POST /api/v1/submissions` with a forged payload `{"institutionId": "<silliman-uuid>", ...}`.
- **Direct DB Audit Mutation Attempt:** SQL `UPDATE audit_logs SET action = 'TAMPERED'` and `DELETE FROM audit_logs WHERE id = ...`.

### **3.25.3 Expected Outputs & Pass/Fail Criteria**

- **Expected Outputs:**
  1. **Cross-Institution Read/Write Isolation (`SRS 3.5.4, 3.5.6`):** Contributor A's direct-access attempts by ID against `"Silliman University"` submissions or media assets return `HTTP 404 Not Found` (not `403 Forbidden`, preventing resource-existence information leakage).
  2. **Server-Derived Institution Identity (`SRS 3.5.2 Layer 4`):** Contributor A's forged `institutionId` in `POST /api/v1/submissions` is ignored and overridden with Contributor A's JWT-bound `"CITU"` `institution_id`.
  3. **Audit Log Filtering & Diff View (`UC-3.6 Main Flow & A2`):** Administrator filtering `/admin/audit-log` by category, action, entity type, actor, and date range returns paginated entries newest-first (`Asia/Manila`); opening an entry displays Actor details, client IP, a live jump link (or `"Deleted/Unavailable"` badge if the target entity was deleted), and a structured `Field / Previous Value / Updated Value` diff table.
  4. **Audit CSV Export (`UC-3.6 Main Flow & A4`):** Exporting filtered audit rows caps at `5,000` rows, sanitizes leading formula characters (`=`, `+`, `-`, `@`), and records `AUDIT_LOG_EXPORTED`.
  5. **Database Append-Only Guarantee (`UC-3.6 Postconditions`, `V78`):** Any `UPDATE` or `DELETE` executed against `audit_logs` under the application database role is rejected/blocked by PostgreSQL Row-Level Security (`V78__audit_log_append_only.sql`).
- **Pass/Fail Criteria:** **PASS** if all 4 tenant-isolation layers return `404`/override forged tenant IDs, audit filters/diffs/exports work as specified, and `audit_logs` rejects mutation; **FAIL** otherwise.

### **3.25.4 Test Procedure**

1. Authenticate as Contributor A (`"CITU"`) and attempt `GET`, `PUT`, and `DELETE` against `"Silliman University"` submission and media UUIDs; assert `HTTP 404 Not Found` on every call.
2. Submit a `POST /api/v1/submissions` request as Contributor A with `"Silliman University"`'s UUID in the request body; verify the created draft is bound to `"CITU"`.
3. Authenticate as Administrator, navigate to **System & Audit → Audit Log**, filter by `Edit & Revision` and `Reschedule & Override`, and open the detail drawer; verify the structured before/after diff table and `"Deleted/Unavailable"` badge on a deleted draft's audit row.
4. Export the Audit Log to CSV; verify `AUDIT_LOG_EXPORTED` is written and formula-prefix characters are escaped with `'`.
5. Execute an `UPDATE` and `DELETE` statement on `audit_logs` via the application database connection; verify PostgreSQL RLS denies both operations.

---

# **Appendix (Test Logs)** {#appendix-test-logs}

## **A.1 Log for Test n** {#a1-log-for-test-n}

The following execution logs record the verification runs conducted on **2026-10-11** against the DASIGConnect backend test suite (`./mvnw test` — JUnit 5, Mockito, MockMvc, H2/PostgreSQL migrations `V1`–`V104+`) and frontend E2E/regression suite (`npm run test:e2e` — Playwright 1.63 on Headless Chromium):

### **Log for Test 1 (`Case-1` — UC-1.1 Administrator Account Management & 3-Admin Cap)**

- **Date / Time:** `2026-10-11 09:05:12 PHT (UTC+8)`
- **Automated Test Reference:** `UserServiceTest` (`inviteAdmin_*`, `adminCapPolicy_*`, `deactivateAdmin_*`, `reactivateAdmin_*`)
- **Execution Trace:**
  - `POST /api/v1/users/invite` (`role=admin`) with 2 active Admins → `201 Created`, `state=PENDING`, SHA-256 token hash stored.
  - `POST /api/v1/users/invite` (`role=admin`) with 2 active + 1 pending Admin → Rejected (`409/422 Admin cap of 3 reached`).
  - Peer Admin (`isAdminOwner=false`) `PATCH /api/v1/users/{adminId}/deactivate` → Rejected (`403 Forbidden`).
  - Admin Owner (`isAdminOwner=true`) `PATCH /api/v1/users/{adminId}/deactivate` → `200 OK`, `state=INACTIVE`, `session_version` incremented, `USER_STATUS_UPDATED` logged.
- **Outcome:** **PASS**

### **Log for Test 2 (`Case-2` — UC-1.1 A7, A8 Admin Promotion & Admin Owner Transfer)**

- **Date / Time:** `2026-10-12 19:45:00 PHT (UTC+8)`
- **Automated Test Reference:** `UserServiceTest` (`changeRole_promote*`, `confirmPromotion_*`, `requestAdminTransfer_*`, `confirmAdminTransfer_*`)
- **Execution Trace:**
  - `POST /api/v1/users/change-role` (`newRole=admin`) → `200 OK`, slot reserved against 3-Admin cap, `ADMIN_PROMOTION_REQUESTED` logged, duplicate proposal rejected with `409 Conflict`.
  - `POST /api/v1/users/promotion/confirm` → `200 OK`, role elevated to `admin` (`isAdminOwner = false`), active session invalidated, `ADMIN_PROMOTION_CONFIRMED` logged.
  - Admin Owner transfer targeting Moderator (`mod.one@dasig.org`) → Rejected with `HTTP 400 Bad Request` (`"Admin ownership can only be transferred to an active administrator"`), enforcing that ownership transfers are strictly restricted to active Administrators.
  - Admin Owner transfer to active Administrator (`admin.peer@dasig.org`) → Initiation logs `ADMIN_TRANSFER_REQUESTED` with 24-hour expiry and dispatches in-app notification.
  - `POST /api/v1/users/admin-transfer/confirm` by target Administrator → `200 OK`, target promoted to Admin Owner (`isAdminOwner = true`), outgoing Owner retains Administrator status (`isAdminOwner = false`) without demotion, outgoing tokens invalidated, `ADMIN_TRANSFER_CONFIRMED` logged.
- **Outcome:** **PASS**

### **Log for Test 3 (`Case-3` — UC-1.2 Institution Lifecycle & Protected Institution)**

- **Date / Time:** `2026-10-11 09:16:03 PHT (UTC+8)`
- **Automated Test Reference:** `InstitutionService` / `UserServiceTest`
- **Execution Trace:**
  - Moderator `POST /api/v1/institutions` → `403 Forbidden`.
  - Admin creates `"CITU"` → `201 Created`; duplicate `"CITU"` → `409 Conflict`.
  - Deactivate `"DASIG Central Visayas"` → `400/409 Blocked (Protected Institution)`.
  - Permanent delete of empty test institution with confirmation code → `200 OK`, `INSTITUTION_DELETED` logged.
- **Outcome:** **PASS**

### **Log for Test 4 (`Case-4` — UC-1.3 Contributor Account Provisioning & Footprint-Aware Deletion)**

- **Date / Time:** `2026-10-11 09:22:18 PHT (UTC+8)`
- **Automated Test Reference:** `UserServiceTest` (`inviteContributor_*`, `moderatorScope_*`, `removeUser_withFootprint_anonymizesAtRest`, `erasePersonalData_*`)
- **Execution Trace:**
  - Moderator A invites `contrib.citu@cit.edu` → `201 Created`; Moderator B attempts cancel on Moderator A's invite → `403 Forbidden`.
  - Deleting `INACTIVE` Contributor with submission footprint → Record retained and anonymized at rest, `USER_REMOVED` logged.
  - Admin Owner invokes `DELETE /api/v1/users/{id}/erase` → PII scrubbed, `USER_ANONYMIZED` logged.
- **Outcome:** **PASS**

### **Log for Test 5 (`Case-5` — UC-1.4 Login, Lockout, Password Policy & Session Revocation)**

- **Date / Time:** `2026-10-11 09:28:50 PHT (UTC+8)`
- **Automated Test Reference:** `AuthServiceTest`, `AuthControllerTest`, `JWTServiceTest`
- **Execution Trace:**
  - 5 failed logins within 15 minutes → `AccountLockoutService` returns `423 Locked` for 15 minutes.
  - Password reset with `"Dasig123!"` → Rejected (`400`, forbidden fragment); with `"Str0ng#Pass_987!"` → `200 OK`, `users.session_version` incremented, prior JWTs rejected by `JWTService.extractClaims`.
  - Single-token logout (`POST /api/v1/auth/logout`) → SHA-256 token hash persisted in `revoked_tokens` (`V88`); other active sessions unaffected.
  - Messenger link code (`POST /api/v1/auth/messenger-link-code`) → `200 OK` for Moderator/Admin, `403 Forbidden` for Contributor.
- **Outcome:** **PASS**

### **Log for Test 6 (`Case-6` — UC-1.5 Three-Step Post Composer, Readiness & Templates)**

- **Date / Time:** `2026-10-11 09:35:15 PHT (UTC+8)`
- **Automated Test Reference:** `SubmissionServiceTest`, `SubmissionControllerTest`, Playwright `performance-regressions.spec.ts`
- **Execution Trace:**
  - 3-step wizard (`Media → Post Details → Organize & Schedule`) enforces Required step gates on forward navigation and allows free backward navigation.
  - Readiness Checklist computes `75%` Required + `25%` Recommended score (`0–100`).
  - Personal template save (`POST /api/v1/post-templates`) scopes to `ownerUserId` and returns `409` on duplicate name.
  - Deleting a `draft` or `rejected` submission cascades `validation_logs`/`review_locks` (`V103`) and logs `SUBMISSION_DELETED`.
- **Outcome:** **PASS**

### **Log for Test 7 (`Case-7` — UC-1.5 A10 Generate Template from Top Posts)**

- **Date / Time:** `2026-10-11 09:41:02 PHT (UTC+8)`
- **Automated Test Reference:** `TopPostTemplateService` / `SubmissionServiceTest`
- **Execution Trace:**
  - `< 8` engaged posts → `available: false` returned without invoking Claude.
  - `≥ 11` engaged posts → Top 6 + bottom 3 ranked by `reactions + 2×comments + 3×shares`, placeholder template + insights returned, rate-limited at `10 req/hr/user` (`429`).
- **Outcome:** **PASS**

### **Log for Test 8 (`Case-8` — UC-1.6 AI Caption Generation, Writing Check & Fancy Text)**

- **Date / Time:** `2026-10-11 09:47:29 PHT (UTC+8)`
- **Automated Test Reference:** `SubmissionServiceTest` (`validateCaptionCharLimit`), `ClaudeVisionClient` / `ProofreadService`
- **Execution Trace:**
  - `>5 MB` image downscaled in-memory; `POST /api/v1/ai/caption` returns 1 caption for selected tone within 30s timeout.
  - Prompt `>280` chars or word count `>2000` → `400 Bad Request`; `>30` calls/hr → `429 Too Many Requests`.
  - Caption `>3000` code points → Rejected (`400` on save, `422` on submit).
  - `FancyTextTool` transforms ASCII alphanumerics across 5 Unicode styles and reverts cleanly via `Plain`.
- **Outcome:** **PASS**

### **Log for Test 9 (`Case-9` — UC-1.7 Media Attachment Rules, AI Suggestions & Album Auto-Match)**

- **Date / Time:** `2026-10-11 09:54:11 PHT (UTC+8)`
- **Automated Test Reference:** `SubmissionServiceTest`, `MediaAssetServiceTest`
- **Execution Trace:**
  - Magic-byte/MIME validation accepts only `JPEG, PNG, WebP, GIF, MP4, MOV, WebM` `≤50 MB` and `≤10` assets per submission.
  - Per-asset caption (`≤500` chars) and photo-only `skipWatermark` persisted on `SubmissionMediaAsset`.
  - `POST /api/v1/ai/submissions/{id}/suggest-album` auto-applies at `≥0.55`, returns top-3 candidates at `0.32–0.54`, and leaves manual entry below `0.32`.
- **Outcome:** **PASS**

### **Log for Test 10 (`Case-10` — UC-1.8 Peak-Hour Engagement Recommendations)**

- **Date / Time:** `2026-10-11 10:01:05 PHT (UTC+8)`
- **Automated Test Reference:** `EngagementRecommendationService` / `GuardRailServiceTest`
- **Execution Trace:**
  - `<20` historical samples → Returns Tue/Wed/Thu 6:00 PM PHT fallback slots (`Best-practice guidance`).
  - `≥20` historical samples → Top 5 `(day, hour)` buckets filtered against `GuardRailService` so hard-blocked slots (`GR-H1`) are excluded from the 3 recommended tiles.
- **Outcome:** **PASS**

### **Log for Test 11 (`Case-11` — UC-1.9 Draft Submission, Withdrawal & Fast-Track Sorting)**

- **Date / Time:** `2026-10-11 10:08:33 PHT (UTC+8)`
- **Automated Test Reference:** `SubmissionServiceTest`, `SubmissionControllerTest`
- **Execution Trace:**
  - Submit with missing album/media/title → `422 Unprocessable Entity`.
  - Submit with concurrent slot conflict → `409 Conflict`, composer redirects to Step 3 (**Organize & Schedule**).
  - `POST /api/v1/submissions/{id}/withdraw` succeeds on unlocked `pending` post (`→ draft`) and returns `409` once a `ReviewLock` exists.
  - Fast-Track submission skips slot checks and sorts to top of active Validation Queue (`fastTrack DESC`).
- **Outcome:** **PASS**

### **Log for Test 12 (`Case-12` — UC-1.10 Moderator Account Management & Lateral Role Change)**

- **Date / Time:** `2026-10-11 10:14:20 PHT (UTC+8)`
- **Automated Test Reference:** `UserServiceTest` (`changeRole_moderatorToContributor_*`, `changeRole_releasesReviewLocks`)
- **Execution Trace:**
  - Lateral move `Moderator → Contributor` assigns `"CITU"`, increments `session_version`, releases active `ReviewLock` rows back to `pending`, and logs `USER_ROLE_CHANGED`.
  - Lateral move `Contributor → Moderator` clears `institution_id` to `NULL` and revokes sessions.
- **Outcome:** **PASS**

### **Log for Test 13 (`Case-13` — UC-2.1 Standalone Library Uploads, Nested Albums & Tag Floor)**

- **Date / Time:** `2026-10-11 10:21:47 PHT (UTC+8)`
- **Automated Test Reference:** `MediaAssetServiceTest` (`upload_imageAsset_staysProcessingAndTriggersClassification`, `upload_videoAsset_isReadyImmediatelyAndSkipsClassification`, `updateAlbum_withNullAlbumId_isRejected`, `removeTag_lastManualTag_ignoresAiGeneratedTagCount`)
- **Execution Trace:**
  - Image upload starts `PROCESSING` and transitions to `READY` after classification + embedding; video upload is immediately `READY`.
  - Removing last manual tag (`source="manual"`) when 8 AI tags exist → Rejected (`400 Bad Request`).
  - Re-parenting an album under its own descendant → Blocked by cycle guard (`findDescendantIds`).
- **Outcome:** **PASS**

### **Log for Test 14 (`Case-14` — UC-2.2 Semantic Search, Keyword Fallback & Asset Detail Panel)**

- **Date / Time:** `2026-10-11 10:28:12 PHT (UTC+8)`
- **Automated Test Reference:** `MediaAssetServiceTest`, `MediaAssetControllerTest`, Playwright `performance-regressions.spec.ts`
- **Execution Trace:**
  - Semantic search queries `pgvector` cosine distance and falls back to keyword search (`loadAllTagLabels` includes both manual and AI tags).
  - Inline title edit (`POST /api/v1/media-assets/{id}/title`) updates `display_title` without mutating `file_name` or R2 object key.
- **Outcome:** **PASS**

### **Log for Test 15 (`Case-15` — UC-2.3 Media History, Tiered Deletion & Duplicate Detection)**

- **Date / Time:** `2026-10-11 10:34:55 PHT (UTC+8)`
- **Automated Test Reference:** `MediaAssetServiceTest` (`deleteAsset_blockedWhenPendingOrScheduled`, `deleteAsset_requiresForceWhenInDraft`, `detectDuplicateHash_*`)
- **Execution Trace:**
  - Deleting asset attached to `pending`/`scheduled` submission → `409 Conflict`.
  - Deleting asset attached to `draft`/`needs_revision` → Requires `force=true`, soft-deletes with 30-day retention.
  - Uploading identical SHA-256 hash within same institution → Flags duplicate and offers `Upload Anyway` / `Cancel` / `Use Existing`.
- **Outcome:** **PASS**

### **Log for Test 16 (`Case-16` — UC-2.4 Approval Workflow, 15-Min Lock & Self-Review Block)**

- **Date / Time:** `2026-10-11 10:42:10 PHT (UTC+8)`
- **Automated Test Reference:** `ValidationServiceTest`, `ValidationControllerTest`
- **Execution Trace:**
  - Opening submission acquires 15-min `ReviewLock` (`in_review`); second reviewer receives read-only lock metadata.
  - Reviewer attempting to lock/approve own submission → Rejected (`403/409 Self-submission review blocked`).
  - `Request Revision` enforces `10–1,000` char remarks and releases slot; `Reject` enforces the 7 valid `REJECTION_REASON_LABELS` (`INAPPROPRIATE_CONTENT`, `OUT_OF_SCOPE`, `DUPLICATE_EVENT`, `NO_LONGER_RELEVANT`, `RIGHTS_OR_PRIVACY`, `WRONG_INSTITUTION`, `OTHER`).
- **Outcome:** **PASS**

### **Log for Test 17 (`Case-17` — UC-2.4 A9–A15 Edit-During-Review Diffs & Late Publish Override)**

- **Date / Time:** `2026-10-11 10:49:38 PHT (UTC+8)`
- **Automated Test Reference:** `ValidationServiceTest` (`logsSinceLastTerminalAction_*`, `restoreOriginal_*`, `approve_whenSlotPassed_requiresOverrideOrReschedule`)
- **Execution Trace:**
  - `ValidationService.logsSinceLock()` tracks edits across the entire review cycle even if the lock is released and re-acquired before approval.
  - Approving a scheduled post whose slot passed during review returns `409` unless rescheduled or approved by Admin with `?publishNow=true` (logging `LATE_PUBLISH_OVERRIDE`).
- **Outcome:** **PASS**

### **Log for Test 18 (`Case-18` — UC-2.5 Automated Brand Watermarking & Derivative Purge)**

- **Date / Time:** `2026-10-11 10:56:04 PHT (UTC+8)`
- **Automated Test Reference:** `WatermarkApplicationService` / `GeneratedWatermarkPurgeJob`
- **Execution Trace:**
  - Watermark config read live at publish time; photo with `skipWatermark=true` and video assets bypass rendering.
  - Render failure publishes original unwatermarked photo, logs `PUBLISH_WATERMARK_FAILED`, and notifies all Administrators.
  - `GeneratedWatermarkPurgeJob` sweeps `generated/watermarked/` objects older than 3 days.
- **Outcome:** **PASS**

### **Log for Test 19 (`Case-19` — UC-3.1 Master Calendar, Moderator Caps & GiST Conflict Constraint)**

- **Date / Time:** `2026-10-11 11:03:22 PHT (UTC+8)`
- **Automated Test Reference:** `CalendarServiceTest`, `GuardRailServiceTest`, migrations `V95` & `V96`
- **Execution Trace:**
  - Contributor calendar masks other HEIs' posts while showing own HEI, `"DASIG Central Visayas"`, and own in-flight posts in full detail.
  - Moderator reschedule #3 or move `> ±1 day` from `originalScheduledAt` → Rejected (`403/422`).
  - Concurrent reschedule race → `claimModeratorReschedule` / `claimAdminReschedule` rejects loser with `409 Conflict`.
  - PostgreSQL GiST exclusion constraint (`V95`) blocks overlapping `±30-min` reservations unless `admin_override = true` (`V96`).
- **Outcome:** **PASS**

### **Log for Test 20 (`Case-20` — UC-3.2 Automated Facebook Publishing & Atomic Claim)**

- **Date / Time:** `2026-10-11 11:11:19 PHT (UTC+8)`
- **Automated Test Reference:** `FacebookPublisherService` / `PublishingSchedulerJob`
- **Execution Trace:**
  - `claimForPublishing` atomically transitions `scheduled → publishing` and bumps `updatedAt` outside any Graph API transaction.
  - Image-only post stages unpublished photos and publishes 1 feed post; video-only post uses `/videos`; mixed-media post transitions immediately to `PUBLISH_FAILED`.
  - Failed photo staging invokes `cleanupStagedPhotos` and records any undeleted IDs in `PublicationAttempt.photoIdsCleanupFailed` (`V97`).
- **Outcome:** **PASS**

### **Log for Test 21 (`Case-21` — UC-3.2 A2 Stale Sweep & Manual Publishing Fallback)**

- **Date / Time:** `2026-10-11 11:18:45 PHT (UTC+8)`
- **Automated Test Reference:** `StaleSubmissionDetectorJob`, `ManualPublishingService`
- **Execution Trace:**
  - Missed `scheduled` post → `PUBLISH_FAILED`; missed unreviewed `pending` post → `MISSED_REVIEW` with slot released; stuck Fast-Track `publishing` post (`scheduledAt IS NULL`, `updatedAt > 5m`) → `PUBLISH_FAILED`.
  - 3-step Manual Publishing Fallback transitions `PUBLISH_FAILED → PUBLISHED_MANUAL`, releases slot, logs `BR-PUB-04` fields, and resets `moderatorRescheduleCount` / `originalScheduledAt` when retried onto a new schedule.
- **Outcome:** **PASS**

### **Log for Test 22 (`Case-22` — UC-3.3 Real-Time SSE Notifications & Cross-Session Sync)**

- **Date / Time:** `2026-10-11 11:25:10 PHT (UTC+8)`
- **Automated Test Reference:** `NotificationServiceTest`, Playwright `performance-regressions.spec.ts` (`SSE INC and READ_SYNC events`)
- **Execution Trace:**
  - Review triggers (`T-01`, `T-11`) fan out to both active Moderators and active Administrators (`NotificationEventListener.reviewers()`).
  - SSE `notification`, `read`, and `read-all` events update `useNotifications` and `useNotificationUnreadCount` across multiple open tabs in real time without polling storms.
- **Outcome:** **PASS**

### **Log for Test 23 (`Case-23` — UC-3.4 Social Engagement Analytics & CSV Sanitization)**

- **Date / Time:** `2026-10-11 11:32:00 PHT (UTC+8)`
- **Automated Test Reference:** `MetricsAggregatorService`, Playwright `performance-regressions.spec.ts`
- **Execution Trace:**
  - Contributor sees institution-wide metrics; Moderator sees network-wide metrics (`403` on `operational-health` and `ai-performance`); Admin sees all reports including `meetsOnTimePublicationTarget` (`≥95.0%`).
  - `MetricsAggregatorService.escapeCsv()` prepends `'` to cells starting with `=`, `+`, `-`, `@`, `\t`, `\r` (`CWE-1236` mitigated).
- **Outcome:** **PASS (Minor UI discrepancy on non-admin placeholder card logged as `INC-04`)**

### **Log for Test 24 (`Case-24` — UC-3.5 System Health Analytics & 16 Tracked Jobs)**

- **Date / Time:** `2026-10-11 11:39:14 PHT (UTC+8)`
- **Automated Test Reference:** `SystemHealthServiceTest`, Playwright `performance-regressions.spec.ts`
- **Execution Trace:**
  - Admin-only `@PreAuthorize("hasRole('ADMIN')")` enforced (`403` for Moderator/Contributor).
  - Evaluates DB & R2 storage against `80%`/`95%` thresholds, 6 external service probes, 16 scheduled jobs, and 14 operational/AI metrics (including `missedReviewRate`).
  - Manual job re-run executes `PublishingSchedulerJob` and `TokenPublishingEscalationJob` asynchronously via `CompletableFuture.runAsync` and the remaining 14 jobs synchronously, logging `BACKGROUND_JOB_RUN`.
- **Outcome:** **PASS (Defensive CSV escaping note logged as `INC-05`)**

### **Log for Test 25 (`Case-25` — UC-3.6 Immutable Audit Log & Multi-Institution Tenant Isolation)**

- **Date / Time:** `2026-10-11 11:46:30 PHT (UTC+8)`
- **Automated Test Reference:** `AuditLogServiceTest`, `TenantScopeServiceTest`, `SubmissionServiceTest`, Playwright `performance-regressions.spec.ts`
- **Execution Trace:**
  - Cross-institution access by Contributor to another HEI's submission/media returns `404 Not Found`; forged `institutionId` in request body is overridden by JWT tenant scope.
  - Audit Log filters across 11 categories and 8 entity types, renders structured before/after diffs, caps CSV export at `5,000` sanitized rows, and enforces DB-level append-only immutability via migration `V78` RLS policy.
- **Outcome:** **PASS**

---

## **A.2 Test Results** {#a2-test-results}

### **Summary of Test Execution Metrics**

| Metric                                                 | Count / Value                                                         | Percentage   |
| :----------------------------------------------------- | :-------------------------------------------------------------------- | :----------- |
| **Total Test Cases Executed (`Case-1` – `Case-25`)**   | 25                                                                    | 100.0%       |
| **Passed Completely**                                  | 25                                                                    | 100.0%       |
| **Partially Passed (With Known Open Sub-Flow Defect)** | 0                                                                     | 0.0%         |
| **Failed (Critical / Blocking Workflow Failure)**      | 0                                                                     | 0.0%         |
| **Automated Backend Test Suite (`./mvnw test`)**       | 17 Test Classes (`273+` JUnit 5 / MockMvc tests)                      | 100% Passing |
| **Automated Frontend E2E Suite (`npm run test:e2e`)**  | 11 Playwright E2E & Performance Regression Specs                      | 100% Passing |

### **Test Case Traceability & Results Matrix**

| Test Case ID | SRS Use Case / Rule          | Feature / Scenario Tested                                                       | Execution Mode                                          | Status           | Remarks / Incident Ref                                                                              |
| :----------- | :--------------------------- | :------------------------------------------------------------------------------ | :------------------------------------------------------ | :--------------- | :-------------------------------------------------------------------------------------------------- |
| **Case-1**   | `UC-1.1`, `BR-ACC-04`        | Admin Invitation, 3-Admin Cap, Owner-Gated Lifecycle                            | JUnit (`UserServiceTest`) + Manual                      | **PASS**         | Uniform 3-Admin cap verified across all 4 gates.                                                    |
| **Case-2**   | `UC-1.1 A7, A8`              | Admin Promotion Propose/Confirm & Admin Owner Transfer                          | JUnit (`UserServiceTest`) + API                         | **PASS**         | Promotion and Admin-to-Admin Owner transfer succeed without demoting outgoing Owner (INC-01 resolved). |
| **Case-3**   | `UC-1.2`, `BR-ACC-01`        | Institution CRUD, Reassignment & Protected Default HEI                          | JUnit + API                                             | **PASS**         | `"DASIG Central Visayas"` protected from deactivation/deletion.                                     |
| **Case-4**   | `UC-1.3`, `BR-ACC-01..04`    | Contributor Invitations, Moderator Scope & Footprint Deletion                   | JUnit (`UserServiceTest`)                               | **PASS**         | Footprint accounts preserved as anonymized rows (`USER_REMOVED`).                                   |
| **Case-5**   | `UC-1.4`, `BR-ACC-02..07`    | JWT Auth, 5-Try Lockout, Password Policy & DB Revocation                        | JUnit (`AuthServiceTest`, `JWTServiceTest`)             | **PASS**         | DB-backed `revoked_tokens` and `session_version` verified (`INC-03` notes unwired `/auth/refresh`). |
| **Case-6**   | `UC-1.5`, `BR-SUB-03`        | 3-Step Composer, Readiness (0–100), Templates & Guard-Rail Switch               | JUnit (`SubmissionServiceTest`) + Playwright            | **PASS**         | Cascading delete (`V103`) and `guardrails_enforced` switch verified.                                |
| **Case-7**   | `UC-1.5 A10`                 | AI Template Synthesis from Top-Performing Facebook Posts                        | JUnit + API                                             | **PASS**         | Refuses `<8` engaged posts; enforces `10 req/hr` rate limit.                                        |
| **Case-8**   | `UC-1.6`, `BR-AI-01..03, 07` | AI Caption Generation, Proofreading (`Check writing`) & Fancy Text              | JUnit + UI                                              | **PASS**         | `3,000`-char ceiling, `>5 MB` downscale, and `30/hr` limiter verified.                              |
| **Case-9**   | `UC-1.7`, `BR-SUB-01..02`    | Media Attachment Rules, AI-Suggested Media & Album Auto-Match                   | JUnit (`SubmissionServiceTest`) + UI                    | **PASS**         | Tiered Auto-Match (`≥0.55` / `0.32–0.54` / `<0.32`) verified.                                       |
| **Case-10**  | `UC-1.8`                     | Peak-Hour Engagement Recommendations & Guard-Rail Pre-Filter                    | JUnit + UI                                              | **PASS**         | Excludes hard-blocked slots; falls back to Tue/Wed/Thu 6 PM below 20 samples.                       |
| **Case-11**  | `UC-1.9`, `GR-H1..H3`        | Draft Submission, `409` Conflict Redirect, Withdraw & Fast-Track Sort           | JUnit (`SubmissionControllerTest`) + UI                 | **PASS**         | Concurrent slot conflict returns composer to Step 3; Fast-Track sorts first.                        |
| **Case-12**  | `UC-1.10`                    | Moderator Account Management & Lateral Role Changes                             | JUnit (`UserServiceTest`)                               | **PASS**         | Lateral move releases active `ReviewLock` rows and revokes sessions.                                |
| **Case-13**  | `UC-2.1`                     | Standalone Library Uploads, Nested Albums & Manual Tag Floor                    | JUnit (`MediaAssetServiceTest`)                         | **PASS**         | Cycle guard, `PROCESSING → READY` fix (`INC-06`), and manual tag floor verified.                    |
| **Case-14**  | `UC-2.2`, `BR-MED-01`        | Semantic Media Search, Keyword Fallback & Editable Display Title                | JUnit (`MediaAssetControllerTest`) + Playwright         | **PASS**         | Manual & AI tags searchable; `display_title` rename preserves R2 key.                               |
| **Case-15**  | `UC-2.3`, `BR-MED-02`        | Media `Used In` History, 3-Tier Deletion & SHA-256 Duplicate Check              | JUnit (`MediaAssetServiceTest`)                         | **PASS**         | Active references blocked (`409`); draft references require `force=true`.                           |
| **Case-16**  | `UC-2.4`, `BR-APP-01..03`    | Approval Workflow, 15-Minute Review Lock & Self-Review Block                    | JUnit (`ValidationServiceTest`) + Playwright            | **PASS**         | Self-review unconditionally blocked; 7 rejection codes enforced.                                    |
| **Case-17**  | `UC-2.4 A9–A15`              | Edit-During-Review Cycle Diffs, Restore Original & Late Publish Override        | JUnit (`ValidationServiceTest`)                         | **PASS**         | Diffs persist across lock release/re-acquire; `LATE_PUBLISH_OVERRIDE` audited.                      |
| **Case-18**  | `UC-2.5`                     | Automated Brand Watermarking, Opt-Out & 3-Day Derivative Purge                  | JUnit + Integration                                     | **PASS**         | Render failure publishes original image, logs `PUBLISH_WATERMARK_FAILED`, alerts Admins.            |
| **Case-19**  | `UC-3.1`, `GR-H1`            | Master Calendar Scoping, Moderator Caps & GiST Conflict Constraint              | JUnit (`CalendarServiceTest`) + DB (`V95`/`V96`)        | **PASS**         | Moderator `≤2` reschedules & `±1 day` cap + GiST constraint verified.                               |
| **Case-20**  | `UC-3.2`, `GR-T5`            | Automated Facebook Publishing, Atomic Claim & Orphan Photo Cleanup              | JUnit + Integration                                     | **PASS**         | Atomic `scheduled → publishing` claim prevents double-publishing; `photoIdsCleanupFailed` surfaced. |
| **Case-21**  | `UC-3.2 A2`, `UC-2.4 A8`     | Stale Submission Sweep (`PUBLISH_FAILED` vs. `MISSED_REVIEW`) & Manual Fallback | JUnit + UI                                              | **PASS**         | Separate outcomes verified; transaction proxy note tracked in `INC-02`.                             |
| **Case-22**  | `UC-3.3`, `BR-NOT-01..04`    | Real-Time SSE Notifications, Cross-Tab Sync & Messenger Opt-In                  | JUnit (`NotificationServiceTest`) + Playwright          | **PASS**         | Reviewer fan-out (`Moderator + Admin`) and SSE `read` sync verified.                                |
| **Case-23**  | `UC-3.4`, `BR-ANA-01..06`    | Role-Scoped Social Engagement Analytics & CSV Sanitization                      | JUnit + Playwright                                      | **PASS**         | `CWE-1236` formula injection mitigated; placeholder panel noted in `INC-04`.                        |
| **Case-24**  | `UC-3.5`                     | System Health Analytics, 16 Tracked Jobs & Manual Async/Sync Re-Run             | JUnit (`SystemHealthServiceTest`) + Playwright          | **PASS**         | 16 jobs, 6 services, 14 metrics, and async job re-run verified (`INC-05`).                          |
| **Case-25**  | `UC-3.6`, `SRS 3.5`          | Immutable Audit Log (`V78` RLS), Structured Diffs & Tenant Isolation            | JUnit (`AuditLogServiceTest`, `TenantScopeServiceTest`) | **PASS**         | 4-layer tenant isolation returns `404`; `audit_logs` blocks `UPDATE`/`DELETE`.                      |

---

## **A.3 Incident Report** {#a3-incident-report}

This section documents software incidents, implementation discrepancies, and technical debt findings identified during verification of `docs/md/srs/SRS.md` and `docs/md/srs/UC-*.md` against the DASIGConnect codebase.

### **Open Incidents & Known Discrepancies**

*(None — all documented incidents verified and resolved as of 2026-10-17.)*

---

### **Resolved Incidents Verified During Regression Testing**

| Incident ID | Related Test / UC                         | Severity   | Defect Summary                                                                                                                                                                                                                                                                                                                                                                                       | Resolution Verified                                                                                                                                                                                                                                                                                                      |
| :---------- | :---------------------------------------- | :--------- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **INC-01**  | `Case-2` (`UC-1.1 A8`)                    | **Medium** | **Admin Owner Transfer Confirmation Blocked When Target Is an Existing Administrator:** Initiating an Admin Owner transfer (`POST /api/v1/users/{id}/initiate-owner-transfer`) to an active Moderator works end-to-end, but when the target is an already-active Administrator, the confirmation endpoint (`confirmAdminTransfer`) rejects the request due to a role-gating inconsistency on the confirmation route. | **Resolved & Verified:** Scope revised to Admin-to-Admin transfer only per PR #395 and #396. Fixed the role guard on `confirmAdminTransfer` to accept active Administrators matching `transferTargetUserId`, resolved the confirmation `409` Conflict, added audit notifications, and verified end-to-end in Test 2 (`Case-2` PASS). Outgoing owner now retains Administrator privileges without demotion. |
| **INC-02**  | `Case-21` (`UC-3.2`)                      | **Medium** | **Transaction Proxy Boundary in Background Escalation Jobs:** As documented in `UC-3.2`, `StaleSubmissionDetectorJob` and `TokenPublishingEscalationJob` invoke internal batch helper methods without an external Spring `@Transactional` proxy boundary, creating a risk of partial commits if an exception occurs mid-batch.                                                                 | **Resolved & Verified (PR #387):** Extracted `PublishingStateService` to encapsulate all transactional state mutations (`findAndMarkMissedReview`, `findAndMarkStaleScheduled`, `findAndMarkStalePublishing`, and `suspendDueToInvalidToken`). `StaleSubmissionDetectorJob` and `TokenPublishingEscalationJob` now invoke these methods via a genuine Spring `@Transactional` proxy boundary, preventing partial batch commits. Verified via unit and integration tests (`Case-21` PASS). |
| **INC-03**  | `Case-5` (`UC-1.4 A7`)                    | **Low**    | **Silent JWT Refresh Endpoint (`/api/v1/auth/refresh`) Unwired in Frontend:** SRS Section 1.3 & 3.4.2 describe a silent token refresh when clicking the Session Warning Banner, whereas `UC-1.4 A7` documents that clicking **"Stay Logged In"** opens the Session Expired password modal (`POST /api/v1/auth/login`) and does not call `POST /api/v1/auth/refresh`.                                      | **Closed (By Design / Aligned with SRS):** Intentionally unwired in favor of a stricter security posture requiring password re-entry. When the session warning banner triggers, clicking **"Stay Logged In"** routes to the Session Modal requiring explicit password re-authentication (`POST /api/v1/auth/login`) rather than silent token extension. Aligned in SRS Section 3.4.2 and `UC-1.4 A7` specification table; verified in test execution (`Case-5` PASS). |
| **INC-04**  | `Case-23` (`UC-3.4`)                      | **Low**    | **Static Placeholder Values in Non-Admin "Content Quality & Compliance" Analytics Panel:** As documented in `UC-3.4`, the `"Content Quality & Compliance"` card rendered for Contributors and Moderators displays static strings (`"Watermark Valid 100%"`, `"Publish Readiness: Optimal"`) instead of live computed backend values.                                                                   | **Resolved & Verified (2026-10-10):** Replaced hardcoded strings in `OperationsAndEngagementCard` with live computed telemetry. `"Watermark Valid"` was replaced by live First-Time Approval rate (`contributorAnalytics`), and `"Publish Readiness"` is now dynamically evaluated (`"Optimal"` vs. `"Needs Review"`) from backend `contentIssues` counts alongside live `contentCompleteness`. |
| **INC-05**  | `Case-24` (`UC-3.5`)                      | **Low**    | **Un-hardened CSV Escape Helper in `SystemHealthService.exportSnapshotCsv()`:** Unlike `MetricsAggregatorService.escapeCsv()` (`UC-3.4`) and `AuditLogService.escapeCsv()` (`UC-3.6`), which prepend `'` to neutralize leading `=+-@` formula triggers (`CWE-1236`), `SystemHealthService.exportSnapshotCsv()` previously used a basic quote-escape helper.                                        | **Resolved & Verified (2026-10-17):** Hardened `SystemHealthService.escape()` by prepending `'` to neutralize leading formula trigger characters (`=+-@\t\r`, `CWE-1236`), aligning with `MetricsAggregatorService` and `AuditLogService`. Verified with unit test `exportSnapshotCsv_neutralizesCsvFormulaInjection` in `SystemHealthServiceTest` (`Case-24` PASS).                             |
| **INC-06**  | `Case-13` (`UC-2.1`)                      | **High**   | **Uploaded Library Images Stuck in `PROCESSING` Forever & Dueling Auto-Match Overwrite:** `AIClassificationService.classifyAndEmbed` previously failed to transition successfully classified/embedded images from `PROCESSING` to `READY`, and backend fallback `autoMatchAlbum` overwrote the frontend's tiered album match (`UC-2.1-library-uploads-albums.md`, lines 52, 55). | **Resolved & Verified (2026-09-13 / 2026-10-09):** `classifyAndEmbed` now explicitly sets `MediaAssetStatus.READY` on completion, videos are marked `READY` immediately on upload, and `UploadModal.tsx` passes `autoMatchAlbum: false` once a frontend match is resolved.                                               |
| **INC-07**  | `Case-17` (`UC-2.4 A10`)                  | **High**   | **Lost Edit-During-Review Diff After Review Lock Release/Re-acquisition:** `ValidationService.logsSinceLock()` previously scoped edit diffs only since the most recent `lock_acquired` event, causing edits made prior to a lock release/timeout to be omitted (`edited = false`) when later approved.                                                                           | **Resolved & Verified (2026-09-17):** Scoped edit-diff aggregation to the entire review cycle (since the last terminal decision or start of history), surviving lock releases and re-acquisitions.                                                                                                                       |
| **INC-08**  | `Case-19` (`UC-3.1 A3, A4`)               | **High**   | **Concurrent Reschedule Race & Missing Database-Level `±30-Minute` Slot Exclusion:** Moderator reschedule caps could be bypassed by parallel requests, and `GR-H1` (`±30-minute` spacing) had no database-level constraint while published submissions never released their `locked` `SlotReservation` rows.                                                                     | **Resolved & Verified (`V95` & `V96` Migrations):** Added atomic conditional `UPDATE` queries (`claimModeratorReschedule`/`claimAdminReschedule`), released slot reservations upon publish completion, and added a PostgreSQL GiST exclusion constraint (`V95`) with an explicit `NOT admin_override` exemption (`V96`). |
| **INC-09**  | `Case-22` (`UC-3.3`)                      | **Medium** | **Review-Related Notifications (`T-01`, `T-11`) Omitted Administrators:** `NotificationEventListener.onSubmissionPending` and `onFastTrackSubmission` initially dispatched notifications only to Moderators, omitting active Administrators (`UC-3.3-automated-reminders-alerts.md`, line 39).                                                                                   | **Resolved & Verified:** Introduced `reviewers()` helper in `NotificationEventListener` to fan out review queue notifications to all active Moderators and Administrators, and added SSE `read`/`read-all` cross-session sync.                                                                                           |
| **INC-10**  | `Case-23`, `Case-25` (`UC-3.4`, `UC-3.6`) | **High**   | **CSV Formula Injection (`CWE-1236`) in Analytics and Audit Log Exports:** Free-text user fields (`event_title`, `contributor_name`, actor queries) starting with `=`, `+`, `-`, or `@` were quoted but not formula-neutralized in CSV exports.                                                                                                                                  | **Resolved & Verified:** Updated `MetricsAggregatorService.escapeCsv()` and `AuditLogService.escapeCsv()` to prepend a leading apostrophe (`'`) before quoting any cell starting with `=`, `+`, `-`, `@`, `\t`, or `\r`.                                                                                                 |

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**DASIGConnect** is a capstone project (Team `2526-sem2-it332-38`, CIT-U) - a web-based Social Media Content Workflow and Scheduling Management System for the **DOST Academe-Science and Innovation Group (DASIG)** under DOST Region 7.

It replaces ad hoc coordination between DASIG member HEIs with a structured workflow: contributors submit event content, validators review it, administrators approve/schedule it, and the system later publishes to the DASIG Facebook Page.

See `README.md`, `TASKS.md`, and `docs/` for project context, SRS, SDD, and task status.

---

## Commands

### Backend (`/backend`)

```bash
# Run locally
./mvnw spring-boot:run

# Build JAR without tests
./mvnw clean package -DskipTests

# Run all tests
./mvnw test

# Run one test class
./mvnw test -Dtest=SubmissionServiceTest
```

Windows note: `.\mvnw.cmd` currently fails in this PowerShell environment. Direct Maven from `.m2/wrapper/dists` was used successfully for the latest verification.

### Frontend (`/frontend`)

```bash
npm install
npm run dev
npm run build
npm run lint
npm run preview
```

Vite dev server default: `http://localhost:5173`.

For local frontend-to-backend calls, create `frontend/.env.local`:

```env
VITE_API_URL=http://localhost:8080/api/v1
VITE_SUPABASE_URL=https://<project-ref>.supabase.co
VITE_SUPABASE_STORAGE_BUCKET=dasigconnect-media
VITE_SUPABASE_ANON_KEY=<supabase-anon-key>
```

`frontend/.env.local` is intentionally ignored and should not be committed.

---

## Architecture

DASIGConnect is a three-tier client-server system:

```text
React SPA (Vercel) -> Spring Boot REST API (Render) -> Supabase PostgreSQL + pgvector
                                                     -> Supabase Storage
                                                     -> Facebook Graph API
                                                     -> Anthropic Claude
                                                     -> Voyage AI
```

### Backend - Spring Boot 4 / Java 21

- Entry point: `backend/src/main/java/com/dasigconnect/backend/BackendApplication.java`.
- Config: `backend/src/main/resources/application.properties`.
- Local env loading: `spring-dotenv` reads `backend/.env`.
- REST base path: `/api/v1`.
- Auth: stateless JWT through Spring Security and `JwtAuthenticationFilter`.
- Tenancy: institution scoping through `TenantScopeService` plus PostgreSQL RLS.
- DB migrations: Flyway in `backend/src/main/resources/db/migration/`.
- DB access: Spring Data JPA + Hibernate through HikariCP.
- Critical pool limit: keep Hikari max pool size at 5 for Supabase Session Pooler.

### Frontend - React 19 / TypeScript / Vite

- Entry: `frontend/src/main.tsx`.
- App shell: `frontend/src/app/App.tsx`.
- API clients: `frontend/src/api/`.
- UI: shadcn/ui, Radix UI, Tailwind CSS v4.
- Routing: React Router v7.
- HTTP: Axios with Authorization header managed by `setAuthToken`.
- Session expiry: `App.tsx` parses JWT `exp`, shows countdown banner near expiry, and opens the session modal at timeout.
- Media upload: browser uploads binary directly to Supabase Storage, then the app sends media metadata to the backend.

---

## Current Local Status - 2026-05-26

Current branch: `module3`

### Completed

- UC-3.1 publishing pipeline and master calendar backend is fully implemented (19 new files, 3 modified).
- Flyway V12 + V13 migrations add `publication_attempts` and `facebook_page_tokens` tables.
- `TokenEncryptionService` encrypts Facebook Page Access Token with AES-256-GCM before DB storage.
- `FacebookPublisherService` integrates with Facebook Graph API v25.0 via Java HttpClient: 2-step photo publish, single-call video publish, 3-attempt retry with 5s/25s/125s backoff.
- `PublishingSchedulerJob` triggers every minute (GR-T5). Transaction is committed before Graph API calls to respect HikariCP 5-connection limit.
- `StaleSubmissionDetectorJob` handles missed publish windows every 5 minutes (GR-T9).
- `TokenHealthCheckJob` validates the page token daily at 08:00 UTC (GR-T4).
- `CalendarService` + `CalendarController` expose `GET /api/v1/calendar` with role-scoped masking.
- Admin reschedule via `PATCH /api/v1/submissions/{id}/reschedule` supports guard rail overrides with audit trail.
- UC-3.4 Resolution Center: `ManualPublishingService` + `ResolutionController` (`/api/v1/resolution`).
- `AbandonmentDetectorJob` clears manual publish sessions open longer than 2 hours.
- UC-3.1 frontend Calendar route `/scheduler/calendar` is wired to `GET /api/v1/calendar` using FullCalendar, reusable calendar components, status legend, event detail modal, loading/empty/error states, and refresh/retry controls.
- UC-3.4 frontend Resolution Center route `/admin/resolution` is wired to `GET /api/v1/resolution/failures` plus retry and manual-publish start/complete/cancel actions using reusable cards, status/action components, modals, busy states, toasts, and refresh-after-action behavior.
- UC-3.1 frontend API separation is in place: `frontend/src/api/calendarApi.ts` owns calendar calls and `frontend/src/api/resolutionApi.ts` owns Resolution Center calls.
- Submit Content Facebook Preview is now dynamic and componentized. It reads current form/draft caption, schedule, saved assets, and local uploaded files instead of hardcoded preview data.
- Facebook Preview card opens a responsive preview/review modal with a larger Facebook-style preview, submission details, validation/missing-field messaging, and action buttons wired to existing Save Draft / Submit for Review / Edit Details handlers.
- Facebook Preview media now supports carousel review with arrow controls, dots/counter, and swipe gestures in both compact and modal contexts.
- Preview modal includes a draggable media-order strip with keyboard-friendly move buttons. Reordering updates the real submission order: local files upload in the chosen order and saved draft assets persist through `PATCH /api/v1/submissions/{id}/media/order`.
- Backend submission media reorder support added: `SubmissionMediaOrderDto`, `SubmissionController.reorderMedia`, and `SubmissionService.reorderMedia()` update `submission_media_assets.display_order` for editable submissions.
- UC-1.3 submission backend is implemented and tested (on `feature/uc13-submission-backend`, pending merge).
- Frontend API wiring matches backend contracts for submissions, guard rail evaluation, media upload metadata, institutions, and lookups.
- Reset password page implemented. Session expiry countdown wired from JWT `exp`.
- Dashboard stats use live endpoints. Invite/manage modal lists pending invitations and supports resend.
- App auth state hydrates from `GET /api/v1/me` after login, invite accept, session relogin, and saved-token restore.
- **Save draft / submit-for-review browser flow verified end-to-end** — 500 bug fixed (see Flyway and notification fixes below).
- **Live Facebook publish verified** — photo post published to the DASIG Facebook Page from a scheduled submission.

### Flyway Migration History Fix (2026-05-26)

The Supabase DB had a mixed Flyway state: V10 was recorded with checksum 0 (manually baselined), and V6–V9 were local files that were never applied while V10 was already in `flyway_schema_history`. This caused `FlywayValidateException` on startup.

Fix applied:
- V6–V9 migration files removed from the codebase (they were never applied to the DB).
- Their content recreated as V14–V17 (linear versions after V13), so Flyway applies them in clean order.
- V17 (`asset_tags`) was rewritten to `DROP TABLE IF EXISTS asset_tags CASCADE` before recreating — V4 had created `asset_tags` with columns `(asset_id, tag, source)` which do not match the `AssetTag` entity mapping `(media_asset_id, label)`.
- `BackendApplication.flyway()` bean now calls `flyway.repair()` before `flyway.migrate()` to fix stored checksums; no `outOfOrder` or `validateOnMigrate=false` needed.
- Current migration sequence: V1–V5 (applied), V10 (applied), V11–V17 (applied on 2026-05-26 startup).

### Submit 500 Fix (2026-05-26)

`SubmissionService.submit()` called `notificationService.createNotification()` directly inside the main `@Transactional` method. When the `notifications` table didn't exist (missing Flyway migration), the INSERT failed and rolled back the entire transaction — including the PENDING status change — returning 500 to the frontend. Fix: wrapped the T1 notification block in try-catch so the submission always reaches PENDING even if the notification insert fails. The notifications table is now created by V14 and this guard is no longer needed for normal operation, but is retained as a defensive measure.

### GlobalExceptionHandler Fix (2026-05-26)

`SlotAlreadyTakenException` was not handled by `GlobalExceptionHandler` and fell through to the catch-all, returning 500 instead of 409 Conflict. Added explicit handler returning 409 with the exception message.

### Media Library ↔ Submission Integration + Upload/Validation Fixes (2026-05-26)

- **Media Library multi-select → New Post.** `AssetCard` has a selection checkbox (separate from the body click that opens the detail panel). A floating action bar shows the count with Clear / New Post. New Post navigates to `/submissions/new?assetIds=a,b,c`.
- **Selection persists across navigation** via a reusable hook `frontend/src/hooks/usePersistentSelection.ts` (sessionStorage-backed `Set<string>`, keyed by `dasigconnect:media-selection`). Reusable system-wide.
- **Submission consumes `?assetIds=`.** `SubmissionScreen` reads the param on mount, fetches each asset's detail, pre-fills `savedAssets`/`mediaOrder`, and attaches them on Save/Submit via new `attachAsset()` (`POST /submissions/{id}/assets`), tolerating 409. The param is stripped after the first save to prevent double-attach.
- **Asset detail panel** shows a Selection review list (view/deselect each, chevron navigation). Footer "Use in New Post" was renamed to **New Post (N)** and rewired to the selection navigation; the redundant selection-block button was removed.
- **Add to Draft** is now functional via `AddToDraftModal` — lists the contributor's existing drafts and appends selected assets through `attachAsset` (contributor-only; falls back to New Post if no drafts).
- **Download Original** upgraded to a true blob download (falls back to opening in a new tab if CORS blocks the fetch).
- **Media upload fix.** Library upload now uses `XMLHttpRequest` for real `upload.onprogress` (was a fake 10%→100% bar with `fetch`), enforces a 25 MB client-side guard, and surfaces the actual Supabase status on failure. Note: mp4 upload failures are typically the Supabase bucket's allowed MIME types / file-size limit, now visible in the error toast.
- **Submission content-completeness validation (the key fix).** `SubmissionService.submit()` previously only checked status and (when `app.guardrails.enforced=true`) the schedule, so incomplete submissions reached PENDING. Added `assertContentComplete()` — enforced on **every** submit regardless of the guard-rail flag — rejecting with **422** when event title, event date, caption, or ≥1 media asset is missing. `SubmissionScreen.handleSubmit` blocks the same set client-side with a toast. Schedule remains gated by `app.guardrails.enforced` (off by default).
- **GlobalExceptionHandler** gained handlers for `HttpRequestMethodNotSupportedException` (405 with method/path/supportedMethods) and `IllegalStateException` (502 with the upstream/storage message) so storage failures stop masquerading as generic 500s.

### Verification

- Backend: **208 tests passing** (0 failures, 0 errors) — all 163 prior tests pass plus UC-3.1 additions.
- Frontend: `npm.cmd run build` passing.
- UC-3.1 frontend: `npm.cmd run build` passed after Calendar and Resolution Center implementation.
- UC-3.1 frontend: targeted ESLint passed for `src/features/calendar`, `src/features/resolution`, `src/hooks/useCalendarEvents.ts`, `src/hooks/useResolutionFailures.ts`, `src/api/calendarApi.ts`, and `src/api/resolutionApi.ts`.
- Submit Content / Facebook Preview: `npm.cmd run build` passed after dynamic preview, preview modal, carousel, and media reorder implementation.
- Submit Content / Facebook Preview: targeted ESLint passed for `src/features/submission/SubmissionScreen.tsx`, `src/components/facebook`, `src/hooks/useFacebookPreviewData.ts`, `src/hooks/useMediaReorder.ts`, and `src/types/facebook.ts`.
- Submission media reorder backend: `.\mvnw.cmd "-Dtest=SubmissionServiceTest,SubmissionControllerTest" test` passed with 29 focused tests after adding the reorder service test.
- Full-project `npm.cmd run lint` still fails due pre-existing lint debt in older files outside the UC-3.1 frontend slice.
- Vite dev server started locally on `http://127.0.0.1:5176/` because ports 5173-5175 were occupied.
- Migration sanity: `mvn clean` and `mvn spring-boot:run` applied V11–V17 successfully on existing Supabase DB.
- Submit-for-review flow: confirmed working end-to-end in the browser.
- Facebook publish: confirmed — photo post published to DasigConnect Facebook Page.
- Submission validation (2026-05-26): `SubmissionServiceTest` 17/17 and `SubmissionControllerTest` 14/14 pass after adding the content-completeness gate and two new tests (`submit_withoutMedia_returns422`, `submit_withoutCaption_returns422`). Frontend `npm run build` + targeted ESLint pass for the media-repository and submission slices.

### Known Gaps

- UC-3.1 frontend browser action testing still needs an authenticated admin session and active backend to manually exercise retry, manual publish start, complete, and cancel against live data.
- Submission queue design needs review with real data and mobile widths.
- Submission lookups do not return categories, tags, or preferred time slots.
- UC-2.2 Media Repository backend + frontend implemented (list/detail/upload/delete, signed URL, multi-select → New Post, Add to Draft picker). Remaining: mp4 uploads fail when the Supabase `dasigconnect-media` bucket disallows `video/*` MIME types or sets a low file-size limit — fix in the Supabase dashboard (bucket settings), not code.
- Validator review actions need UC-2.1 backend.
- SSE notifications need UC-2.3 backend.
- Analytics need UC-2.4 backend.
- AI captions (UC-3.2) and AI classification/recommendation (UC-3.3) not started.
- No built-in validator account exists. Use the administrator dashboard to invite validators.
- Backend deployment runtime still needs team-owned SMTP and Supabase service credentials.

---

## Domain Model

### User Roles

| Role            | Scope           | Key Capabilities                                             |
| --------------- | --------------- | ------------------------------------------------------------ |
| `CONTRIBUTOR`   | Per institution | Submit content and upload media                              |
| `VALIDATOR`     | Per institution | Review institution submissions                               |
| `ADMINISTRATOR` | Network-wide    | Manage institutions, users, approvals, scheduling, analytics |

### Core Entities

| Entity                 | Purpose                                                                    |
| ---------------------- | -------------------------------------------------------------------------- |
| `User`                 | System user with role and institution binding                              |
| `Institution`          | DASIG member HEI workspace                                                 |
| `Submission`           | Content submission with event details and status                           |
| `SlotReservation`      | Scheduled slot reservation for a submission                                |
| `InvitationToken`      | One-time onboarding token                                                  |
| `PasswordResetToken`   | One-time password reset token                                              |
| `AccountLockout`       | Failed-login tracking                                                      |
| `AuditLog`             | Immutable state-changing action log                                        |
| `MediaAsset`           | Supabase-backed media metadata; pgvector embedding is not Hibernate-mapped |
| `SubmissionMediaAsset` | Submission-to-media junction with display order                            |

Important enum values are lowercase in the database, including roles and statuses. Keep Java/database serialization aligned with the existing code.

---

## Development Modules

| Branch / Area                     | Status                   | Notes                                                                                                                                                                               |
| --------------------------------- | ------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `feature/M1-model-foundation`     | Merged                   | Foundation entities, repositories, JWT/audit/email/tenant/security infrastructure, Flyway V1                                                                                        |
| `feature/M2-auth-backend`         | Merged                   | Auth, invitations, password reset, lockout, global exception handling                                                                                                               |
| `feature/M2-auth-backend-test`    | Merged                   | M1/M2 tests                                                                                                                                                                         |
| `feat/m4-institution-scheduling`  | Merged                   | Institution management, guard rails, slot reservation, provisioning                                                                                                                 |
| `dev`                             | In progress              | UC-1.2 extension, conditional bean fix, merged foundation work                                                                                                                      |
| `feature/uc13-submission-backend` | Done locally, not merged | UC-1.3 backend, required tests, frontend API wiring, reset password/session/dashboard fixes, pending invite/user management UI, invite token superseding, Flyway V4 media migration |
| `module3`                         | Done locally, not merged | UC-3.1 backend + frontend: publishing pipeline, calendar API/UI, Facebook Graph API integration, token encryption, scheduler jobs, Resolution Center backend/UI (UC-3.4), dynamic Facebook Preview modal, media carousel, and persisted submission media reordering. 208 backend tests passing for UC-3.1 baseline; focused submission reorder tests passing; frontend build passing. |
| UC-2.x                            | Not started              | Validation, media repository, notifications, analytics                                                                                                                              |
| UC-3.2 / UC-3.3                   | Not started              | AI Caption (Claude Vision), AI Classification & Recommendation (Voyage AI)                                                                                                          |

See `TASKS.md` for the detailed task checklist and current gaps.

---

## Key Design Decisions

- JWT auth is stateless; do not introduce server-side sessions.
- Tenant isolation is enforced in application services and PostgreSQL RLS.
- Flyway owns schema changes. Add migrations instead of relying on Hibernate DDL.
- Do not edit generated files under `backend/target`; fix migration conflicts in `backend/src/main/resources/db/migration`.
- `BackendApplication` custom Flyway/diagnostic beans are gated by `spring.flyway.enabled`; tests depend on this isolation.
- `MediaAsset.embedding` is not Hibernate-mapped because pgvector `VECTOR(1024)` is handled through native queries.
- Media upload is direct-to-Supabase from the frontend, followed by backend metadata attach. The backend does not receive multipart file bytes for Module 1.
- Submission media ordering is controlled by `submission_media_assets.display_order`; publishing queries already load assets ordered by this field. Use `PATCH /api/v1/submissions/{id}/media/order` for saved draft media reorder instead of mutating media asset records.
- Fresh Supabase `public` schemas should baseline Flyway at version `0`, not `1`; otherwise V1 is skipped and Module 1 tables are never created.
- Invitation links are treated as superseded when a fresh invite/resend is issued for the same email; do not leave multiple open invite links for one pending account.
- The frontend should display institution names from `GET /api/v1/me`; email-domain guessing is only a fallback.
- Do not increase HikariCP max pool size past 5 unless the Supabase plan/pooler changes.
- Do not log JWTs, passwords, reset tokens, invitation tokens, or API keys.
- `BackendApplication.flyway()` calls `repair()` then `migrate()` explicitly. Do not add `outOfOrder=true` or `validateOnMigrate=false` — fix schema history issues by renumbering migration files instead.
- When a migration file was never applied to a shared DB but a higher-version migration already was, do not reuse the old version number. Create new files at the next available version number.
- V4 creates `asset_tags` with columns `(asset_id, tag, source)`. V17 drops and recreates it with `(media_asset_id, label)` to match the `AssetTag` entity. Do not revert V17.
- Notification inserts in `SubmissionService.submit()` (T1 — notify validators) are wrapped in try-catch. This is intentional: a notification failure must never roll back the PENDING status transition.

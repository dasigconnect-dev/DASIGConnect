# DASIGConnect - Task Tracker

Team `2526-sem2-it332-38` - CIT-U Capstone - Spring Boot 4.0.6 + React 19

Legend: Done / In Progress / Not Started / Deferred

---

## Backend

### M1 - Foundation & Infrastructure

- Done: JPA entities, base repositories, core services, security infrastructure, Flyway V1, HikariCP configuration, and test-isolated `BackendApplication` beans.
- Done: M1 tests: `BackendApplicationTests`, `JWTServiceTest`, `AuditLogServiceTest`, `TenantScopeServiceTest`.

### M2 - Auth & User Provisioning

- Done: `AuthController` - login/logout.
- Done: `InvitationController` - create, validate, accept, resend.
- Done: invitation lifecycle hardening - a new invite/resend invalidates older unused, unexpired invite tokens for the same email.
- Done: `PasswordController` - forgot/reset password.
- Done: `UserController` - `GET /api/v1/me`, `GET /api/v1/users?institutionId=`, `GET /api/v1/users/counts?institutionId=`.
- Done: pending invitation endpoints - `GET /api/v1/invitations/pending` and `GET /api/v1/invitations/pending/count`.
- Done: validator scope enforcement for user counts and pending invitations.
- Done: `AuthService`, `AccountLockoutService`, `InvitationService`, `PasswordService`, `UserService`, `UserDto`, `JwtUserDetails`, `TokenHashUtils`, `GlobalExceptionHandler`.
- Done: M2 tests: account lockout, auth, invitations, password, and controller coverage.

### M4 - Institution Management & Scheduling

- Done: `InstitutionController`, `InstitutionService`, `InstitutionRepository`.
- Done: canonical `GET/POST /api/v1/institutions`; legacy `/api/v1/admin/institutions` remains supported.
- Done: `GuardRailService`, `SlotReservationService`, repositories, workspace provisioning, stale draft release job, DTOs, exceptions.
- Done: `Institution.emailDomain` and `V2__add_institution_email_domain.sql`.
- Done: M4 tests: institution DTO/service, guard rails, slot reservations.

### UC-1.3 - Content Submission Backend

- Done: Flyway V4 migration for `media_assets`, `asset_tags`, `submission_media_assets`, RLS, and pgvector index.
- Done: `MediaFileType`, `MediaAsset`, `SubmissionMediaAsset`.
- Done: `MediaAssetRepository`, `SubmissionMediaAssetRepository`, `SubmissionRepository` additions, `UserRepository` additions.
- Done: DTOs for create/update/response/summary/lookups/slot evaluation/media attachment.
- Done: `SubmissionService` - create draft, update, delete, submit, evaluate slot, list, attach uploaded media, attach media asset.
- Done: `SubmissionController` - endpoints on `/api/v1/submissions`.
- Done locally: guard rail enforcement is now configurable with `APP_GUARDRAILS_ENFORCED`; local default is `false` so testing can save drafts and submit for review without GR-H1/GR-H2/GR-H3 blocking.
- Done locally: guard rail violations now return a structured `422` response instead of a generic internal server error.
- Done: required tests before merge:
  - `SubmissionServiceTest`
  - `SubmissionControllerTest`
  - `UserServiceTest`
  - `UserControllerTest`
- Gap: save draft / submit-for-review still needs browser-level debugging and verification. The testing bypass and frontend payload defaults are in place, but the team has not yet confirmed the full flow works end to end in the UI.
- Deferred: `POST /api/v1/submissions/{id}/override-request`; implement in Module 3 with override request migration/service.

### Backend Verification

<<<<<<< HEAD

=======

> > > > > > > feature/m1-submission-tags-guardrails

- Done: backend test suite passed with 163 tests.
- Done: Flyway duplicate migration version conflict fixed; source and generated migrations now use unique versions `V1`, `V2`, `V3`, and `V4`.
- Done: `mvn clean` and `mvn -DskipTests package` passed after the migration rename.
- Done: fresh Supabase database startup fixed by baselining Flyway at version `0`; V1 through V4 apply correctly on a new Supabase `public` schema.
- Done: backend starts successfully against the updated Supabase database on a temporary port after migrations are applied.
- Note: `.\mvnw.cmd` fails in the current PowerShell environment; direct Maven from `.m2/wrapper/dists` was used successfully.

### Pending - Backend

<<<<<<< HEAD

=======

> > > > > > > feature/m1-submission-tags-guardrails

- Not Started: UC-2.1 Content Validation - review queue, approve/reject/needs-revision transitions.
- Not Started: UC-2.2 Media Repository - `MediaAssetController`, `GET/DELETE /api/v1/media-assets`.
- Not Started: UC-2.3 Notifications - SSE endpoint, notification service, emitter registry.
- Not Started: UC-2.4 Analytics Dashboard - aggregate endpoints.
- Not Started: UC-3.1 Calendar & Auto-publish - scheduler and Facebook Graph API integration.
- Not Started: UC-3.2 AI Caption - Claude Vision client and async generation.
- Not Started: UC-3.3 AI Classification & Recommendation - Voyage AI client and embedding pipeline.
- Not Started: UC-3.4 Manual Publishing Fallback.
- Not Started: UC-3.5 Admin Exception Handling / Override Request.

---

## Frontend

### UC-1.1 / UC-1.2 Auth & Onboarding

<<<<<<< HEAD

=======

> > > > > > > feature/m1-submission-tags-guardrails

- Done: login/logout UI wired to auth endpoints.
- Done: invitation accept flow.
- Done: forgot password request flow.
- Done: reset password page wired to `POST /api/v1/auth/reset-password`.
- Done: session banner/modal infrastructure now uses JWT `exp` for countdown and expiry.
- Done: dashboard supports administrator, validator, and contributor views.
- Done: institution provisioning modal wired to canonical `/api/v1/institutions`.
- Done: invite users modal wired to invitation endpoints.
- Note: no built-in validator account exists; create validators through the administrator invite flow.

### UC-1.3 Submission Form

<<<<<<< HEAD

=======

> > > > > > > feature/m1-submission-tags-guardrails

- Done: submission list wired to `GET /api/v1/submissions`.
- Done: draft creation wired to `POST /api/v1/submissions`.
- Done: save/update wired to `PATCH /api/v1/submissions/{id}`.
- Done: submit wired to `POST /api/v1/submissions/{id}/submit`.
- Done: delete wired to `DELETE /api/v1/submissions/{id}`.
- Done: guard rail panel wired to `POST /api/v1/submissions/{id}/evaluate-slot`.
- Done: media upload now follows the backend contract: direct Supabase Storage upload, then metadata attach to `POST /api/v1/submissions/{id}/media`.
- Done: backend lookup shape wired for allowed file types, file size, media count, and schedule windows.
- Done locally: `.jpg` uploads are normalized to backend-supported `jpeg`.
- Done locally: submission queue has been restyled into a clearer left-side queue panel with count, stronger item containers, active state, and improved spacing.
- Done locally: submit buttons are not disabled by readiness score during testing.
- Gap: save draft and submit-for-review are still treated as unresolved until the browser flow is manually verified against the active backend/Supabase environment.

### Dashboard

<<<<<<< HEAD

=======

> > > > > > > feature/m1-submission-tags-guardrails

- Done: stat tiles use live endpoints where available: submissions, institutions, user counts, and pending invitation counts.
- Done: invite/manage modal lists pending invitations and current users for the selected institution.
- Done: pending invitations can be resent from the invite/manage modal.
- Done: current user state hydrates from `GET /api/v1/me` after login, invite accept, session relogin, and saved-token restore.

### Pending - Frontend

<<<<<<< HEAD

=======

> > > > > > > feature/m1-submission-tags-guardrails

- Gap: submission queue design improved locally but still needs user/team review against real data and mobile widths.
- Gap: save draft / submit review still needs final UI-level debugging; current local changes are intended to unblock testing but are not yet proven end to end.
- Gap: category/tag/preferred-time selection is limited because backend lookups do not currently return those fields.
- Gap: `AssetPickerModal` cannot be fully wired until UC-2.2 media asset list/delete endpoints exist.
- Gap: validator review actions need UC-2.1 backend.
- Gap: notification badge/stream needs UC-2.3 SSE backend.
- Gap: analytics dashboard needs UC-2.4 backend.
- Gap: calendar, publishing, AI caption, and recommendation screens need UC-3.x backend.
- Gap: Supabase browser upload env is now configured locally, but the full upload flow still needs manual verification through the submission form.

### Frontend Verification

<<<<<<< HEAD

=======

> > > > > > > feature/m1-submission-tags-guardrails

- Done: `npm.cmd run build` passed.
- Local setup: `frontend/.env.local` should contain `VITE_API_URL=http://localhost:8080/api/v1` when using the Vite dev server with the local backend. This file is intentionally ignored by Git.
- Required env vars for browser media upload:
  - `VITE_SUPABASE_URL`
  - `VITE_SUPABASE_STORAGE_BUCKET`
  - `VITE_SUPABASE_ANON_KEY`
- Done locally: `frontend/.env.local` contains the Vite Supabase upload variables and uses the `dasigconnect-media` bucket.

---

## Housekeeping

- Done: M1 test branch merged to `main`.
- Done: M4 test branch merged to `main`.
- Done: docs/handoff updates merged to `main` via PRs #13, #15, #16.
- Done: UC-1.3 backend and frontend API wiring completed locally on `feature/uc13-submission-backend`.
- Done: UC-1.3 Flyway migration conflict fixed by renaming media migration to `V4__media_assets.sql`.
- Done: Module 1 auth/onboarding cleanup completed locally: `/me` hydration, pending invite count/list/resend UI, invite token superseding, and user-count tenant scope hardening.
- In Progress: merge/code review path for `feature/uc13-submission-backend`.
- Done locally: SMTP credentials are configured in ignored local environment files; deployment runtime still needs team-owned SMTP values.
- Done locally: Supabase database, service-role, frontend upload, and storage bucket env values are configured in ignored env files.
- Not Started: configure Supabase service/browser upload environment variables in deployed environments.

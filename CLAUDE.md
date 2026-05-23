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

## Current Local Status - 2026-05-22

Current branch: `feature/uc13-submission-backend`.

### Completed

- UC-1.3 submission backend is implemented and tested.
- Required tests are present:
  - `SubmissionServiceTest`
  - `SubmissionControllerTest`
  - `UserServiceTest`
  - `UserControllerTest`
- Frontend API wiring now matches backend contracts for submissions, guard rail evaluation, media upload metadata, institutions, and lookups.
- Reset password page is implemented.
- Session expiry countdown is wired from JWT `exp`.
- Dashboard stats use live endpoints where backend support exists, including pending invitation counts.
- Dashboard invite/manage modal lists pending invitations, supports resend, and shows current users for the selected institution.
- App auth state hydrates from `GET /api/v1/me` after login, invite accept, session relogin, and saved-token restore.
- Invitation create/resend now supersedes older unused, unexpired invite tokens for the same recipient email.
- User count queries now enforce validator institution scope.
- `InstitutionController` exposes canonical `/api/v1/institutions` and legacy `/api/v1/admin/institutions`.
- Flyway migration versions are unique after the UC-1.3 fix: `V3__ensure_institution_email_domain.sql` and `V4__media_assets.sql`.
- Local datasource configuration supports `DASIG_DATABASE_*` overrides before falling back to standard `DATABASE_*` values and a local JDBC default.
- Backend Supabase config supports `DASIG_SUPABASE_URL`, `DASIG_SUPABASE_SERVICE_ROLE_KEY`, and `DASIG_SUPABASE_STORAGE_BUCKET`.
- Flyway fresh Supabase startup is fixed with baseline version `0`, allowing V1 through V4 to run on a new Supabase `public` schema.
- Local frontend Supabase upload env values are configured through ignored `frontend/.env.local` for the `dasigconnect-media` bucket.
- Guard rail enforcement is now configurable through `APP_GUARDRAILS_ENFORCED`; local default is `false` to keep save draft / submit-for-review testing unblocked while scheduling rules are tuned.
- `GuardRailViolationException` now returns a structured `422` payload instead of falling through as a generic internal server error.
- Submission queue UI has been restyled into a clearer queue panel, and frontend `.jpg` file checks/upload metadata now normalize to `jpeg`.

### Verification

- Backend: 163 tests passing from the full prior suite.
- Backend focused submission verification: 42 tests passing across `SlotReservationServiceTest`, `SubmissionServiceTest`, and `SubmissionControllerTest`.
- Backend focused auth/onboarding tests: 50 tests passing across `InvitationServiceTest`, `InvitationControllerTest`, `UserServiceTest`, and `UserControllerTest`.
- Frontend: `npm.cmd run build` passing.
- Migration sanity: `mvn clean` and `mvn -DskipTests package` passed after renaming the media migration from V3 to V4.
- Fresh Supabase DB startup: backend applied V1 through V4 successfully, then a second startup validated migrations with 0 pending migrations.

### Known Gaps

- Save draft / submit-for-review remains the active Module 1 blocker until the browser flow is manually verified against the current backend and Supabase environment. The testing bypass and frontend payload defaults are in place, but the issue is not considered closed.
- Submission queue design has been improved locally, but still needs review with real queue data and responsive/mobile widths.
- Submission lookups do not return categories, tags, or preferred time slots.
- Media library / asset picker needs UC-2.2 backend: `GET/DELETE /api/v1/media-assets`.
- Validator review actions need UC-2.1 backend.
- SSE notifications need UC-2.3 backend.
- Analytics need UC-2.4 backend.
- Calendar, auto-publish, manual fallback, AI captions, and recommendations need UC-3.x backend.
- No built-in validator account exists. Use the administrator dashboard to invite validators, then accept the invitation and set a password.
- Browser media upload credentials are configured locally, but the full upload flow still needs manual verification through the submission form.
- Backend deployment runtime still needs team-owned SMTP and Supabase service credentials. Local SMTP and Supabase values are configured through ignored env files.

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
| UC-2.x                            | Not started              | Validation, media repository, notifications, analytics                                                                                                                              |
| UC-3.x                            | Not started              | Calendar, publishing, AI captions/classification/recommendations                                                                                                                    |

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
- Fresh Supabase `public` schemas should baseline Flyway at version `0`, not `1`; otherwise V1 is skipped and Module 1 tables are never created.
- Invitation links are treated as superseded when a fresh invite/resend is issued for the same email; do not leave multiple open invite links for one pending account.
- The frontend should display institution names from `GET /api/v1/me`; email-domain guessing is only a fallback.
- Do not increase HikariCP max pool size past 5 unless the Supabase plan/pooler changes.
- Do not log JWTs, passwords, reset tokens, invitation tokens, or API keys.

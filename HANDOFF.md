# Handoff - 2026-05-22

Current branch: `feature/uc13-submission-backend`

## Status

### Backend
- Done: UC-1.3 submission backend endpoints on `/api/v1/submissions`.
- Done: required merge-blocking tests:
  - `SubmissionServiceTest`
  - `SubmissionControllerTest`
  - `UserServiceTest`
  - `UserControllerTest`
- Done: `GlobalExceptionHandler` now returns `400 Bad Request` for missing request parameters instead of falling through to generic server error handling.
- Done: `InstitutionController` now exposes canonical `/api/v1/institutions` and keeps `/api/v1/admin/institutions` as a legacy alias.
- Done: M2 stragglers remain in place: resend invitation, `GET /me`, scoped user listing, and user role counts.
- Done: pending invitation endpoints are available on `/api/v1/invitations/pending` and `/api/v1/invitations/pending/count`.
- Done: invitation lifecycle is hardened so creating/resending an invitation invalidates older unused, unexpired tokens for the same recipient email.
- Done: `GET /api/v1/users/counts` now applies the same validator institution scope rule as user listing.
- Done: Flyway duplicate version conflict fixed by keeping `V3__ensure_institution_email_domain.sql` and renaming the media migration to `V4__media_assets.sql`.
- Done: local datasource config supports `DASIG_DATABASE_*` overrides first, then standard `DATABASE_*` variables, with a local JDBC fallback. This avoids accidental unresolved/provider-style database URLs during local runs.
- Done: backend Supabase config supports `DASIG_SUPABASE_URL`, `DASIG_SUPABASE_SERVICE_ROLE_KEY`, and `DASIG_SUPABASE_STORAGE_BUCKET`.
- Done: Flyway fresh Supabase startup is fixed by using baseline version `0`; a new Supabase `public` schema now baselines at `0` and runs V1 through V4 instead of skipping V1.

### Frontend
- Done: submission API wiring now matches the backend:
  - `POST /api/v1/submissions` for draft creation.
  - `POST /api/v1/submissions/{id}/evaluate-slot` with `{ scheduledAt }`.
  - Direct browser upload to Supabase Storage, then `POST /api/v1/submissions/{id}/media` with `{ storageUrl, fileName, fileType, fileSizeBytes }`.
  - `GET/POST /api/v1/institutions`.
  - Lookup typing now matches backend constants: file types, size limits, media limits, and schedule lead windows.
- Done: reset password screen and `/reset-password` route are wired to `POST /api/v1/auth/reset-password`.
- Done: session warning infrastructure now reads JWT `exp`, starts a five-minute countdown, and opens the expiry modal at timeout.
- Done: dashboard stat tiles are no longer all hardcoded zero; they use live submission/user/institution/pending-invitation endpoints where backend support exists.
- Done: app auth state hydrates from `GET /api/v1/me` after login, invite accept, session modal relogin, and saved-token restore. This prevents Gmail/domain guessing from overriding the real institution.
- Done: invite/manage modal now lists pending invitations and current users for the selected institution, including resend actions for active pending invites.
- Done locally: `frontend/.env.local` points Vite to `http://localhost:8080/api/v1` and contains the Supabase browser upload variables for the `dasigconnect-media` bucket. This file is intentionally ignored and should not be committed.

## Verification

- Frontend: `npm.cmd run build` passed.
- Backend focused auth/onboarding verification: `.\mvnw.cmd '-Dtest=InvitationServiceTest,InvitationControllerTest,UserServiceTest,UserControllerTest' test` passed.
- Backend focused result: `Tests run: 50, Failures: 0, Errors: 0, Skipped: 0`.
- Backend: `mvn test` passed using the local Maven distribution because `.\mvnw.cmd` fails in this PowerShell environment.
- Backend result: `Tests run: 163, Failures: 0, Errors: 0, Skipped: 0`.
- Flyway duplicate migration check: source and generated `target/classes/db/migration` now have unique versions `V1`, `V2`, `V3`, and `V4`; `mvn clean` and `mvn -DskipTests package` passed.
- Fresh Supabase DB check: backend applied V1 through V4 successfully, then a second startup validated migrations and started with 0 pending migrations.
- Latest frontend check after local Supabase env wiring: `npm.cmd run build` passed.

Backend test command used:

```powershell
& "$env:USERPROFILE\.m2\wrapper\dists\apache-maven-3.9.15\0226a00282e400185496f3b60ec5a3f029cbdc6893912937d4876d57695224e1\bin\mvn.cmd" test
```

## Remaining Gaps

- `GET /api/v1/submissions/lookups` does not provide categories, tags, or preferred time slots. The frontend no longer assumes those fields, but richer category/tag UI needs backend support.
- `AssetPickerModal` / media library still needs UC-2.2 backend: `MediaAssetController`, especially `GET /api/v1/media-assets` and delete behavior.
- Validator review queue actions still need UC-2.1 backend: approve, reject, needs-revision, and validator/admin transition rules.
- SSE notifications still need UC-2.3 backend.
- Analytics still need UC-2.4 backend aggregate endpoints.
- Calendar, publishing, Facebook fallback, AI captions, and AI recommendations still need UC-3.x backend.
- No built-in validator account exists. Validators must be invited from the administrator dashboard, accept the invite, and set their password.
- Supabase browser upload credentials are configured locally. The full browser upload flow still needs manual verification through the submission form. Required frontend env vars are:
  - `VITE_SUPABASE_URL`
  - `VITE_SUPABASE_STORAGE_BUCKET`
  - `VITE_SUPABASE_ANON_KEY`
- Local frontend/backend connection requires `frontend/.env.local` with `VITE_API_URL=http://localhost:8080/api/v1`.
- Backend runtime still needs deployment-owned SMTP and Supabase service credentials in deployed environments. Local SMTP and Supabase values are configured through ignored env files.
- `.\mvnw.cmd` may require network access to resolve dependencies in this environment; rerun with normal Maven/network permissions if dependency resolution fails.

## Critical Invariants

- `MediaAsset.embedding` is not mapped by Hibernate. Keep pgvector reads/writes in native repository queries.
- Keep HikariCP max pool size at 5 for Supabase Session Pooler.
- Keep `DATABASE_USER` / `DATABASE_USERNAME` environment naming aligned with `application.properties` and local `.env`.
- Prefer local `DASIG_DATABASE_*` variables when testing locally to avoid collisions with provider/system `DATABASE_URL` values.
- For fresh Supabase projects, keep Flyway baseline version at `0`; baseline version `1` marks V1 as applied without creating Module 1 tables.
- Media upload pattern is frontend direct-to-Supabase first, backend metadata second. Do not switch Module 1 endpoints to multipart upload.
- Invitation tokens are single-use in practice: issuing a fresh invite/resend supersedes older open links for the same email.
- Do not log JWTs, reset tokens, invitation tokens, passwords, or API keys.
- Do not edit generated migration files under `backend/target`; fix Flyway issues only in `backend/src/main/resources/db/migration`.

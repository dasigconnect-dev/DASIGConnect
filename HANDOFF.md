# Handoff - 2026-05-21

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
- Done: Flyway duplicate version conflict fixed by keeping `V3__ensure_institution_email_domain.sql` and renaming the media migration to `V4__media_assets.sql`.

### Frontend
- Done: submission API wiring now matches the backend:
  - `POST /api/v1/submissions` for draft creation.
  - `POST /api/v1/submissions/{id}/evaluate-slot` with `{ scheduledAt }`.
  - Direct browser upload to Supabase Storage, then `POST /api/v1/submissions/{id}/media` with `{ storageUrl, fileName, fileType, fileSizeBytes }`.
  - `GET/POST /api/v1/institutions`.
  - Lookup typing now matches backend constants: file types, size limits, media limits, and schedule lead windows.
- Done: reset password screen and `/reset-password` route are wired to `POST /api/v1/auth/reset-password`.
- Done: session warning infrastructure now reads JWT `exp`, starts a five-minute countdown, and opens the expiry modal at timeout.
- Done: dashboard stat tiles are no longer all hardcoded zero; they use live submission/user/institution endpoints where backend support exists.
- Done locally: `frontend/.env.local` points Vite to `http://localhost:8080/api/v1`; this file is intentionally ignored and should not be committed.

## Verification

- Frontend: `npm.cmd run build` passed.
- Backend: `mvn test` passed using the local Maven distribution because `.\mvnw.cmd` fails in this PowerShell environment.
- Backend result: `Tests run: 163, Failures: 0, Errors: 0, Skipped: 0`.
- Flyway duplicate migration check: source and generated `target/classes/db/migration` now have unique versions `V1`, `V2`, `V3`, and `V4`; `mvn clean` and `mvn -DskipTests package` passed.

Backend test command used:

```powershell
& "$env:USERPROFILE\.m2\wrapper\dists\apache-maven-3.9.15\0226a00282e400185496f3b60ec5a3f029cbdc6893912937d4876d57695224e1\bin\mvn.cmd" test
```

## Remaining Gaps

- Pending invitation dashboard count still shows `0`; there is no dedicated backend count/list endpoint for pending invitations yet.
- `GET /api/v1/submissions/lookups` does not provide categories, tags, or preferred time slots. The frontend no longer assumes those fields, but richer category/tag UI needs backend support.
- `AssetPickerModal` / media library still needs UC-2.2 backend: `MediaAssetController`, especially `GET /api/v1/media-assets` and delete behavior.
- Validator review queue actions still need UC-2.1 backend: approve, reject, needs-revision, and validator/admin transition rules.
- SSE notifications still need UC-2.3 backend.
- Analytics still need UC-2.4 backend aggregate endpoints.
- Calendar, publishing, Facebook fallback, AI captions, and AI recommendations still need UC-3.x backend.
- `GET /api/v1/me` helper exists client-side, but app hydration still primarily depends on login/localStorage state. A future cleanup should hydrate current user from `/me` on app load.
- No built-in validator account exists. Validators must be invited from the administrator dashboard, accept the invite, and set their password.
- Supabase browser upload requires frontend env vars:
  - `VITE_SUPABASE_URL`
  - `VITE_SUPABASE_STORAGE_BUCKET`
  - `VITE_SUPABASE_ANON_KEY`
- Local frontend/backend connection requires `frontend/.env.local` with `VITE_API_URL=http://localhost:8080/api/v1`.
- Backend runtime still needs real SMTP and Supabase service credentials in the environment.
- `.\mvnw.cmd` currently fails to start Maven in this PowerShell environment; direct Maven from `.m2/wrapper/dists` works.

## Critical Invariants

- `MediaAsset.embedding` is not mapped by Hibernate. Keep pgvector reads/writes in native repository queries.
- Keep HikariCP max pool size at 5 for Supabase Session Pooler.
- Keep `DATABASE_USER` / `DATABASE_USERNAME` environment naming aligned with `application.properties` and local `.env`.
- Media upload pattern is frontend direct-to-Supabase first, backend metadata second. Do not switch Module 1 endpoints to multipart upload.
- Do not log JWTs, reset tokens, invitation tokens, passwords, or API keys.
- Do not edit generated migration files under `backend/target`; fix Flyway issues only in `backend/src/main/resources/db/migration`.

# Handoff — 2026-05-21

## What was done this session

### Analysis
- Extracted and read SDD (94pp), SRS (134pp), Proposal (12pp), and Module todos in full
- Updated `TASKS.md` and `CLAUDE.md` with full spec findings on `docs-and-skills` branch, then pushed

### UC-1.3 Backend — `feature/uc13-submission-backend` (commit `6b0c60e`)

**M2 stragglers (completed):**
- `InvitationService.resend()` — generates a fresh 72h token, resets `pending_email_undelivered` → `pending`, re-sends email; reverts on failure
- `InvitationController` `POST /api/v1/invitations/{id}/resend`
- `UserService` — `getProfile()`, `listByInstitution()` (validator scoped to own institution), `countByRole()`
- `UserController` `GET /api/v1/me`, `GET /api/v1/users?institutionId=`, `GET /api/v1/users/counts?institutionId=`
- `UserDto` (no password hash)

**UC-1.3 — Flyway migration:**
- `V3__media_assets.sql` — creates `media_assets` (with `embedding VECTOR(1024)`, `ivfflat` cosine index), `asset_tags` (UNIQUE asset+tag), `submission_media_assets` (junction, ON DELETE CASCADE); RLS on all three tables

**UC-1.3 — New entities:**
- `MediaFileType` enum — `jpeg, png, webp, gif, mp4, mov, webm`; helpers `isVideo()`, `isImage()`
- `MediaAsset` — **`embedding` column NOT mapped by Hibernate** (pgvector incompatibility); managed exclusively via native queries
- `SubmissionMediaAsset` — junction entity for submission↔media_asset with `display_order`

**UC-1.3 — Repository additions:**
- `MediaAssetRepository` — `findActiveById`, `findActiveByInstitution`, `@Modifying` native `updateEmbedding`, native cosine `findTopSimilar` (ready for UC-3.3)
- `SubmissionMediaAssetRepository` — ordered by display_order, existence check, count queries, active-submission-count for UC-2.2 deletion safety
- `UserRepository` — added `findByInstitutionIdOrderByCreatedAtDesc`, `countByInstitutionIdAndRole`
- `SubmissionRepository` — added role-filtered list queries, `existsByIdAndInstitutionId`, `existsByIdAndContributorId`

**UC-1.3 — DTOs:**
- `SubmissionCreateDto`, `SubmissionUpdateDto` (PATCH semantics — all fields optional)
- `SubmissionResponseDto` (full detail + media list), `SubmissionSummaryDto` (list view + mediaCount)
- `SlotEvaluateRequestDto`, `SubmissionLookupsDto` (immutable constants)
- `AttachMediaDto` (storageUrl from frontend direct-upload), `AttachAssetDto`
- `MediaAssetSummaryDto`

**UC-1.3 — Services/Controllers:**
- `SubmissionService` — `create` (DRAFT first → reserve slot), `update`, `delete`, `submit` (re-validates guard rails), `evaluateSlot` (read-only), `list` (role-filtered), `attachMedia` (max 10), `attachAsset`
- `SubmissionController` — all 10 endpoints on `/api/v1/submissions` (lookups declared first to avoid `/{id}` path conflict)

**Build state:** 124 existing tests pass; `./mvnw test` = BUILD SUCCESS on this branch.

## Files changed (commit 6b0c60e — 27 files, +1507 lines)

- `V3__media_assets.sql` (new Flyway migration)
- `MediaFileType.java`, `MediaAsset.java`, `SubmissionMediaAsset.java` (new entities)
- `MediaAssetRepository.java`, `SubmissionMediaAssetRepository.java` (new repos)
- `UserRepository.java`, `SubmissionRepository.java` (new query methods)
- All 9 new DTO files
- `SubmissionNotFoundException.java`, `MediaAssetNotFoundException.java`
- `SubmissionService.java`, `SubmissionController.java`
- `UserService.java`, `UserController.java`, `UserDto.java`
- `InvitationService.java` (resend method), `InvitationController.java` (resend endpoint)
- `backend/.env.example` (all required env var keys)
- `TASKS.md`, `CLAUDE.md`, `HANDOFF.md`

## What's NOT done — immediate next steps

### Backend (Chris or next developer)
1. **Tests for UC-1.3** — `SubmissionServiceTest`, `SubmissionControllerTest`, `UserServiceTest`, `UserControllerTest` are not yet written. All existing 124 tests pass; new tests needed.
2. **`POST /api/v1/submissions/{id}/override-request`** — deferred to Module 3. Requires a new `override_requests` table (V5 migration) and `OverrideRequestService`. Do NOT implement until Module 3.
3. **Merge `feature/uc13-submission-backend` → `main`** (or through `dev`) after code review.

### Frontend (Jay, Anton, or next developer)
All UC-1.3 frontend wiring is unstarted. The backend endpoints are fully live — wire them:

1. **Contributor dashboard / SubmissionFormPage** (Jay)
   - `GET /api/v1/submissions/lookups` → populate allowed file types and limits
   - `POST /api/v1/submissions` → create DRAFT
   - `PATCH /api/v1/submissions/{id}` → auto-save every 60s
   - `POST /api/v1/submissions/{id}/submit` → submit for review
   - `DELETE /api/v1/submissions/{id}` → delete DRAFT
   - `POST /api/v1/submissions/{id}/media` → attach newly uploaded file (upload goes direct to Supabase Storage first, then pass URL here)
   - `POST /api/v1/submissions/{id}/assets` → attach from media library
   - `POST /api/v1/submissions/{id}/evaluate-slot` → real-time slot validation in SlotPicker

2. **AssetPickerModal** — lists media library assets; uses `GET /api/v1/media-assets` (UC-2.2, not yet implemented backend-side); stub for now

3. **SessionWarningBanner + SessionExpiredModal** (Anton) — watch JWT `exp` claim, warn at T-5 min, force logout at T-0; wire to `/api/v1/auth/logout`

4. **Institution management page** (unassigned) — uses `GET/POST /api/v1/institutions` (already implemented, M4)

5. **GET /api/v1/me** is live — use it on app load to hydrate user context (role, institution) instead of parsing JWT claims

### Operational
- Configure real SMTP credentials in `backend/.env` (currently empty — invitation and reset emails will fail)
- Set `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`, `SUPABASE_STORAGE_BUCKET` for media upload to work

## Git state

| Branch | Remote | Status |
|---|---|---|
| `feature/uc13-submission-backend` | none | **Not pushed yet** — 1 commit ahead of `main` |
| `main` | `origin/main` | **11 commits ahead** — not pushed (user's decision) |
| `dev` | `origin/dev` | **22+ commits ahead** — not pushed (user's decision) |

## Critical invariants — do not break

- `embedding` field in `MediaAsset` is NOT a Hibernate-mapped column — native queries only in `MediaAssetRepository`
- HikariCP max 5 connections (Supabase Session Pooler hard limit) — never increase
- `application.properties` uses `${DATABASE_USER}`; `.env` uses `DATABASE_USERNAME` — keep in sync
- Never log JWT tokens, passwords, or API keys
- `spring.flyway.enabled=false` in test resources — required for `@WebMvcTest` / `@SpringBootTest` isolation
- Enum values are lowercase (`draft`, `contributor`, etc.) — match DB CHECK constraints
- Media upload pattern: frontend uploads binary directly to Supabase Storage → passes `storageUrl` to `POST /media`; the backend never handles file bytes in Module 1

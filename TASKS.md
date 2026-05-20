# DASIGConnect - Task Tracker

Team `2526-sem2-it332-38` - CIT-U Capstone - Spring Boot 4.0.6 + React 19

Legend: Done / In Progress / Not Started

---

## Backend

### M1 - Foundation & Infrastructure (Lerah)
- Done: All JPA entities: `User`, `Institution`, `Submission`, `SlotReservation`, `InvitationToken`, `PasswordResetToken`, `AccountLockout`, `AuditLog`
- Done: All base repositories: `UserRepository`, `InstitutionRepository`, `SubmissionRepository`, `SlotReservationRepository`, `InvitationTokenRepository`, `PasswordResetTokenRepository`, `AccountLockoutRepository`, `AuditLogRepository`
- Done: Core services: `JWTService`, `AuditLogService`, `EmailService`, `TenantScopeService`
- Done: Security infrastructure: `JwtAuthenticationFilter`, `SecurityConfig`, `JwtUserDetails`
- Done: Flyway V1 migration and RLS policies
- Done: `JacksonConfig`, HikariCP configuration
- Done: `BackendApplication` custom `flyway` and `dbDiagnostics` beans gated with `@ConditionalOnProperty(spring.flyway.enabled)` — required for `@WebMvcTest` and `@SpringBootTest` test isolation

### M1 Tests
- Done: `BackendApplicationTests`
- Done: `JWTServiceTest`
- Done: `AuditLogServiceTest`
- Done: `TenantScopeServiceTest`

### M2 - Auth & User Provisioning (Chris)
- Done: `AuthController` - `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`
- Done: `InvitationController` - `POST /api/v1/invitations`, `GET /api/v1/invitations/validate`, `POST /api/v1/invitations/accept`, `POST /api/v1/invitations/{id}/resend`
- Done: `PasswordController` - `POST /api/v1/auth/forgot-password`, `POST /api/v1/auth/reset-password`
- Done: `UserController` - `GET /api/v1/me`, `GET /api/v1/users?institutionId=`, `GET /api/v1/users/counts?institutionId=`
- Done: `AuthService` - login with lockout check, BCrypt verify, JWT generation, audit log
- Done: `AccountLockoutService` - failed attempt tracking and lockout policy
- Done: `InvitationService` - hashed one-time invitation tokens, user provisioning, and `resend()` (regenerates 72h token, resets `pending_email_undelivered` → `pending`)
- Done: `PasswordService` - anti-enumeration reset token flow
- Done: `UserService` - `getProfile()`, `listByInstitution()` (validator scoped to own institution), `countByRole()`
- Done: `UserDto` - id, email, role, accountState, institutionId, institutionName, createdAt (no password hash)
- Done: `JwtUserDetails`, `TokenHashUtils`, `GlobalExceptionHandler`

### M2 Tests
- Done: `AccountLockoutServiceTest`
- Done: `AuthServiceTest`
- Done: `InvitationServiceTest` (includes `buildInvitationLink` mock fix for dev branch)
- Done: `PasswordServiceTest`
- Done: `AuthControllerTest`
- Done: `InvitationControllerTest`
- Done: `PasswordControllerTest`

### M4 - Institution Management & Scheduling
- Done: `InstitutionController`
- Done: `InstitutionService`
- Done: `InstitutionRepository`
- Done: `GuardRailService`
- Done: `SlotReservationService`
- Done: `SlotReservationRepository`
- Done: `SubmissionRepository` guard rail queries
- Done: `WorkspaceProvisionerService`
- Done: `StaleDraftSlotReleaseJob`
- Done: Exceptions: `GuardRailViolationException`, `InstitutionNotFoundException`, `SlotAlreadyTakenException`
- Done: DTOs: `CreateInstitutionRequest`, `InstitutionDto`, `GuardRailResult`, `GuardRailViolation`
- Done: `Institution.emailDomain` field + `V2__add_institution_email_domain.sql` Flyway migration (UC-1.2 extension — on `dev`)

### M4 Tests
- Done: `InstitutionDtoTest` (updated for 3-arg `CreateInstitutionRequest` constructor)
- Done: `GuardRailDtoTest`
- Done: `InstitutionServiceTest` (updated for 3-arg `CreateInstitutionRequest` constructor)
- Done: `GuardRailServiceTest`
- Done: `SlotReservationTest`

### UC-1.3 - Content Submission Backend (Chris) — branch `feature/uc13-submission-backend`
- Done: Flyway V3 migration — `V3__media_assets.sql` — `media_assets` (VECTOR(1024) column, ivfflat cosine index), `asset_tags`, `submission_media_assets` (junction, ON DELETE CASCADE); RLS on all three tables
- Done: `MediaFileType` enum — jpeg, png, webp, gif, mp4, mov, webm
- Done: `MediaAsset` entity — NOTE: `embedding` column NOT mapped by Hibernate (pgvector incompatibility); use native queries only
- Done: `SubmissionMediaAsset` junction entity with `display_order`
- Done: `MediaAssetRepository` — findActive*, @Modifying native updateEmbedding, native cosine `findTopSimilar` (ready for UC-3.3)
- Done: `SubmissionMediaAssetRepository` — ordered queries, existence/count checks, active-submission-count for UC-2.2 deletion safety
- Done: `UserRepository` additions — `findByInstitutionIdOrderByCreatedAtDesc`, `countByInstitutionIdAndRole`
- Done: `SubmissionRepository` additions — role-filtered list queries, `existsByIdAndInstitutionId`, `existsByIdAndContributorId`
- Done: DTOs — `SubmissionCreateDto`, `SubmissionUpdateDto` (all-optional PATCH), `SubmissionResponseDto`, `SubmissionSummaryDto`, `SlotEvaluateRequestDto`, `SubmissionLookupsDto`, `AttachMediaDto`, `AttachAssetDto`, `MediaAssetSummaryDto`
- Done: Exceptions — `SubmissionNotFoundException`, `MediaAssetNotFoundException`
- Done: `SubmissionService` — create (DRAFT→reserve slot), update, delete, submit (re-validates guard rails), evaluateSlot (read-only), list (role-filtered), attachMedia (max 10), attachAsset
- Done: `SubmissionController` — 10 endpoints on `/api/v1/submissions` — lookups, list, create (201), get, update, delete (204), submit, evaluate-slot, media (201), assets (201)
- Not Started: **Tests** — `SubmissionServiceTest`, `SubmissionControllerTest`, `UserServiceTest`, `UserControllerTest` (required before merge to main)
- Deferred to Module 3: `POST /api/v1/submissions/{id}/override-request` — needs V5 migration + `override_requests` table

### Flyway Migrations Applied
- Done: V1 — base schema (on `main`)
- Done: V2 — `institution.email_domain` (on `dev`)
- Done: V3 — `media_assets`, `asset_tags`, `submission_media_assets` (on `feature/uc13-submission-backend`)

### Pending - Backend
- Not Started: UC-2.1 Content Validation - `ValidationController`, validator review flow (approve/reject/needs-revision transitions)
- Not Started: UC-2.2 Media Repository - `MediaAssetController`, `GET/DELETE /api/v1/media-assets`
- Not Started: UC-2.3 Notifications - SSE endpoint, `NotificationService`, `SseEmitter` registry
- Not Started: UC-2.4 Analytics Dashboard - aggregate query endpoints
- Not Started: UC-3.1 Calendar & Auto-publish - `PublishingSchedulerJob`, Facebook Graph API integration
- Not Started: UC-3.2 AI Caption - `ClaudeVisionClient`, async caption generation on upload
- Not Started: UC-3.3 AI Classification & Recommendation - `VoyageAIClient`, cosine similarity search (repository query already written in `MediaAssetRepository.findTopSimilar`)
- Not Started: UC-3.4 Manual Publishing Fallback - `ManualPublishingFallbackController`
- Not Started: UC-3.5 Admin Exception Handling / Override Request - `OverrideRequestService`, V5 migration

---

## Frontend

> All UC-1.3 backend endpoints are live. Wire the frontend to these APIs:

### UC-1.1 / UC-1.2 Auth & Onboarding (Anton / unassigned)
- Not Started: Login / logout UI → `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`
- Not Started: Invitation accept flow → `GET /api/v1/invitations/validate?token=`, `POST /api/v1/invitations/accept`
- Not Started: Forgot / reset password pages → `POST /api/v1/auth/forgot-password`, `POST /api/v1/auth/reset-password`
- Not Started: SessionWarningBanner + SessionExpiredModal (Anton) — watch JWT `exp` claim, warn at T-5 min, force logout at T-0
- Not Started: Institution management page (unassigned) → `GET/POST /api/v1/institutions`, `GET /api/v1/users?institutionId=`

### UC-1.3 Submission Form (Jay)
- Not Started: SubmissionFormPage → wire `POST /api/v1/submissions`, `PATCH /{id}` (auto-save 60s), `POST /{id}/submit`, `DELETE /{id}`
- Not Started: SlotPicker → wire `POST /api/v1/submissions/{id}/evaluate-slot` for real-time guard rail feedback
- Not Started: MediaUploadPanel → upload binary direct to Supabase Storage → pass storageUrl to `POST /api/v1/submissions/{id}/media`
- Not Started: AssetPickerModal → `GET /api/v1/media-assets` (UC-2.2 backend not yet implemented — stub this panel)
- Not Started: `GET /api/v1/submissions/lookups` → populate allowed file types, size limits on form load
- Not Started: Submission list page → `GET /api/v1/submissions`

### UC-2.x Validation / Notifications / Analytics (unassigned)
- Not Started: Validator dashboard - review queue, approve/reject/revise → UC-2.1 backend not yet implemented
- Not Started: SSE notification badge → `GET /api/v1/notifications/stream` (UC-2.3 backend not yet implemented)
- Not Started: Analytics dashboard → UC-2.4 backend not yet implemented

### UC-3.x Calendar / Publishing / AI (unassigned)
- Not Started: Calendar view with slot selection → `GET /api/v1/calendar` (UC-3.1 backend not yet implemented)
- Not Started: AI caption display → UC-3.2 backend not yet implemented
- Not Started: Media recommendation panel → `POST /api/v1/media-assets/recommend` (UC-3.3 backend not yet implemented)

### Global (all roles)
- Not Started: Role-based route protection (React Router guards)
- Not Started: `GET /api/v1/me` → call on app load to hydrate user context instead of parsing JWT

---

## Housekeeping

- Done: M1 test branch merged to `main`
- Done: M4 test branch merged to `main`
- Done: Docs/handoff updates merged to `main` via PRs #13, #15, #16
- Done: Test warning/import cleanup (`AuthControllerTest`, `AuthServiceTest`, `InvitationServiceTest`, `InstitutionServiceTest`)
- Done: `origin/main` merged into `dev` (20 commits, 0 conflicts) — 2026-05-20
- Done: Test compatibility fixes after merge (3-arg `CreateInstitutionRequest`, `buildInvitationLink` mock, `BackendApplication` conditional beans) — 124 tests passing on `dev`
- Done: UC-1.3 backend + M2 stragglers implemented on `feature/uc13-submission-backend` — 2026-05-21
- Not Started: Write tests for UC-1.3 (`SubmissionServiceTest`, `SubmissionControllerTest`, `UserServiceTest`, `UserControllerTest`)
- Not Started: Merge `feature/uc13-submission-backend` → `main`/`dev` after code review
- Not Started: Push `main` to `origin/main` (pending user approval — 11 commits ahead)
- Not Started: Push `dev` to `origin/dev` (pending user approval — 22+ commits ahead)
- Not Started: Configure real SMTP credentials in `backend/.env`

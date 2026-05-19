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

### M1 Tests
- Done: `BackendApplicationTests`
- Done: `JWTServiceTest`
- Done: `AuditLogServiceTest`
- Done: `TenantScopeServiceTest`

### M2 - Auth & User Provisioning (Chris)
- Done: `AuthController` - `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`
- Done: `InvitationController` - `POST /api/v1/invitations`, `GET /api/v1/invitations/validate`, `POST /api/v1/invitations/accept`
- Done: `PasswordController` - `POST /api/v1/auth/forgot-password`, `POST /api/v1/auth/reset-password`
- Done: `AuthService` - login with lockout check, BCrypt verify, JWT generation, audit log
- Done: `AccountLockoutService` - failed attempt tracking and lockout policy
- Done: `InvitationService` - hashed one-time invitation tokens and user provisioning
- Done: `PasswordService` - anti-enumeration reset token flow
- Done: `JwtUserDetails`, `TokenHashUtils`, `GlobalExceptionHandler`

### M2 Tests
- Done: `AccountLockoutServiceTest`
- Done: `AuthServiceTest`
- Done: `InvitationServiceTest`
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

### M4 Tests
- Done: `InstitutionDtoTest`
- Done: `GuardRailDtoTest`
- Done: `InstitutionServiceTest`
- Done: `GuardRailServiceTest`
- Done: `SlotReservationTest`

### Pending - Backend
- Not Started: UC-1.3 Content Submission - `SubmissionController`, `SubmissionService` (create, edit, submit draft)
- Not Started: UC-2.1 Content Validation - validator review flow
- Not Started: UC-2.2 Media Repository - `MediaAssetController`, Supabase Storage upload, pgvector embeddings
- Not Started: UC-2.3 Notifications - SSE endpoint, `NotificationService`
- Not Started: UC-2.4 Analytics Dashboard - aggregate query endpoints
- Not Started: UC-3.1 Calendar & Auto-publish - `PublishingSchedulerJob`, Facebook Graph API integration
- Not Started: UC-3.2 AI Caption - `ClaudeVisionClient`, async caption generation on upload
- Not Started: UC-3.3 AI Classification & Recommendation - `VoyageAIClient`, cosine similarity search
- Not Started: UC-3.4 Manual Publishing Fallback - `ManualPublishingFallbackController`
- Not Started: UC-3.5 Admin Exception Handling - escalation flow
- Not Started: Flyway V2 migration for upcoming schema changes

---

## Frontend

- Not Started: Login / logout UI
- Not Started: Invitation accept flow
- Not Started: Forgot / reset password pages
- Not Started: Contributor dashboard - submission form, media upload, AI caption display
- Not Started: Validator dashboard - review queue, approve/reject/revise
- Not Started: Admin dashboard - institution management, calendar, analytics
- Not Started: Calendar view with slot selection and guard rail feedback
- Not Started: SSE notification badge
- Not Started: Role-based route protection

---

## Housekeeping

- Done: M1 test branch merged to `main`
- Done: M4 test branch merged to `main`
- In Progress: Push docs/handoff updates on `docs-and-skills`
- In Progress: Local warning/import cleanup in test files is stashed while docs are being pushed
  - `AuthControllerTest.java`
  - `AuthServiceTest.java`
  - `InvitationServiceTest.java`
  - `InstitutionServiceTest.java`

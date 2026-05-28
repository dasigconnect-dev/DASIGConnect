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
- Done: save draft / submit-for-review flow verified end-to-end in the browser. 500 on submit resolved — see Flyway + notification fixes in the Backend Verification section.
- Deferred: `POST /api/v1/submissions/{id}/override-request`; implement in Module 3 with override request migration/service.

### UC-3.1 - Publishing Pipeline & Master Calendar (NEW)

- Done: Flyway V12 migration - `publication_attempts` and `facebook_page_tokens` tables.
- Done: `PublicationAttempt` entity - maps publish attempt records (attempt number, result, error detail, staged photo IDs).
- Done: `FacebookPageToken` entity - maps encrypted DB-backed token store.
- Done: `PublicationAttemptRepository` - `findTopBySubmissionIdOrderByAttemptedAtDesc`, `countBySubmissionId`.
- Done: `FacebookPageTokenRepository` - `findByPageIdAndIsActiveTrue`.
- Done: `TokenEncryptionService` - AES-256-GCM token encryption; key derived from SHA-256 of app secret; IV stored as `iv_b64:ciphertext_b64`.
- Done: `SubmissionRepository` additions - `findScheduledInPublishWindow`, `findMissedScheduledSubmissions`, `findPublishFailures`, `findAllWithScheduledSlot`, `findWithScheduledSlotByInstitution`, `findAbandonedManualPublishes`.
- Done: `CalendarEventDto` - role-scoped DTO; admin sees full detail, contributors/validators see masked title for other institutions.
- Done: `CalendarService` - `getCalendarEvents(JwtUserDetails)` with admin vs scoped calendar logic.
- Done: `CalendarController` - `GET /api/v1/calendar` (authenticated), `PATCH /api/v1/submissions/{id}/reschedule` (admin only).
- Done: `RescheduleRequestDto` - `scheduledAt` + optional `overrideReason` for hard guard rail bypass.
- Done: `SubmissionService.reschedule()` - guard rail validation, hard violation override with audit log, slot re-lock, `SubmissionRescheduledEvent`.
- Done: `SlotReservationService.reserveLockedSlot()` - direct locked reservation for admin reschedule (bypasses held→locked flow).
- Done: `FacebookPublisherService` - Java HttpClient Graph API client matching `SupabaseStorageService` pattern.
  - `@PostConstruct` token sync from env to DB on startup.
  - 2-step photo publish: stage unpublished photos → POST to feed with attached_media.
  - Single-call video publish via `/{PAGE_ID}/videos`.
  - Mixed media → immediate PUBLISH_FAILED (pilot SRS constraint).
  - GR-T5 retry: 3 attempts, backoff 5s → 25s → 125s.
  - `recordAttempt()` writes `PublicationAttempt` row after each attempt.
  - `validateToken()` calls `debug_token` Graph API endpoint; updates `last_validated_at` in DB.
  - DB transaction committed before HTTP calls (respects HikariCP 5-connection limit).
- Done: `PublishingSchedulerJob` - GR-T5, runs every minute; read transaction to load due submissions, then publish outside transaction.
- Done: `StaleSubmissionDetectorJob` - GR-T9, runs every 5 minutes; missed publish windows → PUBLISH_FAILED + `PublishFailedEvent`.
- Done: `TokenHealthCheckJob` - GR-T4, runs daily at 08:00 UTC; invalid token → `TokenValidationFailedEvent`; expiry within 7 days → `TokenExpiryWarningEvent`.
- Done: `FailedPublicationDto` - Resolution Center response DTO with last attempt detail and manual publish in-progress flag.
- Done: `ManualPublishCompleteDto` - request DTO for manual publish completion (optional post URL + notes).
- Done: `ManualPublishingService` - UC-3.4 start/complete/cancel/retry/clearAbandoned; audit log written on complete with priorStatus, scheduledAt, publishedAt; cancel writes MANUAL_PUBLISH_CANCELLED; clearAbandoned writes MANUAL_PUBLISH_ABANDONED with startedAt/abandonedAt and sets lastManualPublishAbandonedAt.
- Done: `ResolutionController` - admin-only endpoints under `/api/v1/resolution`: list failures, `GET /{id}` detail, retry, manual-publish start/complete/cancel.
- Done: `ManualPublishDetailDto` - full post content DTO for the manual publish panel (caption, media assets sorted by displayOrder, scheduled time, contributor info, institution, in-progress flag, abandonment timestamp).
- Done: `AbandonmentDetectorJob` (in `schedule/` package) - runs every 5 minutes; clears manual publish sessions open longer than 2 hours.
- Done: Flyway V19 - adds `last_manual_publish_abandoned_at TIMESTAMPTZ` column to `submissions` for A2 abandonment note.
- Done: `Submission` entity - `lastManualPublishAbandonedAt` field for tracking most recent 2-hour abandonment.
- Done: `FailedPublicationDto` - exposes `lastManualPublishAbandonedAt`.
- Done: Tests - `ManualPublishingServiceTest` (13), `ResolutionControllerTest` (12), `CaptionControllerTest` (9), `CaptionGenerationServiceTest` (9).
- Not Started: UC-3.5 Administrator Exception Handling - `ExceptionHandlingController`, `ValidationTimeoutService`, `OverrideRequestService`, `DirectPostService`, `TokenManagementService`, `OverrideRequest` entity, Flyway V20 `override_requests` table.
- Done: `application.properties` - `app.facebook.*` properties wired from env vars.
- Done: all 208 backend tests pass after UC-3.1 implementation (no regressions).

### Backend Verification

- Done: backend test suite passed with 163 tests (pre-UC-3.1 baseline).
- Done: backend test suite passed with 208 tests after UC-3.1 implementation.
- Done: Flyway duplicate migration version conflict fixed; source and generated migrations now use unique versions `V1`, `V2`, `V3`, and `V4`.
- Done: `mvn clean` and `mvn -DskipTests package` passed after the migration rename.
- Done: fresh Supabase database startup fixed by baselining Flyway at version `0`; V1 through V4 apply correctly on a new Supabase `public` schema.
- Done: backend starts successfully against the updated Supabase database on a temporary port after migrations are applied.
- Done: Flyway mixed-history fix (2026-05-26) — V6–V9 removed from codebase (never applied); recreated as V14–V17; V17 drops and recreates `asset_tags` to fix column-name mismatch with `AssetTag` entity; `BackendApplication.flyway()` now calls `repair()` + `migrate()` explicitly; V10 checksum repaired; V11–V17 applied cleanly.
- Done: `SubmissionService.submit()` 500 fix — T1 notification wrapped in try-catch so a missing `notifications` table never rolls back the PENDING status transition.
- Done: `GlobalExceptionHandler` — added `SlotAlreadyTakenException` handler returning 409 Conflict instead of 500.
- Done: live Facebook photo publish confirmed end-to-end — post published to DasigConnect Facebook Page from a scheduled submission.
- Note: `.\mvnw.cmd` fails in the current PowerShell environment; use `mvnw` (bash) or direct Maven from `.m2/wrapper/dists`.

### Pending - Backend

- Done: UC-2.1 Content Validation — `ValidationController` (review queue, lock acquire/release, approve, request revision, reject), `ValidationService` (state machine transitions PENDING→APPROVED/NEEDS_REVISION/REJECTED with event publishing), `ReviewLockService` (15-min lock acquire/release/ownership check, expiry cleanup), `ReviewLock` + `ValidationLog` + `ValidationAction` entities, `ReviewLockRepository` + `ValidationLogRepository`, `ReviewLockCleanupJob` (expired lock sweep), all 4 DTOs, V10 Flyway migration (`validation_logs` + `review_locks` tables).
- Done: UC-2.2 Media Repository backend - `MediaAssetController` (`GET/GET{id}/DELETE /api/v1/media-assets`, `POST /upload-url`, `POST /upload`, `POST /{id}/use-in-new-post`, `POST /{id}/add-to-draft`, tag endpoints, `POST /bulk-delete`), `MediaAssetService`, `SupabaseStorageService` signed upload URLs.
- Done locally: UC-2.2 Media Repository — soft-delete retention lifecycle (`Active → Soft Deleted → 30-day retention → Purged`). V23 migration adds `deleted_by_user_id` and `purged_at`. `MediaAssetService.delete()` + `bulkDelete()` record the deleting user. `MediaAssetRetentionService` purges expired assets (clears embedding, AI fields, asset tags, Supabase object). `MediaAssetRetentionPurgeJob` runs daily at 02:30 UTC. Config: `MEDIA_ASSET_DELETED_RETENTION_DAYS` (30), `MEDIA_ASSET_PURGE_BATCH_SIZE` (25), `MEDIA_ASSET_PURGE_CRON`. Tests: `MediaAssetRetentionServiceTest`, `MediaAssetServiceTest`.
- Done: Submission content-completeness validation - `SubmissionService.submit()` now rejects with 422 when event title, event date, caption, or ≥1 media asset is missing (always enforced, independent of `app.guardrails.enforced`). Tests: `submit_withoutMedia_returns422`, `submit_withoutCaption_returns422`.
- Done: `GlobalExceptionHandler` handles `HttpRequestMethodNotSupportedException` (405) and `IllegalStateException` (502) so storage failures surface clearly.
- Done: UC-2.3 Notifications — `NotificationService` (SSE emitter registry, list/unread/history/markRead/markAllRead), `NotificationController` (`GET /notifications`, `/unread-count`, `/history`, `PATCH /{id}/read`, `/read-all`, SSE `GET /stream`), `NotificationEventListener` (triggers T2–T17 via Spring events, `REQUIRES_NEW` propagation so failures never roll back business actions), `ValidationDeadlineNotificationJob` (T8 — urgent 30-min deadline alerts with dedup), V14 Flyway migration (`notifications` table + RLS policy), `EmailDeliveryService` (plain-text delivery with `email_delivery_log` tracking), V15 Flyway migration (`email_delivery_log`). Backend event classes: `SubmissionApprovedEvent`, `RevisionRequestedEvent`, `SubmissionRejectedEvent`, `PostPublishedEvent`, `PostPublishedManualEvent`, `PublishFailedEvent`, `OverrideApprovedEvent`, `OverrideDeniedEvent`, `OverrideSlotSuggestedEvent`, `AdminDirectPostEvent`, `TokenExpiryWarningEvent`, `TokenValidationFailedEvent`, `InstitutionNoValidatorEvent`, `InstitutionOnboardedEvent`, `SubmissionRescheduledEvent`.
- Done locally: UC-2.4 Analytics Dashboard backend - `AnalyticsController`, `MetricsAggregatorService`, `AnalyticsRepository`, and analytics DTOs now expose role-scoped summary/report/export endpoints under `/api/v1/analytics`. Contributor scope is own submissions only; validator scope is their institution; administrator scope is network-wide by default with optional `institutionId` filter. Operational health remains administrator-only.
- Done locally: UC-2.4 Analytics role content - contributor analytics include own submitted/published posts, posting delay, completeness, revision/requested-changes, rejected/needs-revision rate, AI caption usage/acceptance, tag correction rate, top categories, and status breakdown. Validator analytics include institution workload, validation turnaround, contributor breakdown, missing requirements, queue aging, institution AI performance, and status/completeness signals. Admin analytics include network KPIs, institution comparison/filter, workflow posts, admin direct posts, publishing success, validation timeout risk, override rate, admin workload, Facebook/API failures, cross-institution trends, and network AI performance.
- Done: UC-3.2 AI Caption backend — `ClaudeVisionClient` base64 image encoding, in-memory resize (5 MB limit), Supabase service-role-key auth fallback, smart image curation prompt, `existingCaption` context support with prompt injection defense, 512 max_tokens, 30s timeout. `CaptionRequestDto` + `CaptionGenerationService` + `CaptionController` updated to pass existingCaption.
- Done: UC-3.2 AI Caption backend — `ClaudeVisionClient.buildPrompt()` updated with instruction-vs-draft intent detection: Claude reads the caption field and determines whether it is a user instruction/request (generates captions following that direction) or a draft caption to refine (improves wording, energy, hashtags). No UI change required.
- In Progress: UC-3.3 AI Media Recommendation - `VoyageAIClient`, `AIRecommendationService`, `AIRecommendationController`, media suggestion DTOs, pgvector similarity queries, and `EmbeddingReconciliationJob` are implemented locally. Image uploads also trigger async Claude classification plus Voyage embedding through `AIClassificationService`, but this is backend support for searchable/recommendable media metadata.
- Done locally: UC-3.3 recommendation quality pass - asset embedding text now includes asset code, normalized filename, media type, AI category, AI image description, AI tags, and manual tags where available. Suggestion request text now includes title, caption, category, and tags.
- Done locally: UC-3.3 hybrid ranking - pgvector returns a larger candidate set, then `AIRecommendationService` re-ranks with category, tag, and recency boosts before returning the top 8 media suggestions.
- Done locally: UC-3.3 embedding resilience - media upload now falls back to metadata-only Voyage embeddings if Claude image classification fails, and `EmbeddingReconciliationJob` can embed old/unscanned assets where `embedding IS NULL`.
- Done locally: UC-3.3 recommendation fallback - if Voyage query embedding fails or pgvector returns no candidates, the backend returns metadata-ranked active institution-library assets instead of an empty failure path.
- Done locally: UC-3.3 embedding model switch - `VoyageAIClient` now uses `voyage-4-lite` with explicit `output_dimension=1024`; V21 converts `media_assets.embedding` back to `VECTOR(1024)` and clears incompatible vectors for re-embedding. V19 is already occupied in the connected Supabase DB by `V19__submission_manual_publish_abandoned_at.sql`; V20 was the earlier 512-dimension correction and is retained only as Flyway history before V21.
- Done locally: UC-3.3 richer asset-profile recommendation - Claude classification now produces broad controlled categories, detailed descriptions, and 8-15 controlled tags; AI tags are persisted to `asset_tags`; embeddings include upload month; suggestion query context includes already-attached media metadata; match reasons explain semantic, category, tag, asset-detail, profile-quality, and recency signals.
- Done locally: UC-3.3 lightweight AI provenance - V22 adds media AI classification/embedding timestamps and model names plus `asset_tags.source`. Manual tags and AI-generated tags are now distinguished in storage, API detail DTOs, recommendation weighting, and match reasons.
- Done locally: UC-3.3 AI media pipeline documentation - current design is upload metadata -> Claude Vision asset enrichment -> Voyage embedding/vectorization -> pgvector semantic search -> deterministic reranking -> hover/focus match reasons. This keeps suggestion-time token use low because images are scanned once at upload/reconciliation time.
- Deferred / Cut from current scope: contributor-facing category/tag AI suggestion. Do not continue the `useAiClassification` / `AiClassificationButton` / `AiClassificationSuggestion` path or add `/api/v1/ai/submissions/{id}/suggestions` unless scope changes; media suggestion has higher impact for UC-3.3.
- Done locally: UC-3.5 Administrator Exception Handling backend — cherry-picked from `feat/uc35-exception-handling` into `module3`. All files present: `ExceptionHandlingController`, 8 DTOs under `model/dto/exception/`, `OverrideRequest`/`OverrideRequestDecision` entities, `OverrideRequestRepository`, `ExpiredOverrideCleanupJob`, `DirectPostService`, `OverrideRequestService`, `TokenManagementService`, `ValidationTimeoutService`, `SubmissionStatus` new enum values (`direct_post_scheduled`, `admin_direct_post`, `direct_post_failed`), `V24__override_requests.sql` migration. V24 not yet applied to Supabase — backend restart required.
- Done locally: UC-3.5 Administrator Exception Handling frontend — 5-tab hub at `/admin/resolution`: `ValidationTimeoutTab` (Cat. B timeouts: Approve/Defer/Reject, UrgencyPill), `OverrideRequestsTab` (Cat. C: Approve/Suggest/Deny, active/expired split, repeated-request flag), `DirectPostTab` (Cat. D: institution select, caption counter, immediate/schedule toggle, GR-H1 ack, live FB preview), `SystemAuditTab` (Cat. E: token table, OAuth re-auth, audit log placeholder). `RejectOnBehalfModal` (shared modal for timeout taxonomy + override free-text), `SlotSuggestionModal`, `useResolutionCounts` (60s polling badge hook), `adminApi` axios instance. `ResolutionCenterScreen` rewritten as tab shell. `resolution.css` (~350 lines). Build: 228 modules, 0 TS errors, ESLint clean on all 15 files.

---

## Frontend

### UC-1.1 / UC-1.2 Auth & Onboarding

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

- Done: submission list wired to `GET /api/v1/submissions`.
- Done: draft creation wired to `POST /api/v1/submissions`.
- Done: save/update wired to `PATCH /api/v1/submissions/{id}`.
- Done: submit wired to `POST /api/v1/submissions/{id}/submit`.
- Done: delete wired to `DELETE /api/v1/submissions/{id}`.
- Done: guard rail panel wired to `POST /api/v1/submissions/{id}/evaluate-slot`.
- Done: media upload now follows the backend contract: direct Supabase Storage upload, then metadata attach to `POST /api/v1/submissions/{id}/media`.
- Done: media order can now be updated through `PATCH /api/v1/submissions/{id}/media/order`; backend persists `submission_media_assets.display_order` for editable submissions.
- Done: backend lookup shape wired for allowed file types, file size, media count, and schedule windows.
- Done locally: `.jpg` uploads are normalized to backend-supported `jpeg`.
- Done locally: submission queue has been restyled into a clearer left-side queue panel with count, stronger item containers, active state, and improved spacing.
- Done locally: submit buttons are not disabled by readiness score during testing.
- Done: save draft and submit-for-review browser flow verified end-to-end against the active backend/Supabase environment (2026-05-26).
- Done: Facebook Preview on Submit Content is dynamic and componentized; it uses live form/draft media, caption, page fallback, and schedule data with empty/error-safe states.
- Done: Facebook Preview card is clickable and opens a responsive preview/review modal with larger post preview, submission details, missing-field messaging, and Save Draft / Submit for Review / Edit Details actions wired to existing handlers.
- Done: Facebook Preview media supports carousel review in compact and modal preview; modal includes draggable thumbnail reordering plus keyboard-friendly move controls.
- Done: reordered media updates the actual submission order. Local files are uploaded in reordered sequence, saved draft media persists through `PATCH /api/v1/submissions/{id}/media/order`, and the first reordered item becomes the first Facebook Preview media.

### Dashboard

- Done: stat tiles use live endpoints where available: submissions, institutions, user counts, and pending invitation counts.
- Done: invite/manage modal lists pending invitations and current users for the selected institution.
- Done: pending invitations can be resent from the invite/manage modal.
- Done: current user state hydrates from `GET /api/v1/me` after login, invite accept, session relogin, and saved-token restore.

### UC-3.1 Frontend - Calendar & Resolution Center

- Done: Calendar route `/scheduler/calendar` wired through reusable React components and `useCalendarEvents`.
- Done: `frontend/src/api/calendarApi.ts` calls `GET /api/v1/calendar` and maps backend calendar DTOs into FullCalendar events.
- Done: FullCalendar month/week views render live backend events with status coloring, legend, event details modal, loading, empty, error, refresh, and retry states.
- Done: Resolution Center route `/admin/resolution` wired through reusable React components and `useResolutionFailures`.
- Done: `frontend/src/api/resolutionApi.ts` calls `GET /api/v1/resolution/failures` and the action endpoints for retry plus manual publish start/complete/cancel.
- Done: Resolution Center supports action busy states, confirmation modals, success/error toast feedback, list refresh after successful actions, and responsive failure cards.
- Done: frontend code is componentized under `frontend/src/features/calendar`, `frontend/src/features/resolution`, `frontend/src/hooks`, and `frontend/src/api`; no hardcoded calendar events or failure records.
- Done: `ManualPublishWorkflowPanel.tsx` — 3-step modal (Copy Content → Post to Facebook → Record Details). Spec-compliant: Mark as Published disabled when URL invalid (A3), correct error message, scheduled date/contributor/submission ID displayed, video instruction note, A2 abandonment banner when lastManualPublishAbandonedAt is set, live 2-hour countdown timer.
- Done: `resolutionApi.ts` extended with `ManualPublishDetail`, `ManualPublishMediaItem` interfaces and `getResolutionDetail(id)`.
- Done: `useResolutionFailures.ts` extended with `activeDetail`/`detailLoading` state, `openWorkflowPanel()`/`closeWorkflowPanel()`; `handleStartManual` auto-opens panel.
- Done: `ResolutionCenterScreen.tsx` — wired `ManualPublishWorkflowPanel` replacing simple complete modal.
- Done locally: UC-3.5 frontend — 5-tab Resolution Center fully implemented and wired to `ExceptionHandlingController` at `/api/admin/resolution`. See backend section for detail. Build and ESLint clean. V24 migration must be applied (backend restart) before browser E2E testing.

### UC-2.2 Frontend - Media Repository

- Done: Media Repository route `/media-repository` with grid/list, search, sort, tag filters, network-view (admin), detail panel, upload modal, and 3-tier delete.
- Done: multi-select via card click (clicking anywhere on a card toggles selection); selection persists across navigation through the reusable `usePersistentSelection` hook (sessionStorage). Checkbox square remains as a visual indicator only.
- Done: selection review list in the detail panel (view/deselect each). Redundant floating selection action bar (`med-selbar`) removed.
- Done: "New Submission (N)" navigates to `/submissions/new?assetIds=...`; `SubmissionScreen` consumes the param, pre-fills media, and attaches via `POST /submissions/{id}/assets` on save/submit (strips the param after first save).
- Done: "Add to Draft" picker (`AddToDraftModal`) appends selected assets to an existing contributor draft; "Download Original" performs a true blob download.
- Done: sidebar actions restructured — Delete + Download + Add to Draft on row 1 (flex equal-width); + New Submission on row 2 (full-width). Bulk delete wired into the sidebar via `canBulkDelete` / `onRequestBulkDelete` props on `AssetDetailPanel`. Header Delete button removed.
- Done: upload uses XHR with real progress, a 50 MB client guard (raised from 25 MB to match Supabase free-tier limit), and surfaces the Supabase error on failure.
- Gap: mp4 uploads depend on the Supabase `dasigconnect-media` bucket allowing `video/*` MIME types and file-size limit set to 50 MB (dashboard config, not code). Per-type limits (25 MB image / 500 MB video) require Supabase Pro upgrade.

### Pending - Frontend

- Gap: submission queue design improved locally but still needs user/team review against real data and mobile widths.
- Done: save draft / submit-for-review verified end-to-end (2026-05-26).
- Done: categories and tags are confirmed returned by `GET /api/v1/submissions/lookups` and rendered in the submission form — gap was stale.
- Gap: preferred time slots are not returned by lookups (not yet required for current scope).
- Done: UC-2.3 Notifications frontend — `/notifications` route, `NotificationsScreen`, `useNotifications` hook (real-time SSE via fetch+ReadableStream, list fetch, mark-read, mark-all-read, filter tabs, incoming animation with `latestIncomingId`), 7 components (AuditLog, BellWidget, DeliveryChannels, FilterTabs, NotificationItem, NotificationList, SseStatusBar), `notificationApi.ts`, `notifications.css`, sidebar notification badge (unread count polled in `DashboardLayout` on mount + window focus), CORS fixed for any Vite dev port.
- Done: UC-2.1 Validation frontend — `ValidationQueueScreen.tsx` (queue panel, review lock acquire/release, approve/revision/reject decision modals, lock ownership UX), `useValidationQueue.ts` hook, `validationApi.ts`, `validation.css`, route wired in `App.tsx` and sidebar nav in `DashboardShell`.
- Done locally: UC-2.4 Analytics frontend - `/analytics` is wired to live backend summary/report/export data with role-specific rendering. Contributor view hides peer names/leaderboards and network/admin operational data. Validator view shows institution-scoped contributor quality and review workload. Admin view shows network operations with optional institution filter. Full report modal and CSV export pass the same admin institution filter.
- Done: UC-2.4 analytics role view components extracted into dedicated files under `frontend/src/features/analytics/components/`: `RoleMetricPanel.tsx`, `StatusBreakdownPanel.tsx`, `ContentIssuesPanel.tsx`, `CategoryPerformancePanel.tsx`, `ContributorAnalyticsView.tsx`, `ValidatorAnalyticsView.tsx`, `AdminAnalyticsPanel.tsx`. `AnalyticsDashboardPage.tsx` reduced from 356 to ~135 lines as a pure orchestrator. Build and ESLint clean.
- Done: UC-3.2 AI Caption frontend — `useAiCaptionAssist` hook, `AiCaptionButton` inline pill button (idle/loading/error/rate-limited states), `AiCaptionSuggestion` panel (3 variant rows, tone badges, expand-to-edit, Use/Dismiss), caption counter repositioned to bottom-right inside textarea, hover-delete on saved filmstrip assets, `aiApi.ts` validateStatus fix + existingCaption param. Backend restart required to activate Java changes.
- Done locally: Submit Content media picker now includes Upload Files, My Library, and AI Suggestions tabs. The AI Suggestions tab uses saved draft context (event title/caption/tags) to generate ranked media recommendations, supports multi-select, and adds selected assets into the current submission order.
- Done locally: AI Suggestions media picker accents changed from violet to the existing DASIGConnect blue tokens.
- Gap: UC-3.3 media suggestion still needs targeted frontend lint and browser E2E against a restarted backend with `VOYAGE_API_KEY`, `ANTHROPIC_API_KEY`, Flyway V21/V22 applied, and existing embedded assets. Frontend production build and full backend tests pass.
- Gap: Supabase browser upload env is now configured locally, but the full upload flow still needs manual verification through the submission form.

### Frontend Verification

- Done: `npm.cmd run build` passed.
- Done: Submit Content / Facebook Preview build verification passed after dynamic preview, preview modal, carousel, and media reorder implementation.
- Done: targeted ESLint passed for Submit Content / Facebook Preview files: `SubmissionScreen.tsx`, `components/facebook`, `useFacebookPreviewData.ts`, `useMediaReorder.ts`, and `types/facebook.ts`.
- Done: focused backend tests passed for submission controller/service after adding media reorder support: `.\mvnw.cmd "-Dtest=SubmissionServiceTest,SubmissionControllerTest" test` (29 tests passing).
- Done: UC-3.1 frontend build verification passed after Calendar and Resolution Center wiring.
- Done: targeted ESLint passed for new/changed Calendar, Resolution Center, API, and hook files.
- Note: full-project `npm.cmd run lint` still fails due pre-existing lint debt in older files (`App.tsx`, dashboard, submission, validation, user-management, shared toast/button files); not caused by the UC-3.1 frontend slice.
- Done locally: Vite dev server started successfully on `http://127.0.0.1:5176/` because ports 5173-5175 were occupied.
- Local setup: `frontend/.env.local` should contain `VITE_API_URL=http://localhost:8080/api/v1` when using the Vite dev server with the local backend. This file is intentionally ignored by Git.
- Required env vars for browser media upload:
  - `VITE_SUPABASE_URL`
  - `VITE_SUPABASE_STORAGE_BUCKET`
  - `VITE_SUPABASE_ANON_KEY`
- Done locally: `frontend/.env.local` contains the Vite Supabase upload variables and uses the `dasigconnect-media` bucket.
- Done: UC-2.3 Notifications (2026-05-26): `npm.cmd run build` passed (187 modules, 0 TypeScript errors) after completing notification integration — routing, hook, components, sidebar badge, CORS fix. Browser verification pending (needs running backend for SSE stream).
- Done: UC-2.4 Analytics (2026-05-27): focused backend tests passed with `.\mvnw.cmd test "-Dtest=MetricsAggregatorServiceTest,AnalyticsControllerTest"`. Frontend production build passed with `npm.cmd run build`. Targeted analytics ESLint passed with `npx.cmd eslint src/api/analyticsApi.ts src/features/analytics --quiet`.
- Done: UC-3.3 Media Suggestions frontend build (2026-05-27): `npm.cmd run build` passed after the Submit Content media picker and AI Suggestions tab changes (219 modules, 0 TypeScript errors).
- Done: UC-3.3 Media Suggestions backend verification (2026-05-27): `.\mvnw.cmd test` passed after hybrid ranking, richer embedding text, and the `voyage-4-lite`/V21 dimension alignment (220 tests, 0 failures, 0 errors).
- Note: full-project `npm.cmd run lint` still fails due pre-existing lint debt outside analytics (`App.tsx`, dashboard, submission, validation, user-management, shared toast/button files); targeted analytics lint is clean.

---

## Housekeeping

- Done: M1 test branch merged to `main`.
- Done: M4 test branch merged to `main`.
- Done: docs/handoff updates merged to `main` via PRs #13, #15, #16.
- Done: UC-1.3 backend and frontend API wiring completed locally on `feature/uc13-submission-backend`.
- Done: UC-1.3 Flyway migration conflict fixed by renaming media migration to `V4__media_assets.sql`.
- Done: Module 1 auth/onboarding cleanup completed locally: `/me` hydration, pending invite count/list/resend UI, invite token superseding, and user-count tenant scope hardening.
- Done: UC-3.1 publishing pipeline backend implemented on `module3` branch (208 tests passing).
- Done: UC-3.1 frontend wiring completed locally: Calendar page and Resolution Center page connected to verified backend endpoints.
- Done locally: UC-2.4 Analytics backend + frontend implemented on `module3` with role-scoped contributor/validator/admin analytics and admin institution filtering.
- In Progress: merge/code review path for `feature/uc13-submission-backend`.
- In Progress: merge/code review path for `module3` (UC-3.1 backend + frontend).
- Done locally: SMTP credentials are configured in ignored local environment files; deployment runtime still needs team-owned SMTP values.
- Done locally: Supabase database, service-role, frontend upload, and storage bucket env values are configured in ignored env files.
- Not Started: configure Supabase service/browser upload environment variables in deployed environments.
- Done: Spring 7 deprecation warning cleanup — `HttpStatus.UNPROCESSABLE_ENTITY` replaced with `HttpStatusCode.valueOf(422)` in `GlobalExceptionHandler`, `CaptionGenerationService`, `DirectPostService` (7 occurrences); `ResponseEntity.unprocessableEntity()` replaced with `ResponseEntity.status(422)`; unused `AIClassificationService` import removed from `MediaAssetService`; unused `PUBLISHING_SUCCESS_TARGET` removed from `MetricsAggregatorService`; unused `MAX_OVERRIDE_REQUESTS` removed from `OverrideRequestService`. 269 tests passing.

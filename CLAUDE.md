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

## Current Local Status - 2026-05-28 (Session 8)

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

### UC-2.3 Notification Integration (2026-05-26)

- **Backend (fully complete):** `NotificationService` (SSE emitter registry, CRUD, mark-read), `NotificationController` (`GET /notifications`, `/unread-count`, `/history`, `PATCH /{id}/read`, `PATCH /read-all`, SSE `GET /stream`), `NotificationEventListener` (T2–T17 via `@TransactionalEventListener(AFTER_COMMIT)`, each in its own `REQUIRES_NEW` transaction so failures never roll back the triggering business action), `ValidationDeadlineNotificationJob` (T8 — every 5 min, dedup within 30-min window), `EmailDeliveryService` (plain-text with `email_delivery_log`), V14 (`notifications` table + RLS), V15 (`email_delivery_log`). All 15 event record classes under `event/`.
- **Frontend (fully complete):** `/notifications` route wired in `App.tsx`; `DashboardLayout` active-nav mapping; Notifications sidebar nav item in `DashboardShell`; `notificationApi.ts` (REST + fetch-based SSE stream with `Authorization` header); `useNotifications` hook (list fetch, SSE subscription, filter, mark-read/all-read, `latestIncomingId` tracking for incoming animation); 7 components (AuditLog, BellWidget, DeliveryChannels, FilterTabs, NotificationItem, NotificationList, SseStatusBar); `notifications.css`.
- **Sidebar notification badge:** `DashboardLayout` polls `GET /api/v1/notifications/unread-count` on mount and window focus; passes count to `DashboardShell` which renders a red badge on the Notifications nav item when unread > 0.
- **CORS fix:** `SecurityConfig` changed from `setAllowedOrigins` (hardcoded port 5173) to `setAllowedOriginPatterns(localhost:*, 127.0.0.1:*)` so the SSE stream works on any Vite dev port.
- **Simulation artifacts removed:** The module2 prototype had mock SSE events and a `simulateSSE` button; those were stripped. All data flows from the live backend.
- **Verification:** `npm.cmd run build` passed (187 modules, 0 TypeScript errors). Browser SSE verification pending (needs running backend).

### UC-3.2 AI Caption Enhancements (2026-05-26)

- **Backend fixes:** `ClaudeVisionClient` now base64-encodes images (Supabase URLs may be auth-gated). In-memory resize via `BufferedImage`/`Graphics2D`/`ImageIO` handles >5 MB images (scales 70%→50%→35%→25%→15% until JPEG ≤ 5 MB). `java.awt.headless=true` set for Render server compatibility. Supabase service-role-key auth fallback on 401/403. Prompt updated: image curation guidance (deprioritize title/banner slides) + conditional draft refinement via `<context>` tags with injection defense. `max_tokens` 1024→512, timeout 10s→30s.
- **existingCaption flow:** `CaptionRequestDto`, `CaptionGenerationService`, `CaptionController` updated to pass `existingCaption` through to Claude so AI refines existing text rather than replacing it.
- **Frontend redesign:** Removed large AI card; replaced with inline `AiCaptionButton` pill in Caption label row and `AiCaptionSuggestion` panel below textarea. `useAiCaptionAssist` hook owns all AI state. `aiApi.ts` `validateStatus: null` → `() => true` fix.
- **UX polish:** Caption counter repositioned to bottom-right inside textarea. Hover-visible X delete button added to saved filmstrip assets.

### UC-3.2 AI Caption — Intent Detection (2026-05-27)

- **Smart instruction detection:** `ClaudeVisionClient.buildPrompt()` updated so Claude reads the caption field and determines intent before generating: if the text is a directive or question ("make a caption about X", "focus on Y"), Claude treats it as creative direction and generates captions following that request; if it reads like a draft caption, Claude refines it. No UI change required — the caption field already serves both purposes.
- **No auto-display confirmed:** `suggest()` is only triggered by explicit user button click ("✨ Suggest Caption") or the "Regenerate" button inside the panel. No `useEffect` auto-fires suggestions.

### Gap Audit (2026-05-27)

- **`DASIG_SUPABASE_SERVICE_ROLE_KEY` confirmed present** in `backend/.env` and correctly mapped to `app.supabase.service-role-key` in `application.properties` — no code change needed.
- **Categories and tags confirmed working** — `GET /api/v1/submissions/lookups` returns both fields and the submission form renders them. Gap note was stale.
- **Media Library upload guard raised 25 MB → 50 MB** — `MediaRepositoryScreen.tsx` `MAX_UPLOAD_MB` and `UploadModal.tsx` display text updated to align with Supabase free-tier 50 MB global limit. Per-type limits (25 MB image / 500 MB video) require Supabase Pro; code for per-type split was explored but reverted.

### UC-2.4 Role-Scoped Analytics (2026-05-27)

- **Backend implemented locally:** `AnalyticsController`, `MetricsAggregatorService`, `AnalyticsRepository`, and analytics DTOs expose `/api/v1/analytics/summary`, `/report/{metric}`, and `/export/{metric}`. All endpoints accept `range`; admin requests also support optional `institutionId`.
- **Contributor scope:** own submissions only. Shows own submitted/published posts, average posting delay, completeness, revision/requested-changes count, rejected/needs-revision rate, AI caption usage/acceptance, tag correction rate, top categories, and status breakdown. Do not expose peer names, institution leaderboards, operational health, or network publishing data.
- **Validator scope:** institution only. Shows institution submission volume, pending/in-review workload, average validation turnaround, contributor breakdown, missing requirements, queue aging, institution-level AI performance, and status/completeness signals. Do not expose other institutions or admin network health.
- **Admin scope:** network-wide by default with optional institution filter. Shows KPI tiles, institution comparison/filter, workflow posts, admin direct posts, publishing success, validation timeout risk, override rate, admin workload, Facebook/API failures, cross-institution trends, and network AI performance.
- **Frontend implemented locally:** `/analytics` renders role-specific views from live API data. `FullReportModal` and CSV export pass the same admin institution filter.
- **Verification:** `.\mvnw.cmd test "-Dtest=MetricsAggregatorServiceTest,AnalyticsControllerTest"` passed. `npm.cmd run build` passed. `npx.cmd eslint src/api/analyticsApi.ts src/features/analytics --quiet` passed. Full-project `npm.cmd run lint` still fails due unrelated pre-existing lint debt outside analytics.

### UC-2.4 Analytics Component Extraction (2026-05-27 Session 4)

- **All 7 role-view components extracted** from `AnalyticsDashboardPage.tsx` into dedicated files under `frontend/src/features/analytics/components/`: `RoleMetricPanel.tsx`, `StatusBreakdownPanel.tsx`, `ContentIssuesPanel.tsx`, `CategoryPerformancePanel.tsx`, `ContributorAnalyticsView.tsx`, `ValidatorAnalyticsView.tsx`, `AdminAnalyticsPanel.tsx`.
- **`AnalyticsDashboardPage.tsx` reduced** from 356 lines to ~135 lines; now a pure page-level orchestrator.
- **Verification:** `npm.cmd run build` passed (209 modules, 0 TypeScript errors). `npx.cmd eslint src/features/analytics --quiet` passed with zero warnings.

### UC-3.4 Manual Publishing Fallback — Full Implementation (2026-05-27 Session 5)

- **Backend spec gaps closed:** `ManualPublishingService.complete()` audit log extended with `priorStatus`, `scheduledAt`, `publishedAt`; `clearAbandoned()` now sets `lastManualPublishAbandonedAt` on the submission and writes `MANUAL_PUBLISH_ABANDONED` audit log with `startedAt` and `abandonedAt`; `cancel()` writes `MANUAL_PUBLISH_CANCELLED` audit log; `loadEligibleForManualPublish()` accepts both `PUBLISH_FAILED` and `SCHEDULED`.
- **Flyway V19** adds `last_manual_publish_abandoned_at TIMESTAMPTZ` column to `submissions`. `Submission` entity updated with field and accessors. `ManualPublishDetailDto` and `FailedPublicationDto` expose the field.
- **`GET /api/v1/resolution/{id}` detail endpoint** added to `ResolutionController`; returns `ManualPublishDetailDto` with full post content (caption, media assets sorted by display order, scheduled time, contributor name/email, institution, in-progress flag, last abandonment timestamp).
- **Duplicate `job/AbandonmentDetectorJob`** deleted — `schedule/AbandonmentDetectorJob` already existed; duplicate caused `ConflictingBeanDefinitionException` on startup.
- **43 new backend tests added:** `ManualPublishingServiceTest` (13), `ResolutionControllerTest` (12), `CaptionControllerTest` (9), `CaptionGenerationServiceTest` (9) — all passing.
- **`ManualPublishWorkflowPanel.tsx`** — full 3-step modal: Step 1 copy caption + image thumbnails (hover-to-reveal download) + video download with spec-required note; Step 2 Open DASIG Facebook Page; Step 3 Live Post URL (facebook.com validated, button disabled when invalid — A3) + Admin Notes. Live 2-hour countdown timer (amber when < 10 min). A2 abandonment banner shown when `lastManualPublishAbandonedAt` is set. Submission meta row shows scheduled time, contributor name, and submission ID (Step 2 spec requirement).
- **`useResolutionFailures.ts`** extended: `activeDetail`/`detailLoading` state, `openWorkflowPanel()`/`closeWorkflowPanel()`; `handleStartManual` auto-opens workflow panel after API start.
- **Frontend build and targeted ESLint clean** after all changes.

### UC-3.3 Media Suggestions + Submission Media Picker (2026-05-27)

- **Backend media recommendation started:** `AIRecommendationController`, `AIRecommendationService`, `VoyageAIClient`, media suggestion DTOs, and `EmbeddingReconciliationJob` are implemented locally.
- **Recommendation endpoints:** `POST /api/v1/ai/submissions/{id}/suggest-media` embeds title/caption/tags with Voyage AI and returns ranked institution-library media using pgvector; `GET /api/v1/ai/submissions/{id}/similar-media` finds related assets from an attached asset embedding; `POST /api/v1/ai/submissions/{id}/log-interaction` records best-effort AI interaction events.
- **Classification/embedding support pipeline:** `AIClassificationService` runs asynchronously after image upload, calls Claude Vision classification, persists `ai_category`, `ai_confidence`, and `ai_description`, then stores a Voyage embedding. If Claude classification fails, it still stores a metadata-only Voyage embedding from asset code, filename, media type, category/description when present, and tags. Video assets are skipped. This remains backend support for media recommendation/search metadata, not a contributor-facing category suggestion feature.
- **Media library filtering:** `GET /api/v1/media-assets` supports query, AI category, media type, pagination, sort, and admin network scope filters.
- **Submit Content media UX:** `SubmissionScreen` now uses `MediaAssetsPicker` with Upload Files, My Library, and AI Suggestions tabs. The picker includes a selected-media strip, drag/drop reorder, move/remove controls, source badges, reusable asset grid/cards, library search/filter controls, loading/empty/error states, and sticky multi-select add bars.
- **AI Suggestions tab:** Contributors can generate recommendations from a saved draft's title/caption/tags, view ranked media with similarity scores, multi-select results, and add selected assets into the submission media order.
- **Verification:** `npm.cmd run build` passed after the media picker changes (219 modules, 0 TypeScript errors).
- **Scope decision:** contributor-facing category/tag AI suggestion is cut from the current scope because media suggestion has higher impact. Do not continue the `useAiClassification` / `AiClassificationButton` / `AiClassificationSuggestion` path or add `/api/v1/ai/submissions/{id}/suggestions` unless scope changes.
- **Recommendation quality pass:** asset embedding text now includes asset code, normalized filename, media type, AI category, AI image description, AI tags, and manual tags where available. Suggestion requests now include category as well as title/caption/tags.
- **Hybrid ranking:** `suggest-media` retrieves a wider pgvector candidate set and re-ranks with category, tag, and recency boosts before returning top suggestions. This keeps suggestion-time fast because image scanning still happens only once at upload/reconciliation time.
- **Fallback pass:** `EmbeddingReconciliationJob` now embeds old/unscanned assets where `embedding IS NULL`, and media suggestions fall back to metadata-ranked active assets when Voyage query embedding or pgvector candidates are unavailable.
- **Embedding dimension fix:** `VoyageAIClient` now uses `voyage-4-lite` with explicit `output_dimension=1024`, validates returned vector length, and stores vectors into the pgvector `VECTOR(1024)` column. V21 (`V21__media_embedding_1024_dimensions.sql`) restores `media_assets.embedding` to `VECTOR(1024)` after the earlier V20 512-dimension correction.
- **Richer asset-profile recommendation:** Claude classification now asks for one broad controlled category, a detailed factual description, and 8-15 controlled tags. `AIClassificationService` persists AI tags into `asset_tags`, embeddings include upload month, and `AIRecommendationService` uses already-attached media as additional query context. Match reasons now explain combined signals: semantic similarity, category, shared tags, asset-detail terms, rich profile, and recency as a tie-breaker.
- **Lightweight AI provenance:** V22 (`V22__media_ai_provenance.sql`) adds `ai_classified_at`, `ai_classification_model`, `embedding_generated_at`, `embedding_model`, and `asset_tags.source`. User-created tags are `manual`, Claude tags are `ai_generated`, and manual tag matches receive stronger recommendation weight than AI-detected tags.
- **Pipeline summary:** AI media suggestion is implemented as asset enrichment plus semantic indexing. Upload stores base metadata; Claude Vision enriches the image with category, description, tags, and confidence; Voyage converts the enriched asset profile to a `voyage-4-lite` 1024-dimensional embedding; pgvector performs semantic similarity search when suggestions are requested; backend re-ranks and returns explainable match reasons without rescanning images on every suggestion request.
- **UI polish:** AI media suggestion accents in `media-picker.css` use DASIGConnect blue tokens instead of violet.
- **Verification:** full backend `.\mvnw.cmd test` passed with 220 tests. Frontend `npm.cmd run build` passed.

### UC-2.2 Media Asset Retention Policy (2026-05-28)

- **Flyway V23** adds `deleted_by_user_id UUID`, `purged_at TIMESTAMPTZ`, and a retention lookup index to `media_assets`.
- **`MediaAssetService.delete()` and `bulkDelete()`** now record `deleted_by_user_id` (acting user UUID) at soft-delete time.
- **`MediaAssetRetentionService`** purges soft-deleted assets after the configurable retention window expires. Purge clears: pgvector embedding, embedding model/timestamp, AI category/description/confidence, AI classification model/timestamp, DB asset tags, and the Supabase storage object (best-effort).
- **`MediaAssetRetentionPurgeJob`** runs daily at 02:30 UTC by default, processes up to `MEDIA_ASSET_PURGE_BATCH_SIZE` (25) assets per run.
- **Config keys:** `MEDIA_ASSET_DELETED_RETENTION_DAYS` (default 30), `MEDIA_ASSET_PURGE_BATCH_SIZE` (default 25), `MEDIA_ASSET_PURGE_CRON` (default `0 30 2 * * *`).
- **`MediaAssetBulkDeleteRequestDto`** + **`MediaAssetBulkDeleteResponseDto`** added for the bulk delete endpoint.
- **Tests:** `MediaAssetRetentionServiceTest` (new), `MediaAssetServiceTest` (new), `MediaAssetControllerTest` (updated).

### UC-2.2 Media Repository UI Cleanup (2026-05-28)

- **Removed redundant floating action bar** (`med-selbar` pill at bottom of page) — all selected-asset actions are now in the right-side detail panel.
- **Removed header Delete button** — the `Delete N` danger button that appeared in the page header when assets were checked is gone.
- **Sidebar actions restructured:** Row 1 = `[Delete] [Download] [Add to Draft]` (flex equal-width); Row 2 = `[+ New Submission (N)]` (full-width). Bulk delete is wired into the sidebar via `canBulkDelete` + `onRequestBulkDelete` props on `AssetDetailPanel`.
- **Backend deletion authorization verified correct** — `loadAssetForDelete` enforces Admin = unrestricted, Validator = institution-scoped, Contributor = own uploads by UUID. No backend changes needed.
- **CSS cleanup:** removed `.med-selbar` (~60 lines), `.med-grid.selecting padding-bottom`, `.med-delete-section`, `.med-delete-label`, `.med-delete-triggers`.

### Asset Card Click-to-Select (2026-05-28)

- **Clicking anywhere on a card now toggles selection** — previously required clicking the small checkbox square in the corner.
- **Checkbox square kept as visual indicator** — converted from interactive `<button>` to `<span aria-hidden="true">`. Appears on hover and stays visible when checked.
- **`onToggleCheck` prop removed** from `AssetCard` — the card's `onClick` directly calls `handleToggleCheck(asset)`.
- **Verification:** frontend `npm.cmd run build` passed (219 modules, 0 TypeScript errors). Targeted ESLint clean on `MediaRepositoryScreen.tsx` and `AssetDetailPanel.tsx`.

### Spring 7 Deprecation Warning Cleanup (2026-05-28)

- **`GlobalExceptionHandler.java`** — `ResponseEntity.unprocessableEntity()` replaced with `ResponseEntity.status(422)`.
- **`CaptionGenerationService.java`** — `HttpStatus.UNPROCESSABLE_ENTITY` replaced with `HttpStatusCode.valueOf(422)`; `HttpStatusCode` import added.
- **`DirectPostService.java`** — all 7 `HttpStatus.UNPROCESSABLE_ENTITY` occurrences replaced with `HttpStatusCode.valueOf(422)`; `HttpStatusCode` import added.
- **`MediaAssetService.java`** — unused `AIClassificationService` import removed.
- **`MetricsAggregatorService.java`** — unused `PUBLISHING_SUCCESS_TARGET = 95.0` constant removed.
- **`OverrideRequestService.java`** — unused `MAX_OVERRIDE_REQUESTS = 2` constant removed; IDE linter also auto-applied `UNPROCESSABLE_CONTENT` fix in `suggest()`.
- **`CaptionGenerationServiceTest.java`** — two 422 assertions updated to `.value() == 422` to match Spring 7 renamed reason phrase.
- **Verification:** 269 backend tests passing (0 failures, 0 errors).

### UC-3.5 Administrator Exception Handling — Backend Confirmed (2026-05-28)

- All 12 backend files cherry-picked from `feat/uc35-exception-handling` verified present on `module3`: `ExceptionHandlingController`, 8 DTOs (`DirectPostRequestDto`, `DirectPostResponseDto`, `OAuthInitResponseDto`, `OverrideDenyRequestDto`, `OverrideRequestDto`, `OverrideSuggestRequestDto`, `ResolutionCountsDto`, `TimeoutEscalationDto`, `TimeoutRejectRequestDto`, `TokenStatusDto`), `OverrideRequest`/`OverrideRequestDecision` entities, `OverrideRequestRepository`, `ExpiredOverrideCleanupJob`, `DirectPostService`, `OverrideRequestService`, `TokenManagementService`, `ValidationTimeoutService`, new `SubmissionStatus` enum values.
- **`V24__override_requests.sql`** migration present; not yet applied to Supabase — requires backend restart.

### UC-3.5 Administrator Exception Handling — Frontend (2026-05-28, Session 8)

- **Additional Java deprecation cleanup:** `HttpStatus.UNPROCESSABLE_ENTITY` replaced with `HttpStatusCode.valueOf(422)` in `SubmissionService` (2×) and `ValidationService` (3×). `SubmissionControllerTest` 422 assertion fixed to `status().is(422)`. `@SuppressWarnings("unused")` added to `OverrideRequestService.submissionRepository`.
- **`adminApi` axios instance** added to `authApi.ts` — base URL strips `/v1` from `VITE_API_URL`; `setAuthToken` updates both `api` and `adminApi` so UC-3.5 endpoints at `/api/admin/resolution/*` share the same Bearer token.
- **`resolutionApi.ts`** extended with all UC-3.5 types (`ResolutionCounts`, `TimeoutEscalation`, `OverrideRequest`, `TokenStatus`, `DirectPostPayload`, etc.) and API functions using `adminApi`.
- **`useResolutionCounts.ts`** — 60s polling hook drives red count badges on each tab.
- **`RejectOnBehalfModal.tsx`** — shared modal for timeout reject (6-code taxonomy) and override deny (free-text); `mode` prop controls behavior.
- **`SlotSuggestionModal.tsx`** — Cat. C alternative slot picker; `minDatetime` set via `useState` lazy initializer to satisfy `react-hooks/purity`.
- **`ValidationTimeoutTab.tsx`** — Cat. B escalations table with Approve / Defer / Reject actions and `UrgencyPill` (red/amber/green countdown).
- **`OverrideRequestsTab.tsx`** — Cat. C requests split into active/expired sections; Approve / Suggest / Deny; `rc-repeat-flag` badge when `overrideRequestCount >= 2`.
- **`DirectPostTab.tsx`** — Cat. D direct post form: institution select, caption counter (80–280), immediate/schedule toggle, reason (20 min), GR-H1 ack checkbox, live Facebook preview panel; submits to `POST /admin/resolution/direct-post`.
- **`SystemAuditTab.tsx`** — Cat. E token management table (`TokenStatusBadge` ACTIVE/EXPIRING/EXPIRED/INVALID), Re-Authenticate OAuth flow (`initOAuth` → new tab), audit log placeholder section.
- **`ResolutionCenterScreen.tsx`** rewritten as 5-tab hub with icon+label tabs, per-tab red count badges, `refreshSignal`, and `tokenSectionRef` scroll targeting.
- **`resolution.css`** — ~350-line stylesheet: tab bar, table variants, action button variants, urgency pills, modal styles, toggle groups, Direct Post grid, token badge variants.
- **Commit `ea3ac10`** — 15 files, 2263 insertions. Build: 228 modules, 0 TypeScript errors. ESLint clean on all 15 files.

### Known Gaps

- UC-3.1 / UC-3.4 frontend browser action testing still needs an authenticated admin session and active backend to manually exercise retry, manual publish start, complete, cancel, and the full 3-step workflow panel against live data.
- `ManualPublishDetail` TypeScript interface is missing `lastManualPublishAbandonedAt: string | null` — linter stripped it. Re-add before browser testing UC-3.4's abandonment banner.
- UC-3.5 frontend is fully implemented (build + lint clean). V24 migration not yet applied to Supabase — backend restart required before browser E2E testing of UC-3.5 endpoints (validation timeouts, override requests, direct post, token re-auth).
- Submission queue design needs review with real data and mobile widths.
- Submission lookups do not return preferred time slots (not yet required for current scope). Categories and tags are working.
- UC-2.2 Media Repository: mp4 uploads require Supabase `dasigconnect-media` bucket to allow `video/*` MIME types and max file size set to 50 MB in dashboard settings.
- UC-2.2 Media Repository retention: V23 migration has not yet been applied to the Supabase database — restart the backend to trigger Flyway before the retention purge job can run.
- UC-2.4 Analytics component extraction is done. Browser review with contributor, validator, and admin accounts against a running backend is still needed.
- UC-3.2 AI caption requires a backend restart to activate all Java changes. `DASIG_SUPABASE_SERVICE_ROLE_KEY` is already wired. Browser end-to-end test pending (test both instruction path and draft-refine path).
- UC-3.3 media suggestion still needs targeted frontend lint and browser E2E with `VOYAGE_API_KEY`, `ANTHROPIC_API_KEY`, Flyway V21/V22 applied, and media assets that have embeddings.
- Category/tag AI suggestion is intentionally cut from the current scope; stale prototype files may exist locally, but should not be treated as a required gap.
- No built-in validator account exists. Use the administrator dashboard to invite validators.
- Backend deployment runtime still needs team-owned SMTP credentials configured on Render.
- Per-type file size limits (images 25 MB / videos 500 MB) require Supabase Pro upgrade — currently capped at 50 MB for all file types.

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
| `MediaAsset`           | Supabase-backed media metadata; pgvector `VECTOR(1024)` embedding is not Hibernate-mapped |
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
| `module3`                         | Done locally, not merged | UC-3.1 backend + frontend: publishing pipeline, calendar API/UI, Facebook Graph API integration, token encryption, scheduler jobs, Resolution Center backend/UI (UC-3.4), dynamic Facebook Preview modal, media carousel, and persisted submission media reordering. UC-2.2 Media Repository: backend + frontend fully implemented. Soft-delete retention lifecycle added (V23 migration, `MediaAssetRetentionService`, `MediaAssetRetentionPurgeJob`, 30-day purge window). UC-2.3 Notifications: backend (SSE, T1-T17, deadline job, email log) + frontend (route, hook, components, sidebar badge, CORS fix) fully implemented. UC-2.4 Analytics backend + frontend implemented with role-scoped contributor/validator/admin analytics, admin institution filtering, full reports, and CSV export. UC-3.2 AI Caption enhanced: base64 images, 5 MB resize, existingCaption context, inline button redesign (`AiCaptionButton`, `AiCaptionSuggestion`, `useAiCaptionAssist`), caption counter bottom-right, hover-delete on filmstrip assets. `buildPrompt()` updated with instruction-vs-draft intent detection so Claude follows user directives typed in the caption field. UC-3.3 media recommendation is in progress: `voyage-4-lite` embeddings with explicit 1024 dimensions, async image classification/embedding support pipeline, pgvector media suggestion endpoints, Submit Content media picker with AI Suggestions tab, blue AI suggestion accents, and explainable match reasons. Category/tag AI suggestion is cut from current scope. UC-3.4 Manual Publishing Fallback fully spec-compliant: `ManualPublishWorkflowPanel` 3-step workflow, V19 migration, abandonment banner, URL validation disabling confirm, audit log completeness, and 43 new backend tests passing. Media Repository UI: redundant floating action bar removed; sidebar actions restructured (Delete/Download/Add to Draft on row 1, New Submission on row 2); card click now toggles selection directly. Session 7: Spring 7 deprecation warnings cleaned up across 5 service files + 1 test (269 tests passing); UC-3.5 backend cherry-pick confirmed complete (V24 migration, 12 new files including `ExceptionHandlingController`, `DirectPostService`, `OverrideRequestService`, `TokenManagementService`, `ValidationTimeoutService`); unused constants removed from `MetricsAggregatorService` and `OverrideRequestService`. Session 8: UC-3.5 Administrator Exception Handling frontend fully implemented — `ResolutionCenterScreen` rewritten as 5-tab hub (`ValidationTimeoutTab`, `OverrideRequestsTab`, `DirectPostTab`, `SystemAuditTab`, `RejectOnBehalfModal`, `SlotSuggestionModal`); `adminApi` instance added to `authApi.ts`; `useResolutionCounts` 60s polling hook; `resolution.css` stylesheet; additional Java deprecation fixes in `SubmissionService` + `ValidationService` + `OverrideRequestService`. Build: 228 modules, 0 TS errors, ESLint clean. |
| UC-2.1                            | Done locally, not merged | Content validation — `ValidationController`, `ValidationService`, `ReviewLockService`, `ReviewLockCleanupJob`, entities (`ReviewLock`, `ValidationLog`, `ValidationAction`), V10 migration, frontend `ValidationQueueScreen` + `useValidationQueue` + `validationApi` |
| UC-2.4                            | Done locally, needs browser test | Analytics Dashboard — role-scoped backend summary/report/export endpoints plus frontend role views. All 7 role-view components extracted into dedicated files (`ContributorAnalyticsView`, `ValidatorAnalyticsView`, `AdminAnalyticsPanel`, `RoleMetricPanel`, `StatusBreakdownPanel`, `ContentIssuesPanel`, `CategoryPerformancePanel`). Remaining gap: browser-test contributor/validator/admin accounts against live backend. |
| UC-3.2 / UC-3.3                   | In progress              | AI Caption is implemented locally. UC-3.3 media recommendation is partially implemented with `voyage-4-lite` 1024-dimensional embeddings, pgvector search, and Submit Content AI Suggestions. Category/tag AI suggestion is intentionally cut from current scope. |

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

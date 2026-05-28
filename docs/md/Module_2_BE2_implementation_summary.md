# Module 2 — BE-2 Branch Implementation Summary

Branch: `feature/be2-media-notifications`  
Date: 2026-05-24  
Status: Complete, not yet merged. 207 tests passing.

---

## What Was Built

### UC-2.2 — Media Asset Repository

Four gaps from the original stub were closed.

#### 1. "Used In" List

`MediaAssetDetailDto` now includes a `usedIn` list showing every submission an asset appears in — event title, status, submitted date, and deep link.

#### 2. Admin Network-View Scope

`GET /api/v1/media-assets?scope=network` returns assets across all institutions for administrators. Contributors and validators are always scoped to their own institution.

#### 3. Direct-to-Library Upload

`POST /api/v1/media-assets/upload` lets any authenticated user register a Supabase-uploaded file directly into the media library without attaching it to a submission first.

#### 4. Custom Tags

Tags can be added and removed per asset:

- `POST /api/v1/media-assets/{id}/tags` — adds a tag (deduped by label, 409 if duplicate)
- `DELETE /api/v1/media-assets/{id}/tags/{tagId}` — removes a tag

New `asset_tags` table (Flyway V9) with `UNIQUE(media_asset_id, label)` and RLS.

#### Full Endpoint Surface — `/api/v1/media-assets`

| Method   | Path                    | Auth          | Description                                                           |
| -------- | ----------------------- | ------------- | --------------------------------------------------------------------- |
| `GET`    | `/`                     | authenticated | List/search assets (query, aiCategory, uploaderId, sort, page, scope) |
| `GET`    | `/{id}`                 | authenticated | Asset detail with usedIn and tags                                     |
| `POST`   | `/upload`               | authenticated | Register a Supabase-uploaded file into the library                    |
| `POST`   | `/{id}/use-in-new-post` | contributor   | Create a new draft submission pre-attached with this asset            |
| `POST`   | `/{id}/add-to-draft`    | contributor   | Attach asset to an existing draft submission                          |
| `POST`   | `/{id}/tags`            | authenticated | Add a custom tag                                                      |
| `DELETE` | `/{id}/tags/{tagId}`    | authenticated | Remove a custom tag                                                   |
| `DELETE` | `/{id}?force=false`     | authenticated | Soft-delete (or force-delete if not in use)                           |

#### New Files — UC-2.2

| File                               | Purpose                                          |
| ---------------------------------- | ------------------------------------------------ |
| `MediaAssetController`             | REST endpoints                                   |
| `MediaAssetService`                | Business logic, 3-tier deletion, tag dedup       |
| `AssetTag` entity                  | Mapped to `asset_tags` table                     |
| `AssetTagRepository`               | Queries for tag lookup and dedup                 |
| `AssetTagDto`                      | Tag response record                              |
| `AddAssetTagRequestDto`            | Tag creation request (`@NotBlank @Size(max=50)`) |
| `MediaAssetDetailDto`              | Detail response including `usedIn` + `tags`      |
| `MediaAssetListResponseDto`        | Paginated list response                          |
| `MediaAssetUploadRequestDto`       | Upload request body                              |
| `MediaAssetUsageDto`               | One entry in the `usedIn` list                   |
| `MediaAssetAddToDraftRequestDto`   | Add-to-draft request body                        |
| `MediaAssetUseInNewPostRequestDto` | Use-in-new-post request body                     |
| `V8__media_search_indexes.sql`     | pg_trgm GIN indexes for ILIKE search             |
| `V9__asset_tags.sql`               | `asset_tags` table + RLS policy                  |

---

### UC-2.3 — Notifications

#### Infrastructure

- **`Notification` entity** — persisted to `notifications` table (Flyway V6) with `recipient`, `eventType`, `message`, `deepLink`, `readAt`, `createdAt`.
- **`NotificationEventType` enum** — 23 values covering all submission lifecycle, publishing, override, token, and institution events.
- **`NotificationRepository`** — top-50 query, paginated query, unread count, mark-all-read `@Modifying`, dedup existence check.
- **`NotificationService`** — CRUD + SSE emitter registry using `ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>>`.

#### Endpoints — `/api/v1/notifications`

| Method  | Path                          | Description                     |
| ------- | ----------------------------- | ------------------------------- |
| `GET`   | `/`                           | Top 50 notifications for caller |
| `GET`   | `/unread-count`               | Count of unread notifications   |
| `PATCH` | `/{id}/read`                  | Mark one notification read      |
| `PATCH` | `/read-all`                   | Mark all notifications read     |
| `GET`   | `/history?page=0&pageSize=20` | Paginated notification history  |
| `GET`   | `/stream`                     | SSE stream (real-time push)     |

#### Notification Triggers

All 17 triggers (T1–T17) are wired.

| Trigger | Event                               | Recipients                          | Channels                       |
| ------- | ----------------------------------- | ----------------------------------- | ------------------------------ |
| T1      | Submission → PENDING                | Institution validators              | In-app                         |
| T2      | Submission → SCHEDULED (approved)   | Contributor                         | In-app, email                  |
| T3      | Submission → NEEDS_REVISION         | Contributor                         | In-app, email                  |
| T4      | Submission → REJECTED               | Contributor                         | In-app, email                  |
| T5      | Post auto-published                 | Contributor                         | In-app                         |
| T6      | Post manually published             | Contributor, institution validators | In-app                         |
| T7      | Automated publish failed            | Admins, contributor                 | In-app, email (admins)         |
| T8      | Validation deadline <30 min         | Institution validators, admins      | In-app, email (deduped 30 min) |
| T9      | Override approved                   | Contributor, institution validators | In-app                         |
| T10     | Override denied                     | Contributor                         | In-app, email                  |
| T11     | Admin direct post                   | Institution validators              | In-app                         |
| T12     | Token expiring soon                 | Admins                              | In-app, email                  |
| T13     | Token validation failed             | Admins                              | In-app, email                  |
| T14     | Institution has no active validator | Admins                              | In-app, email                  |
| T15     | Institution onboarded               | Admins                              | In-app                         |
| T16     | Post rescheduled by admin           | Contributor                         | In-app                         |
| T17     | Admin suggests alternative slot     | Contributor                         | In-app, email                  |

#### Event Bus Design

T2–T17 use Spring's `ApplicationEventPublisher`. Each listener handler is annotated with:

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Transactional(propagation = Propagation.REQUIRES_NEW)
```

This guarantees notification failures cannot roll back the business transaction that triggered them. T1 is wired directly in `SubmissionService` since it already holds all required data.

T8 (`ValidationDeadlineNotificationJob`) runs on `@Scheduled(fixedDelay = 5 * 60 * 1000)` and uses a 30-minute dedup window via `existsByRecipientIdAndEventTypeAndDeepLinkAndCreatedAtAfter`.

---

### Email Delivery Log

The `email_delivery_log` table (Flyway V7) was previously created but never written to. It is now fully wired.

**`EmailDeliveryService.send(User recipient, String templateCode, String subject, String body)`**

1. Saves a `queued` row to `email_delivery_log`.
2. Calls `EmailService.sendPlainText()` (which retries 3× internally).
3. On success: updates row to `sent`, sets `deliveredAt`.
4. On failure: updates row to `failed`, sets `errorDetail` (capped at 1 000 chars).
5. Never throws — a failed email is recorded but does not affect the caller.

Every transactional notification email (T2–T4, T7–T8, T10, T12–T14, T17) now writes a delivery log entry. Template codes match the `NotificationEventType` enum name (e.g., `submission_approved`, `validation_timeout`).

---

## Flyway Migration Sequence

| Version | File                                      | Description                                |
| ------- | ----------------------------------------- | ------------------------------------------ |
| V1      | `V1__initial_schema.sql`                  | Foundation entities                        |
| V2      | `V2__...`                                 | Auth tokens, audit log                     |
| V3      | `V3__ensure_institution_email_domain.sql` | Email domain constraint                    |
| V4      | `V4__media_assets.sql`                    | `media_assets` + `submission_media_assets` |
| V5      | `V5__slot_reservations.sql`               | Slot reservation table                     |
| V6      | `V6__notifications.sql`                   | `notifications` table + RLS                |
| V7      | `V7__email_delivery_log.sql`              | `email_delivery_log` table + RLS           |
| V8      | `V8__media_search_indexes.sql`            | pg_trgm GIN indexes on `media_assets`      |
| V9      | `V9__asset_tags.sql`                      | `asset_tags` table + RLS                   |

---

## Commits

| Hash      | Message                                                                      |
| --------- | ---------------------------------------------------------------------------- |
| `b6a4725` | `feat(media): implement UC-2.2 media asset repository backend`               |
| `4df2134` | `feat(notifications): implement UC-2.3 notification infrastructure`          |
| `a61678b` | `feat(notifications): wire notification triggers T1-T17 and T8 deadline job` |
| `f1319b2` | `docs: add Module 2 notification trigger matrix`                             |
| `8b7ce34` | `feat(email): wire email_delivery_log for all transactional emails`          |

---

## What Needs a Frontend Client Next

These backend endpoints are ready but have no frontend API wiring yet:

- `frontend/src/api/mediaAssetApi.ts` — UC-2.2 endpoints
- `frontend/src/api/notificationApi.ts` — UC-2.3 endpoints + SSE stream hook

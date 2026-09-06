# DASIGConnect Incremental Caching Architecture Plan

Branch: `feature/caching-architecture-phase0`

Source base: `dev`

Date: 2026-09-04

## Purpose

This document preserves the reviewed caching prompt and adjusts it to fit the current DASIGConnect codebase. It should be used as the working context for the next caching development tasks.

The plan is intentionally incremental. It does not authorize a broad rewrite, Redis adoption, or blind caching of every route.

## Prompt Review Result

The prompt fits the current system direction and is technically sound for DASIGConnect with a few clarifications:

1. Phase 0 is correctly prioritized. `AdministratorManagementScreen.tsx` has a module-level cache that is not registered with the logout cache reset registry, which creates a real cross-session stale-data risk in the same browser tab.
2. Phase 1 should not assume TanStack Query is already available. Current frontend dependencies do not include `@tanstack/react-query` or `react-query`.
3. If TanStack Query is introduced, verify compatibility with the current frontend stack before installation:
   - React: `19.2.6`
   - React DOM: `19.2.6`
   - Vite: `8.0.12`
   - TypeScript: `~6.0.2`
4. Administrator Management is network-admin scoped, not ordinary institution scoped. Query keys still need authenticated user, role, and scope boundaries. Institution ID may be `null` for network-level admin data.
5. Validation Queue, Notifications, Audit Log, and SSE behavior must be treated as correctness-sensitive. The prompt's caution here is appropriate.
6. Backend caching should remain selective. The current backend has Spring Cache enabled only for analytics summary, plus specific HTTP/static and storage usage caches.
7. The existing project-specific backend guidance says not to cache submission data broadly because staleness can cause workflow inconsistency. Frontend short-lived query caching is acceptable only when paired with explicit invalidation after mutations.

## Current Confirmed State

### Dashboard

No frontend API cache.

File:

`frontend/src/features/dashboard/DashboardScreen.tsx`

Direct API calls occur on mount around line 104.

### Recent Activity

No frontend API cache.

File:

`frontend/src/features/dashboard/RecentActivityScreen.tsx`

Submissions are fetched again on mount around line 54.

### Submissions

Already has caching.

Files:

`frontend/src/hooks/useSubmissions.ts`

`frontend/src/features/submission/SubmissionScreen.tsx`

Current behavior:

- Submission list TTL is approximately 30 seconds.
- Submission lookups TTL is approximately 5 minutes.
- Templates and album references TTL is approximately 2 minutes.
- Submission detail preview entries are held in `submissionDetailsMemoryCache` and cleared through `registerAppCacheReset`.

Preserve this behavior until a focused migration replaces it with centralized query caching.

### Validation Queue

No normal cache.

File:

`frontend/src/features/validation/hooks/useValidationQueue.ts`

Queue and validation log load whenever the hook mounts.

This is freshness-sensitive operational data. Do not add long-lived cache. If query caching is introduced later, use a very short stale time, immediate invalidation after review actions, and preserve validator concurrency behavior.

### Calendar

Partial display persistence.

File:

`frontend/src/hooks/useCalendarEvents.ts`

Previous events are retained to prevent loading flashes, but the hook still fetches when mounted. There is no TTL-based network skip.

### Media Repository

No frontend metadata cache.

File:

`frontend/src/features/media-repository/hooks/useMediaAssets.ts`

Data reloads on mount and scope changes.

Separate media metadata caching from media file/content caching. Do not replace immutable browser or HTTP caching for media files unless there is a proven issue.

### Notifications

Already has:

- approximately 60-second module cache
- SSE updates
- SSE reconnection behavior

File:

`frontend/src/features/notifications/hooks/useNotifications.ts`

Preserve the current SSE behavior. Later integration should use query cache for initial GET data and update or invalidate the notification query from SSE events.

### Analytics

Frontend refetches normally.

File:

`frontend/src/features/analytics/hooks/useAnalyticsSummary.ts`

Backend Analytics Summary is cached for approximately 60 seconds.

Files:

`backend/src/main/java/com/dasigconnect/backend/config/CacheConfig.java`

`backend/src/main/java/com/dasigconnect/backend/service/MetricsAggregatorService.java`

Preserve backend `analyticsSummary` caching.

### Institution Management

Partial frontend module cache.

File:

`frontend/src/features/institution-management/InstitutionManagementScreen.tsx`

The cache is reset on logout but has no TTL and still performs background fetching.

### Administrator Management

Important security and cache isolation issue.

File:

`frontend/src/features/administrator-management/AdministratorManagementScreen.tsx`

There is a module-level `memoryCache`, but it does not register with:

`frontend/src/lib/appCache.ts`

Logout calls `clearAppCaches` from:

`frontend/src/app/App.tsx`

Because Administrator Management does not participate in the reset system, cached administrator information can briefly survive logout and be displayed after another login in the same browser tab.

This is the first issue to fix.

### User Management

No frontend cache.

File:

`frontend/src/features/user-management/UserManagementScreen.tsx`

Direct network and institution-related API calls occur around line 156.

### System Health

Approximately 60-second frontend cache.

File:

`frontend/src/features/system-health/SystemHealthScreen.tsx`

Preserve the current short cache unless a centralized migration clearly improves consistency.

### Audit Log

No cache.

File:

`frontend/src/features/audit-log/AuditLogScreen.tsx`

Categories and logs are reloaded around line 144.

Do not automatically cache all audit data. New/current audit information must remain relatively fresh. Historical immutable pages may later be eligible for longer caching if pagination and filters make this clean.

### Settings

Partial caching.

File:

`frontend/src/features/auth/AccountSettingsScreen.tsx`

Profile settings are cached for approximately 60 seconds. Page and watermark data load once during the screen lifecycle.

### Static Assets

Static avatar/logo endpoints already use long-lived immutable HTTP caching.

Files:

`backend/src/main/java/com/dasigconnect/backend/controller/UserController.java`

`backend/src/main/java/com/dasigconnect/backend/controller/InstitutionController.java`

Preserve this behavior unless inspection finds a correctness bug.

### Storage Usage

File:

`backend/src/main/java/com/dasigconnect/backend/service/MediaStorageService.java`

Storage usage has approximately a 10-minute internal cache. Preserve it unless inspection shows a correctness problem.

## Target Architecture

Progress toward:

```text
React Components
-> Centralized Frontend Query Cache
-> API Client
-> Spring Boot
-> Selective Backend Cache
-> Repository
-> Database
```

The frontend query layer should eventually provide:

- query caching
- request deduplication
- stale/fresh state
- stale-while-revalidate behavior
- background refetching
- targeted invalidation
- prefetching where justified
- cancellation where appropriate
- consistent loading/error states

Do not create additional unrelated module-level `memoryCache` implementations.

## Phase 0: Fix Admin Cache Isolation

Do this first.

Inspect:

- `frontend/src/features/administrator-management/AdministratorManagementScreen.tsx`
- `frontend/src/lib/appCache.ts`
- `frontend/src/app/App.tsx`

Required change:

- Import `registerAppCacheReset` into `AdministratorManagementScreen.tsx`.
- Register a module-scope reset handler for the existing `memoryCache`.
- Clear both:
  - `memoryCache.admins`
  - `memoryCache.pendingInvitations`

Expected reset state:

```ts
memoryCache.admins = null;
memoryCache.pendingInvitations = [];
```

Security requirement:

User A login -> admin data loaded -> User A logout -> admin cache cleared -> User B login -> User B must never receive cached admin data from User A.

Do not merely hide the information visually. The underlying cache must be invalidated.

Scope note:

Administrator Management data is network-admin scoped. The cache is not institution-specific in the normal tenant sense, but it is still authenticated and role-sensitive. It must not cross user/session boundaries.

## Phase 1: Centralized Frontend Query Cache Foundation

Do this only after Phase 0 is fixed and verified.

Current dependency state:

- `@tanstack/react-query` is not installed.
- `react-query` is not installed.
- React is `19.2.6`.

Before introducing TanStack Query:

1. Verify the latest compatible package version for the current React setup.
2. Add it deliberately in a small dependency change.
3. Keep the initial integration limited to provider setup and conventions.

Intended structure:

```text
main.tsx
-> QueryClientProvider
-> BrowserRouter
-> ToastProvider
-> App
-> Routes
-> Feature hooks
-> API services
```

The provider could also wrap inside `App`, but `main.tsx` is the clearer application boundary.

Initial query client behavior should be conservative:

- disable broad aggressive refetching until each feature is migrated intentionally
- use feature-specific `staleTime`
- use bounded `gcTime`
- keep retry behavior modest
- preserve existing API error handling

Authenticated cache clearing:

On logout:

1. cancel active authenticated queries where needed
2. clear authenticated query cache
3. clear existing legacy app caches via `clearAppCaches`
4. preserve existing auth token removal
5. preserve existing SSE disconnection through component unmount and abort behavior

Do not include auth tokens in query keys.

## Query Key Strategy

Use stable, scoped keys. Authenticated query keys must include data scope.

Suggested root keys:

- `dashboard`
- `institutions`
- `users`
- `administrators`
- `submissions`
- `calendar-events`
- `media-assets`
- `validation`
- `notifications`
- `analytics`
- `settings`
- `audit-log`
- `system-health`

Include relevant scope fields:

- user ID when user-specific
- institution ID when institution-specific
- role or permission scope where response shape/data differs by role
- page
- page size
- filters
- search
- sort
- date range

Examples:

```ts
["administrators", { role: "admin", scope: "network", userId }]
["users", { role, institutionId, search, sort, page, pageSize }]
["calendar-events", { institutionId, startDate, endDate }]
["analytics", { role, userId, institutionId, range }]
["media-assets", { networkView, institutionId, albumId, search, sort, mediaType, page }]
```

## Migration Order

Follow this order unless existing code reveals a strong technical reason to change it:

1. Fix Administrator Management logout cache bug.
2. Establish centralized query-cache infrastructure.
3. Integrate cache clearing with logout.
4. Migrate Dashboard.
5. Migrate Analytics frontend fetching.
6. Migrate User Management.
7. Migrate Administrator Management away from legacy module cache.
8. Migrate Institution Management.
9. Improve Calendar caching.
10. Add Media Repository metadata caching.
11. Improve Recent Activity.
12. Carefully improve Validation Queue.
13. Integrate Notifications with centralized caching without breaking SSE.
14. Review System Health.
15. Review Settings.
16. Review Audit Log.
17. Profile backend candidates.
18. Expand Spring or Caffeine caching only where justified.
19. Add prefetching where measurements show benefit.
20. Re-audit the entire application.

Do not begin Redis implementation.

## Freshness Guidelines

Initial suggested `staleTime` values:

- Dashboard: 30 seconds
- Recent Activity: 10 to 30 seconds
- Analytics frontend: 30 to 60 seconds
- User Management: 30 to 60 seconds
- Administrator Management: 30 to 60 seconds
- Institution Management: 2 to 5 minutes
- Calendar: 1 to 2 minutes by requested date/month range
- Media metadata: 30 to 60 seconds
- Validation Queue: 0 to 5 seconds, with immediate invalidation after actions
- Notifications: preserve current 60-second behavior plus SSE
- System Health: 10 to 60 seconds
- Settings profile: 1 to 5 minutes
- Watermark/static settings: 5 to 15 minutes if invalidation is implemented
- Audit current page: 10 to 30 seconds
- Audit historical pages: longer caching may be considered later

TTL is not the main correctness mechanism. Successful mutations must invalidate or update affected queries immediately.

## Invalidation Rules

Use targeted invalidation. Do not globally clear on every mutation.

Examples:

- Create submission:
  - invalidate submissions
  - invalidate relevant dashboard data
  - invalidate recent activity
- Approve/reject/claim/release validation item:
  - invalidate validation queue
  - invalidate affected submission queries
  - invalidate relevant dashboard and analytics summaries when applicable
- Create/update/delete user:
  - invalidate users
  - invalidate administrators if role affects admin roster
  - invalidate dashboard summaries if counts change
- Create administrator:
  - invalidate administrators
  - invalidate pending admin invitations
- Update institution:
  - invalidate institution list/detail queries
  - invalidate dependent institution lookups
- Update profile:
  - update or invalidate profile/settings query
- Upload/delete media:
  - invalidate media metadata
  - invalidate storage usage if represented in frontend cache

Global clearing should generally be reserved for authentication/session boundary changes such as logout.

## Backend Caching Guidance

Do not cache every controller endpoint.

Good backend-cache candidates generally have:

- expensive calculation
- high read frequency
- low write frequency
- safe bounded staleness
- deterministic result
- clear invalidation rules

Possible areas to investigate later:

- dashboard summary calculations
- analytics
- stable lookup/reference tables
- institution metadata
- storage calculations
- expensive count/aggregation queries

Do not cache:

- authorization decisions in an unsafe manner
- highly dynamic validation state for long periods
- arbitrary user-specific responses without scoped keys
- mutations
- sensitive cross-tenant data with ambiguous cache keys

Current deployment appears oriented around a single Spring Boot app deployment. If backend caching expands, inspect deployment first and prefer local in-process caching such as Caffeine where appropriate. Do not introduce Redis in this task.

## Deliverables Per Phase

Each implementation phase must report:

- Files changed
- Previous behavior
- New behavior
- Query keys used
- Freshness policy
- Invalidation rules
- Authentication isolation
- Network behavior examples
- Risks
- Tests or manual validation performed

## Immediate Next Implementation Prompt

Use this for the next coding step:

```text
Start Phase 0 only.

Fix the Administrator Management logout/cache-isolation issue.

Inspect:
- frontend/src/features/administrator-management/AdministratorManagementScreen.tsx
- frontend/src/lib/appCache.ts
- frontend/src/app/App.tsx

Modify AdministratorManagementScreen.tsx so its module-level memoryCache is registered with registerAppCacheReset and fully cleared on logout/session reset.

Do not migrate Administrator Management to TanStack Query yet.
Do not add TanStack Query yet.
Do not change Administrator Management business logic, permissions, API contracts, UI behavior, or mutation behavior.

After implementation, verify with focused tests/build if practical and report:
1. files changed
2. exact bug
3. exact fix
4. why the fix prevents cross-session stale data
5. tests or validation performed
```

## Phase 0 Implementation Status

Status: implemented on `feature/caching-architecture-phase0`.

Changed file:

`frontend/src/features/administrator-management/AdministratorManagementScreen.tsx`

Implemented fix:

- Imported `registerAppCacheReset` from `frontend/src/lib/appCache.ts`.
- Registered the existing module-level Administrator Management `memoryCache` with the shared legacy cache reset registry.
- Reset handler clears:
  - `memoryCache.admins = null`
  - `memoryCache.pendingInvitations = []`

Why this fixes the confirmed bug:

- `App.tsx` already calls `clearAppCaches()` on normal login startup and logout.
- Before this fix, Administrator Management was not registered, so `clearAppCaches()` could not clear its module-level data.
- After this fix, logout and fresh-login boundaries clear the underlying administrator cache, not just the rendered UI.

Validation performed:

- Ran `npm.cmd run build` from `frontend`.
- Build completed successfully.

Residual item for Phase 1:

- Review modal/session-expired reauthentication as part of centralized authenticated cache boundary design. A full logout/login path is covered by Phase 0; modal reauth may need broader handling because mounted route state can survive if a different user authenticates without route teardown.

## Phase 1 Foundation Implementation Status

Status: implemented on `feature/caching-architecture-phase0`.

Changed files:

- `frontend/package.json`
- `frontend/package-lock.json`
- `frontend/src/app/main.tsx`
- `frontend/src/app/App.tsx`
- `frontend/src/lib/queryClient.ts`
- `frontend/src/lib/queryKeys.ts`

Dependency verification:

- `@tanstack/react-query` was not previously installed.
- Registry check returned latest version `5.102.8`.
- Peer dependency is `react: ^18 || ^19`, which is compatible with current React `19.2.6`.
- Installed `@tanstack/react-query@5.102.8`.

Query client foundation:

- Added singleton `appQueryClient`.
- Default query policy:
  - `staleTime: 0`
  - `gcTime: 5 minutes`
  - `refetchOnWindowFocus: false`
  - `retry: 1`
- Default mutation policy:
  - `retry: 0`
- Added `authenticatedQueryMeta` for future migrated authenticated queries.
- Added `clearAuthenticatedQueryCache()`:
  - cancels authenticated queries
  - removes authenticated queries
  - clears mutation cache

Provider integration:

- Wrapped the existing app tree with `QueryClientProvider` in `frontend/src/app/main.tsx`.
- Existing `BrowserRouter`, `ToastProvider`, and route behavior were preserved.

Auth-boundary integration:

- `App.tsx` now clears the centralized authenticated query cache during:
  - normal login startup
  - invitation activation
  - normal logout
  - modal reauthentication
- Existing legacy `clearAppCaches()` behavior remains in place.
- Existing token removal, `setAuthToken`, navigation, session modal, and logout behavior were preserved.

Query key foundation:

- Added `frontend/src/lib/queryKeys.ts`.
- Root key groups:
  - `dashboard`
  - `institutions`
  - `users`
  - `administrators`
  - `submissions`
  - `calendar-events`
  - `media-assets`
  - `validation`
  - `notifications`
  - `analytics`
  - `settings`
  - `audit-log`
  - `system-health`
- Keys include role/user/institution/scope/filter parameters where applicable.
- Tokens are not included in query keys.

Validation performed:

- Ran `npm.cmd run build` from `frontend`.
- Build completed successfully.

Known non-goals in this phase:

- No page data-fetching hooks were migrated yet.
- No backend caching was changed.
- No Redis was introduced.
- No legacy module cache was removed except the Phase 0 reset integration.

Next recommended implementation step:

- Phase 2A: migrate Dashboard reads to centralized query hooks with approximately 30-second `staleTime`, preserving all existing Dashboard role-specific data behavior.

## Phase 2A Dashboard Migration Status

Status: implemented on `feature/caching-architecture-phase0`.

Changed files:

- `frontend/src/features/dashboard/DashboardScreen.tsx`
- `frontend/src/features/dashboard/hooks/useDashboardData.ts`
- `frontend/src/api/authApi.ts`
- `frontend/src/types/auth.types.ts`
- `frontend/src/app/App.tsx`

Previous behavior:

- `DashboardScreen.tsx` owned multiple mount-time `useEffect` fetches.
- Contributor dashboard called `listSubmissions()`.
- Moderator dashboard called:
  - `getValidationQueue()`
  - `getValidationQueue({ history: true })`
- Admin dashboard called:
  - `listInstitutions()`
  - `getValidationQueue()`
  - `getValidationQueue({ history: true })`
  - `listNetworkUsers()`
  - `listPendingNetworkInvitations()`
  - `listPendingAdminInvitations()`
  - `getAnalyticsSummary("30d")`
- Returning to Dashboard caused those calls to run again because no centralized query cache was used.

New behavior:

- Added `useDashboardData(user)` in `frontend/src/features/dashboard/hooks/useDashboardData.ts`.
- Dashboard reads now go through TanStack Query.
- Dashboard still preserves the same role-specific API fan-out and fallback behavior.
- The screen now renders from query data:
  - `institutions`
  - `stats`
- No backend endpoint or API contract was changed.
- No authorization decision is cached client-side; backend endpoints remain the authority.

Query key:

```ts
queryKeys.dashboard.summary({
  role: user.role,
  userId: user.id ?? user.email.trim().toLowerCase(),
  institutionId: user.institutionId ?? null,
})
```

Concrete shape:

```ts
["dashboard", { role, userId, institutionId }]
```

Freshness policy:

- `staleTime: 30_000`
- `gcTime: 5 minutes` inherited from `appQueryClient`
- `refetchOnWindowFocus: false` inherited from `appQueryClient`
- `retry: 1` inherited from `appQueryClient`

Network behavior:

- First visit: `NETWORK`
- Return within 30 seconds: `CACHE`
- Return after 30 seconds while cached data exists: `CACHE immediately + BACKGROUND REFRESH`
- Logout/login/modal reauth: `AUTHENTICATED QUERY CACHE CLEARED`

Invalidation rules:

- No Dashboard-specific mutation invalidation was added in this phase.
- Dashboard cache is cleared on authentication boundaries through `clearAuthenticatedQueryCache()`.
- Later mutation migrations should invalidate the dashboard query when they affect:
  - submissions
  - validation queue counts
  - user/member counts
  - pending invitations
  - analytics-derived dashboard metrics

Authentication isolation:

- Added optional `id` to the frontend `User` type and populated it from `GET /me`.
- Query key uses `user.id` when available.
- If a profile ID is unavailable, query key falls back to normalized email.
- Query key includes role and institution ID.
- Authenticated query cache is cleared during normal login startup, invitation activation, logout, and modal reauthentication.

Cancellation:

- Dashboard query passes TanStack Query's `AbortSignal` into Dashboard API calls where supported.
- Added optional `AbortSignal` support to:
  - `listNetworkUsers`
  - `listPendingAdminInvitations`
  - `listPendingNetworkInvitations`
- Cancelled/aborted Dashboard queries are rethrown so cancellation does not cache empty default dashboard data.

Risks:

- Dashboard still performs a multi-endpoint fan-out. This is intentional for now; no aggregation endpoint was introduced.
- Dashboard fallback behavior remains broad: real request failures render default/empty stats, matching the old screen behavior.
- Some related mutation flows are not yet migrated to React Query, so targeted invalidation will be added in later phases.

Validation performed:

- Ran `npm.cmd run build` from `frontend`.
- Build completed successfully.

Next recommended implementation step:

- Phase 2B: migrate Analytics frontend fetching to the centralized query cache while preserving the backend `analyticsSummary` cache.

## Phase 2B Analytics Migration Status

Status: implemented on `feature/caching-architecture-phase0`.

Changed files:

- `frontend/src/features/analytics/hooks/useAnalyticsSummary.ts`
- `frontend/src/features/analytics/AnalyticsDashboardPage.tsx`

Previous behavior:

- `useAnalyticsSummary` used local React state for `summary`, `loading`, `error`, and a `refreshKey`.
- The hook fetched `GET /analytics/summary` whenever the hook mounted, range changed, institution filter changed, or the manual/background refresh key changed.
- A local `AbortController` cancelled the in-flight request on unmount.
- A 5-minute interval refetched analytics while the tab was visible.
- The frontend did not skip the network during a fresh analytics revisit, even though the backend summary is already cached for about 60 seconds.

New behavior:

- `useAnalyticsSummary(user, initialRange)` now reads analytics through TanStack Query.
- The existing backend `analyticsSummary` cache remains unchanged.
- The analytics page still owns the same range and admin institution filter behavior.
- Cached summaries render immediately while stale data revalidates in the background.
- Manual refresh now calls the active query's `refetch`.
- Background refresh still runs every 5 minutes while the tab is visible.
- No analytics API contract, export flow, report modal behavior, authorization rule, or backend cache was changed.

Query key:

```ts
queryKeys.analytics.summary({
  role: user.role,
  userId: user.id ?? user.email.trim().toLowerCase(),
  institutionId: selectedInstitutionId ?? user.institutionId ?? null,
  range,
})
```

Concrete shape:

```ts
["analytics", { role, userId, institutionId, range }]
```

Freshness policy:

- `staleTime: 60_000`
- `gcTime: 5 minutes` inherited from `appQueryClient`
- `refetchOnWindowFocus: false` inherited from `appQueryClient`
- `retry: 1` inherited from `appQueryClient`
- Background interval: manual `refetch()` every 5 minutes only when `document.visibilityState === "visible"`

Network behavior:

- First analytics visit for a scope/range: `NETWORK`
- Return within 60 seconds: `CACHE`
- Return after 60 seconds while cached data exists: `CACHE immediately + BACKGROUND REFRESH`
- Change to an uncached range or institution filter: `NETWORK`
- Change back to a previously cached range or institution filter within cache lifetime: `CACHE`
- Manual refresh: `NETWORK REFRESH` for the active analytics query
- Logout/login/modal reauth: `AUTHENTICATED QUERY CACHE CLEARED`

Invalidation rules:

- No analytics mutations were introduced in this phase.
- Analytics cache is cleared on authentication boundaries through `clearAuthenticatedQueryCache()`.
- Later mutation migrations should invalidate analytics summary queries when writes affect published/submission/validation/user or institution metrics.

Authentication isolation:

- Query key includes user identity, role, institution scope, selected admin institution filter, and range.
- Auth tokens are not included in keys.
- Authenticated query cache is cancelled and cleared during normal login startup, invitation activation, logout, and modal reauthentication.
- Backend authorization remains the final authority for all analytics responses.

Cancellation:

- TanStack Query passes its `AbortSignal` into `getAnalyticsSummary`.
- This preserves request cancellation on unmount or authenticated cache clearing.

Risks:

- The hook keeps the existing 5-minute background refresh in addition to query stale behavior; this is intentional to preserve current analytics behavior.
- Analytics export/report endpoints are not migrated and still fetch directly when users request reports or CSV exports.
- Related mutation flows are not yet migrated, so targeted analytics invalidation will be added in later phases.

Validation performed:

- Ran `npm.cmd run build` from `frontend`.
- Build completed successfully.

Next recommended implementation step:

- Phase 2C: migrate User Management reads into centralized queries with scoped keys and targeted invalidation after user mutations.

## Phase 2C User Management Migration Status

Status: implemented on `feature/caching-architecture-phase0`.

Changed files:

- `frontend/src/api/authApi.ts`
- `frontend/src/lib/queryKeys.ts`
- `frontend/src/features/user-management/UserManagementScreen.tsx`
- `frontend/src/features/user-management/hooks/useUserManagementData.ts`

Previous behavior:

- `UserManagementScreen.tsx` owned separate mount-time `useEffect` fetches for:
  - institutions
  - network users
  - pending network invitations
  - admin roster data used to compute owner access and open admin slots
- Returning to User Management always reissued those reads.
- Successful mutations manually patched local arrays in some paths and fully reloaded the lists in others.

New behavior:

- Added `useUserManagementData(user)` backed by TanStack Query.
- User Management reads now share one scoped, authenticated query.
- The screen still preserves the same UI, role guard, invite modal behavior, reassignment modal, role-change modal, and API contracts.
- Partial-load behavior is preserved with independent institution and management error messages.
- Successful mutations invalidate centralized query groups instead of maintaining separate local server-state copies.

Query key:

```ts
queryKeys.users.all({
  role: user.role,
  userId: user.id ?? user.email.trim().toLowerCase(),
  institutionId: null,
  scope: "network",
})
```

Concrete shape:

```ts
["users", { role, userId, institutionId: null, scope: "network" }]
```

Freshness policy:

- `staleTime: 60_000`
- `gcTime: 5 minutes` inherited from `appQueryClient`
- `refetchOnWindowFocus: false` inherited from `appQueryClient`
- `retry: 1` inherited from `appQueryClient`

Network behavior:

- First User Management visit: `NETWORK`
- Return within 60 seconds: `CACHE`
- Return after 60 seconds while cached data exists: `CACHE immediately + BACKGROUND REFRESH`
- Logout/login/modal reauth: `AUTHENTICATED QUERY CACHE CLEARED`

Invalidation rules:

- User Management mutations invalidate:
  - `users`
  - `administrators`
  - `dashboard`
  - `analytics`
- Covered mutation paths:
  - invite user
  - activate/deactivate user
  - delete/deactivate user
  - erase user data
  - cancel invitation
  - resend invitation
  - reassign contributor
  - change user role

Authentication isolation:

- Query key includes user identity, role, and network scope.
- Auth tokens are not included in keys.
- The centralized authenticated query cache is still cleared at auth boundaries through `clearAuthenticatedQueryCache()`.
- Backend authorization remains the final authority for all user-management data.

Cancellation:

- TanStack Query passes its `AbortSignal` into the User Management read fan-out.
- `listAdmins` now accepts an optional `AbortSignal`, matching the existing signal support on related list APIs.

Risks:

- The query still fans out across four existing endpoints. This preserves current contracts but does not reduce backend request count on a cold load.
- Mutation paths refetch from the server after success instead of applying optimistic local array updates.

Validation performed:

- Ran `npm.cmd run build` from `frontend`.
- Build completed successfully.

Next recommended implementation step:

- Phase 2D: migrate Administrator Management away from its legacy module cache and into centralized query caching, preserving the Phase 0 logout cache reset until the legacy cache is fully removed.

## Phase 2D Administrator Management Migration Status

Status: implemented on `feature/caching-architecture-phase0`.

Changed files:

- `frontend/src/features/administrator-management/AdministratorManagementScreen.tsx`
- `frontend/src/features/administrator-management/hooks/useAdministratorManagementData.ts`
- `frontend/src/features/user-management/hooks/useUserManagementData.ts`
- `docs/md/caching-architecture-incremental-plan.md`

Previous behavior:

- `AdministratorManagementScreen.tsx` used a module-level `memoryCache` for admin roster and pending admin invitations.
- Phase 0 registered that cache with logout reset, but the screen still owned manual fetch state and refresh logic.
- The screen separately loaded institutions for the role-change modal after ownership was known.
- Successful mutations manually patched local admin arrays or reloaded lists.

New behavior:

- Added `useAdministratorManagementData(user)` backed by TanStack Query.
- Removed the legacy Administrator Management module-level `memoryCache`.
- Admin roster, pending admin invitations, and institutions now load through one centralized authenticated query.
- The screen keeps the same permissions, modal behavior, admin limit logic, transfer flow, role-change flow, and API contracts.
- Successful mutations invalidate centralized query groups instead of maintaining local server-state copies.
- Query cancellation now rethrows when the TanStack `AbortSignal` is aborted, preventing empty fallback data from being cached on auth/session reset.

Query key:

```ts
queryKeys.administrators.all({
  role: user.role,
  userId: user.id ?? user.email.trim().toLowerCase(),
  scope: "network",
})
```

Concrete shape:

```ts
["administrators", { role, userId, scope: "network" }]
```

Freshness policy:

- `staleTime: 60_000`
- `gcTime: 5 minutes` inherited from `appQueryClient`
- `refetchOnWindowFocus: false` inherited from `appQueryClient`
- `retry: 1` inherited from `appQueryClient`

Network behavior:

- First Administrator Management visit: `NETWORK`
- Return within 60 seconds: `CACHE`
- Return after 60 seconds while cached data exists: `CACHE immediately + BACKGROUND REFRESH`
- Logout/login/modal reauth: `AUTHENTICATED QUERY CACHE CLEARED`

Invalidation rules:

- Administrator Management mutations invalidate:
  - `administrators`
  - `users`
  - `dashboard`
  - `analytics`
- Covered mutation paths:
  - invite admin
  - activate/deactivate admin
  - delete/deactivate admin
  - erase admin data
  - request admin transfer
  - confirm admin transfer
  - resend admin invitation
  - cancel admin invitation
  - change admin role

Authentication isolation:

- Query key includes user identity, role, and network scope.
- Auth tokens are not included in keys.
- The legacy module cache was removed, so there is no separate cross-route cache requiring app-cache reset registration.
- The centralized authenticated query cache is still cleared at auth boundaries through `clearAuthenticatedQueryCache()`.
- Backend authorization remains the final authority for administrator data.

Cancellation:

- TanStack Query passes its `AbortSignal` into the admin roster, pending admin invitations, and institution reads.
- Aborted reads throw `AbortError` instead of caching empty fallback data.
- The same cancellation guard was added to `useUserManagementData` because it uses the same `Promise.allSettled` fan-out pattern.

Risks:

- The query still fans out across three existing endpoints. This preserves current contracts but does not reduce backend request count on a cold load.
- Institution data is loaded for all admins through this screen query; role-change controls remain owner-gated in the UI and authorization remains backend-enforced.

Validation performed:

- Ran targeted ESLint from `frontend`:
  - `npx.cmd eslint src/features/administrator-management/AdministratorManagementScreen.tsx src/features/administrator-management/hooks/useAdministratorManagementData.ts src/features/user-management/hooks/useUserManagementData.ts --quiet`
- Ran `npm.cmd run build` from `frontend`.
- Both completed successfully.

Next recommended implementation step:

- Phase 2E: migrate Institution Management reads into centralized query caching with a longer freshness window and targeted invalidation after institution/user invite mutations.

## Phase 2E Institution Management Migration Status

Status: implemented on `feature/caching-architecture-phase2e`.

Changed files:

- `frontend/src/api/authApi.ts`
- `frontend/src/features/institution-management/InstitutionManagementScreen.tsx`
- `frontend/src/features/institution-management/hooks/useInstitutionManagementData.ts`
- `docs/md/caching-architecture-incremental-plan.md`

Previous behavior:

- `InstitutionManagementScreen.tsx` used a module-level `institutionsMemoryCache` for the institution registry.
- The registry loaded institutions on mount, then separately fetched user counts and pending invitation counts per institution.
- The selected institution detail view loaded users and pending invitations through local effect state.
- Mutations patched local arrays in some paths and reloaded detail data in others.

New behavior:

- Added `useInstitutionRegistryData(user)` backed by TanStack Query.
- Added `useInstitutionDetailData(user, institutionId)` backed by TanStack Query.
- Removed the legacy institution module-level memory cache and app-cache reset registration.
- Institution registry data, per-institution stats, selected institution users, and selected institution pending invitations now use centralized authenticated query caching.
- Local state now holds UI concerns such as selected institution id, modal state, form state, upload busy state, and filters.
- Successful institution/user/invite mutations invalidate centralized query groups instead of maintaining separate local server-state copies.
- Added `AbortSignal` support to institution-scoped API reads used by the query hooks.

Query keys:

```ts
queryKeys.institutions.all({
  role: user.role,
  userId: user.id ?? user.email.trim().toLowerCase(),
})
```

```ts
queryKeys.users.all({
  role: user.role,
  userId: user.id ?? user.email.trim().toLowerCase(),
  institutionId,
  scope: "institution",
})
```

Concrete shapes:

```ts
["institutions", { role, userId }]
["users", { role, userId, institutionId, scope: "institution" }]
```

Freshness policy:

- Institution registry: `staleTime: 300_000`
- Institution detail users/invitations: `staleTime: 60_000`
- `gcTime: 5 minutes` inherited from `appQueryClient`
- `refetchOnWindowFocus: false` inherited from `appQueryClient`
- `retry: 1` inherited from `appQueryClient`

Network behavior:

- First Institution Management visit: `NETWORK`
- Return to registry within 5 minutes: `CACHE`
- Open selected institution detail within 60 seconds for the same institution: `CACHE`
- Return after stale time while cached data exists: `CACHE immediately + BACKGROUND REFRESH`
- Logout/login/modal reauth: `AUTHENTICATED QUERY CACHE CLEARED`

Invalidation rules:

- Institution Management mutations invalidate:
  - `institutions`
  - `users`
  - `dashboard`
  - `analytics`
- Covered mutation paths:
  - create institution
  - edit institution
  - delete institution
  - activate/deactivate institution
  - upload institution logo
  - upload user avatar
  - invite contributor/moderator
  - activate/deactivate user
  - delete user
  - cancel invitation
  - resend invitation
  - reassign contributor

Authentication isolation:

- Query keys include user identity and role.
- Institution detail keys include institution ID and institution scope.
- Auth tokens are not included in keys.
- The legacy module cache was removed, so there is no separate cache requiring app-cache reset registration.
- The centralized authenticated query cache is still cleared at auth boundaries through `clearAuthenticatedQueryCache()`.
- Backend authorization remains the final authority.

Cancellation:

- TanStack Query passes its `AbortSignal` into:
  - `listInstitutions`
  - `getUserCounts`
  - `getPendingInvitationCount`
  - `listUsers`
  - `listPendingInvitations`
- Cancelled registry stat requests rethrow cancellation instead of caching partial fallback data.

Risks:

- Registry stats now resolve through the query before rendering the completed registry data, rather than progressively filling individual stat cells after the base list appears.
- The cold-load fan-out still uses existing endpoints and has not been consolidated into a backend summary endpoint.

Validation performed:

- Ran targeted ESLint from `frontend`:
  - `npx.cmd eslint src/api/authApi.ts src/features/institution-management/InstitutionManagementScreen.tsx src/features/institution-management/hooks/useInstitutionManagementData.ts --quiet`
- Ran `npm.cmd run build` from `frontend`.
- Both completed successfully.

Next recommended implementation step:

- Phase 2F: improve Calendar caching by moving calendar event reads to centralized query caching keyed by requested date range and authenticated scope.

## Phase 2F Calendar Migration Status

Status: implemented on `feature/caching-architecture-phase2f`.

Changed files:

- `frontend/src/hooks/useCalendarEvents.ts`
- `frontend/src/features/calendar/CalendarScreen.tsx`
- `docs/md/caching-architecture-incremental-plan.md`

Previous behavior:

- `useCalendarEvents` used local React state plus a module-level `cachedCalendarEvents` array.
- The hook registered the module cache with `registerAppCacheReset`.
- Calendar events were fetched on mount and manual refresh, independent of authenticated query keys.
- Returning to the calendar reused only the module cache and still had no scoped stale/fresh policy.

New behavior:

- `useCalendarEvents(user, range)` now uses TanStack Query.
- The legacy module-level calendar event cache was removed.
- Calendar reads are keyed by authenticated user scope and the visible calendar range.
- `CalendarScreen` seeds the initial query range to the current month so the calendar can fetch before FullCalendar emits its first `datesSet`.
- Manual refresh invalidates the `calendar-events` query group.
- Successful reschedules invalidate:
  - `calendar-events`
  - `dashboard`
  - `analytics`
  - `submissions`
- Existing calendar UI behavior, filters, metrics, event detail modal, and drag-reschedule flow are preserved.

Query key:

```ts
queryKeys.calendarEvents.range({
  role: user.role,
  userId: user.id ?? user.email.trim().toLowerCase(),
  institutionId: user.institutionId ?? null,
  startDate,
  endDate,
})
```

Concrete shape:

```ts
["calendar-events", { role, userId, institutionId, startDate, endDate }]
```

Freshness policy:

- `staleTime: 120_000`
- `gcTime: 5 minutes` inherited from `appQueryClient`
- `refetchOnWindowFocus: false` inherited from `appQueryClient`
- `retry: 1` inherited from `appQueryClient`

Network behavior:

- First visit for a range/scope: `NETWORK`
- Return to the same range within 2 minutes: `CACHE`
- Return after 2 minutes while cached data exists: `CACHE immediately + BACKGROUND REFRESH`
- Manual refresh: invalidates calendar-event queries and refetches active observers.
- Reschedule success: invalidates calendar, dashboard, analytics, and submission queries.
- Logout/login/modal reauth: `AUTHENTICATED QUERY CACHE CLEARED`

Authentication isolation:

- Query key includes user identity, role, institution ID, and date range.
- Auth tokens are not included in keys.
- The legacy module cache was removed, so calendar data no longer has a separate cross-route cache.
- The centralized authenticated query cache is still cleared at auth boundaries through `clearAuthenticatedQueryCache()`.
- Backend authorization remains the final authority for event visibility and masking.

Backend note:

- The current backend `GET /api/v1/calendar` endpoint does not accept range parameters.
- This phase intentionally keeps the API contract stable and uses range-scoped frontend cache keys.
- A future backend optimization can add server-side date filtering if calendar payload size becomes a measured problem.

Validation performed:

- Ran targeted ESLint from `frontend`:
  - `npx.cmd eslint src/hooks/useCalendarEvents.ts src/features/calendar/CalendarScreen.tsx --quiet`
- Ran `npm.cmd run build` from `frontend`.
- Both completed successfully.

Next recommended implementation step:

- Phase 2G: add Media Repository metadata caching through centralized query hooks, keeping media file/browser caching separate.

## Phase 2G Media Repository Migration Status

Status: implemented on `feature/caching-architecture-phase2g`.

Changed files:

- `frontend/src/features/media-repository/hooks/useMediaAssets.ts`
- `frontend/src/features/media-repository/MediaRepositoryScreen.tsx`
- `docs/md/caching-architecture-incremental-plan.md`

Previous behavior:

- `useMediaAssets` used local React state and manual effect-driven fetches.
- Scope changes cleared local assets and triggered a new `/media-assets` request.
- Manual refresh re-ran the current list request directly.
- Uploads, folder moves, album changes, tag edits, and deletes maintained local UI state but did not invalidate centralized query data.

New behavior:

- `useMediaAssets(user, networkView, institutionId, albumId, enabled)` now uses TanStack Query.
- Media repository asset metadata reads are keyed by authenticated user scope, network/institution scope, and folder scope.
- The hook still returns the existing `{ assets, setAssets, loading, error, refresh }` shape so the screen can keep its immediate local UI updates.
- `setAssets` now updates the active media-assets query data instead of a separate local array.
- Manual refresh invalidates the `media-assets` query group.
- Media file delivery remains browser/object-storage based; this phase only caches metadata.

Query key:

```ts
queryKeys.mediaAssets.all({
  role: user.role,
  userId: user.id ?? user.email.trim().toLowerCase(),
  networkView,
  institutionId: selectedInstitutionId ?? null,
  albumId: listAlbumId ?? null,
})
```

Concrete shape:

```ts
["media-assets", { role, userId, networkView, institutionId, albumId }]
```

Freshness policy:

- `staleTime: 60_000`
- `gcTime: 5 minutes` inherited from `appQueryClient`
- `refetchOnWindowFocus: false` inherited from `appQueryClient`
- `retry: 1` inherited from `appQueryClient`

Network behavior:

- First Media Repository visit for a scope/folder: `NETWORK`
- Return to the same scope/folder within 60 seconds: `CACHE`
- Return after 60 seconds while cached data exists: `CACHE immediately + BACKGROUND REFRESH`
- Manual refresh: invalidates media-assets queries and refetches active observers.
- Logout/login/modal reauth: `AUTHENTICATED QUERY CACHE CLEARED`

Invalidation rules:

- Media Repository metadata invalidates after:
  - create album
  - rename album
  - move album
  - delete album
  - move asset to album
  - add/remove asset tag
  - upload asset
  - upload folder
  - single or bulk asset delete
- Media Repository mutations also invalidate:
  - `dashboard`
  - `analytics`

Authentication isolation:

- Query key includes user identity, role, network view flag, institution ID, and album ID.
- Auth tokens are not included in keys.
- The centralized authenticated query cache is still cleared at auth boundaries through `clearAuthenticatedQueryCache()`.
- Backend authorization remains the final authority for network-wide or institution-scoped visibility.

Risks:

- Album lists still use local state and direct reloads; this phase only centralizes asset metadata caching.
- Semantic search still uses direct request state because it is an explicit search action, not the main repository metadata list.
- The hook preserves immediate `setAssets` updates by writing into the active query cache; inactive scope caches are invalidated instead of manually patched.

Validation performed:

- Ran targeted ESLint from `frontend`:
  - `npx.cmd eslint src/features/media-repository/hooks/useMediaAssets.ts src/features/media-repository/MediaRepositoryScreen.tsx --quiet`
- Ran `npm.cmd run build` from `frontend`.
- Both completed successfully.

Next recommended implementation step:

- Phase 2H: improve Recent Activity and notification-adjacent read caching through centralized query hooks, while keeping real-time or user-initiated refresh behavior explicit.

## Phase 2H Recent Activity And Notifications Migration Status

Status: implemented on `feature/caching-architecture-phase2h`.

Changed files:

- `frontend/src/features/notifications/hooks/useNotifications.ts`
- `frontend/src/features/notifications/NotificationsScreen.tsx`
- `frontend/src/components/layout/DashboardLayout.tsx`
- `frontend/src/features/dashboard/RecentActivityScreen.tsx`
- `frontend/src/lib/queryKeys.ts`
- `docs/md/caching-architecture-incremental-plan.md`

Previous behavior:

- `useNotifications` used a module-scoped TTL cache and registered it with `registerAppCacheReset`.
- The notifications page manually fetched `/notifications` with local loading/error state.
- SSE arrivals and optimistic read updates mutated local notification state plus the module cache.
- The sidebar unread badge used local state and polled `/notifications/unread-count` directly.
- Recent Activity fetched submissions and admin institution labels through local effects.

New behavior:

- `useNotifications(user)` now uses TanStack Query for the initial notifications list.
- The module-scoped notification cache and app-cache reset registration were removed.
- SSE still connects explicitly, but new events are merged into the active notifications query cache.
- Mark-read and mark-all-read actions optimistically update the notifications query cache and synchronize the unread-count query cache.
- `useNotificationUnreadCount(user)` centralizes the sidebar unread badge query while preserving the 3-minute visible-tab poll and focus refresh.
- `RecentActivityScreen` now reads submissions and admin institution labels through a scoped TanStack Query.
- Recent Activity uses the `submissions` root with a `recent-activity` view marker so existing submission invalidations continue to refresh it.

Query keys:

```ts
queryKeys.notifications.all({
  role: user.role,
  userId: user.id ?? user.email.trim().toLowerCase(),
  institutionId: user.institutionId ?? null,
})

queryKeys.notifications.unreadCount({
  role: user.role,
  userId: user.id ?? user.email.trim().toLowerCase(),
  institutionId: user.institutionId ?? null,
})

queryKeys.submissions.all({
  role: user.role,
  userId: user.id ?? user.email.trim().toLowerCase(),
  institutionId: user.institutionId ?? null,
  status: "recent-activity",
})
```

Freshness policy:

- Notifications list: `staleTime: 60_000`
- Notification unread count: `staleTime: 30_000`
- Recent Activity: `staleTime: 60_000`
- `gcTime: 5 minutes` inherited from `appQueryClient`
- `refetchOnWindowFocus: false` inherited from `appQueryClient`; the layout keeps an explicit focus invalidation for notification counts.
- `retry: 1` inherited from `appQueryClient`

Network behavior:

- First notifications page visit for a user scope: `NETWORK`
- Return to notifications within 60 seconds: `CACHE`
- SSE notification event: updates active notifications cache and unread-count cache without refetching the whole list.
- Manual notifications refresh: invalidates `notifications` queries.
- Sidebar badge: cached unread count, plus visible-tab interval/focus invalidation.
- First Recent Activity visit for a user scope: `NETWORK`
- Return to Recent Activity within 60 seconds: `CACHE`
- Existing submission mutations that invalidate `submissions` also invalidate Recent Activity.
- Logout/login/modal reauth: `AUTHENTICATED QUERY CACHE CLEARED`

Authentication isolation:

- Notification and unread-count keys include user identity, role, and institution ID.
- Recent Activity key includes user identity, role, institution ID, and the view marker.
- Auth tokens are not included in keys.
- The centralized authenticated query cache is still cleared at auth boundaries through `clearAuthenticatedQueryCache()`.
- Backend authorization remains the final authority for visible notifications, unread counts, submissions, and institution labels.

Risks:

- Notification relative-time labels are still computed when DTOs are mapped, matching previous behavior.
- SSE remains page-hook scoped; the layout unread badge continues to rely on polling/focus invalidation when the notifications page is not mounted.
- Recent Activity keeps the previous partial-fallback behavior: if submissions or institution label lookup fails, that section returns an empty list rather than blocking the whole screen.

Validation performed:

- Ran targeted ESLint from `frontend`:
  - `npx.cmd eslint src/lib/queryKeys.ts src/features/notifications/hooks/useNotifications.ts src/features/notifications/NotificationsScreen.tsx src/components/layout/DashboardLayout.tsx src/features/dashboard/RecentActivityScreen.tsx --quiet`
- Ran `npm.cmd run build` from `frontend`.
- Both completed successfully.

Next recommended implementation step:

- Phase 2I: continue migrating remaining authenticated read-heavy screens, starting with Validation Queue if it still has direct effect-driven fetching or route-local caches.

## Phase 2I Validation Queue Migration Status

Status: implemented on `feature/caching-architecture-phase2i`.

Changed files:

- `frontend/src/features/validation/hooks/useValidationQueue.ts`
- `frontend/src/features/validation/ValidationQueueScreen.tsx`
- `frontend/src/lib/queryKeys.ts`
- `docs/md/caching-architecture-incremental-plan.md`

Previous behavior:

- `useValidationQueue` used local React state and a mount effect to fetch the active review queue.
- `useValidationLog` used local React state and a mount/selection effect for validation history.
- `ValidationQueueScreen` kept a separate local `allQueue` state and direct `getValidationQueue({ history: true })` fetch flow.
- Review lock and decision actions refreshed only the local queue fetch paths.

New behavior:

- `useValidationQueue(user, history)` now uses TanStack Query for both active and history/all queue reads.
- `useValidationLog(user, submissionId)` now uses TanStack Query and is keyed by authenticated user plus submission ID.
- `ValidationQueueScreen` uses the same query hook for active queue and all/history queue data.
- The direct history queue effect in `ValidationQueueScreen` was removed.
- Review lock and terminal decision paths now invalidate validation, submissions, dashboard, calendar, analytics, and notifications query groups.
- Existing local detail state, review lock renewal, editor state, failure workflow state, and decision modals remain unchanged.

Query keys:

```ts
queryKeys.validation.queue({
  role: user.role,
  userId: user.id ?? user.email.trim().toLowerCase(),
  institutionId: user.institutionId ?? null,
  scope: history ? "history" : "network",
})

queryKeys.validation.log({
  role: user.role,
  userId: user.id ?? user.email.trim().toLowerCase(),
  submissionId,
})
```

Freshness policy:

- Validation queue: `staleTime: 5_000`
- Validation log: `staleTime: 30_000`
- `gcTime: 5 minutes` inherited from `appQueryClient`
- `refetchOnWindowFocus: false` inherited from `appQueryClient`
- `retry: 1` inherited from `appQueryClient`

Network behavior:

- First Validation Queue visit for active/history scope: `NETWORK`
- Return within 5 seconds for the same queue scope: `CACHE`
- Return after 5 seconds while cached data exists: `CACHE immediately + BACKGROUND REFRESH`
- Open a submission's validation history within 30 seconds: `CACHE`
- Manual/all-mode queue refresh invalidates validation queries.
- Review lock acquire/release/loss and approve/revise/reject/edit success invalidate affected workflow query groups.
- Logout/login/modal reauth: `AUTHENTICATED QUERY CACHE CLEARED`

Invalidation rules:

- Validation workflow mutations invalidate:
  - `validation`
  - `submissions`
  - `dashboard`
  - `calendar-events`
  - `analytics`
  - `notifications`

Authentication isolation:

- Queue keys include user identity, role, institution ID, and queue scope.
- Log keys include user identity, role, and submission ID.
- Auth tokens are not included in keys.
- The centralized authenticated query cache is still cleared at auth boundaries through `clearAuthenticatedQueryCache()`.
- Backend authorization and review-lock enforcement remain the final authority.

Risks:

- Validation queue freshness is intentionally short because queue ownership and review locks are workflow-sensitive.
- Selected submission detail reads still use direct requests so lock acquisition and detail-panel behavior remain explicit.
- Resolution failure data remains in its existing hook and was not migrated in this phase.

Validation performed:

- Ran targeted ESLint from `frontend`:
  - `npx.cmd eslint src/lib/queryKeys.ts src/features/validation/hooks/useValidationQueue.ts src/features/validation/ValidationQueueScreen.tsx --quiet`
- Ran `npm.cmd run build` from `frontend`.
- Both completed successfully.

Next recommended implementation step:

- Phase 2J: migrate remaining lower-risk authenticated read screens such as audit log, system health, and settings reads with conservative stale times and targeted invalidation.

## Phase 2J System Health Migration Status

Status: implemented on `feature/caching-architecture-phase2j`.

Changed files:

- `frontend/src/features/system-health/SystemHealthScreen.tsx`
- `docs/md/caching-architecture-incremental-plan.md`

Previous behavior:

- `SystemHealthScreen` used module-scoped `cachedSummary`, `cachedTokens`, and `cachedAt` values.
- The screen registered that cache with `registerAppCacheReset`.
- System health summary and token status loaded through a local effect and manual `load()` function.
- Running a background job manually re-called the local loader.

New behavior:

- `SystemHealthScreen` now uses TanStack Query for the combined system-health summary and token-status read.
- The module-scoped system-health cache and app-cache reset registration were removed.
- The summary remains the required part of the payload; token status still falls back to an empty list if token loading fails.
- Manual refresh invalidates the `system-health` query group.
- Running a background job invalidates the `system-health` query group after the job completes.
- OAuth reauthorization and CSV export remain explicit imperative actions.

Query key:

```ts
queryKeys.systemHealth.summary({
  role: user.role,
  userId: user.id ?? user.email.trim().toLowerCase(),
})
```

Concrete shape:

```ts
["system-health", { role, userId }]
```

Freshness policy:

- `staleTime: 60_000`
- `gcTime: 5 minutes` inherited from `appQueryClient`
- `refetchOnWindowFocus: false` inherited from `appQueryClient`
- `retry: 1` inherited from `appQueryClient`

Network behavior:

- First System Health visit for a user scope: `NETWORK`
- Return within 60 seconds: `CACHE`
- Return after 60 seconds while cached data exists: `CACHE immediately + BACKGROUND REFRESH`
- Manual refresh: invalidates system-health queries.
- Run background job: invalidates system-health queries after completion.
- Logout/login/modal reauth: `AUTHENTICATED QUERY CACHE CLEARED`

Authentication isolation:

- Query key includes user identity and role.
- Auth tokens are not included in keys.
- The centralized authenticated query cache is still cleared at auth boundaries through `clearAuthenticatedQueryCache()`.
- Backend authorization remains the final authority for system-health access.

Risks:

- System Health remains operational telemetry, so the freshness window stays short.
- Token-status load failure still degrades to an empty token list when summary loading succeeds, preserving previous partial-data behavior.
- Audit Log and settings reads are intentionally left for follow-up phases because their filter/edit flows need separate review.

Validation performed:

- Ran targeted ESLint from `frontend`:
  - `npx.cmd eslint src/features/system-health/SystemHealthScreen.tsx src/lib/queryKeys.ts --quiet`
- Ran `npm.cmd run build` from `frontend`.
- Both completed successfully.

Next recommended implementation step:

- Phase 2K: migrate Audit Log reads with filter/page-scoped query keys, keeping export imperative and audit freshness conservative.

## Phase 2K Audit Log Migration Status

Status: implemented on `feature/caching-architecture-phase2k`.

Changed files:

- `frontend/src/features/audit-log/AuditLogScreen.tsx`
- `frontend/src/lib/queryKeys.ts`
- `docs/md/caching-architecture-incremental-plan.md`

Previous behavior:

- `AuditLogScreen` used local React state for audit rows, totals, loading, load errors, and metadata options.
- Audit categories loaded once from a mount effect.
- Audit rows loaded through a local `loadData()` function and effect tied to filter params.
- Filter, search, and pagination handlers manually set `loading` before state changes.
- CSV export was already imperative and separate from the table load path.

New behavior:

- Audit metadata now loads through TanStack Query with a longer metadata freshness window.
- Audit log pages now load through TanStack Query with filter/page-scoped cache keys.
- Manual refresh/retry invalidates the `audit-log` query group.
- Filter, search, and pagination controls rely on query loading/fetching state instead of manually setting loading.
- CSV export remains imperative and does not cache downloaded report data.
- Default category/entity options remain as the fallback when metadata is unavailable.

Query keys:

```ts
queryKeys.auditLog.metadata({
  role: user.role,
  userId: user.id ?? user.email.trim().toLowerCase(),
})

queryKeys.auditLog.page({
  role: user.role,
  userId: user.id ?? user.email.trim().toLowerCase(),
  page,
  pageSize,
  startDate,
  endDate,
  category,
  entityType,
  search,
})
```

Freshness policy:

- Audit log page: `staleTime: 15_000`
- Audit metadata: `staleTime: 300_000`
- `gcTime: 5 minutes` inherited from `appQueryClient`
- `refetchOnWindowFocus: false` inherited from `appQueryClient`
- `retry: 1` inherited from `appQueryClient`

Network behavior:

- First Audit Log visit for a filter/page scope: `NETWORK`
- Return to the same filter/page within 15 seconds: `CACHE`
- Return after 15 seconds while cached data exists: `CACHE immediately + BACKGROUND REFRESH`
- Changing filters/search/page uses a distinct query key.
- Manual refresh/retry invalidates audit-log queries.
- CSV export always performs a fresh download request.
- Logout/login/modal reauth: `AUTHENTICATED QUERY CACHE CLEARED`

Authentication isolation:

- Audit page and metadata keys include user identity and role.
- Auth tokens are not included in keys.
- The centralized authenticated query cache is still cleared at auth boundaries through `clearAuthenticatedQueryCache()`.
- Backend authorization remains the final authority for audit visibility and CSV export access.

Risks:

- Audit data remains freshness-sensitive, so the page stale time is intentionally short.
- Relative timestamps are still computed at render time and can age while cached data remains visible.
- Audit mutations are not performed from this screen; cross-feature audit log invalidation can be added later if users need newly created audit events to appear instantly without manual refresh.

Validation performed:

- Ran targeted ESLint from `frontend`:
  - `npx.cmd eslint src/features/audit-log/AuditLogScreen.tsx src/lib/queryKeys.ts --quiet`
- Ran `npm.cmd run build` from `frontend`.
- Both completed successfully.

Next recommended implementation step:

- Phase 2L: migrate account/settings reads such as profile page data and watermark configuration, with explicit invalidation after saves.

## Phase 2L Account Settings Migration Status

Status: implemented on `feature/caching-architecture-phase2l`.

Changed files:

- `frontend/src/features/auth/AccountSettingsScreen.tsx`
- `frontend/src/api/authApi.ts`
- `frontend/src/api/watermarkApi.ts`
- `docs/md/caching-architecture-incremental-plan.md`

Previous behavior:

- `AccountSettingsScreen` used a module-level profile cache for the `GET /me` profile settings slice.
- Page settings and watermark configuration were loaded through a one-shot effect the first time an admin opened the Page tab.
- Watermark loading was tracked with local component state.
- Account, page, and watermark saves updated local component state without invalidating a shared settings cache.
- The active settings tab was mirrored from the URL hash into local state.

New behavior:

- Profile settings now load through TanStack Query using the existing `settings.profile` key.
- Page settings now load lazily through TanStack Query when an admin opens the Page tab.
- Watermark configuration now loads lazily through TanStack Query when an admin opens the Page tab or Watermark Studio route.
- Successful account, page, and watermark saves write the returned response into the matching query cache and invalidate the `settings` query group.
- Editable form state hydrates once from query data so background refetches do not overwrite in-progress edits.
- The settings tab is derived directly from the URL hash instead of being mirrored through a state-setting effect.
- Settings API read helpers now accept `AbortSignal` so query cancellation reaches Axios.

Query keys:

```ts
queryKeys.settings.profile({
  userId: user.id ?? user.email.trim().toLowerCase(),
})

queryKeys.settings.page({
  role: user.role,
  userId: user.id ?? user.email.trim().toLowerCase(),
  institutionId: null,
})

queryKeys.settings.watermark({
  role: user.role,
  userId: user.id ?? user.email.trim().toLowerCase(),
  institutionId: null,
})
```

Freshness policy:

- Profile settings: `staleTime: 60_000`
- Page settings: `staleTime: 300_000`
- Watermark configuration: `staleTime: 300_000`
- `gcTime: 5 minutes` inherited from `appQueryClient`
- `refetchOnWindowFocus: false` inherited from `appQueryClient`
- `retry: 1` inherited from `appQueryClient`

Network behavior:

- First Settings visit for a user scope: `NETWORK` for profile settings.
- Return within 60 seconds: `CACHE` for profile settings.
- First admin Page tab visit: `NETWORK` for page settings and watermark configuration.
- Return to the admin Page tab within 5 minutes: `CACHE` for page settings and watermark configuration.
- Return after stale time while cached data exists: `CACHE immediately + BACKGROUND REFRESH`.
- Successful account/page/watermark saves invalidate `settings` queries after caching the returned response.
- Logout/login/modal reauth: `AUTHENTICATED QUERY CACHE CLEARED`.

Authentication isolation:

- Profile settings are scoped by user identity.
- Page settings and watermark configuration are scoped by user identity, role, and institution context.
- Auth tokens are not included in keys.
- The centralized authenticated query cache is still cleared at auth boundaries through `clearAuthenticatedQueryCache()`.
- Backend authorization remains the final authority for profile, page, and watermark access.

Risks:

- Messenger connection status remains imperative in this phase because its setup/check-code flow is a separate account channel workflow.
- Form hydration is intentionally one-shot per mounted screen to avoid replacing unsaved edits during background refetch.
- Profile saves still call `onProfileUpdated()` so the app-level user shell reflects the latest display name.

Validation performed:

- Ran targeted ESLint from `frontend`:
  - `npx.cmd eslint src/features/auth/AccountSettingsScreen.tsx src/api/authApi.ts src/api/watermarkApi.ts src/lib/queryKeys.ts --quiet`
- Ran `npm.cmd run build` from `frontend`.
- Both completed successfully.

Next recommended implementation step:

- Phase 2M: migrate the remaining ad hoc watermark consumers, such as validation previews and Facebook preview carousel reads, to shared query-backed hooks.

## Phase 2M Watermark Consumer Migration Status

Status: implemented on `feature/caching-architecture-phase2m`.

Changed files:

- `frontend/src/hooks/useWatermarkConfiguration.ts`
- `frontend/src/components/facebook/FacebookPreviewMediaCarousel.tsx`
- `frontend/src/features/validation/ValidationQueueScreen.tsx`
- `docs/md/caching-architecture-incremental-plan.md`

Previous behavior:

- `FacebookPreviewMediaCarousel` loaded watermark configuration through its own mount effect and local state.
- `ValidationQueueScreen` loaded watermark configuration through its own mount effect and local state.
- These consumers did not share in-flight requests or cached watermark configuration with Account Settings.
- Watermark read failures in these preview surfaces were intentionally silent.

New behavior:

- Added `useWatermarkConfiguration()` as the shared query-backed read hook for watermark configuration.
- The hook uses the existing `queryKeys.settings.watermark(...)` key shape and the authenticated query cache.
- Facebook preview carousel now consumes `useWatermarkConfiguration()` and keeps its silent failure behavior by treating missing query data as no watermark config.
- Validation Queue now consumes `useWatermarkConfiguration({ user })` so preview watermark data is scoped to the authenticated reviewer.
- Account Settings save invalidation from Phase 2L now also refreshes these preview consumers through the shared `settings` query group.

Query key:

```ts
queryKeys.settings.watermark({
  role: user?.role ?? "authenticated-preview",
  userId: user?.id ?? user?.email.trim().toLowerCase() ?? null,
  institutionId,
})
```

Freshness policy:

- Watermark configuration: `staleTime: 300_000`
- `gcTime: 5 minutes` inherited from `appQueryClient`
- `refetchOnWindowFocus: false` inherited from `appQueryClient`
- `retry: 1` inherited from `appQueryClient`

Network behavior:

- First preview consumer mount for a watermark scope: `NETWORK`.
- Additional consumers for the same scope share cached/in-flight query data.
- Return within 5 minutes: `CACHE`.
- Return after 5 minutes while cached data exists: `CACHE immediately + BACKGROUND REFRESH`.
- Saving watermark settings invalidates `settings` queries through the Phase 2L save path.
- Logout/login/modal reauth: `AUTHENTICATED QUERY CACHE CLEARED`.

Authentication isolation:

- Validation Queue passes the authenticated user into the watermark query key.
- Generic Facebook preview components that do not receive user props use a stable authenticated-preview scope.
- Auth tokens are not included in keys.
- Backend authorization remains the final authority for watermark visibility.

Risks:

- Generic Facebook preview components still do not receive user context, so their cache scope is intentionally generic until those preview props are widened.
- Silent preview failure behavior is preserved to avoid blocking submission review or preview rendering when watermark settings are unavailable.
- Account Settings keeps its local query setup from Phase 2L because it has form-hydration requirements that differ from passive preview consumers.

Validation performed:

- Ran targeted ESLint from `frontend`:
  - `npx.cmd eslint src/hooks/useWatermarkConfiguration.ts src/components/facebook/FacebookPreviewMediaCarousel.tsx src/features/validation/ValidationQueueScreen.tsx --quiet`
- Ran `npm.cmd run build` from `frontend`.
- Both completed successfully.

Next recommended implementation step:

- Phase 2N: review remaining authenticated imperative reads and either migrate them to query-backed hooks or explicitly document them as mutation/one-off workflows.

## Phase 2N Resolution Failures Migration And Imperative Read Audit Status

Status: implemented on `feature/caching-architecture-phase2n`.

Changed files:

- `frontend/src/hooks/useResolutionFailures.ts`
- `frontend/src/features/validation/ValidationQueueScreen.tsx`
- `frontend/src/lib/queryKeys.ts`
- `docs/md/caching-architecture-incremental-plan.md`

Previous behavior:

- `useResolutionFailures()` loaded failed publications through a mount/tick effect with local `loading`, `error`, and `failures` state.
- Manual publish detail loaded imperatively inside `openWorkflowPanel()`.
- Refreshes used a local tick counter instead of query invalidation.
- Resolution reads did not share authenticated query-cache lifecycle behavior.

New behavior:

- Failed-publication list reads now use TanStack Query through `queryKeys.resolution.failures(...)`.
- Manual publish detail reads now use TanStack Query through `queryKeys.resolution.detail(...)`.
- The resolution hook accepts the current `user` so list/detail keys are scoped by identity, role, and institution.
- Manual retry/start/cancel/complete workflows invalidate the `resolution` query group after successful mutations.
- Existing failure and detail-loading return fields are preserved for `ValidationQueueScreen`.
- Detail-load failure toasts are preserved.

Query keys:

```ts
queryKeys.resolution.failures({
  role: user.role,
  userId: user.id ?? user.email.trim().toLowerCase(),
  institutionId: user.institutionId ?? null,
})

queryKeys.resolution.detail({
  role: user.role,
  userId: user.id ?? user.email.trim().toLowerCase(),
  institutionId: user.institutionId ?? null,
  submissionId,
})
```

Freshness policy:

- Resolution failures: `staleTime: 30_000`
- Manual publish detail: `staleTime: 15_000`
- `gcTime: 5 minutes` inherited from `appQueryClient`
- `refetchOnWindowFocus: false` inherited from `appQueryClient`
- `retry: 1` inherited from `appQueryClient`

Network behavior:

- First Failed tab visit for a reviewer scope: `NETWORK`.
- Return within 30 seconds: `CACHE` for the failures list.
- Open a manual workflow panel: `NETWORK` for that submission detail unless cached/fresh.
- Return after stale time while cached data exists: `CACHE immediately + BACKGROUND REFRESH`.
- Manual retry/start/cancel/complete invalidates `resolution` queries.
- Logout/login/modal reauth: `AUTHENTICATED QUERY CACHE CLEARED`.

Authentication isolation:

- Resolution failures and detail keys include user identity, role, and institution context.
- Auth tokens are not included in keys.
- The centralized authenticated query cache is still cleared at auth boundaries through `clearAuthenticatedQueryCache()`.
- Backend authorization remains the final authority for resolution visibility and workflow actions.

Remaining imperative reads reviewed:

- `useSubmissions()` still owns the contributor submission list and lookup module caches; this is a larger composer/workspace migration because local create/edit/withdraw/delete flows currently mutate the hook-local list.
- Validation guard-rail checks, engagement recommendations, AI classification/media suggestions, and similar-media reads are request-on-demand workflows tied to user edits or modal actions.
- Media upload URL reads and selected media asset detail/history reads are short-lived workflow/detail requests.
- Messenger connection/link-code reads remain part of the account-channel setup flow.
- Calendar/detail/modal clipboard and UI effects are not data-cache candidates.

Risks:

- Resolution mutation success now invalidates query state instead of incrementing a local tick, so future cross-feature invalidations should use the same query group.
- Manual publish detail is keyed per submission and kept short-lived because workflow state can change quickly.
- `useSubmissions()` remains the largest non-query read surface and should be handled as its own phase.

Validation performed:

- Ran targeted ESLint from `frontend`:
  - `npx.cmd eslint src/hooks/useResolutionFailures.ts src/features/validation/ValidationQueueScreen.tsx src/lib/queryKeys.ts --quiet`
- Ran `npm.cmd run build` from `frontend`.
- Both completed successfully.

Next recommended implementation step:

- Phase 2O: migrate `useSubmissions()` and `useSubmissionLookups()` from module-level caches to query-backed hooks, including explicit cache updates after draft/save/submit/withdraw/delete flows.

## Phase 2O Submission List And Lookup Cache Migration Status

Status: implemented on `feature/caching-architecture-phase2o`.

Changed files:

- `frontend/src/hooks/useSubmissions.ts`
- `frontend/src/features/submission/SubmissionScreen.tsx`
- `docs/md/caching-architecture-incremental-plan.md`

Previous behavior:

- `useSubmissions()` kept a module-level list cache with manual timestamps and a mount effect.
- `useSubmissionLookups()` kept a module-level lookup cache with manual timestamps and a mount effect.
- Submission list updates from save/submit/withdraw/delete wrote into local hook state and the module cache.
- Refresh manually called `listSubmissions()` or `getSubmissionLookups()` outside the authenticated query cache.

New behavior:

- `useSubmissions(user)` now loads the contributor submission list through TanStack Query.
- `useSubmissionLookups(user)` now loads composer lookup/reference data through TanStack Query.
- Submission list cache is scoped by authenticated user, role, and institution context.
- Lookup cache is scoped by role and institution context.
- The existing `setSubmissions` API is preserved and now writes directly to the query cache.
- The existing `refresh` APIs are preserved and force a fresh query fetch.
- `SubmissionScreen` now passes the authenticated user into both hooks.
- Synchronous effect-body state updates touched by this phase were deferred to satisfy `react-hooks/set-state-in-effect`.

Query keys:

```ts
queryKeys.submissions.all({
  role: user.role,
  userId: user.id ?? user.email.trim().toLowerCase(),
  institutionId: user.institutionId ?? null,
})

queryKeys.submissions.lookups({
  role: user.role,
  institutionId: user.institutionId ?? null,
})
```

Freshness policy:

- Submission list: `staleTime: 30_000`
- Submission lookups: `staleTime: 300_000`
- `gcTime: 5 minutes` inherited from `appQueryClient`
- `refetchOnWindowFocus: false` inherited from `appQueryClient`
- `retry: 1` inherited from `appQueryClient`

Network behavior:

- First Submissions visit for a user scope: `NETWORK`.
- Return within 30 seconds: `CACHE` for the submission list.
- Return within 5 minutes: `CACHE` for composer lookups.
- Return after stale time while cached data exists: `CACHE immediately + BACKGROUND REFRESH`.
- Save/submit/withdraw/delete flows continue updating the list through `setSubmissions`.
- Explicit list refresh forces a fresh query fetch.
- Logout/login/modal reauth: `AUTHENTICATED QUERY CACHE CLEARED`.

Authentication isolation:

- Submission list keys include user identity, role, and institution context.
- Lookup keys include role and institution context because lookup limits are role/scope driven.
- Auth tokens are not included in keys.
- The centralized authenticated query cache is still cleared at auth boundaries through `clearAuthenticatedQueryCache()`.
- Backend authorization remains the final authority for submission visibility and composer limits.

Remaining imperative reads reviewed:

- Submission detail hydration still uses `getSubmission(id)` imperatively because it populates a large editable form and handles route/modal transitions.
- Composer templates, album names, and submission detail memory cache still use local reference caches and should be considered separately from the user submission list.
- Guard rails, engagement recommendations, media uploads, AI suggestions, and similar-media reads remain user-triggered workflow calls.

Risks:

- The hook-level `setSubmissions` API is preserved to reduce blast radius, but future cleanup can migrate save/submit/withdraw/delete flows to dedicated mutation hooks.
- List loading now follows query `isFetching`, so manual refresh continues to disable list refresh controls while a fetch is active.
- Composer reference caches remain outside TanStack Query in this phase.

Validation performed:

- Ran targeted ESLint from `frontend`:
  - `npx.cmd eslint src/hooks/useSubmissions.ts src/features/submission/SubmissionScreen.tsx src/lib/queryKeys.ts --quiet`
- Ran `npm.cmd run build` from `frontend`.
- Both completed successfully.

Next recommended implementation step:

- Phase 2P: migrate composer reference caches such as custom templates and media album name lookups, or document them as local composer-only caches if they should stay isolated.

## Phase 2P Composer Reference Cache Migration Status

Status: implemented on `feature/caching-architecture-phase2p`.

Changed files:

- `frontend/src/features/submission/SubmissionScreen.tsx`
- `frontend/src/lib/queryKeys.ts`
- `docs/md/caching-architecture-incremental-plan.md`

Previous behavior:

- Custom post templates used a module-level `cachedTemplates` object with manual timestamps.
- Existing media album names used a module-level `cachedAlbumsByInstitution` map.
- Both reference caches were cleared through `registerAppCacheReset()`.
- Template create/delete handlers updated local component state and the module cache manually.

New behavior:

- Custom post templates now load through TanStack Query using `queryKeys.submissions.templates(...)`.
- Existing album names now load through TanStack Query using `queryKeys.submissions.albumNames(...)`.
- Template create/delete handlers write the changed template list into the query cache and invalidate the `submissions` query group.
- Template and album load error toasts are preserved through query error effects.
- The composer no longer owns module-level caches for templates or album names.

Query keys:

```ts
queryKeys.submissions.templates({
  role: user.role,
  userId: user.id ?? user.email.trim().toLowerCase(),
  institutionId: selectedInstitutionId || null,
})

queryKeys.submissions.albumNames({
  role: user.role,
  userId: user.id ?? user.email.trim().toLowerCase(),
  institutionId: selectedInstitutionId,
})
```

Freshness policy:

- Composer templates: `staleTime: 120_000`
- Composer album names: `staleTime: 120_000`
- `gcTime: 5 minutes` inherited from `appQueryClient`
- `refetchOnWindowFocus: false` inherited from `appQueryClient`
- `retry: 1` inherited from `appQueryClient`

Network behavior:

- First composer visit for a template scope: `NETWORK`.
- First selected institution album-name lookup: `NETWORK`.
- Return within 2 minutes: `CACHE`.
- Return after 2 minutes while cached data exists: `CACHE immediately + BACKGROUND REFRESH`.
- Template create/delete writes the returned local change to cache and invalidates `submissions`.
- Logout/login/modal reauth: `AUTHENTICATED QUERY CACHE CLEARED`.

Authentication isolation:

- Template and album-name keys include user identity, role, and institution context.
- Auth tokens are not included in keys.
- The centralized authenticated query cache is still cleared at auth boundaries through `clearAuthenticatedQueryCache()`.
- Backend authorization remains the final authority for template and media album visibility.

Remaining imperative reads reviewed:

- Institution picker loading remains effect-driven because it also seeds the default institution into an empty admin/moderator form.
- Submission detail hydration remains imperative because it populates a large editable form and coordinates route/modal transitions.
- Submission detail memory cache remains in `submission/constants.ts` for list-card preview hydration and can be handled independently.
- Guard rails, engagement recommendations, media uploads, AI suggestions, and similar-media reads remain user-triggered workflow calls.

Risks:

- Template query scope includes `selectedInstitutionId`, matching template save payload scope and avoiding cross-institution leakage.
- Album-name query is disabled until an institution is selected.
- Template create/delete still use local cache writes to keep the UI responsive before invalidated data refetches.

Validation performed:

- Ran targeted ESLint from `frontend`:
  - `npx.cmd eslint src/features/submission/SubmissionScreen.tsx src/lib/queryKeys.ts --quiet`
- Ran `npm.cmd run build` from `frontend`.
- Both completed successfully.

Next recommended implementation step:

- Phase 2Q: review submission detail hydration and detail memory cache; either migrate read-only detail data to query keys or keep editor hydration imperative with explicit documentation.

## Phase 2Q Submission Detail Preview Cache Migration Status

Status: implemented on `feature/caching-architecture-phase2q`.

Changed files:

- `frontend/src/features/submission/SubmissionScreen.tsx`
- `frontend/src/features/submission/constants.ts`
- `docs/md/caching-architecture-incremental-plan.md`

Previous behavior:

- The My Submissions list loaded missing caption/media preview details through a `Promise.allSettled()` effect.
- Preview detail results were stored in local `listDetails` state and mirrored into `submissionDetailsMemoryCache`.
- `submissionDetailsMemoryCache` lived in `submission/constants.ts` and was cleared through `registerAppCacheReset()`.
- Full submission editor hydration still loaded details imperatively through `applySubmission()`.

New behavior:

- Missing My Submissions preview details now load through TanStack Query via `useQueries`.
- Each preview detail request uses the existing `queryKeys.submissions.detail(...)` key shape.
- Preview detail data is derived from query results instead of local state.
- `submissionDetailsMemoryCache` and its app-cache reset registration were removed.
- Full editor hydration remains imperative because it populates a large editable form, handles route state, and owns user-facing error recovery.

Query key:

```ts
queryKeys.submissions.detail({
  role: user.role,
  userId: user.id ?? user.email.trim().toLowerCase(),
  institutionId: item.institutionId || user.institutionId || null,
  submissionId: item.id,
})
```

Freshness policy:

- Submission preview detail: `staleTime: 120_000`
- `gcTime: 5 minutes` inherited from `appQueryClient`
- `refetchOnWindowFocus: false` inherited from `appQueryClient`
- `retry: 1` inherited from `appQueryClient`

Network behavior:

- My Submissions list rows with complete summary data do not trigger preview detail fetches.
- Rows missing caption/media preview data request detail data once per keyed submission scope.
- Return within 2 minutes: `CACHE`.
- Return after 2 minutes while cached data exists: `CACHE immediately + BACKGROUND REFRESH`.
- Save/submit/withdraw/delete list cache updates from Phase 2O still update the summary list path.
- Logout/login/modal reauth: `AUTHENTICATED QUERY CACHE CLEARED`.

Authentication isolation:

- Preview detail keys include user identity, role, institution context, and submission id.
- Auth tokens are not included in keys.
- The centralized authenticated query cache is still cleared at auth boundaries through `clearAuthenticatedQueryCache()`.
- Backend authorization remains the final authority for submission detail visibility.

Imperative detail hydration decision:

- `applySubmission()` remains imperative in this phase because it is not a passive read surface.
- It coordinates route-selected submissions, form hydration, media picker state, revision feedback modal state, dirty-signature tracking, and error navigation.
- A future mutation-hook/editor-state refactor could split read-only submission detail from editable form hydration, but that would be larger than this cache migration.

Risks:

- `useQueries` creates one query per queued row that lacks summary preview data; complete summary rows avoid extra requests.
- Preview detail failures remain silent at the row level, matching the previous fallback behavior of rendering empty preview data.
- Detail cache freshness is short enough to avoid long-lived stale media previews while still removing repeated remount fetches.

Validation performed:

- Ran targeted ESLint from `frontend`:
  - `npx.cmd eslint src/features/submission/SubmissionScreen.tsx src/features/submission/constants.ts src/lib/queryKeys.ts --quiet`
- Ran `npm.cmd run build` from `frontend`.
- Both completed successfully.

Next recommended implementation step:

- Phase 2R: review institution picker loading in the composer and decide whether to reuse the institution query cache while preserving default institution seeding.

## Phase 2R Composer Institution Picker Migration Status

Status: implemented on `feature/caching-architecture-phase2r`.

Changed files:

- `frontend/src/features/submission/SubmissionScreen.tsx`
- `frontend/src/lib/queryKeys.ts`
- `docs/md/caching-architecture-incremental-plan.md`

Previous behavior:

- The admin/moderator composer loaded posting institutions through a component effect.
- Active institutions were stored in local component state.
- Loading and error state were tracked manually for the Posting As picker.
- The same effect also seeded the default DASIG institution into a new empty form.

New behavior:

- Posting institution options now load through TanStack Query.
- Added `queryKeys.institutions.composerOptions(...)` so the lightweight composer list does not conflict with the richer institution-management registry query.
- The query still filters inactive institutions before exposing picker options.
- Loading and error state are derived from query state.
- Default DASIG institution seeding is preserved as a guarded effect that runs after active institutions are available.

Query key:

```ts
queryKeys.institutions.composerOptions({
  role: user.role,
  userId: user.id ?? user.email.trim().toLowerCase(),
})
```

Freshness policy:

- Composer institution options: `staleTime: 300_000`
- `gcTime: 5 minutes` inherited from `appQueryClient`
- `refetchOnWindowFocus: false` inherited from `appQueryClient`
- `retry: 1` inherited from `appQueryClient`

Network behavior:

- First admin/moderator composer visit: `NETWORK`.
- Return within 5 minutes: `CACHE`.
- Return after 5 minutes while cached data exists: `CACHE immediately + BACKGROUND REFRESH`.
- Contributors do not run this query because they do not use the Posting As picker.
- Logout/login/modal reauth: `AUTHENTICATED QUERY CACHE CLEARED`.

Authentication isolation:

- Composer institution option keys include user identity and role.
- Auth tokens are not included in keys.
- The centralized authenticated query cache is still cleared at auth boundaries through `clearAuthenticatedQueryCache()`.
- Backend authorization remains the final authority for institution visibility.

Remaining imperative reads reviewed:

- Full submission editor hydration remains imperative for now because it coordinates route state, form state, media picker state, revision modal state, and error navigation.
- Guard rails and engagement recommendations remain user-triggered schedule workflow reads.
- Upload URLs, AI suggestions, similar-media reads, and clipboard/UI effects remain one-off workflows.

Risks:

- This phase intentionally uses a separate composer institution key instead of `queryKeys.institutions.all(...)` to avoid shape conflicts with institution management data.
- Default institution seeding remains effect-driven because it mutates the draft form rather than only rendering fetched data.
- Picker error rendering is preserved with a simple inline message from query error state.

Validation performed:

- Ran targeted ESLint from `frontend`:
  - `npx.cmd eslint src/features/submission/SubmissionScreen.tsx src/lib/queryKeys.ts --quiet`
- Ran `npm.cmd run build` from `frontend`.
- Both completed successfully.

Next recommended implementation step:

- Phase 2S: review schedule helper reads such as guard rails and engagement recommendations, and decide which should remain request-on-demand versus query-backed by scheduled time/institution.

## Phase 2S Schedule Helper Cache Migration Status

Status: implemented on `feature/caching-architecture-phase2s`.

Changed files:

- `frontend/src/features/submission/SubmissionScreen.tsx`
- `frontend/src/api/submissionApi.ts`
- `frontend/src/lib/queryKeys.ts`
- `docs/md/caching-architecture-incremental-plan.md`

Previous behavior:

- Engagement recommendations were loaded through a component effect on schedule-step entry.
- Recommendation data and loading state were stored in local component state.
- Guard-rail validation used a debounced imperative POST, but stale in-flight requests were not wired to the cleanup abort signal.

New behavior:

- Engagement recommendations now load through TanStack Query.
- Added `queryKeys.submissions.engagementRecommendations(...)` for the schedule helper cache.
- Recommendation loading and data are derived from query state and are enabled only while the editable composer is on the schedule step.
- Guard rails remain a debounced request-on-demand POST validation because they depend on the current scheduled timestamp and draft id.
- Guard-rail validation now receives an `AbortSignal` and ignores canceled requests so stale schedule checks do not update component state.

Query key:

```ts
queryKeys.submissions.engagementRecommendations({
  role: user.role,
  userId: user.id ?? user.email.trim().toLowerCase(),
  institutionId: selectedInstitutionId || null,
})
```

Freshness policy:

- Engagement recommendations: `staleTime: 120_000`
- Guard rails: uncached, debounced request-on-demand validation
- `gcTime: 5 minutes` inherited from `appQueryClient`
- `refetchOnWindowFocus: false` inherited from `appQueryClient`
- `retry: 1` inherited from `appQueryClient`

Network behavior:

- First eligible schedule-step entry for a user/institution scope: `NETWORK`.
- Return within 2 minutes: `CACHE`.
- Return after 2 minutes while cached data exists: `CACHE immediately + BACKGROUND REFRESH`.
- Fast-track posts, read-only submissions, and admin composers without an institution do not load recommendations.
- Guard rails still revalidate after scheduled date/time changes and cancel stale in-flight checks during cleanup.
- Logout/login/modal reauth: `AUTHENTICATED QUERY CACHE CLEARED`.

Authentication isolation:

- Engagement recommendation keys include user identity, role, and institution context.
- Auth tokens are not included in keys.
- The centralized authenticated query cache is still cleared at auth boundaries through `clearAuthenticatedQueryCache()`.
- Backend authorization remains the final authority for recommendation and guard-rail visibility.

Request-on-demand decision:

- Guard rails are intentionally not cached because the result is tied to the exact draft schedule and must stay sensitive to live edits.
- Engagement recommendations are cache-backed because the API is a read-only GET scoped by institution and reused while the user works in the composer.

Risks:

- Recommendation freshness is scoped by institution rather than exact schedule time, matching the existing API contract.
- Guard-rail validation remains imperative, so a broader editor-state refactor would be needed before it could become a query without changing validation semantics.

Validation performed:

- Ran targeted ESLint from `frontend`:
  - `npx.cmd eslint src/features/submission/SubmissionScreen.tsx src/api/submissionApi.ts src/lib/queryKeys.ts --quiet`
- Ran `npm.cmd run build` from `frontend`.
- Both completed successfully.

Next recommended implementation step:

- Phase 2T: review remaining one-off composer reads such as AI caption suggestions, similar-media lookup, and modal detail hydration, then either document them as request-on-demand workflows or migrate any passive GET reads to query-backed hooks.

## Phase 2T AI Helper Read Review Status

Status: implemented on `feature/caching-architecture-phase2t`.

Changed files:

- `frontend/src/hooks/useSimilarMedia.ts`
- `frontend/src/api/aiApi.ts`
- `frontend/src/lib/queryKeys.ts`
- `docs/md/caching-architecture-incremental-plan.md`

Previous behavior:

- Similar-media recommendations were fetched through hook-local state and a `fetchedForRef` guard.
- Similar-media requests did not receive a query cancellation signal.
- AI caption generation, AI media suggestion, and AI classification helpers were still effect or action driven.

New behavior:

- Similar-media recommendations now load through TanStack Query.
- Added `queryKeys.ai.similarMedia(...)` for the submission-scoped similar-media read.
- `getSimilarMedia(...)` now accepts an optional `AbortSignal`.
- The similar-media hook derives `idle`, `loading`, `ready`, `empty`, and `error` states from query state while preserving the existing return contract.
- The `media_recommendation/shown` log remains outside the query function so cached reads stay side-effect free.

Query key:

```ts
queryKeys.ai.similarMedia({
  submissionId,
})
```

Freshness policy:

- Similar media: `staleTime: 120_000`
- `gcTime: 5 minutes` inherited from `appQueryClient`
- `refetchOnWindowFocus: false` inherited from `appQueryClient`
- `retry: 1` inherited from `appQueryClient`

Network behavior:

- First eligible similar-media render for a saved submission with saved assets: `NETWORK`.
- Return within 2 minutes for the same submission: `CACHE`.
- Manual refresh calls `query.refetch()`.
- Query cancellation propagates through the AI API helper when the component unmounts or the query is canceled.
- Logout/login/modal reauth: `AUTHENTICATED QUERY CACHE CLEARED`.

Authentication isolation:

- Similar-media keys are scoped by submission id.
- Auth tokens are not included in keys.
- The centralized authenticated query cache is still cleared at auth boundaries through `clearAuthenticatedQueryCache()`.
- Backend authorization remains the final authority for AI recommendation visibility.

Request-on-demand decisions:

- AI caption assist stays imperative because it is a POST generation workflow with prompt, tone, rate-limit state, and user-visible variants.
- AI media suggestions stay imperative because they POST draft text/category/tag context and are tied to debounced composer edits.
- AI classification stays action driven because accepting/dismissing suggestions mutates editor state and logs explicit user decisions.
- Editable submission detail hydration remains imperative because it populates form state, picker state, revision modal state, routing state, and dirty-signature tracking.
- Media-library asset prefill from `?assetIds=` remains a route action because it hydrates a pending composer selection exactly once.

Risks:

- `queryKeys.ai.similarMedia(...)` is submission-scoped rather than user-scoped; authenticated cache clearing and backend authorization preserve boundary safety.
- The similar-media hook is currently reusable infrastructure and has no active caller in `SubmissionScreen`; this migration keeps it ready without expanding the composer surface.
- Manual refresh preserves the previous user-triggered behavior but now participates in query retry and cancellation behavior.

Validation performed:

- Ran targeted ESLint from `frontend`:
  - `npx.cmd eslint src/hooks/useSimilarMedia.ts src/api/aiApi.ts src/lib/queryKeys.ts --quiet`
- Ran `npm.cmd run build` from `frontend`.
- Both completed successfully.

Next recommended implementation step:

- Phase 2U: review remaining submission detail hydration and asset prefill paths, then decide whether to keep documenting them as editor workflow orchestration or split passive detail reads into a dedicated query-backed hook.

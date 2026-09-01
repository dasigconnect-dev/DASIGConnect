# DASIGConnect Data Loading Performance Optimization Plan

## Purpose

This document defines the priority list for the next DASIGConnect performance pass. The focus is data loading across frontend, backend, database, and rendering paths, not only image optimization.

No implementation should start until baseline measurements are captured and the affected endpoints/components are confirmed. The goal is to reduce perceived loading time without changing authentication behavior, authorization rules, tenant isolation, database meaning, or existing user workflows.

## Branch Recommendation

Create a new branch before development starts:

```text
fix/data-loading-performance-audit
```

Reason: this work can touch frontend request orchestration, backend query shape, response DTOs, pagination, caching, and possibly Flyway indexes. It should not be mixed into the existing frontend image optimization branch or role/security hardening branches.

## Measurement First

Before making changes, capture baseline metrics using a production build and a browser profile with extensions disabled.

Required baseline checks:

- Test production build, not only Vite development mode.
- Disable browser extensions such as McAfee WebAdvisor before measuring.
- Capture Chrome Network data for every data-heavy page.
- Capture Chrome Performance trace for frontend scripting, rendering, and layout cost.
- Record request duration, TTFB, response size, download time, JSON parse time, React render time, and DOM rendering cost.
- Separate API/database delay from image download/decode/rendering delay.
- Keep before-and-after numbers for every optimization.

## Priority 1: Endpoint And Page Inventory

Build a table of pages, endpoints, response size, timing, and owner component.

Pages to inspect first:

- Dashboard
- Media Repository
- Review Queue
- Audit Log
- User Management
- Analytics Dashboard
- Calendar
- Notifications

Data to map:

- Total users
- Institutions
- Submissions
- Published content
- Pending content
- Media records
- Notifications and unread counts
- Audit log records
- Dashboard statistics
- Review queue items

Expected output:

- A measured list of slow endpoints.
- A measured list of large responses.
- A measured list of duplicate requests.
- A measured list of expensive frontend renders.

## Priority 2: Media Repository Loading Diagnosis

This is the highest perceived-load risk area.

Measure separately:

- API request time.
- Database query time.
- JSON response size.
- Number of media records returned on first load.
- Number of image requests triggered on first load.
- Image formats and byte sizes.
- Image decode time.
- React render time for grid/list cards.
- Duplicate image or API requests.

Likely fixes only if confirmed by measurements:

- Server-side pagination for media lists.
- Return lightweight media list DTOs for card views.
- Keep full-resolution media only for detail/lightbox views.
- Continue using optimized thumbnails for card/grid views.
- Lazy-load below-the-fold thumbnails only.
- Preserve explicit dimensions/aspect ratios to keep CLS at `0.00`.

## Priority 3: Backend Query And Response Optimization

Inspect Spring Boot services, repositories, and DTOs for slow or oversized data access.

Check for:

- `findAll()` usage on data-heavy tables.
- Endpoints returning entire datasets for frontend-side counts.
- N+1 query patterns.
- Unnecessary joins or eager loading.
- Fetching entities when projections/DTOs are enough.
- Missing pagination on list endpoints.
- Missing indexes on measured slow filters/sorts.
- Repeated queries within one request.
- Large JSON responses with fields not needed by the current UI.

Likely fixes only if confirmed:

- Server-side aggregate queries for dashboard counts and statistics.
- `Pageable` pagination for large list endpoints.
- DTO projections for list/card responses.
- Targeted Flyway indexes for measured slow query patterns.
- Short transactions and no external API calls while holding DB connections.
- Cache only low-volatility data, such as institution lists, where safe.

Avoid:

- Caching frequently changing submission data.
- Weakening tenant isolation or RLS assumptions.
- Replacing authorization checks with frontend-only filtering.
- Adding indexes without query evidence.

## Priority 4: Frontend Request Pipeline

Inspect React screens and API hooks for avoidable delays.

Check for:

- Duplicate API requests on page load.
- `useEffect` dependency mistakes.
- Sequential requests that can safely run in parallel.
- Requests triggered by components that are not visible.
- Repeated state updates from one response.
- Expensive client-side filtering or sorting of large datasets.
- Large JSON parsing cost on the main thread.
- Rendering large lists without pagination or virtualization.
- Secondary data blocking critical summary content.

Likely fixes only if confirmed:

- Parallelize independent requests with `Promise.all`.
- Defer secondary data until after critical content renders.
- Add server-state caching where it matches existing patterns.
- Split summary data from heavy detail data.
- Use pagination before virtualization when the backend can support it.
- Virtualize only genuinely large lists after profiling confirms render cost.

## Priority 5: Dashboard And Statistics

Dashboard values should not require downloading complete datasets.

Audit whether the frontend currently calculates:

- Total users.
- Total submissions.
- Published count.
- Pending/review count.
- Institution totals.
- Media totals.
- Notification totals.

If any dashboard count is calculated from full list responses, move it to backend aggregate queries.

Preferred backend shape:

```text
GET /api/v1/dashboard/summary
```

Return only the counts and small summaries needed for the first dashboard paint.

## Priority 6: Audit Log And User Management

These areas can become slow as data grows.

Audit:

- Whether audit logs are paginated server-side.
- Whether metadata/raw JSON fields are returned before the user opens details.
- Whether user lists are paginated and filtered on the backend.
- Whether counts use aggregate queries instead of full user lists.
- Whether admin-only data is still protected by backend authorization.

Likely fixes:

- Lightweight list DTO for audit rows.
- Detail endpoint for full audit metadata if needed.
- Server-side pagination, filtering, and sorting.
- Aggregate count endpoints for role/status totals.

## Priority 7: Database Index Review

Only add indexes after measuring slow queries.

Candidate columns to verify with `EXPLAIN ANALYZE`:

- `submissions.institution_id`
- `submissions.status`
- `submissions.created_at`
- `submissions.scheduled_at`
- `media_assets.institution_id`
- `media_assets.created_at`
- `audit_log.timestamp`
- `audit_log.actor_user_id`
- `audit_log.entity_id`
- `users.institution_id`
- `users.role`
- `notifications.user_id`
- `notifications.read_at`

Rules:

- Add indexes through a new Flyway migration only.
- Do not edit existing pushed migrations.
- Avoid broad indexes without proven query benefit.
- Validate that indexes do not conflict with write-heavy workflows.

## Priority 8: Production Verification

After each optimization:

- Rebuild the frontend production bundle.
- Run backend tests relevant to changed services/controllers.
- Re-test affected pages with extensions disabled.
- Compare before-and-after Network and Performance metrics.
- Confirm auth, role restrictions, tenant scoping, and API error handling still behave correctly.
- Confirm no new browser console errors, React warnings, or CSP violations.

## Suggested Implementation Order

1. Create branch `fix/data-loading-performance-audit`.
2. Capture baseline measurements and document the slowest endpoints/pages.
3. Fix duplicate frontend requests and safe request parallelization.
4. Add backend aggregate endpoints for dashboard/statistics if measured.
5. Add server-side pagination and lightweight DTOs for heavy lists.
6. Add targeted DB indexes only after query proof.
7. Re-measure production build and record before/after results.

## Non-Goals For This Pass

- No database schema redesign.
- No role or permission model changes.
- No speculative dependency replacements.
- No broad caching of frequently changing submission data.
- No security header or CSP weakening.
- No changes to full-resolution media inspection behavior unless measured as necessary.


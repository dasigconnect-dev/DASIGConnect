# Submission and Review Queue Server Pagination Development Plan

## 1. Document Purpose

This document defines the implementation plan for making **My Submissions** and the **Review Queue** scale safely as submission volume increases.

The work must preserve the current frontend design. It must not redesign, restyle, rename, remove, or reorganize the existing cards, tabs, search controls, loading indicators, selected-submission panel, responsive layout, or infinite-scroll interaction.

The implementation changes only the data contract, database queries, React Query cache shape, request lifecycle, and list rendering inputs.

## 2. Baseline

Plan created from branch:

```text
perf/submission-review-server-pagination
```

Baseline commit:

```text
c50f65f fix(media): push semantic search filtering into SQL instead of full-table load (#310)
```

The branch was created after fast-forwarding local `dev` to `origin/dev`.

### Implementation Progress

| Phase | Status | Verification |
| --- | --- | --- |
| Phase 1 - Contract and My Submissions Backend | Complete | New additive endpoint, bounded page sizes, ownership scope, server bucket/search handling, complete bucket counts, batched page media previews, controller/service coverage, application-context query parsing |
| Phase 2 - My Submissions Frontend | Complete | Server-backed 20-item infinite query, debounced bucket/search requests, page cache reuse, mutation cache reset, direct ID-scoped detail loading, production build and focused lint |
| Phase 3 - Review Queue Backend | Complete | Additive paginated endpoint, server view/search/sort handling, complete tab counts, batched media previews, frozen snapshot preservation, authorization/service coverage, application-context query parsing |
| Phase 4 - Review Queue Frontend | Not started | Pending |
| Phase 5 - Failed Publications | Not started | Pending |
| Phase 6 - Indexing, Measurement, and Cleanup | Not started | Pending representative-data measurements |

Phase 1 verification completed on September 23, 2026:

```text
Focused suite: 79 tests passed
Full backend suite after the final compatibility refinement: 748 tests passed
Repository query parsing: verified by BackendApplicationTests context startup
```

Phase 2 verification completed on September 23, 2026:

```text
Frontend production build: passed
Focused ESLint for all changed frontend files: passed with no errors
Repository-wide ESLint: blocked by 48 pre-existing errors outside the Phase 2 changes
UI/CSS files changed: none
```

Phase 3 verification completed on September 23, 2026:

```text
Focused validation suite: 28 tests passed
Full backend suite: 754 tests passed
Repository query parsing: verified by BackendApplicationTests context startup
UI/CSS files changed: none
```

## 3. Verified Current Behavior

### 3.1 My Submissions

The frontend requests the complete authorized list from:

```http
GET /api/v1/submissions
```

The response is `SubmissionSummary[]`. The backend loads every submission authored by the current user through `findByContributorIdOrderByCreatedAtDesc(...)`, then loads preview-media information for all returned IDs.

The page filters and searches the complete array in the browser. `useIncrementalPagination` displays 8 records initially and reveals 8 more through an `IntersectionObserver` sentinel.

This limits rendered cards, but it does not limit:

- database rows read;
- response payload size;
- browser memory used by the query cache;
- client-side filtering work; or
- initial network transfer time.

Current cache freshness is 30 seconds.

### 3.2 Review Queue

The frontend requests complete arrays from:

```http
GET /api/v1/validation/queue
GET /api/v1/validation/queue?history=true
```

The active queue contains `pending`, `in_review`, and `needs_revision`. The history query contains post-review statuses. The page combines both arrays for the **All** tab and uses the history dataset for the Scheduled, Published, and Rejected tabs.

Search, status filtering, and sorting are currently performed in the browser. `useIncrementalPagination` displays 15 records initially and reveals 15 more through the existing sentinel.

`ValidationService` also performs a media-count query while mapping each queue/history item. This creates query amplification as the number of submissions grows.

Current queue cache freshness is 5 seconds.

### 3.3 Failed Tab

The Failed tab is part of the Review Queue page but uses a separate endpoint:

```http
GET /api/v1/resolution/failures
```

It returns the complete failure list. The controller retrieves the latest publication attempt separately for every failure, producing another per-record query pattern. The frontend displays 15 failures initially using the same client-side incremental hook.

### 3.4 Existing Database Indexes

The database currently has individual indexes for submission contributor, institution, status, and scheduled time. The paginated access patterns require composite indexes that match filtering and stable ordering. Any new index must be confirmed with PostgreSQL `EXPLAIN ANALYZE` against representative data before it is finalized.

## 4. Goals

1. Bound initial database, API, cache, and rendering work to a configured page size.
2. Preserve the existing infinite-scroll experience and visual design.
3. Move list search, status filtering, and sorting to the backend.
4. Preserve all existing role and ownership restrictions.
5. Preserve the current tab meanings and ordering rules.
6. Keep selected submission details cached independently by submission ID.
7. Prevent duplicate records, missing records, and stale tab counts after mutations.
8. Remove per-record media-count and publication-attempt queries from paginated list operations.
9. Introduce the change without breaking existing dashboard, media, or recent-activity consumers.

## 5. Non-Goals

- No visual redesign.
- No change to submission workflow states or review permissions.
- No change to review-lock behavior.
- No list virtualization unless later profiling proves it is necessary.
- No aggressive prefetching on slow connections.
- No removal of legacy list endpoints until every existing consumer has migrated.

## 6. Compatibility Strategy

The existing unpaged API functions are used by screens outside My Submissions and Review Queue. Replacing their response shape immediately would risk regressions.

The first implementation must therefore add new paginated endpoints and frontend API functions while leaving these legacy contracts operational:

```text
listSubmissions()
getValidationQueue()
getResolutionFailures()
```

The target screens will migrate to the paginated endpoints first. Other consumers can be migrated separately after their data requirements are verified. Legacy endpoints may only be removed in a later PR after a repository-wide usage check.

## 7. Pagination Model

### 7.1 Initial Delivery

Use Spring Data `Pageable` with a stable secondary sort by submission ID. This matches existing project pagination patterns and lowers implementation risk.

Default and maximum sizes:

```text
My Submissions: default 20, maximum 50
Review Queue: default 20, maximum 50
Review History: default 20, maximum 50
Failed Publications: default 20, maximum 50
```

Every ordering must include a deterministic ID tiebreaker. Without this, equal timestamps can move between pages.

### 7.2 Future Upgrade Condition

Review Queue records can move while a moderator is browsing. If production telemetry shows duplicate or skipped records during frequent concurrent updates, upgrade that endpoint to cursor/keyset pagination. Do not begin with a complex cursor implementation without evidence because the queue has custom status priority, Fast-Track priority, nullable schedule fields, and multiple user-selectable sort modes.

## 8. Proposed API Contracts

Exact path names may follow established controller naming, but the implementation must use separate paginated functions during migration.

### 8.1 Shared Page Response

```json
{
  "items": [],
  "page": 0,
  "pageSize": 20,
  "totalCount": 0,
  "totalPages": 0,
  "hasNext": false
}
```

Do not expose Spring's raw `Page` serialization as the public contract. Add an explicit DTO so the response remains stable across framework upgrades.

### 8.2 My Submissions

```http
GET /api/v1/submissions/page?page=0&pageSize=20&bucket=all&search=
```

Supported bucket values must match the existing UI:

```text
all
drafts
action-needed
submitted
published
failed
```

The backend must use the same status-to-bucket mapping currently implemented in `frontend/src/features/submission/utils.ts`.

The response must also provide unfiltered bucket totals so tab badges remain correct when only one page is loaded:

```json
{
  "items": [],
  "page": 0,
  "pageSize": 20,
  "totalCount": 0,
  "totalPages": 0,
  "hasNext": false,
  "counts": {
    "all": 0,
    "drafts": 0,
    "action-needed": 0,
    "submitted": 0,
    "published": 0,
    "failed": 0
  }
}
```

Search must preserve the fields currently checked by `matchesQueueSearch`.

### 8.3 Review Queue and History

Use one paginated query contract with an explicit view/status rather than downloading active and history arrays and combining them in the browser:

```http
GET /api/v1/validation/queue/page?view=all&sort=publish_slot&page=0&pageSize=20&search=
GET /api/v1/validation/queue/page?view=pending&sort=publish_slot&page=0&pageSize=20&search=
GET /api/v1/validation/queue/page?view=published&sort=submitted&page=0&pageSize=20&search=
```

Supported views must match the existing tabs:

```text
all
pending
in_review
needs_revision
scheduled
published
rejected
```

`failed` remains backed by the Resolution endpoint because it has a different DTO and workflow.

Supported sorts must match the current UI:

```text
publish_slot
submitted
```

The response must include counts for every non-failed Review Queue tab. Counts must be computed independently of the active search term unless the product explicitly decides that tab badges should represent search-result counts. The current behavior represents complete tab totals.

The endpoint must preserve:

- active queue urgency ordering;
- Fast-Track priority in active views;
- `needs_revision` placement and frozen review-snapshot rendering;
- descending history order;
- nullable schedule fallback behavior; and
- stable ID tiebreaking.

### 8.4 Failed Publications

```http
GET /api/v1/resolution/failures/page?page=0&pageSize=20&search=
```

The result must preserve the current `FailedPublication` data contract and return the latest publication attempt without issuing one query per submission.

## 9. Backend Development

### 9.1 DTOs

Add explicit page-response DTOs for:

- submission summaries and My Submissions counts;
- validation queue summaries and tab counts; and
- failed publication summaries.

Use `long` for total counts.

### 9.2 Repository Queries

Add paginated repository methods that enforce ownership and role scope in the query itself.

My Submissions must always include:

```text
contributor_id = authenticated user ID
```

Review Queue remains restricted by the controller's Moderator/Admin authorization and its current network-wide semantics.

Repository queries must support:

- bucket/status filtering;
- case-insensitive search over the same fields as the current frontend;
- stable sorting;
- page bounds; and
- count queries compatible with the filters.

Avoid fetching full entities or relationships that are not needed by `SubmissionSummaryDto`.

### 9.3 Batch Projection Data

For each returned page, obtain media counts and preview media in one batch query using only that page's submission IDs.

For Review Queue and History, replace `countBySubmissionId(...)` inside stream mapping with one grouped query for the page IDs.

For Failed Publications, replace `findTopBySubmissionIdOrderByAttemptedAtDesc(...)` inside stream mapping with a query that returns the latest attempt for all page IDs in one operation.

### 9.4 Service Layer

The service layer must:

1. Clamp invalid page sizes.
2. Validate supported bucket, view, and sort values.
3. Apply role/ownership scope before repository access.
4. Map only the current page to DTOs.
5. Preserve frozen `review_snapshot` handling for `needs_revision`.
6. Return counts needed for tab badges.
7. Keep transactions read-only and short.

### 9.5 Controller Layer

Controllers must remain thin. They should parse validated query parameters, call the service, and return the explicit response DTO inside the existing `ApiResponse` envelope.

Apply the same `@PreAuthorize` rules as the current endpoints.

### 9.6 Database Indexes

Create a new immutable Flyway migration only after query shapes are final.

Candidate indexes to test include:

```sql
(contributor_id, created_at DESC, id)
(status, scheduled_at, submitted_at, id)
(status, updated_at DESC, id)
```

These are candidates, not automatic requirements. Confirm actual PostgreSQL plans and avoid redundant indexes. Search across caption/description/tags may require trigram or full-text indexing if measured search latency warrants it; do not add broad text indexes without measurement.

## 10. Frontend Development

### 10.1 API Types and Functions

Add page-response TypeScript types and new API functions. Preserve existing functions until all their consumers are migrated.

### 10.2 React Query Keys

Paginated query keys must include every value that changes the response:

```text
authenticated user scope
role
institution scope where applicable
bucket or view
search term
sort mode
page size
```

Do not include transient UI-only state.

### 10.3 Infinite Queries

Use `useInfiniteQuery` for the migrated lists. Flatten `data.pages[].items` for the existing card renderer and use `hasNextPage` plus `fetchNextPage()` with the existing scroll sentinel.

The UI must continue to:

- show existing loaders and errors;
- keep already loaded cards visible during next-page fetches;
- prevent duplicate next-page calls;
- reset to the first page when filter, search, or sort changes;
- cancel obsolete requests through the existing Axios/React Query signal path; and
- avoid replacing the screen with a full loader during background refetch.

### 10.4 Search

Because search moves to the backend, debounce text input by approximately 300-500 ms while leaving the displayed input value immediate. An old search response must not overwrite a newer search result.

### 10.5 Selected Submission

Keep selected submission details in the existing ID-scoped detail query. Selection must not require the item to remain in the currently loaded page after a mutation.

If a deep-linked or previously selected submission is not in loaded pages, fetch its detail directly instead of downloading preceding pages.

### 10.6 Cache Updates and Invalidation

After create, update, submit, withdraw, delete, approve, request revision, reject, retry, or manual-publication actions:

1. Update or remove the affected record from all relevant loaded page caches where safe.
2. Invalidate tab counts and relevant list roots.
3. Preserve the selected detail cache when its data remains valid.
4. Never mix cached pages from different users, roles, filters, or searches.
5. Keep login/logout cache clearing behavior intact.

Where exact page placement cannot be determined locally, invalidate and refetch rather than guessing.

## 11. Exact Files Expected to Change

### Backend

```text
backend/src/main/java/com/dasigconnect/backend/controller/SubmissionController.java
backend/src/main/java/com/dasigconnect/backend/controller/ValidationController.java
backend/src/main/java/com/dasigconnect/backend/controller/ResolutionController.java
backend/src/main/java/com/dasigconnect/backend/service/SubmissionService.java
backend/src/main/java/com/dasigconnect/backend/service/ValidationService.java
backend/src/main/java/com/dasigconnect/backend/repository/SubmissionRepository.java
backend/src/main/java/com/dasigconnect/backend/repository/SubmissionMediaAssetRepository.java
backend/src/main/java/com/dasigconnect/backend/repository/PublicationAttemptRepository.java
backend/src/main/java/com/dasigconnect/backend/model/dto/submission/*Page*.java
backend/src/main/java/com/dasigconnect/backend/model/dto/validation/*Page*.java
backend/src/main/java/com/dasigconnect/backend/model/dto/resolution/*Page*.java
backend/src/main/resources/db/migration/V{next}__submission_list_pagination_indexes.sql
```

### Frontend

```text
frontend/src/api/submissionApi.ts
frontend/src/api/validationApi.ts
frontend/src/api/resolutionApi.ts
frontend/src/lib/queryKeys.ts
frontend/src/hooks/useSubmissions.ts
frontend/src/hooks/useDebouncedValue.ts
frontend/src/hooks/useResolutionFailures.ts
frontend/src/features/validation/hooks/useValidationQueue.ts
frontend/src/features/submission/SubmissionScreen.tsx
frontend/src/features/validation/ValidationQueueScreen.tsx
```

`useIncrementalPagination.ts` must remain available for unrelated client-side lists. Remove it from these two screens only after their server-backed infinite queries are verified.

### Tests

```text
backend/src/test/java/com/dasigconnect/backend/controller/SubmissionControllerTest.java
backend/src/test/java/com/dasigconnect/backend/service/SubmissionServiceTest.java
backend/src/test/java/com/dasigconnect/backend/service/ValidationServiceTest.java
```

Add Resolution controller/service/repository coverage where the current structure permits. Add focused frontend tests if the current test toolchain supports them; otherwise require build verification and a documented manual regression matrix.

## 12. Development Phases

### Phase 1 - Contract and My Submissions Backend

- Add explicit page DTOs.
- Add paginated My Submissions repository/service/controller path.
- Implement backend bucket filtering, search, stable sorting, counts, and bounded preview-media loading.
- Add controller and service tests.
- Keep the legacy endpoint unchanged.

Exit criteria:

- Page size is bounded.
- Ownership scope cannot be bypassed.
- Counts match complete authorized data.
- No full authored-submission list is loaded by the new endpoint.
- No per-submission media query is introduced.

### Phase 2 - My Submissions Frontend

- Add paginated API types/function.
- Convert `useSubmissions` list behavior to an infinite-query path for My Submissions.
- Move bucket and debounced search parameters into the query key/request.
- Reuse existing cards, tabs, loaders, and sentinel.
- Preserve mutations and selected-detail caching.

Exit criteria:

- Initial navigation requests only one page.
- Scrolling requests one additional page at a time.
- Filter/search changes cancel obsolete requests and reset pages.
- Returning to the page reuses fresh cached pages.
- Existing visual snapshots/layout remain unchanged.

Implemented September 23, 2026:

- `SubmissionScreen` no longer requests the unbounded legacy submissions list.
- Initial list work is bounded to 20 records; the existing sentinel requests one additional page at a time.
- Status tabs and the 350 ms debounced search are server parameters included in the scoped React Query key.
- Tab badges use complete backend counts instead of counts derived from loaded pages.
- Fresh pages remain cached for back navigation; explicit refresh resets the active query to page zero.
- Composer/detail routes keep using ID-scoped detail queries and do not trigger the legacy full-list request.
- Submission mutations clear paginated list caches so the next list visit fetches one fresh first page rather than refetching every previously loaded page.
- No stylesheet, component hierarchy, card markup, tab markup, or responsive-layout change was introduced.

### Phase 3 - Review Queue Backend

- Add a paginated queue/history endpoint with explicit view and sort parameters.
- Preserve active, history, Fast-Track, and frozen-snapshot semantics.
- Return complete tab counts independently from loaded page size.
- Batch media counts for page IDs.
- Add authorization, ordering, counts, and edge-case tests.

Exit criteria:

- No complete active/history arrays are required by the new endpoint.
- No per-row media-count queries occur.
- Equal timestamps have deterministic ordering.
- Existing review permissions and locks remain unchanged.

Implemented September 23, 2026:

- Added `GET /api/v1/validation/queue/page` as an additive Moderator/Admin-only endpoint; the legacy array endpoint remains available during frontend migration.
- Added explicit `view`, `sort`, `page`, `pageSize`, and `search` parameters with a default page size of 20 and a maximum of 50.
- Preserved active-tab Fast-Track priority and ascending order, history-backed descending order, nullable publish-time fallback, and stable ID tiebreaking.
- Returned complete network-wide tab counts independently of the selected page and search term.
- Replaced per-row media-count lookups with one ordered media-preview query for the current page and reused the same batching path for legacy queue/history responses.
- Preserved frozen `needs_revision` snapshots without loading live media rows and declared Jackson Java-time support required by the snapshot fields.
- Added endpoint authorization/contract tests and service coverage for paging bounds, normalized views, ordering flags, counts, invalid parameters, media batching, and frozen snapshots.
- No review mutation, lock, role permission, frontend component, or stylesheet was changed.

### Phase 4 - Review Queue Frontend

- Convert Review Queue lists to infinite queries.
- Stop combining complete active and history arrays in the browser.
- Move search/filter/sort into the request and query key.
- Preserve selection, mobile master-detail behavior, and current loaders.
- Verify every review mutation updates or invalidates the correct cached views.

Exit criteria:

- Each tab loads only its requested page.
- Tab badges remain complete and accurate.
- Approve/revise/reject moves records to the correct views without stale duplicates.
- No UI or CSS changes are required.

### Phase 5 - Failed Publications

- Add paginated failures endpoint and backend search.
- Batch latest publication-attempt lookup.
- Convert the Failed tab to an infinite query.
- Preserve retry and manual-publishing workflows.

Exit criteria:

- Initial Failed-tab load is bounded.
- Opening a failure still loads full detail independently.
- Retry/resolution actions remove or update the affected cached record and count.
- No per-failure latest-attempt query occurs.

### Phase 6 - Indexing, Measurement, and Cleanup

- Seed or generate representative large datasets in a non-production environment.
- Capture query plans and response timings before and after indexes.
- Add only indexes supported by evidence.
- Confirm payload size and request count improvements.
- Audit remaining legacy endpoint consumers.
- Remove dead client-side pagination wiring from migrated screens.
- Retain legacy endpoints until all remaining consumers are migrated.

## 13. Required Test Matrix

### Backend Contract

- Empty dataset.
- First, middle, and final pages.
- Requested page beyond the final page.
- Default, minimum, maximum, and excessive page sizes.
- Invalid bucket/view/sort values.
- Deterministic ordering when timestamps are equal.
- Search with mixed case and surrounding whitespace.
- Counts with and without active search.

### Authorization

- Contributor only receives personally authored submissions.
- Moderator and Admin retain current Review Queue access.
- Contributor cannot access Review Queue pagination.
- Account/role switching cannot reuse another user's page cache.

### My Submissions Behavior

- Every existing bucket maps to the same statuses as before.
- Create and delete update lists/counts.
- Submit and withdraw move records between buckets.
- Search across existing supported fields returns equivalent results.

### Review Queue Behavior

- Pending, In Review, Needs Revision, Scheduled, Published, Rejected, All, and Failed tabs.
- Fast-Track ordering.
- Publish-slot and submitted-time sorts.
- Frozen snapshot for Needs Revision.
- Approve, Request Revision, and Reject transitions.
- Selected record remains usable during background refresh.
- Review lock behavior is unchanged.

### Network and Cache Behavior

- Slow connection keeps existing content visible while loading the next page.
- Failed next-page request shows the existing error/retry behavior without clearing prior pages.
- Rapid searches cancel obsolete requests.
- Navigating away and back reuses fresh cache.
- Logout clears authenticated pages.

## 14. Performance Verification

Measure with representative datasets such as 100, 1,000, and 10,000 submissions where practical.

Record:

- database query count per endpoint request;
- database execution time;
- API response duration;
- response payload size;
- initial records transferred;
- next-page request duration;
- browser heap/query-cache growth; and
- visible loading-state duration.

Measurements must be labeled as measured, calculated, estimated, or unavailable. Do not invent results.

## 15. Risks and Controls

| Risk | Control |
| --- | --- |
| Existing consumers expect arrays | Add new paginated endpoints/functions; retain legacy contracts during migration. |
| Records move between pages during review | Stable ID sorting, short stale time, mutation invalidation, and telemetry; consider cursor pagination if measured. |
| Tab counts become page-local | Return server-computed complete counts. |
| Search behavior changes | Reproduce current searchable fields and add equivalence tests. |
| Status bucket mismatch | Define one documented backend mapping and test it against the current frontend mapping. |
| Mutation leaves duplicates across pages | Centralize infinite-cache update helpers and invalidate when placement is uncertain. |
| N+1 queries remain | Assert bounded repository calls in tests and inspect SQL/query telemetry. |
| New indexes slow writes | Validate with query plans and add only justified composite indexes. |
| Cross-user cache leakage | Keep authenticated user, role, and institution scope in query keys and retain logout cleanup. |
| UI regression | Do not edit CSS or markup except data-state conditions required to wire existing components. |

## 16. Definition of Done

The development is complete only when:

1. My Submissions, Review Queue/History, and Failed Publications request bounded pages.
2. Existing UI design and interaction behavior are unchanged.
3. Search, filters, sorts, tab counts, selection, and mutations work across page boundaries.
4. Authorization and ownership tests pass.
5. Per-record media-count and latest-attempt query patterns are removed from paginated paths.
6. Backend tests pass.
7. Frontend production build passes.
8. Manual desktop and mobile regression checks pass.
9. Slow-network and navigation-back behavior are verified.
10. Performance measurements demonstrate bounded initial transfer and query work.
11. Legacy endpoints remain until all non-target consumers have been audited and migrated.

## 17. PR Strategy

Use small, independently reviewable PRs aligned with the phases above. Do not combine all backend and frontend migrations into one large change.

Recommended PR sequence:

```text
1. perf(submissions): add paginated submissions API
2. perf(submissions): use server-backed infinite loading
3. perf(validation): add paginated review queue API
4. perf(validation): use server-backed infinite loading
5. perf(resolution): paginate failed publications
6. perf(data): add measured pagination indexes and cleanup
```

After each PR is merged, update local `dev` from `origin/dev`, branch from the updated commit, and continue with the next phase.

# Handoff - 2026-06-08

## Universal validator workflow + validator self-submission

The professor-requested validator scope change is implemented locally:

- **Universal validation:** validators can now review, lock, approve, request revision, and reject
  submissions from any institution. `ValidationService` and `ReviewLockService` no longer block
  cross-institution validation or validator-authored submissions.
- **Validator content submission:** validators can use the submission form, AI caption/media
  suggestion endpoints, media attach-to-draft flows, and dashboard submit action.
- **Validator self-approval:** validator-authored submissions skip manual peer review on submit,
  move directly to `scheduled`, confirm the slot reservation, and write a validation audit entry
  with `action = self_approved`.
- **Attribution:** validation log DTOs now include the validator institution. Analytics full-report
  rows include `validatorName` and `validatorInstitutionName` for approved/self-approved content.
- **Analytics access:** `/analytics/audience` is administrator-only. Existing analytics role scope
  is retained: admin = network, validator = own institution analytics, contributor = own content.
- **Business decision:** Media Library remains institution-scoped for validators. Universal review
  happens in the validation queue where the submitted media is attached; cross-institution media
  reuse from the library is intentionally not opened.
- **Flyway:** new migration is `V43__validation_self_approval_action.sql`. V41 is already
  `facebook_content_insight_metrics`; V42 is `facebook_page_audience`. Next free version is V44.

Verification already run for the backend slice:

```powershell
.\mvnw.cmd test "-Dtest=SubmissionServiceTest,SubmissionControllerTest,AnalyticsControllerTest,MetricsAggregatorServiceTest"
```

Result: 47 tests, 0 failures. Frontend production build also passed with `npm.cmd run build`.

## Facebook insights sync — visibility + resilience fixes

Investigated "engagement on the Page isn't reflecting in Analytics." Findings + fixes:

- **Analytics is not live.** Views/Engagement/Audience read `facebook_post_metrics`; rows only land
  when a sync runs (admin **Sync insights** button, or `FacebookInsightsSyncJob` cron
  `0 15 */6 * * *` UTC). The button + System Operations panel are **administrator-only**
  (`AdminAnalyticsPanel`), so a contributor sees no sync control and no Facebook insight data.
- **Sync now reports outcomes.** `syncDueMetrics()` returns `SyncResult(due, synced, failed,
  reason)` (was a bare int); the controller returns `syncedPosts / duePosts / failedPosts / reason`;
  the dashboard toast distinguishes *synced X of Y* / *all failed — token permission* / *no due
  posts* / *not configured / not migrated*. Added a **"Last synced"** tile + footnote in System
  Operations (only DASIGConnect-published posts are tracked; reach/views lag Facebook).
- **`FacebookInsightsClient` made resilient to post object type:**
  - `attachments` is fetched **separately, best-effort** (some objects reject it with `(#100)
    nonexisting field (attachments)`, which previously failed the whole metric fetch).
  - Engagement uses page-post fields (`reactions/comments/shares`) and **falls back to media-object
    edges (`likes`/`comments`) for bare photo/video ids** (e.g. ids from `/{page}/videos`, which have
    **no** `{page}_{post}` underscore and don't expose `reactions`). Never throws on a field error —
    degrades to zeros so one odd post can't fail the whole sync.
- **Known cause of empty *Views* (not a bug):** post-insight metrics `post_impressions`,
  `post_impressions_unique`, `post_video_views`, etc. are **deprecated in Graph API v25.0**
  (`(#100) The value must be a valid insights metric`), so reach/impressions/views return null.
  Engagement works; **Views stays sparse** until valid v25.0 metric names are mapped or an honest
  "metric unavailable" state is shown. **Open follow-up** (overlaps Phase 5c below).
- **Benign noise:** Hikari `Failed to validate connection (… has been closed)` warnings are the
  Supabase pooler dropping idle connections (discarded + recreated, not data loss). Optional
  follow-up: tune `maxLifetime`/`keepaliveTime`.
- Verification: backend `test-compile` BUILD SUCCESS; `FacebookInsightsSyncServiceTest` +
  `AnalyticsControllerTest` green; frontend build + targeted ESLint clean. No migration added.

## Planned Phase 5c Facebook insights expansion

The richer Meta `read_insights` analytics belongs in **Phase 5**, as a planned Phase 5c expansion
on top of the current Facebook insights foundation:

- **Add now when implementing Phase 5c:** Page impressions/reach, Page post engagements, post
  impressions/reach, post clicks/actions, engagement rate, top-performing posts, follower/fan
  growth, and fans-online timing.
- **Add carefully:** aggregated audience breakdowns by country, city, locale, and age/gender. These
  must be shown as aggregate analytics only and may be unavailable when Page volume is too small.
- **Keep restricted:** negative feedback and spam/hide/unlike-style signals should be visible only
  to administrators and validators.
- **Implementation rule:** store whatever Meta returns as normalized snapshots, keep old metric names
  behind best-effort adapters, and render missing/deprecated metrics as "unavailable" instead of a
  broken dashboard.

## Content submission flow redevelopment (panel feedback)

The panel flagged the submission flow for redevelopment: the **AI caption is image-based**
(Claude Vision), but it lived in a Details-first wizard, so it fired before any media existed
("how can a caption suggestion work if there's no media to base on?"). The AI **media
suggestion** had the opposite problem — it required title/caption/tags **and** a manually saved
draft, forcing back-and-forth just to enable the button. Both fixed:

- **Wizard reordered to `Media Assets → Post Details → Preferred Schedule`.** Default/initial step
  is **Media**; new and loaded drafts open on Media; next/back order updated (`StepPanelActions`);
  only **Schedule** stays gated on Post Details completeness. So by the time the contributor reaches
  Post Details, media exists and the image-based caption AI has photos to work from.
- **Caption-AI button no longer vanishes.** When media/draft isn't ready it renders a **disabled
  "Suggest Caption" with a hint** ("Add a photo in Media Assets first — captions are generated from
  your selected images.") instead of disappearing. It enables once an image asset is present.
- **AI media suggestion: no more back-and-forth.**
  - **Auto-save:** `saveDraft()` now returns the draft id; new `ensureDraftSaved()` is passed to the
    picker as `onEnsureDraft`. AI flows call it, so the draft (and its media) is saved automatically
    — no manual "Save draft" to activate suggestions.
  - **Dual-mode AI tab:** **"More like your selection"** (image-based `similar-media`, enabled once a
    photo is added, **needs no text**) is the primary path; **"From your post details"** (text-based
    `suggest-media`) is the optional secondary; a friendly empty state replaces the old "save draft
    first" / "add a title first" dead-ends. `useAiMediaSuggestions` rewritten with
    `fetchSimilar`/`fetchFromDetails`.
- **Caveat:** `similar-media` ranks from an attached asset's embedding — **library-picked** photos
  return instantly; a **freshly uploaded** photo is embedded asynchronously (PROCESSING→READY), so
  right after upload "more like these" may briefly show the empty state until its embedding lands
  (retry covers it). State this as a known latency, not a bug.
- **Design note for the revised SRS:** media-first makes the *text→photo* suggestion no longer the
  primed path; the AI-media value is preserved via image-based *similar-media* — a deliberate design
  decision, not a regression.
- Verification: frontend `npm run build` + targeted ESLint clean (SubmissionScreen, AiCaptionButton,
  MediaAssetsPicker, AiSuggestedMediaTab, useAiMediaSuggestions). No migration; no backend schema change.

## Insights interactivity + per-content-type "reads"

- **Clickable KPI tiles drive the chart.** The 4 metric tiles on **Views** (Views / 3-second /
  1-minute / Watch time) and **Engagement** (Engagement / Reactions / Comments / Shares) are now
  selectable toggle buttons (`role="button"` + `aria-pressed`, keyboard Enter/Space); selecting one
  re-plots the trend area chart to that KPI's daily series. `ContentInsightDailyPointDto` +
  `AnalyticsRepository.contentInsightDailyTrend` now carry per-day uniqueViews / reactions /
  comments / shares / 15s / 60s views. No migration (read-only query change).
- **"Seen & read by content type."** Facebook exposes no literal "read" metric for text/photo, so
  the Views content-type panel now shows **impressions** (times shown) + **post clicks** (reads —
  "See more" expansions / photo opens) per format. This also fixes the prior bug where that panel
  used the **video-only** `views` field, so Photo/Text/Link rows showed 0. `ContentTypeInsightDto`
  + `contentTypeInsights` query gain `impressions` and `postClicks`. No migration.
- These are **post-level proxies** — Facebook gives no caption-read metric; values populate once
  posts are synced (dev currently has 0).
- Verification: backend analytics tests green (`MetricsAggregatorServiceTest`,
  `AnalyticsControllerTest`); frontend build + targeted ESLint clean.

## Insights redesign + page audience (UC-4.8 Phase 5b)

- Insights split into separate **Views / Engagement / Audience** pages (Facebook-style) with
  Recharts charts (area / donut / horizontal bars), metric tiles, a reusable chart kit
  (`MetricTile`, `InsightAreaChart`, `InsightDonut`, `BreakdownBars`, `InsightsScaffold`), and an
  updated analytics sidebar. Routes: `/analytics/views|engagement|audience`
  (`/analytics/insights` redirects to Views). Kept the app's light theme (FB structure, not its
  dark palette).
- Chart pages are **lazy-loaded** behind a `RouteErrorBoundary` so a charting dependency can never
  blank the whole app. **Recharts pinned to 2.x** — Recharts 3 + Vite 8 mis-bundled its
  `es-toolkit` dep (`require_isUnsafeProperty is not a function`) and crashed every chart page.
- **Phase 5b page audience:** `V42__facebook_page_audience.sql`
  (`facebook_page_audience_snapshots`, page-global / no tenant RLS), `FacebookPageAudienceService`
  + daily `FacebookPageAudienceSyncJob`, `FacebookInsightsClient.fetchPageAudience()` (followers
  + best-effort demographics), `GET /api/v1/analytics/audience-insights` (+ admin
  `POST .../sync`). Demographics are best-effort — Meta deprecated most `page_fans_*` metrics, so
  followers/growth is the reliable part; the Audience tab shows an honest "unavailable" note.
- Backend compiler warnings cleaned to **0** (deprecated `JsonNode.fields()` /
  `HttpStatus.UNPROCESSABLE_ENTITY`, unused methods/fields/imports/locals).
- **Flyway next free version is now V44** (V43 = validator self-approval action).
- Verification: backend **460 tests** pass; frontend `npm run build` + targeted ESLint clean.

## Latest Local Work

- Facebook analytics Phase 5 is now operationally wired after the Page token update work.
- Flyway migration conflict fixed: the connected dev database already used version `39` for
  `V39__seed_lerah_caones_contributor.sql`, so the Facebook metrics migration is now
  `V40__facebook_post_metrics.sql`.
- Dev database verification after migration:
  - `facebook_post_metrics` exists.
  - `flyway_schema_history` has `V40__facebook_post_metrics.sql` with `success=true`.
  - Current eligible published Facebook posts in the last 90 days: `0`.
  - Current metrics rows: `0`.
- Analytics is resilient while migrations are catching up:
  - `AnalyticsRepository.facebookEngagement(...)` returns an empty engagement summary if
    `facebook_post_metrics` is not present.
  - `FacebookInsightsSyncService.syncDueMetrics()` skips safely if the table is missing.
- Meta OAuth callback 403 fixed:
  - The callback was previously inside the admin-only `ExceptionHandlingController`, so Meta's
    browser redirect arrived without a DASIGConnect JWT and hit `403`.
  - New public callback controller: `FacebookOAuthCallbackController`.
  - Security boundary for the callback is the one-time OAuth `state` token generated by
    `TokenManagementService`.
  - Successful callback redirects back to
    `/admin/resolution?tab=system-audit&facebook=connected`.
- Manual Page token refresh path added:
  - `FacebookPublisherService.syncTokenFromEnv()` now refreshes an existing active
    `facebook_page_tokens` row from `FACEBOOK_PAGE_ACCESS_TOKEN` on backend startup.
  - The raw token must stay in `backend/.env`; the DB stores only the AES-GCM encrypted value.
- Manual Facebook insights sync added:
  - Backend endpoint: `POST /api/v1/analytics/facebook-insights/sync`.
  - Administrator-only.
  - Frontend Analytics dashboard now has a **Sync insights** button in the System Operations
    panel, with loading state, toast feedback, and summary refresh.

## Latest Verification

- Backend focused suite passed:
  `.\mvnw.cmd test "-Dtest=FacebookPublisherServiceTest,FacebookOAuthCallbackControllerTest,FacebookInsightsSyncServiceTest,MetricsAggregatorServiceTest,AnalyticsControllerTest,ResolutionControllerTest"`
  - Result: 27 tests, 0 failures.
- Frontend production build passed: `npm.cmd run build`.
- Local services check:
  - Frontend reachable at `http://localhost:5173`.
  - Backend reachable at `http://localhost:8080`; unauthenticated analytics returns `403`, as expected.

## Immediate Next Steps

1. Restart the backend so Flyway applies `V43__validation_self_approval_action.sql`.
2. Put the long-lived Page access token in `backend/.env`:
   `FACEBOOK_PAGE_ACCESS_TOKEN=<page-token>`.
3. Confirm startup log includes `Facebook page token refreshed from env for page ...`.
4. Open `http://localhost:5173/analytics` as an administrator.
5. Click **Sync insights**.
6. Browser-check the validator path: login as validator, open Submit Event Content, submit a draft,
   confirm it moves to scheduled, and confirm the validation queue still shows submissions from
   other institutions.
7. If the toast says no due posts were found, publish a post through DASIGConnect first so a
   submission has `platform_post_id` and `published_at` within the 90-day insights lookback.

---

# Handoff - 2026-06-07

## Latest Pushed Work

- Commit pushed to `origin/capstone2-advance-development`: `ea18b8e Improve bulk media deletion flows`.
- Repo was clean and synced after push.
- Multi-select pages now show **Select all** after one item is selected: Media Library, Collection contents, Trash, Submit Content library picker, AI Suggestions, and prompt Collection Builder.
- Upload and delete flows show animated dots plus live completed/total and remaining counts.
- Media Library bulk delete now processes assets one by one. If an asset is used by a draft, pending, in-review, scheduled, or active submission, the UI pauses and asks the user to skip it or force-delete it.
- Force delete retries with `force=true`, records the force flag in audit metadata, and warns that affected submissions can show missing media or fail validation/publishing until replacement media is attached.
- Trash delete is different: deleting from Trash is permanent purge. It does not ask skip/force because the asset has already been soft-deleted from the active library.

## Latest Verification

- `npm.cmd run build` in `frontend`: passed.
- Focused frontend ESLint on changed media/delete files: passed.
- `.\mvnw.cmd test "-Dtest=MediaAssetServiceTest,MediaAssetControllerTest"`: passed.

---

# Handoff - 2026-05-29 (Session 10)

## What Was Done This Session

### Task 1 — Admin "New Submission" → "Direct Post"
- **`MediaRepositoryScreen.tsx`** header button: admin sees "Direct Post", contributor sees "New Submission".
- **`AssetDetailPanel.tsx`** sidebar action button: added `isAdmin` prop; admin sees "Direct Post (N)", contributor sees "New Submission (N)". Prop is passed from `MediaRepositoryScreen`.

### Task 2 — Wire Admin Direct Post to Resolution Center
- **`MediaRepositoryScreen.tsx` `handleNewPost()`**: admin navigates to `/admin/resolution?tab=direct-post`; with selected assets appends `&assetIds=id1,id2,...`. Contributors still go to `/submissions/new`.
- **`ResolutionCenterScreen.tsx`**: reads `tab` and `assetIds` from query params on mount (`useSearchParams`). `tab` pre-selects the active tab; `assetIds` is passed as `initialAssetIds` prop to `DirectPostTab`. Query params are cleared from URL (`replace: true`) after consuming.
- **`DirectPostTab.tsx`**: accepts `initialAssetIds?: string[]` prop; initializes `mediaAssetIds` state from it. Assets are sent in the API payload as `mediaAssetIds`.

### Task 3 — Reuse Date Picker From Content Submission
- Extracted `CalendarDateField`, `TimePickerField`, and `TimeStepper` from `SubmissionScreen.tsx` into:
  - `frontend/src/components/form/dateTimeHelpers.ts` — pure helper functions (no React; satisfies `react-refresh/only-export-components`)
  - `frontend/src/components/form/DateTimePicker.tsx` — the three React components only
- `SubmissionScreen.tsx` now imports from the shared file; local definitions removed.
- `DirectPostTab.tsx` replaced native `<input type="datetime-local">` with `CalendarDateField` + `TimePickerField` in a `rc-dp-datetime-row` flex row.

### Task 4 — Component Reuse
- Handled by extraction above. No new duplicates introduced.

### Task 5 — Network View Assessment
- Network view IS connected to real data: it changes the asset fetch scope (`useMediaAssets(networkView, ...)`) and shows institution chips per card.
- Decision: **kept** — serves a genuine admin purpose (cross-institution asset browsing + access audit log).

### Task 6 — Wire Direct Post Media Assets
- `DirectPostTab.tsx` shows a blue badge "N assets selected from Media Library" when `mediaAssetIds` is non-empty.
- Clear button removes all attached assets from the payload.
- Preview panel shows "N media assets attached" note when assets are present.
- `scheduledAt` is now computed from `scheduledDate` + `scheduledTime` (YYYY-MM-DD + HH:MM → ISO string), matching the Content Submission contract.

### Task 7 — UI Enhancement
- Added CSS to `resolution.css`:
  - `.rc-dp-datetime-row` — flex row for CalendarDateField + TimePickerField side-by-side
  - `.rc-dp-media-badge` — blue accent badge for attached assets with clear button
  - `.rc-dp-preview-media-note` — media count note in preview card

## Files Changed

**New files:**
- `frontend/src/components/form/dateTimeHelpers.ts`
- `frontend/src/components/form/DateTimePicker.tsx`

**Modified files:**
- `frontend/src/features/submission/SubmissionScreen.tsx` — removed local CalendarDateField, TimePickerField, TimeStepper, usePopoverCollision, and 10 helper functions; imports from shared DateTimePicker.tsx
- `frontend/src/features/resolution/DirectPostTab.tsx` — new initialAssetIds prop, shared date pickers, media badge UI
- `frontend/src/features/resolution/ResolutionCenterScreen.tsx` — useSearchParams for tab/assetIds deep-link; passes initialAssetIds to DirectPostTab
- `frontend/src/features/media-repository/MediaRepositoryScreen.tsx` — role-aware header button and handleNewPost routing
- `frontend/src/features/media-repository/components/AssetDetailPanel.tsx` — isAdmin prop, conditional button label
- `frontend/src/styles/resolution.css` — new datetime-row, media-badge, preview-media-note styles

## Verification
- `npm.cmd run build`: **227 modules, 0 TypeScript errors** ✓
- `npx.cmd eslint --quiet` on all 7 changed/new files: **0 errors, 0 warnings** ✓

## What's Next

1. **Browser E2E — Admin Direct Post from Media Library:**
   - Login as admin → Media Library → select 1–3 assets → click "Direct Post"
   - Confirm routed to Resolution Center with Direct Post tab active and asset badge showing correct count
   - Fill caption/reason/institution → click "Publish Now" → confirm API call includes `mediaAssetIds`

2. **Browser E2E — Direct Post without assets:**
   - Navigate directly to `/admin/resolution` → click "Direct Post" tab → form should be empty (no badge)
   - Schedule for later → confirm CalendarDateField + TimePickerField work (same UX as Content Submission)

3. **Browser E2E — Contributor isolation:**
   - Login as contributor → Media Library → select assets → "New Submission" button still routes to `/submissions/new?assetIds=...` (not Resolution Center)

4. **Apply pending Flyway migrations** (if not already done):
   - V24 (UC-3.5 override requests), V25 (dual embeddings), V26 (publishing claim statuses)
   - Backend restart triggers Flyway automatically

5. **Known open gap:** `ManualPublishDetail` TypeScript interface still missing `lastManualPublishAbandonedAt: string | null` — re-add before browser testing UC-3.4 abandonment banner.

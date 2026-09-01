# Frontend Performance and Accessibility Optimization Plan

**Scope:** DASIGConnect frontend performance, accessibility, and asset-delivery improvements  
**Environment Reviewed:** Local Vite development environment and recent production build output  
**Branch Context:** `fix/frontend-review-queue-performance`  
**Date:** 2026-09-01

## Executive Summary

The initial frontend optimization pass already made the page shell much faster. The app now uses route-level lazy loading, route CSS splitting, non-blocking Tabler icon loading, safer development CSP handling, duplicate React key fixes, and small rendering improvements around image decoding and select positioning.

The remaining work should be split into safe frontend-only changes first, then larger media/storage/backend changes later. The next phase must avoid broad security relaxations, avoid database migrations unless a feature truly requires one, and preserve existing API contracts.

## Completed First-Wave Fixes

These changes are already developed on the current frontend performance branch.

| Area | Change | Risk Level | Notes |
| :--- | :--- | :--- | :--- |
| Route loading | Major screens are loaded with `React.lazy` and `Suspense` | Low | Reduces initial JavaScript loaded by the app shell. |
| CSS loading | Page-specific CSS imports moved out of `main.tsx` and into route components | Low | Prevents unrelated feature CSS from blocking the initial route. |
| Icon CSS | Tabler icon font stylesheet moved out of `index.html` and loaded after React starts | Low to Medium | Reduces render-blocking CSS. Requires visual check that icons still appear. |
| CSP | Development CSP allows only the legitimate backend image/media origin | Low | Keeps policy restrictive while fixing local image loading. |
| React keys | Duplicate `Uncategorized` category keys replaced with stable composite keys | Low | Fixes React warning without changing data. |
| Layout churn | `BrandedSelect` avoids repeated state updates when measured placement/height did not change | Low | Reduces avoidable forced layout work. |
| Image rendering | Added `decoding="async"` and lazy loading where safe | Low | Keeps important selected preview eager while making secondary images cheaper. |
| Duplicate route font imports | Removed repeated Google Font imports from media repository and notifications CSS | Low | Prevents duplicate route-level font CSS requests because the same families are already loaded globally with `display=swap`. |

## Priority 1 - Safe Frontend-Only Fixes

These should be done next because they do not require backend changes or database migrations.

### 1. Accessibility Contrast Pass

**Problem:** Several UI colors, especially `#1877F2` and light status/date text, may fail WCAG contrast when used as normal text on white backgrounds.

**Fix:** Introduce verified accessible color tokens and replace low-contrast text/status usage carefully.

**Important:** Do not blindly use `#166FE5` for all text. It may still be too light for normal text on white. Use a darker accessible blue such as `#0B5FCC` or another value verified by tooling.

**Likely files:**

- `frontend/src/styles/index.css`
- `frontend/src/styles/ui.css`
- `frontend/src/styles/analytics.css`
- `frontend/src/styles/audit-log.css`
- `frontend/src/styles/validation.css`
- `frontend/src/styles/media-repository.css`
- `frontend/src/styles/submission.css`
- `frontend/src/styles/settings.css`
- `frontend/src/features/**/*.tsx` files with inline `#1877F2`

**Risk controls:**

- Update tokens first, then only targeted direct color usages.
- Run visual checks on dashboard, review queue, analytics, audit log, media repository, and settings.
- Do not change brand assets or backend watermark data.

### 2. Main Landmark and Accessible Names

**Problem:** Pages may still report missing main landmarks or accessible-name mismatches.

**Fix:** Ensure each routed page has one clear `<main>` region and that icon buttons, dropdowns, and visible labels have matching accessible names.

**Likely files:**

- `frontend/src/components/layout/DashboardShell.tsx`
- `frontend/src/components/layout/DashboardLayout.tsx`
- `frontend/src/components/ui/BrandedSelect.tsx`
- Feature page components under `frontend/src/features/`

**Risk controls:**

- Keep markup changes structural only.
- Do not change route protection or data-fetch behavior.
- Run accessibility checks after each page group.

### 3. Review Queue Render Profiling

**Problem:** The scanner reported forced reflow warnings around 40-55 ms. Some were already reduced, but we should confirm with profiling before adding complex changes.

**Fix:** Use DevTools Performance or React Profiler to identify whether remaining reflows are from our components, browser autofill, icon font loading, or image layout.

**Likely files if optimization is needed:**

- `frontend/src/features/validation/ValidationQueueScreen.tsx`
- `frontend/src/features/validation/ManualPublishWorkflowPanel.tsx`
- `frontend/src/components/ui/BrandedSelect.tsx`

**Risk controls:**

- Profile first.
- Optimize only measured hot paths.
- Avoid suppressing warnings or removing useful layout behavior.

## Priority 2 - Medium-Risk Frontend Optimizations

These are still frontend-only, but they touch larger user workflows and need careful regression testing.

### 1. Split Heavy Submission Feature Internals

**Problem:** `SubmissionScreen.tsx` is still a large route chunk. Route lazy loading keeps it out of the initial shell, but once the submission page opens it still loads a lot at once.

**Fix:** Split expensive internal panels with dynamic imports, especially features that are not needed immediately.

**Candidate split points:**

- Media picker/modal
- AI caption tools
- Template picker
- Facebook preview/publishing panels
- Advanced metadata sections

**Likely files:**

- `frontend/src/features/submission/SubmissionScreen.tsx`
- `frontend/src/components/media/*`
- `frontend/src/features/submission/*`

**Risk controls:**

- Keep form state ownership unchanged.
- Add loading states for deferred panels.
- Test draft creation, media upload, submit, edit, and schedule flows.

### 2. Icon Strategy Cleanup

**Problem:** The app still loads a full external Tabler icon font, which adds CSS/font transfer and render work.

**Fix:** Replace broad icon-font dependency with local icon imports or a small SVG sprite for icons actually used.

**Likely files:**

- `frontend/src/app/App.tsx`
- `frontend/index.html`
- Components using `ti ti-*` classes

**Risk controls:**

- Inventory icons first with `rg "ti ti-"`.
- Convert feature by feature.
- Run screenshots because this is visually risky.

### 3. Further CSS Cleanup

**Problem:** Global CSS is smaller after route splitting but still broad.

**Fix:** Continue moving feature-specific selectors into route-loaded CSS, remove duplicate selectors, and keep global CSS limited to tokens, resets, layout primitives, and shared UI.

**Status:** In progress. Duplicate route-level imports for globally loaded fonts have been removed from media repository and notifications styles. Remaining cleanup should focus on selector ownership, not broad rewrites.

**Likely files:**

- `frontend/src/styles/index.css`
- `frontend/src/styles/ui.css`
- Feature CSS under `frontend/src/styles/`

**Risk controls:**

- Avoid large rewrites.
- Move only selectors clearly owned by one feature.
- Build and smoke test after each feature group.

## Priority 3 - Backend, Storage, and CDN Coordinated Work

These changes can produce the largest payload reduction for media-heavy pages, but they must be treated as separate work because they can affect uploads, stored assets, and API responses.

### 1. Thumbnail and WebP/AVIF Derivative Pipeline

**Problem:** Media grid cards may load original images from storage instead of smaller optimized thumbnails.

**Preferred fix:** Generate thumbnail derivatives on upload and serve thumbnails to list/grid views while preserving originals.

**Possible approaches:**

- Backend-generated WebP thumbnails stored beside originals.
- Cloudflare Worker or image-resizing service in front of R2/Supabase storage.
- Deterministic thumbnail object paths to avoid a database migration when possible.

**Likely files if backend path is chosen:**

- Backend media upload/service classes
- Media asset DTOs
- Storage client/service integration
- Frontend media card components

**Risk controls:**

- Do not delete or overwrite original uploads.
- Keep old `storageUrl` fields backward compatible.
- Add new optional thumbnail fields instead of replacing existing fields immediately.
- Use a separate branch and PR.
- Add tests for upload, fallback, and missing thumbnail behavior.

### 2. API Payload Slimming for List Views

**Problem:** Some list endpoints may return more data than cards/tables need.

**Fix:** Add lightweight summary DTOs for list views, while keeping detail endpoints complete.

**Candidate endpoints:**

- `GET /api/v1/submissions`
- `GET /api/v1/calendar`
- `GET /api/v1/audit-log`
- Media repository list endpoints

**Risk controls:**

- Do not remove existing response fields without a migration plan.
- Prefer additive DTOs or new query options.
- Keep role and tenant filtering unchanged.

## Priority 4 - Infrastructure and Measurement

These should be confirmed before making more invasive code changes.

| Task | Purpose | Risk |
| :--- | :--- | :--- |
| Confirm Brotli/Gzip in production | Reduce JS/CSS transfer size | Low |
| Confirm HTTP/2 or HTTP/3 | Make many requests cheaper | Low |
| Add bundle visualizer script | Identify exact dependency weight | Low |
| Add Lighthouse budgets | Prevent regressions | Low |
| Add repeatable WebPageTest/Lighthouse runs | Compare real mobile performance | Low |

## What Not To Do

- Do not add broad CSP permissions such as `unsafe-eval` unless a verified dependency requires it and there is no safer alternative.
- Do not suppress browser, React, or accessibility warnings just to make the console clean.
- Do not bulk-convert, overwrite, or delete original uploaded media.
- Do not introduce database migrations for frontend-only performance work.
- Do not virtualize small server-paginated lists unless profiling shows a real problem.
- Do not change authorization rules as part of performance work.

## Recommended Execution Order

1. Complete the frontend accessibility contrast and landmark pass.
2. Run a production build and Lighthouse scan on the key routes.
3. Profile Review Queue and Submission page interaction cost.
4. Split heavy Submission internals only where profiling or bundle analysis proves benefit.
5. Add bundle analyzer and performance budgets.
6. Plan media thumbnail/WebP delivery as a separate backend/storage branch.

## Validation Checklist

Run these checks before merging each phase.

| Check | Expected Result |
| :--- | :--- |
| `npm.cmd run build` | Production build succeeds. |
| Login smoke test | Login and token flow still work. |
| Dashboard smoke test | Dashboard loads without console errors. |
| Review Queue smoke test | Images load, no duplicate-key warning, no real CSP errors. |
| Submission smoke test | Create/edit/upload/submit flow still works. |
| Analytics smoke test | Charts load only when route is opened and remain readable. |
| Audit Log smoke test | Pagination and search still work. |
| Accessibility scan | No missing main landmark, contrast failures reduced or eliminated. |
| Security scan | CSP remains restrictive and no new broad allowances are added. |

## Database Safety Position

Priority 1 and Priority 2 should not touch the database. Priority 3 may require storage changes or new optional media metadata, but that work should be isolated in a separate branch with explicit migration review. Any media optimization must preserve existing uploaded originals and support fallback behavior when optimized derivatives are missing.

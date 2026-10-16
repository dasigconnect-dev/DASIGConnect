# DASIGConnect Demo Readiness Cleanup Plan

## 1. Purpose

Prepare DASIGConnect for demonstration by making the existing test and quality checks pass, removing verified dead or development-only code, filling high-risk test gaps, consolidating documentation, and producing concise evidence that connects implemented improvements to the MVP validation findings.

This plan does not authorize deleting academic deliverables or changing visible application behavior. Destructive cleanup must happen only after the team confirms the canonical copy of each document and creates a recoverable archive or relies on a verified Git-tracked version.

## 2. Verified Baseline

Audit date: 2026-10-16

| Check | Result | Immediate implication |
|---|---:|---|
| Backend `mvnw test` | 897 tests, 1 failure | Fix or update the stale authorization test before other backend work |
| Frontend `npm run build` | Pass | Production TypeScript and Vite build are currently valid |
| Frontend `npm run lint` | 70 errors, 12 warnings across 32 files | Resolve behavior-sensitive React lint errors before demo |
| Frontend automated coverage | 3 Playwright spec files | Critical Moderator, Administrator, media, calendar, and settings workflows need coverage |
| Backend controller inventory | 29 controllers | 11 controllers have no matching controller test class |
| Document/output inventory | 601 files, about 154 MB across `docs`, `output`, `outputs`, `tmp`, and `work` | Generated files and duplicate document versions need consolidation |

### Confirmed backend failure

`UserControllerTest.confirmAdminTransfer_asModerator_returnsIncomingAdmin` expects HTTP 200 but receives HTTP 403.

The implementation restricts `/api/v1/users/admin-transfer/confirm` to `ADMIN`, and `UserService.confirmAdminTransfer` also requires an active administrator. A separate `/api/v1/users/promotion/confirm` endpoint already permits an authenticated Moderator or Contributor to accept a pending promotion. This indicates a stale test expectation rather than evidence that the production authorization rule should be weakened.

Required resolution:

1. Confirm the SRS rule for ownership transfer.
2. Change the controller test to use an active `ADMIN` for ownership-transfer confirmation.
3. Retain and expand the existing Moderator promotion-confirmation test.
4. Add explicit 403 tests proving that Moderator and Contributor accounts cannot confirm an Admin ownership transfer.

### Confirmed frontend quality failures

The largest lint concentrations are:

| File | Errors | Warnings | Main issue class |
|---|---:|---:|---|
| `FancyTextTool.tsx` | 20 | 1 | Render-time ref access and hook dependencies |
| `FolderFloat.tsx` | 8 | 0 | `any`, render-time ref mutation, effect state, empty blocks |
| `ShapeGrid.tsx` | 4 | 0 | `any` and unused expressions |
| `TrashView.tsx` | 3 | 0 | React/hook correctness |
| `TextType.tsx` | 3 | 0 | Unused value and render-time ref access |
| `AiCaptionPromptDialog.tsx` | 3 | 0 | React/hook correctness |
| `ToastContext.tsx` | 3 | 0 | Fast-refresh export boundaries |
| `useAiCaptionAssist.ts` | 3 | 0 | Render-time ref access |
| Remaining 24 files | 25 | 11 | Effect state, impurity, unused assignments, exports, dependencies |

These findings must be fixed by preserving behavior, not by disabling ESLint rules globally.

## 3. Phase 1: Stabilize the Existing Test Suite

### Backend

1. Correct the stale Admin ownership-transfer controller test.
2. Re-run the targeted `UserControllerTest`.
3. Run all backend tests and require `897+ tests, 0 failures, 0 errors`.
4. Investigate and reduce test-environment warnings:
   - H2 cannot create PostgreSQL-specific `inet` and `jsonb` schema types during the context test.
   - Mockito self-attachment is deprecated for future JDK releases.
   - `AuditLogService` and `SubmissionServiceTest` use deprecated APIs.
   - `AuditLogServiceTest` contains unchecked operations.
5. Do not hide legitimate schema warnings. Either use explicit H2-compatible test mappings or add a PostgreSQL/Testcontainers integration profile for database-specific behavior.

### Frontend

1. Keep `npm run build` passing after every batch.
2. Fix lint errors in small, reviewable groups:
   - Group A: unused variables, unused assignments, empty blocks, explicit `any`.
   - Group B: Fast Refresh file-boundary violations.
   - Group C: synchronous state updates inside effects.
   - Group D: render-time ref access or mutation.
   - Group E: render-time `Date.now()` and `Math.random()` impurity.
   - Group F: missing hook dependencies.
3. Add a regression test whenever a lint fix changes state timing, memoization, event handling, or component lifecycle.
4. Exit criterion: `npm run lint` and `npm run build` both pass with zero errors.

## 4. Phase 2: Code Cleanup

### Verified cleanup candidates

1. Remove `frontend/src/dev/DemoBar.tsx` if a final import check confirms it remains unreferenced.
2. Remove or replace default Picsum demonstration items in `CircularGallery.tsx`; the live landing page supplies its own DASIG gallery items.
3. Remove fabricated fallback trend values `[1, 2, 4, 3, 5, 6, 8]` from `PublishingTrendChart.tsx`. Empty or insufficient data must be represented honestly, not as production analytics.
4. Replace `System.out.println` and `System.err.println` in `BackendApplication.java` with the established SLF4J logger.
5. Keep purposeful error reporting in `AppErrorBoundary`, `tourStorage`, and failed gallery font loading unless it is replaced by centralized telemetry. These are not equivalent to debug `console.log` statements.
6. Remove unused imports, functions, props, and assignments reported by TypeScript/ESLint only after confirming they are not public extension points.

### Dependency and asset review

1. Compare imported packages against `frontend/package.json` before removing dependencies.
2. Check large UI/graphics dependencies and components for actual route usage. Do not remove packages based only on bundle size.
3. Run the production build and bundle budget check after dependency cleanup.
4. Confirm that no removed asset is referenced from CSS, runtime strings, or backend-generated URLs.

### Exit criteria

- No debug `console.log` calls.
- No `System.out` or `System.err` in application code.
- No known fabricated production analytics or stale demo data.
- No unreferenced development-only component retained without a documented reason.
- Backend tests, frontend lint, and frontend build remain green.

## 5. Phase 3: Missing Tests

### Backend controller tests to assess and prioritize

The following controllers have no matching controller test class:

- `AiTemplateController`
- `EngagementRecommendationController`
- `FacebookOAuthCallbackController`
- `GuardRailController`
- `HealthController`
- `MediaProcessingAdminController`
- `MessengerConnectionController`
- `PageSettingsController`
- `PostTemplateController`
- `ProofreadController`
- `WatermarkConfigurationController`

Priority order:

1. Authorization and tenant isolation: `MediaProcessingAdminController`, `PageSettingsController`, `WatermarkConfigurationController`, `GuardRailController`.
2. External integration boundaries: `FacebookOAuthCallbackController`, `MessengerConnectionController`, `ProofreadController`.
3. User-facing features: `AiTemplateController`, `EngagementRecommendationController`, `PostTemplateController`.
4. Simple liveness endpoint: `HealthController`.

Each controller test should cover success, invalid input, unauthenticated access, unauthorized role, service failure mapping, and tenant isolation where applicable.

### Frontend test foundation

The frontend has Playwright E2E coverage but no component/unit-test framework. Add Vitest, React Testing Library, and `@testing-library/user-event` in a dedicated test-infrastructure PR before writing component tests.

Initial unit/component targets:

- Readiness checklist and required-vs-recommended gate.
- AI caption state and submission-context switching.
- Media autosave and suggestion activation.
- Date/time parsing and scheduling controls.
- Invitation recipient validation and batch limits.
- Role/permission rendering helpers.
- Query-cache invalidation helpers.

### Frontend E2E gaps

Add focused workflows for:

- Moderator opens a queued submission and approves, requests revision, or rejects it.
- Administrator creates/edits an institution and sends multiple invitations.
- Mobile Moderator can reach all terminal review actions at a 360 px viewport.
- Media Repository upload, processing status, semantic search, delete, restore, and purge.
- Calendar detail, reschedule, and publication-status workflows.
- Notification deep links open the intended entity.
- Page settings, watermark settings, and guard-rail configuration persist correctly.
- AI suggestion failure and slow-processing fallbacks remain non-blocking.

### Coverage enforcement

1. Add JaCoCo for backend line and branch reports.
2. Add Vitest coverage for frontend logic and components.
3. Establish a baseline first; do not impose an arbitrary high threshold that encourages low-value tests.
4. Enforce coverage on changed files and security/business-critical packages before enforcing a repository-wide threshold.

## 6. Phase 4: Documentation and Repository Cleanup

### Keep as canonical demo/development documentation

- `README.md`
- `DEPLOY.md`
- `docs/md/srs/SRS.md`
- `docs/md/srs/UC-*.md`
- `docs/md/std.md`
- Final approved Proposal, SRS, SDD, SPMP, RTM, Requirements Inventory, MVP Validation Findings, and Validation Framework artifacts
- Local database setup only if it remains the supported developer setup

### Consolidate or archive after review

Historical implementation plans and audits should not compete with current documentation. Review and either archive externally or remove from the demo branch after their work is complete:

- `Implementation-Plan.md`
- `Module_1_todos.md`
- `Module_2_BE2_implementation_summary.md`
- `Module_2_notification_trigger_matrix.md`
- caching/performance plans and audit reports
- AI media development phase/decision/upgrade plans
- Facebook history planning documents not approved for the current demo
- provisional content-category research
- root architecture/performance audit reports

Git history preserves tracked documents, so completed development plans do not need to remain in the demo working tree unless they are required evidence.

### Remove from the working tree after canonical copies are confirmed

- `tmp/` Playwright traces, extracted resources, screenshots, and debug artifacts
- `work/` document-generation sources and preview images
- redundant `output/` and `outputs/` generated copies
- duplicate React audit output already preserved under `docs/audits`
- obsolete screenshots under `docs/img` that are not part of validation evidence
- superseded Word versions such as multiple `MVP_Validation_Framework*` drafts
- duplicate Requirements Inventory spreadsheets
- temporary rendering files and caches

### Required repository controls

1. Add `tmp/`, `work/`, generated preview folders, and local output folders to `.gitignore` where appropriate.
2. Use one canonical folder per deliverable type. Recommended structure:

```text
docs/
  current/
    srs/
    sdd/
    spmp/
    std/
    validation/
    rtm/
  developer/
    deployment/
    local-setup/
```

3. Store submission-only evidence outside the application repository when it contains respondent information, recordings, emails, or large images.
4. Never commit `.env`, access tokens, respondent-identifying spreadsheets, or private validation evidence.
5. Before deletion, create a manifest containing original path, action, reason, canonical replacement, and approver.

## 7. Phase 5: Refactoring Evidence Document

Create a 2-3 page PDF or DOCX for Folder 01 with this structure:

1. Title and purpose.
2. MVP evidence summary: finding, affected SMART objective, and implemented response.
3. Three to five compact before/after cases.
4. Verification table showing tests and measurable outcomes.
5. GitHub references.

Recommended evidence cases already present in Git history:

| MVP finding | Evidence candidate | GitHub reference |
|---|---|---|
| Users required more guidance | View-only composer guide | PR #326, commit `b4dc556` |
| Mobile review actions were difficult to reach | Review Queue mobile layout | PR #329, commit `39e2bd0` |
| Mobile submission workflow needed parity | Responsive My Submissions | PR #325, commit `f607788` |
| AI workflow required automatic draft/media persistence | AI caption autosave | PR #268, commit `aa802f2` |
| Media suggestions needed image-first activation | Selected-image suggestions | PR #333, commit `d2f972c` |
| Media selection needed safe persistence | Media mutation autosave | PR #335, commit `90a441d` |
| Initial image context could be missing | Initial autosave-context fix | PR #358, merge commit `cdb6cc2` |

Repository PR URL format:

`https://github.com/dasigconnect-dev/DASIGConnect/pull/<PR_NUMBER>`

For each selected case, include:

- Validation quote or measured finding.
- Before behavior and a short old-code excerpt from the parent commit.
- After behavior and a short new-code excerpt from the implementation commit.
- PR link and commit hash.
- Test or demo evidence.
- SMART objective or measurable outcome affected.

Do not claim that a validation priority is complete merely because related code exists. In particular, the current caption flow still appears user-triggered, and the current readiness UI still displays a blended score. These must be described accurately unless a later verified implementation changes them.

## 8. Recommended PR Sequence

1. `fix/test-suite-admin-transfer`
   - Correct stale test expectations and add authorization regressions.
2. `chore/frontend-lint-cleanup-1`
   - Mechanical unused/type/export fixes.
3. `refactor/frontend-react-correctness`
   - Effects, refs, purity, and hook dependency fixes with tests.
4. `test/backend-controller-coverage`
   - Add high-risk missing controller tests and JaCoCo reporting.
5. `test/frontend-unit-and-critical-e2e`
   - Add Vitest/RTL and role-critical E2E workflows.
6. `chore/demo-code-cleanup`
   - Remove verified dead code, stale mock data, and debug output.
7. `docs/demo-repository-cleanup`
   - Consolidate canonical documents and remove approved generated artifacts.
8. `docs/refactoring-evidence`
   - Add the final 2-3 page evidence document and its source.

Every PR must pass backend tests, frontend lint, frontend build, and applicable E2E tests before merge.

## 9. Demo Readiness Exit Checklist

- Backend test suite passes with zero failures and errors.
- Frontend lint and production build pass cleanly.
- Critical Contributor, Moderator, and Administrator workflows have automated coverage.
- No stale mock analytics or unreferenced demo components remain.
- No debug logs or plaintext secrets are present.
- Canonical academic documents are clearly identified.
- Temporary/generated artifacts are removed or ignored.
- Private validation evidence is outside the source repository.
- Refactoring Evidence document contains verified PR links, hashes, before/after snippets, and test evidence.
- A final clean clone can build and run using documented setup steps.

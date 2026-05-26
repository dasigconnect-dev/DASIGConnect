# Handoff — 2026-05-26

## What was done this session
- **Media Library ↔ Submission integration.** Multi-select assets in the Media Library, then "New Post (N)" carries them to the Content Submission form via `/submissions/new?assetIds=...`, which auto-attaches them on save/submit (`POST /submissions/{id}/assets`, tolerating 409; URL param stripped after first save).
- **Reusable selection hook.** `usePersistentSelection` (sessionStorage-backed `Set<string>`) so a selection survives navigation/back; used by the Media Library.
- **Detail panel selection review.** Lists every selected asset with view/deselect; "Use in New Post" renamed to "New Post (N)" and rewired; redundant selection-block button removed.
- **Add to Draft** is now functional (`AddToDraftModal` — appends selected assets to an existing contributor draft; contributor-only).
- **Download Original** upgraded to a true blob download (falls back to open-in-tab on CORS).
- **Upload fix.** Library upload uses XHR with real progress, a 25 MB client guard, and surfaces the actual Supabase error (was a fake bar via `fetch`). mp4 failures are bucket MIME/size config, now visible in the toast.
- **Submission validation (key fix).** `SubmissionService.submit()` now rejects incomplete submissions with **422** (missing event title, date, caption, or ≥1 media) — always enforced, independent of `app.guardrails.enforced`. Frontend blocks the same set with a toast.
- **GlobalExceptionHandler** gained `HttpRequestMethodNotSupportedException` (405) and `IllegalStateException` (502) handlers.
- Updated `CLAUDE.md` (new dated subsection + verification/known-gaps) and `TASKS.md` (UC-2.2 backend/frontend Done, validation Done).

## Files changed
- `backend/.../service/SubmissionService.java` — `assertContentComplete()` gate in `submit()`.
- `backend/.../service/SubmissionServiceTest.java` — media mocks on success tests + 2 new 422 tests.
- `backend/.../exception/GlobalExceptionHandler.java` — 405 + 502 handlers.
- `frontend/src/hooks/usePersistentSelection.ts` — new reusable selection hook.
- `frontend/src/features/media-repository/MediaRepositoryScreen.tsx` — selection, action bar, New Post nav, upload XHR/progress/guard, true download, add-to-draft wiring.
- `frontend/src/features/media-repository/components/{AssetCard,AssetDetailPanel,UploadModal}.tsx` + new `AddToDraftModal.tsx`.
- `frontend/src/features/submission/SubmissionScreen.tsx` — consume `?assetIds=`, attach on save/submit, client completeness guard.
- `frontend/src/api/submissionApi.ts` — `attachAsset()`.
- `frontend/src/styles/media-repository.css` — selection checkbox, action bar, selection list, draft modal.

## What's next
1. **Browser verification** (couldn't run live here): select assets → New Post → confirm attach; submit an empty draft → expect client block + backend 422; upload an mp4 → read the toast to confirm whether it's bucket config.
2. **Supabase bucket config** for `dasigconnect-media`: allow `video/mp4`/`video/quicktime` MIME types and raise the file-size limit if mp4 uploads are rejected.
3. Decide whether **schedule** should be mandatory at submit (currently gated by `app.guardrails.enforced`, which defaults off).
4. Still pending: UC-2.1 validation, UC-2.3 SSE notifications, UC-2.4 analytics, UC-3.2/3.3 AI.

## Blockers / notes
- `app.guardrails.enforced` defaults **false** locally — schedule/guard-rail checks are skipped on submit; content-completeness is enforced regardless.
- Untracked docs in `docs/md/` (`Implementation-Plan.md`, `Module_2_BE2_implementation_summary.md`, `dasigconnect_module_sprints (1).html`) are NOT included in this session's commit (only the feature files + CLAUDE.md/TASKS.md/HANDOFF.md).
- `.\mvnw.cmd` fails in this PowerShell env; use Maven from `.m2/wrapper/dists/.../bin/mvn.cmd`.
- Backend tests verified this session: `SubmissionServiceTest` 17/17, `SubmissionControllerTest` 14/14. Frontend `npm run build` + targeted ESLint pass.

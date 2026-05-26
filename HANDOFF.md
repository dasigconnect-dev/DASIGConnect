# Handoff — 2026-05-27 (Session 10)

## What was done this session

- **Gap audit:** Reviewed all known gaps against live code. Confirmed `DASIG_SUPABASE_SERVICE_ROLE_KEY` is present in `backend/.env` and correctly mapped via `application.properties` to `app.supabase.service-role-key` — no code change needed. Confirmed `categories` and `availableTags` are already returned by `GET /api/v1/submissions/lookups` and already rendered in the submission form — the CLAUDE.md gap note was stale.
- **Media Library upload limit raised 25 MB → 50 MB:** `MediaRepositoryScreen.tsx` `MAX_UPLOAD_MB` updated from 25 to 50 to match Supabase free-tier global file size limit. `UploadModal.tsx` display text updated accordingly ("50 MB per file"). Submission form `SubmissionLookupsDto.maxFileSizeMb` was already 50 MB — no change needed there.
- **File size limits note:** Per-type limits (images 25 MB, videos 500 MB) were explored but cannot be enforced until Supabase Pro is activated (free tier hard-caps at 50 MB globally). Code reverted to single 50 MB limit. When upgrading to Pro: set Supabase bucket max upload size to 524288000 bytes and re-introduce per-type guards in `SubmissionLookupsDto`, `submissionApi.ts`, `useSubmissions.ts`, `SubmissionScreen.tsx`, and `MediaRepositoryScreen.tsx`.

## Files changed

- `frontend/src/features/media-repository/MediaRepositoryScreen.tsx` — raised `MAX_UPLOAD_MB` from 25 to 50
- `frontend/src/features/media-repository/components/UploadModal.tsx` — updated display text to "50 MB per file"
- `frontend/src/features/submission/SubmissionScreen.tsx` — minor blank-line cleanup (net +1 line from helper removal)

## What's next

1. **Supabase bucket** — go to Supabase Dashboard → Storage → Buckets → `dasigconnect-media` → Edit → set Max upload size to `52428800` bytes (50 MB) and confirm `video/*` MIME types are in the allowed list.
2. **Backend restart** — required for all Java changes from previous sessions (`ClaudeVisionClient` base64, resize, intent detection, existingCaption) to activate. Run `./mvnw spring-boot:run`.
3. **Browser end-to-end test — AI caption** — after backend restart, test: (a) type an instruction in the caption field, click Suggest; (b) type a real draft caption, click Suggest.
4. **UC-3.3 AI Classification & Recommendation** — Voyage AI embedding pipeline for media recommendations; not started.
5. **Merge planning** — `module3` is feature-complete for UC-3.1, UC-3.2, UC-3.4. Plan merge to `main` after team review.

## Blockers / notes

- Supabase free tier: 50 MB global upload limit per file. Per-type limits (25 MB image / 500 MB video) require Pro upgrade.
- Backend restart required for `ClaudeVisionClient` changes to activate.
- `app.supabase.service-role-key` is already wired — `DASIG_SUPABASE_SERVICE_ROLE_KEY` in `.env` maps correctly.
- Categories and tags in submission lookups are already working — CLAUDE.md gap note was outdated.

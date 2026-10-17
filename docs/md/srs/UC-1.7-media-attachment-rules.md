# UC-1.7 Media Attachment Rules

**Use Case ID:** UC-1.7

**Use Case Name:** Media Attachment Rules

**Actor(s):** Contributor, Moderator, Administrator — all three use the same composer (UC-1.5); there is no separate "Administrator acting as Contributor" mode. Any of the three roles can upload, pick from the library, caption, watermark-toggle, and album-assign media on their own draft.

**Precondition(s):** The actor is in an active composer session (UC-1.5).

## Main Flow

1. The actor initiates media attachment from the composer's **Add Media** step by choosing to upload a new file from their device or select from the existing media library.
2. **Device Upload:** the actor selects one or more image (**JPEG, PNG, WebP, GIF**) or video (**MP4, MOV, WebM**) files from their device.
3. The system validates each file: `MediaFileType` accepts only the seven formats above (case-insensitive; `jpg` is normalized to `jpeg`), and file size must be `> 0` and `≤ 50 MB`.
4. Valid files are uploaded (via a presigned R2 URL) and attached to the draft; the system displays a thumbnail preview of each attached file within the composer.
5. **Library Selection:** alternatively, the actor browses or searches the media repository (UC-2.2 — Semantic Search & Filtering) and selects existing asset(s) to attach.
6. **AI-Suggested Media:** once the actor has entered enough context (event title + caption + category + tags combined ≥ 10 characters), the system embeds that text (Voyage AI), runs a pgvector nearest-neighbor search scoped to the submission's institution, re-ranks the top 30 candidates with metadata/tag boosts, keeps only results scoring **≥ 0.40**, and shows up to **8** ranked suggestions. The actor may select any suggested asset to attach it directly, bypassing manual search.
7. The selected library asset(s) are linked to the draft and displayed as thumbnail previews within the composer.
8. For any attached asset, the actor may optionally open the **Media Caption** modal to set a per-asset caption (**≤ 500 characters**, tracked with a live counter) scoped to that specific image or video.
9. Within that same modal, for a photo asset only (the checkbox is not rendered for a video), the actor may check **"Skip watermark for this image"** to exclude that asset from automatic watermark application at approval time.
10. In the **Organize & Schedule** step (UC-1.5, Step 3) the actor assigns the draft's album via a combobox: type to filter/select an existing album, type a new name to create one, or click **Auto-Match**. Auto-Match calls `POST /ai/submissions/{id}/suggest-album` with the draft's event title, caption, and media tags; the backend ranks every root album in the institution by a blend of tag overlap and "closest existing asset" visual similarity (Voyage embedding + pgvector `<=>` on `media_asset_embeddings`, max score across the album's assets rather than a centroid). A **confident** match (≥ 0.55) is applied immediately with a small "AI-matched" badge and its reasons; an **ambiguous** result (≥ 0.32 but below 0.55) lists up to 3 ranked candidates in the dropdown instead of auto-applying; **no match** (nothing clears 0.32, or the institution has no root album yet) leaves the field for manual select/create with a brief notice. The actor may also add media tags; if none are added, the **Event Title is used as the default tag** on submit.
11. The system enforces mandatory media attachment and organization: the draft cannot be submitted (UC-1.9) until at least one valid media file is attached and an album name is set.

## Alternative Flow(s)

- **A1 — Invalid File Type:** rejected with `400 Bad Request` ("Unsupported file type. Accepted types: JPEG, PNG, WebP, GIF, MP4, MOV, WebM."); no asset record is created.
- **A2 — File Exceeds Size Limit:** rejected with `422` ("File size must be greater than 0 and no larger than 50 MB."); no asset record is created.
- **A3 — Upload Network Failure:** the composer shows a **"Retry upload"** action; no partial asset record is created.
- **A4 — Remove Attached Media:** the actor removes a previously attached file (device-uploaded or library-selected) from the draft before submission.
- **A5 — No Relevant AI Suggestions Found:** if no candidate clears the 0.40 similarity floor after re-ranking, the hook's state is `"empty"` and the suggestions row does not render; the actor proceeds via standard Device Upload or Library Selection. (If the Voyage embedding call itself fails, the service falls back to a metadata-only suggestion list rather than showing nothing.)
- **A6 — Insufficient Event Details for Suggestions:** the suggestions row does not appear until the combined event title + caption + category + tags text reaches **10 characters**.
- **A7 — Mixed-Media Attachment:** confirmed accurate. The draft accepts both image(s) and video for drafting purposes (`refreshManualPublishingFlag` sets `Submission.requiresManualPublishing = true` whenever both are present). At the actual publishing stage, `FacebookPublisherService` detects the mix and calls `markFailed(...)` instead of attempting an automated post; the submission then surfaces in the **Approval Queue's Failed tab** (`ValidationQueueScreen`, `filter === "failed"` — the calendar's own link to this screen is labeled "Open in Approval Queue") for manual recovery (UC-2.4).
- **A8 — Ambiguous Auto-Match:** when the top candidate scores ≥ 0.32 but below the 0.55 confident bar, the field is left untouched and the dropdown instead lists up to 3 ranked candidates (album name, match %, and reason text — tag overlap and/or "visually similar to existing media in this album") for the actor to click-select, choose a different existing album, or create a new one. There is no separate modal — the ranked list is inline in the same `AlbumCombobox` dropdown, above the regular album list.
- **A8a — Auto-Match, No Confident Result:** when nothing clears 0.32 (or the institution has no root album yet), the field is left untouched, a small "No confident album match — pick an existing album or create a new one." notice appears under it, and the actor proceeds exactly as if Auto-Match didn't exist.
- **A9 — Attempted Submission Without Media or Album:** `assertContentComplete` blocks the submit and returns `422` with one combined message listing everything missing (e.g. "add at least one media attachment, an album assignment before submitting.").
- **A10 — Per-Asset Caption Skipped:** per-asset captions are optional; they surface only on the non-blocking **Recommended** readiness checklist (`mediaCaptions` check), never on Required, and never block progression to submission.

## Postcondition(s)

The draft contains at least one valid media attachment (device-uploaded or library-selected), meeting format and size requirements, with any per-asset captions and watermark preferences set, and an album name assigned — eligible to proceed to submission (UC-1.9). A media tag is always present in practice (the Event Title default), though the backend does not independently enforce "at least one tag" as a submit gate — it falls out of the Event Title being required for submit in the first place.

---

## Corrections from the prior draft of this UC

- **Actors:** added Moderator — the composer is shared by all three roles (see UC-1.5); there's no separate "Administrator in a Contributor capacity" mode, they use the identical form.
- **Auto-Match built out to match this spec (2026-09-12).** Until this session, clicking **Auto-Match** in `AlbumCombobox` just called `applyAutoAlbum()`, which filled the album field with `form.eventTitle || form.liveEventName || "Auto-Matched Album"` — a plain text autofill with no scoring, no embeddings, no confirmation step, and the backend's `resolveSubmissionAlbum`/`resolveAlbumByName` would then do a blind exact-name match/create on submit. It's now backed by a real endpoint (`AIRecommendationService.suggestAlbum`, `POST /ai/submissions/{id}/suggest-album`): tag overlap (`AssetTagRepository.findLabelsByRootAlbumForInstitution`) blended 65/35 with visual similarity (Voyage `embedQuery` + pgvector `<=>`, `MediaAssetEmbeddingRepository.findMaxSimilarityByRootAlbum` — the *best-matching single asset* in each root album, not a centroid, so one strong visual match is enough even in an otherwise mixed album). No-embedding-signal drafts (nothing to embed, or the Voyage call fails) fall back to tag-only scoring. See the confident/ambiguous/none thresholds in the Main Flow step above and A8/A8a. (The pre-existing tag/filename-substring "auto-match" in `MediaAssetService.autoMatchAlbum` is unrelated — that backs the standalone Media Library upload endpoint, UC-2.1, not the submission composer.)
- **AI-Suggested Media** got concrete numbers: the 10-character combined-text gate for showing the row at all (A6), the pgvector similarity floor of **0.40** and the top-**8** cap after re-ranking (A5), and the Voyage-embedding-failure fallback path — none were specified in the prior draft.
- **Per-asset caption** has a **500-character** cap with a live counter, not stated before.
- **"At least one tag"** in the postcondition is not an independent backend check — it's an emergent guarantee from `defaultMediaTags` falling back to the (required) Event Title when no tag is manually added.

---

_Verified against the running code as of 2026-09-12 (Auto-Match implementation landed the same day). Primary sources: `SubmissionService` (`validateMediaFile`, `MAX_FILE_SIZE_BYTES`, `refreshManualPublishingFlag`, `resolveSubmissionAlbum`/`resolveAlbumByName`, `assertContentComplete`), `MediaFileType`, `FacebookPublisherService` (mixed-media `markFailed` check), `AIRecommendationService.suggestMedia` (0.40 score floor, top-8, Voyage fallback) and `.suggestAlbum` (album Auto-Match: 0.55/0.32 thresholds, 65/35 embedding/tag blend), `MediaAssetEmbeddingRepository.findMaxSimilarityByRootAlbum`, `AssetTagRepository.findLabelsByRootAlbumForInstitution`, `AIRecommendationController` (`/suggest-media`, `/suggest-album`), `frontend/src/hooks/useAiMediaSuggestions.ts` (`hasSufficientMediaContext`, 10-char gate), `frontend/src/components/ui/AlbumCombobox.tsx` and `SubmissionScreen.tsx` (`applyAutoAlbum`, the Media Caption modal, `updateMediaSkipWatermark`), `frontend/src/features/submission/utils.ts` (`defaultMediaTags`, readiness checklist)._

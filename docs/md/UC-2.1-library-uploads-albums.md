# UC-2.1 Library Uploads & Albums

**Use Case ID:** UC-2.1

**Use Case Name:** Library Uploads & Albums

**Actor(s):** Contributor, Moderator, Administrator — every upload/album endpoint on `MediaAssetController` (`/upload`, `/albums`, `/albums/{id}`, `/{id}/album`, etc.) is `@PreAuthorize("isAuthenticated()")`, i.e. any of the three roles, not just Contributor/Administrator.

**Precondition(s):** The actor is authenticated with an active session. The institution's media library exists (provisioned upon institution creation, UC-1.2). Confirmed accurate.

## Main Flow

1. The actor initiates an upload from the Media Library screen ("Upload Asset"), selecting one or more files from their device.
2. The system validates each file against accepted formats (JPG, PNG, WEBP, GIF, MP4, MOV, WEBM — client-side in `UploadModal`, and server-side via `MediaAssetService.upload`'s `MediaFileType.valueOf`) and the **50 MB** size limit, both checked twice (client for instant feedback, server as the real gate).
3. Before the upload is finalized, the actor specifies:
   - **Album:** select an existing album, click **Auto-Match**, or type a new name to create one. Auto-Match now scores every existing album by tag/filename word overlap (still no visual signal — the file isn't uploaded, let alone embedded, at this point) and tiers the result: a confident match (≥0.6) auto-fills with a reasons badge, an ambiguous one (≥0.3) lists up to 3 ranked candidates in the same dropdown for the actor to confirm or pick a different one, and no match leaves the field for manual entry — see A4/A4a, fixed 2026-09-13.
   - **Tags:** the actor enters at least one tag — enforced server-side too (`"At least one media tag is required."`, `400`). A tooltip beside the tag field reads exactly "Tip: using the event name as a tag improves search results." (a `title` attribute on a `?` icon).
4. On confirmation, the system stores the asset(s) — `status: PROCESSING` for an image, **`status: READY` immediately for a video** (nothing is queued for it — see step 5) — applies the selected/created album, and saves the actor-entered tag(s) as `source: "manual"` `AssetTag` rows.
5. The system triggers the AI classification and embedding pipeline asynchronously (`AIClassificationService.classifyAndEmbed`, `@Async`) — **only for image files** (`if (savedType.isImage())` in `MediaAssetService.upload`); a video upload is never classified or embedded at all (unchanged — see "Known limitation" below).
6. The asset appears in the library grid under its assigned album with a **"Processing…"** badge (`asset.status === "processing"`) that now actually clears once classification and both embeddings succeed (fixed 2026-09-13 — see the bug note below for what was wrong).

## Alternative Flows

- **A1 — Unsupported File Type:** confirmed, both ends (`UploadModal`'s `ACCEPTED_EXTENSIONS` check client-side, `MediaFileType.valueOf` server-side → `400`).
- **A2 — File Exceeds Size Limit:** confirmed, both ends, exact 50 MB limit.
- **A3 — Upload Network Failure:** consistent with the composer's own retry pattern (UC-1.7 A3) — not re-verified line-by-line here, no reason to expect it differs.
- **A4 — Ambiguous Album Match (Auto-Match):** **does not exist.** There is no similarity score, so there's no "uncertain range" to be in. `MediaAssetService.autoMatchAlbum` is pure text matching — it collects the entered tags plus filename tokens and returns the **first** existing album whose name contains, or is contained by, one of those cues (`albums.stream().filter(...).findFirst()`); the frontend's own `autoMatchedAlbum` preview mirrors the identical heuristic. There is no "confident vs. ambiguous vs. no match" tiering and no confirm-or-choose-different dialog — it's binary: a text match is found and silently pre-filled into the album field for the actor to accept or overwrite, or nothing matches (A4a).
- **A4a — Auto-Match, No Confident Result:** confirmed, in spirit. `resolveAlbum` throws `400` ("No confident album match found…") when the server-side auto-match path finds nothing; the modal's own client-side check shows the same idea inline: *"No confident folder match from tags or filenames. Type a folder name instead."* Upload is correctly blocked with no album selected (`normalizeRequiredAlbumName` rejects a blank name).
- **A5 — Manual Album Reassignment:** **half implemented.** Moving an asset to a different existing album works (`POST /{id}/album`, wired to a drag-and-drop-style `onUpdateAlbum` handler in `MediaRepositoryScreen`). **"Removes it from all albums" is not implemented anywhere** — `MediaAssetService.updateAlbum` explicitly rejects a null `albumId` (`400`, "An album is required."), and the frontend's own handler guards against ever calling it with one (`if (!albumId) return;`) even though `updateMediaAssetAlbum`'s TypeScript signature accepts `string | null`. Every library asset must always belong to exactly one album; there's no "no album" state reachable through the UI or API.
- **A6 — Manual Album Creation:** confirmed (`POST /media-assets/albums`, independent of any upload).
- **A7 — Rename Album:** confirmed (`POST /media-assets/albums/{id}` → `MediaAssetService.renameAlbum`).

## Postcondition(s)

The uploaded asset exists in the institution's media library, assigned to an album, and is queued for AI classification and embedding generation — for an image; a video asset is stored and album/tag-assigned identically but is marked `READY` immediately since nothing is queued for it (see "Known limitation" below). Confirmed accurate for images, now that `READY` is actually reachable.

---

## Bug found during verification, now fixed (2026-09-13) — assets never left `PROCESSING`

This wasn't a spec-accuracy correction — it was a real functional defect surfaced while checking whether "Processing…" ever clears. `AIClassificationService.classifyAndEmbed` called `mediaAssetRepository.updateStatus(assetId, MediaAssetStatus.FAILED.name())` on every failure branch, but **no code path anywhere in the backend ever set an asset's status to `READY`** on success — grepping the entire backend for `MediaAssetStatus.READY` turned up exactly one hit, a `SELECT … WHERE status = 'READY'` filter, never a write. The one migration that touches `READY` (`V25__media_asset_dual_embeddings.sql`) was a one-time backfill of pre-existing rows, not an ongoing mechanism.

Every asset uploaded since that migration was permanently stuck on `PROCESSING` even after classification and embedding genuinely succeeded (`asset.aiCategory`, `asset.aiConfidence`, and the embedding row were all correctly written — just not the status), which meant every `status = 'READY'`-gated feature (search, `suggestMedia`, `getSimilarMedia`, the UC-1.7 Auto-Match visual score) silently excluded every asset uploaded since. No test caught this — there was no `AIClassificationServiceTest` at all.

**Fix:** `classifyAndEmbed` now sets `MediaAssetStatus.READY` once classification *and* both embeddings (image + semantic) succeed. Covered by new `AIClassificationServiceTest` cases (success → `READY`; a Claude or Voyage failure → `FAILED`, never `READY`).

---

## Corrections from the prior draft of this UC

- **Actors:** added Moderator — upload and album management are `isAuthenticated()`, open to all three roles, not Contributor/Administrator-only.
- **A4/A4a — fixed (2026-09-13).** Auto-Match is still text matching, not visual/embedding similarity — that stays a real, structural limitation: the file hasn't been uploaded (let alone classified and embedded) at album-selection time, so there's nothing to compare visually yet, unlike the submission composer's Auto-Match (UC-1.7), which scores *already-attached, already-embedded* assets. What was missing is now built: `UploadModal`'s client-side matching (`scoreAlbumMatches`) scores tag/filename word overlap against every candidate album and tiers the result — confident (≥0.6, single auto-applied pick with a reasons badge), ambiguous (≥0.3, up to 3 ranked candidates shown inline in `AlbumCombobox`'s dropdown, reusing the same component UC-1.7 extended), or no match (unchanged inline notice). This is client-side and synchronous — no backend round-trip — since the file doesn't exist server-side yet.
- **A5 — fixed (2026-09-13).** `MediaAssetService.updateAlbum` now accepts a null `albumId` as a deliberate "remove from all albums" request (`media_album_id` was always nullable — `STAGED` submission uploads already sit album-less pre-submit) rather than rejecting it. Moving to a different existing album already worked. (No frontend entry point wired up yet — the API supports it, but nothing in `MediaRepositoryScreen` calls `updateMediaAssetAlbum(id, null)`; that's a follow-up if a "remove from folder" action is wanted in the UI.)
- **Known limitation, unchanged:** video uploads are accepted (format/size validated identically) but **never classified or embedded** — the AI pipeline trigger is still image-only. A video is now marked `READY` immediately on upload instead of sitting on `PROCESSING` forever, but it never gets AI category/tags/embeddings. The prior UC-2.1 draft's postcondition implied classification applies to any uploaded asset.

---

_Verified against the running code as of 2026-09-13 (A4/A5 and the `READY`-status fixes landed the same day). Primary sources: `MediaAssetController` (`@PreAuthorize` on every album/upload endpoint), `MediaAssetService` (`upload`, `resolveAlbum`, `autoMatchAlbum`, `updateAlbum`, `renameAlbum`, `createAlbum`, `normalizeRequiredAlbumName`), `AIClassificationService` (`classifyAndEmbed`'s `READY`/`FAILED` transitions), `MediaAssetStatus` enum, `AIClassificationServiceTest`, `MediaAssetServiceTest` (`upload_imageAsset_staysProcessingAndTriggersClassification`, `upload_videoAsset_isReadyImmediatelyAndSkipsClassification`, `updateAlbum_withNullAlbumId_unassignsAndRecordsAuditEntry`), `frontend/src/features/media-repository/components/UploadModal.tsx` (`scoreAlbumMatches`, `albumMatches`/`confidentMatch`/`ambiguousMatches`, the tags tooltip), `frontend/src/components/ui/AlbumCombobox.tsx` (`matchedBadge`/`suggestions`/`noMatchNotice`, shared with UC-1.7), `frontend/src/features/media-repository/MediaRepositoryScreen.tsx` (`handleUpdateAssetAlbum`), `frontend/src/api/mediaApi.ts` (`updateMediaAssetAlbum`), `frontend/src/features/media-repository/components/AssetCard.tsx` / `AssetDetailPanel.tsx` (the "Processing…" badge)._

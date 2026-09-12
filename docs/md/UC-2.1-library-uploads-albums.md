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
- **A5 — Manual Album Reassignment:** confirmed, and "removes it from all albums" is correctly **not** offered — every library asset must always belong to exactly one album; there's no "unfiled" state to move into. Moving an asset to a different existing album works (`POST /{id}/album`, wired to `AssetDetailPanel`'s "Move here" button via `MediaRepositoryScreen.handleUpdateAssetAlbum`). `MediaAssetService.updateAlbum` rejects a null `albumId` (`400`, "An album is required.") and the frontend guards against ever sending one (`if (!albumId) return;`) — this is a deliberate invariant, not a gap: the prior UC draft's "or removes it from all albums" clause doesn't match the actual product design.
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
- **A5's "removes it from all albums" clause doesn't match the product.** Tried allowing a null `albumId` as an unassign action (`media_album_id` is nullable at the DB level — `STAGED` submission uploads sit album-less pre-submit — so it looked like a safe gap to close), but every library asset is meant to always belong to exactly one album; there's no "unfiled" state in the actual design. Reverted: `updateAlbum` keeps rejecting a null `albumId` with `400`, and no UI offers it. Moving to a different existing album is the only reassignment this UC supports, and that already worked.
- **Known limitation, unchanged:** video uploads are accepted (format/size validated identically) but **never classified or embedded** — the AI pipeline trigger is still image-only. A video is now marked `READY` immediately on upload instead of sitting on `PROCESSING` forever, but it never gets AI category/tags/embeddings. The prior UC-2.1 draft's postcondition implied classification applies to any uploaded asset.

---

## Adjacent features this UC doesn't mention

The prior draft only covers upload + basic album organization. The same code area (`MediaAssetController`/`MediaAssetService`, `frontend/src/features/media-repository/`) also implements, without any of it being in this UC:

- **Nested folders, not just flat albums.** An album can have a `parentAlbumId`; `POST /albums/ensure-path` walks/creates a whole folder path in one call (backs an "Upload folder" flow), and `PATCH /albums/{id}/parent` re-parents an existing one, guarded against a cycle (`findDescendantIds` blocks an album from becoming its own descendant).
- **Cross-institution asset/album movement.** An admin's "All institutions" view lists every institution's albums together; a Contributor or Moderator can additionally move an asset they uploaded into the shared default-institution library (not just within their own institution) — `updateAlbum`'s cross-institution branch.
- **Two-step presigned upload.** `POST /upload-url` then `/upload` — the browser `PUT`s bytes straight to Cloudflare R2; the backend only ever receives metadata, never the file itself.
- **Post-upload tag management.** `POST`/`DELETE /{id}/tags` add or remove individual tags after the fact — this UC only covers tags entered at upload time.
- **Usage tracking.** `GET /{id}/history` and a `usedIn` list on the asset detail (`MediaAssetUsageDto`) show which submissions currently reference the asset.
- **Pulling a library asset the other direction** — `POST /{id}/use-in-new-post` and `/{id}/add-to-draft` (Contributor-only) start a new or existing submission draft from a library asset, the reverse of this UC's upload-into-the-library flow (see UC-1.7 for the submission composer's own media-picking side of this).
- **Semantic + keyword search** (`GET /search`, Voyage embeddings + pgvector) and "more like this" (`getSimilarMedia`) — both distinct from the Auto-Match text matching this UC covers.
- **Bulk delete** (`POST /bulk-delete`) and per-asset delete with three authorization tiers (Admin-any / Moderator-institution / Contributor-own-uploads) — this UC only covers create/organize, not deletion (that's UC-2.2, Media Asset Retention).
- **Rich AI classification metadata beyond a category.** `aiDescription`, `visibleObjects`, `specificSubjects`, `visualStyle`, `dominantColors`, `possibleUseCases`, `excludedCategories`, plus a confidence score — the prior draft only mentions "category tags and confidence scores."

---

_Verified against the running code as of 2026-09-13 (the A4 and `READY`-status fixes landed the same day; a tried A5 "unassign" fix was reverted the same day — see above). Primary sources: `MediaAssetController` (`@PreAuthorize` on every album/upload endpoint), `MediaAssetService` (`upload`, `resolveAlbum`, `autoMatchAlbum`, `updateAlbum`, `renameAlbum`, `createAlbum`, `normalizeRequiredAlbumName`), `AIClassificationService` (`classifyAndEmbed`'s `READY`/`FAILED` transitions), `MediaAssetStatus` enum, `AIClassificationServiceTest`, `MediaAssetServiceTest` (`upload_imageAsset_staysProcessingAndTriggersClassification`, `upload_videoAsset_isReadyImmediatelyAndSkipsClassification`, `updateAlbum_withNullAlbumId_isRejected`), `frontend/src/features/media-repository/components/UploadModal.tsx` (`scoreAlbumMatches`, `albumMatches`/`confidentMatch`/`ambiguousMatches`, the tags tooltip), `frontend/src/components/ui/AlbumCombobox.tsx` (`matchedBadge`/`suggestions`/`noMatchNotice`, shared with UC-1.7), `frontend/src/features/media-repository/MediaRepositoryScreen.tsx` (`handleUpdateAssetAlbum`), `frontend/src/api/mediaApi.ts` (`updateMediaAssetAlbum`), `frontend/src/features/media-repository/components/AssetCard.tsx` / `AssetDetailPanel.tsx` (the "Processing…" badge)._

# UC-2.1 Library Uploads & Albums

**Use Case ID:** UC-2.1

**Use Case Name:** Library Uploads & Albums

**Actor(s):** Contributor, Moderator, Administrator — every upload/album endpoint on `MediaAssetController` (`/upload`, `/albums`, `/albums/{id}`, `/{id}/album`, etc.) is `@PreAuthorize("isAuthenticated()")`, i.e. any of the three roles, not just Contributor/Administrator.

**Precondition(s):** The actor is authenticated with an active session. The institution's media library exists (provisioned upon institution creation, UC-1.2). Confirmed accurate.

## Main Flow

1. The actor initiates an upload from the Media Library screen ("Upload Asset"), selecting one or more files from their device.
2. The system validates each file against accepted formats (JPG, PNG, WEBP, GIF, MP4, MOV, WEBM — client-side in `UploadModal`, and server-side via `MediaAssetService.upload`'s `MediaFileType.valueOf`) and the **50 MB** size limit, both checked twice (client for instant feedback, server as the real gate).
3. Before the upload is finalized, the actor specifies:
   - **Album:** select an existing album, click **Auto-Match**, or type a new name to create one.
   - **Tags:** the actor enters at least one tag — enforced server-side too (`"At least one media tag is required."`, `400`). A tooltip beside the tag field reads exactly "Tip: using the event name as a tag improves search results." (a `title` attribute on a `?` icon).
4. On confirmation, the system stores the asset(s) with `status: PROCESSING`, applies the selected/created album, and saves the actor-entered tag(s) as `source: "manual"` `AssetTag` rows.
5. The system triggers the AI classification and embedding pipeline asynchronously (`AIClassificationService.classifyAndEmbed`, `@Async`) — **only for image files** (`if (savedType.isImage())` in `MediaAssetService.upload`); a video upload is never classified or embedded at all.
6. The asset appears in the library grid under its assigned album with a **"Processing…"** badge (`asset.status === "processing"`) — see the bug note below on why this badge never actually clears.

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

The uploaded asset exists in the institution's media library, assigned to an album, and is queued for AI classification and embedding generation — **for an image**. A video asset is stored and album/tag-assigned identically but is never queued for classification at all. See the bug below regarding whether "queued" ever actually finishes.

---

## Bug found during verification — assets never leave `PROCESSING` (2026-09-13, not yet fixed)

This isn't a spec-accuracy correction — it's a real functional defect surfaced while checking whether "Processing…" ever clears. `MediaAssetService.upload` sets a new asset's status to `PROCESSING`, and `AIClassificationService.classifyAndEmbed` — the only place classification runs — calls `mediaAssetRepository.updateStatus(assetId, MediaAssetStatus.FAILED.name())` on **every failure branch**, but **no code path anywhere in the backend ever sets an asset's status to `READY`** on success. Grepping the entire backend for `MediaAssetStatus.READY` turns up exactly one hit, and it's a `SELECT … WHERE status = 'READY'` filter, not a write. The one migration that touches `READY` (`V25__media_asset_dual_embeddings.sql`) is a one-time backfill of pre-existing rows, not an ongoing mechanism.

Practical effect: every asset uploaded since that migration is permanently stuck on `PROCESSING` even after classification and embedding genuinely succeed (`asset.aiCategory`, `asset.aiConfidence`, and the embedding row are all correctly written — just not the status). Consequences:
- The "Processing…" badge in the library grid and asset detail panel never clears for any asset, regardless of outcome.
- Every feature that filters on `status = 'READY'` — semantic/keyword search, `AIRecommendationService.suggestMedia`'s pgvector candidate query, `getSimilarMedia`, the new album Auto-Match's visual-similarity query (UC-1.7) — silently excludes every asset uploaded since, even fully-classified ones.
- No test caught this: there is no `AIClassificationServiceTest` at all, and no repository/DB-integration test layer in this codebase that would exercise the real query.

Not fixed as part of this doc-verification pass — flagged for a follow-up.

---

## Corrections from the prior draft of this UC

- **Actors:** added Moderator — upload and album management are `isAuthenticated()`, open to all three roles, not Contributor/Administrator-only.
- **Auto-Match is text matching (tags + filename), not "visual/embedding similarity."** Same finding independently confirmed during the UC-1.7 investigation, where the *submission composer's* Auto-Match was rebuilt to actually use tag overlap + Voyage/pgvector visual similarity — this standalone Media Library upload flow still uses the original, simpler tag/filename-substring version and was not touched by that change.
- **A4 (the ambiguous middle tier) does not exist for this flow** — it's a binary match/no-match, not three tiers with a confirm-or-choose-different dialog.
- **A5's "removes it from all albums" is not implemented** — every library asset must have exactly one album; the API rejects an attempt to null it out.
- Video uploads are accepted (format/size validated identically) but **never classified or embedded** — the AI pipeline trigger is image-only. The prior draft's postcondition implied this applies to any uploaded asset.

---

_Verified against the running code as of 2026-09-13. Primary sources: `MediaAssetController` (`@PreAuthorize` on every album/upload endpoint), `MediaAssetService` (`upload`, `resolveAlbum`, `autoMatchAlbum`, `updateAlbum`, `renameAlbum`, `createAlbum`, `normalizeRequiredAlbumName`), `AIClassificationService` (`classifyAndEmbed`, the `FAILED`-only `updateStatus` calls), `MediaAssetStatus` enum, `MediaAssetRepository`/`MediaAssetEmbeddingRepository` (`status = 'READY'` read filters, no write), `V25__media_asset_dual_embeddings.sql` (the one-time `READY` backfill), `frontend/src/features/media-repository/components/UploadModal.tsx` (`autoMatchedAlbum`, the tags tooltip, `fileError`/`metadataError`), `frontend/src/features/media-repository/MediaRepositoryScreen.tsx` (`handleUpdateAssetAlbum`), `frontend/src/api/mediaApi.ts` (`updateMediaAssetAlbum`), `frontend/src/features/media-repository/components/AssetCard.tsx` / `AssetDetailPanel.tsx` (the "Processing…" badge)._

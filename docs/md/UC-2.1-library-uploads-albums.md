# UC-2.1 Library Uploads & Albums

**Use Case ID:** UC-2.1

**Use Case Name:** Library Uploads & Albums

**Actor(s):** Contributor, Moderator, Administrator — upload and album management endpoints are open to any authenticated user, not role-restricted (`@PreAuthorize("isAuthenticated()")` on every relevant `MediaAssetController` route).

**Precondition(s):** The actor is authenticated with an active session. The institution's media library exists (provisioned upon institution creation, UC-1.2).

## Main Flow

1. The actor initiates an upload from the Media Library screen, selecting one or more files from their device.
2. The system validates each file against accepted formats (JPG, PNG, WEBP, GIF, MP4, MOV, WEBM) and the **50 MB** size limit, checked both client-side and server-side.
3. Before the upload is finalized, the actor specifies:
   - **Album:** select an existing album, click **Auto-Match**, or type a new name to create one — or select/type a full folder path to file the upload into a nested album structure (see A8). Auto-Match scores existing albums by tag/filename word overlap and tiers the result — a confident match (≥0.6) auto-fills with a reasons badge; an ambiguous one (≥0.3) lists up to 3 ranked candidates for the actor to confirm or override; no match leaves the field for manual entry.
   - **Tags:** the actor enters at least one tag (server-enforced). A tooltip suggests using the event name as a tag to improve search results.
4. On confirmation, the system stores the asset(s) — an image is marked `PROCESSING`; a video is marked `READY` immediately, since nothing is queued for it — applies the selected/created album, and saves the actor-entered tags.
5. The system triggers AI classification and embedding asynchronously, for image files only. A video upload is never classified or embedded.
6. The asset appears in the library grid under its assigned album with a "Processing…" badge that clears once classification and embedding succeed.

## Alternative Flows

- **A1 — Unsupported File Type:** Rejected client- and server-side.
- **A2 — File Exceeds Size Limit:** Rejected at the 50 MB limit, both ends.
- **A3 — Upload Network Failure:** Retry prompt, consistent with the composer's own pattern (UC-1.7 A3).
- **A4 — Ambiguous Album Match (Auto-Match):** Auto-Match is text-based (tag/filename overlap), not visual similarity — the file isn't yet uploaded or embedded at album-selection time, unlike the submission composer's Auto-Match (UC-1.7), which scores already-attached, already-embedded assets. A confident match (≥0.6) auto-applies with a reasons badge; an ambiguous result (≥0.3) presents up to 3 ranked candidates inline for the actor to confirm or choose differently.
- **A4a — Auto-Match, No Confident Result:** No text match found; the field is left for manual entry with an inline notice. Upload is blocked with no album name set.
- **A5 — Manual Album Reassignment:** The actor moves an asset to a different existing album. Every library asset always belongs to exactly one album — there is no "unfiled"/unassign state, by deliberate design.
- **A6 — Manual Album Creation:** The actor creates a new album independent of any upload.
- **A7 — Rename Album:** The actor renames an existing album.
- **A8 — Nested Album Organization:** An album may be created as a sub-album of another (a parent-child folder structure). The actor may either create/select a full folder path in one action (each segment created if it doesn't already exist), or re-parent an existing album under a different parent afterward. The system blocks any re-parenting action that would make an album a descendant of itself, preventing a circular folder structure.
- **A9 — Post-Upload Tag Management:** After upload, the actor may add or remove individual tags from an asset's detail view, independent of the tags entered at upload time. At least one **actor-entered** tag must always remain — removing the last one is blocked, consistent with the mandatory-tag rule enforced at upload. AI-generated tags don't count toward this: an asset with AI tags but only one manual tag still blocks that manual tag's removal, since AI classification isn't the actor's own tagging.
- **A10 — Cross-Institution Asset Movement:** An asset may be moved into or out of the shared network-default institution's library (DASIG Central Visayas), in addition to reassignment within the actor's own institution (A5). **Scope correction from the decided draft:** this is **not** Administrator-only-for-any-pair, Contributor/Moderator-only-for-shared — the code splits it as **Contributor: owner + shared-library only**, **Moderator and Admin: unrestricted, any institution to any institution**, no "All institutions" view gate required. Moderator is a network-wide role in this codebase (`isNetworkRole` = Admin or Moderator) and gets the identical unrestricted cross-institution move rights as Admin here, not the Contributor-tier restriction the decided draft grouped it with. Flagging this for a decision: match the doc to the code (Moderator = Admin-tier), or restrict Moderator in code to match the drafted intent (Contributor-tier, own-uploads + shared-library only).

## Postcondition(s)

The uploaded asset exists in the institution's media library, assigned to an album (which may be nested within a folder structure) with at least one tag. An image asset is queued for AI classification and embedding, transitioning from `PROCESSING` to `READY` on success (or `FAILED` if classification or embedding fails). A video asset is stored and organized identically but marked `READY` immediately, without AI classification or embedding — a known scope limitation of the current pipeline, not a defect. Tags and album/folder placement may continue to be edited after upload (A8, A9).

---

## Scope note

Bulk delete, tiered per-asset delete authorization, usage tracking (`/history`, `usedIn`), and semantic/keyword search are intentionally **not** part of this use case — they belong to UC-2.2 (Media Asset Retention) and adjacent search functionality, not upload/album organization.

---

## Implementation history

- **2026-09-13 — A9 tag-removal guard added.** `MediaAssetService.removeTag` previously deleted any tag unconditionally, with no floor. It now counts only `source: "manual"` tags and rejects (`400`) removing the last one, leaving AI-generated tags (`source: "ai_generated"`) out of the count entirely — they're classification metadata, not a stand-in for the actor's required tag. `AssetDetailPanel`'s tag list hides the remove (×) button on an asset's last manual tag instead of letting the actor hit the 400.
- **2026-09-13 — A4/A4a scored/tiered Auto-Match.** `UploadModal`'s Auto-Match (`scoreAlbumMatches`) replaced a "first substring match wins" binary with confident/ambiguous/none tiers, reusing `AlbumCombobox`'s `matchedBadge`/`suggestions`/`noMatchNotice` props (the same extension built for UC-1.7's composer Auto-Match).
- **2026-09-13 — `READY`-status bug fixed.** `AIClassificationService.classifyAndEmbed` previously never transitioned a successfully-classified asset off `PROCESSING` — only `FAILED` was ever written. It now sets `READY` once classification and both embeddings succeed. A video is marked `READY` immediately on upload instead of sitting on `PROCESSING` forever with nothing queued for it.
- **2026-09-13 — A5 "unassign" tried and reverted.** A null-`albumId` "remove from all albums" path was briefly implemented, then reverted on the correct call that every library asset is meant to always belong to exactly one album — there's no "unfiled" state in the actual product design.

---

_Verified against the running code as of 2026-09-13. Primary sources: `MediaAssetController` (`@PreAuthorize` on every album/upload endpoint), `MediaAssetService` (`upload`, `resolveAlbum`, `autoMatchAlbum`, `updateAlbum`, `removeTag`, `renameAlbum`, `createAlbum`, `moveAlbum`, `ensureAlbumPath`, `isNetworkRole`), `AIClassificationService` (`classifyAndEmbed`'s `READY`/`FAILED` transitions), `MediaAssetStatus` enum, `AssetTag.source`, `MediaAlbumRepository.findDescendantIds` (the re-parent cycle guard), `AIClassificationServiceTest`, `MediaAssetServiceTest` (`upload_imageAsset_staysProcessingAndTriggersClassification`, `upload_videoAsset_isReadyImmediatelyAndSkipsClassification`, `updateAlbum_withNullAlbumId_isRejected`, `removeTag_lastManualTag_isRejected`, `removeTag_withAnotherManualTagRemaining_succeeds`, `removeTag_lastManualTag_ignoresAiGeneratedTagCount`), `frontend/src/features/media-repository/components/UploadModal.tsx` (`scoreAlbumMatches`), `frontend/src/components/ui/AlbumCombobox.tsx`, `frontend/src/features/media-repository/components/AssetDetailPanel.tsx` (the tag list's last-tag guard, the "Processing…" badge)._

# UC-2.2: Semantic Search & Filtering

## Use Case ID
UC-2.2

## Use Case Name
Semantic Search & Filtering 

## Actor(s)
Contributor, Moderator, Administrator 

## Precondition(s)
The actor is authenticated with an active session. The institution media library exists (UC-1.2, UC-2.1). 

## Main Flow
1. The actor navigates to the Media Library from the workspace sidebar.
2. The system displays all media assets for the actor's institution in a grid view, grouped/filterable by album. Each asset card shows the asset code, display title, upload date, file size and type, and a "Processing…" badge if processing is ongoing. **Note: Unlike initially specified, the AI classification label is not displayed on the grid card.**
3. The actor browses using keyword search, meaning-based semantic search, an album or institution filter, sort options (Newest, Oldest, Name, Size), and a grid/list view toggle. Keyword search covers filename, display title, asset code, uploader email, available AI classification metadata, and tags. Meaning-based search uses a Voyage AI query embedding and pgvector similarity ranking, with keyword matching used as a fallback or supplement when semantic results are unavailable. Event names are not directly searchable because they are not stored on the media asset.
4. The result strip updates with the matching count and any active filter labels.
5. The actor clicks an asset card to open the Asset Detail Panel: media preview; asset code (read-only); an editable title (click to reveal a text field; Enter or blur saves, Escape cancels); a metadata grid (filename, uploader, institution, upload date, album, file size, resolution/duration, file type); an AI Tags block with per-tag confidence percentages; a "Your tags" block with an add-tag field; and a "Used In" history list.
6. From the panel, the actor may select "Add to Draft" or "New Submission" — available to all three roles.

## Alternative Flow(s)
- **A1 — No Results Found:** The system displays an empty state ("No assets match your filters") with active filters remaining visible and clearable.
- **A2 — Cross-Institution Access Attempt:** Requesting an asset outside the actor’s visible institutions returns a not-found response, reinforced by database-level row security. Contributors can browse their own institution and the shared network-default institution. Network View, which provides full cross-institution browsing, is available to Moderators and Administrators because both roles are network-wide.
- **A3 — Custom Tag or Metadata Search:** A newly added manual tag is immediately searchable through keyword matching. AI-generated tags are also included in keyword matching. Semantic ranking reflects the embeddings available for the asset and is not recomputed after every tag or title edit; therefore, later metadata changes may affect keyword results without changing semantic-ranking order.
- **A4 — Add to Existing Draft, No Active Drafts:** If the actor has no draft-status submissions, the system allows the user to click "New Submission" instead.
- **A5 — Add to Existing Draft, Asset Limit Reached:** If adding an asset would exceed the 10-media-per-submission limit, the system notifies the actor; when adding multiple assets in one action, any assets successfully attached before the limit was reached remain attached, and the cache is updated properly.

## Postcondition(s)
The actor has located and, if applicable, reused a media asset. The asset’s detail view accurately reflects its current metadata, editable display title, AI tags, manual tags, album, and available actions. Keyword and semantic search results reflect the metadata and embeddings currently available for the asset.

## Implementation Status
- **Verified:** Semantic search logic (Voyage AI + `pgvector`), keyword fallback, 10-media limits, draft UI handling, and editable title flows are all present and match the specification.
- **Discrepancy:** The specification stated that `AssetCard` shows "either a Processing… badge or the available AI classification label". However, code analysis confirms the `Processing...` badge exists, but the AI classification label is NOT rendered on the grid card (it only appears inside `AssetDetailPanel.tsx`).
- **Resolved Bug:** Found and fixed a cache bypass bug in `frontend/src/features/media-repository/MediaRepositoryScreen.tsx`. Previously, if adding multiple assets triggered a 422 error (due to the 10-media limit), the UI failed to run `syncSubmissionCache` and `invalidateQueries` for the successfully attached assets because it threw an error and exited the loop. This was fixed by catching the error inside the loop, breaking it, and invalidating the cache before re-throwing the error to show the toast message.

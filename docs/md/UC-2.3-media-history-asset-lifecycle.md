# UC-2.3 Media History & Asset Lifecycle

**Use Case ID:** UC-2.3

**Use Case Name:** Media History & Asset Lifecycle

**Actor(s):** Contributor, Moderator, Administrator — all three roles can browse visible media assets. Moderators and Administrators are network-wide; Contributors are limited to their institution and the shared default library where applicable.

> **Numbering note:** The current project documentation also uses UC-2.3 for Notifications in the Module 2 implementation summary. This document describes the media-history/lifecycle capability that is implemented alongside UC-2.1 and UC-2.2. The numbering should be reconciled in the project SRS before final submission.

## Precondition(s)

- The actor is authenticated with an active session.
- A media asset exists in the library, or the actor is attaching an existing library asset to a draft/submission.
- The actor can access the asset under the media-library visibility rules.

## Main Flow

1. The actor opens an asset in the Asset Detail Panel.
2. The system displays the asset metadata, album, tags, and **Used In** submissions. Each current usage includes the submission title, submitted date, status, and a submission deep link where the submission still exists.
3. When an existing library asset is attached to a submission, the system creates a `submission_media_assets` relationship and records a `MEDIA_ASSET_REUSED` audit event containing the asset ID, submission ID, submission title, and submission status.
4. The asset Activity tab displays immutable audit history for upload, reuse, movement, rename, deletion, tag changes, and other recorded media events.
5. If a reuse audit record points to a submission that has since been deleted, the Used In entry is retained and displayed as **`[Submission Deleted]`** without a dead jump link.
6. An Administrator can review media lifecycle events through the administrator audit-log interface. Moderators may also access network-wide media within their role permissions.

## Alternative Flows

### A1 - Active Submission Deletion Block

If an asset is referenced by a `pending`, `in_review`, or `scheduled` submission, deletion is rejected with HTTP `409 CONFLICT`. The response includes the conflicting submission ID, title, status, and deep link so the client can identify the blocking record.

The current frontend also pre-computes the deletion tier from the asset's Used In records and displays a blocked-deletion confirmation state.

### A2 - Draft Reference Warning

If an asset is referenced only by `draft` or `needs_revision` submissions, deletion requires the explicit force path. The backend returns a structured conflict unless `force=true` is supplied.

A forced deletion soft-deletes the asset. The submission-media relationship remains visible to the submission and is represented as **`[Asset Deleted]`** with no storage URL, allowing the composer or submission detail view to identify the broken reference.

### A3 - Terminal or Unused Asset Deletion

If no active submission blocks deletion, the actor may delete the asset. The system:

- sets `deleted_at`;
- records the deleting user;
- changes the asset status to `DELETED`;
- removes its embeddings; and
- records `MEDIA_ASSET_DELETED` in the audit log.

The physical object is retained until the media retention purge job permanently removes it. Bulk deletion is also supported.

### A4 - Submission with a Broken Media Reference

Before a draft or `needs_revision` submission transitions to `pending`, the system verifies that it has at least one usable, non-deleted media attachment. A submission whose only attachments are deleted assets is rejected with HTTP `422` and a message instructing the actor to replace the deleted media.

### A5 - Duplicate Upload Detection

During a direct media-library upload, the browser computes a SHA-256 content hash before requesting the R2 presigned upload URL. The backend stores the hash in `media_assets.content_hash` and checks for an active asset with the same hash in the target institution.

If an exact duplicate exists, the upload is paused before the browser sends bytes to R2. The dialog offers:

- **Use Existing** — closes the uploader and opens the matching asset in the repository;
- **Upload Anyway** — retries only the duplicate file with an explicit override and records it as a separate asset; or
- **Cancel** — skips the duplicate file and continues the remaining files in a multi-file upload.

This is exact-content detection only. High visual similarity is used by recommendation and album-matching features but is not currently used as duplicate detection.

## Postcondition(s)

- Reuse relationships are visible in the asset's Used In block.
- Reuse and lifecycle operations are retained in the audit log.
- Deleted submissions are represented as `[Submission Deleted]` in historical usage records.
- Deleted assets are represented as `[Asset Deleted]` in submission media summaries.
- Submissions cannot be submitted when they contain no usable media.
- Active submissions cannot lose a referenced asset through deletion.
- Exact duplicate uploads are detected before storage upload.
- Soft-deleted assets remain recoverable by the retention process until permanent purge.

## Current Scope and Related Features

The capability depends on the following UC-2.1 and UC-2.2 features:

- institution-scoped and network-wide media visibility;
- nested albums and cross-institution asset movement;
- editable display titles independent of the original filename;
- manual and AI-generated tag distinction;
- searchable manual tags;
- semantic search through Voyage embeddings and pgvector;
- processing states (`PROCESSING`, `READY`, `FAILED`, and `DELETED`);
- Add to Draft and New Submission actions;
- a maximum of 10 media assets per submission;
- bulk deletion and 30-day media retention purge;
- once-per-session Network View access auditing.

## Known Limitations

- Duplicate detection is based on SHA-256 equality only; resized, recompressed, or visually similar files are not considered duplicates.
- Current submission usage rows are backed by the submission-media relationship. Historical reuse after relationship removal is preserved through `MEDIA_ASSET_REUSED` audit records rather than a separate usage-history table.
- The current Used In ordering is based on relationship creation time for current links and audit creation time for historical reuse records; it is not a single database-level chronological union.
- The deletion-conflict response is structured on the backend, but the frontend still primarily uses its locally loaded Used In data to render the deletion modal.

## Verification Sources

Verified against the current codebase on 2026-09-13. Primary sources:

- `MediaAssetService` (`get`, `history`, `upload`, `createUploadUrl`, `delete`, `bulkDelete`, `validateDeleteReferences`, `recordAssetAudit`);
- `SubmissionService` (`attachLibraryAssetTo`, `submit`, `delete`);
- `MediaAssetSummaryDto` and `MediaAssetUsageDto`;
- `SubmissionMediaAssetRepository`;
- `AuditLogService`;
- `MediaAssetDeletionConflictException` and `GlobalExceptionHandler`;
- `V92__media_asset_content_hash.sql`;
- `AssetDetailPanel.tsx`, `MediaRepositoryScreen.tsx`, and `mediaApi.ts`;
- `SubmissionServiceTest`, `MediaAssetServiceTest`, and the focused backend test suite.

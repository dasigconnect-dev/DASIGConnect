# UC-2.3 Media History Tracking

**Use Case ID:** UC-2.3
**Use Case Name:** Media History Tracking
**Actor(s):** Contributor, Moderator, Administrator
**Precondition(s):** The actor is authenticated with an active session and is viewing an asset via the Asset Detail Panel (UC-2.2) or the audit log.

**Main Flow:**
From the Asset Detail Panel, the actor views the Used In block: a chronological list of all submissions that have referenced this asset, each showing submission title, date, current status badge, and a jump link (subject to the actor's access permissions). Deleted submissions display as "[Submission Deleted]."
The system records every reuse action (attachment to a new post or draft) server-side, updating the Used In block upon draft save or submission.
The system records every media lifecycle event — upload, edit, album reassignment, reuse, and deletion — to an immutable media audit log.
An Administrator may view the media audit log from the admin console to review asset history network-wide.

**Alternative Flow(s):**
A1 — Asset Deletion, Blocked: If the asset is referenced in a submission in PENDING_APPROVAL or SCHEDULED state, the system blocks deletion and displays the conflicting submission with a jump link.
A2 — Asset Deletion, Warning: If the asset is referenced only in DRAFT or NEEDS_REVISION submissions, the system prompts for confirmation, warning that the reference will break; the affected draft surfaces a broken-reference warning the next time the Contributor attempts to submit it (UC-1.9).
A3 — Asset Deletion, Free: If the asset is referenced only in terminal-state submissions or has no references, the system prompts for confirmation and, upon approval, marks the asset deleted; Used In entries in terminal submissions update to "[Asset Deleted]."
A4 — Duplicate Asset Detected: Upon upload (UC-2.1), if the system detects a likely duplicate of an existing asset based on an exact content hash match, it flags the new upload and prompts the actor to confirm intent to "Upload Anyway", "Cancel", or "Use Existing" (which opens the matching asset in the repository). Visual similarity is not used to detect duplicates upon upload.

**Postcondition(s):**
The asset's full lifecycle — reuse, album changes, and deletion status — is accurately reflected in its Used In block and the media audit log, retained for audit purposes even after deletion.

---

### Technical Version (For Developers)

# UC-2.3 Media History & Asset Lifecycle

**Use Case ID:** UC-2.3
**Use Case Name:** Media History & Asset Lifecycle
**Actor(s):** Contributor, Moderator, Administrator – all three roles can browse visible media assets. Moderators and Administrators are network-wide; Contributors are limited to their institution and the shared default library where applicable.

> **Legacy reference:** Older implementation notes may call the notification infrastructure "UC-2.3." In the authoritative SRS numbering, this document is **UC-2.3 Media History & Asset Lifecycle**; notifications are documented separately under the current notification implementation materials.

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
- **Use Existing** – closes the uploader and opens the matching asset in the repository;
- **Upload Anyway** – retries only the duplicate file with an explicit override and records it as a separate asset; or
- **Cancel** – skips the duplicate file and continues the remaining files in a multi-file upload.
This is exact-content detection only. High visual similarity is used by recommendation and album-matching features but is not currently used as duplicate detection upon upload.

## Postcondition(s)
- Reuse relationships are visible in the asset's Used In block.
- Reuse and lifecycle operations are retained in the audit log.
- Deleted submissions are represented as `[Submission Deleted]` in historical usage records.
- Deleted assets are represented as `[Asset Deleted]` in submission media summaries.
- Submissions cannot be submitted when they contain no usable media.
- Active submissions cannot lose a referenced asset through deletion.
- Exact duplicate uploads are detected before storage upload.
- Soft-deleted assets remain recoverable by the retention process until permanent purge.

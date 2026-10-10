**Use Case ID**
UC-3.6

**Use Case Name**
Audit Log Review 

**Actor(s)**
Administrator

**Precondition(s)**
The actor is authenticated with Administrator privileges. At least one auditable action has occurred in the system. 

## Main Flow
1. The actor navigates to System & Audit → Audit Log from the admin console.
2. The screen displays a table of audit entries, newest first, with these columns: Actor, Timestamp (Asia/Manila), a combined Category/Action badge, Affected Entity, and a generated one-line Summary. Each row also carries additional data available in the detail view: the actor's role, institution, and Admin Owner flag; the target entity's live/deleted state; and the client IP address and user agent recorded at the time of the action.
3. The actor can filter the log server-side by date range, actor (by ID or free-text name/email), category, specific action code, entity type, resource ID, and a general free-text search across actor email/name/action/IP — combinable, with paginated results.
4. Categories (11): Approval, Rejection, Edit & Revision, Reschedule & Override, Publishing, Account Management, Institution Management, Media Lifecycle, Configuration, Security, Other. There is no standalone "Deletion" category — asset and album deletions fall under Media Lifecycle, and user or institution deletions fall under Account Management / Institution Management respectively.
5. Entity types (8): Submission, Media Asset, Media Album, User, Institution, Facebook Token, Watermark Configuration, System.
6. Selecting an entry opens a detail view showing:
   - Actor & Execution details (name, email, role, institution, action code, client IP)
   - Target Entity (type, a jump link to the live record or a "Deleted/Unavailable" badge, entity ID)
   - A generated summary, including any override or justification reason provided at the time of the action
   - A structured diff table (Field / Previous Value / Updated Value) for actions that changed data — covering edit/caption changes, schedule changes, status transitions, and account role changes — with a raw-detail inspector available for anything outside the structured diff types.
7. The actor can export the currently filtered results as a CSV, which is itself recorded as an audited event.

## Alternative Flow(s)
- **A1 — No Matching Entries.** The table shows a standard empty state when the current filters return no results.
- **A2 — Entry References a Deleted Entity.** If the entity an entry points to no longer exists, the table and detail view show a "Deleted/Unavailable" badge instead of a jump link. Live jump links are available for Submission, User, Media Asset, and Institution entities; Facebook Token, Watermark Configuration, and Media Album entities link to their general management screen rather than a per-entity deep link; System-typed entries have no jump link.
- **A3 — Export Failure.** A failed export surfaces as a toast notification. The current filter state is preserved, so the actor doesn't need to reconstruct their filters to retry.
- **A4 — Large Date Range Query.** The table view uses server-side pagination and is unaffected by range size. The CSV export caps at 5,000 rows; a filtered result set larger than that is truncated in the export without a user-facing warning.

## Postcondition(s)
The audit log is append-only at the database level: the audit table's row-level security policy permits inserts only, with no update or delete policy defined, so entries cannot be modified or removed regardless of application-layer behavior. The only sanctioned mutations are a scheduled retention purge (see below) and a right-to-be-forgotten scrub that removes only the actor's email fields from an entry's metadata, without deleting the entry itself. 

=========================================
## Implementation Status

✅ **Implemented:**
- **Access Control:** `AuditLogController` correctly restricts all endpoints via `@PreAuthorize("hasRole('ADMIN')")`.
- **Columns & Detail View:** Present in `AuditLogScreen.tsx` (Actor, Timestamp, Action/Category, Affected Entity, Summary) and `AuditDetailModal.tsx`. Lookups (User, Institution, MediaAsset, Submission) are effectively batched in `AuditLogService.buildLookups`.
- **Filters:** API supports all specified filters: `startDate`, `endDate`, `actorId`, `actorQuery`, `category`, `action`, `entityType`, `resourceId`, and `search`.
- **Export Logging & Limits:** The CSV export generates an `AUDIT_LOG_EXPORTED` event and enforces the 5,000-row limit (`entries.subList(0, 5000)`).
- **Append-Only DB:** Implemented via PostgreSQL Row-Level Security in `V78__audit_log_append_only.sql`, explicitly denying `UPDATE` and `DELETE`.
- **Transaction Isolation:** Audit entries are written in an independent `REQUIRES_NEW` transaction (via `AuditLogWriter`), ensuring audit records commit even if the parent business transaction fails.
- **Formula Injection Mitigation:** The CSV exporter (`escapeCsv`) explicitly prepends an apostrophe if an entry starts with `=` or other formula triggers.

❌ **Not Implemented:**
- **None.** The outlined functionality is broadly present.

⚠️ **Discrepancies:**
- **Jump Links (A2):** The use case claims deep per-entity jump links are available for Submission, User, Media Asset, and Institution. However, `AuditLogService.resolveEntity()` only provides an actual per-entity deep link for `Submission` (`/submissions?id=...`). `User` (`/admin/admin-management`), `Media Asset` (`/media-repository`), and `Institution` (`/institution-management`) simply link to their respective general management screens without passing the entity ID.



# UC-3.6 Audit Log Review

**Use Case ID:** UC-3.6

**Use Case Name:** Audit Log Review (the admin Audit Log screen).

**Actor(s):** Administrator — confirmed exactly: `AuditLogController` is class-level `@PreAuthorize("hasRole('ADMIN')")`, no other role can reach any endpoint on this screen. Frontend mirrors it: `/admin/audit-log` is wrapped in `<ProtectedRoute allowedRoles={["admin"]}>`.

**Precondition(s):** The actor is authenticated with Administrator privileges. At least one auditable action has occurred in the system.

## Main Flow

1. Confirmed — the actor navigates to System & Audit → Audit Log from the admin console.
2. **Columns: draft undercounts.** The table shows Actor, Timestamp (Asia/Manila), Action/Category badge, Affected Entity, and a generated one-line Summary — five columns, not a literal "action type" cell but a combined category+action badge. Beyond what's visible in the table, each `AuditLogDto` row also carries fields the draft doesn't mention at all: actor **role**, **institution name**, and **Admin Owner** flag; the entity's live/dead state (`exists`) and a `jumpUrl`; and **client IP address + user agent** (`AuditLog.ip_address`/`user_agent`, `inet`/`text` columns). Sorting is newest-first, matching "reverse-sorted."
3. **Filtering: real and server-side**, via a JPA `Specification` (`AuditLogService.buildSpecification`) — not client-side-only. Query params: `startDate`, `endDate`, `actorId`, `actorQuery` (free-text name/email), `category`, `action` (exact code), `entityType`, `resourceId`, `search` (free-text over actor email/name/action/IP), plus `page`/`size`.
   - **Category grouping is real, not aspirational**, but broader than the draft's list: `AuditLogCategory` has 11 values — `APPROVAL`, `REJECTION`, `EDIT_AND_REVISION`, `RESCHEDULE_AND_OVERRIDE`, `PUBLISHING`, `ACCOUNT_MANAGEMENT`, `INSTITUTION_MANAGEMENT`, `MEDIA_LIFECYCLE`, `CONFIGURATION`, `SECURITY`, `OTHER`. `PUBLISHING` and `SECURITY` aren't in the draft's example list. There is no standalone "Deletion" category — asset/album deletes fall under `MEDIA_LIFECYCLE`, user/institution deletes under `ACCOUNT_MANAGEMENT`/`INSTITUTION_MANAGEMENT`.
   - Category and entity type are **derived at read/filter time from the raw action string**, not stored columns — `AuditLogCategory.fromAction()` drives display, and a second, independently-maintained mapping (`AuditLogService.getActionsForCategory`) drives the `category=` filter's `WHERE action IN (...)`. These two mappings must be kept in sync by hand; not found out of sync in this pass, but it's a latent risk.
   - Entity type filter (`AuditEntityType`, 8 values: `SUBMISSION`, `MEDIA_ASSET`, `MEDIA_ALBUM`, `USER`, `INSTITUTION`, `FACEBOOK_TOKEN`, `WATERMARK_CONFIG`, `SYSTEM`) is broader than the draft's five — splits media assets from albums and adds a distinct Facebook Token type.
4. **Detail/diff view: matches, and goes further than the draft describes.** `AuditDetailModal` shows Actor & Execution (name, email, role, institution, action code, client IP), Target Entity (type, jump link or a "Deleted/Unavailable" badge, entity UUID), Summary/Justification, a **structured diff table** (Field / Previous Value / Updated Value) when diffs exist, and a raw-JSON fallback inspector for anything not covered by the structured view — both, not one or the other. Diff extraction (`AuditLogService.extractDiffs`) covers a generic `editDiff` map (used for things like an Edit & Approve caption change) plus three hardcoded before/after pairs: scheduled slot, status transition, and account role. A guard-rail override reason surfaces in the generated `summary` sentence, not as a diff row.
5. **CSV export: real endpoint**, and its formula-injection gap was found and fixed the same session as this verification (see "Fixed this session" below).

## Alternative Flows

- **A1 — No Matching Entries:** standard empty-state pattern; not specifically re-verified beyond confirming filters are real server-side predicates that can legitimately return zero rows.
- **A2 — Entry References a Deleted Entity: confirmed accurate, exact string used.** `AuditLogService.resolveEntity` returns the literal label `"[Entity no longer available]"` with `exists=false` for a missing Submission/User/Media Asset/Institution. The frontend abstracts this into a styled "Deleted/Unavailable" badge (table and modal alike) rather than displaying the raw string. Jump links exist for live Submission, User, Media Asset, and Institution rows; Facebook Token, Watermark Config, and Media Album entities get a static label with a route-level link instead of a true per-entity deep link; `SYSTEM`-typed rows get no jump link at all.
- **A3 — Export Failure: partial match.** The export is wrapped in try/catch, but failure surfaces as a **toast**, not an inline error near the export control (unlike the inline-error+retry pattern already built for the Analytics report modal). Filter state does survive a failed export, so the "without losing the current filter state" half of A3 holds even though the "inline error" half doesn't literally.
- **A4 — Large Date Range Query: pagination is real, the "narrow the range" prompt is aspirational.** Server-side pagination is real (`page`/`size`, size clamped 1–100, sorted newest-first) and the frontend has full prev/next controls. There is no date-span validation or warning threshold anywhere for the table view. The CSV export has a related but different real behavior not named in the draft at all: it silently caps at **5,000 rows** with no user-facing truncation warning — a large-range export beyond that just drops the tail.

## Postcondition(s)

Matches, and is enforced more strongly than the draft implies: not merely "no delete button," but a DB-level guarantee. `V78__audit_log_append_only.sql` gives the audit table an `INSERT`-only RLS policy and explicitly defines no `UPDATE`/`DELETE` policy — the database itself rejects those regardless of what the application layer does. `AuditLogController` exposes only `@GetMapping` endpoints. The only sanctioned mutations at all are a retention purge scoped to a fixed low-value action allowlist, and a right-to-be-forgotten scrub that strips only `email`/`originalEmail` keys from metadata — never deletes a row.

## Not in the original draft at all

- **A narrower, non-Admin consumer of the same table exists elsewhere.** `MediaAssetService` builds a per-asset "Activity history" entry (open to all three roles for assets they can see) from `AuditLogRepository.findByResourceIdOrderByCreatedAtDesc` — this is the "Used In" block referenced in the UC-2.3 Media History doc, not a second entry point into the Audit Log screen itself. RLS backs this with a separate read policy letting institution-scoped users see rows where the actor is in their own institution.
- **Tiered, mostly-indefinite retention.** `AuditLogRetentionJob` runs daily and prunes only a fixed allowlist of high-volume operational actions (login success/failed, logout, media upload, submission update, background job run) older than `app.audit.retention.operational-days` (default 730 days). Everything else — security events, account/role changes, erasure, institution changes, override decisions, exports — is retained indefinitely by design. Distinct from, and far longer-lived than, the 30-day media-asset retention window.
- **Every export is itself an audited event.** `AUDIT_LOG_EXPORTED` records row count and the filter summary used, so "who pulled a governance report and with what filters" is answerable from the log itself — a detail the draft's postcondition doesn't mention.
- **Right-to-be-forgotten interaction.** Erasing an account (UC-1.1/UC-1.3) does not delete that account's audit rows — they're retained per the policy above — but does scrub `email`/`originalEmail` out of stored metadata wherever that account was actor or resource, which affects what an Admin sees when reviewing history involving an erased account.

## Fixed this session (2026-09-18)

- **CSV/Formula Injection (CWE-1236) in `AuditLogService.escapeCsv()`.** The export includes free-text, user-controlled fields (actor name/email, entity labels such as a submission's event title, generated summaries that can embed rejection/revision-request remarks) and the escaping helper only neutralized embedded double-quotes — it did not guard a leading `=`/`+`/`-`/`@` character, the exact CSV-injection pattern already found and fixed in the Analytics export (`MetricsAggregatorService.escapeCsv()`) and flagged-but-not-fixed in System Health's export. Fixed the same way: any cell whose first character is a formula trigger (`=+-@`, tab, or CR) gets a leading apostrophe before quoting, forcing spreadsheet apps to render it as literal text.

## Known limitations (not changed this pass)

- Export failure shown via toast, not an inline error+retry control near the export button.
- CSV export silently truncates at 5,000 rows with no user-facing warning.
- No large-date-range warning/prompt exists for either the table view or the CSV export (the 5,000-row cap is the closest real analog, and it's silent).
- Category/entity-type derivation from the raw action string is duplicated across two hand-maintained mappings (`fromAction` for display, `getActionsForCategory` for filtering) with no compile-time guarantee they stay in sync, and a new action code with no matching keyword silently falls into `OTHER`/`SYSTEM`.

---

_Verified against the running code as of 2026-09-18, including the same-day fix hardening `AuditLogService.escapeCsv()` against formula injection. Primary sources: `AuditLogController` (`getAuditLogs`, `export-csv`, `categories`), `AuditLogService` (`buildSpecification`, `getActionsForCategory`, `resolveEntity`, `extractDiffs`, `generateSummary`, `exportAuditLogsCsv`, `escapeCsv`), `AuditLog` entity, `AuditLogDto`/`ActorDto`/`EntityRefDto`/`ClientInfoDto`, `AuditLogCategory`, `AuditEntityType`, `AuditLogRepository` (`deleteByActionInAndCreatedAtBefore`, `scrubPersonalMetadataForUser`, `findByResourceIdOrderByCreatedAtDesc`), `AuditLogRetentionJob`, migration `V78__audit_log_append_only.sql`, `frontend/src/features/audit-log/AuditLogScreen.tsx`, `AuditDetailModal.tsx`, `frontend/src/api/auditLogApi.ts`, `frontend/src/app/App.tsx` (route guard), `MediaAssetService` (Activity history cross-reference)._

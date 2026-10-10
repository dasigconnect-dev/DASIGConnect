**Use Case ID**
UC-3.5

**Use Case Name**
System Health Analytics 

**Actor(s)**
Administrator

**Precondition(s)**
The actor is authenticated with Administrator privileges. All endpoints on this screen are restricted to the Administrator role. 

## Main Flow
The actor navigates to System Health from the admin console. This screen is distinct from both the Analytics dashboard and the Resolution Center.

The screen displays current infrastructure and operational health, grouped into four sections:
- **Storage capacity.** Database storage is measured directly from the database engine; media storage is measured via a live scan of the object storage bucket (including derivatives and orphaned objects), with a database-derived estimate used as a fallback if that scan fails. Both are shown against configurable platform-tier limits, defaulting to the current free-tier caps for the underlying database and object storage providers.
- **External API connection status**, for six integrations:
  - Facebook Graph API — live token status with a real expiry countdown.
  - Anthropic Claude and Voyage AI — reachability and API-key-configured checks (not full authenticated capability calls).
  - Email Service Provider — reachability check against the configured mail API.
  - Database Health — reachability check against the relational database engine.
  - Media Object Storage Health — bucket ping against the Cloudflare R2 bucket.
- **Background job health**, covering 16 tracked jobs: Publishing Scheduler, Review Lock Cleanup, Stale Submission Detector, Abandonment Detector, Token Publishing Escalation, Validation Deadline Notification, Embedding Reconciliation, Social Engagement Sync, Invitation Expiry, Media Asset Retention Purge, Generated Watermark Purge, Stale Draft Slot Release, Token Health Check, Scheduled Job Run Retention, Embedding Failure Digest, and Empty Schedule Warning. Each job has a known expected interval (ranging from one minute to weekly); a job is flagged stale only once its last run exceeds twice its expected interval, so infrequent jobs aren't misflagged for being quiet between runs.
- **Operational metrics**, computed over a rolling 30-day window. This covers 14 metrics in total: 6 core metrics (approval turnaround time, Edit & Approve rate, manual fallback resolution rate, publish success rate, missed-review rate, and Live Event Fast-Track submission volume) and 8 AI telemetry metrics (such as queue depth, completion rate, and embedding coverage). Publish success rate uses the same calculation as the Analytics dashboard, so the two screens always agree on that figure.

An overall page-level status rolls up the worst status across all storage, service, job, and metric rows, with counts of how many items fall into each of WARNING, UNHEALTHY, and UNAVAILABLE, shown before the Administrator reviews individual sections.

The Administrator can trigger any tracked background job on demand via a per-job Re-run control, independent of its schedule. A dedicated shortcut is available specifically for re-running the Token Health Check job. Every re-run is audit-logged. Two jobs — Publishing Scheduler and Token Publishing Escalation — run asynchronously when manually triggered, since they may retry multiple submissions with backoff delays; the Administrator sees a "started" acknowledgment rather than an immediate result, and refreshes to see the outcome. The remaining 13 jobs are fast, database-only operations and return their result synchronously.

Storage, external services, background jobs, and operational metrics all use a consistent status vocabulary — HEALTHY, WARNING, UNHEALTHY, UNAVAILABLE, or SCHEDULED — shown as an inline status indicator on each item. Storage status changes at configurable thresholds (default 80% warning, 95% critical).

## Alternative Flow(s)
- **A1 — Metric Unavailable.** Any metric whose underlying check fails (database unreachable, object-storage probe and fallback both fail, no Facebook token configured, an external API key missing, a job that has never run and isn't due) is shown as UNAVAILABLE — a distinct status from UNHEALTHY, never presented as a false healthy or unhealthy reading.
- **A2 — Storage Threshold Warning.** Storage status changes color at the configured warning/critical thresholds. This is shown as an inline indicator on the storage card itself (and reflected in the overall page status), remaining visible for as long as the condition holds.
- **A3 — Background Job Failure Detected.** A failed or stale job is flagged WARNING or UNHEALTHY in the job list. There is currently no dedicated notification trigger for job failures; visibility is limited to this screen.
- **A4 — Export System Health Snapshot.** The Administrator can export a point-in-time CSV snapshot of all storage, service, job, and metric rows. The export is audit-logged.
- **A5 — Token Re-authentication.** The Administrator can re-authenticate the Facebook integration via OAuth. This updates the stored token and is audit-logged. Resumption of any submissions suspended by the prior token failure, and clearing of their associated expiry-warning flags, happens per-submission as the escalation job's next scheduled check (or an individual submission's own retry) succeeds — not as an immediate effect of the re-authentication step itself, which typically completes within a few minutes.

## Postcondition(s)
The actor views current, accurate infrastructure and operational health status, with threshold-based warnings on storage, background jobs, external services, and operational metrics. Any metric that cannot be computed is shown as UNAVAILABLE rather than a false reading. 

=========================================
## Implementation Status

✅ **Implemented:**
- **Access Control:** Restricted exactly to `Administrator` role (via `@PreAuthorize("hasRole('ADMIN')")`).
- **Storage Metrics:** Database (`pg_database_size`) and media (`MediaStorageService.probeUsage()` against Cloudflare R2 bucket) are fully implemented against configurable limits.
- **External Integrations:** Includes probes for Facebook Graph API, Anthropic Claude Vision API, Voyage AI API, Email Service Provider, plus Database Health and Media Object Storage Health (6 integrations total).
- **Background Jobs:** 16 jobs are tracked and report health based on their specific intervals (e.g., 1 minute to weekly). `SystemHealthService` tracks: `PublishingSchedulerJob`, `ReviewLockCleanupJob`, `StaleSubmissionDetectorJob`, `AbandonmentDetectorJob`, `TokenPublishingEscalationJob`, `ValidationDeadlineNotificationJob`, `EmbeddingReconciliationJob`, `SocialEngagementSyncJob`, `InvitationExpiryJob`, `MediaAssetRetentionPurgeJob`, `GeneratedWatermarkPurgeJob`, `StaleDraftSlotReleaseJob`, `TokenHealthCheckJob`, `ScheduledJobRunRetentionJob`, `EmbeddingFailureDigestJob`, `EmptyScheduleWarningJob`. 
- **Operational Metrics:** Tracks 14 metrics over a 30-day window: 6 core metrics and 8 AI telemetry metrics (from `MediaAiTelemetryService`).
- **Overall Status:** Page-level rollup status (`SystemHealthSummaryDto.overall`) correctly computes the worst-of across all rows.
- **Threshold Warnings:** Works across storage, jobs, services, and operational metrics with `HEALTHY`/`WARNING`/`UNHEALTHY`/`UNAVAILABLE`/`SCHEDULED`.
- **Alternative Flows A1, A2, A3, A4:** All behavior precisely matches the backend endpoints, data returned, and CSV exporting behavior.
- **Alternative Flow A5 (Token Re-authentication):** `TokenManagementService.handleCallback` logs `TOKEN_REAUTHORIZED`. However, resumption of publishing is handled by `TokenPublishingEscalationJob` later, and block flags are cleared when `markPublished`/`markFailed` completes, not synchronously.
- **Re-run Job Action:** The manual `POST /system-health/jobs/{jobKey}/run` is implemented. Async handling for `PublishingSchedulerJob` and `TokenPublishingEscalationJob` is present.
- **Frontend AI UI:** Frontend is equipped with custom icons and detailed benchmarks for the 8 AI telemetry metrics.

❌ **Not Implemented:**
- **Job Execution History:** No execution history drill-in exists. The `ScheduledJobRunRepository` only exposes the single latest run per job.
- **Per-Institution Storage Breakdown:** No endpoint or query exists for grouping storage by institution.

⚠️ **Discrepancies:**
- Storage thresholds are indicated inline via a usage meter bar rather than a separate persistent banner.

🔍 **Undocumented Code:**
- **Infrequent Job Handling:** The backend applies an `INFREQUENT_JOB_THRESHOLD` (23 hours) to prevent weekly/daily jobs from showing as UNAVAILABLE immediately after system restart before their first run.
- **CSV Formula Injection Risk:** `SystemHealthService.exportSnapshotCsv()` uses a basic `escape()` method similar to an older vulnerable implementation. It is not currently exploitable as no free-text is included, but represents a hardening opportunity.

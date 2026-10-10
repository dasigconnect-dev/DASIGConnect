Use Case ID
UC-3.4

Use Case Name
Social Engagement Analytics

Actor(s)
Contributor, Moderator, Administrator.

Precondition(s)
The actor is authenticated. At least one submission has been published to generate meaningful analytics data.

Main Flow
1. The actor navigates to the Analytics dashboard.
2. The system displays role-scoped analytics data. Contributors view metrics for their institution's posts. Moderators and Administrators view network-wide metrics across all institutions.
3. The system displays two structurally distinct categories of data: Workflow metrics (such as posting frequency and content completeness rate) are computed live on every dashboard load. Facebook engagement metrics (such as reactions, comments, shares) are read from a periodically-refreshed cache.
4. The actor may apply filters to narrow the displayed data. Administrators can filter by multiple institutions. All roles can filter by date range.
5. Administrators can view additional operational metrics, AI feature performance, and approval-turnaround-time statistics. Administrators can also view live Facebook Page-level insights distinct from per-post engagement.
6. The actor may export the current view for any metric to a CSV file or view it as an in-app report modal.

Alternative Flow(s)
A1 — Insufficient Data for Metric: A filter combination with no published posts displays an empty state rather than an error.
A2 — Facebook Engagement Data Delayed: A recently published post that hasn't yet been included in a cache refresh cycle displays an indicator that posts are pending updated figures from Facebook.
A3 — Export Failure: If an export fails, the system displays an inline error and allows the user to retry downloading without losing the current filter state.
A4 — Cross-Institution Access Attempt (Contributor): A Contributor attempting to access another institution's analytics receives an authorization error; their view is strictly scoped to their own institution.

Postcondition(s)
Every actor views analytics accurately scoped to their role. Workflow metrics reflect the current state. Filtering and exports accurately reflect the data. The dashboard refreshes its data on a 5-minute cadence.
=========================================
## Implementation Status

✅ **Implemented:**
- **Role-based Access:** Analytics is correctly scoped. Contributors see their institution data. Admins and Moderators see network-wide data. Admins also see operational metrics (AI Performance, Operational Health, Validator Workload).
- **Live vs. Cached Data:** Workflow metrics are computed live from database queries. Facebook engagement metrics are fetched from `submission_engagement_metrics` and updated asynchronously.
- **Filters:** Date range filter is fully implemented supporting presets and custom ranges. Institution multi-select filter is implemented for Administrators.
- **Alternative Flows:** A1 (Empty States), A2 (Pending indicators), A3 (Export failure UI handling), and A4 (Cross-institution 403 block) are all implemented accurately.
- **CSV Formula Injection fix:** The CWE-1236 vulnerability in the export feature is fixed using `escapeCsv`.
- **Background Refresh:** The UI automatically refreshes the data every 5 minutes (`BACKGROUND_REFRESH_MS = 5 * 60_000`) when the tab is visible.
- **Page Performance:** Admin-only Facebook Page-level insights fetch data gracefully degrading if Meta rejects the metrics.

❌ **Not Implemented:**
- **Category Filter:** Submissions have a `category` field, but it is not used in Analytics. There is no category filter on the dashboard.
- **Facebook Cache Refresh Timestamp:** The dashboard does not display an indication of exactly when the Facebook cache was last refreshed (it only displays the `pendingCount` of posts awaiting sync and the overall `lastUpdated` time of the `AnalyticsSummaryDto`).

⚠️ **Discrepancies:**
- **Posts-by-Institution Export Fields:** The original document draft implied that the `posts-by-institution` export includes user-controlled free-text fields like `event_title` and `contributor_name`. However, the implementation of `posts-by-institution` groups metrics by `institution_name` and `status`, only returning `institution_name`, `status`, and `post_count`. (The `event_title` and `contributor_name` do exist in the `submissionReportRows` DTO and `facebook-engagement` report, but not in the `posts-by-institution` export).
- **Placeholder "Content Quality" for Non-Admins:** The "Content Quality & Compliance" panel for Contributors/Moderators currently renders hardcoded strings ("Watermark Valid 100%", "Publish Readiness: Optimal") instead of live computed data.

🔍 **Undocumented Code:**
- **AI Feature Adoption Tracking:** The backend heavily tracks AI feature adoptions (Album Auto-Match, Template from Top Posts, Writing Check) and generates an Admin-only AI Performance report, which was heavily expanded beyond the basic description.
- **CSV Sanitization:** To prevent spreadsheet formula injection, any string cell starting with `=, +, -, @, \t, \r` is prefixed with an apostrophe in the CSV export.

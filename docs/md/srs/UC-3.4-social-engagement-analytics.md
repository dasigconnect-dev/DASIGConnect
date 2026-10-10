Use Case ID
UC-3.4

Use Case Name
Social Engagement Analytics

Actor(s)
Contributor, Moderator, Administrator.

Precondition(s)
The actor is authenticated. At least one submission has been published to generate meaningful analytics data.

13: Main Flow
14: 1. The actor navigates to the Analytics dashboard.
15: 2. The system displays role-scoped analytics data. Contributors view metrics for their institution's posts. Moderators and Administrators view network-wide metrics across all institutions.
16: 3. The system displays two structurally distinct categories of data: Workflow metrics (such as posting frequency and content completeness rate) are computed live on every dashboard load. Facebook engagement metrics (such as reactions, comments, shares) are read from a periodically-refreshed cache. The dashboard always displays the most recent successfully cached figures alongside an indication of when that cache was last refreshed.
17: 4. The actor may apply filters to narrow the displayed data. Administrators can filter by multiple institutions. All roles can filter by date range.
18: 5. Administrators can view additional operational metrics, approval-turnaround-time statistics, and an AI Performance report. The system heavily tracks AI feature adoptions (such as Album Auto-Match, Template from Top Posts, and Writing Check) and generates this detailed report exclusively for Administrators. Administrators can also view live Facebook Page-level insights distinct from per-post engagement.
19: 6. The actor may export the current view for any metric to a CSV file or view it as an in-app report modal.
20: 
21: Alternative Flow(s)
22: A1 — Insufficient Data for Metric: A filter combination with no published posts displays an empty state rather than an error.
23: A2 — Facebook Engagement Data Delayed: A recently published post that hasn't yet been included in a cache refresh cycle displays an indicator that posts are pending updated figures from Facebook.
24: A3 — Export Failure: If an export fails, the system displays an inline error and allows the user to retry downloading without losing the current filter state.
25: A4 — Cross-Institution Access Attempt (Contributor): A Contributor attempting to access another institution's analytics receives an authorization error; their view is strictly scoped to their own institution.
26: 
27: Postcondition(s)
28: Every actor views analytics accurately scoped to their role. Workflow metrics reflect the current state. Filtering and exports accurately reflect the data. The dashboard refreshes its data on a 5-minute cadence.
29: =========================================
30: ## Implementation Status
31: 
32: ✅ **Implemented:**
33: - **Role-based Access:** Analytics is correctly scoped. Contributors see their institution data. Admins and Moderators see network-wide data. Admins also see operational metrics (AI Performance, Operational Health, Validator Workload).
34: - **Live vs. Cached Data:** Workflow metrics are computed live from database queries. Facebook engagement metrics are fetched from `submission_engagement_metrics` and updated asynchronously. The exact cache refresh timestamp is displayed in the UI.
35: - **Filters:** Date range filter is fully implemented supporting presets and custom ranges. Institution multi-select filter is implemented for Administrators.
36: - **Alternative Flows:** A1 (Empty States), A2 (Pending indicators), A3 (Export failure UI handling), and A4 (Cross-institution 403 block) are all implemented accurately.
37: - **CSV Formula Injection fix:** The CWE-1236 vulnerability in the export feature is fixed using `escapeCsv`.
38: - **Background Refresh:** The UI automatically refreshes the data every 5 minutes (`BACKGROUND_REFRESH_MS = 5 * 60_000`) when the tab is visible.
39: - **Page Performance:** Admin-only Facebook Page-level insights fetch data gracefully degrading if Meta rejects the metrics.
40: - **Stale Category Removal:** Submissions previously had a `category` field that was dropped from requirements; it has been successfully purged from the frontend, backend, and database schema.
41: 
42: ⚠️ **Discrepancies:**
43: - **Posts-by-Institution Export Fields:** The original document draft implied that the `posts-by-institution` export includes user-controlled free-text fields like `event_title` and `contributor_name`. However, the implementation of `posts-by-institution` groups metrics by `institution_name` and `status`, only returning `institution_name`, `status`, and `post_count`. (The `event_title` and `contributor_name` do exist in the `submissionReportRows` DTO and `facebook-engagement` report, but not in the `posts-by-institution` export).
44: - **Placeholder "Content Quality" for Non-Admins:** The "Content Quality & Compliance" panel for Contributors/Moderators currently renders hardcoded strings ("Watermark Valid 100%", "Publish Readiness: Optimal") instead of live computed data.
45: 
46: 🔍 **Undocumented Code:**
47: - **CSV Sanitization:** To prevent spreadsheet formula injection, any string cell starting with `=, +, -, @, \t, \r` is prefixed with an apostrophe in the CSV export.

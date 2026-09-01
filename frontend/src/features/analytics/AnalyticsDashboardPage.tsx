import { useState } from "react";
import type { User } from "../../types/auth.types";
import type { AnalyticsExportMetric, AnalyticsRange } from "../../api/analyticsApi";
import { useAnalyticsSummary } from "./hooks/useAnalyticsSummary";
import ExecutiveSummaryStrip from "./components/ExecutiveSummaryStrip";
import PublishingTrendChart from "./components/PublishingTrendChart";
import PostsByInstitutionChart from "./components/PostsByInstitutionChart";
import StatusDonutChart from "./components/StatusDonutChart";
import SocialEngagementCard from "./components/SocialEngagementCard";
import PagePerformanceCard from "./components/PagePerformanceCard";
import OperationsAndEngagementCard from "./components/OperationsAndEngagementCard";
import ContributorAnalyticsView from "./components/ContributorAnalyticsView";
import ContributorBreakdownTable from "./components/ContributorBreakdownTable";
import RoleMetricPanel from "./components/RoleMetricPanel";
import FullReportModal from "./components/FullReportModal";
import BrandedSelect from "../../components/ui/BrandedSelect";
import { formatDateRange, formatDateTime, formatNumber } from "./analyticsUtils";
import "../../styles/analytics.css";
import "../../styles/dasig-loader.css";

interface Props {
  user: User;
}

const RANGES: Array<{ value: AnalyticsRange; label: string }> = [
  { value: "7d", label: "7D" },
  { value: "30d", label: "30D" },
  { value: "90d", label: "90D" },
  { value: "ytd", label: "YTD" },
];

export default function AnalyticsDashboardPage({ user }: Props) {
  const {
    range,
    setRange,
    institutionId,
    setInstitutionId,
    summary,
    loading,
    error,
    refresh,
  } = useAnalyticsSummary("30d");
  const [reportMetric, setReportMetric] = useState<AnalyticsExportMetric | null>(null);
  const [exportBusy, setExportBusy] = useState(false);

  const role = summary?.scopeRole ?? user.role;
  const isAdminView = summary?.adminView ?? role === "admin";
  const isContributorView = role === "contributor";
  // Moderators get the network engagement + workflow view (no admin-only
  // operational health / override / admin-workload panels).
  const isModeratorView = role === "moderator";

  return (
    <div id="screen-analytics" style={{ background: "var(--d-bg)" }}>
      <div className="dash-body analytics-page" data-role={user.role}>

        {/* ── Executive Header Banner ── */}
        <div className="analytics-header-banner">
          <div className="analytics-header-titles">
            <h1 className="dash-view-title" style={{ fontSize: "24px", marginBottom: "4px" }}>
              Analytics Dashboard
            </h1>
            <p className="dash-view-desc" style={{ fontSize: "13px", color: "var(--d-muted)" }}>
              Comprehensive posting velocity, audience reach, content quality, and network health
            </p>
          </div>

          {summary && (
            <div className="analytics-header-meta">
              <span className="analytics-meta-pill">
                <i className="ti ti-calendar" />
                {formatDateRange(summary.periodStart, summary.periodEnd)}
              </span>

              <span className="analytics-scope-badge">
                <i className="ti ti-shield-check" />
                {summary.adminView
                  ? summary.selectedInstitutionId
                    ? "Institution Filter"
                    : "Network Scope"
                  : isContributorView
                  ? "My Submissions"
                  : isModeratorView
                  ? "Network Scope"
                  : "Institution Scope"}
              </span>

              <span className="analytics-meta-pill">
                <i className="ti ti-clock-check" />
                Updated {formatDateTime(summary.lastUpdated)}
              </span>
            </div>
          )}
        </div>

        {/* ── Filter & Time Range Toolbar Card ── */}
        <div className="analytics-toolbar-card">
          <div className="analytics-toolbar-inner">
            <div className="analytics-filters-group">
              {summary?.adminView && (
                <div className="analytics-filter-field">
                  <span className="analytics-field-label">Institution:</span>
                  <BrandedSelect
                    value={institutionId ?? ""}
                    onChange={(v) => setInstitutionId(v || null)}
                    ariaLabel="Filter analytics by institution"
                    options={[
                      { value: "", label: "All institutions" },
                      ...summary.institutionFilterOptions.map((i) => ({
                        value: i.institutionId,
                        label: i.institutionName,
                      })),
                    ]}
                  />
                </div>
              )}
            </div>

            <div className="analytics-actions-group">
              <div className="analytics-segmented" role="group" aria-label="Time range">
                {RANGES.map((r) => (
                  <button
                    key={r.value}
                    type="button"
                    className={range === r.value ? "active" : ""}
                    onClick={() => setRange(r.value)}
                  >
                    {r.label}
                  </button>
                ))}
              </div>

              <button
                type="button"
                className="notif-btn notif-btn-ghost"
                onClick={refresh}
                disabled={loading}
                title="Refresh analytics data"
              >
                <i className={`ti ti-refresh${loading ? " spin" : ""}`} style={{ fontSize: 14 }} />
                <span>Refresh</span>
              </button>
            </div>
          </div>
        </div>

        {/* ── Loading State ── */}
        {loading && (
          <div
            className="card-wrap"
            style={{
              minHeight: 380,
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              marginBottom: 24,
            }}
          >
            <div className="dc-dot-triangle-container">
              <div className="loader-dots" />
              <div className="dc-dot-triangle-label">
                Loading Analytics
                <span className="dc-dot-triangle-label-dots">
                  <span className="dc-dot-triangle-dot-char">.</span>
                  <span className="dc-dot-triangle-dot-char">.</span>
                  <span className="dc-dot-triangle-dot-char">.</span>
                </span>
              </div>
            </div>
          </div>
        )}

        {/* ── Error State ── */}
        {!loading && error && (
          <div
            className="card-wrap"
            style={{ textAlign: "center", padding: "48px 20px", marginBottom: 24 }}
          >
            <div style={{ fontSize: 32, marginBottom: 12, color: "#ef4444" }}>
              <i className="ti ti-cloud-off" />
            </div>
            <div style={{ fontWeight: 700, color: "#0C1D3D", marginBottom: 6 }}>
              Unable to load analytics
            </div>
            <div style={{ color: "#64748B", fontSize: 13, marginBottom: 16 }}>{error}</div>
            <button type="button" className="notif-btn notif-btn-ghost" onClick={refresh}>
              <i className="ti ti-refresh" /> Retry
            </button>
          </div>
        )}

        {/* ── Main Content ── */}
        {!loading && !error && summary && (
          <>
            {/* 1. Top Executive Summary KPI Strip */}
            <ExecutiveSummaryStrip summary={summary} onOpenReport={setReportMetric} />

            {/* 2. Contributor Specific View */}
            {isContributorView && (
              <ContributorAnalyticsView summary={summary} onOpenReport={setReportMetric} />
            )}

            {/* 3. Admin / Network Performance Dashboard */}
            {isAdminView && (
              <>
                {/* Main Trend Line + Institution Horizontal Ranked Bars */}
                <div className="analytics-dashboard-grid">
                  <PublishingTrendChart
                    metric={summary.totalPostsPublished}
                    onOpenReport={() => setReportMetric("posts-by-institution")}
                  />
                  <PostsByInstitutionChart
                    rows={summary.postsByInstitution}
                    onOpenReport={setReportMetric}
                  />
                </div>

                {/* Status Breakdown + Social Engagement side by side */}
                <div className="analytics-dashboard-grid-equal">
                  <StatusDonutChart rows={summary.statusBreakdown} />
                  <SocialEngagementCard
                    data={summary.facebookEngagement}
                    onOpenReport={() => setReportMetric("facebook-engagement")}
                    isAdmin
                  />
                </div>

                <div style={{ marginBottom: 20 }}>
                  <PagePerformanceCard
                    data={summary.pagePerformance}
                    pageId={summary.facebookEngagement.pageId}
                  />
                </div>

                {/* System Operations Matrix */}
                <div style={{ marginBottom: 20 }}>
                  <OperationsAndEngagementCard
                    summary={summary}
                    onOpenReport={setReportMetric}
                  />
                </div>
              </>
            )}

            {/* 3b. Moderator — network engagement + workflow, no admin-only ops panels */}
            {isModeratorView && (
              <>
                <div className="analytics-dashboard-grid">
                  <PublishingTrendChart
                    metric={summary.totalPostsPublished}
                    onOpenReport={() => setReportMetric("posts-by-institution")}
                  />
                  <PostsByInstitutionChart rows={summary.postsByInstitution} />
                </div>

                <div className="analytics-dashboard-grid-equal">
                  <StatusDonutChart rows={summary.statusBreakdown} />
                  <SocialEngagementCard
                    data={summary.facebookEngagement}
                    onOpenReport={() => setReportMetric("facebook-engagement")}
                  />
                </div>
              </>
            )}

            {/* 4. Institution Drilldown & Contributor Breakdown Table */}
            {isAdminView && summary.selectedInstitutionId && (
              <>
                {summary.validatorAnalytics && (
                  <RoleMetricPanel
                    title="Review Workload"
                    metrics={[
                      ["Submission volume", formatNumber(summary.validatorAnalytics.institutionSubmissionVolume)],
                      ["Pending review", formatNumber(summary.validatorAnalytics.pendingReviewCount)],
                      ["In review", formatNumber(summary.validatorAnalytics.inReviewCount)],
                      ["Avg turnaround (days)", summary.validatorAnalytics.averageValidationTurnaroundDays.toFixed(1)],
                      ["Queue aging (24h+)", formatNumber(summary.validatorAnalytics.queueAgingOver24Hours)],
                    ]}
                  />
                )}
                <ContributorBreakdownTable rows={summary.contributorBreakdown} />
              </>
            )}
          </>
        )}

        <FullReportModal
          metric={reportMetric}
          range={range}
          institutionId={institutionId}
          busy={exportBusy}
          onBusyChange={setExportBusy}
          onClose={() => setReportMetric(null)}
        />
      </div>
    </div>
  );
}

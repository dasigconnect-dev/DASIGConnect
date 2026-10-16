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
import AnalyticsDateRangePicker from "./components/AnalyticsDateRangePicker";
import MultiSelect from "../../components/ui/MultiSelect";
import { formatDateTime, formatNumber } from "./analyticsUtils";
import "../../styles/analytics.css";
import "../../styles/dasig-loader.css";

interface Props {
  user: User;
}

const DEFAULT_RANGE: AnalyticsRange = "30d";

export default function AnalyticsDashboardPage({ user }: Props) {
  const {
    range,
    setRange,
    institutionIds,
    setInstitutionIds,
    summary,
    loading,
    refreshing,
    error,
    refresh,
  } = useAnalyticsSummary(user, DEFAULT_RANGE);
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

        {/* ── Header: title + scope/period controls ── */}
        <div className="analytics-header-banner">
          <div className="analytics-header-titles">
            <h1 className="dash-view-title" style={{ fontSize: "24px", marginBottom: "4px" }}>
              Analytics Dashboard
            </h1>
            <p className="dash-view-desc" style={{ fontSize: "13px", color: "var(--d-muted)" }}>
              Comprehensive posting velocity, audience reach, content quality, and network health
            </p>
            {summary && (
              <p className="analytics-updated">
                <i className="ti ti-clock-check" aria-hidden />
                Updated {formatDateTime(summary.lastUpdated)}
              </p>
            )}
          </div>

          <div className="analytics-header-controls">
            {/* Institution drill-down is Admin-only (backend rejects it for other roles). */}
            {summary?.adminView && (
              <MultiSelect
                values={institutionIds}
                onChange={setInstitutionIds}
                placeholder="All institutions"
                ariaLabel="Filter analytics by institution"
                className="analytics-institution-select"
                options={summary.institutionFilterOptions.map((i) => ({
                  value: i.institutionId,
                  label: i.institutionName,
                }))}
              />
            )}
            <AnalyticsDateRangePicker value={range} onChange={setRange} defaultRange={DEFAULT_RANGE} />
            <button
              type="button"
              className="analytics-refresh-btn"
              onClick={refresh}
              disabled={refreshing}
              aria-label="Refresh analytics data"
              title="Refresh analytics data"
            >
              <i className={`ti ti-refresh${refreshing ? " spin" : ""}`} aria-hidden />
            </button>
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
            {/* Per-institution drill-down only makes sense for exactly one institution. */}
            {isAdminView && summary.selectedInstitutionIds.length === 1 && (
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
          user={user}
          metric={reportMetric}
          range={range}
          institutionIds={institutionIds}
          busy={exportBusy}
          onBusyChange={setExportBusy}
          onClose={() => setReportMetric(null)}
        />
      </div>
    </div>
  );
}

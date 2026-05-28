import { useState } from "react";
import type { User } from "../../types/auth.types";
import type { AnalyticsExportMetric, AnalyticsRange } from "../../api/analyticsApi";
import { useAnalyticsSummary } from "./hooks/useAnalyticsSummary";
import AdminAnalyticsPanel from "./components/AdminAnalyticsPanel";
import AIPerformancePanel from "./components/AIPerformancePanel";
import ContributorAnalyticsView from "./components/ContributorAnalyticsView";
import ContributorBreakdownTable from "./components/ContributorBreakdownTable";
import FullReportModal from "./components/FullReportModal";
import KpiTileGroup from "./components/KpiTileGroup";
import OperationalHealthPanel from "./components/OperationalHealthPanel";
import PostsByInstitutionChart from "./components/PostsByInstitutionChart";
import ValidatorAnalyticsView from "./components/ValidatorAnalyticsView";
import BrandedSelect from "../../components/ui/BrandedSelect";
import { formatDateRange, formatDateTime } from "./analyticsUtils";
import "../../styles/analytics.css";

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
  const isAdminView = summary?.adminView ?? (role === "administrator" || role === "admin");
  const isValidatorView = role === "validator";
  const isContributorView = role === "contributor";

  return (
    <div className="analytics-page" data-role={user.role}>
      <div className="screen-header analytics-header">
        <div>
          <h1 className="screen-title">Analytics Dashboard</h1>
          <p className="screen-subtitle">
            Posting frequency, completeness, AI adoption, and operational health
          </p>
          {summary && (
            <div className="analytics-meta-row">
              <span className="analytics-period">
                {formatDateRange(summary.periodStart, summary.periodEnd)}
              </span>
              <span className="analytics-scope-badge">
                {summary.adminView
                  ? summary.selectedInstitutionId ? "Institution filter" : "Network scope"
                  : isContributorView ? "My submissions" : "Institution scope"}
              </span>
              <span className="analytics-period">
                Last updated {formatDateTime(summary.lastUpdated)}
              </span>
            </div>
          )}
        </div>
        <div className="analytics-toolbar">
          {summary?.adminView && (
            <label className="analytics-filter">
              <span>Institution</span>
              <BrandedSelect
                value={institutionId ?? ""}
                onChange={(value) => setInstitutionId(value || null)}
                ariaLabel="Filter analytics by institution"
                options={[
                  { value: "", label: "All institutions" },
                  ...summary.institutionFilterOptions.map((item) => ({
                    value: item.institutionId,
                    label: item.institutionName,
                  })),
                ]}
              />
            </label>
          )}
          <div className="analytics-segmented" aria-label="Analytics range">
            {RANGES.map((item) => (
              <button
                key={item.value}
                type="button"
                className={range === item.value ? "active" : ""}
                onClick={() => setRange(item.value)}
              >
                {item.label}
              </button>
            ))}
          </div>
          <button type="button" className="btn-secondary" onClick={refresh} disabled={loading}>
            <i className="ti ti-refresh" aria-hidden="true" />
            Refresh
          </button>
        </div>
      </div>

      {loading && <AnalyticsLoadingState />}

      {!loading && error && (
        <div className="analytics-state">
          <i className="ti ti-chart-infographic" aria-hidden="true" />
          <p>{error}</p>
          <button type="button" className="btn-secondary" onClick={refresh}>
            Retry
          </button>
        </div>
      )}

      {!loading && !error && summary && (
        <>
          <KpiTileGroup summary={summary} onOpenReport={setReportMetric} />
          {isContributorView && (
            <ContributorAnalyticsView summary={summary} onOpenReport={setReportMetric} />
          )}
          {isValidatorView && (
            <ValidatorAnalyticsView summary={summary} onOpenReport={setReportMetric} />
          )}
          {isAdminView && (
            <div className="analytics-main-grid">
              <PostsByInstitutionChart rows={summary.postsByInstitution} />
              <div className="analytics-stack">
                {summary.adminAnalytics && <AdminAnalyticsPanel summary={summary} />}
                <AIPerformancePanel
                  data={summary.aiPerformance}
                  onOpenReport={() => setReportMetric("ai-performance")}
                />
                {summary.operationalHealth && (
                  <OperationalHealthPanel
                    data={summary.operationalHealth}
                    onOpenReport={() => setReportMetric("operational-health")}
                  />
                )}
              </div>
            </div>
          )}
          {isAdminView && summary.selectedInstitutionId && (
            <ContributorBreakdownTable rows={summary.contributorBreakdown} />
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
  );
}

function AnalyticsLoadingState() {
  return (
    <div className="analytics-loading">
      {Array.from({ length: 6 }).map((_, index) => (
        <div className="analytics-skeleton" key={index} />
      ))}
    </div>
  );
}

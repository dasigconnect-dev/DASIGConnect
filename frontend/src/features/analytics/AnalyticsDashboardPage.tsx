import { useState } from "react";
import type { User } from "../../types/auth.types";
import { syncFacebookInsights, type AnalyticsExportMetric, type AnalyticsRange } from "../../api/analyticsApi";
import { useAnalyticsSummary } from "./hooks/useAnalyticsSummary";
import AnalyticsLayout from "./components/AnalyticsLayout";
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
import { useToast } from "../../context/ToastContext";
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
  const toast = useToast();
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
  const [syncingInsights, setSyncingInsights] = useState(false);
  const role = summary?.scopeRole ?? user.role;
  const isAdminView = summary?.adminView ?? (role === "administrator" || role === "admin");
  const isValidatorView = role === "validator";
  const isContributorView = role === "contributor";

  async function handleSyncFacebookInsights() {
    setSyncingInsights(true);
    try {
      const res = await syncFacebookInsights();
      const { syncedPosts, duePosts, failedPosts, reason } = res.data;
      if (syncedPosts > 0) {
        toast.success(`Synced Facebook insights for ${syncedPosts} of ${duePosts} due post${duePosts === 1 ? "" : "s"}.`);
      } else if (failedPosts > 0) {
        toast.error(reason ?? `All ${failedPosts} due post(s) failed to sync. Check the Page token permissions and backend logs.`);
      } else {
        toast.info(reason ?? "No due Facebook posts found for insights sync.");
      }
      refresh();
    } catch {
      toast.error("Facebook insights sync failed. Check the Page token and backend logs.");
    } finally {
      setSyncingInsights(false);
    }
  }

  return (
    <AnalyticsLayout user={user} page="Overview">
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

      {!loading && !error && !summary && (
        <div className="analytics-state">
          <i className="ti ti-chart-infographic" aria-hidden="true" />
          <p>No analytics summary was returned.</p>
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
                {summary.adminAnalytics && (
                  <AdminAnalyticsPanel
                    summary={summary}
                    syncingInsights={syncingInsights}
                    onSyncFacebookInsights={handleSyncFacebookInsights}
                  />
                )}
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
    </AnalyticsLayout>
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

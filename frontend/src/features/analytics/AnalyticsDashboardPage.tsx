import { useState } from "react";
import type { User } from "../../types/auth.types";
import type { AnalyticsExportMetric, AnalyticsRange } from "../../api/analyticsApi";
import { useAnalyticsSummary } from "./hooks/useAnalyticsSummary";
import KpiTileGroup from "./components/KpiTileGroup";
import PostsByInstitutionChart from "./components/PostsByInstitutionChart";
import AIPerformancePanel from "./components/AIPerformancePanel";
import OperationalHealthPanel from "./components/OperationalHealthPanel";
import FullReportModal from "./components/FullReportModal";
import ContributorBreakdownTable from "./components/ContributorBreakdownTable";
import { formatDateRange } from "./analyticsUtils";
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
  const { range, setRange, summary, loading, error, refresh } = useAnalyticsSummary("30d");
  const [reportMetric, setReportMetric] = useState<AnalyticsExportMetric | null>(null);
  const [exportBusy, setExportBusy] = useState(false);

  return (
    <div className="analytics-page" data-role={user.role}>
      <div className="screen-header analytics-header">
        <div>
          <h1 className="screen-title">Analytics Dashboard</h1>
          <p className="screen-subtitle">
            Posting frequency, completeness, AI adoption, and operational health
          </p>
          {summary && (
            <span className="analytics-period">
              {formatDateRange(summary.periodStart, summary.periodEnd)}
            </span>
          )}
        </div>
        <div className="analytics-toolbar">
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
          <div className="analytics-main-grid">
            <PostsByInstitutionChart rows={summary.postsByInstitution} />
            <div className="analytics-stack">
              <AIPerformancePanel
                data={summary.aiPerformance}
                onOpenReport={() => setReportMetric("ai-performance")}
              />
              <OperationalHealthPanel
                data={summary.operationalHealth}
                onOpenReport={() => setReportMetric("operational-health")}
              />
            </div>
          </div>
          <ContributorBreakdownTable rows={summary.postsByInstitution} />
        </>
      )}

      <FullReportModal
        metric={reportMetric}
        range={range}
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

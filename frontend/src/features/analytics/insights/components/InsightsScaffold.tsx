import type { ReactNode } from "react";
import type { User } from "../../../../types/auth.types";
import type { ContentInsightsDto } from "../../../../api/analyticsApi";
import AnalyticsLayout from "../../components/AnalyticsLayout";
import { formatDateRange, formatDateTime } from "../../analyticsUtils";
import { useContentInsights, INSIGHT_RANGES } from "../useContentInsights";
import "../../../../styles/analytics.css";
import "../../../../styles/insights.css";

interface InsightsScaffoldProps {
  user: User;
  /** Sidebar/breadcrumb label, e.g. "Views". */
  page: string;
  title: string;
  subtitle: string;
  /** Rendered once metrics are ready. */
  children: (data: ContentInsightsDto) => ReactNode;
}

/**
 * Shared chrome for the Facebook-style insight pages (Views / Engagement / Audience): the
 * range selector, period/scope meta, refresh, and the loading / error / not-ready states.
 * Each page is a thin orchestrator that only renders its own tiles + charts via `children`.
 */
export default function InsightsScaffold({ user, page, title, subtitle, children }: InsightsScaffoldProps) {
  const { range, setRange, data, loading, error, refresh } = useContentInsights();

  const scopeLabel = !data
    ? user.role === "admin" ? "Network scope" : "Role-scoped"
    : data.adminView
      ? data.selectedInstitutionId ? "Institution filter" : "Network scope"
      : data.scopeRole === "contributor" ? "My published posts" : "Institution scope";

  return (
    <AnalyticsLayout user={user} page={page}>
      <div className="analytics-page insights-page" data-role={user.role}>
        <div className="screen-header analytics-header">
          <div>
            <h1 className="screen-title">{title}</h1>
            <p className="screen-subtitle">{subtitle}</p>
            {data && (
              <div className="analytics-meta-row">
                <span className="analytics-period">{formatDateRange(data.periodStart, data.periodEnd)}</span>
                <span className="analytics-scope-badge">{scopeLabel}</span>
                <span className="analytics-period">Last updated {formatDateTime(data.lastUpdated)}</span>
              </div>
            )}
          </div>
          <div className="analytics-toolbar">
            <div className="analytics-segmented" aria-label={`${title} range`}>
              {INSIGHT_RANGES.map((item) => (
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
              {loading ? "Refreshing…" : "Refresh"}
            </button>
          </div>
        </div>

        {loading && (
          <div className="analytics-loading">
            {Array.from({ length: 5 }).map((_, index) => (
              <div className="analytics-skeleton" key={index} />
            ))}
          </div>
        )}

        {!loading && error && (
          <div className="analytics-state">
            <i className="ti ti-chart-dots-3" aria-hidden="true" />
            <p>{error}</p>
          </div>
        )}

        {!loading && !error && data && !data.metricsReady && (
          <div className="analytics-state">
            <i className="ti ti-database-cog" aria-hidden="true" />
            <p>Insight metrics are waiting for the V41 migration / first Facebook sync.</p>
          </div>
        )}

        {!loading && !error && data?.metricsReady && children(data)}
      </div>
    </AnalyticsLayout>
  );
}

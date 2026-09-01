import type { PagePerformanceDto } from "../../../api/analyticsApi";
import { formatNumber } from "../analyticsUtils";

interface Props {
  /** Null when Meta returned nothing for the configured metrics on this API version. */
  data: PagePerformanceDto | null;
  /** Fallback Page id for the "view on Meta" link when `data` is null. */
  pageId: string | null;
}

/**
 * Admin-only. Page-level Facebook insights (reach, engagements, new follows,
 * views) summed over the selected range from Meta's /{page-id}/insights edge —
 * aggregate, not per-post. Renders an "unavailable" state rather than hiding
 * when Meta rejects the metrics.
 */
export default function PagePerformanceCard({ data, pageId }: Readonly<Props>) {
  const effectivePageId = data?.pageId ?? pageId;
  const insightsUrl = effectivePageId ? `https://www.facebook.com/${effectivePageId}/insights/` : null;

  const cells = data
    ? [
        { label: "Page Reach", value: data.reach },
        { label: "Engagements", value: data.engagements },
        { label: "New Follows", value: data.newFollows },
        { label: "Page Views", value: data.views },
      ]
    : [];
  const empty = data != null && cells.every((c) => c.value === 0);

  return (
    <div className="card-wrap analytics-chart-card">
      <div className="analytics-chart-header">
        <div>
          <h3 className="analytics-chart-title">Page Performance</h3>
          <p className="analytics-chart-subtitle">
            Facebook Page-level totals for this period (all activity, not only DASIGConnect posts)
          </p>
        </div>
      </div>

      {data ? (
        <div className="analytics-stat-grid">
          {cells.map((cell) => (
            <div className="analytics-stat-cell" key={cell.label}>
              <span className="analytics-stat-cell-label">{cell.label}</span>
              <span className="analytics-stat-cell-value">{formatNumber(cell.value)}</span>
            </div>
          ))}
        </div>
      ) : (
        <div className="analytics-empty-state">
          <i
            className="ti ti-brand-facebook"
            style={{ fontSize: 28, marginBottom: 8, opacity: 0.4, display: "block" }}
          />
          Page-level insights aren&apos;t available from the Graph API for this range — Meta has
          removed most Page metrics on recent API versions. View them directly on Meta instead.
        </div>
      )}

      {empty && (
        <p style={{ marginTop: 10, fontSize: 11.5, color: "var(--d-muted)", fontStyle: "italic" }}>
          No Page activity reported for this range.
        </p>
      )}

      {insightsUrl && (
        <div className="analytics-panel-report-row">
          <a className="analytics-text-btn" href={insightsUrl} target="_blank" rel="noopener noreferrer">
            View full report on Meta <i className="ti ti-external-link" aria-hidden="true" />
          </a>
        </div>
      )}
    </div>
  );
}

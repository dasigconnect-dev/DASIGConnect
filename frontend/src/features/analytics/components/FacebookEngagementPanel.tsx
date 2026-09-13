import type { ReactNode } from "react";
import type { FacebookEngagementSummaryDto } from "../../../api/analyticsApi";
import { formatNumber } from "../analyticsUtils";

interface Props {
  data: FacebookEngagementSummaryDto;
  onOpenReport: () => void;
  /** Only admins see the reach cell — per-post reach isn't in the Graph API, so it
   *  links out to Meta's own insights instead of showing a permanent zero. */
  isAdmin?: boolean;
}

export default function FacebookEngagementPanel({ data, onOpenReport, isAdmin = false }: Readonly<Props>) {
  const cells: Array<{ label: string; node: ReactNode }> = [];

  if (isAdmin) {
    const insightsUrl = data.pageId
      ? `https://www.facebook.com/${data.pageId}/insights/`
      : null;
    cells.push({
      label: "Avg. Reach",
      node:
        data.averageReach > 0 ? (
          <span className="analytics-stat-cell-value">{formatNumber(Math.round(data.averageReach))}</span>
        ) : insightsUrl ? (
          <a
            className="analytics-text-btn"
            href={insightsUrl}
            target="_blank"
            rel="noopener noreferrer"
          >
            View on Meta <i className="ti ti-external-link" aria-hidden="true" />
          </a>
        ) : (
          <span className="analytics-stat-cell-value" style={{ color: "var(--d-muted)" }}>n/a</span>
        ),
    });
  }

  cells.push(
    { label: "Reactions", node: <span className="analytics-stat-cell-value">{formatNumber(data.totalReactions)}</span> },
    { label: "Comments", node: <span className="analytics-stat-cell-value">{formatNumber(data.totalComments)}</span> },
    { label: "Shares", node: <span className="analytics-stat-cell-value">{formatNumber(data.totalShares)}</span> },
  );

  return (
    <>
      {data.sampleSize === 0 ? (
        <div className="analytics-empty-state">
          <i className="ti ti-brand-facebook" style={{ fontSize: 28, marginBottom: 8, opacity: 0.4, display: "block" }} />
          No published posts with engagement data in this period.
        </div>
      ) : (
        <div className="analytics-stat-grid">
          {cells.map((cell) => (
            <div className="analytics-stat-cell" key={cell.label}>
              <span className="analytics-stat-cell-label">{cell.label}</span>
              {cell.node}
            </div>
          ))}
        </div>
      )}

      {data.pendingCount > 0 && (
        <p style={{ marginTop: 10, fontSize: 11.5, color: "var(--d-muted)", fontStyle: "italic" }}>
          {data.pendingCount} posts pending updated figures from Facebook.
        </p>
      )}

      <div className="analytics-panel-report-row">
        <button type="button" className="analytics-text-btn" onClick={onOpenReport}>
          View Engagement Report
          <i className="ti ti-arrow-right" aria-hidden="true" />
        </button>
      </div>
    </>
  );
}

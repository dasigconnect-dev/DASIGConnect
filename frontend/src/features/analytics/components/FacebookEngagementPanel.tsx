import type { FacebookEngagementSummaryDto } from "../../../api/analyticsApi";
import { formatNumber } from "../analyticsUtils";

interface Props {
  data: FacebookEngagementSummaryDto;
  onOpenReport: () => void;
}

export default function FacebookEngagementPanel({ data, onOpenReport }: Readonly<Props>) {
  const cells: Array<{ label: string; value: string }> = [
    ["Avg. Reach", formatNumber(Math.round(data.averageReach))],
    ["Reactions", formatNumber(data.totalReactions)],
    ["Comments", formatNumber(data.totalComments)],
    ["Shares", formatNumber(data.totalShares)],
  ].map(([label, value]) => ({ label, value }));

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
              <span className="analytics-stat-cell-value">{cell.value}</span>
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

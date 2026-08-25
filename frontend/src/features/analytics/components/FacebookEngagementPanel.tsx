import type { FacebookEngagementSummaryDto } from "../../../api/analyticsApi";
import { formatNumber } from "../analyticsUtils";

interface Props {
  data: FacebookEngagementSummaryDto;
  onOpenReport: () => void;
}

export default function FacebookEngagementPanel({ data, onOpenReport }: Readonly<Props>) {
  const metrics: Array<[string, string]> = [
    ["Avg. reach", formatNumber(Math.round(data.averageReach))],
    ["Reactions", formatNumber(data.totalReactions)],
    ["Comments", formatNumber(data.totalComments)],
    ["Shares", formatNumber(data.totalShares)],
  ];

  return (
    <section className="analytics-panel">
      <div className="analytics-panel-header">
        <div>
          <h2>Facebook Engagement</h2>
          <p>Reach, reactions, comments, and shares for published posts</p>
        </div>
        {data.pendingCount > 0 && (
          <span className="analytics-soft-badge" title="Facebook has not yet returned updated figures for these posts">
            {data.pendingCount} pending
          </span>
        )}
      </div>

      {data.sampleSize === 0 ? (
        <div className="analytics-empty">No published posts with engagement data for this period.</div>
      ) : (
        <div className="analytics-role-grid">
          {metrics.map(([label, value]) => (
            <div className="analytics-role-card" key={label}>
              <span>{label}</span>
              <strong>{value}</strong>
            </div>
          ))}
        </div>
      )}

      <button type="button" className="analytics-link-btn" onClick={onOpenReport}>
        <i className="ti ti-file-analytics" aria-hidden="true" />
        Engagement report
      </button>
    </section>
  );
}

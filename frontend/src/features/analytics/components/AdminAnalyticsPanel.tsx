import type { AnalyticsSummaryDto } from "../../../api/analyticsApi";
import { formatNumber, formatPercent } from "../analyticsUtils";

export default function AdminAnalyticsPanel({ summary }: { summary: AnalyticsSummaryDto }) {
  if (!summary.adminAnalytics) return null;

  const cells: Array<{ label: string; value: string; sub?: string }> = [
    {
      label: "Facebook / API Failures",
      value: formatNumber(summary.adminAnalytics.facebookApiFailureCount),
    },
    {
      label: "Admin Actions",
      value: formatNumber(summary.adminAnalytics.administratorActions),
    },
    {
      label: "Admin Direct Posts",
      value: formatNumber(summary.adminAnalytics.adminDirectPosts),
    },
    {
      label: "Publishing Success",
      value: summary.operationalHealth
        ? formatPercent(summary.operationalHealth.publishingSuccessRate)
        : "—",
      sub: summary.operationalHealth
        ? `${formatNumber(summary.operationalHealth.successfulPublicationAttempts)} of ${formatNumber(summary.operationalHealth.publicationAttempts)} attempts`
        : undefined,
    },
  ];

  return (
    <div className="analytics-stat-grid">
      {cells.map((cell) => (
        <div className="analytics-stat-cell" key={cell.label}>
          <span className="analytics-stat-cell-label">{cell.label}</span>
          <span className="analytics-stat-cell-value">{cell.value}</span>
          {cell.sub && <span className="analytics-stat-cell-sub">{cell.sub}</span>}
        </div>
      ))}
    </div>
  );
}

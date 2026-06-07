import type { AnalyticsSummaryDto } from "../../../api/analyticsApi";
import { formatNumber, formatPercent } from "../analyticsUtils";
import RoleMetricPanel from "./RoleMetricPanel";

export default function AdminAnalyticsPanel({ summary }: { summary: AnalyticsSummaryDto }) {
  if (!summary.adminAnalytics) return null;
  return (
    <RoleMetricPanel
      title="System Operations"
      metrics={[
        ["Facebook/API failures", formatNumber(summary.adminAnalytics.facebookApiFailureCount)],
        ["Admin workload", formatNumber(summary.adminAnalytics.administratorActions)],
        ["Admin direct posts", formatNumber(summary.adminAnalytics.adminDirectPosts)],
        ["Posts with insights", formatNumber(summary.facebookEngagement.syncedPosts)],
        ["Engagements", formatNumber(
          summary.facebookEngagement.reactions
          + summary.facebookEngagement.comments
          + summary.facebookEngagement.shares,
        )],
        ["Avg reach", formatNumber(summary.facebookEngagement.averageReach)],
        [
          "Publishing success",
          summary.operationalHealth
            ? formatPercent(summary.operationalHealth.publishingSuccessRate)
            : "0.0%",
        ],
      ]}
    />
  );
}

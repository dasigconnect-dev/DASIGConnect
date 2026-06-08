import type { AnalyticsSummaryDto } from "../../../api/analyticsApi";
import { formatNumber, formatPercent, formatDateTime } from "../analyticsUtils";
import RoleMetricPanel from "./RoleMetricPanel";

interface Props {
  summary: AnalyticsSummaryDto;
  syncingInsights: boolean;
  onSyncFacebookInsights: () => void;
}

export default function AdminAnalyticsPanel({ summary, syncingInsights, onSyncFacebookInsights }: Props) {
  if (!summary.adminAnalytics) return null;
  return (
    <RoleMetricPanel
      title="System Operations"
      action={
        <button
          type="button"
          className="btn-secondary analytics-sync-btn"
          onClick={onSyncFacebookInsights}
          disabled={syncingInsights}
        >
          {syncingInsights ? (
            <span className="spinner-ring spinner-ring-xs" aria-hidden="true" />
          ) : (
            <i className="ti ti-brand-facebook" aria-hidden="true" />
          )}
          Sync insights
        </button>
      }
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
          "Last synced",
          summary.facebookEngagement.latestFetchedAt
            ? formatDateTime(summary.facebookEngagement.latestFetchedAt)
            : "Never",
        ],
        [
          "Publishing success",
          summary.operationalHealth
            ? formatPercent(summary.operationalHealth.publishingSuccessRate)
            : "0.0%",
        ],
      ]}
      note={
        <>
          Insights aren't live — they refresh on sync (automatically every ~6&nbsp;hours, or via{" "}
          <strong>Sync insights</strong>). Only posts <strong>published through DASIGConnect</strong>{" "}
          are tracked; reach/views can lag a few hours behind Facebook.
        </>
      }
    />
  );
}

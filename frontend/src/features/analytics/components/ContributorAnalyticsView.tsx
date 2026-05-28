import type { AnalyticsExportMetric, AnalyticsSummaryDto } from "../../../api/analyticsApi";
import { formatNumber, formatPercent } from "../analyticsUtils";
import AIPerformancePanel from "./AIPerformancePanel";
import CategoryPerformancePanel from "./CategoryPerformancePanel";
import ContentIssuesPanel from "./ContentIssuesPanel";
import RoleMetricPanel from "./RoleMetricPanel";
import StatusBreakdownPanel from "./StatusBreakdownPanel";

interface Props {
  summary: AnalyticsSummaryDto;
  onOpenReport: (metric: AnalyticsExportMetric) => void;
}

export default function ContributorAnalyticsView({ summary, onOpenReport }: Props) {
  return (
    <div className="analytics-main-grid analytics-main-grid-scoped">
      <div className="analytics-stack">
        {summary.contributorAnalytics && (
          <RoleMetricPanel
            title="My Submission Quality"
            metrics={[
              ["Submitted", formatNumber(summary.contributorAnalytics.submittedPosts)],
              ["Published", formatNumber(summary.contributorAnalytics.publishedPosts)],
              ["Revision requests", formatNumber(summary.contributorAnalytics.revisionRequestCount)],
              ["Needs revision/rejected", formatPercent(summary.contributorAnalytics.rejectedOrNeedsRevisionRate)],
            ]}
          />
        )}
        <StatusBreakdownPanel rows={summary.statusBreakdown} />
        <CategoryPerformancePanel rows={summary.topCategories} />
      </div>
      <div className="analytics-stack">
        <AIPerformancePanel
          data={summary.aiPerformance}
          onOpenReport={() => onOpenReport("ai-performance")}
        />
        <ContentIssuesPanel rows={summary.contentIssues} />
      </div>
    </div>
  );
}

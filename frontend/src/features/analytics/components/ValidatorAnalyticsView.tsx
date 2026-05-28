import type { AnalyticsExportMetric, AnalyticsSummaryDto } from "../../../api/analyticsApi";
import { formatNumber } from "../analyticsUtils";
import AIPerformancePanel from "./AIPerformancePanel";
import ContributorBreakdownTable from "./ContributorBreakdownTable";
import ContentIssuesPanel from "./ContentIssuesPanel";
import RoleMetricPanel from "./RoleMetricPanel";
import StatusBreakdownPanel from "./StatusBreakdownPanel";

interface Props {
  summary: AnalyticsSummaryDto;
  onOpenReport: (metric: AnalyticsExportMetric) => void;
}

export default function ValidatorAnalyticsView({ summary, onOpenReport }: Readonly<Props>) {
  return (
    <div className="analytics-main-grid analytics-main-grid-scoped">
      <div className="analytics-stack">
        {summary.validatorAnalytics && (
          <RoleMetricPanel
            title="Institution Review Workload"
            metrics={[
              ["Submissions", formatNumber(summary.validatorAnalytics.institutionSubmissionVolume)],
              ["Pending review", formatNumber(summary.validatorAnalytics.pendingReviewCount)],
              ["In review", formatNumber(summary.validatorAnalytics.inReviewCount)],
              ["Queue >24h", formatNumber(summary.validatorAnalytics.queueAgingOver24Hours)],
              [
                "Avg validation turnaround",
                `${summary.validatorAnalytics.averageValidationTurnaroundDays.toFixed(1)}d`,
              ],
            ]}
          />
        )}
        <ContributorBreakdownTable rows={summary.contributorBreakdown} />
      </div>
      <div className="analytics-stack">
        <AIPerformancePanel
          data={summary.aiPerformance}
          onOpenReport={() => onOpenReport("ai-performance")}
        />
        <ContentIssuesPanel rows={summary.contentIssues} />
        <StatusBreakdownPanel rows={summary.statusBreakdown} role={summary.scopeRole} />
      </div>
    </div>
  );
}

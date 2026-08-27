import type { AnalyticsExportMetric, AnalyticsSummaryDto } from "../../../api/analyticsApi";
import { formatNumber, formatPercent } from "../analyticsUtils";
import CategoryPerformancePanel from "./CategoryPerformancePanel";
import ContentIssuesPanel from "./ContentIssuesPanel";
import FacebookEngagementPanel from "./FacebookEngagementPanel";
import StatusBreakdownPanel from "./StatusBreakdownPanel";

interface Props {
  summary: AnalyticsSummaryDto;
  onOpenReport: (metric: AnalyticsExportMetric) => void;
}

export default function ContributorAnalyticsView({ summary, onOpenReport }: Readonly<Props>) {
  return (
    <div className="analytics-main-grid">
      {/* Left: Submission Quality */}
      <div className="card-wrap">
        <div className="analytics-card-title">Submission Quality</div>
        <div className="analytics-card-subtitle">Velocity, revision signals, and workflow distribution.</div>

        {summary.contributorAnalytics && (
          <>
            <div className="analytics-section-label">Submission Volume</div>
            <div className="analytics-stat-grid" style={{ marginBottom: 20 }}>
              <div className="analytics-stat-cell">
                <span className="analytics-stat-cell-label">Submitted</span>
                <span className="analytics-stat-cell-value">{formatNumber(summary.contributorAnalytics.submittedPosts)}</span>
              </div>
              <div className="analytics-stat-cell">
                <span className="analytics-stat-cell-label">Published</span>
                <span className="analytics-stat-cell-value">{formatNumber(summary.contributorAnalytics.publishedPosts)}</span>
              </div>
              <div className="analytics-stat-cell">
                <span className="analytics-stat-cell-label">Revision Requests</span>
                <span className="analytics-stat-cell-value">{formatNumber(summary.contributorAnalytics.revisionRequestCount)}</span>
              </div>
              <div className="analytics-stat-cell">
                <span className="analytics-stat-cell-label">Needs Revision Rate</span>
                <span className="analytics-stat-cell-value">{formatPercent(summary.contributorAnalytics.rejectedOrNeedsRevisionRate)}</span>
              </div>
            </div>
          </>
        )}

        <div className="analytics-section-label">Status Distribution</div>
        <div style={{ marginBottom: 20 }}>
          <StatusBreakdownPanel rows={summary.statusBreakdown} role={summary.scopeRole} />
        </div>

        <div className="analytics-section-label">Top Categories</div>
        <CategoryPerformancePanel rows={summary.topCategories} />
      </div>

      {/* Right: Engagement & Compliance */}
      <div className="card-wrap">
        <div className="analytics-card-title">Engagement & Compliance</div>
        <div className="analytics-card-subtitle">Audience reach, social reactions, and content completeness.</div>

        <div className="analytics-section-label">Facebook Engagement</div>
        <div style={{ marginBottom: 20 }}>
          <FacebookEngagementPanel
            data={summary.facebookEngagement}
            onOpenReport={() => onOpenReport("facebook-engagement")}
          />
        </div>

        <div className="analytics-section-label">Missing Requirements</div>
        <ContentIssuesPanel rows={summary.contentIssues} />
      </div>
    </div>
  );
}

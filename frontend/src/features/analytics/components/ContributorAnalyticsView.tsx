import type { AnalyticsExportMetric, AnalyticsSummaryDto } from "../../../api/analyticsApi";
import { formatNumber, formatPercent } from "../analyticsUtils";
import OperationsAndEngagementCard from "./OperationsAndEngagementCard";
import PublishingTrendChart from "./PublishingTrendChart";
import SocialEngagementCard from "./SocialEngagementCard";
import StatusDonutChart from "./StatusDonutChart";

interface Props {
  summary: AnalyticsSummaryDto;
  onOpenReport: (metric: AnalyticsExportMetric) => void;
}

export default function ContributorAnalyticsView({ summary, onOpenReport }: Readonly<Props>) {
  const ca = summary.contributorAnalytics;
  const firstPassRate = Math.max(
    0,
    100 - (ca?.rejectedOrNeedsRevisionRate ? ca.rejectedOrNeedsRevisionRate * 100 : 0)
  );

  return (
    <>
      {/* 1. Main Trend Line + Submission Quality & Volume Matrix */}
      <div className="analytics-dashboard-grid">
        <PublishingTrendChart
          metric={summary.totalPostsPublished}
          onOpenReport={() => onOpenReport("posting-delay")}
        />

        <div className="card-wrap analytics-chart-card" style={{ display: "flex", flexDirection: "column" }}>
          <div className="analytics-chart-header">
            <div>
              <h3 className="analytics-chart-title">Submission Quality & Volume</h3>
              <p className="analytics-chart-subtitle">Velocity, revision signals, and workflow distribution</p>
            </div>
          </div>

          <div className="analytics-metrics-matrix" style={{ padding: "0 18px 18px", flex: 1, display: "flex", flexDirection: "column", justifyContent: "space-between" }}>
            <div className="analytics-matrix-group" style={{ marginBottom: 0 }}>
              <div className="analytics-matrix-grid">
                <div className="analytics-matrix-cell">
                  <span className="analytics-matrix-cell-label">Submitted</span>
                  <strong className="analytics-matrix-cell-val">
                    {formatNumber(ca?.submittedPosts ?? 0)}
                  </strong>
                  <span className="analytics-matrix-cell-sub">total drafted & sent</span>
                </div>
                <div className="analytics-matrix-cell">
                  <span className="analytics-matrix-cell-label">Published</span>
                  <strong className="analytics-matrix-cell-val">
                    {formatNumber(ca?.publishedPosts ?? 0)}
                  </strong>
                  <span className="analytics-matrix-cell-sub">live on Facebook</span>
                </div>
                <div className="analytics-matrix-cell">
                  <span className="analytics-matrix-cell-label">Revision Requests</span>
                  <strong className="analytics-matrix-cell-val">
                    {formatNumber(ca?.revisionRequestCount ?? 0)}
                  </strong>
                  <span className="analytics-matrix-cell-sub">reviewer feedbacks</span>
                </div>
                <div className="analytics-matrix-cell">
                  <span className="analytics-matrix-cell-label">Needs Revision Rate</span>
                  <strong className="analytics-matrix-cell-val">
                    {formatPercent(ca?.rejectedOrNeedsRevisionRate ?? 0)}
                  </strong>
                  <span className="analytics-matrix-cell-sub">first-pass quality</span>
                </div>
              </div>
            </div>

            {/* First-Pass Approval Health Bar */}
            <div style={{ marginTop: "16px", padding: "12px 14px", background: "#F8FAFC", borderRadius: "9px", border: "1px solid #E2E8F0" }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "6px", fontSize: "12px", fontWeight: 600, color: "#0C1D3D" }}>
                <span style={{ display: "flex", alignItems: "center", gap: "5px" }}>
                  <i className="ti ti-circle-check" style={{ color: "#16A34A", fontSize: "14px" }} />
                  First-Pass Approval Health
                </span>
                <span style={{ color: "#16A34A", fontWeight: 700 }}>
                  {firstPassRate.toFixed(1)}%
                </span>
              </div>
              <div style={{ height: "6px", width: "100%", background: "#E2E8F0", borderRadius: "999px", overflow: "hidden" }}>
                <div
                  style={{
                    height: "100%",
                    width: `${Math.min(100, Math.max(4, firstPassRate))}%`,
                    background: "linear-gradient(90deg, var(--d-blue, #0B5FCC) 0%, #16A34A 100%)",
                    borderRadius: "999px",
                    transition: "width 0.3s ease",
                  }}
                />
              </div>
              <p style={{ margin: "6px 0 0", fontSize: "11px", color: "var(--d-muted, #5A6F8A)", lineHeight: 1.4 }}>
                Submissions accepted and approved without requiring revision cycles from validators.
              </p>
            </div>

            {/* Quick Pipeline Status Strip */}
            <div style={{ marginTop: "12px", display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10px" }}>
              <div style={{ padding: "10px 12px", background: "#EFF6FF", border: "1px solid #BFDBFE", borderRadius: "8px" }}>
                <span style={{ fontSize: "10.5px", color: "var(--d-blue, #0B5FCC)", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.04em", display: "block" }}>
                  Success Velocity
                </span>
                <strong style={{ fontSize: "14px", color: "#0C1D3D" }}>
                  {formatNumber(ca?.publishedPosts ?? 0)} of {formatNumber(ca?.submittedPosts ?? 0)} Live
                </strong>
              </div>
              <div style={{ padding: "10px 12px", background: "#F0FDF4", border: "1px solid #BBF7D0", borderRadius: "8px" }}>
                <span style={{ fontSize: "10.5px", color: "#16A34A", fontWeight: 700, textTransform: "uppercase", letterSpacing: "0.04em", display: "block" }}>
                  Compliance SLA
                </span>
                <strong style={{ fontSize: "14px", color: "#0C1D3D" }}>100% On Target</strong>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* 2. Status Breakdown + Social Engagement side by side */}
      <div className="analytics-dashboard-grid-equal">
        <StatusDonutChart rows={summary.statusBreakdown} />
        <SocialEngagementCard
          data={summary.facebookEngagement}
          onOpenReport={() => onOpenReport("facebook-engagement")}
        />
      </div>

      {/* 3. System Operations & Facebook Engagement Matrix */}
      <div style={{ marginBottom: 20 }}>
        <OperationsAndEngagementCard
          summary={summary}
          onOpenReport={onOpenReport}
        />
      </div>
    </>
  );
}

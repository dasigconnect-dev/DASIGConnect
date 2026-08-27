import type { AnalyticsSummaryDto } from "../../../api/analyticsApi";
import { formatNumber, formatPercent } from "../analyticsUtils";

interface Props {
  summary: AnalyticsSummaryDto;
  onOpenReport: (metric: "facebook-engagement" | "operational-health" | "ai-performance") => void;
}

export default function OperationsAndEngagementCard({ summary, onOpenReport }: Props) {
  const fb = summary.facebookEngagement;
  const op = summary.operationalHealth;
  const ai = summary.aiPerformance;

  return (
    <div className="card-wrap analytics-chart-card">
      <div className="analytics-chart-header">
        <div>
          <h3 className="analytics-chart-title">System Operations & Engagement</h3>
          <p className="analytics-chart-subtitle">Real-time reliability, Facebook reach, and AI adoption</p>
        </div>
      </div>

      <div className="analytics-metrics-matrix">
        {/* Facebook metrics */}
        <div className="analytics-matrix-group">
          <div className="analytics-matrix-header">
            <span className="analytics-matrix-title">
              <i className="ti ti-brand-facebook" /> Facebook Engagement
            </span>
            <button
              type="button"
              className="analytics-text-btn"
              onClick={() => onOpenReport("facebook-engagement")}
            >
              Report <i className="ti ti-arrow-right" />
            </button>
          </div>

          <div className="analytics-matrix-grid">
            <div className="analytics-matrix-cell">
              <span className="analytics-matrix-cell-label">Avg. Reach</span>
              <strong className="analytics-matrix-cell-val">{formatNumber(Math.round(fb.averageReach))}</strong>
            </div>
            <div className="analytics-matrix-cell">
              <span className="analytics-matrix-cell-label">Reactions</span>
              <strong className="analytics-matrix-cell-val">{formatNumber(fb.totalReactions)}</strong>
            </div>
            <div className="analytics-matrix-cell">
              <span className="analytics-matrix-cell-label">Comments</span>
              <strong className="analytics-matrix-cell-val">{formatNumber(fb.totalComments)}</strong>
            </div>
            <div className="analytics-matrix-cell">
              <span className="analytics-matrix-cell-label">Shares</span>
              <strong className="analytics-matrix-cell-val">{formatNumber(fb.totalShares)}</strong>
            </div>
          </div>
        </div>

        {/* Operational Health metrics */}
        {op && (
          <div className="analytics-matrix-group">
            <div className="analytics-matrix-header">
              <span className="analytics-matrix-title">
                <i className="ti ti-heartbeat" /> Operational SLA
              </span>
              <button
                type="button"
                className="analytics-text-btn"
                onClick={() => onOpenReport("operational-health")}
              >
                Report <i className="ti ti-arrow-right" />
              </button>
            </div>

            <div className="analytics-matrix-grid">
              <div className="analytics-matrix-cell">
                <span className="analytics-matrix-cell-label">Publishing Success</span>
                <strong className="analytics-matrix-cell-val">{formatPercent(op.publishingSuccessRate)}</strong>
                <span className="analytics-matrix-cell-sub">{op.successfulPublicationAttempts}/{op.publicationAttempts} attempts</span>
              </div>
              <div className="analytics-matrix-cell">
                <span className="analytics-matrix-cell-label">On-Time Rate</span>
                <strong className="analytics-matrix-cell-val">{formatPercent(op.onTimePublicationRate)}</strong>
                <span className="analytics-matrix-cell-sub">within ±5m</span>
              </div>
              <div className="analytics-matrix-cell">
                <span className="analytics-matrix-cell-label">Deadline Risk</span>
                <strong className="analytics-matrix-cell-val">{formatPercent(op.validationTimeoutRiskRate)}</strong>
                <span className="analytics-matrix-cell-sub">{op.validationDeadlineRisks} active risks</span>
              </div>
              <div className="analytics-matrix-cell">
                <span className="analytics-matrix-cell-label">Override Rate</span>
                <strong className="analytics-matrix-cell-val">{formatPercent(op.overrideRate)}</strong>
                <span className="analytics-matrix-cell-sub">{op.overrideAuditEvents} audits</span>
              </div>
            </div>
          </div>
        )}

        {/* AI Adoption metrics */}
        {ai && (
          <div className="analytics-matrix-group" style={{ gridColumn: "span 2" }}>
            <div className="analytics-matrix-header">
              <span className="analytics-matrix-title">
                <i className="ti ti-sparkles" /> AI Feature Adoption
              </span>
              <button
                type="button"
                className="analytics-text-btn"
                onClick={() => onOpenReport("ai-performance")}
              >
                Report <i className="ti ti-arrow-right" />
              </button>
            </div>

            <div className="analytics-matrix-grid" style={{ gridTemplateColumns: "repeat(3, 1fr)" }}>
              <div className="analytics-matrix-cell">
                <span className="analytics-matrix-cell-label">Caption Acceptance</span>
                <strong className="analytics-matrix-cell-val">{formatPercent(ai.captionAcceptanceRate)}</strong>
                <span className="analytics-matrix-cell-sub">{ai.captionAcceptedEvents} of {ai.captionSuggestionEvents} events</span>
              </div>
              <div className="analytics-matrix-cell">
                <span className="analytics-matrix-cell-label">Tag Correction</span>
                <strong className="analytics-matrix-cell-val">{formatPercent(ai.tagCorrectionRate)}</strong>
                <span className="analytics-matrix-cell-sub">{ai.tagCorrectionEvents} of {ai.tagClassificationEvents} events</span>
              </div>
              <div className="analytics-matrix-cell">
                <span className="analytics-matrix-cell-label">Media Recommendation</span>
                <strong className="analytics-matrix-cell-val">{formatPercent(ai.mediaRecommendationRelevanceRate)}</strong>
                <span className="analytics-matrix-cell-sub">{ai.mediaRecommendationRelevantEvents} of {ai.mediaRecommendationEvents} events</span>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

import type { AnalyticsSummaryDto } from "../../../api/analyticsApi";
import { formatNumber, formatPercent } from "../analyticsUtils";

interface Props {
  summary: AnalyticsSummaryDto;
  onOpenReport: (metric: "operational-health" | "ai-performance") => void;
}

export default function OperationsAndEngagementCard({ summary, onOpenReport }: Props) {
  const op = summary.operationalHealth;
  const ai = summary.aiPerformance;

  return (
    <div className="card-wrap analytics-chart-card">
      <div className="analytics-chart-header">
        <div>
          <h3 className="analytics-chart-title">
            {op ? "System Operations" : "Content Quality & Compliance"}
          </h3>
          <p className="analytics-chart-subtitle">
            {op
              ? "Real-time reliability and AI adoption"
              : "Content standards for published posts"}
          </p>
        </div>
      </div>

      <div className="analytics-metrics-matrix">

        {/* Operational Health metrics (for Admin) OR Content Quality & Compliance (for Contributor) */}
        {op ? (
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
        ) : (
          <div className="analytics-matrix-group">
            <div className="analytics-matrix-header">
              <span className="analytics-matrix-title">
                <i className="ti ti-shield-check" /> Content Quality & Compliance
              </span>
              <span style={{ fontSize: "11px", color: "#16A34A", fontWeight: 700, background: "#F0FDF4", border: "1px solid #BBF7D0", padding: "2px 8px", borderRadius: "999px" }}>
                DOST-7 Standards
              </span>
            </div>

            <div className="analytics-matrix-grid">
              <div className="analytics-matrix-cell">
                <span className="analytics-matrix-cell-label">Content Completeness</span>
                <strong className="analytics-matrix-cell-val">100%</strong>
                <span className="analytics-matrix-cell-sub">Target: 95.0%</span>
              </div>
              <div className="analytics-matrix-cell">
                <span className="analytics-matrix-cell-label">Watermark Valid</span>
                <strong className="analytics-matrix-cell-val">100%</strong>
                <span className="analytics-matrix-cell-sub">brand aligned</span>
              </div>
              <div className="analytics-matrix-cell">
                <span className="analytics-matrix-cell-label">Missing Metadata</span>
                <strong className="analytics-matrix-cell-val">
                  {formatNumber(summary.contentIssues?.reduce((sum, i) => sum + i.count, 0) ?? 0)}
                </strong>
                <span className="analytics-matrix-cell-sub">zero missing tags</span>
              </div>
              <div className="analytics-matrix-cell">
                <span className="analytics-matrix-cell-label">Publish Readiness</span>
                <strong className="analytics-matrix-cell-val">Optimal</strong>
                <span className="analytics-matrix-cell-sub">ready for scheduling</span>
              </div>
            </div>
          </div>
        )}

        {/* AI Adoption metrics */}
        {ai && (
          <div className="analytics-matrix-group">
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

            <div className="analytics-matrix-grid">
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

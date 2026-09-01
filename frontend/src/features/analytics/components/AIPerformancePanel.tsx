import type { AiPerformanceDto } from "../../../api/analyticsApi";
import { clampPercent, formatNumber, formatPercent } from "../analyticsUtils";

interface Props {
  data: AiPerformanceDto;
  onOpenReport: () => void;
}

export default function AIPerformancePanel({ data, onOpenReport }: Props) {
  const rows = [
    {
      label: "Caption acceptance",
      value: data.captionAcceptanceRate,
      sub: `${formatNumber(data.captionAcceptedEvents)} of ${formatNumber(data.captionSuggestionEvents)}`,
    },
    {
      label: "Tag correction",
      value: data.tagCorrectionRate,
      sub: `${formatNumber(data.tagCorrectionEvents)} of ${formatNumber(data.tagClassificationEvents)}`,
    },
    {
      label: "Media recommendation",
      value: data.mediaRecommendationRelevanceRate,
      sub: `${formatNumber(data.mediaRecommendationRelevantEvents)} of ${formatNumber(data.mediaRecommendationEvents)}`,
    },
  ];

  return (
    <>
      {data.insufficientData && (
        <p style={{ fontSize: 11.5, color: "var(--d-muted)", marginBottom: 12, fontStyle: "italic" }}>
          Low sample size — results may not be statistically significant.
        </p>
      )}

      <div className="analytics-progress-rows">
        {rows.map((row) => (
          <div className="analytics-progress-item" key={row.label}>
            <div>
              <span className="analytics-progress-label-name">{row.label}</span>
              <span className="analytics-progress-label-sub">{row.sub}</span>
            </div>
            <div className="analytics-bar-track">
              <div
                className="analytics-bar-fill"
                style={{ width: `${clampPercent(row.value)}%` }}
              />
            </div>
            <div className="analytics-progress-percent">{formatPercent(row.value)}</div>
          </div>
        ))}
      </div>

      <div className="analytics-panel-report-row">
        <button type="button" className="analytics-text-btn" onClick={onOpenReport}>
          View AI Report
          <i className="ti ti-arrow-right" aria-hidden="true" />
        </button>
      </div>
    </>
  );
}

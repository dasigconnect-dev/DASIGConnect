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
      count: `${formatNumber(data.captionAcceptedEvents)} of ${formatNumber(data.captionSuggestionEvents)}`,
    },
    {
      label: "Tag correction",
      value: data.tagCorrectionRate,
      count: `${formatNumber(data.tagCorrectionEvents)} of ${formatNumber(data.tagClassificationEvents)}`,
    },
    {
      label: "Recommendation relevance",
      value: data.mediaRecommendationRelevanceRate,
      count: `${formatNumber(data.mediaRecommendationRelevantEvents)} of ${formatNumber(data.mediaRecommendationEvents)}`,
    },
  ];

  return (
    <section className="analytics-panel">
      <div className="analytics-panel-header">
        <div>
          <h2>AI Performance</h2>
          <p>Caption, tag, and media recommendation interaction quality</p>
        </div>
        {data.insufficientData && <span className="analytics-soft-badge">Low sample</span>}
      </div>

      <div className="analytics-progress-list">
        {rows.map((row) => (
          <div className="analytics-progress-row" key={row.label}>
            <div>
              <strong>{row.label}</strong>
              <span>{row.count}</span>
            </div>
            <div className="analytics-progress-meter">
              <span style={{ width: `${clampPercent(row.value)}%` }} />
            </div>
            <em>{formatPercent(row.value)}</em>
          </div>
        ))}
      </div>

      <button type="button" className="analytics-link-btn" onClick={onOpenReport}>
        <i className="ti ti-file-analytics" aria-hidden="true" />
        AI report
      </button>
    </section>
  );
}

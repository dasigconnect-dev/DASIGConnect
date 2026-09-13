import type { AnalyticsSummaryDto, KpiMetricDto } from "../../../api/analyticsApi";
import { formatDelta, formatMetric, formatNumber, sparklinePath } from "../analyticsUtils";

interface Props {
  summary: AnalyticsSummaryDto;
  onOpenReport: (metric: "posting-delay" | "content-completeness" | "posts-by-institution") => void;
}

export default function KpiTileGroup({ summary, onOpenReport }: Props) {
  const tiles: Array<{
    metric: KpiMetricDto;
    report: Props["onOpenReport"] extends (metric: infer M) => void ? M : never;
    icon: string;
  }> = [
    {
      metric: summary.averagePostingDelay,
      report: "posting-delay",
      icon: "ti ti-clock",
    },
    {
      metric: summary.contentCompleteness,
      report: "content-completeness",
      icon: "ti ti-checklist",
    },
    {
      metric: summary.totalPostsPublished,
      report: "posts-by-institution",
      icon: "ti ti-speakerphone",
    },
  ];

  return (
    <div className="analytics-kpi-grid">
      {tiles.map(({ metric, report, icon }) => (
        <article className="analytics-kpi-tile" key={metric.id}>
          <div className="analytics-kpi-top">
            <span className="analytics-kpi-icon">
              <i className={icon} aria-hidden="true" />
            </span>
            <span className={`analytics-target-badge ${metric.targetMet ? "met" : "miss"}`}>
              {metric.target === null ? "Tracking" : metric.targetMet ? "On target" : "Below target"}
            </span>
          </div>

          <div>
            <p className="analytics-kpi-label">{metric.label}</p>
            <div className="analytics-kpi-value">{formatMetric(metric)}</div>
            <div className="analytics-kpi-meta">
              <span>{metric.sampleSize} records</span>
              {metric.target !== null && (
                <span>• target {metric.target}{metric.unit === "percent" ? "%" : ""}</span>
              )}
              {metric.deltaPercent !== null && (
                <span className="analytics-kpi-delta">• {formatDelta(metric.deltaPercent)}</span>
              )}
            </div>
            {metric.secondaryLabel && metric.secondaryValue !== null && (
              <p className="analytics-kpi-meta" style={{ marginTop: "4px", color: "var(--d-blue, #1877f2)", fontWeight: 600 }}>
                {metric.secondaryLabel}: {formatNumber(metric.secondaryValue)}
              </p>
            )}
          </div>

          <div className="analytics-sparkline-wrap">
            <svg className="analytics-sparkline" viewBox="0 0 160 42" role="img" aria-label={`${metric.label} trend`}>
              <path d={sparklinePath(metric.sparkline)} />
            </svg>
          </div>

          <div className="analytics-kpi-footer">
            <button type="button" className="analytics-text-btn" onClick={() => onOpenReport(report)}>
              <span>View Full Report</span>
              <i className="ti ti-arrow-right" aria-hidden="true" />
            </button>
          </div>
        </article>
      ))}
    </div>
  );
}

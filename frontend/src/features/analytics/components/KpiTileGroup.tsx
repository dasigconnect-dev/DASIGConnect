import type { AnalyticsSummaryDto, KpiMetricDto } from "../../../api/analyticsApi";
import { formatMetric, sparklinePath } from "../analyticsUtils";

interface Props {
  summary: AnalyticsSummaryDto;
  onOpenReport: (metric: "posting-delay" | "content-completeness" | "posts-by-institution") => void;
}

export default function KpiTileGroup({ summary, onOpenReport }: Props) {
  const tiles: Array<{ metric: KpiMetricDto; report: Props["onOpenReport"] extends (metric: infer M) => void ? M : never; values: number[]; icon: string }> = [
    {
      metric: summary.averagePostingDelay,
      report: "posting-delay",
      values: [3.4, 2.9, 3.1, summary.averagePostingDelay.value],
      icon: "ti ti-clock",
    },
    {
      metric: summary.contentCompleteness,
      report: "content-completeness",
      values: [88, 91, 94, summary.contentCompleteness.value],
      icon: "ti ti-checklist",
    },
    {
      metric: summary.totalPostsPublished,
      report: "posts-by-institution",
      values: [1, 2, 3, summary.totalPostsPublished.value],
      icon: "ti ti-speakerphone",
    },
  ];

  return (
    <div className="analytics-kpi-grid">
      {tiles.map(({ metric, report, values, icon }) => (
        <article className="analytics-kpi-tile" key={metric.id}>
          <div className="analytics-kpi-top">
            <span className="analytics-kpi-icon">
              <i className={icon} aria-hidden="true" />
            </span>
            <span className={`analytics-target ${metric.targetMet ? "met" : "miss"}`}>
              {metric.target === null ? "Tracking" : metric.targetMet ? "On target" : "Below target"}
            </span>
          </div>
          <div>
            <p className="analytics-kpi-label">{metric.label}</p>
            <div className="analytics-kpi-value">{formatMetric(metric)}</div>
            <p className="analytics-kpi-meta">
              {metric.sampleSize} records{metric.target !== null ? ` · target ${metric.target}${metric.unit === "percent" ? "%" : ""}` : ""}
            </p>
          </div>
          <svg className="analytics-sparkline" viewBox="0 0 160 42" role="img" aria-label={`${metric.label} trend`}>
            <path d={sparklinePath(values)} />
          </svg>
          <button type="button" className="analytics-link-btn" onClick={() => onOpenReport(report)}>
            <i className="ti ti-table-export" aria-hidden="true" />
            Full report
          </button>
        </article>
      ))}
    </div>
  );
}

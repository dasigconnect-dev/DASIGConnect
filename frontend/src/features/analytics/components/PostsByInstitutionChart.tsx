import type { AnalyticsExportMetric, InstitutionPostsDto } from "../../../api/analyticsApi";
import { BLUE_GRADIENT_PALETTE, formatNumber } from "../analyticsUtils";

interface Props {
  rows: InstitutionPostsDto[];
  onOpenReport?: (metric: AnalyticsExportMetric) => void;
}

export default function PostsByInstitutionChart({ rows, onOpenReport }: Props) {
  const sorted = [...rows].sort((a, b) => b.totalPublished - a.totalPublished);
  const max = Math.max(...sorted.map((r) => r.totalPublished), 1);

  return (
    <div className="card-wrap analytics-chart-card">
      <div className="analytics-chart-header">
        <div>
          <h3 className="analytics-chart-title">Posts by Institution</h3>
          <p className="analytics-chart-subtitle">Workflow volume ranking across network members</p>
        </div>
        {onOpenReport && (
          <button
            type="button"
            className="analytics-text-btn"
            onClick={() => onOpenReport("posts-by-institution")}
          >
            <span>View Report</span>
            <i className="ti ti-arrow-right" />
          </button>
        )}
      </div>

      {sorted.length === 0 ? (
        <div className="analytics-empty-state">No published posts in this period.</div>
      ) : (
        <div className="analytics-ranked-bars">
          {sorted.map((row, idx) => {
            const barColor = BLUE_GRADIENT_PALETTE[idx % BLUE_GRADIENT_PALETTE.length];
            const widthPct = Math.max((row.totalPublished / max) * 100, 4);

            return (
              <div className="analytics-ranked-row" key={row.institutionId}>
                <div className="analytics-ranked-label" title={row.institutionName}>
                  <strong>{row.institutionName}</strong>
                  <span>{formatNumber(row.automatedPublished)} auto • {formatNumber(row.manualPublished)} manual</span>
                </div>

                <div className="analytics-ranked-track">
                  <div
                    className="analytics-ranked-fill"
                    style={{ width: `${widthPct}%`, backgroundColor: barColor }}
                  />
                </div>

                <div className="analytics-ranked-value">{formatNumber(row.totalPublished)}</div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

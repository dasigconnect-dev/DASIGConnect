import type { CategoryPerformanceDto } from "../../../api/analyticsApi";
import { BLUE_GRADIENT_PALETTE, formatNumber, formatPercent } from "../analyticsUtils";

interface Props {
  rows: CategoryPerformanceDto[];
}

export default function CategoryPerformanceChart({ rows }: Props) {
  const sorted = [...rows].sort((a, b) => b.postCount - a.postCount);
  const max = Math.max(...sorted.map((r) => r.postCount), 1);

  return (
    <div className="card-wrap analytics-chart-card">
      <div className="analytics-chart-header">
        <div>
          <h3 className="analytics-chart-title">Top Categories Performance</h3>
          <p className="analytics-chart-subtitle">Post volume & completeness rate by content category</p>
        </div>
      </div>

      {sorted.length === 0 ? (
        <div className="analytics-empty-state">No published category data yet.</div>
      ) : (
        <div className="analytics-ranked-bars">
          {sorted.map((row, idx) => {
            const barColor = BLUE_GRADIENT_PALETTE[(idx + 2) % BLUE_GRADIENT_PALETTE.length];
            const widthPct = Math.max((row.postCount / max) * 100, 4);

            return (
              <div className="analytics-ranked-row" key={row.category}>
                <div className="analytics-ranked-label" title={row.category}>
                  <strong>{row.category}</strong>
                  <span>{formatPercent(row.completenessRate)} completeness</span>
                </div>

                <div className="analytics-ranked-track">
                  <div
                    className="analytics-ranked-fill"
                    style={{ width: `${widthPct}%`, backgroundColor: barColor }}
                  />
                </div>

                <div className="analytics-ranked-value">{formatNumber(row.postCount)}</div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

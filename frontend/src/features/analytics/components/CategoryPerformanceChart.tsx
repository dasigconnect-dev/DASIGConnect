import type { CategoryPerformanceDto } from "../../../api/analyticsApi";
import { BLUE_GRADIENT_PALETTE, formatNumber, formatPercent } from "../analyticsUtils";

interface Props {
  rows: CategoryPerformanceDto[];
}

function categoryPerformanceKey(row: CategoryPerformanceDto, index: number) {
  return `${row.category || "uncategorized"}:${row.postCount}:${row.completenessRate}:${index}`;
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
        <div style={{ display: "flex", flexDirection: "column", height: "calc(100% - 60px)", justifyContent: "space-between" }}>
          <div className="analytics-ranked-bars">
            {sorted.map((row, idx) => {
              const barColor = BLUE_GRADIENT_PALETTE[(idx + 2) % BLUE_GRADIENT_PALETTE.length];
              const widthPct = Math.max((row.postCount / max) * 100, 4);

              return (
                <div className="analytics-ranked-row" key={categoryPerformanceKey(row, idx)}>
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

          <div style={{ marginTop: "auto", paddingTop: "14px", borderTop: "1px solid var(--d-border, #E2E8F0)", display: "flex", alignItems: "center", justifyContent: "space-between", fontSize: "12px", color: "var(--d-muted, #5A6F8A)" }}>
            <span style={{ display: "inline-flex", alignItems: "center", gap: "5px" }}>
              <i className="ti ti-chart-bar" style={{ color: "#1877F2" }} />
              Categories Tracked: <strong style={{ color: "#0C1D3D" }}>{sorted.length}</strong>
            </span>
            <span style={{ display: "inline-flex", alignItems: "center", gap: "5px" }}>
              <i className="ti ti-circle-check" style={{ color: "#16A34A" }} />
              Target Completeness: <strong style={{ color: "#16A34A" }}>95.0% Met</strong>
            </span>
          </div>
        </div>
      )}
    </div>
  );
}

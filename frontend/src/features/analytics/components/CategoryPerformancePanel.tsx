import type { CategoryPerformanceDto } from "../../../api/analyticsApi";
import { formatNumber, formatPercent } from "../analyticsUtils";

export default function CategoryPerformancePanel({ rows }: { rows: CategoryPerformanceDto[] }) {
  return (
    <section className="analytics-panel">
      <div className="analytics-panel-header">
        <div>
          <h2>Best Categories</h2>
          <p>Top categories by post count and completeness rate</p>
        </div>
      </div>
      <div className="analytics-simple-list">
        {rows.length === 0 ? (
          <span className="analytics-muted">No published category data yet.</span>
        ) : (
          rows.map((row) => (
            <div key={row.category}>
              <span>{row.category}</span>
              <strong>
                {formatNumber(row.postCount)} | {formatPercent(row.completenessRate)}
              </strong>
            </div>
          ))
        )}
      </div>
    </section>
  );
}

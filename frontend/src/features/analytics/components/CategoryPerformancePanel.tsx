import type { CategoryPerformanceDto } from "../../../api/analyticsApi";
import { formatNumber, formatPercent } from "../analyticsUtils";

function categoryPerformanceKey(row: CategoryPerformanceDto, index: number) {
  return `${row.category || "uncategorized"}:${row.postCount}:${row.completenessRate}:${index}`;
}

export default function CategoryPerformancePanel({ rows }: { rows: CategoryPerformanceDto[] }) {
  if (rows.length === 0) {
    return <p style={{ color: "var(--d-muted)", fontSize: 13 }}>No published category data yet.</p>;
  }

  return (
    <div className="analytics-simple-list">
      {rows.map((row, index) => (
        <div className="analytics-simple-row" key={categoryPerformanceKey(row, index)}>
          <span>{row.category}</span>
          <span className="analytics-simple-row-value">
            {formatNumber(row.postCount)} posts &nbsp;·&nbsp; {formatPercent(row.completenessRate)}
          </span>
        </div>
      ))}
    </div>
  );
}

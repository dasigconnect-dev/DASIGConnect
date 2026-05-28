import type { StatusBreakdownDto } from "../../../api/analyticsApi";
import { formatNumber } from "../analyticsUtils";

function formatStatus(status: string) {
  return status.replaceAll("_", " ").replace(/\b\w/g, (letter) => letter.toUpperCase());
}

export default function StatusBreakdownPanel({ rows }: { rows: StatusBreakdownDto[] }) {
  return (
    <section className="analytics-panel">
      <div className="analytics-panel-header">
        <div>
          <h2>Status Breakdown</h2>
        </div>
      </div>
      <div className="analytics-chip-list">
        {rows.length === 0 ? (
          <span className="analytics-muted">No submissions in scope.</span>
        ) : (
          rows.map((row) => (
            <span className="analytics-status-chip" key={row.status}>
              {formatStatus(row.status)}
              <strong>{formatNumber(row.count)}</strong>
            </span>
          ))
        )}
      </div>
    </section>
  );
}

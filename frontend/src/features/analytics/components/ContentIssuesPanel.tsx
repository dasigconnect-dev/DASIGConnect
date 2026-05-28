import type { ContentIssueDto } from "../../../api/analyticsApi";
import { formatNumber } from "../analyticsUtils";

export default function ContentIssuesPanel({ rows }: { rows: ContentIssueDto[] }) {
  return (
    <section className="analytics-panel">
      <div className="analytics-panel-header">
        <div>
          <h2>Missing Requirements</h2>
        </div>
      </div>
      <div className="analytics-simple-list">
        {rows.length === 0 ? (
          <span className="analytics-muted">No repeated missing requirements in this period.</span>
        ) : (
          rows.map((row) => (
            <div key={row.issue}>
              <span>{row.issue}</span>
              <strong>{formatNumber(row.count)}</strong>
            </div>
          ))
        )}
      </div>
    </section>
  );
}

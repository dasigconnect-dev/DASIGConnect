import type { ContentIssueDto } from "../../../api/analyticsApi";
import { formatNumber } from "../analyticsUtils";

export default function ContentIssuesPanel({ rows }: Readonly<{ rows: ContentIssueDto[] }>) {
  if (rows.length === 0) {
    return <p style={{ color: "var(--d-muted)", fontSize: 13 }}>No repeated missing requirements in this period.</p>;
  }

  return (
    <div className="analytics-simple-list">
      {rows.map((row) => (
        <div className="analytics-simple-row" key={row.issue}>
          <span>{row.issue}</span>
          <span className="analytics-simple-row-value">{formatNumber(row.count)}</span>
        </div>
      ))}
    </div>
  );
}

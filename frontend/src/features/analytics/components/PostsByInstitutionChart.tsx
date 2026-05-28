import type { InstitutionPostsDto } from "../../../api/analyticsApi";
import { formatNumber } from "../analyticsUtils";

interface Props {
  rows: InstitutionPostsDto[];
}

export default function PostsByInstitutionChart({ rows }: Props) {
  const max = Math.max(...rows.map((row) => row.totalPublished), 1);

  return (
    <section className="analytics-panel analytics-panel-tall">
      <div className="analytics-panel-header">
        <div>
          <h2>Posts by Institution</h2>
          <p>Workflow post volume by institution. Direct posts are shown separately.</p>
        </div>
      </div>

      {rows.length === 0 ? (
        <div className="analytics-empty">No published posts in this period.</div>
      ) : (
        <div className="analytics-bars">
          {rows.map((row) => (
            <div className="analytics-bar-row" key={row.institutionId}>
              <div className="analytics-bar-label">
                <strong>{row.institutionName}</strong>
                <span>
                  {formatNumber(row.automatedPublished)} auto | {formatNumber(row.manualPublished)} manual |{" "}
                  {formatNumber(row.adminDirectPosts)} direct
                </span>
              </div>
              <div className="analytics-bar-track" aria-label={`${row.institutionName} workflow posts`}>
                <span style={{ width: `${(row.totalPublished / max) * 100}%` }} />
              </div>
              <div className="analytics-bar-value">{formatNumber(row.totalPublished)}</div>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

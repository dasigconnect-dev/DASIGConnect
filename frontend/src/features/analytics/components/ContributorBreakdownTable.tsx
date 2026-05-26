import type { InstitutionPostsDto } from "../../../api/analyticsApi";
import { formatNumber } from "../analyticsUtils";

interface Props {
  rows: InstitutionPostsDto[];
}

export default function ContributorBreakdownTable({ rows }: Props) {
  return (
    <section className="analytics-panel">
      <div className="analytics-panel-header">
        <div>
          <h2>Contributor Breakdown</h2>
          <p>Institution-level publishing distribution for contributor reporting</p>
        </div>
      </div>
      <div className="analytics-table-wrap">
        <table className="analytics-table">
          <thead>
            <tr>
              <th>Institution</th>
              <th>Total</th>
              <th>Automated</th>
              <th>Manual</th>
              <th>Direct</th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td colSpan={5}>No publication records for this period.</td>
              </tr>
            ) : (
              rows.map((row) => (
                <tr key={row.institutionId}>
                  <td>{row.institutionName}</td>
                  <td>{formatNumber(row.totalPublished)}</td>
                  <td>{formatNumber(row.automatedPublished)}</td>
                  <td>{formatNumber(row.manualPublished)}</td>
                  <td>{formatNumber(row.adminDirectPosts)}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}

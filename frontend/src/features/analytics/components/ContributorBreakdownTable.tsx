import type { ContributorBreakdownDto } from "../../../api/analyticsApi";
import { formatMetric, formatNumber, formatPercent } from "../analyticsUtils";

interface Props {
  rows: ContributorBreakdownDto[];
}

export default function ContributorBreakdownTable({ rows }: Readonly<Props>) {
  return (
    <section className="analytics-panel">
      <div className="analytics-panel-header">
        <div>
          <h2>Contributor Breakdown</h2>
          <p>Publishing volume, completeness, and posting delay per contributor</p>
        </div>
      </div>
      <div className="analytics-table-wrap">
        <table className="analytics-table">
          <thead>
            <tr>
              <th>Contributor</th>
              <th>Submitted</th>
              <th>Posts Published</th>
              <th>Needs Revision</th>
              <th>Revision Cycles</th>
              <th>Completeness</th>
              <th>Avg Posting Delay</th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td colSpan={7}>No contributor publication records for this period.</td>
              </tr>
            ) : (
              rows.map((row) => (
                <tr key={row.contributorId}>
                  <td>{row.contributorName}</td>
                  <td>{formatNumber(row.postsSubmitted)}</td>
                  <td>{formatNumber(row.postsPublished)}</td>
                  <td>{formatNumber(row.needsRevisionCount)}</td>
                  <td>{formatNumber(row.revisionCycles)}</td>
                  <td>{formatPercent(row.completenessRate)}</td>
                  <td>{formatMetric({
                    id: "delay",
                    label: "Delay",
                    value: row.averagePostingDelayDays,
                    unit: "days",
                    sampleSize: row.postsPublished,
                    target: null,
                    targetMet: true,
                    deltaPercent: null,
                    sparkline: [],
                    secondaryLabel: null,
                    secondaryValue: null,
                  })}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}

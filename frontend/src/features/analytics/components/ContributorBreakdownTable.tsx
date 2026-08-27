import { useState, useMemo } from "react";
import type { ContributorBreakdownDto } from "../../../api/analyticsApi";
import { formatMetric, formatNumber, formatPercent } from "../analyticsUtils";

interface Props {
  rows: ContributorBreakdownDto[];
}

const PAGE_SIZE = 7;

export default function ContributorBreakdownTable({ rows }: Readonly<Props>) {
  const [showAll, setShowAll] = useState(false);

  const displayedRows = useMemo(() => {
    if (showAll) return rows;
    return rows.slice(0, PAGE_SIZE);
  }, [rows, showAll]);

  return (
    <>
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          margin: "24px 0 12px",
          padding: "0 2px",
        }}
      >
        <div className="analytics-section-title" style={{ margin: 0 }}>
          Contributor Breakdown ({rows.length})
        </div>

        {rows.length > PAGE_SIZE && (
          <button
            type="button"
            className="notif-view-all-pill"
            onClick={() => setShowAll((prev) => !prev)}
            title={showAll ? `Show top ${PAGE_SIZE}` : "Show all contributors"}
          >
            <i className={showAll ? "ti ti-chevron-up" : "ti ti-list-details"} />
            <span>{showAll ? `Show Top ${PAGE_SIZE}` : `View All Contributors (${rows.length})`}</span>
          </button>
        )}
      </div>

      <div className="card-wrap" style={{ marginBottom: "24px" }}>
        <table className="data-table" id="analytics-contributor-table">
          <thead>
            <tr>
              <th style={{ width: "26%" }}>CONTRIBUTOR / NAME</th>
              <th style={{ width: "12%" }}>SUBMITTED</th>
              <th style={{ width: "12%" }}>PUBLISHED</th>
              <th style={{ width: "14%" }}>NEEDS REVISION</th>
              <th style={{ width: "12%" }}>CYCLES</th>
              <th style={{ width: "12%" }}>COMPLETENESS</th>
              <th style={{ width: "12%" }}>AVG DELAY</th>
            </tr>
          </thead>
          <tbody className="act-table-animate">
            {rows.length === 0 ? (
              <tr>
                <td colSpan={7} style={{ textAlign: "center", padding: "36px 20px", color: "var(--d-muted)" }}>
                  No contributor publication records for this period.
                </td>
              </tr>
            ) : (
              displayedRows.map((row) => (
                <tr key={row.contributorId}>
                  <td>
                    <div style={{ fontWeight: 600, color: "#0C1D3D" }}>{row.contributorName}</div>
                  </td>
                  <td className="act-institution">{formatNumber(row.postsSubmitted)}</td>
                  <td className="act-institution">{formatNumber(row.postsPublished)}</td>
                  <td>
                    <span
                      className={`status-pill ${
                        row.needsRevisionCount > 0 ? "pill-revision" : "sp-approved"
                      }`}
                    >
                      {formatNumber(row.needsRevisionCount)}
                    </span>
                  </td>
                  <td className="act-institution">{formatNumber(row.revisionCycles)}</td>
                  <td className="act-institution">{formatPercent(row.completenessRate)}</td>
                  <td className="act-date">
                    {formatMetric({
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
                    })}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </>
  );
}

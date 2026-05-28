import { useNavigate } from "react-router-dom";
import type { StatusBreakdownDto } from "../../../api/analyticsApi";
import { formatNumber } from "../analyticsUtils";

interface Props {
  rows: StatusBreakdownDto[];
  role?: string;
}

function formatStatus(status: string) {
  return status.replaceAll("_", " ").replace(/\b\w/g, (letter) => letter.toUpperCase());
}

/**
 * Returns the navigation URL for a given status and role.
 * Returns null when no meaningful navigation target exists.
 */
function getStatusNavUrl(status: string, role: string): string | null {
  const s = status.toLowerCase();

  if (role === "contributor") {
    if (s === "draft") return "/submissions/new?tab=drafts";
    // Any non-draft status lives in the Submitted queue
    return "/submissions/new?tab=submitted";
  }

  if (role === "validator") {
    if (s === "approved" || s === "published") return "/scheduler/calendar";
    return "/validation/queue";
  }

  if (role === "administrator" || role === "admin") {
    if (s === "approved" || s === "published") return "/scheduler/calendar";
    if (s === "publish_failed" || s === "direct_post_scheduled") return "/admin/resolution";
    return "/validation/queue";
  }

  return null;
}

export default function StatusBreakdownPanel({ rows, role = "" }: Readonly<Props>) {
  const navigate = useNavigate();

  return (
    <section className="analytics-panel">
      <div className="analytics-panel-header">
        <div>
          <h2>Status Breakdown</h2>
          <p>Submission distribution across all workflow statuses</p>
        </div>
      </div>

      <div className="analytics-chip-list">
        {rows.length === 0 ? (
          <span className="analytics-muted">No submissions in scope.</span>
        ) : (
          rows.map((row) => {
            const url = role ? getStatusNavUrl(row.status, role) : null;
            const label = formatStatus(row.status);

            return (
              <div className="analytics-chip-item" key={row.status}>
                {url ? (
                  <button
                    type="button"
                    className="analytics-status-chip analytics-status-chip-btn"
                    onClick={() => navigate(url)}
                    aria-label={`View ${label} submissions`}
                    title={`Navigate to ${label}`}
                  >
                    {label}
                    <strong>{formatNumber(row.count)}</strong>
                  </button>
                ) : (
                  <span className="analytics-status-chip">
                    {label}
                    <strong>{formatNumber(row.count)}</strong>
                  </span>
                )}

                {url && (
                  <button
                    type="button"
                    className="analytics-chip-view-btn"
                    onClick={() => navigate(url)}
                    aria-label={`View ${label} submissions`}
                    tabIndex={-1}
                  >
                    {"View"}
                    <i className="ti ti-arrow-up-right" aria-hidden="true" />
                  </button>
                )}
              </div>
            );
          })
        )}
      </div>
    </section>
  );
}

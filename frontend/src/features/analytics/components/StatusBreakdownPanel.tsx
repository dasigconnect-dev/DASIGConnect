import { useNavigate } from "react-router-dom";
import type { StatusBreakdownDto } from "../../../api/analyticsApi";
import { formatNumber } from "../analyticsUtils";

interface Props {
  rows: StatusBreakdownDto[];
  role?: string;
}

function formatStatus(status: string) {
  return status.replaceAll("_", " ").replace(/\b\w/g, (l) => l.toUpperCase());
}

function getStatusNavUrl(status: string, role: string): string | null {
  const s = status.toLowerCase();
  if (role === "contributor") {
    return s === "draft" ? "/submissions/new?tab=drafts" : "/submissions/new?tab=submitted";
  }
  if (role === "moderator" || role === "admin") {
    if (s === "approved" || s === "published") return "/scheduler/calendar";
    if (s === "publish_failed") return "/validation/queue?tab=failed";
    return "/validation/queue";
  }
  return null;
}

export default function StatusBreakdownPanel({ rows, role = "" }: Readonly<Props>) {
  const navigate = useNavigate();

  if (rows.length === 0) {
    return <p style={{ color: "var(--d-muted)", fontSize: 13 }}>No submissions in scope.</p>;
  }

  return (
    <div className="analytics-status-chips-wrap">
      {rows.map((row) => {
        const url = role ? getStatusNavUrl(row.status, role) : null;
        const label = formatStatus(row.status);
        return (
          <button
            key={row.status}
            type="button"
            className="analytics-status-pill-btn"
            onClick={() => url && navigate(url)}
            style={{ cursor: url ? "pointer" : "default" }}
            title={url ? `Go to ${label}` : label}
          >
            {label}
            <strong>{formatNumber(row.count)}</strong>
          </button>
        );
      })}
    </div>
  );
}

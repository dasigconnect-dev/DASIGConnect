import { STATUS_COLORS, STATUS_LABELS } from "./calendarStatus";
import type { User } from "../../types/auth.types";

const LEGEND_STATUSES = [
  "scheduled",
  "published",
  "published_manual",
  "publish_failed",
  "admin_direct_post",
  "pending",
];

export default function CalendarLegend({ user }: { user: User }) {
  const statuses = user.role === "contributor"
    ? LEGEND_STATUSES.filter((status) => status !== "pending")
    : LEGEND_STATUSES;

  return (
    <div className="cal-legend">
      {statuses.map((status) => (
        <div key={status} className="cal-legend-item">
          <span className="cal-legend-dot" style={{ background: STATUS_COLORS[status].text }} />
          <span>{STATUS_LABELS[status]}</span>
        </div>
      ))}
    </div>
  );
}

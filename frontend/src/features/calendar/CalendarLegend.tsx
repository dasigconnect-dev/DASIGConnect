import { STATUS_COLORS, STATUS_LABELS } from "./calendarStatus";

const LEGEND_STATUSES = [
  "scheduled",
  "published",
  "published_manual",
  "publish_failed",
  "admin_direct_post",
  "pending",
];

export default function CalendarLegend() {
  return (
    <div className="cal-legend">
      {LEGEND_STATUSES.map((status) => (
        <div key={status} className="cal-legend-item">
          <span className="cal-legend-dot" style={{ background: STATUS_COLORS[status].text }} />
          <span>{STATUS_LABELS[status]}</span>
        </div>
      ))}
    </div>
  );
}

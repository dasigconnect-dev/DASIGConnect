import { STATUS_COLORS, STATUS_LABELS } from "./calendarStatus";

const LEGEND_STATUSES = [
  "scheduled",
  "published",
  "published_manual",
  "publish_failed",
  "admin_direct_post",
  "pending",
  "missed_review",
];

export default function CalendarLegend() {
  // Every role can now see workflow states on the calendar — contributors and
  // moderators for their own submissions, admins for all — so the full legend
  // applies to everyone.
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

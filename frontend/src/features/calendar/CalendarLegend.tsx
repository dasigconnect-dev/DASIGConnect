import { STATUS_COLORS, STATUS_LABELS } from "./calendarStatus";

export default function CalendarLegend() {
  return (
    <div className="cal-legend">
      {Object.entries(STATUS_COLORS).map(([status, color]) => (
        <div key={status} className="cal-legend-item">
          <span className="cal-legend-dot" style={{ background: color.bg }} />
          <span>{STATUS_LABELS[status]}</span>
        </div>
      ))}
    </div>
  );
}

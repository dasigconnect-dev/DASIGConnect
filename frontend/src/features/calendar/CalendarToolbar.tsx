export type CalendarViewMode = "dayGridMonth" | "timeGridWeek";

interface CalendarToolbarProps {
  view: CalendarViewMode;
  loading: boolean;
  onViewChange: (view: CalendarViewMode) => void;
  onRefresh: () => void;
}

export default function CalendarToolbar({
  view,
  loading,
  onViewChange,
  onRefresh,
}: CalendarToolbarProps) {
  return (
    <div className="screen-actions">
      <div className="cal-view-toggle" aria-label="Calendar view">
        <button
          type="button"
          className={`view-btn${view === "dayGridMonth" ? " active" : ""}`}
          onClick={() => onViewChange("dayGridMonth")}
          aria-pressed={view === "dayGridMonth"}
        >
          <i className="ti ti-calendar-month" aria-hidden="true" />
          Month
        </button>
        <button
          type="button"
          className={`view-btn${view === "timeGridWeek" ? " active" : ""}`}
          onClick={() => onViewChange("timeGridWeek")}
          aria-pressed={view === "timeGridWeek"}
        >
          <i className="ti ti-calendar-week" aria-hidden="true" />
          Week
        </button>
      </div>
      <button
        type="button"
        className="btn-secondary"
        onClick={onRefresh}
        disabled={loading}
      >
        <i className="ti ti-refresh" aria-hidden="true" />
        Refresh
      </button>
    </div>
  );
}

export type CalendarViewMode = "dayGridMonth" | "timeGridWeek";

interface CalendarToolbarProps {
  view: CalendarViewMode;
  loading: boolean;
  rangeLabel: string;
  showFullDay: boolean;
  onViewChange: (view: CalendarViewMode) => void;
  onNavigate: (action: "prev" | "today" | "next") => void;
  onToggleFullDay: () => void;
  onRefresh: () => void;
}

export default function CalendarToolbar({
  view,
  loading,
  rangeLabel,
  showFullDay,
  onViewChange,
  onNavigate,
  onToggleFullDay,
  onRefresh,
}: CalendarToolbarProps) {
  return (
    <div className="screen-actions cal-toolbar">
      <div className="cal-toolbar-group cal-toolbar-left" aria-label="Calendar navigation">
        <div className="cal-range-controls">
        <button type="button" className="btn-secondary btn-sm" onClick={() => onNavigate("prev")}>
          <i className="ti ti-chevron-left" aria-hidden="true" />
          Previous
        </button>
        <button type="button" className="btn-primary btn-sm cal-today-btn" onClick={() => onNavigate("today")}>
          Today
        </button>
        <button type="button" className="btn-secondary btn-sm" onClick={() => onNavigate("next")}>
          Next
          <i className="ti ti-chevron-right" aria-hidden="true" />
        </button>
        </div>
      </div>
      <div className="cal-toolbar-group cal-toolbar-center" aria-live="polite">
        <div className="cal-range-label">{rangeLabel}</div>
      </div>
      <div className="cal-toolbar-group cal-toolbar-right">
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
        <div className={`cal-full-day-slot${view === "timeGridWeek" ? " is-active" : ""}`}>
          <button
            type="button"
            className={`btn-secondary btn-sm cal-full-day-toggle${showFullDay ? " active" : ""}`}
            onClick={onToggleFullDay}
            aria-pressed={showFullDay}
            disabled={view !== "timeGridWeek"}
          >
            <i className="ti ti-clock-hour-24" aria-hidden="true" />
            {showFullDay ? "Publishing Hours" : "Show Full Day"}
          </button>
        </div>
        <button
          type="button"
          className="btn-secondary"
          onClick={onRefresh}
          disabled={loading}
        >
          {loading ? (
            <span className="spinner-ring spinner-ring-sm" aria-hidden="true" />
          ) : (
            <i className="ti ti-refresh" aria-hidden="true" />
          )}
          {loading ? "Refreshing" : "Refresh"}
        </button>
      </div>
    </div>
  );
}

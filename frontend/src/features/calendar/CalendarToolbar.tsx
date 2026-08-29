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
    <div className="cal-toolbar">
      {/* Left Zone: Navigation Controls */}
      <div className="cal-toolbar-left" aria-label="Calendar navigation">
        <div className="cal-range-controls">
          <button type="button" className="cal-nav-btn" onClick={() => onNavigate("prev")} aria-label="Previous">
            <i className="ti ti-chevron-left" aria-hidden="true" />
          </button>
          <button type="button" className="cal-today-pill" onClick={() => onNavigate("today")}>
            Today
          </button>
          <button type="button" className="cal-nav-btn" onClick={() => onNavigate("next")} aria-label="Next">
            <i className="ti ti-chevron-right" aria-hidden="true" />
          </button>
        </div>
      </div>

      {/* Center Zone: Date Range Heading */}
      <div className="cal-toolbar-center" aria-live="polite">
        <div className="cal-range-label">{rangeLabel}</div>
      </div>

      {/* Right Zone: View Modes & Actions */}
      <div className="cal-toolbar-right">
        <div className="cal-view-toggle" role="tablist" aria-label="Calendar view">
          <button
            type="button"
            role="tab"
            className={`view-btn${view === "dayGridMonth" ? " active" : ""}`}
            onClick={() => onViewChange("dayGridMonth")}
            aria-selected={view === "dayGridMonth"}
          >
            <i className="ti ti-calendar-month" aria-hidden="true" />
            Month
          </button>
          <button
            type="button"
            role="tab"
            className={`view-btn${view === "timeGridWeek" ? " active" : ""}`}
            onClick={() => onViewChange("timeGridWeek")}
            aria-selected={view === "timeGridWeek"}
          >
            <i className="ti ti-calendar-week" aria-hidden="true" />
            Week
          </button>
        </div>

        <button
          type="button"
          className={`cal-pill-btn${view === "timeGridWeek" ? " is-week" : " is-disabled"}${showFullDay && view === "timeGridWeek" ? " active" : ""}`}
          onClick={onToggleFullDay}
          disabled={view !== "timeGridWeek"}
          aria-pressed={view === "timeGridWeek" && showFullDay}
          title={
            view !== "timeGridWeek"
              ? "Show Full Day is only available in Week view"
              : showFullDay
                ? "Switch to standard publishing hours"
                : "Show full 24-hour day schedule"
          }
        >
          <i className="ti ti-clock-hour-24" aria-hidden="true" />
          <span>{showFullDay && view === "timeGridWeek" ? "Publishing Hours" : "Show Full Day"}</span>
        </button>

        <button
          type="button"
          className="cal-refresh-btn"
          onClick={onRefresh}
          disabled={loading}
          title="Refresh calendar"
          aria-label="Refresh calendar"
        >
          {loading ? (
            <span className="spinner-ring spinner-ring-sm" aria-hidden="true" />
          ) : (
            <i className="ti ti-refresh" aria-hidden="true" />
          )}
          <span>{loading ? "Refreshing" : "Refresh"}</span>
        </button>
      </div>
    </div>
  );
}

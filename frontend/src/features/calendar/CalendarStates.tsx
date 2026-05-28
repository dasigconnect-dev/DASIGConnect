interface CalendarErrorStateProps {
  message: string;
  onRetry: () => void;
}

export function CalendarLoadingState() {
  return (
    <div className="cal-loading-shell" aria-live="polite" aria-label="Loading calendar events">
      <div className="cal-loading-top">
        <div className="cal-skeleton cal-skeleton-title" />
        <div className="cal-skeleton cal-skeleton-action" />
      </div>
      <div className="cal-skeleton-grid">
        {Array.from({ length: 35 }).map((_, index) => (
          <div className="cal-skeleton-cell" key={index}>
            {index % 4 === 0 && <div className="cal-skeleton cal-skeleton-event" />}
            {index % 7 === 0 && <div className="cal-skeleton cal-skeleton-event small" />}
          </div>
        ))}
      </div>
    </div>
  );
}

export function CalendarLoadingOverlay() {
  return (
    <div className="cal-loading-overlay" aria-live="polite" aria-label="Updating calendar view">
      <div className="cal-loading-overlay-card">
        <div className="cal-skeleton cal-loading-overlay-title" />
        <div className="cal-skeleton cal-loading-overlay-sub" />
      </div>
    </div>
  );
}

export function CalendarErrorState({
  message,
  onRetry,
}: CalendarErrorStateProps) {
  return (
    <div className="screen-state-center" role="alert">
      <i className="ti ti-alert-circle state-icon error-icon" aria-hidden="true" />
      <p>{message}</p>
      <button type="button" className="btn-primary" onClick={onRetry}>
        Retry
      </button>
    </div>
  );
}

export function CalendarEmptyOverlay() {
  return (
    <div className="cal-empty-overlay cal-empty-overlay-visible">
      <i className="ti ti-calendar-off state-icon" aria-hidden="true" />
      <p>No publishing items match this view.</p>
    </div>
  );
}

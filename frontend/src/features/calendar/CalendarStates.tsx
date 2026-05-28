interface CalendarErrorStateProps {
  message: string;
  onRetry: () => void;
}

export function CalendarLoadingState() {
  return (
    <div className="screen-state-center" aria-live="polite">
      <div className="spinner-ring" />
      <span>Loading calendar...</span>
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
    <div className="cal-empty-overlay">
      <i className="ti ti-calendar-off state-icon" aria-hidden="true" />
      <p>No scheduled content yet.</p>
    </div>
  );
}

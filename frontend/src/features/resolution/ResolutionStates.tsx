interface ResolutionErrorStateProps {
  message: string;
  onRetry: () => void;
}

export function ResolutionLoadingState() {
  return (
    <div className="screen-state-center" aria-live="polite">
      <div className="spinner-ring" />
      <span>Loading failures...</span>
    </div>
  );
}

export function ResolutionErrorState({
  message,
  onRetry,
}: ResolutionErrorStateProps) {
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

export function ResolutionEmptyState() {
  return (
    <div className="screen-state-center">
      <i className="ti ti-circle-check state-icon success-icon" aria-hidden="true" />
      <p className="state-heading">All clear</p>
      <p className="state-sub">No failed publications at this time.</p>
    </div>
  );
}

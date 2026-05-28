interface ResolutionActionButtonsProps {
  busy: boolean;
  manualPublishInProgress: boolean;
  onRetry: () => void;
  onStartManual: () => void;
  onCancelManual: () => void;
  onComplete: () => void;
}

export default function ResolutionActionButtons({
  busy,
  manualPublishInProgress,
  onRetry,
  onStartManual,
  onCancelManual,
  onComplete,
}: ResolutionActionButtonsProps) {
  if (manualPublishInProgress) {
    return (
      <div className="res-actions">
        <button
          type="button"
          className="btn-primary btn-sm"
          disabled={busy}
          onClick={onComplete}
        >
          <i className="ti ti-check" aria-hidden="true" />
          Mark as Published
        </button>
        <button
          type="button"
          className="btn-danger btn-sm"
          disabled={busy}
          onClick={onCancelManual}
        >
          <i className="ti ti-x" aria-hidden="true" />
          Cancel
        </button>
      </div>
    );
  }

  return (
    <div className="res-actions">
      <button
        type="button"
        className="btn-secondary btn-sm"
        disabled={busy}
        onClick={onRetry}
      >
        <i className="ti ti-refresh" aria-hidden="true" />
        Retry Auto-Publish
      </button>
      <button
        type="button"
        className="btn-primary btn-sm"
        disabled={busy}
        onClick={onStartManual}
      >
        <i className="ti ti-hand-click" aria-hidden="true" />
        Start Manual Publish
      </button>
    </div>
  );
}

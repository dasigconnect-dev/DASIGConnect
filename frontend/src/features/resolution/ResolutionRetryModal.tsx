import type { FailedPublication } from "../../api/resolutionApi";

interface ResolutionRetryModalProps {
  item: FailedPublication | null;
  busy: boolean;
  onConfirm: () => void;
  onClose: () => void;
}

export default function ResolutionRetryModal({
  item,
  busy,
  onConfirm,
  onClose,
}: ResolutionRetryModalProps) {
  if (!item) return null;

  return (
    <div
      className="modal-backdrop"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-label="Retry auto-publish"
    >
      <div className="modal-card" onClick={(e) => e.stopPropagation()}>
        <div className="modal-card-header">
          <h2 className="modal-card-title">Retry Auto-Publish?</h2>
          <button
            type="button"
            className="modal-close-btn"
            onClick={onClose}
            aria-label="Close"
          >
            <i className="ti ti-x" aria-hidden="true" />
          </button>
        </div>
        <div className="modal-card-body">
          <p>
            This will reset the retry counter and re-queue{" "}
            <strong>"{item.eventTitle}"</strong> for the automated publisher. It
            will attempt to post to Facebook on the next scheduler cycle.
          </p>
        </div>
        <div className="modal-card-footer">
          <button type="button" className="btn-secondary" onClick={onClose}>
            Cancel
          </button>
          <button
            type="button"
            className="btn-primary"
            disabled={busy}
            onClick={onConfirm}
          >
            {busy ? (
              <>
                <div className="spinner-ring spinner-ring-sm" />
                Queuing...
              </>
            ) : (
              "Confirm Retry"
            )}
          </button>
        </div>
      </div>
    </div>
  );
}

import { useState } from "react";
import type { FailedPublication } from "../../api/resolutionApi";

interface ResolutionRetryModalProps {
  item: FailedPublication | null;
  busy: boolean;
  onConfirm: () => void;
  onConfirmWithNewSchedule: (scheduledAt: string, overrideReason?: string) => void;
  onClose: () => void;
}

export default function ResolutionRetryModal({
  item,
  busy,
  onConfirm,
  onConfirmWithNewSchedule,
  onClose,
}: ResolutionRetryModalProps) {
  const [rescheduling, setRescheduling] = useState(false);
  const [scheduledAt, setScheduledAt] = useState("");
  const [overrideReason, setOverrideReason] = useState("");

  if (!item) return null;

  function handleClose() {
    setRescheduling(false);
    setScheduledAt("");
    setOverrideReason("");
    onClose();
  }

  return (
    <div
      className="modal-backdrop"
      onClick={handleClose}
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
            onClick={handleClose}
            aria-label="Close"
          >
            <i className="ti ti-x" aria-hidden="true" />
          </button>
        </div>
        <div className="modal-card-body">
          {!rescheduling ? (
            <p>
              This will reset the retry counter and re-queue{" "}
              <strong>"{item.eventTitle}"</strong> for the automated publisher. It
              will attempt to post to Facebook on the next scheduler cycle.
            </p>
          ) : (
            <>
              <p>
                Choose a new publish time for <strong>"{item.eventTitle}"</strong>.
                Guard rails are re-checked against the new slot.
              </p>
              <div className="rc-field">
                <label className="rc-label" htmlFor="retry-new-slot">
                  New scheduled time <span className="rc-required">*</span>
                </label>
                <input
                  id="retry-new-slot"
                  type="datetime-local"
                  className="rc-input"
                  value={scheduledAt}
                  onChange={(e) => setScheduledAt(e.target.value)}
                />
              </div>
              <div className="rc-field">
                <label className="rc-label" htmlFor="retry-override-reason">
                  Override reason (only needed if the new slot violates a guard rail)
                </label>
                <input
                  id="retry-override-reason"
                  type="text"
                  className="rc-input"
                  value={overrideReason}
                  onChange={(e) => setOverrideReason(e.target.value)}
                  placeholder="Optional"
                />
              </div>
            </>
          )}
        </div>
        <div className="modal-card-footer">
          <button type="button" className="btn-secondary" onClick={handleClose}>
            Cancel
          </button>
          {!rescheduling && (
            <button
              type="button"
              className="btn-secondary"
              onClick={() => setRescheduling(true)}
            >
              Retry With New Schedule
            </button>
          )}
          <button
            type="button"
            className="btn-primary"
            disabled={busy || (rescheduling && !scheduledAt)}
            onClick={() => {
              if (rescheduling) {
                onConfirmWithNewSchedule(
                  new Date(scheduledAt).toISOString(),
                  overrideReason || undefined,
                );
              } else {
                onConfirm();
              }
            }}
          >
            {busy ? (
              <>
                <div className="spinner-ring spinner-ring-sm" />
                {rescheduling ? "Rescheduling..." : "Queuing..."}
              </>
            ) : rescheduling ? (
              "Confirm New Schedule"
            ) : (
              "Confirm Retry"
            )}
          </button>
        </div>
      </div>
    </div>
  );
}

import { useEffect, useState } from "react";
import type { FailedPublication } from "../../api/resolutionApi";

interface ResolutionRetryModalProps {
  item: FailedPublication | null;
  busy: boolean;
  onConfirmWithNewSchedule: (scheduledAt: string, overrideReason?: string) => void;
  onClose: () => void;
}

function toDatetimeLocal(iso: string | null) {
  if (!iso) return "";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "";
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export default function ResolutionRetryModal({
  item,
  busy,
  onConfirmWithNewSchedule,
  onClose,
}: ResolutionRetryModalProps) {
  const [scheduledAt, setScheduledAt] = useState("");
  const [overrideReason, setOverrideReason] = useState("");

  useEffect(() => {
    if (!item) return;
    queueMicrotask(() => {
      setScheduledAt(toDatetimeLocal(item.scheduledAt));
      setOverrideReason("");
    });
  }, [item]);

  if (!item) return null;

  function handleClose() {
    setOverrideReason("");
    onClose();
  }

  return (
    <div
      className="modal-backdrop"
      onClick={handleClose}
      role="dialog"
      aria-modal="true"
      aria-label="Retry with new schedule"
    >
      <div className="modal-card" onClick={(e) => e.stopPropagation()}>
        <div className="modal-card-header">
          <h2 className="modal-card-title">Retry With New Schedule</h2>
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
          <p>
            Choose a publish time for <strong>"{item.eventTitle}"</strong> and it will
            be re-queued for the automated publisher. Guard rails are re-checked
            against the new slot.
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
        </div>
        <div className="modal-card-footer">
          <button type="button" className="btn-secondary" onClick={handleClose}>
            Cancel
          </button>
          <button
            type="button"
            className="btn-primary"
            disabled={busy || !scheduledAt}
            onClick={() =>
              onConfirmWithNewSchedule(new Date(scheduledAt).toISOString(), overrideReason || undefined)
            }
          >
            {busy ? (
              <>
                <div className="spinner-ring spinner-ring-sm" />
                Rescheduling...
              </>
            ) : (
              "Confirm New Schedule"
            )}
          </button>
        </div>
      </div>
    </div>
  );
}

import { useState } from "react";
import type { OverrideRequest } from "../../api/resolutionApi";

interface SlotSuggestionModalProps {
  request: OverrideRequest | null;
  busy: boolean;
  onConfirm: (requestId: string, suggestedSlot: string, message: string) => void;
  onClose: () => void;
}

export default function SlotSuggestionModal({
  request,
  busy,
  onConfirm,
  onClose,
}: SlotSuggestionModalProps) {
  const [slot, setSlot] = useState("");
  const [message, setMessage] = useState("");
  const [minDatetime] = useState(() =>
    new Date(Date.now() + 2 * 60 * 60 * 1000).toISOString().slice(0, 16),
  );

  if (!request) return null;

  const canConfirm = slot !== "" && !busy;

  function handleConfirm() {
    if (!canConfirm) return;
    onConfirm(request!.id, new Date(slot).toISOString(), message.trim());
  }

  function handleClose() {
    if (busy) return;
    setSlot("");
    setMessage("");
    onClose();
  }

  return (
    <div
      className="modal-backdrop"
      onClick={handleClose}
      role="dialog"
      aria-modal="true"
      aria-label="Suggest alternative slot"
    >
      <div className="modal-card rc-modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-card-header">
          <h2 className="modal-card-title">Suggest Alternative Slot</h2>
          <button type="button" className="modal-close-btn" onClick={handleClose} aria-label="Close">
            <i className="ti ti-x" aria-hidden="true" />
          </button>
        </div>

        <div className="modal-card-body rc-modal-body">
          <p className="rc-modal-target">
            <i className="ti ti-file-text" aria-hidden="true" />
            {request.eventTitle}
          </p>

          <div className="rc-modal-meta-row">
            <span className="rc-meta-chip rc-chip-amber">
              <i className="ti ti-shield-exclamation" aria-hidden="true" />
              {request.violatedRule}
            </span>
            <span className="rc-meta-chip">
              Requested: {new Date(request.requestedSlot).toLocaleString("en-PH", {
                month: "short", day: "numeric", hour: "numeric", minute: "2-digit", hour12: true,
              })}
            </span>
          </div>

          <div className="rc-field">
            <label className="rc-label" htmlFor="suggest-slot">
              Compliant alternative slot <span className="rc-required">*</span>
            </label>
            <input
              id="suggest-slot"
              type="datetime-local"
              className="rc-input"
              min={minDatetime}
              value={slot}
              onChange={(e) => setSlot(e.target.value)}
            />
            <span className="rc-hint">
              Must satisfy all hard guard rail rules (GR-H1, H2, H3). Validated server-side.
            </span>
          </div>

          <div className="rc-field">
            <label className="rc-label" htmlFor="suggest-message">
              Message to Contributor (optional)
            </label>
            <textarea
              id="suggest-message"
              className="rc-textarea"
              rows={3}
              placeholder="Explain why this slot works better..."
              value={message}
              onChange={(e) => setMessage(e.target.value)}
            />
          </div>
        </div>

        <div className="modal-card-footer">
          <button type="button" className="btn-secondary" onClick={handleClose} disabled={busy}>
            Cancel
          </button>
          <button
            type="button"
            className="btn-primary"
            disabled={!canConfirm}
            onClick={handleConfirm}
          >
            {busy ? (
              <><div className="spinner-ring spinner-ring-sm" /> Sending...</>
            ) : (
              <>
                <i className="ti ti-send" aria-hidden="true" />
                Send Suggestion
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}

import { useState } from "react";

const REJECTION_CODES = [
  { value: "INCOMPLETE_CONTENT", label: "Incomplete content" },
  { value: "INAPPROPRIATE_CONTENT", label: "Inappropriate content" },
  { value: "WRONG_FORMAT", label: "Wrong format" },
  { value: "DUPLICATE_EVENT", label: "Duplicate event" },
  { value: "WRONG_INSTITUTION", label: "Wrong institution" },
  { value: "OTHER", label: "Other (requires notes)" },
];

interface RejectOnBehalfModalProps {
  open: boolean;
  eventTitle: string;
  // "timeout" = Cat. B (6-option taxonomy + optional notes)
  // "override" = Cat. C deny (free-text reason, optional)
  mode: "timeout" | "override";
  busy: boolean;
  onConfirm: (reasonCode: string, notes: string) => void;
  onClose: () => void;
}

export default function RejectOnBehalfModal({
  open,
  eventTitle,
  mode,
  busy,
  onConfirm,
  onClose,
}: RejectOnBehalfModalProps) {
  const [reasonCode, setReasonCode] = useState("");
  const [notes, setNotes] = useState("");

  if (!open) return null;

  const isTimeout = mode === "timeout";
  const notesRequired = isTimeout && reasonCode === "OTHER";
  const canConfirm = isTimeout
    ? reasonCode !== "" && (!notesRequired || notes.trim().length >= 5)
    : true;

  function handleConfirm() {
    if (!canConfirm || busy) return;
    onConfirm(isTimeout ? reasonCode : "OTHER", notes.trim());
  }

  function handleClose() {
    if (busy) return;
    setReasonCode("");
    setNotes("");
    onClose();
  }

  return (
    <div
      className="modal-backdrop"
      onClick={handleClose}
      role="dialog"
      aria-modal="true"
      aria-label={isTimeout ? "Reject on behalf" : "Deny override request"}
    >
      <div className="modal-card rc-modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-card-header">
          <h2 className="modal-card-title">
            {isTimeout ? "Reject on Behalf" : "Deny Override Request"}
          </h2>
          <button type="button" className="modal-close-btn" onClick={handleClose} aria-label="Close">
            <i className="ti ti-x" aria-hidden="true" />
          </button>
        </div>

        <div className="modal-card-body rc-modal-body">
          <p className="rc-modal-target">
            <i className="ti ti-file-text" aria-hidden="true" />
            {eventTitle}
          </p>

          {isTimeout ? (
            <div className="rc-field">
              <label className="rc-label" htmlFor="reject-reason">
                Rejection reason <span className="rc-required">*</span>
              </label>
              <select
                id="reject-reason"
                className="rc-select"
                value={reasonCode}
                onChange={(e) => setReasonCode(e.target.value)}
              >
                <option value="">Select a reason...</option>
                {REJECTION_CODES.map((c) => (
                  <option key={c.value} value={c.value}>{c.label}</option>
                ))}
              </select>
            </div>
          ) : (
            <p className="rc-modal-hint">
              Optionally explain the denial. The Contributor will be notified with
              this reason via UC-2.3 T10.
            </p>
          )}

          <div className="rc-field">
            <label className="rc-label" htmlFor="reject-notes">
              {isTimeout ? (
                <>Notes {notesRequired && <span className="rc-required">*</span>}</>
              ) : (
                "Denial reason (optional)"
              )}
            </label>
            <textarea
              id="reject-notes"
              className="rc-textarea"
              rows={3}
              placeholder={
                isTimeout
                  ? notesRequired
                    ? "Required when reason is 'Other'"
                    : "Additional context (optional)"
                  : "Why is this override request being denied?"
              }
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
            />
          </div>
        </div>

        <div className="modal-card-footer">
          <button type="button" className="btn-secondary" onClick={handleClose} disabled={busy}>
            Cancel
          </button>
          <button
            type="button"
            className="btn-danger"
            disabled={!canConfirm || busy}
            onClick={handleConfirm}
          >
            {busy ? (
              <><div className="spinner-ring spinner-ring-sm" /> Processing...</>
            ) : isTimeout ? (
              "Confirm Rejection"
            ) : (
              "Deny Override"
            )}
          </button>
        </div>
      </div>
    </div>
  );
}

import { useEffect, type ReactNode } from "react";
import { createPortal } from "react-dom";
import "../styles/confirm-modal.css";

export interface ConfirmOptions {
  title: string;
  message: ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  /** Shown on the confirm button while the action runs. */
  busyLabel?: string;
  /** Red confirm button for destructive actions. */
  dangerous?: boolean;
}

interface ConfirmModalProps extends ConfirmOptions {
  busy: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export default function ConfirmModal({
  title,
  message,
  confirmLabel = "Confirm",
  cancelLabel = "Cancel",
  busyLabel = "Working…",
  dangerous = false,
  busy,
  onConfirm,
  onCancel,
}: ConfirmModalProps) {
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape" && !busy) onCancel();
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [busy, onCancel]);

  return createPortal(
    <div
      className="cfm-overlay"
      role="dialog"
      aria-modal="true"
      aria-labelledby="cfm-title"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget && !busy) onCancel();
      }}
    >
      <div className="cfm-card">
        <h2 id="cfm-title" className="cfm-title">
          {title}
        </h2>
        <div className="cfm-message">{message}</div>
        <div className="cfm-actions">
          <button type="button" className="cfm-btn cfm-cancel" disabled={busy} onClick={onCancel}>
            {cancelLabel}
          </button>
          <button
            type="button"
            className={`cfm-btn cfm-ok${dangerous ? " is-danger" : ""}`}
            disabled={busy}
            autoFocus
            onClick={() => void onConfirm()}
          >
            {busy ? (
              <>
                {busyLabel}
                <span className="cfm-busy-dots" aria-hidden="true">
                  <span />
                  <span />
                  <span />
                </span>
              </>
            ) : (
              confirmLabel
            )}
          </button>
        </div>
      </div>
    </div>,
    document.body,
  );
}

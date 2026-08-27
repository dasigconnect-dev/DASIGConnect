import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
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

  return createPortal(
    <div
      className="val-modal-overlay"
      onClick={handleClose}
      role="dialog"
      aria-modal="true"
      aria-label="Retry with new schedule"
    >
      <div
        className="val-modal"
        style={{ maxWidth: "480px", width: "100%", padding: "24px" }}
        onClick={(e) => e.stopPropagation()}
      >
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "16px" }}>
          <h3 style={{ margin: 0, fontSize: "18px", fontWeight: 700, color: "var(--val-text)" }}>
            Retry With New Schedule
          </h3>
          <button
            type="button"
            className="val-collapse-btn"
            onClick={handleClose}
            aria-label="Close"
            style={{ color: "#64748b" }}
          >
            <i className="ti ti-x" aria-hidden="true" />
          </button>
        </div>

        <div style={{ display: "flex", flexDirection: "column", gap: "16px", marginBottom: "24px" }}>
          <p style={{ margin: 0, fontSize: "13.5px", color: "var(--val-muted)", lineHeight: 1.5 }}>
            Choose a publish time for <strong>"{item.eventTitle}"</strong> and it will
            be re-queued for the automated publisher. Guard rails are re-checked
            against the new slot.
          </p>

          <label style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
            <span style={{ fontSize: "12px", fontWeight: 700, color: "var(--val-text-2)" }}>
              New scheduled time <span style={{ color: "var(--val-red)" }}>*</span>
            </span>
            <input
              type="datetime-local"
              style={{
                width: "100%",
                padding: "8px 12px",
                borderRadius: "8px",
                border: "1px solid var(--val-border)",
                background: "var(--val-surface)",
                color: "var(--val-text)",
                font: "inherit",
                fontSize: "13.5px",
                boxSizing: "border-box",
              }}
              value={scheduledAt}
              onChange={(e) => setScheduledAt(e.target.value)}
            />
          </label>

          <label style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
            <span style={{ fontSize: "12px", fontWeight: 700, color: "var(--val-text-2)" }}>
              Override reason <span style={{ fontSize: "11px", fontWeight: 500, color: "var(--val-muted)" }}>(only needed if new slot violates guard rail)</span>
            </span>
            <input
              type="text"
              placeholder="Optional"
              style={{
                width: "100%",
                padding: "8px 12px",
                borderRadius: "8px",
                border: "1px solid var(--val-border)",
                background: "var(--val-surface)",
                color: "var(--val-text)",
                font: "inherit",
                fontSize: "13.5px",
                boxSizing: "border-box",
              }}
              value={overrideReason}
              onChange={(e) => setOverrideReason(e.target.value)}
            />
          </label>
        </div>

        <div style={{ display: "flex", justifyContent: "flex-end", gap: "10px" }}>
          <button
            type="button"
            className="val-btn val-btn-secondary"
            onClick={handleClose}
          >
            Cancel
          </button>
          <button
            type="button"
            className="val-btn val-btn-primary"
            disabled={busy || !scheduledAt}
            onClick={() =>
              onConfirmWithNewSchedule(new Date(scheduledAt).toISOString(), overrideReason || undefined)
            }
          >
            {busy && <i className="ti ti-loader-2 val-spin" />}
            {busy ? "Rescheduling..." : "Confirm New Schedule"}
          </button>
        </div>
      </div>
    </div>,
    document.body,
  );
}

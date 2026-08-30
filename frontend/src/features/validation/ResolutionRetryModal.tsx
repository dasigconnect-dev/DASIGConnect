import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import type { FailedPublication } from "../../api/resolutionApi";
import { validateGuardRails, type GuardRailResult } from "../../api/submissionApi";

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
  const [guardRails, setGuardRails] = useState<GuardRailResult | null>(null);
  const [checking, setChecking] = useState(false);

  useEffect(() => {
    if (!item) return;
    queueMicrotask(() => {
      setScheduledAt(toDatetimeLocal(item.scheduledAt));
      setOverrideReason("");
      setGuardRails(null);
    });
  }, [item]);

  // Re-check guard rails whenever the picked slot changes.
  useEffect(() => {
    if (!item || !scheduledAt) {
      const clear = window.setTimeout(() => {
        setGuardRails(null);
        setChecking(false);
      }, 0);
      return () => window.clearTimeout(clear);
    }
    const iso = new Date(scheduledAt).toISOString();
    const t = window.setTimeout(() => {
      setChecking(true);
      validateGuardRails(iso, item.institutionId)
        .then((res) => setGuardRails(res.data))
        .catch(() => setGuardRails(null))
        .finally(() => setChecking(false));
    }, 250);
    return () => window.clearTimeout(t);
  }, [item, scheduledAt]);

  if (!item) return null;

  const hardBlocked = (guardRails?.hardBlocks?.length ?? 0) > 0;
  const canConfirm =
    !busy && !!scheduledAt && (!hardBlocked || overrideReason.trim().length >= 10);

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

          {checking && (
            <p style={{ margin: 0, fontSize: "12px", color: "var(--val-muted)" }}>Checking the slot…</p>
          )}
          {!checking && hardBlocked && (
            <div className="val-edit-gr-block" style={{ fontSize: "12.5px" }}>
              <i className="ti ti-shield-x" aria-hidden /> {guardRails?.hardBlocks[0]?.message}
            </div>
          )}
          {!checking && !hardBlocked && (guardRails?.softWarnings?.length ?? 0) > 0 && (
            <div className="val-edit-gr-warn" style={{ fontSize: "12.5px" }}>
              {guardRails?.softWarnings[0]?.message}
            </div>
          )}

          {hardBlocked && (
            <label style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
              <span style={{ fontSize: "12px", fontWeight: 700, color: "var(--val-red)" }}>
                Override reason <span style={{ fontWeight: 500 }}>(required — this slot breaks a guard rail; the override is audited)</span>
              </span>
              <input
                type="text"
                placeholder="Explain why this time is necessary…"
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
          )}
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
            disabled={!canConfirm}
            onClick={() =>
              onConfirmWithNewSchedule(
                new Date(scheduledAt).toISOString(),
                hardBlocked ? overrideReason.trim() : undefined,
              )
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

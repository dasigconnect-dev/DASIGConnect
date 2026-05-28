import { useState } from "react";
import { createPortal } from "react-dom";
import type { CalendarEvent } from "../../api/calendarApi";

interface CalendarRescheduleModalProps {
  event: CalendarEvent;
  newStart: Date;
  onConfirm: (reason: string) => Promise<void>;
  onCancel: () => void;
}

function formatDatetime(date: Date) {
  return date.toLocaleString("en-PH", {
    weekday: "short",
    month: "short",
    day: "numeric",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
    hour12: true,
  });
}

export default function CalendarRescheduleModal({
  event,
  newStart,
  onConfirm,
  onCancel,
}: CalendarRescheduleModalProps) {
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [apiError, setApiError] = useState("");

  const reasonTrimmed = reason.trim();
  const MIN_REASON_LEN = 10;
  const canConfirm = reasonTrimmed.length >= MIN_REASON_LEN && !busy;
  const originalDate = new Date(event.scheduledAt);

  async function handleConfirm() {
    if (!canConfirm) return;
    setBusy(true);
    setApiError("");
    try {
      await onConfirm(reasonTrimmed);
    } catch (err: unknown) {
      const message =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setApiError(
        message ||
          "Reschedule failed — the new slot may be taken, or this submission cannot be moved in its current state.",
      );
      setBusy(false);
    }
  }

  return createPortal(
    <div
      className="modal-backdrop cal-reschedule-backdrop"
      role="dialog"
      aria-modal="true"
      aria-label="Confirm calendar reschedule"
    >
      <div className="modal-card cal-reschedule-modal">
        <div className="modal-card-header cal-reschedule-header">
          <div className="cal-reschedule-title-group">
            <span className="cal-reschedule-warn-icon" aria-hidden="true">
              <i className="ti ti-alert-triangle" />
            </span>
            <div>
              <h2 className="modal-card-title">Confirm Reschedule</h2>
              <p className="cal-reschedule-subtitle">
                {event.title ?? "Reserved publishing slot"} &middot; {event.institutionName}
              </p>
            </div>
          </div>
          <button
            type="button"
            className="modal-close-btn"
            onClick={onCancel}
            disabled={busy}
            aria-label="Cancel and revert to original time"
          >
            <i className="ti ti-x" />
          </button>
        </div>

        <div className="modal-card-body cal-reschedule-body">
          <div className="cal-reschedule-warning" role="alert">
            <i className="ti ti-info-circle" aria-hidden="true" />
            <p>
              Rescheduling moves the institution&rsquo;s publishing slot. The contributor may have
              planned around the original date. This change is written to the audit log and cannot
              be undone without another reschedule.
            </p>
          </div>

          <div className="cal-reschedule-diff" aria-label="Schedule change">
            <div className="cal-reschedule-diff-col">
              <span className="cal-reschedule-diff-label">From</span>
              <span className="cal-reschedule-diff-value">{formatDatetime(originalDate)}</span>
            </div>
            <i className="ti ti-arrow-right cal-reschedule-diff-arrow" aria-hidden="true" />
            <div className="cal-reschedule-diff-col cal-reschedule-diff-to">
              <span className="cal-reschedule-diff-label">To</span>
              <span className="cal-reschedule-diff-value">{formatDatetime(newStart)}</span>
            </div>
          </div>

          <div className="cal-reschedule-reason-group">
            <label htmlFor="cal-reschedule-reason" className="cal-reschedule-reason-label">
              <span className="cal-reschedule-reason-title">
                Reason for rescheduling
                <span className="cal-reschedule-required" aria-hidden="true"> *</span>
              </span>
              <span className="cal-reschedule-reason-hint">
                Required · minimum {MIN_REASON_LEN} characters · saved to the audit log
              </span>
            </label>
            <textarea
              id="cal-reschedule-reason"
              className="cal-reschedule-reason-input"
              rows={3}
              placeholder="Explain why this submission is being rescheduled…"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              disabled={busy}
              autoFocus
            />
            <div className="cal-reschedule-reason-footer">
              <span className="cal-reschedule-reason-hint-inline">
                {reasonTrimmed.length < MIN_REASON_LEN && reasonTrimmed.length > 0
                  ? `${MIN_REASON_LEN - reasonTrimmed.length} more character${MIN_REASON_LEN - reasonTrimmed.length === 1 ? "" : "s"} needed`
                  : reasonTrimmed.length >= MIN_REASON_LEN
                  ? "Reason accepted"
                  : ""}
              </span>
              <span
                className={`cal-reschedule-char-count${reasonTrimmed.length >= MIN_REASON_LEN ? " is-valid" : ""}`}
              >
                {reasonTrimmed.length} / {MIN_REASON_LEN} min
              </span>
            </div>
          </div>

          {apiError && (
            <div className="cal-reschedule-error" role="alert">
              <i className="ti ti-circle-x" aria-hidden="true" />
              {apiError}
            </div>
          )}
        </div>

        <div className="modal-card-footer cal-reschedule-footer">
          <button
            type="button"
            className="btn-secondary"
            onClick={onCancel}
            disabled={busy}
          >
            <i className="ti ti-arrow-back-up" aria-hidden="true" />
            Cancel — keep original time
          </button>
          <button
            type="button"
            className={`btn-danger cal-reschedule-confirm-btn${!canConfirm && !busy ? " cal-reschedule-confirm-locked" : ""}`}
            onClick={handleConfirm}
            disabled={!canConfirm}
            title={!canConfirm && !busy ? `Enter at least ${MIN_REASON_LEN} characters to confirm` : undefined}
          >
            {busy ? (
              <>
                <i className="ti ti-loader-2 cal-reschedule-spin" aria-hidden="true" />
                Rescheduling…
              </>
            ) : !canConfirm ? (
              <>
                <i className="ti ti-lock" aria-hidden="true" />
                Confirm Reschedule
              </>
            ) : (
              <>
                <i className="ti ti-calendar-event" aria-hidden="true" />
                Confirm Reschedule
              </>
            )}
          </button>
        </div>
      </div>
    </div>,
    document.body,
  );
}

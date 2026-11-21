import { useCallback, useEffect, useState } from "react";
import { createPortal } from "react-dom";
import {
  useIsMobileViewport,
  usePickerDismiss,
  usePopoverCollision,
} from "../hooks/usePopoverCollision";
import {
  cycleNumber,
  dateToInputValue,
  formatTimeDisplay,
  formatTimeParts,
  parseTimeValue,
  timePartsToValue,
} from "../utils";

function TimeStepper({
  label,
  value,
  onIncrement,
  onDecrement,
}: {
  label: string;
  value: string;
  onIncrement: () => void;
  onDecrement: () => void;
}) {
  return (
    <div className="sub-time-stepper">
      <button type="button" onClick={onIncrement} aria-label={`Increase ${label}`}>
        <i className="ti ti-chevron-up"></i>
      </button>
      <div>
        <span>{label}</span>
        <strong>{value}</strong>
      </div>
      <button type="button" onClick={onDecrement} aria-label={`Decrease ${label}`}>
        <i className="ti ti-chevron-down"></i>
      </button>
    </div>
  );
}

export function TimePickerField({
  value,
  placeholder = "Pick a time",
  readOnly,
  id,
  startHour,
  endHour,
  enforceRange,
  selectedDate,
  invalid,
  onChange,
}: {
  value: string;
  placeholder?: string;
  readOnly?: boolean;
  id?: string;
  startHour: number;
  endHour: number;
  enforceRange: boolean;
  selectedDate?: string;
  invalid?: boolean;
  onChange: (value: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const isMobile = useIsMobileViewport(900);
  const closePicker = useCallback(() => setOpen(false), []);
  const { rootRef, popoverRef, placement, maxHeight } =
    usePopoverCollision(open && !isMobile, 290);
  const [draft, setDraft] = useState(() => parseTimeValue(value));
  const displayValue = value ? formatTimeDisplay(value) : "";

  useEffect(() => {
    if (!open) return;
    const frame = window.requestAnimationFrame(() => setDraft(parseTimeValue(value)));
    return () => window.cancelAnimationFrame(frame);
  }, [open, value]);

  usePickerDismiss(open, closePicker, rootRef, popoverRef);

  function draftToMinutes(parts: { hour: number; minute: number; period: "AM" | "PM" }) {
    let h = parts.hour;
    if (parts.period === "PM" && h !== 12) h += 12;
    if (parts.period === "AM" && h === 12) h = 0;
    return h * 60 + parts.minute;
  }

  const now = new Date();
  const todayValue = dateToInputValue(now);
  const nowMinutes = now.getHours() * 60 + now.getMinutes();

  const draftMinutes = draftToMinutes(draft);
  const isPastTime = Boolean(
    selectedDate &&
      (selectedDate < todayValue ||
        (selectedDate === todayValue && draftMinutes <= nowMinutes)),
  );
  // Posting windows are start-inclusive and end-exclusive, matching the
  // backend's GR-H4 rule. When guard rails are off, any future time of day is valid.
  const isOutOfRange = enforceRange
    && (draftMinutes < startHour * 60 || draftMinutes >= endHour * 60);
  const isDraftBlocked = isPastTime || isOutOfRange;

  const valueMinutes = value ? draftToMinutes(parseTimeValue(value)) : null;
  const isValuePast = Boolean(
    value &&
      selectedDate &&
      valueMinutes !== null &&
      (selectedDate < todayValue ||
        (selectedDate === todayValue && valueMinutes <= nowMinutes)),
  );
  const isValueOutOfRange = Boolean(
    value &&
      enforceRange &&
      valueMinutes !== null &&
      (valueMinutes < startHour * 60 || valueMinutes >= endHour * 60),
  );
  const isTriggerInvalid = Boolean(invalid || isValuePast || isValueOutOfRange);

  function adjust(part: "hour" | "minute", offset: number) {
    setDraft((current) => {
      if (part === "hour") {
        return { ...current, hour: cycleNumber(current.hour + offset, 1, 12) };
      }
      return { ...current, minute: cycleNumber(current.minute + offset, 0, 59) };
    });
  }

  function applyTime() {
    if (isDraftBlocked) return;
    onChange(timePartsToValue(draft));
    setOpen(false);
  }

  const popoverContent = open && !readOnly && (
    <div
      className="sub-time-popover"
      ref={popoverRef}
      role="dialog"
      aria-modal={isMobile || undefined}
      aria-label={placeholder}
      style={isMobile ? undefined : { maxHeight }}
    >
      <div className="sub-time-head">
        <div>
          <div className="sub-time-title">Preferred time</div>
          <div className="sub-time-hint">Set the requested publish time</div>
        </div>
        <div className="sub-time-preview">{formatTimeParts(draft)}</div>
      </div>

      <div className="sub-time-controls">
        <TimeStepper
          label="Hour"
          value={String(draft.hour).padStart(2, "0")}
          onIncrement={() => adjust("hour", 1)}
          onDecrement={() => adjust("hour", -1)}
        />
        <TimeStepper
          label="Minute"
          value={String(draft.minute).padStart(2, "0")}
          onIncrement={() => adjust("minute", 1)}
          onDecrement={() => adjust("minute", -1)}
        />
        <div className="sub-time-period" aria-label="Meridiem">
          {(["AM", "PM"] as const).map((period) => (
            <button
              key={period}
              type="button"
              className={draft.period === period ? "active" : ""}
              onClick={() =>
                setDraft((current) => ({ ...current, period }))
              }
            >
              {period}
            </button>
          ))}
        </div>
      </div>

      <div className="sub-time-quick">
        {[0, 15, 30, 45].map((minute) => (
          <button
            key={minute}
            type="button"
            className={draft.minute === minute ? "active" : ""}
            onClick={() => setDraft((current) => ({ ...current, minute }))}
          >
            :{String(minute).padStart(2, "0")}
          </button>
        ))}
      </div>

      {isPastTime ? (
        <div className="sub-time-range-error" role="alert">
          <i className="ti ti-alert-triangle"></i>
          Selected time has already passed. Choose a future time.
        </div>
      ) : isOutOfRange ? (
        <div className="sub-time-range-error" role="alert">
          <i className="ti ti-alert-triangle"></i>
          Time must be from {startHour}:00 up to, but not including, {endHour}:00.
        </div>
      ) : null}

      <div className="sub-time-actions">
        <button
          type="button"
          onClick={() => {
            onChange("");
            setOpen(false);
          }}
        >
          Clear
        </button>
        <button type="button" onClick={applyTime} disabled={isDraftBlocked}>
          Apply Time
        </button>
      </div>
    </div>
  );

  return (
    <div
      className={`sub-time-field ${open ? "is-open" : ""} ${placement}`}
      ref={rootRef}
    >
      <button
        id={id}
        className={`sub-time-trigger ${open ? "open" : ""}${isTriggerInvalid ? " is-invalid" : ""}`}
        type="button"
        disabled={readOnly}
        aria-invalid={isTriggerInvalid || undefined}
        onClick={() => {
          if (!readOnly) setOpen((current) => !current);
        }}
        aria-haspopup="dialog"
        aria-expanded={open}
      >
        <span className={displayValue ? "" : "placeholder"}>
          {displayValue || placeholder}
        </span>
        <i className="ti ti-clock"></i>
      </button>

      {isMobile && popoverContent && typeof document !== "undefined"
        ? createPortal(
            <div
              className="sub-picker-modal-backdrop"
              onClick={(event) => {
                if (event.target === event.currentTarget) setOpen(false);
              }}
            >
              {popoverContent}
            </div>,
            document.body,
          )
        : popoverContent}
    </div>
  );
}




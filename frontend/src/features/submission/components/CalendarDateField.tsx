import { useCallback, useEffect, useMemo, useState } from "react";
import { createPortal } from "react-dom";
import {
  useIsMobileViewport,
  usePickerDismiss,
  usePopoverCollision,
} from "../hooks/usePopoverCollision";
import { buildCalendarDays, dateToInputValue, formatLongDate, parseInputDate } from "../utils";

export function CalendarDateField({
  value,
  placeholder,
  readOnly,
  minValue,
  id,
  invalid,
  onChange,
}: {
  value: string;
  placeholder: string;
  readOnly?: boolean;
  minValue?: string;
  id?: string;
  invalid?: boolean;
  onChange: (value: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const isMobile = useIsMobileViewport(900);
  const closePicker = useCallback(() => setOpen(false), []);
  const { rootRef, popoverRef, placement, maxHeight } =
    usePopoverCollision(open && !isMobile, 375);
  const selectedDate = useMemo(() => parseInputDate(value), [value]);
  const [visibleMonth, setVisibleMonth] = useState(() => {
    const base = selectedDate || new Date();
    return new Date(base.getFullYear(), base.getMonth(), 1);
  });

  usePickerDismiss(open, closePicker, rootRef, popoverRef);

  useEffect(() => {
    if (selectedDate) {
      queueMicrotask(() => {
        setVisibleMonth(
          new Date(selectedDate.getFullYear(), selectedDate.getMonth(), 1),
        );
      });
    }
  }, [selectedDate]);

  const days = useMemo(() => buildCalendarDays(visibleMonth), [visibleMonth]);
  const todayValue = dateToInputValue(new Date());
  const displayValue = selectedDate ? formatLongDate(value) : "";

  function moveMonth(offset: number) {
    setVisibleMonth(
      (current) => new Date(current.getFullYear(), current.getMonth() + offset, 1),
    );
  }

  function selectDate(next: string) {
    onChange(next);
    setOpen(false);
  }

  const popoverContent = open && !readOnly && (
    <div
      className="sub-date-popover"
      ref={popoverRef}
      role="dialog"
      aria-modal={isMobile || undefined}
      aria-label={placeholder}
      style={isMobile ? undefined : { maxHeight }}
    >
      <div className="sub-date-popover-head">
        <button
          type="button"
          className="sub-date-nav"
          onClick={() => moveMonth(-1)}
          aria-label="Previous month"
        >
          <i className="ti ti-chevron-left"></i>
        </button>
        <div>
          <div className="sub-date-month">
            {visibleMonth.toLocaleDateString(undefined, {
              month: "long",
              year: "numeric",
            })}
          </div>
          <div className="sub-date-hint">Pick a calendar date</div>
        </div>
        <button
          type="button"
          className="sub-date-nav"
          onClick={() => moveMonth(1)}
          aria-label="Next month"
        >
          <i className="ti ti-chevron-right"></i>
        </button>
      </div>

      <div className="sub-date-weekdays" aria-hidden="true">
        {["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"].map((day) => (
          <span key={day}>{day}</span>
        ))}
      </div>

      <div className="sub-date-grid">
        {days.map((day) => {
          const isPast = minValue ? day.value < minValue : false;
          return (
            <button
              key={day.value}
              className={[
                "sub-date-day",
                day.inMonth ? "" : "muted",
                day.value === value ? "selected" : "",
                day.value === todayValue ? "today" : "",
                isPast ? "past" : "",
              ].join(" ")}
              type="button"
              disabled={isPast}
              onClick={() => selectDate(day.value)}
            >
              {day.date.getDate()}
            </button>
          );
        })}
      </div>

      <div className="sub-date-actions">
        <button
          type="button"
          onClick={() => {
            onChange("");
            setOpen(false);
          }}
        >
          Clear
        </button>
        <button type="button" onClick={() => selectDate(todayValue)}>
          Today
        </button>
      </div>
    </div>
  );

  return (
    <div
      className={`sub-date-field ${open ? "is-open" : ""} ${placement}`}
      ref={rootRef}
    >
      <button
        id={id}
        className={`sub-date-trigger ${open ? "open" : ""}${invalid ? " is-invalid" : ""}`}
        type="button"
        disabled={readOnly}
        aria-invalid={invalid || undefined}
        onClick={() => {
          if (!readOnly) setOpen((current) => !current);
        }}
        aria-haspopup="dialog"
        aria-expanded={open}
      >
        <span className={displayValue ? "" : "placeholder"}>
          {displayValue || placeholder}
        </span>
        <i className="ti ti-calendar-event"></i>
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

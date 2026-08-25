import { useEffect, useMemo, useState } from "react";
import { usePopoverCollision } from "../hooks/usePopoverCollision";
import { buildCalendarDays, dateToInputValue, formatLongDate, parseInputDate } from "../utils";

export function CalendarDateField({
  value,
  placeholder,
  readOnly,
  minValue,
  onChange,
}: {
  value: string;
  placeholder: string;
  readOnly?: boolean;
  minValue?: string;
  onChange: (value: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const { rootRef, popoverRef, placement, maxHeight } =
    usePopoverCollision(open);
  const selectedDate = parseInputDate(value);
  const [visibleMonth, setVisibleMonth] = useState(() => {
    const base = selectedDate || new Date();
    return new Date(base.getFullYear(), base.getMonth(), 1);
  });

  useEffect(() => {
    if (!open) return;
    function handlePointerDown(event: PointerEvent) {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    }
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") setOpen(false);
    }
    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [open, rootRef]);

  useEffect(() => {
    if (selectedDate) {
      setVisibleMonth(
        new Date(selectedDate.getFullYear(), selectedDate.getMonth(), 1),
      );
    }
  }, [value, selectedDate]);

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

  return (
    <div
      className={`sub-date-field ${open ? "is-open" : ""} ${placement}`}
      ref={rootRef}
    >
      <button
        className={`sub-date-trigger ${open ? "open" : ""}`}
        type="button"
        disabled={readOnly}
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

      {open && !readOnly && (
        <div
          className="sub-date-popover"
          ref={popoverRef}
          role="dialog"
          aria-label={placeholder}
          style={{ maxHeight }}
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
      )}
    </div>
  );
}

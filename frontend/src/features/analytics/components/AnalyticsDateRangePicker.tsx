import { useEffect, useId, useMemo, useRef, useState } from "react";
import type { AnalyticsRange } from "../../../api/analyticsApi";
import { addDays, customRange, formatRangeLabel, rangeBounds, toIsoDate } from "../analyticsUtils";

/** Matches the backend's MAX_CUSTOM_RANGE_DAYS (inclusive span). */
const MAX_RANGE_DAYS = 366;
const WEEKDAYS = ["S", "M", "T", "W", "T", "F", "S"];

interface Preset {
  label: string;
  range: (today: Date) => AnalyticsRange;
}

// Rolling presets reuse the backend's named ranges; calendar-anchored ones
// ("this week", "this month") and 60 days are sent as explicit date ranges.
const PRESETS: Preset[] = [
  { label: "Today", range: (today) => customRange(today, today) },
  { label: "This week", range: (today) => customRange(addDays(today, -today.getDay()), today) },
  { label: "This month", range: (today) => customRange(new Date(today.getFullYear(), today.getMonth(), 1), today) },
  { label: "Past 7 days", range: () => "7d" },
  { label: "Past 30 days", range: () => "30d" },
  { label: "Past 60 days", range: (today) => customRange(addDays(today, -60), today) },
  { label: "Past 90 days", range: () => "90d" },
  { label: "Year to date", range: () => "ytd" },
];

interface Props {
  value: AnalyticsRange;
  onChange: (range: AnalyticsRange) => void;
  /** What "Reset" returns to. */
  defaultRange: AnalyticsRange;
}

export default function AnalyticsDateRangePicker({ value, onChange, defaultRange }: Props) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement | null>(null);
  const triggerRef = useRef<HTMLButtonElement | null>(null);
  const popoverId = useId();
  const today = useMemo(() => startOfDay(new Date()), []);

  useEffect(() => {
    if (!open) return;
    const onPointerDown = (event: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) setOpen(false);
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setOpen(false);
        triggerRef.current?.focus();
      }
    };
    document.addEventListener("mousedown", onPointerDown);
    window.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("mousedown", onPointerDown);
      window.removeEventListener("keydown", onKeyDown);
    };
  }, [open]);

  function choose(range: AnalyticsRange) {
    onChange(range);
    setOpen(false);
    triggerRef.current?.focus();
  }

  return (
    <div className={`adr${open ? " is-open" : ""}`} ref={rootRef}>
      <button
        ref={triggerRef}
        type="button"
        className="adr-trigger"
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-controls={open ? popoverId : undefined}
        aria-label={`Reporting period: ${formatRangeLabel(value, today)}. Change period`}
        onClick={() => setOpen((current) => !current)}
      >
        <i className="ti ti-calendar" aria-hidden />
        <span>{formatRangeLabel(value, today)}</span>
        <i className="ti ti-chevron-down adr-chevron" aria-hidden />
      </button>

      {open && (
        <RangePopover
          id={popoverId}
          value={value}
          today={today}
          defaultRange={defaultRange}
          onChoose={choose}
        />
      )}
    </div>
  );
}

function RangePopover({
  id,
  value,
  today,
  defaultRange,
  onChoose,
}: {
  id: string;
  value: AnalyticsRange;
  today: Date;
  defaultRange: AnalyticsRange;
  onChoose: (range: AnalyticsRange) => void;
}) {
  const selected = rangeBounds(value, today);
  const [month, setMonth] = useState(() => new Date(selected.to.getFullYear(), selected.to.getMonth(), 1));
  const [anchor, setAnchor] = useState<Date | null>(null);
  const [hovered, setHovered] = useState<Date | null>(null);

  const days = useMemo(() => monthGrid(month), [month]);
  const isCurrentMonth = month.getFullYear() === today.getFullYear() && month.getMonth() === today.getMonth();
  const activePreset = PRESETS.find((preset) => preset.range(today) === value);

  // While picking, preview anchor → hovered day; otherwise show the applied range.
  const [previewFrom, previewTo] = anchor
    ? orderPair(anchor, hovered ?? anchor)
    : [selected.from, selected.to];

  function pickDay(day: Date) {
    if (!anchor) {
      setAnchor(day);
      return;
    }
    onChoose(customRange(anchor, day));
  }

  function isDisabled(day: Date) {
    if (day > today) return true;
    return anchor !== null && Math.abs(daysBetween(anchor, day)) + 1 > MAX_RANGE_DAYS;
  }

  const monthLabel = month.toLocaleDateString(undefined, { month: "long", year: "numeric" });

  return (
    <div className="adr-popover" id={id} role="dialog" aria-label="Choose reporting period">
      <div className="adr-body">
        <ul className="adr-presets" aria-label="Quick ranges">
          {PRESETS.map((preset) => (
            <li key={preset.label}>
              <button
                type="button"
                className={preset === activePreset ? "is-active" : undefined}
                aria-pressed={preset === activePreset}
                onClick={() => onChoose(preset.range(today))}
              >
                {preset.label}
              </button>
            </li>
          ))}
        </ul>

        <div className="adr-calendar">
          <div className="adr-month" aria-live="polite">
            {monthLabel}
          </div>
          <div className="adr-grid" role="group" aria-label={monthLabel} onMouseLeave={() => setHovered(null)}>
            {WEEKDAYS.map((weekday, index) => (
              <span key={`${weekday}-${index}`} className="adr-weekday" aria-hidden>
                {weekday}
              </span>
            ))}
            {days.map((day, index) =>
              day ? (
                <button
                  key={toIsoDate(day)}
                  type="button"
                  className={dayClass(day, previewFrom, previewTo, today)}
                  disabled={isDisabled(day)}
                  aria-pressed={day >= previewFrom && day <= previewTo}
                  aria-label={day.toLocaleDateString(undefined, { weekday: "long", month: "long", day: "numeric", year: "numeric" })}
                  onClick={() => pickDay(day)}
                  onMouseEnter={() => setHovered(day)}
                  onFocus={() => setHovered(day)}
                >
                  {day.getDate()}
                </button>
              ) : (
                <span key={`blank-${index}`} aria-hidden />
              ),
            )}
          </div>
          <p className="adr-hint" aria-live="polite">
            {anchor ? "Now pick the end date." : "Pick a start date, or choose a quick range."}
          </p>
        </div>
      </div>

      <div className="adr-footer">
        <button
          type="button"
          className="adr-reset"
          onClick={() => onChoose(defaultRange)}
          disabled={value === defaultRange && !anchor}
        >
          Reset
        </button>
        <div className="adr-nav">
          <button
            type="button"
            aria-label="Previous month"
            onClick={() => setMonth((current) => new Date(current.getFullYear(), current.getMonth() - 1, 1))}
          >
            <i className="ti ti-chevron-left" aria-hidden />
          </button>
          <button
            type="button"
            aria-label="Next month"
            disabled={isCurrentMonth}
            onClick={() => setMonth((current) => new Date(current.getFullYear(), current.getMonth() + 1, 1))}
          >
            <i className="ti ti-chevron-right" aria-hidden />
          </button>
        </div>
      </div>
    </div>
  );
}

function dayClass(day: Date, from: Date, to: Date, today: Date) {
  const classes = ["adr-day"];
  const inRange = day >= from && day <= to;
  if (inRange) classes.push("is-in-range");
  if (sameDay(day, from)) classes.push("is-start");
  if (sameDay(day, to)) classes.push("is-end");
  if (sameDay(day, today)) classes.push("is-today");
  return classes.join(" ");
}

/** Month cells, Sunday-first, with nulls padding the first week. */
function monthGrid(month: Date): Array<Date | null> {
  const first = new Date(month.getFullYear(), month.getMonth(), 1);
  const daysInMonth = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate();
  const cells: Array<Date | null> = Array.from({ length: first.getDay() }, () => null);
  for (let day = 1; day <= daysInMonth; day += 1) {
    cells.push(new Date(month.getFullYear(), month.getMonth(), day));
  }
  return cells;
}

function startOfDay(date: Date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

function sameDay(a: Date, b: Date) {
  return toIsoDate(a) === toIsoDate(b);
}

function daysBetween(a: Date, b: Date) {
  return Math.round((b.getTime() - a.getTime()) / 86_400_000);
}

function orderPair(a: Date, b: Date): [Date, Date] {
  return a <= b ? [a, b] : [b, a];
}

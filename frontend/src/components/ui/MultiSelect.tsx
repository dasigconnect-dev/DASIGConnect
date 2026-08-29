import { useEffect, useRef, useState } from "react";
import "../../styles/branded-select.css";

export interface MultiSelectOption {
  value: string;
  label: string;
}

interface MultiSelectProps {
  values: string[];
  options: MultiSelectOption[];
  onChange: (values: string[]) => void;
  placeholder?: string;
  ariaLabel?: string;
  className?: string;
}

export default function MultiSelect({
  values,
  options,
  onChange,
  placeholder = "All",
  ariaLabel,
  className = "",
}: MultiSelectProps) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement | null>(null);

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
  }, [open]);

  function toggle(value: string) {
    onChange(
      values.includes(value)
        ? values.filter((v) => v !== value)
        : [...values, value],
    );
  }

  const selectedLabels = options
    .filter((option) => values.includes(option.value))
    .map((option) => option.label);
  const summary =
    selectedLabels.length === 0
      ? ""
      : `${selectedLabels.length} selected`;

  return (
    <div
      className={`dc-select dc-multi ${open ? "is-open" : ""} drop-down ${className}`.trim()}
      ref={rootRef}
    >
      <button
        type="button"
        className={`dc-select-trigger ${open ? "open" : ""}`}
        onClick={() => setOpen((v) => !v)}
        aria-label={ariaLabel}
        aria-haspopup="listbox"
        aria-expanded={open}
      >
        <span className={`dc-multi-text ${summary ? "" : "placeholder"}`}>
          {summary || placeholder}
        </span>
        <span className="dc-multi-controls">
          {values.length > 0 && (
            <span
              role="button"
              className="dc-select-clear"
              onClick={(e) => {
                e.stopPropagation();
                onChange([]);
              }}
              aria-label={`Clear ${ariaLabel ?? "filter"}`}
              title="Clear"
            >
              <i className="ti ti-x" aria-hidden="true" />
            </span>
          )}
          <i className="ti ti-chevron-down dc-multi-chevron" aria-hidden="true" />
        </span>
      </button>

      {open && (
        <div className="dc-select-popover" role="listbox" aria-multiselectable="true">
          {options.length === 0 && (
            <div className="dc-select-empty">No options available.</div>
          )}
          {options.map((option) => {
            const isSelected = values.includes(option.value);
            return (
              <button
                key={`${option.value}-${option.label}`}
                type="button"
                className={`dc-select-option ${isSelected ? "selected" : ""}`}
                role="option"
                aria-selected={isSelected}
                onClick={() => toggle(option.value)}
              >
                <span>{option.label}</span>
                {isSelected && <i className="ti ti-check" aria-hidden="true" />}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}

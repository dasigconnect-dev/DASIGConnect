import { useEffect, useRef, useState } from "react";
import "../../styles/branded-select.css";

export interface BrandedSelectOption {
  value: string;
  label: string;
  disabled?: boolean;
}

interface BrandedSelectProps {
  value: string;
  options: BrandedSelectOption[];
  onChange: (value: string) => void;
  placeholder?: string;
  hint?: string;
  disabled?: boolean;
  loading?: boolean;
  ariaLabel?: string;
  className?: string;
}

export default function BrandedSelect({
  value,
  options,
  onChange,
  placeholder = "Select an option",
  hint,
  disabled,
  loading,
  ariaLabel,
  className = "",
}: BrandedSelectProps) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement | null>(null);
  const popoverRef = useRef<HTMLDivElement | null>(null);
  const [placement, setPlacement] = useState<"drop-down" | "drop-up">("drop-down");
  const [maxHeight, setMaxHeight] = useState(360);
  const selected = options.find((option) => option.value === value);
  const selectedLabel = selected?.label ?? "";
  const isDisabled = Boolean(disabled || loading);

  useEffect(() => {
    if (!open) return;

    function handlePointerDown(event: PointerEvent) {
      if (!rootRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setOpen(false);
      }
    }

    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [open]);

  useEffect(() => {
    if (!open) return;
    let frame = 0;
    const viewportGap = 18;
    const triggerGap = 8;
    const minComfortHeight = 220;

    function updatePlacement() {
      window.cancelAnimationFrame(frame);
      frame = window.requestAnimationFrame(() => {
        const root = rootRef.current;
        const popover = popoverRef.current;
        if (!root || !popover) return;

        const rootRect = root.getBoundingClientRect();
        const naturalHeight = popover.scrollHeight;
        const spaceBelow =
          window.innerHeight - rootRect.bottom - triggerGap - viewportGap;
        const spaceAbove = rootRect.top - triggerGap - viewportGap;
        const shouldDropUp =
          spaceBelow < Math.min(naturalHeight, minComfortHeight) &&
          spaceAbove > spaceBelow;
        const availableSpace = shouldDropUp ? spaceAbove : spaceBelow;

        setPlacement(shouldDropUp ? "drop-up" : "drop-down");
        setMaxHeight(Math.max(180, Math.min(naturalHeight, availableSpace)));
      });
    }

    updatePlacement();
    window.addEventListener("resize", updatePlacement);
    window.addEventListener("scroll", updatePlacement, true);
    return () => {
      window.cancelAnimationFrame(frame);
      window.removeEventListener("resize", updatePlacement);
      window.removeEventListener("scroll", updatePlacement, true);
    };
  }, [open, options.length]);

  function handleSelect(nextValue: string) {
    onChange(nextValue);
    setOpen(false);
  }

  return (
    <div
      className={`dc-select ${open ? "is-open" : ""} ${placement} ${className}`.trim()}
      ref={rootRef}
    >
      <button
        type="button"
        className={`dc-select-trigger ${open ? "open" : ""}`}
        disabled={isDisabled}
        onClick={() => {
          if (!isDisabled) setOpen((current) => !current);
        }}
        aria-label={ariaLabel}
        aria-haspopup="listbox"
        aria-expanded={open}
      >
        <span className={selectedLabel ? "" : "placeholder"}>
          {selectedLabel || placeholder}
        </span>
        <i
          className={`ti ${loading ? "ti-loader-2 dc-select-spin" : "ti-chevron-down"}`}
          aria-hidden="true"
        />
      </button>

      {open && !isDisabled && (
        <div
          className="dc-select-popover"
          ref={popoverRef}
          role="listbox"
          style={{ maxHeight }}
        >
          {hint && <div className="dc-select-hint">{hint}</div>}
          {options.length === 0 && (
            <div className="dc-select-empty">No options available.</div>
          )}
          {options.map((option) => {
            const isDefaultOption = option.value === "";
            const isSelected = value === option.value;

            return (
              <button
                key={`${option.value}-${option.label}`}
                type="button"
                className={`dc-select-option ${isDefaultOption ? "default-option" : ""} ${isSelected && !isDefaultOption ? "selected" : ""}`}
                role="option"
                aria-selected={isSelected}
                disabled={option.disabled}
                onClick={() => handleSelect(option.value)}
              >
                <span>{option.label}</span>
                {isSelected && !isDefaultOption && (
                  <i className="ti ti-check" aria-hidden="true" />
                )}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}

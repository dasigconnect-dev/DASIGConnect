import { useEffect, useId, useLayoutEffect, useRef, useState, type CSSProperties } from "react";
import "./InfoTip.css";

const VIEWPORT_MARGIN = 8;

/**
 * An ⓘ "toggletip": a real button that shows a styled explanation bubble.
 * - Tap / click toggles it (pinned open until tapped again, Escape, or a tap
 *   elsewhere) — the only trigger that works on touch.
 * - With a mouse, hovering previews it; keyboard focus opens it too.
 * The bubble sits above the icon, flips below when there's no room, and is
 * nudged sideways to stay inside the viewport.
 */
export default function InfoTip({
  text,
  label,
  className,
}: {
  text: string;
  /** What the tip is about, for the button's accessible name ("About {label}"). */
  label: string;
  className?: string;
}) {
  const [pinned, setPinned] = useState(false);
  const [hovered, setHovered] = useState(false);
  const [focused, setFocused] = useState(false);
  const open = pinned || hovered || focused;

  const rootRef = useRef<HTMLSpanElement | null>(null);
  const bubbleRef = useRef<HTMLSpanElement | null>(null);
  const [placement, setPlacement] = useState<"top" | "bottom">("top");
  const [shift, setShift] = useState(0);
  const bubbleId = useId();

  useLayoutEffect(() => {
    if (!open) return;
    const root = rootRef.current;
    const bubble = bubbleRef.current;
    if (!root || !bubble) return;
    const anchor = root.getBoundingClientRect();
    const box = bubble.getBoundingClientRect();
    const spaceAbove = anchor.top;
    const spaceBelow = window.innerHeight - anchor.bottom;
    setPlacement(spaceAbove < box.height + 16 && spaceBelow > spaceAbove ? "bottom" : "top");
    // Centre on the icon, then clamp inside the viewport.
    const centre = anchor.left + anchor.width / 2;
    const left = centre - box.width / 2;
    const maxLeft = window.innerWidth - VIEWPORT_MARGIN - box.width;
    setShift(Math.min(Math.max(left, VIEWPORT_MARGIN), Math.max(maxLeft, VIEWPORT_MARGIN)) - left);
  }, [open, text]);

  useEffect(() => {
    if (!open) return;
    const close = () => {
      setPinned(false);
      setHovered(false);
      setFocused(false);
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") close();
    };
    const onPointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) close();
    };
    window.addEventListener("keydown", onKeyDown);
    document.addEventListener("pointerdown", onPointerDown);
    return () => {
      window.removeEventListener("keydown", onKeyDown);
      document.removeEventListener("pointerdown", onPointerDown);
    };
  }, [open]);

  return (
    <span
      ref={rootRef}
      className={`infotip${open ? " is-open" : ""}${className ? ` ${className}` : ""}`}
      onPointerEnter={(event) => {
        if (event.pointerType === "mouse") setHovered(true);
      }}
      onPointerLeave={(event) => {
        if (event.pointerType === "mouse") setHovered(false);
      }}
    >
      <button
        type="button"
        className="infotip-btn"
        aria-label={`About ${label}`}
        aria-expanded={open}
        aria-describedby={open ? bubbleId : undefined}
        onClick={(event) => {
          event.preventDefault();
          event.stopPropagation();
          // A hover-opened tip pins on click; a pinned one closes.
          if (pinned) {
            setPinned(false);
            setHovered(false);
            setFocused(false);
          } else {
            setPinned(true);
          }
        }}
        onFocus={(event) => {
          // Keyboard focus only; a mouse click is handled by onClick.
          if (event.currentTarget.matches(":focus-visible")) setFocused(true);
        }}
        onBlur={() => setFocused(false)}
      >
        <i className="ti ti-info-circle" aria-hidden="true" />
      </button>
      {open && (
        <span
          ref={bubbleRef}
          id={bubbleId}
          role="tooltip"
          className={`infotip-bubble is-${placement}`}
          style={{ "--infotip-shift": `${shift}px` } as CSSProperties}
        >
          {text}
        </span>
      )}
    </span>
  );
}

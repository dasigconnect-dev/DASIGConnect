import { useEffect, useRef, useState } from "react";

function getScrollParent(node: HTMLElement | null): HTMLElement | Window {
  if (!node) return window;
  let parent: HTMLElement | null = node.parentElement;
  while (parent && parent !== document.body && parent !== document.documentElement) {
    const style = window.getComputedStyle(parent);
    const overflowY = style.overflowY;
    if (
      (overflowY === "auto" || overflowY === "scroll") &&
      parent.scrollHeight > parent.clientHeight + 10
    ) {
      return parent;
    }
    parent = parent.parentElement;
  }
  return window;
}

export function usePopoverCollision(open: boolean, minRequiredHeight = 370) {
  const rootRef = useRef<HTMLDivElement | null>(null);
  const popoverRef = useRef<HTMLDivElement | null>(null);
  const [placement, setPlacement] = useState<"drop-down" | "drop-up">("drop-down");
  const [maxHeight, setMaxHeight] = useState(440);
  const hasAutoScrolledRef = useRef(false);

  useEffect(() => {
    if (!open) {
      hasAutoScrolledRef.current = false;
      return;
    }

    let frame = 0;
    const viewportGap = 16;
    const triggerGap = 8;
    const topNavbarOffset = 58;

    function updatePlacement() {
      window.cancelAnimationFrame(frame);
      frame = window.requestAnimationFrame(() => {
        const root = rootRef.current;
        const popover = popoverRef.current;
        if (!root) return;

        const rootRect = root.getBoundingClientRect();
        const naturalHeight = popover?.scrollHeight || minRequiredHeight;
        const vh = window.innerHeight;

        const spaceBelow = vh - rootRect.bottom - triggerGap - viewportGap;
        const spaceAbove = rootRect.top - triggerGap - viewportGap - topNavbarOffset;

        // Intelligent placement:
        // 1. If it fits completely below with natural height, drop down
        // 2. If it does not fit below, but fits completely above, drop UP
        // 3. Otherwise, pick whichever side provides more space
        let shouldDropUp = false;
        if (spaceBelow >= naturalHeight) {
          shouldDropUp = false;
        } else if (spaceAbove >= naturalHeight) {
          shouldDropUp = true;
        } else {
          shouldDropUp = spaceAbove > spaceBelow;
        }

        setPlacement(shouldDropUp ? "drop-up" : "drop-down");

        const targetMaxHeight = Math.max(minRequiredHeight, naturalHeight);
        const screenBoundedHeight = Math.max(300, vh - topNavbarOffset - viewportGap * 2);
        setMaxHeight(Math.min(targetMaxHeight, screenBoundedHeight));

        // Auto-scroll on mobile/touch viewports so opened popover is 100% visible
        if (!hasAutoScrolledRef.current && popover) {
          const scrollParent = getScrollParent(root);

          if (!shouldDropUp) {
            const popoverBottom = rootRect.bottom + triggerGap + naturalHeight;
            const overflow = popoverBottom - (vh - viewportGap);
            if (overflow > 0) {
              if (scrollParent === window) {
                window.scrollBy({ top: overflow + 16, behavior: "smooth" });
              } else if (scrollParent instanceof HTMLElement) {
                scrollParent.scrollBy({ top: overflow + 16, behavior: "smooth" });
              }
              hasAutoScrolledRef.current = true;
            }
          } else {
            const popoverTop = rootRect.top - triggerGap - naturalHeight;
            const overflow = (topNavbarOffset + viewportGap) - popoverTop;
            if (overflow > 0) {
              if (scrollParent === window) {
                window.scrollBy({ top: -(overflow + 16), behavior: "smooth" });
              } else if (scrollParent instanceof HTMLElement) {
                scrollParent.scrollBy({ top: -(overflow + 16), behavior: "smooth" });
              }
              hasAutoScrolledRef.current = true;
            }
          }
        }
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
  }, [open, minRequiredHeight]);

  return { rootRef, popoverRef, placement, maxHeight };
}


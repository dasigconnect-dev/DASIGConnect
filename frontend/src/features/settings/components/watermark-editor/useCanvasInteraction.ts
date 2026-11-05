import { useCallback, useEffect, useRef } from "react";
import type { PointerEvent as ReactPointerEvent, RefObject } from "react";
import type { AspectRatioType, WatermarkElement } from "../../../../types/watermark.types";
import {
  aspectOf,
  applyBox,
  computeBox,
  moveBox,
  resizeBox,
  type ElementBox,
  type ResizeHandle,
} from "../../../../components/watermark/watermarkLayout";

const DRAG_THRESHOLD_PX = 3;

interface DragSession {
  id: string;
  handle: ResizeHandle | null; // null = move
  startX: number;
  startY: number;
  startBox: ElementBox;
  moved: boolean;
}

interface Options {
  frameRef: RefObject<HTMLDivElement | null>;
  ratio: AspectRatioType;
  elements: WatermarkElement[];
  onChange: (elements: WatermarkElement[]) => void;
  disabled: boolean;
}

/**
 * Pointer-driven move / resize of canvas elements. Every edit is written back
 * through `applyBox`, so a drag only changes the position of the CURRENT format.
 */
export function useCanvasInteraction({ frameRef, ratio, elements, onChange, disabled }: Options) {
  const sessionRef = useRef<DragSession | null>(null);

  // Latest values for the window-level listeners (registered once).
  const latest = useRef({ ratio, elements, onChange });
  useEffect(() => {
    latest.current = { ratio, elements, onChange };
  });

  const begin = useCallback(
    (event: ReactPointerEvent, id: string, handle: ResizeHandle | null) => {
      if (disabled) return;
      const target = latest.current.elements.find((el) => el.id === id);
      if (!target) return;
      event.stopPropagation();
      sessionRef.current = {
        id,
        handle,
        startX: event.clientX,
        startY: event.clientY,
        startBox: computeBox(target, latest.current.ratio),
        moved: false,
      };
    },
    [disabled],
  );

  useEffect(() => {
    function handleMove(event: PointerEvent) {
      const session = sessionRef.current;
      const frame = frameRef.current;
      if (!session || !frame) return;

      const dxPx = event.clientX - session.startX;
      const dyPx = event.clientY - session.startY;
      if (!session.moved && Math.hypot(dxPx, dyPx) < DRAG_THRESHOLD_PX) return;
      session.moved = true;

      const rect = frame.getBoundingClientRect();
      if (rect.width === 0 || rect.height === 0) return;
      const dx = (dxPx / rect.width) * 100;
      const dy = (dyPx / rect.height) * 100;

      const nextBox = session.handle
        ? resizeBox(session.startBox, session.handle, dx, dy)
        : moveBox(session.startBox, dx, dy);

      const { ratio: currentRatio, elements: currentElements, onChange: emit } = latest.current;
      emit(
        currentElements.map((el) =>
          el.id === session.id ? applyBox(el, currentRatio, nextBox, aspectOf(currentRatio)) : el,
        ),
      );
    }

    function handleUp() {
      sessionRef.current = null;
    }

    window.addEventListener("pointermove", handleMove);
    window.addEventListener("pointerup", handleUp);
    window.addEventListener("pointercancel", handleUp);
    return () => {
      window.removeEventListener("pointermove", handleMove);
      window.removeEventListener("pointerup", handleUp);
      window.removeEventListener("pointercancel", handleUp);
    };
  }, [frameRef]);

  return {
    beginMove: (event: ReactPointerEvent, id: string) => begin(event, id, null),
    beginResize: (event: ReactPointerEvent, id: string, handle: ResizeHandle) => begin(event, id, handle),
  };
}

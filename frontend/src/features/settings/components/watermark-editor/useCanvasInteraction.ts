import { useCallback, useEffect, useRef } from "react";
import type { PointerEvent as ReactPointerEvent, RefObject } from "react";
import type { AspectRatioType, WatermarkAnchor, WatermarkElement } from "../../../../types/watermark.types";
import {
  applyBox,
  computeBox,
  normalizeWatermarkAnchor,
  resizeBox,
  type ElementBox,
  type ResizeHandle,
} from "../../../../components/watermark/watermarkLayout";

const DRAG_THRESHOLD_PX = 2;

interface ResizeSession {
  id: string;
  handle: ResizeHandle;
  anchor?: WatermarkAnchor;
  startX: number;
  startY: number;
  startBox: ElementBox;
  moved: boolean;
}

interface Options {
  frameRef: RefObject<HTMLDivElement | null>;
  ratio: AspectRatioType;
  canvasAspect?: number;
  elements: WatermarkElement[];
  onChange: (elements: WatermarkElement[]) => void;
  disabled: boolean;
}

/**
 * Pointer-driven resize of anchored canvas elements. Elements stay locked to
 * their selected edge or corner anchor while their dimensions are resized.
 */
export function useCanvasInteraction({ frameRef, ratio, canvasAspect = 1, elements, onChange, disabled }: Options) {
  const sessionRef = useRef<ResizeSession | null>(null);

  // Latest values for the window-level listeners (registered once).
  const latest = useRef({ ratio, canvasAspect, elements, onChange });
  useEffect(() => {
    latest.current = { ratio, canvasAspect, elements, onChange };
  });

  const beginResize = useCallback(
    (event: ReactPointerEvent, id: string, handle: ResizeHandle) => {
      if (disabled) return;
      const target = latest.current.elements.find((el) => el.id === id);
      if (!target) return;
      event.stopPropagation();
      const normalized = normalizeWatermarkAnchor(target);
      sessionRef.current = {
        id,
        handle,
        anchor: normalized.anchor,
        startX: event.clientX,
        startY: event.clientY,
        startBox: computeBox(target, latest.current.ratio, latest.current.canvasAspect),
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

      const nextBox = resizeBox(session.startBox, session.handle, dx, dy, session.anchor);

      const {
        ratio: currentRatio,
        canvasAspect: currentAspect,
        elements: currentElements,
        onChange: emit,
      } = latest.current;
      emit(
        currentElements.map((el) =>
          el.id === session.id ? applyBox(el, currentRatio, nextBox, currentAspect) : el,
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
    beginResize,
  };
}

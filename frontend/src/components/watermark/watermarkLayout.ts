import type {
  AspectRatioType,
  ElementRatioConfig,
  WatermarkAnchor,
  WatermarkElement,
} from "../../types/watermark.types";

export const DEFAULT_ASPECT_RATIO: AspectRatioType = "1:1";

export const MAX_WATERMARK_ELEMENTS = 3;
export const FULL_WIDTH_PERCENT = 100;

const MIN_BOX_WIDTH = 3;
const MIN_BOX_HEIGHT = 1;
export const DEFAULT_ANCHOR_INSET_PERCENT = 3;
const SNAP_MARGIN = DEFAULT_ANCHOR_INSET_PERCENT;

export interface ElementBox {
  left: number;
  top: number;
  width: number;
  height: number;
}

export type ResizeHandle = "n" | "s" | "e" | "w" | "ne" | "nw" | "se" | "sw";
export type HorizontalAnchor = "left" | "center" | "right";
export type VerticalAnchor = "top" | "middle" | "bottom";
export type StackDirection = "up" | "down" | "top" | "bottom";

export const round1 = (value: number) => Math.round(value * 10) / 10;
export const clamp = (value: number, min: number, max: number) =>
  Math.max(min, Math.min(max, value));

export function normalizeWatermarkAnchor(
  el: Pick<WatermarkElement, "type" | "anchor" | "xPercent" | "yPercent" | "widthPercent" | "heightPercent">,
): Pick<WatermarkElement, "anchor" | "xPercent" | "yPercent"> {
  const isShape = el.type === "shape";
  const anchor = el.anchor;
  if (isShape) {
    if (anchor === "top" || anchor === "bottom") {
      return { anchor, xPercent: 0, yPercent: el.yPercent };
    }
    if (anchor === "top-left" || anchor === "top-right" || anchor === "bottom-left" || anchor === "bottom-right") {
      return {
        anchor: anchor.startsWith("bottom") ? "bottom" : "top",
        xPercent: 0,
        yPercent: el.yPercent,
      };
    }
    const bottom = el.yPercent + el.heightPercent / 2 >= 50;
    return {
      anchor: bottom ? "bottom" : "top",
      xPercent: 0,
      yPercent: bottom ? Math.max(0, 100 - el.yPercent - el.heightPercent) : el.yPercent,
    };
  }

  if (anchor === "top-left" || anchor === "top-right" || anchor === "bottom-left" || anchor === "bottom-right") {
    return { anchor, xPercent: el.xPercent, yPercent: el.yPercent };
  }
  const right = el.xPercent + el.widthPercent / 2 >= 50;
  const bottom = el.yPercent + el.heightPercent / 2 >= 50;
  return {
    anchor: bottom ? (right ? "bottom-right" : "bottom-left") : (right ? "top-right" : "top-left"),
    xPercent: right ? Math.max(0, 100 - el.xPercent - el.widthPercent) : el.xPercent,
    yPercent: bottom ? Math.max(0, 100 - el.yPercent - el.heightPercent) : el.yPercent,
  };
}

/**
 * Resolves square-reference geometry from the shared element fields. Legacy
 * format overrides remain readable only for non-geometric settings.
 */
export function resolveRatioConfig(
  el: WatermarkElement,
  _ratio: AspectRatioType,
  defaultOrder = 0,
): ElementRatioConfig {
  const saved = el.ratios?.["1:1"];
  return {
    xPercent: el.xPercent,
    yPercent: el.yPercent,
    widthPercent: el.widthPercent,
    heightPercent: el.heightPercent,
    opacity: el.opacity ?? 1.0,
    order: el.order ?? saved?.order ?? defaultOrder,
    hidden: el.hidden ?? saved?.hidden ?? false,
    textColor: el.textColor ?? saved?.textColor,
    textShadow: el.textShadow ?? saved?.textShadow ?? true,
    fillColor: el.fillColor ?? saved?.fillColor,
    strokeColor: el.strokeColor ?? saved?.strokeColor,
    fontSizePercent: el.fontSizePercent ?? saved?.fontSizePercent,
    fontWeight: el.fontWeight ?? saved?.fontWeight,
    fontStyle: el.fontStyle ?? saved?.fontStyle,
    fontFamily: el.fontFamily ?? saved?.fontFamily,
  };
}

/**
 * Updates an element's configuration for a specific ratio without overwriting
 * the configurations of other ratios.
 */
export function updateElementForRatio(
  el: WatermarkElement,
  ratio: AspectRatioType,
  updates: Partial<ElementRatioConfig>,
  defaultOrder = 0,
): WatermarkElement {
  const current = resolveRatioConfig(el, ratio, defaultOrder);
  const nextConfig: ElementRatioConfig = {
    ...current,
    ...updates,
  };
  const baseElement = { ...el };
  delete baseElement.ratios;
  return {
    ...baseElement,
    ...nextConfig,
  };
}

/**
 * Returns elements sorted by their layer order in the given ratio
 * (lowest order = back, highest order = front).
 */
export function getSortedElementsByRatio(
  elements: WatermarkElement[],
  ratio: AspectRatioType,
): WatermarkElement[] {
  return [...elements].sort((a, b) => {
    const orderA = resolveRatioConfig(a, ratio, elements.indexOf(a)).order ?? elements.indexOf(a);
    const orderB = resolveRatioConfig(b, ratio, elements.indexOf(b)).order ?? elements.indexOf(b);
    return orderA - orderB;
  });
}

/**
 * Reorders elements for a specific ratio using up/down/top/bottom commands.
 */
export function reorderElementsByRatio(
  elements: WatermarkElement[],
  ratio: AspectRatioType,
  id: string,
  direction: StackDirection,
): WatermarkElement[] {
  const sorted = getSortedElementsByRatio(elements, ratio);
  const index = sorted.findIndex((el) => el.id === id);
  if (index === -1) return elements;

  const nextSorted = [...sorted];
  const last = nextSorted.length - 1;

  if (direction === "up" && index < last) {
    [nextSorted[index], nextSorted[index + 1]] = [nextSorted[index + 1], nextSorted[index]];
  } else if (direction === "down" && index > 0) {
    [nextSorted[index], nextSorted[index - 1]] = [nextSorted[index - 1], nextSorted[index]];
  } else if (direction === "top" && index < last) {
    nextSorted.push(nextSorted.splice(index, 1)[0]);
  } else if (direction === "bottom" && index > 0) {
    nextSorted.unshift(nextSorted.splice(index, 1)[0]);
  } else {
    return elements;
  }

  // Assign explicit orders (0..last) for this ratio
  const orderMap = new Map<string, number>();
  nextSorted.forEach((item, i) => orderMap.set(item.id, i));

  return elements.map((el) => {
    const newOrder = orderMap.get(el.id);
    if (newOrder !== undefined) {
      return updateElementForRatio(el, ratio, { order: newOrder }, newOrder);
    }
    return el;
  });
}

/**
 * Reorders elements for a specific ratio using drag-and-drop.
 */
export function reorderElementsByDrag(
  elements: WatermarkElement[],
  ratio: AspectRatioType,
  draggedId: string,
  targetId: string,
): WatermarkElement[] {
  if (draggedId === targetId) return elements;
  const sorted = getSortedElementsByRatio(elements, ratio);
  const fromIndex = sorted.findIndex((el) => el.id === draggedId);
  const toIndex = sorted.findIndex((el) => el.id === targetId);
  if (fromIndex === -1 || toIndex === -1 || fromIndex === toIndex) return elements;

  const nextSorted = [...sorted];
  const [moved] = nextSorted.splice(fromIndex, 1);
  nextSorted.splice(toIndex, 0, moved);

  const orderMap = new Map<string, number>();
  nextSorted.forEach((item, i) => orderMap.set(item.id, i));

  return elements.map((el) => {
    const newOrder = orderMap.get(el.id);
    if (newOrder !== undefined) {
      return updateElementForRatio(el, ratio, { order: newOrder }, newOrder);
    }
    return el;
  });
}

/**
 * On-canvas rectangle of an element for the given ratio.
 */
export function computeBox(
  el: WatermarkElement,
  ratio: AspectRatioType,
  canvasAspect = 1,
): ElementBox {
  const cfg = resolveRatioConfig(el, ratio);
  const normalized = normalizeWatermarkAnchor({
    ...el,
    xPercent: cfg.xPercent,
    yPercent: cfg.yPercent,
    widthPercent: cfg.widthPercent,
    heightPercent: cfg.heightPercent,
  });
  const baseWidth = Math.min(1, 1 / canvasAspect);
  const baseHeight = Math.min(1, canvasAspect);
  const isFullWidthBar = el.type === "shape" && (normalized.anchor === "top" || normalized.anchor === "bottom");
  const insetPercent = isFullWidthBar ? 0 : DEFAULT_ANCHOR_INSET_PERCENT;
  const xOffset = clamp(insetPercent * baseWidth, 0, 45);
  const yOffset = clamp(insetPercent * baseHeight, 0, 45);
  const maxWidth = isFullWidthBar ? 100 : Math.max(MIN_BOX_WIDTH, 100 - xOffset * 2);
  const maxHeight = isFullWidthBar ? 100 : Math.max(MIN_BOX_HEIGHT, 100 - yOffset * 2);
  const width = isFullWidthBar ? 100 : clamp(cfg.widthPercent * baseWidth, MIN_BOX_WIDTH, maxWidth);
  const height = clamp(cfg.heightPercent * baseHeight, MIN_BOX_HEIGHT, maxHeight);
  const isRightAnchored = normalized.anchor === "top-right" || normalized.anchor === "bottom-right";
  const isBottomAnchored = normalized.anchor === "bottom" || normalized.anchor === "bottom-left" || normalized.anchor === "bottom-right";
  const isTopAnchored = normalized.anchor === "top" || normalized.anchor === "top-left" || normalized.anchor === "top-right";
  return {
    left: isFullWidthBar
      ? 0
      : isRightAnchored
        ? 100 - xOffset - width
        : normalized.anchor === "top" || normalized.anchor === "bottom"
          ? (100 - width) / 2
          : xOffset,
    top: isBottomAnchored ? 100 - yOffset - height : isTopAnchored ? yOffset : (100 - height) / 2,
    width,
    height,
  };
}

/**
 * Writes an edited box back to the element while keeping its anchor inset fixed.
 */
export function applyBox(
  el: WatermarkElement,
  ratio: AspectRatioType,
  box: ElementBox,
  canvasAspect = 1,
): WatermarkElement {
  const config = resolveRatioConfig(el, ratio);
  const normalized = normalizeWatermarkAnchor({
    ...el,
    xPercent: config.xPercent,
    yPercent: config.yPercent,
    widthPercent: config.widthPercent,
    heightPercent: config.heightPercent,
  });
  const baseWidth = Math.min(1, 1 / canvasAspect);
  const baseHeight = Math.min(1, canvasAspect);
  const isFullWidthBar = el.type === "shape" && (normalized.anchor === "top" || normalized.anchor === "bottom");
  const updated = updateElementForRatio(el, ratio, {
    xPercent: isFullWidthBar ? 0 : DEFAULT_ANCHOR_INSET_PERCENT,
    yPercent: isFullWidthBar ? 0 : DEFAULT_ANCHOR_INSET_PERCENT,
    widthPercent: isFullWidthBar ? 100 : round1(box.width / baseWidth),
    heightPercent: round1(box.height / baseHeight),
  });
  return { ...updated, anchor: normalized.anchor };
}

export function moveBox(box: ElementBox, dx: number, dy: number): ElementBox {
  return {
    ...box,
    left: clamp(box.left + dx, 0, 100 - box.width),
    top: clamp(box.top + dy, 0, 100 - box.height),
  };
}

export function resizeBox(
  box: ElementBox,
  handle: ResizeHandle,
  dx: number,
  dy: number,
  anchor?: WatermarkAnchor,
): ElementBox {
  let left = box.left;
  let top = box.top;
  let right = box.left + box.width;
  let bottom = box.top + box.height;

  const isRightAnchored = anchor === "top-right" || anchor === "bottom-right";
  const isLeftAnchored = anchor === "top-left" || anchor === "bottom-left";
  const isTopAnchored = anchor === "top" || anchor === "top-left" || anchor === "top-right";
  const isBottomAnchored = anchor === "bottom" || anchor === "bottom-left" || anchor === "bottom-right";

  if (anchor === "top" || anchor === "bottom") {
    left = 0;
    right = 100;
  } else if (isLeftAnchored) {
    const minRight = left + MIN_BOX_WIDTH;
    const maxRight = 100 - left;
    if (handle.includes("e")) right = clamp(right + dx, minRight, maxRight);
    else if (handle.includes("w")) right = clamp(right - dx, minRight, maxRight);
  } else if (isRightAnchored) {
    const minLeft = 100 - right;
    const maxLeft = right - MIN_BOX_WIDTH;
    if (handle.includes("w")) left = clamp(left + dx, minLeft, maxLeft);
    else if (handle.includes("e")) left = clamp(left - dx, minLeft, maxLeft);
  } else {
    if (handle.includes("e")) right = clamp(right + dx, left + MIN_BOX_WIDTH, 100);
    if (handle.includes("w")) left = clamp(left + dx, 0, right - MIN_BOX_WIDTH);
  }

  if (isTopAnchored) {
    const minBottom = top + MIN_BOX_HEIGHT;
    const maxBottom = anchor === "top" ? 100 : 100 - top;
    if (handle.includes("s")) bottom = clamp(bottom + dy, minBottom, maxBottom);
    else if (handle.includes("n")) bottom = clamp(bottom - dy, minBottom, maxBottom);
  } else if (isBottomAnchored) {
    const minTop = anchor === "bottom" ? 0 : 100 - bottom;
    const maxTop = bottom - MIN_BOX_HEIGHT;
    if (handle.includes("n")) top = clamp(top + dy, minTop, maxTop);
    else if (handle.includes("s")) top = clamp(top - dy, minTop, maxTop);
  } else {
    if (handle.includes("s")) bottom = clamp(bottom + dy, top + MIN_BOX_HEIGHT, 100);
    if (handle.includes("n")) top = clamp(top + dy, 0, bottom - MIN_BOX_HEIGHT);
  }

  return { left, top, width: right - left, height: bottom - top };
}

export function snapBox(box: ElementBox, horizontal: HorizontalAnchor, vertical: VerticalAnchor): ElementBox {
  const left =
    horizontal === "left"
      ? SNAP_MARGIN
      : horizontal === "right"
        ? 100 - box.width - SNAP_MARGIN
        : (100 - box.width) / 2;
  const top =
    vertical === "top"
      ? SNAP_MARGIN
      : vertical === "bottom"
        ? 100 - box.height - SNAP_MARGIN
        : (100 - box.height) / 2;
  return {
    ...box,
    left: clamp(left, 0, 100 - box.width),
    top: clamp(top, 0, 100 - box.height),
  };
}

export function fitTextHeight(text: string, fontSizePercent: number): number {
  const lines = Math.max(1, text.split("\n").length);
  return round1(lines * fontSizePercent * 1.25 + 0.6);
}

import type {
  AspectRatioType,
  ElementRatioConfig,
  WatermarkElement,
} from "../../types/watermark.types";

export const MAX_WATERMARK_ELEMENTS = 3;
export const DEFAULT_ASPECT_RATIO: AspectRatioType = "1:1";

export interface AspectRatioOption {
  id: AspectRatioType;
  label: string;
  aspect: number; // width / height
}

export const ASPECT_RATIO_OPTIONS: readonly AspectRatioOption[] = [
  { id: "1:1", label: "Square", aspect: 1 },
  { id: "4:3", label: "Standard", aspect: 4 / 3 },
  { id: "16:9", label: "Landscape", aspect: 16 / 9 },
];

/** Widest element that still fits the widest format (16:9). Fills any canvas. */
export const FULL_WIDTH_PERCENT = 177.8;

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

export function aspectOf(ratio: AspectRatioType): number {
  return ASPECT_RATIO_OPTIONS.find((option) => option.id === ratio)?.aspect ?? 1;
}

/** Picks the format whose proportions are closest to a real image's. */
export function nearestAspectRatio(aspect: number): AspectRatioType {
  if (!Number.isFinite(aspect) || aspect <= 0) return DEFAULT_ASPECT_RATIO;
  let best = ASPECT_RATIO_OPTIONS[0];
  for (const option of ASPECT_RATIO_OPTIONS) {
    if (Math.abs(Math.log(aspect / option.aspect)) < Math.abs(Math.log(aspect / best.aspect))) {
      best = option;
    }
  }
  return best.id;
}

/**
 * Resolves the configuration for a given ratio. If this ratio has been edited,
 * its saved configuration is returned. If untouched, it copies from the initial
 * 1:1 baseline with positioning adapted to keep the same pixel position and size.
 */
export function resolveRatioConfig(
  el: WatermarkElement,
  ratio: AspectRatioType,
  defaultOrder = 0,
): ElementRatioConfig {
  const saved = el.ratios?.[ratio];
  if (saved) {
    return {
      xPercent:
        saved.xPercent ??
        (ratio === "1:1" || (el.anchor != null && el.anchor !== "freeform")
          ? el.xPercent
          : el.xPercent / aspectOf(ratio)),
      yPercent: saved.yPercent ?? el.yPercent,
      widthPercent: saved.widthPercent ?? el.widthPercent,
      heightPercent: saved.heightPercent ?? el.heightPercent,
      opacity: saved.opacity ?? el.opacity ?? 1.0,
      order: saved.order ?? defaultOrder,
      hidden: saved.hidden ?? el.hidden ?? false,
      textColor: saved.textColor ?? el.textColor,
      fillColor: saved.fillColor ?? el.fillColor,
      strokeColor: saved.strokeColor ?? el.strokeColor,
      fontSizePercent: saved.fontSizePercent ?? el.fontSizePercent,
      fontWeight: saved.fontWeight ?? el.fontWeight,
      fontStyle: saved.fontStyle ?? el.fontStyle,
      fontFamily: saved.fontFamily ?? el.fontFamily,
    };
  }

  // Untouched ratio: adapt initial 1:1 coordinates
  const aspect = aspectOf(ratio);
  const isCornerAnchored = el.anchor != null && el.anchor !== "freeform";
  const initialX = ratio === "1:1" || isCornerAnchored ? el.xPercent : el.xPercent / aspect;

  return {
    xPercent: round1(initialX),
    yPercent: round1(el.yPercent),
    widthPercent: round1(el.widthPercent),
    heightPercent: round1(el.heightPercent),
    opacity: el.opacity ?? 1.0,
    order: defaultOrder,
    hidden: el.hidden ?? false,
    textColor: el.textColor,
    fillColor: el.fillColor,
    strokeColor: el.strokeColor,
    fontSizePercent: el.fontSizePercent,
    fontWeight: el.fontWeight,
    fontStyle: el.fontStyle,
    fontFamily: el.fontFamily,
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

  // If 1:1 is edited, also sync base properties for legacy readers
  const baseUpdates =
    ratio === "1:1"
      ? {
          ...(updates.xPercent !== undefined ? { xPercent: round1(updates.xPercent) } : {}),
          ...(updates.yPercent !== undefined ? { yPercent: round1(updates.yPercent) } : {}),
          ...(updates.widthPercent !== undefined ? { widthPercent: round1(updates.widthPercent) } : {}),
          ...(updates.heightPercent !== undefined ? { heightPercent: round1(updates.heightPercent) } : {}),
          ...(updates.opacity !== undefined ? { opacity: updates.opacity } : {}),
          ...(updates.hidden !== undefined ? { hidden: updates.hidden } : {}),
          ...(updates.textColor !== undefined ? { textColor: updates.textColor } : {}),
          ...(updates.fillColor !== undefined ? { fillColor: updates.fillColor } : {}),
          ...(updates.strokeColor !== undefined ? { strokeColor: updates.strokeColor } : {}),
          ...(updates.fontSizePercent !== undefined ? { fontSizePercent: updates.fontSizePercent } : {}),
          ...(updates.fontWeight !== undefined ? { fontWeight: updates.fontWeight } : {}),
          ...(updates.fontStyle !== undefined ? { fontStyle: updates.fontStyle } : {}),
          ...(updates.fontFamily !== undefined ? { fontFamily: updates.fontFamily } : {}),
        }
      : {};

  return {
    ...el,
    ...baseUpdates,
    ratios: {
      ...el.ratios,
      [ratio]: nextConfig,
    },
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
  canvasAspect: number = aspectOf(ratio),
): ElementBox {
  const cfg = resolveRatioConfig(el, ratio);
  const width = Math.min(cfg.widthPercent / canvasAspect, 100);
  const height = Math.min(cfg.heightPercent, 100);
  const xOffset = clamp(cfg.xPercent, 0, 100 - width);
  const yOffset = clamp(cfg.yPercent, 0, 100 - height);
  const isRightAnchored = el.anchor === "top-right" || el.anchor === "bottom-right";
  const isBottomAnchored = el.anchor === "bottom-left" || el.anchor === "bottom-right";
  return {
    left: isRightAnchored ? 100 - xOffset - width : xOffset,
    top: isBottomAnchored ? 100 - yOffset - height : yOffset,
    width,
    height,
  };
}

/**
 * Writes an edited box back to the element for ONE ratio only.
 */
export function applyBox(
  el: WatermarkElement,
  ratio: AspectRatioType,
  box: ElementBox,
  canvasAspect: number = aspectOf(ratio),
): WatermarkElement {
  const isRightAnchored = el.anchor === "top-right" || el.anchor === "bottom-right";
  const isBottomAnchored = el.anchor === "bottom-left" || el.anchor === "bottom-right";
  return updateElementForRatio(el, ratio, {
    xPercent: round1(isRightAnchored ? 100 - box.left - box.width : box.left),
    yPercent: round1(isBottomAnchored ? 100 - box.top - box.height : box.top),
    widthPercent: round1(box.width * canvasAspect),
    heightPercent: round1(box.height),
  });
}

export function moveBox(box: ElementBox, dx: number, dy: number): ElementBox {
  return {
    ...box,
    left: clamp(box.left + dx, 0, 100 - box.width),
    top: clamp(box.top + dy, 0, 100 - box.height),
  };
}

export function resizeBox(box: ElementBox, handle: ResizeHandle, dx: number, dy: number): ElementBox {
  let left = box.left;
  let top = box.top;
  let right = box.left + box.width;
  let bottom = box.top + box.height;

  if (handle.includes("e")) right = clamp(right + dx, left + MIN_BOX_WIDTH, 100);
  if (handle.includes("w")) left = clamp(left + dx, 0, right - MIN_BOX_WIDTH);
  if (handle.includes("s")) bottom = clamp(bottom + dy, top + MIN_BOX_HEIGHT, 100);
  if (handle.includes("n")) top = clamp(top + dy, 0, bottom - MIN_BOX_HEIGHT);

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

import type { WatermarkElement } from "../../../../types/watermark.types";
import { FULL_WIDTH_PERCENT, fitTextHeight } from "../../../../components/watermark/watermarkLayout";

export type ShapeVariant = NonNullable<WatermarkElement["shapeType"]>;

const newId = () => `el_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`;

export function createImageElement(imageUrl: string): WatermarkElement {
  return {
    id: newId(),
    type: "image",
    xPercent: 78,
    yPercent: 78,
    widthPercent: 18,
    heightPercent: 18,
    opacity: 0.9,
    imageUrl,
  };
}

export function createTextElement(text: string): WatermarkElement {
  const fontSizePercent = 3.2;
  return {
    id: newId(),
    type: "text",
    xPercent: 27,
    yPercent: 88,
    widthPercent: 46,
    heightPercent: fitTextHeight(text, fontSizePercent),
    opacity: 0.95,
    text,
    textColor: "#FFFFFF",
    fontSizePercent,
    fontWeight: "700",
    fontStyle: "normal",
    fontFamily: "inherit",
  };
}

const SHAPE_DEFAULTS: Record<ShapeVariant, Pick<WatermarkElement, "yPercent" | "heightPercent" | "opacity" | "fillColor" | "strokeColor">> = {
  rectangle: { yPercent: 86, heightPercent: 14, opacity: 0.5, fillColor: "rgba(15, 23, 42, 0.75)", strokeColor: "transparent" },
  gradient: { yPercent: 70, heightPercent: 30, opacity: 0.85, fillColor: "#07112a", strokeColor: "transparent" },
  line: { yPercent: 86, heightPercent: 1, opacity: 0.9, fillColor: "transparent", strokeColor: "#FFFFFF" },
};

/** Full-width bars: wide enough to span every format. */
export function createShapeElement(shapeType: ShapeVariant): WatermarkElement {
  return {
    id: newId(),
    type: "shape",
    shapeType,
    xPercent: 0,
    widthPercent: FULL_WIDTH_PERCENT,
    ...SHAPE_DEFAULTS[shapeType],
  };
}

/** Patch that turns an existing shape into another variant with sensible proportions. */
export function switchShapeVariant(el: WatermarkElement, variant: ShapeVariant): Partial<WatermarkElement> {
  const wasLine = el.shapeType === "line";
  const willBeLine = variant === "line";
  const defaults = SHAPE_DEFAULTS[variant];
  const patch: Partial<WatermarkElement> = { shapeType: variant };

  if (wasLine !== willBeLine || (variant === "gradient" && el.heightPercent < 20)) {
    patch.heightPercent = defaults.heightPercent;
  }
  if (!el.fillColor || el.fillColor === "transparent") patch.fillColor = defaults.fillColor;
  if (!el.strokeColor || el.strokeColor === "transparent") patch.strokeColor = defaults.strokeColor;
  return patch;
}

export function describeElement(el: WatermarkElement): string {
  if (el.type === "image") return "Image";
  if (el.type === "text") return el.text?.split("\n")[0] || "Text";
  if (el.shapeType === "line") return "Line";
  if (el.shapeType === "gradient") return "Gradient";
  return "Ribbon";
}

export function elementIcon(el: WatermarkElement): string {
  if (el.type === "image") return "ti-photo";
  if (el.type === "text") return "ti-typography";
  return "ti-rectangle";
}

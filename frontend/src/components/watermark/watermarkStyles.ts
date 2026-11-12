import type { CSSProperties } from "react";
import type { ElementRatioConfig, WatermarkElement } from "../../types/watermark.types";
import { normalizeWatermarkAnchor } from "./watermarkLayout";

export const DEFAULT_FONT_SIZE_PERCENT = 3.2;

/** Typography scales against the shorter side of the image, like backend rendering. */
export function textElementStyle(el: WatermarkElement, cfg?: ElementRatioConfig | null): CSSProperties {
  const fontSize = cfg?.fontSizePercent ?? el.fontSizePercent ?? DEFAULT_FONT_SIZE_PERCENT;
  const textColor = cfg?.textColor ?? el.textColor ?? "#ffffff";
  const fontFamily = cfg?.fontFamily ?? el.fontFamily ?? "inherit";
  const fontWeight = cfg?.fontWeight ?? el.fontWeight;
  const fontStyle = cfg?.fontStyle ?? el.fontStyle;
  const anchor = normalizeWatermarkAnchor(el).anchor;
  const isRight = anchor === "top-right" || anchor === "bottom-right";
  const isLeft = anchor === "top-left" || anchor === "bottom-left";

  return {
    color: textColor,
    fontSize: `calc(${fontSize} * min(1cqi, 1cqb))`,
    fontFamily,
    fontWeight: fontWeight === "400" ? 400 : 700,
    fontStyle: fontStyle === "italic" ? "italic" : "normal",
    textAlign: isRight ? "right" : isLeft ? "left" : "center",
    justifyContent: isRight ? "flex-end" : isLeft ? "flex-start" : "center",
  };
}

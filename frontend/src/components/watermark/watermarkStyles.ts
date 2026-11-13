import type { CSSProperties } from "react";
import type { ElementRatioConfig, WatermarkElement } from "../../types/watermark.types";

export const DEFAULT_FONT_SIZE_PERCENT = 3.2;

/** Typography of a text element. Font size is a percent of the canvas height (cqh). */
export function textElementStyle(el: WatermarkElement, cfg?: ElementRatioConfig | null): CSSProperties {
  const fontSize = cfg?.fontSizePercent ?? el.fontSizePercent ?? DEFAULT_FONT_SIZE_PERCENT;
  const textColor = cfg?.textColor ?? el.textColor ?? "#ffffff";
  const fontFamily = cfg?.fontFamily ?? el.fontFamily ?? "inherit";
  const fontWeight = cfg?.fontWeight ?? el.fontWeight;
  const fontStyle = cfg?.fontStyle ?? el.fontStyle;

  return {
    color: textColor,
    fontSize: `calc(${fontSize} * 1cqh)`,
    fontFamily,
    fontWeight: fontWeight === "400" ? 400 : 700,
    fontStyle: fontStyle === "italic" ? "italic" : "normal",
  };
}

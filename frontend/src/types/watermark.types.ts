export type WatermarkElementType = "image" | "text" | "shape";

/** Canvas formats the studio can lay out. 1:1 is the reference format. */
export type AspectRatioType = "1:1" | "4:3" | "16:9";

/**
 * Configuration of an element specific to a format (1:1, 4:3, or 16:9).
 * Layering (order), positioning, sizes/height, transparency, and colors
 * can each be customized independently per format.
 */
export interface ElementRatioConfig {
  xPercent: number; // % of canvas width
  yPercent: number; // % of canvas height
  widthPercent: number; // % of canvas height (or width in square)
  heightPercent: number; // % of canvas height
  opacity?: number; // 0.05 - 1.0
  order?: number; // Layer stack position in this ratio (0 = back, 1 = middle, 2 = front)
  hidden?: boolean;

  // Style overrides for this ratio
  textColor?: string;
  fillColor?: string;
  strokeColor?: string;
  fontSizePercent?: number;
  fontWeight?: string;
  fontStyle?: "normal" | "italic";
  fontFamily?: string;
}

export interface WatermarkElement {
  id: string;
  type: WatermarkElementType;

  // Base fallback properties (also represent default 1:1 configuration)
  xPercent: number;
  yPercent: number;
  widthPercent: number;
  heightPercent: number;
  opacity: number;
  hidden?: boolean;

  // Text specific
  text?: string;
  textColor?: string;
  fontSizePercent?: number;
  fontWeight?: string;
  fontStyle?: "normal" | "italic";
  fontFamily?: string;

  // Image specific
  imageUrl?: string;

  // Shape specific
  shapeType?: "rectangle" | "line" | "gradient";
  fillColor?: string;
  strokeColor?: string;

  // Per-ratio configurations: "1:1" | "4:3" | "16:9"
  ratios?: Partial<Record<AspectRatioType, ElementRatioConfig>>;
}

export interface WatermarkConfiguration {
  id: string | null;
  enabled: boolean;
  elements: WatermarkElement[];
  updatedAt: string | null;
  updatedBy: string | null;
}

export interface WatermarkConfigurationRequest {
  enabled: boolean;
  elements: WatermarkElement[];
}

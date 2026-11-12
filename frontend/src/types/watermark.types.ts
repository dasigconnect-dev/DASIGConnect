export type WatermarkElementType = "image" | "text" | "shape";
export type WatermarkAnchor =
  | "freeform"
  | "top"
  | "bottom"
  | "top-left"
  | "top-right"
  | "bottom-left"
  | "bottom-right";

/** Legacy format keys retained only to read existing saved configurations. */
export type AspectRatioType = "1:1" | "4:3" | "16:9";

/**
 * Legacy format-specific settings. New layouts ignore per-format geometry
 * and use a single square-reference configuration for every image shape.
 */
export interface ElementRatioConfig {
  xPercent: number; // % of the square reference side from the selected edge
  yPercent: number; // % of the square reference side from the selected edge
  widthPercent: number; // % of the square reference side
  heightPercent: number; // % of the square reference side
  opacity?: number; // 0.05 - 1.0
  order?: number; // Layer stack position (0 = back)
  hidden?: boolean;

  // Retained style overrides from the retired format editor.
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
  /** Shared edge anchor; x/y percentages are insets from the selected edge. */
  anchor?: WatermarkAnchor;

  // Base properties use square-reference percentages for every image size.
  xPercent: number;
  yPercent: number;
  widthPercent: number;
  heightPercent: number;
  opacity: number;
  order?: number;
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

  // Legacy format configurations retained for saved-data compatibility.
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

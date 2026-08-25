export type WatermarkElementType = "image" | "text" | "shape";

export type AspectRatioType = "1:1" | "4:5" | "16:9";

export interface WatermarkElement {
  id: string;
  type: WatermarkElementType;
  xPercent: number; // 0 - 100
  yPercent: number; // 0 - 100
  widthPercent: number; // 0 - 100
  heightPercent: number; // 0 - 100
  opacity: number; // 0.05 - 1.0

  // Text specific
  text?: string;
  textColor?: string;
  fontSizePercent?: number; // e.g. 2.5 - 10%
  fontWeight?: string; // "400" | "600" | "700" | "800"
  fontStyle?: "normal" | "italic";
  fontFamily?: string;

  // Image specific
  imageUrl?: string;

  // Shape specific
  shapeType?: "rectangle" | "line";
  fillColor?: string;
  strokeColor?: string;
}

export interface WatermarkConfiguration {
  id: string | null;
  institutionId: string | null;
  institutionName: string;
  enabled: boolean;
  isOverride: boolean;
  elements: WatermarkElement[];
  updatedAt: string | null;
  updatedBy: string | null;
}

export interface WatermarkConfigurationRequest {
  institutionId?: string | null;
  enabled: boolean;
  elements: WatermarkElement[];
}

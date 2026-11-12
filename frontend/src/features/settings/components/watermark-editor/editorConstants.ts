export type CanvasBackground = "checkerboard" | "sample-photo";

export const BACKGROUND_OPTIONS: ReadonlyArray<{ id: CanvasBackground; label: string; icon: string }> = [
  { id: "checkerboard", label: "Transparent", icon: "ti-grid-dots" },
  { id: "sample-photo", label: "Sample photo", icon: "ti-photo" },
];

export const COLOR_PRESETS = [
  "#FFFFFF",
  "#000000",
  "#1877F2",
  "#0F172A",
  "#475569",
  "#2563EB",
  "#059669",
  "#DC2626",
  "#D97706",
  "rgba(15, 23, 42, 0.75)",
  "rgba(255, 255, 255, 0.85)",
];

export const FONT_OPTIONS = [
  { label: "Modern Sans", value: "inherit" },
  { label: "Geist / Clean", value: "Geist, -apple-system, sans-serif" },
  { label: "Outfit (Geometric)", value: "'Outfit', sans-serif" },
  { label: "Montserrat", value: "'Montserrat', sans-serif" },
  { label: "Playfair (Serif)", value: "'Playfair Display', Georgia, serif" },
  { label: "Cinzel (Classic)", value: "'Cinzel', Times, serif" },
  { label: "JetBrains Mono", value: "'JetBrains Mono', monospace" },
  { label: "Impact / Display", value: "Impact, 'Arial Black', sans-serif" },
];

export const FONT_STYLESHEET_URL =
  "https://fonts.googleapis.com/css2?family=Cinzel:wght@700&family=JetBrains+Mono:wght@700&family=Montserrat:wght@700&family=Outfit:wght@700&family=Playfair+Display:ital,wght@0,700;1,700&display=swap";

export const ACCEPTED_IMAGE_TYPES = "image/png,image/jpeg,image/svg+xml,image/webp";

export const FONT_SIZE_STEP = 0.3;
export const FONT_SIZE_MIN = 1.5;
export const FONT_SIZE_MAX = 8;


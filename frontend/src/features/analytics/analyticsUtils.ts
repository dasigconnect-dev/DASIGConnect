import type { AnalyticsRange, KpiMetricDto } from "../../api/analyticsApi";

export function formatMetric(metric: KpiMetricDto) {
  if (metric.unit === "percent") return `${metric.value.toFixed(1)}%`;
  if (metric.unit === "days") return `${metric.value.toFixed(1)}d`;
  return Intl.NumberFormat().format(metric.value);
}

export function formatNumber(value: number) {
  return Intl.NumberFormat().format(value);
}

export function formatPercent(value: number) {
  return `${value.toFixed(1)}%`;
}

export function formatDateTime(value: string) {
  return new Date(value).toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}

export function formatDelta(value: number | null) {
  if (value === null) return "No comparison";
  const sign = value > 0 ? "+" : "";
  return `${sign}${value.toFixed(1)}% vs prev period`;
}

export function clampPercent(value: number) {
  return Math.max(0, Math.min(100, value));
}

export function sparklinePath(values: number[], width = 160, height = 42) {
  if (values.length === 0) return "";
  const max = Math.max(...values, 1);
  const min = Math.min(...values, 0);
  const span = Math.max(max - min, 1);
  return values
    .map((value, index) => {
      const x = values.length === 1 ? width : (index / (values.length - 1)) * width;
      const y = height - ((value - min) / span) * height;
      return `${index === 0 ? "M" : "L"}${x.toFixed(1)} ${y.toFixed(1)}`;
    })
    .join(" ");
}

/**
 * Generate smooth SVG bezier line and filled area paths
 */
export function generateSmoothLinePath(
  points: number[],
  width: number,
  height: number,
  padding = 16,
): { linePath: string; areaPath: string; coords: Array<{ x: number; y: number; val: number }> } {
  if (!points || points.length === 0) {
    return { linePath: "", areaPath: "", coords: [] };
  }

  const effectiveWidth = width - padding * 2;
  const effectiveHeight = height - padding * 2;
  const maxVal = Math.max(...points, 1);
  const minVal = Math.min(...points, 0);
  const range = Math.max(maxVal - minVal, 1);

  const coords = points.map((val, idx) => {
    const x = padding + (points.length === 1 ? effectiveWidth / 2 : (idx / (points.length - 1)) * effectiveWidth);
    const y = padding + (effectiveHeight - ((val - minVal) / range) * effectiveHeight);
    return { x, y, val };
  });

  if (coords.length === 1) {
    const c = coords[0];
    return {
      linePath: `M ${padding} ${c.y} L ${width - padding} ${c.y}`,
      areaPath: `M ${padding} ${c.y} L ${width - padding} ${c.y} L ${width - padding} ${height} L ${padding} ${height} Z`,
      coords,
    };
  }

  // Build cubic Bezier curves
  let linePath = `M ${coords[0].x.toFixed(1)} ${coords[0].y.toFixed(1)}`;

  for (let i = 0; i < coords.length - 1; i++) {
    const curr = coords[i];
    const next = coords[i + 1];
    const cpX1 = curr.x + (next.x - curr.x) / 2;
    const cpY1 = curr.y;
    const cpX2 = curr.x + (next.x - curr.x) / 2;
    const cpY2 = next.y;

    linePath += ` C ${cpX1.toFixed(1)} ${cpY1.toFixed(1)}, ${cpX2.toFixed(1)} ${cpY2.toFixed(1)}, ${next.x.toFixed(1)} ${next.y.toFixed(1)}`;
  }

  const lastCoord = coords[coords.length - 1];
  const firstCoord = coords[0];
  const areaPath = `${linePath} L ${lastCoord.x.toFixed(1)} ${height} L ${firstCoord.x.toFixed(1)} ${height} Z`;

  return { linePath, areaPath, coords };
}

export const BLUE_GRADIENT_PALETTE = [
  "#0C1D3D", // Deep Navy
  "#164E87", // Dark Slate Blue
  "#1877f2", // Accessible Royal Blue
  "#2563EB", // Primary Accent
  "#3B82F6", // Bright Blue
  "#60A5FA", // Sky Blue
  "#93C5FD", // Soft Blue
  "#BFDBFE", // Light Blue
];

// ── Reporting range (preset or custom date range) ──

/** Local calendar date as YYYY-MM-DD. */
export function toIsoDate(date: Date) {
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${date.getFullYear()}-${month}-${day}`;
}

/** Parses YYYY-MM-DD as a local calendar date (not UTC midnight). */
export function parseIsoDate(value: string) {
  const [year, month, day] = value.split("-").map(Number);
  return new Date(year, month - 1, day);
}

export function addDays(date: Date, days: number) {
  const next = new Date(date);
  next.setDate(next.getDate() + days);
  return next;
}

export function customRange(from: Date, to: Date): AnalyticsRange {
  const [start, end] = from <= to ? [from, to] : [to, from];
  return `${toIsoDate(start)}..${toIsoDate(end)}`;
}

/** Calendar dates a range covers, mirroring the backend's resolvePeriod. */
export function rangeBounds(range: AnalyticsRange, today = new Date()): { from: Date; to: Date } {
  const end = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  switch (range) {
    case "7d":
      return { from: addDays(end, -7), to: end };
    case "30d":
      return { from: addDays(end, -30), to: end };
    case "90d":
      return { from: addDays(end, -90), to: end };
    case "ytd":
      return { from: new Date(end.getFullYear(), 0, 1), to: end };
    default: {
      const [from, to] = range.split("..");
      return { from: parseIsoDate(from), to: parseIsoDate(to) };
    }
  }
}

/** "Aug 24 – Sep 23, 2026", "Sep 23, 2026" for a single day. */
export function formatRangeLabel(range: AnalyticsRange, today = new Date()) {
  const { from, to } = rangeBounds(range, today);
  const full: Intl.DateTimeFormatOptions = { month: "short", day: "numeric", year: "numeric" };
  if (toIsoDate(from) === toIsoDate(to)) return from.toLocaleDateString(undefined, full);
  const sameYear = from.getFullYear() === to.getFullYear();
  const start = from.toLocaleDateString(
    undefined,
    sameYear ? { month: "short", day: "numeric" } : full,
  );
  return `${start} – ${to.toLocaleDateString(undefined, full)}`;
}

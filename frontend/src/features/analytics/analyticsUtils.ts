import type { KpiMetricDto } from "../../api/analyticsApi";

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

export function formatDateRange(start: string, end: string) {
  const startDate = new Date(start);
  const endDate = new Date(end);
  return `${startDate.toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
  })} - ${endDate.toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric",
  })}`;
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
  "#0B5FCC", // Accessible Royal Blue
  "#2563EB", // Primary Accent
  "#3B82F6", // Bright Blue
  "#60A5FA", // Sky Blue
  "#93C5FD", // Soft Blue
  "#BFDBFE", // Light Blue
];

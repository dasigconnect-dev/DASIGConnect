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

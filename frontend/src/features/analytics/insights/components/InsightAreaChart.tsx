import { useId } from "react";
import {
  ResponsiveContainer,
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
} from "recharts";

export interface AreaSeries {
  key: string;
  name: string;
  color: string;
}

interface InsightAreaChartProps {
  data: Array<Record<string, string | number>>;
  xKey: string;
  series: AreaSeries[];
  height?: number;
  /** Format the x-axis tick (e.g. a date string → "May 11"). */
  formatX?: (value: string | number) => string;
}

/**
 * Facebook-style filled trend chart (1-2 series) on the light analytics theme. Wrapped in a
 * ResponsiveContainer so it reflows; gradients + subtle grid keep the data, not chrome, in focus.
 */
export default function InsightAreaChart({ data, xKey, series, height = 280, formatX }: InsightAreaChartProps) {
  const gradientId = useId().replace(/:/g, "");

  if (data.length === 0) {
    return <div className="ins-chart-empty">No data in this range yet.</div>;
  }

  return (
    <div className="ins-chart" style={{ height }}>
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={data} margin={{ top: 8, right: 12, left: 4, bottom: 4 }}>
          <defs>
            {series.map((s, i) => (
              <linearGradient key={s.key} id={`${gradientId}-${i}`} x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor={s.color} stopOpacity={0.28} />
                <stop offset="100%" stopColor={s.color} stopOpacity={0.02} />
              </linearGradient>
            ))}
          </defs>
          <CartesianGrid stroke="#e4e9f2" strokeDasharray="3 3" vertical={false} />
          <XAxis
            dataKey={xKey}
            tickFormatter={formatX}
            tick={{ fill: "#667085", fontSize: 12 }}
            tickLine={false}
            axisLine={{ stroke: "#e4e9f2" }}
            minTickGap={24}
          />
          <YAxis
            tick={{ fill: "#667085", fontSize: 12 }}
            tickLine={false}
            axisLine={false}
            width={36}
            allowDecimals={false}
          />
          <Tooltip
            contentStyle={{
              borderRadius: 10,
              border: "1px solid #e4e9f2",
              boxShadow: "0 8px 24px rgba(16,24,40,0.12)",
              fontSize: 13,
            }}
            labelFormatter={(value) => (formatX ? formatX(value as string) : String(value))}
          />
          {series.length > 1 && <Legend wrapperStyle={{ fontSize: 12, paddingTop: 8 }} iconType="circle" />}
          {series.map((s, i) => (
            <Area
              key={s.key}
              type="monotone"
              dataKey={s.key}
              name={s.name}
              stroke={s.color}
              strokeWidth={2.5}
              fill={`url(#${gradientId}-${i})`}
              dot={false}
              activeDot={{ r: 4 }}
            />
          ))}
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}

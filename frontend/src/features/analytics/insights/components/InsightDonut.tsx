import { ResponsiveContainer, PieChart, Pie, Cell, Tooltip } from "recharts";
import { formatNumber } from "../../analyticsUtils";

export interface DonutSlice {
  name: string;
  value: number;
  color: string;
}

interface InsightDonutProps {
  slices: DonutSlice[];
  height?: number;
}

/** Facebook-style proportion donut (e.g. reactions/comments/shares) with a legend + percentages. */
export default function InsightDonut({ slices, height = 200 }: InsightDonutProps) {
  const total = slices.reduce((sum, s) => sum + s.value, 0);
  if (total === 0) {
    return <div className="ins-chart-empty">No data in this range yet.</div>;
  }
  return (
    <div className="ins-donut">
      <div className="ins-donut-chart" style={{ height }}>
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie
              data={slices}
              dataKey="value"
              nameKey="name"
              innerRadius="62%"
              outerRadius="100%"
              paddingAngle={slices.length > 1 ? 2 : 0}
              stroke="none"
            >
              {slices.map((s) => (
                <Cell key={s.name} fill={s.color} />
              ))}
            </Pie>
            <Tooltip
              formatter={(value) => formatNumber(Number(value))}
              contentStyle={{ borderRadius: 10, border: "1px solid #e4e9f2", fontSize: 13 }}
            />
          </PieChart>
        </ResponsiveContainer>
      </div>
      <ul className="ins-donut-legend">
        {slices.map((s) => (
          <li key={s.name}>
            <span className="ins-donut-dot" style={{ background: s.color }} aria-hidden="true" />
            <span className="ins-donut-name">{s.name}</span>
            <span className="ins-donut-pct">{total > 0 ? `${((s.value / total) * 100).toFixed(1)}%` : "0%"}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

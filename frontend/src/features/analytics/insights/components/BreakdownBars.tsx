export interface BreakdownRow {
  label: string;
  /** Drives the bar width (relative to the row with the largest value). */
  value: number;
  /** Right-aligned display, e.g. "85.2%" or "1,204". Defaults to the value. */
  display?: string;
  /** Optional secondary caption under the label. */
  caption?: string;
}

interface BreakdownBarsProps {
  rows: BreakdownRow[];
  /** Bar fill color (defaults to the analytics blue). */
  color?: string;
  emptyLabel?: string;
}

/** Facebook-style "by content type" labeled horizontal bars (styled, not a heavy chart). */
export default function BreakdownBars({ rows, color = "#155eef", emptyLabel = "No data in this range yet." }: BreakdownBarsProps) {
  if (rows.length === 0) {
    return <div className="ins-chart-empty">{emptyLabel}</div>;
  }
  const max = Math.max(...rows.map((r) => r.value), 1);
  return (
    <div className="ins-bars">
      {rows.map((row) => (
        <div className="ins-bar-row" key={row.label}>
          <div className="ins-bar-head">
            <span className="ins-bar-label">{row.label}</span>
            <span className="ins-bar-value">{row.display ?? row.value.toLocaleString()}</span>
          </div>
          <div className="ins-bar-track">
            <span
              className="ins-bar-fill"
              style={{ width: `${Math.max(2, (row.value / max) * 100)}%`, background: color }}
            />
          </div>
          {row.caption && <span className="ins-bar-caption">{row.caption}</span>}
        </div>
      ))}
    </div>
  );
}

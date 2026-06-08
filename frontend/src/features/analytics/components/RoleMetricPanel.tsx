import type { ReactNode } from "react";

interface Props {
  title: string;
  metrics: Array<[string, string]>;
  action?: ReactNode;
  /** Optional muted footnote rendered under the metric grid (e.g. data-source caveats). */
  note?: ReactNode;
}

export default function RoleMetricPanel({ title, metrics, action, note }: Props) {
  return (
    <section className="analytics-panel">
      <div className="analytics-panel-header">
        <div>
          <h2>{title}</h2>
        </div>
        {action}
      </div>
      <div className="analytics-role-grid">
        {metrics.map(([label, value]) => (
          <div className="analytics-role-card" key={label}>
            <span>{label}</span>
            <strong>{value}</strong>
          </div>
        ))}
      </div>
      {note && (
        <p
          className="analytics-panel-note"
          style={{ margin: "10px 4px 0", fontSize: 12, lineHeight: 1.5, color: "var(--sub-muted, #64748b)" }}
        >
          {note}
        </p>
      )}
    </section>
  );
}

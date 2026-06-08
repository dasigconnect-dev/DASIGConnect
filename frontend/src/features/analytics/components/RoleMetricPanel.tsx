import type { ReactNode } from "react";

interface Props {
  title: string;
  metrics: Array<[string, string]>;
  action?: ReactNode;
}

export default function RoleMetricPanel({ title, metrics, action }: Props) {
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
    </section>
  );
}

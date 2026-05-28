interface Props {
  title: string;
  metrics: Array<[string, string]>;
}

export default function RoleMetricPanel({ title, metrics }: Props) {
  return (
    <section className="analytics-panel">
      <div className="analytics-panel-header">
        <div>
          <h2>{title}</h2>
        </div>
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

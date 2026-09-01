interface Props {
  title: string;
  metrics: Array<[string, string]>;
}

export default function RoleMetricPanel({ title, metrics }: Props) {
  return (
    <div className="card-wrap" style={{ marginBottom: 20 }}>
      <div className="analytics-card-title" style={{ marginBottom: 14 }}>{title}</div>
      <div className="analytics-stat-grid">
        {metrics.map(([label, value]) => (
          <div className="analytics-stat-cell" key={label}>
            <span className="analytics-stat-cell-label">{label}</span>
            <span className="analytics-stat-cell-value">{value}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

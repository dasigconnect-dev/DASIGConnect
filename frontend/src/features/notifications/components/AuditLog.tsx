import type { AuditEntry } from "../types";

interface Props {
  entries: AuditEntry[];
}

export default function AuditLog({ entries }: Props) {
  return (
    <div className="notif-card">
      <div className="notif-card-header">
        <div className="notif-card-title">Audit Log</div>
        <span className="audit-view-all">View all</span>
      </div>
      <div className="audit-list">
        {entries.map((entry, i) => (
          <div key={i} className="audit-row">
            <span className={`audit-type notif-badge ${entry.typeClass}`}>
              {entry.type}
            </span>
            <div className="audit-detail">{entry.detail}</div>
            <div className="audit-time">{entry.time}</div>
          </div>
        ))}
      </div>
    </div>
  );
}

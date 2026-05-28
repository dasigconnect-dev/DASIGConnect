interface Props {
  unreadCount: number;
  criticalCount: number;
  totalCount: number;
}

export default function BellWidget({ unreadCount, criticalCount, totalCount }: Props) {
  return (
    <div className="notif-card bell-widget">
      <div className="notif-card-header">
        <div className="notif-card-title">NotificationBell</div>
        <div className="notif-card-sub">Live notification state</div>
      </div>
      <div className="bell-widget-body">
        <div className="bell-preview">
          <div className="bell-preview-btn">
            <i className="ti ti-bell" style={{ fontSize: 24 }} />
          </div>
          {unreadCount > 0 && <div className="bell-preview-badge">{unreadCount}</div>}
        </div>
        <div className="bell-stats">
          <div className="bell-stat">
            <div className="bell-stat-val red">{unreadCount}</div>
            <div className="bell-stat-lbl">Unread</div>
          </div>
          <div className="bell-stat">
            <div className="bell-stat-val gold">{criticalCount}</div>
            <div className="bell-stat-lbl">Critical</div>
          </div>
          <div className="bell-stat">
            <div className="bell-stat-val">{totalCount}</div>
            <div className="bell-stat-lbl">Total</div>
          </div>
        </div>
      </div>
    </div>
  );
}

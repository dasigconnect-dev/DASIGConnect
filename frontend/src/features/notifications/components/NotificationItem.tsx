import type { Notification } from "../types";

interface Props {
  notification: Notification;
  onClick: (id: string) => void;
  incoming?: boolean;
}

export default function NotificationItem({ notification: n, onClick, incoming }: Props) {
  const className = [
    "notif-item",
    n.unread ? "unread" : "",
    n.critical ? "critical" : "",
    n.warning ? "warning-bg" : "",
    incoming ? "incoming" : "",
  ]
    .filter(Boolean)
    .join(" ");

  function handleClick(e: React.MouseEvent) {
    e.preventDefault();
    onClick(n.id);
  }

  return (
    <a className={className} href="#" onClick={handleClick}>
      {n.unread && <div className="notif-unread-dot" />}
      <div className={`notif-icon-wrap ${n.iconClass}`}>
        <i className={n.icon} style={{ fontSize: 17 }} />
      </div>
      <div className="notif-body">
        <div className="notif-top-row">
          <span className="notif-sender">{n.sender}</span>
          <span className="notif-time">{n.time}</span>
        </div>
        <div className="notif-text">{n.text}</div>
        <div className="notif-tags">
          {n.tags.map((tag, i) => (
            <span key={i} className={`notif-badge ${tag.badgeClass}`}>
              {tag.label}
            </span>
          ))}
          <a
            href={n.link}
            className="notif-deeplink"
            onClick={(e) => e.stopPropagation()}
          >
            {n.linkLabel}
            <i className="ti ti-arrow-right" style={{ fontSize: 11 }} />
          </a>
        </div>
      </div>
    </a>
  );
}

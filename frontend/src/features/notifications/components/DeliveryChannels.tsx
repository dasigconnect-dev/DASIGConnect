import type { SseStatus } from "../types";

interface Props {
  totalCount: number;
  unreadCount: number;
  criticalCount: number;
  sseStatus: SseStatus;
}

export default function DeliveryChannels({
  totalCount,
  unreadCount,
  criticalCount,
  sseStatus,
}: Props) {
  const readCount = Math.max(totalCount - unreadCount, 0);
  const readPercent = totalCount === 0 ? 0 : Math.round((readCount / totalCount) * 100);
  const streamHealthy = sseStatus === "connected";

  return (
    <div className="notif-card">
      <div className="notif-card-header">
        <div className="notif-card-title">Delivery Channels</div>
        <div className="notif-card-sub">Current session</div>
      </div>
      <div className="channel-list">
        <div className="channel-row">
          <div className="channel-icon icon-info">
            <i className="ti ti-device-desktop" style={{ fontSize: 15 }} />
          </div>
          <div className="channel-info">
            <div className="channel-name">In-App</div>
            <div className="channel-sub">
              {totalCount} loaded - {unreadCount} unread
            </div>
          </div>
          <div className="channel-bar-wrap">
            <div className="channel-bar-bg">
              <div
                className="channel-bar-fill"
                style={{ width: `${readPercent}%`, background: "var(--notif-blue-brand)" }}
              />
            </div>
            <div className="channel-bar-pct">{readPercent}%</div>
          </div>
        </div>
        <div className="channel-row">
          <div className={streamHealthy ? "channel-icon icon-success" : "channel-icon icon-warning"}>
            <i
              className={streamHealthy ? "ti ti-broadcast" : "ti ti-broadcast-off"}
              style={{ fontSize: 15 }}
            />
          </div>
          <div className="channel-info">
            <div className="channel-name">SSE Stream</div>
            <div className="channel-sub">
              {streamHealthy ? "Live updates connected" : "Using API refresh fallback"}
            </div>
          </div>
          <span className={`notif-badge ${streamHealthy ? "badge-approved-alt" : "badge-pending"}`}>
            {streamHealthy ? "Live" : "Fallback"}
          </span>
        </div>
        <div className="channel-row">
          <div className={criticalCount > 0 ? "channel-icon icon-error" : "channel-icon icon-navy"}>
            <i className="ti ti-alert-triangle" style={{ fontSize: 14 }} />
          </div>
          <div className="channel-info">
            <div className="channel-name">Critical Queue</div>
            <div className="channel-sub">
              {criticalCount > 0 ? `${criticalCount} critical notification(s)` : "No critical notifications"}
            </div>
          </div>
          <span className={`notif-badge ${criticalCount > 0 ? "badge-critical" : "badge-approved-alt"}`}>
            {criticalCount > 0 ? "Review" : "Clear"}
          </span>
        </div>
      </div>
    </div>
  );
}

import type { SseStatus } from "../types";

interface Props {
  status: SseStatus;
  eventCount: number;
  unreadCount: number;
  lastEventTime: string | null;
}

const STATUS_LABEL: Record<SseStatus, string> = {
  connected: "Connected",
  connecting: "Connecting...",
  disconnected: "Disconnected",
};

export default function SseStatusBar({
  status,
  eventCount,
  unreadCount,
  lastEventTime,
}: Props) {
  const sublabel =
    status === "connected"
      ? `Connected to /api/v1/notifications/stream. Last event: ${lastEventTime ?? "None yet"}`
      : status === "connecting"
        ? "Establishing connection to /api/v1/notifications/stream..."
        : "SSE stream unavailable. Notifications will refresh from the API.";

  return (
    <div className="sse-bar">
      <div className="sse-left">
        <div className={`sse-dot ${status === "connected" ? "" : status}`} />
        <div>
          <div className="sse-label">SSENotificationListener - {STATUS_LABEL[status]}</div>
          <div className="sse-sublabel">{sublabel}</div>
        </div>
      </div>
      <div className="sse-right">
        <div className="sse-stat">
          <div className="sse-stat-val">{eventCount}</div>
          <div className="sse-stat-lbl">Events today</div>
        </div>
        <div className="sse-divider" />
        <div className="sse-stat">
          <div className="sse-stat-val">{unreadCount}</div>
          <div className="sse-stat-lbl">Unread</div>
        </div>
        <div className="sse-divider" />
        <div className="sse-stat">
          <div className="sse-stat-val" style={{ color: "var(--notif-success)" }}>0</div>
          <div className="sse-stat-lbl">Failures</div>
        </div>
      </div>
    </div>
  );
}

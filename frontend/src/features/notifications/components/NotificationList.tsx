import { useRef, useEffect } from "react";
import type { Notification } from "../types";
import { GROUP_ORDER } from "../types";
import NotificationItem from "./NotificationItem";

interface Props {
  notifications: Notification[];
  onNotificationClick: (id: string) => void;
  latestIncomingId?: string;
  loading?: boolean;
  error?: string | null;
  emptyTitle?: string;
  emptyMessage?: string;
}

const SKELETON_KEYS = ["sk-a", "sk-b", "sk-c", "sk-d", "sk-e"];

export default function NotificationList({
  notifications,
  onNotificationClick,
  latestIncomingId,
  loading,
  error,
  emptyTitle = "Nothing here yet",
  emptyMessage = "No notifications in this category.",
}: Readonly<Props>) {
  const listRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (latestIncomingId && listRef.current) {
      const el = listRef.current.querySelector(`[data-id="${latestIncomingId}"]`);
      if (el) {
        el.classList.add("incoming");
        const timer = window.setTimeout(() => el.classList.remove("incoming"), 800);
        return () => window.clearTimeout(timer);
      }
    }
  }, [latestIncomingId]);

  if (loading) {
    return (
      <div className="notif-list-loading">
        {SKELETON_KEYS.map((key) => (
          <div key={key} className="notif-skeleton">
            <div className="notif-skeleton-icon" />
            <div className="notif-skeleton-body">
              <div className="notif-skeleton-line notif-skeleton-line--wide" />
              <div className="notif-skeleton-line notif-skeleton-line--narrow" />
            </div>
          </div>
        ))}
      </div>
    );
  }

  if (error) {
    return (
      <div className="notif-empty">
        <i className="ti ti-wifi-off" style={{ fontSize: 32 }} />
        <p>{error}</p>
      </div>
    );
  }

  if (notifications.length === 0) {
    return (
      <div className="notif-empty">
        <i className="ti ti-bell-off" style={{ fontSize: 32 }} />
        <strong>{emptyTitle}</strong>
        <p>{emptyMessage}</p>
      </div>
    );
  }

  const groups: Record<string, Notification[]> = {};
  for (const n of notifications) {
    if (!groups[n.group]) groups[n.group] = [];
    groups[n.group].push(n);
  }

  const sortedGroups = Object.entries(groups).sort(
    ([a], [b]) => (GROUP_ORDER[a] ?? 99) - (GROUP_ORDER[b] ?? 99)
  );

  return (
    <div className="notif-list" ref={listRef}>
      {sortedGroups.map(([group, items]) => (
        <div key={group}>
          <div className="notif-date-group">{group}</div>
          {items.map((n) => (
            <NotificationItem
              key={n.id}
              notification={n}
              onClick={onNotificationClick}
              incoming={n.id === latestIncomingId}
            />
          ))}
        </div>
      ))}
    </div>
  );
}

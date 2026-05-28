import { useCallback, useEffect, useMemo, useState } from "react";
import {
  listNotifications,
  markAllNotificationsRead as apiMarkAllRead,
  markNotificationRead as apiMarkRead,
  openNotificationStream,
} from "../../../api/notificationApi";
import type { NotificationDto } from "../../../api/notificationApi";
import type {
  AuditEntry,
  Notification,
  NotificationCategory,
  NotificationFilter,
  SseStatus,
} from "../types";

export interface NotificationCounts {
  all: number;
  unread: number;
  submissions: number;
  publishing: number;
  system: number;
  overrides: number;
  deadline: number;
}

interface EventDisplayMeta {
  trigger: string;
  category: NotificationCategory;
  icon: string;
  iconClass: string;
  critical?: boolean;
  warning?: boolean;
  sender: string;
  linkLabel: string;
  badgeClass: string;
}

const EVENT_META: Record<string, EventDisplayMeta> = {
  submission_pending: {
    trigger: "T1",
    category: "submissions",
    icon: "ti ti-file-plus",
    iconClass: "icon-navy",
    sender: "Submission System",
    linkLabel: "Go to Validation Queue",
    badgeClass: "badge-pending",
  },
  submission_approved: {
    trigger: "T2",
    category: "submissions",
    icon: "ti ti-circle-check",
    iconClass: "icon-success",
    sender: "Validation",
    linkLabel: "Open Submission",
    badgeClass: "badge-approved",
  },
  submission_needs_revision: {
    trigger: "T3",
    category: "submissions",
    icon: "ti ti-pencil",
    iconClass: "icon-warning",
    sender: "Validation",
    linkLabel: "View Feedback",
    badgeClass: "badge-revision",
  },
  submission_rejected: {
    trigger: "T4",
    category: "submissions",
    icon: "ti ti-circle-x",
    iconClass: "icon-error",
    sender: "Validation",
    linkLabel: "View Feedback",
    badgeClass: "badge-rejected",
  },
  submission_scheduled: {
    trigger: "T2",
    category: "submissions",
    icon: "ti ti-calendar",
    iconClass: "icon-info",
    sender: "Scheduler",
    linkLabel: "View Schedule",
    badgeClass: "badge-approved",
  },
  submission_publish_failed: {
    trigger: "T7",
    category: "publishing",
    icon: "ti ti-circle-x",
    iconClass: "icon-error",
    sender: "Publishing Engine",
    linkLabel: "Open Submission",
    badgeClass: "badge-failed",
    critical: true,
  },
  submission_published: {
    trigger: "T5",
    category: "publishing",
    icon: "ti ti-circle-check",
    iconClass: "icon-success",
    sender: "Publishing Engine",
    linkLabel: "View Published Post",
    badgeClass: "badge-published",
  },
  submission_published_manual: {
    trigger: "T6",
    category: "publishing",
    icon: "ti ti-send",
    iconClass: "icon-success",
    sender: "Admin - Manual Publish",
    linkLabel: "View Published Post",
    badgeClass: "badge-published",
  },
  validation_timeout: {
    trigger: "T8",
    category: "deadline",
    icon: "ti ti-clock",
    iconClass: "icon-warning",
    sender: "Deadline Watch",
    linkLabel: "Open Submission",
    badgeClass: "badge-pending",
    warning: true,
  },
  override_approved: {
    trigger: "T9",
    category: "overrides",
    icon: "ti ti-shield",
    iconClass: "icon-purple",
    sender: "Override Decision - Admin",
    linkLabel: "Open Submission",
    badgeClass: "badge-approved",
  },
  override_denied: {
    trigger: "T10",
    category: "overrides",
    icon: "ti ti-shield-off",
    iconClass: "icon-error",
    sender: "Override Decision - Admin",
    linkLabel: "View Feedback",
    badgeClass: "badge-rejected",
  },
  override_slot_suggested: {
    trigger: "T17",
    category: "overrides",
    icon: "ti ti-calendar-check",
    iconClass: "icon-purple",
    sender: "Override Suggestion - Admin",
    linkLabel: "Review Schedule",
    badgeClass: "badge-revision",
  },
  admin_direct_post: {
    trigger: "T11",
    category: "publishing",
    icon: "ti ti-speakerphone",
    iconClass: "icon-navy",
    sender: "Admin Direct Post",
    linkLabel: "View Post Record",
    badgeClass: "badge-published",
  },
  institution_no_validator: {
    trigger: "T14",
    category: "system",
    icon: "ti ti-building",
    iconClass: "icon-error",
    sender: "System - Institution Monitor",
    linkLabel: "Manage Institution",
    badgeClass: "badge-critical",
    critical: true,
  },
  institution_onboarded: {
    trigger: "T15",
    category: "system",
    icon: "ti ti-sparkles",
    iconClass: "icon-success",
    sender: "Onboarding System",
    linkLabel: "View Institution",
    badgeClass: "badge-approved",
  },
  submission_rescheduled: {
    trigger: "T16",
    category: "submissions",
    icon: "ti ti-calendar",
    iconClass: "icon-info",
    sender: "Admin - Rescheduled Post",
    linkLabel: "View Schedule",
    badgeClass: "badge-revision",
  },
  token_expiring: {
    trigger: "T12",
    category: "system",
    icon: "ti ti-key",
    iconClass: "icon-warning",
    sender: "System - Token Guard",
    linkLabel: "Renew Token",
    badgeClass: "badge-pending",
    warning: true,
  },
  token_invalid: {
    trigger: "T13",
    category: "system",
    icon: "ti ti-shield-exclamation",
    iconClass: "icon-error",
    sender: "System - Token Health Check",
    linkLabel: "Go to Token Settings",
    badgeClass: "badge-critical",
    critical: true,
  },
  generic: {
    trigger: "SYS",
    category: "system",
    icon: "ti ti-bell",
    iconClass: "icon-info",
    sender: "System",
    linkLabel: "View",
    badgeClass: "badge-system",
  },
};

function formatRelativeTime(iso: string): string {
  const diffMs = Date.now() - new Date(iso).getTime();
  const diffMin = Math.floor(diffMs / 60000);
  if (diffMin < 1) return "just now";
  if (diffMin < 60) return `${diffMin} min ago`;
  const diffHr = Math.floor(diffMin / 60);
  if (diffHr < 24) return `${diffHr} hr ago`;
  const diffDays = Math.floor(diffHr / 24);
  if (diffDays === 1) return "Yesterday";
  return `${diffDays} days ago`;
}

function computeGroup(iso: string): string {
  const diffDays = Math.floor((Date.now() - new Date(iso).getTime()) / 86400000);
  if (diffDays === 0) return "Today";
  if (diffDays === 1) return "Yesterday";
  if (diffDays <= 6) return `${diffDays} Days Ago`;
  return "Last Week";
}

function mapDto(dto: NotificationDto): Notification {
  const meta = EVENT_META[dto.eventType] ?? EVENT_META.generic;
  return {
    id: dto.id,
    eventType: dto.eventType,
    trigger: meta.trigger,
    category: meta.category,
    unread: dto.readAt === null,
    critical: meta.critical,
    warning: meta.warning,
    icon: meta.icon,
    iconClass: meta.iconClass,
    sender: meta.sender,
    time: formatRelativeTime(dto.createdAt),
    text: dto.message,
    tags: [{ label: meta.trigger, badgeClass: meta.badgeClass }],
    link: dto.deepLink ?? "/notifications",
    linkLabel: meta.linkLabel,
    group: computeGroup(dto.createdAt),
  };
}

export function useNotifications() {
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [auditLog, setAuditLog] = useState<AuditEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [fetchError, setFetchError] = useState<string | null>(null);
  const [sseStatus, setSseStatus] = useState<SseStatus>("connecting");
  const [eventCount, setEventCount] = useState(0);
  const [lastEventTime, setLastEventTime] = useState<string | null>(null);
  const [latestIncomingId, setLatestIncomingId] = useState<string | null>(null);
  const [activeFilter, setActiveFilter] = useState<NotificationFilter>("all");
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    listNotifications(controller.signal)
      .then((res) => {
        setNotifications(res.data.map(mapDto));
        setFetchError(null);
      })
      .catch((err: { code?: string }) => {
        if (err?.code !== "ERR_CANCELED") {
          setFetchError("Could not load notifications. The backend may not be available.");
        }
      })
      .finally(() => setLoading(false));
    return () => controller.abort();
  }, [refreshKey]);

  useEffect(() => {
    const controller = new AbortController();
    openNotificationStream(
      (dto) => {
        const mapped = mapDto(dto);
        setNotifications((prev) => [mapped, ...prev]);
        setLatestIncomingId(mapped.id);
        setEventCount((prev) => prev + 1);
        setLastEventTime("just now");
        setAuditLog((prev) => [
          {
            type: "DISPATCHED",
            typeClass: "badge-approved",
            detail: `${dto.eventType} -> in-app SSE`,
            time: "just now",
          },
          ...prev.slice(0, 9),
        ]);
      },
      () => setSseStatus("connected"),
      () => setSseStatus("disconnected"),
      controller.signal,
    );
    return () => controller.abort();
  }, []);

  const counts = useMemo<NotificationCounts>(() => {
    const unread = notifications.filter((n) => n.unread).length;
    return {
      all: notifications.length,
      unread,
      submissions: notifications.filter((n) => n.category === "submissions").length,
      publishing: notifications.filter((n) => n.category === "publishing").length,
      system: notifications.filter((n) => n.category === "system").length,
      overrides: notifications.filter((n) => n.category === "overrides").length,
      deadline: notifications.filter((n) => n.category === "deadline").length,
    };
  }, [notifications]);

  const filteredNotifications = useMemo(
    () =>
      notifications.filter((n) => {
        if (activeFilter === "all") return true;
        if (activeFilter === "unread") return n.unread;
        return n.category === activeFilter;
      }),
    [activeFilter, notifications],
  );

  const markAllRead = useCallback(() => {
    setNotifications((prev) => prev.map((n) => ({ ...n, unread: false })));
    apiMarkAllRead().catch(() => {
      // The optimistic update is enough for the current session.
    });
  }, []);

  const markRead = useCallback((id: string) => {
    setNotifications((prev) =>
      prev.map((n) => (n.id === id ? { ...n, unread: false } : n)),
    );
    apiMarkRead(id).catch(() => {
      // The optimistic update is enough for the current session.
    });
  }, []);

  const refreshNotifications = useCallback(() => {
    setLoading(true);
    setFetchError(null);
    setRefreshKey((value) => value + 1);
  }, []);

  return {
    notifications: filteredNotifications,
    allNotifications: notifications,
    auditLog,
    loading,
    fetchError,
    sseStatus,
    eventCount,
    lastEventTime,
    latestIncomingId,
    activeFilter,
    setActiveFilter,
    unreadCount: counts.unread,
    criticalCount: notifications.filter((n) => n.critical).length,
    totalCount: notifications.length,
    counts,
    markAllRead,
    markRead,
    refreshNotifications,
  };
}

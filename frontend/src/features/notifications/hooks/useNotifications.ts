import { useCallback, useEffect, useMemo, useState } from "react";
import axios from "axios";
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
    trigger: "T-01",
    category: "submissions",
    icon: "ti ti-file-plus",
    iconClass: "icon-navy",
    sender: "New submission",
    linkLabel: "Review Submission",
    badgeClass: "badge-pending",
  },
  submission_approved: {
    trigger: "T-02",
    category: "submissions",
    icon: "ti ti-circle-check",
    iconClass: "icon-success",
    sender: "Review",
    linkLabel: "Open Submission",
    badgeClass: "badge-approved",
  },
  submission_needs_revision: {
    trigger: "REV",
    category: "submissions",
    icon: "ti ti-pencil",
    iconClass: "icon-warning",
    sender: "Review",
    linkLabel: "View Feedback",
    badgeClass: "badge-revision",
  },
  submission_rejected: {
    trigger: "T-03",
    category: "submissions",
    icon: "ti ti-circle-x",
    iconClass: "icon-error",
    sender: "Review",
    linkLabel: "View Feedback",
    badgeClass: "badge-rejected",
  },
  submission_scheduled: {
    trigger: "T-02",
    category: "submissions",
    icon: "ti ti-calendar",
    iconClass: "icon-info",
    sender: "Schedule",
    linkLabel: "View Schedule",
    badgeClass: "badge-approved",
  },
  submission_published: {
    trigger: "T-04",
    category: "publishing",
    icon: "ti ti-circle-check",
    iconClass: "icon-success",
    sender: "Publishing",
    linkLabel: "View Published Post",
    badgeClass: "badge-published",
  },
  submission_published_manual: {
    trigger: "T-05",
    category: "publishing",
    icon: "ti ti-send",
    iconClass: "icon-success",
    sender: "Publishing",
    linkLabel: "View Published Post",
    badgeClass: "badge-published",
  },
  submission_publish_failed: {
    trigger: "T-06",
    category: "publishing",
    icon: "ti ti-circle-x",
    iconClass: "icon-error",
    sender: "Publishing",
    linkLabel: "Review Failed Publication",
    badgeClass: "badge-failed",
    critical: true,
  },
  empty_schedule_warning: {
    trigger: "T-07",
    category: "system",
    icon: "ti ti-calendar-off",
    iconClass: "icon-warning",
    sender: "Schedule",
    linkLabel: "View Calendar",
    badgeClass: "badge-pending",
    warning: true,
  },
  token_expiring: {
    trigger: "T-08",
    category: "system",
    icon: "ti ti-key",
    iconClass: "icon-warning",
    sender: "Facebook integration",
    linkLabel: "Manage Tokens",
    badgeClass: "badge-pending",
    warning: true,
  },
  token_invalid: {
    trigger: "T-09",
    category: "system",
    icon: "ti ti-shield-exclamation",
    iconClass: "icon-error",
    sender: "Facebook integration",
    linkLabel: "Manage Tokens",
    badgeClass: "badge-critical",
    critical: true,
  },
  submission_rescheduled: {
    trigger: "T-10",
    category: "submissions",
    icon: "ti ti-calendar",
    iconClass: "icon-info",
    sender: "Schedule change",
    linkLabel: "View Schedule",
    badgeClass: "badge-revision",
  },
  fast_track_submission: {
    trigger: "T-11",
    category: "submissions",
    icon: "ti ti-bolt",
    iconClass: "icon-purple",
    sender: "Fast-track",
    linkLabel: "Immediate Review",
    badgeClass: "badge-critical",
    critical: true,
  },
  embedding_failure_digest: {
    trigger: "T-12",
    category: "system",
    icon: "ti ti-photo-off",
    iconClass: "icon-warning",
    sender: "AI media",
    linkLabel: "View Media Library",
    badgeClass: "badge-revision",
  },
  validation_timeout: {
    trigger: "TIMEOUT",
    category: "deadline",
    icon: "ti ti-clock",
    iconClass: "icon-warning",
    sender: "Deadline",
    linkLabel: "Open Submission",
    badgeClass: "badge-pending",
    warning: true,
  },
  override_requested: {
    trigger: "OVERRIDE",
    category: "overrides",
    icon: "ti ti-shield-question",
    iconClass: "icon-purple",
    sender: "Guard rail override",
    linkLabel: "Review Request",
    badgeClass: "badge-pending",
    warning: true,
  },
  override_approved: {
    trigger: "OVERRIDE",
    category: "overrides",
    icon: "ti ti-shield",
    iconClass: "icon-purple",
    sender: "Guard rail override",
    linkLabel: "Open Submission",
    badgeClass: "badge-approved",
  },
  override_denied: {
    trigger: "OVERRIDE",
    category: "overrides",
    icon: "ti ti-shield-off",
    iconClass: "icon-error",
    sender: "Guard rail override",
    linkLabel: "View Feedback",
    badgeClass: "badge-rejected",
  },
  override_slot_suggested: {
    trigger: "OVERRIDE",
    category: "overrides",
    icon: "ti ti-calendar-check",
    iconClass: "icon-purple",
    sender: "Guard rail override",
    linkLabel: "Review Schedule",
    badgeClass: "badge-revision",
  },
  admin_direct_post: {
    trigger: "DIRECT",
    category: "publishing",
    icon: "ti ti-speakerphone",
    iconClass: "icon-navy",
    sender: "Direct post",
    linkLabel: "View Post Record",
    badgeClass: "badge-published",
  },
  institution_no_moderator: {
    trigger: "SYSTEM",
    category: "system",
    icon: "ti ti-building",
    iconClass: "icon-error",
    sender: "Institution",
    linkLabel: "Manage Institution",
    badgeClass: "badge-critical",
    critical: true,
  },
  institution_onboarded: {
    trigger: "ONBOARD",
    category: "system",
    icon: "ti ti-sparkles",
    iconClass: "icon-success",
    sender: "Institution",
    linkLabel: "View Institution",
    badgeClass: "badge-approved",
  },
  submission_missed_review: {
    trigger: "MISSED",
    category: "deadline",
    icon: "ti ti-clock-x",
    iconClass: "icon-error",
    sender: "Deadline",
    linkLabel: "Open Submission",
    badgeClass: "badge-failed",
    critical: true,
  },
  user_role_changed: {
    trigger: "ACCOUNT",
    category: "system",
    icon: "ti ti-user-cog",
    iconClass: "icon-info",
    sender: "Account",
    linkLabel: "Go to Dashboard",
    badgeClass: "badge-revision",
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
    createdAt: dto.createdAt,
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
    let isCurrent = true;
    const controller = new AbortController();
    setLoading(true);

    listNotifications(controller.signal)
      .then((res) => {
        if (!isCurrent) return;
        setNotifications(res.data.map(mapDto));
        setFetchError(null);
        setLoading(false);
      })
      .catch((err: unknown) => {
        if (!isCurrent) return;
        if (axios.isCancel(err) || (err as { code?: string })?.code === "ERR_CANCELED") {
          return;
        }
        setFetchError("Could not load notifications. The backend may not be available.");
        setLoading(false);
      });

    return () => {
      isCurrent = false;
      controller.abort();
    };
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

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  useInfiniteQuery,
  useQuery,
  useQueryClient,
  type InfiniteData,
} from "@tanstack/react-query";
import {
  getUnreadCount,
  listNotificationHistory,
  markAllNotificationsRead as apiMarkAllRead,
  markNotificationRead as apiMarkRead,
  openNotificationStream,
} from "../../../api/notificationApi";
import type { NotificationDto } from "../../../api/notificationApi";
import { useToast } from "../../../context/ToastContext";
import { authenticatedQueryMeta } from "../../../lib/queryClient";
import { queryKeys, queryRoots } from "../../../lib/queryKeys";
import type { User } from "../../../types/auth.types";
import type {
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

// Keep navigation re-entry warm through TanStack Query instead of a module cache.
const NOTIFICATIONS_STALE_TIME_MS = 60_000;
const UNREAD_COUNT_STALE_TIME_MS = 30_000;
const NOTIFICATIONS_PAGE_SIZE = 50;

interface NotificationPage {
  items: Notification[];
  totalCount: number;
  page: number;
  pageSize: number;
}

function userScope(user: User) {
  return user.id ?? user.email.trim().toLowerCase();
}

function unreadCountQueryKey(user: User) {
  return queryKeys.notifications.unreadCount({
    role: user.role,
    userId: userScope(user),
    institutionId: user.institutionId ?? null,
  });
}

export function useNotifications(user: User) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [sseStatus, setSseStatus] = useState<SseStatus>("connecting");
  const [activeFilter, setActiveFilter] = useState<NotificationFilter>("all");
  const role = user.role;
  const scopedUserId = userScope(user);
  const institutionId = user.institutionId ?? null;
  const listQueryKey = useMemo(
    () => queryKeys.notifications.all({ role, userId: scopedUserId, institutionId }),
    [institutionId, role, scopedUserId],
  );
  const countQueryKey = useMemo(
    () => queryKeys.notifications.unreadCount({ role, userId: scopedUserId, institutionId }),
    [institutionId, role, scopedUserId],
  );

  const notificationsQuery = useInfiniteQuery({
    queryKey: listQueryKey,
    queryFn: ({ signal, pageParam }) =>
      listNotificationHistory(pageParam, NOTIFICATIONS_PAGE_SIZE, signal).then((res) => ({
        ...res.data,
        items: res.data.items.map(mapDto),
      })),
    initialPageParam: 0,
    getNextPageParam: (lastPage) =>
      (lastPage.page + 1) * lastPage.pageSize < lastPage.totalCount
        ? lastPage.page + 1
        : undefined,
    staleTime: NOTIFICATIONS_STALE_TIME_MS,
    meta: authenticatedQueryMeta,
  });

  const notifications = useMemo(() => {
    const seen = new Set<string>();
    return (notificationsQuery.data?.pages ?? []).flatMap((page) =>
      page.items.filter((notification) => {
        if (seen.has(notification.id)) return false;
        seen.add(notification.id);
        return true;
      }),
    );
  }, [notificationsQuery.data]);

  const updateNotifications = useCallback(
    (updater: (current: Notification[]) => Notification[]) => {
      queryClient.setQueryData<InfiniteData<NotificationPage, number>>(listQueryKey, (current) => {
        if (!current) return current;
        const pages = current.pages.map((page) => ({
          ...page,
          items: updater(page.items),
        }));
        return { ...current, pages };
      });
    },
    [listQueryKey, queryClient],
  );

  const prependNotification = useCallback(
    (notification: Notification) => {
      const current = queryClient.getQueryData<InfiniteData<NotificationPage, number>>(listQueryKey);
      if (!current || current.pages.length === 0) return;
      if (current.pages.some((page) => page.items.some((item) => item.id === notification.id))) {
        return;
      }
      const [firstPage, ...remainingPages] = current.pages;
      queryClient.setQueryData<InfiniteData<NotificationPage, number>>(listQueryKey, {
        ...current,
        pages: [
          {
            ...firstPage,
            items: [notification, ...firstPage.items],
            totalCount: firstPage.totalCount + 1,
          },
          ...remainingPages.map((page) => ({ ...page, totalCount: page.totalCount + 1 })),
        ],
      });
      if (notification.unread) {
        const unreadCount = queryClient.getQueryData<number>(countQueryKey);
        if (unreadCount === undefined) {
          void queryClient.invalidateQueries({ queryKey: countQueryKey, exact: true });
        } else {
          queryClient.setQueryData<number>(countQueryKey, unreadCount + 1);
        }
      }
    },
    [countQueryKey, listQueryKey, queryClient],
  );

  const snapshotNotificationCache = useCallback(() => {
    const data = queryClient.getQueryData<InfiniteData<NotificationPage, number>>(listQueryKey);
    return {
      data,
      unreadCount: queryClient.getQueryData<number>(countQueryKey),
    };
  }, [countQueryKey, listQueryKey, queryClient]);

  const reconcileNotificationCache = useCallback(() => {
    return Promise.all([
      queryClient.invalidateQueries({ queryKey: listQueryKey, exact: true }),
      queryClient.invalidateQueries({ queryKey: countQueryKey, exact: true }),
    ]);
  }, [countQueryKey, listQueryKey, queryClient]);

  const restoreNotificationCache = useCallback(
    (snapshot: {
      data: InfiniteData<NotificationPage, number> | undefined;
      unreadCount: number | undefined;
    }) => {
      queryClient.setQueryData<InfiniteData<NotificationPage, number>>(listQueryKey, snapshot.data);
      queryClient.setQueryData<number>(countQueryKey, snapshot.unreadCount);
    },
    [countQueryKey, listQueryKey, queryClient],
  );

  // Cross-session read-state sync: applies a "read"/"read-all" pushed by
  // another of this user's open tabs/devices to this tab's own cache, the
  // same way markRead/markAllRead update it locally — just without calling
  // the API again (it already happened wherever the push came from).
  const applyRemoteRead = useCallback(
    (id: string) => {
      const wasUnread = notifications.some((notification) => notification.id === id && notification.unread);
      updateNotifications((prev) => prev.map((n) => (n.id === id ? { ...n, unread: false } : n)));
      if (wasUnread) {
        const unreadCount = queryClient.getQueryData<number>(countQueryKey);
        if (unreadCount !== undefined) {
          queryClient.setQueryData<number>(countQueryKey, Math.max(0, unreadCount - 1));
        }
      }
    },
    [countQueryKey, notifications, queryClient, updateNotifications],
  );

  const applyRemoteReadAll = useCallback(() => {
    updateNotifications((prev) => prev.map((n) => ({ ...n, unread: false })));
    queryClient.setQueryData<number>(countQueryKey, 0);
  }, [countQueryKey, queryClient, updateNotifications]);

  useEffect(() => {
    // The server closes the SSE stream every 30 minutes (and connections drop
    // on flaky networks), so reconnect with capped exponential backoff instead
    // of going silent until the next page load.
    let stopped = false;
    let controller = new AbortController();
    let retryTimer: number | undefined;
    let attempts = 0;
    let connectedAt = 0;
    let hasConnected = false;

    const connect = () => {
      if (stopped) return;
      const connectionController = new AbortController();
      controller = connectionController;
      setSseStatus("connecting");
      let disconnected = false;

      const handleDisconnect = () => {
        if (disconnected || connectionController.signal.aborted) return;
        disconnected = true;
        setSseStatus("disconnected");
        if (stopped) return;
        // A stream that stayed open a while (e.g. the 30-min server timeout)
        // is healthy, so reconnect quickly. Repeated early failures back off.
        if (connectedAt && Date.now() - connectedAt > 10_000) attempts = 0;
        connectedAt = 0;
        const delay = Math.min(2000 * 2 ** Math.min(attempts, 4), 30_000);
        attempts = Math.min(attempts + 1, 4);
        retryTimer = window.setTimeout(connect, delay);
      };

      openNotificationStream(
        {
          onNotification: (dto) => {
            if (stopped || connectionController.signal.aborted) return;
            attempts = 0;
            const mapped = mapDto(dto);
            // A fetch that raced the same event can already hold this id.
            prependNotification(mapped);
          },
          onRead: (id) => {
            if (stopped || connectionController.signal.aborted) return;
            applyRemoteRead(id);
          },
          onReadAll: () => {
            if (stopped || connectionController.signal.aborted) return;
            applyRemoteReadAll();
          },
        },
        () => {
          if (stopped || connectionController.signal.aborted) return;
          connectedAt = Date.now();
          setSseStatus("connected");
          if (hasConnected) {
            void reconcileNotificationCache();
          }
          hasConnected = true;
        },
        handleDisconnect,
        connectionController.signal,
      );
    };

    connect();

    return () => {
      stopped = true;
      if (retryTimer) window.clearTimeout(retryTimer);
      controller.abort();
    };
  }, [applyRemoteRead, applyRemoteReadAll, prependNotification, reconcileNotificationCache]);

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

  const markAllRead = useCallback(() => {
    const snapshot = snapshotNotificationCache();
    updateNotifications((prev) => prev.map((n) => ({ ...n, unread: false })));
    queryClient.setQueryData<number>(countQueryKey, 0);
    apiMarkAllRead().catch(() => {
      restoreNotificationCache(snapshot);
      void reconcileNotificationCache();
      toast.error("Could not mark all notifications as read.");
    });
  }, [countQueryKey, queryClient, reconcileNotificationCache, restoreNotificationCache, snapshotNotificationCache, toast, updateNotifications]);

  const markRead = useCallback((id: string) => {
    const snapshot = snapshotNotificationCache();
    const wasUnread = notifications.some((notification) => notification.id === id && notification.unread);
    updateNotifications((prev) => prev.map((n) => (n.id === id ? { ...n, unread: false } : n)));
    if (wasUnread) {
      const unreadCount = queryClient.getQueryData<number>(countQueryKey);
      if (unreadCount === undefined) {
        void queryClient.invalidateQueries({ queryKey: countQueryKey, exact: true });
      } else {
        queryClient.setQueryData<number>(countQueryKey, Math.max(0, unreadCount - 1));
      }
    }
    apiMarkRead(id).catch(() => {
      restoreNotificationCache(snapshot);
      void reconcileNotificationCache();
      toast.error("Could not mark the notification as read.");
    });
  }, [countQueryKey, notifications, queryClient, reconcileNotificationCache, restoreNotificationCache, snapshotNotificationCache, toast, updateNotifications]);

  const [refreshing, setRefreshing] = useState(false);

  const refreshNotifications = useCallback(async () => {
    if (refreshing) return;
    setRefreshing(true);
    try {
      const [refetchResult] = await Promise.all([
        notificationsQuery.refetch(),
        queryClient.invalidateQueries({ queryKey: queryRoots.notifications }),
      ]);
      if (refetchResult.isError) {
        toast.error("Could not refresh notifications.");
      }
    } catch {
      toast.error("Could not refresh notifications.");
    } finally {
      setRefreshing(false);
    }
  }, [notificationsQuery, queryClient, refreshing, toast]);

  return {
    allNotifications: notifications,
    hasNextPage: Boolean(notificationsQuery.hasNextPage),
    loadingMore: notificationsQuery.isFetchingNextPage,
    loadMoreFailed: notificationsQuery.isFetchNextPageError,
    loadMore: notificationsQuery.fetchNextPage,
    loading: notificationsQuery.isLoading,
    refreshing: refreshing || notificationsQuery.isRefetching,
    fetchError: notificationsQuery.isError && !notificationsQuery.data
      ? "Could not load notifications. The backend may not be available."
      : null,
    sseStatus,
    activeFilter,
    setActiveFilter,
    counts,
    markAllRead,
    markRead,
    refreshNotifications,
  };
}

export function useNotificationUnreadCount(user: User) {
  const queryClient = useQueryClient();
  const queryKey = useMemo(() => unreadCountQueryKey(user), [user]);

  const query = useQuery({
    queryKey,
    queryFn: ({ signal }) => getUnreadCount(signal).then((res) => res.data.unreadCount),
    staleTime: UNREAD_COUNT_STALE_TIME_MS,
    // SSE below keeps this live; polling is just a safety net for a dropped
    // stream the reconnect loop hasn't caught up with yet.
    refetchInterval: 3 * 60_000,
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: true,
    meta: authenticatedQueryMeta,
  });

  // The ambient navbar badge (unlike the full Notifications screen) used to
  // rely solely on the 3-minute poll above, so a new notification or a read
  // marked elsewhere could take up to 3 minutes to show here — even though
  // the backend already pushes both over SSE instantly. Subscribe here too.
  useEffect(() => {
    let stopped = false;
    let controller = new AbortController();
    let retryTimer: number | undefined;
    let attempts = 0;
    let connectedAt = 0;

    // Refetches the true count from the server rather than adding/subtracting
    // locally -- this hook and useNotifications (on the Notifications screen)
    // can both be mounted at once, each with its own SSE connection, so the
    // same server-side event reaches this tab twice; arithmetic deltas would
    // double-count, but re-asking the server for the authoritative number is
    // safe no matter how many times it's triggered.
    const refetchCount = () => {
      void queryClient.invalidateQueries({ queryKey, exact: true });
    };

    const connect = () => {
      if (stopped) return;
      const connectionController = new AbortController();
      controller = connectionController;
      let disconnected = false;

      const handleDisconnect = () => {
        if (disconnected || connectionController.signal.aborted) return;
        disconnected = true;
        if (stopped) return;
        if (connectedAt && Date.now() - connectedAt > 10_000) attempts = 0;
        connectedAt = 0;
        const delay = Math.min(2000 * 2 ** Math.min(attempts, 4), 30_000);
        attempts = Math.min(attempts + 1, 4);
        retryTimer = window.setTimeout(connect, delay);
      };

      openNotificationStream(
        {
          onNotification: () => {
            if (stopped || connectionController.signal.aborted) return;
            attempts = 0;
            refetchCount();
          },
          onRead: () => {
            if (stopped || connectionController.signal.aborted) return;
            refetchCount();
          },
          onReadAll: () => {
            if (stopped || connectionController.signal.aborted) return;
            refetchCount();
          },
        },
        () => {
          if (stopped || connectionController.signal.aborted) return;
          connectedAt = Date.now();
        },
        handleDisconnect,
        connectionController.signal,
      );
    };

    connect();

    return () => {
      stopped = true;
      if (retryTimer) window.clearTimeout(retryTimer);
      controller.abort();
    };
  }, [queryClient, queryKey]);

  return query;
}

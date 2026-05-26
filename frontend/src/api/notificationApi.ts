import { api } from "./authApi";

export interface NotificationDto {
  id: string;
  eventType: string;
  message: string;
  deepLink: string | null;
  readAt: string | null;
  createdAt: string;
}

export interface NotificationPageDto {
  items: NotificationDto[];
  totalCount: number;
  page: number;
  pageSize: number;
}

export function listNotifications(signal?: AbortSignal) {
  return api.get<NotificationDto[]>("/notifications", { signal });
}

export function getUnreadCount(signal?: AbortSignal) {
  return api.get<{ unreadCount: number }>("/notifications/unread-count", { signal });
}

export function markNotificationRead(id: string) {
  return api.patch<void>(`/notifications/${id}/read`);
}

export function markAllNotificationsRead() {
  return api.patch<void>("/notifications/read-all");
}

export function getNotificationHistory(page = 0, pageSize = 20, signal?: AbortSignal) {
  return api.get<NotificationPageDto>("/notifications/history", {
    params: { page, pageSize },
    signal,
  });
}

export function openNotificationStream(
  onNotification: (dto: NotificationDto) => void,
  onConnect: () => void,
  onDisconnect: () => void,
  signal: AbortSignal,
): void {
  const BASE_URL = import.meta.env.VITE_API_URL ?? "/api/v1";
  const authHeader = api.defaults.headers.common["Authorization"] as string | undefined;
  if (!authHeader) {
    onDisconnect();
    return;
  }

  fetch(`${BASE_URL}/notifications/stream`, {
    headers: { Authorization: authHeader, Accept: "text/event-stream" },
    signal,
  })
    .then((res) => {
      if (!res.ok || !res.body) {
        onDisconnect();
        return;
      }
      onConnect();
      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";

      function pump(): Promise<void> {
        return reader.read().then(({ done, value }) => {
          if (done) {
            onDisconnect();
            return;
          }
          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split("\n");
          buffer = lines.pop() ?? "";
          let evtName = "";
          let data = "";
          for (const line of lines) {
            if (line.startsWith("event:")) {
              evtName = line.slice(6).trim();
            } else if (line.startsWith("data:")) {
              data = line.slice(5).trim();
            } else if (line === "" && data) {
              if (evtName === "notification") {
                try {
                  onNotification(JSON.parse(data) as NotificationDto);
                } catch {
                  // Ignore malformed SSE payloads and keep the stream open.
                }
              }
              evtName = "";
              data = "";
            }
          }
          return pump();
        });
      }
      return pump();
    })
    .catch((err: { name?: string }) => {
      if (err?.name !== "AbortError") onDisconnect();
    });
}

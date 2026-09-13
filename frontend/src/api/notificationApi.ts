import { api } from "./authApi";

export interface NotificationDto {
  id: string;
  eventType: string;
  message: string;
  deepLink: string | null;
  readAt: string | null;
  createdAt: string;
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

type NotificationSseParser = {
  push: (chunk: string) => void;
};

export function createNotificationSseParser(
  onNotification: (dto: NotificationDto) => void,
): NotificationSseParser {
  let buffer = "";
  let eventName = "";
  let dataLines: string[] = [];

  const dispatchEvent = () => {
    if (eventName === "notification" && dataLines.length > 0) {
      try {
        onNotification(JSON.parse(dataLines.join("\n")) as NotificationDto);
      } catch {
        // Ignore malformed payloads without closing an otherwise healthy stream.
      }
    }
    eventName = "";
    dataLines = [];
  };

  const processLine = (rawLine: string) => {
    const line = rawLine.endsWith("\r") ? rawLine.slice(0, -1) : rawLine;
    if (line === "") {
      dispatchEvent();
      return;
    }
    if (line.startsWith(":")) return;

    const separator = line.indexOf(":");
    const field = separator === -1 ? line : line.slice(0, separator);
    let value = separator === -1 ? "" : line.slice(separator + 1);
    if (value.startsWith(" ")) value = value.slice(1);

    if (field === "event") eventName = value;
    if (field === "data") dataLines.push(value);
  };

  return {
    push(chunk) {
      buffer += chunk;
      let newlineIndex = buffer.indexOf("\n");
      while (newlineIndex >= 0) {
        processLine(buffer.slice(0, newlineIndex));
        buffer = buffer.slice(newlineIndex + 1);
        newlineIndex = buffer.indexOf("\n");
      }
    },
  };
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
      const parser = createNotificationSseParser(onNotification);

      function pump(): Promise<void> {
        return reader.read().then(({ done, value }) => {
          if (done) {
            const finalChunk = decoder.decode();
            if (finalChunk) parser.push(finalChunk);
            onDisconnect();
            return;
          }
          parser.push(decoder.decode(value, { stream: true }));
          return pump();
        });
      }
      return pump();
    })
    .catch((err: { name?: string }) => {
      if (err?.name !== "AbortError") onDisconnect();
    });
}

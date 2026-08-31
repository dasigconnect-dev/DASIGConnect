export type NotificationCategory =
  | "submissions"
  | "publishing"
  | "system"
  | "overrides"
  | "deadline";

export type NotificationFilter = "all" | "unread" | NotificationCategory;

export type SseStatus = "connected" | "connecting" | "disconnected";

export interface NotificationTag {
  label: string;
  badgeClass: string;
}

export interface Notification {
  id: string;
  eventType: string;
  trigger: string;
  category: NotificationCategory;
  unread: boolean;
  critical?: boolean;
  warning?: boolean;
  icon: string;
  iconClass: string;
  sender: string;
  time: string;
  text: string;
  tags: NotificationTag[];
  link: string;
  linkLabel: string;
  group: string;
  createdAt?: string;
}

export const FILTER_LABELS: Record<NotificationFilter, string> = {
  all: "All",
  unread: "Unread",
  submissions: "Submission Updates",
  publishing: "Publishing",
  system: "System",
  overrides: "Overrides",
  deadline: "Deadlines",
};


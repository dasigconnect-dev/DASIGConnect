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
}

export interface AuditEntry {
  type: string;
  typeClass: string;
  detail: string;
  time: string;
}

export const FILTER_LABELS: Record<NotificationFilter, string> = {
  all: "All",
  unread: "Unread",
  submissions: "Submissions",
  publishing: "Publishing",
  system: "System",
  overrides: "Overrides",
  deadline: "Deadlines",
};

export const FILTER_ORDER: NotificationFilter[] = [
  "all",
  "unread",
  "submissions",
  "publishing",
  "system",
  "overrides",
  "deadline",
];

export const GROUP_ORDER: Record<string, number> = {
  Today: 0,
  Yesterday: 1,
  "2 Days Ago": 2,
  "3 Days Ago": 3,
  "4 Days Ago": 4,
  "5 Days Ago": 5,
  "6 Days Ago": 6,
  "Last Week": 7,
};

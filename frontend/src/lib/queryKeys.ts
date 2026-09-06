type ScopedQueryParams = Record<string, unknown>;

function scopedKey<TParams extends ScopedQueryParams>(root: string, params: TParams) {
  return [root, params] as const;
}

export const queryKeys = {
  dashboard: {
    summary: (params: { role: string; userId?: string | null; institutionId?: string | null }) =>
      scopedKey("dashboard", params),
  },
  institutions: {
    all: (params: { role: string; userId?: string | null }) => scopedKey("institutions", params),
    detail: (params: { role: string; userId?: string | null; institutionId: string }) =>
      scopedKey("institutions", { ...params, view: "detail" }),
    composerOptions: (params: { role: string; userId?: string | null }) =>
      scopedKey("institutions", { ...params, view: "composer-options" }),
  },
  users: {
    all: (params: {
      role: string;
      userId?: string | null;
      institutionId?: string | null;
      page?: number;
      pageSize?: number;
      search?: string;
      sort?: string;
      scope?: "network" | "institution";
    }) => scopedKey("users", params),
  },
  administrators: {
    all: (params: { role: string; userId?: string | null; scope: "network" }) =>
      scopedKey("administrators", params),
    pendingInvitations: (params: { role: string; userId?: string | null; scope: "network" }) =>
      scopedKey("administrators", { ...params, view: "pending-invitations" }),
  },
  submissions: {
    all: (params: { role: string; userId?: string | null; institutionId?: string | null; status?: string }) =>
      scopedKey("submissions", params),
    detail: (params: { role: string; userId?: string | null; institutionId?: string | null; submissionId: string }) =>
      scopedKey("submissions", { ...params, view: "detail" }),
    lookups: (params: { role: string; institutionId?: string | null }) =>
      scopedKey("submissions", { ...params, view: "lookups" }),
    templates: (params: { role: string; userId?: string | null; institutionId?: string | null }) =>
      scopedKey("submissions", { ...params, view: "templates" }),
    albumNames: (params: { role: string; userId?: string | null; institutionId: string }) =>
      scopedKey("submissions", { ...params, view: "album-names" }),
  },
  calendarEvents: {
    range: (params: { role: string; userId?: string | null; institutionId?: string | null; startDate: string; endDate: string }) =>
      scopedKey("calendar-events", params),
  },
  mediaAssets: {
    all: (params: {
      role: string;
      userId?: string | null;
      networkView?: boolean;
      institutionId?: string | null;
      albumId?: string | null;
      page?: number;
      pageSize?: number;
      search?: string;
      sort?: string;
      mediaType?: string;
    }) => scopedKey("media-assets", params),
  },
  validation: {
    queue: (params: { role: string; userId?: string | null; scope?: "network" | "institution" | "history"; institutionId?: string | null }) =>
      scopedKey("validation", { ...params, view: "queue" }),
    log: (params: { role: string; userId?: string | null; submissionId: string }) =>
      scopedKey("validation", { ...params, view: "log" }),
  },
  notifications: {
    all: (params: { role: string; userId?: string | null; institutionId?: string | null }) =>
      scopedKey("notifications", params),
    unreadCount: (params: { role: string; userId?: string | null; institutionId?: string | null }) =>
      scopedKey("notifications", { ...params, view: "unread-count" }),
  },
  analytics: {
    summary: (params: { role: string; userId?: string | null; institutionId?: string | null; range: string }) =>
      scopedKey("analytics", params),
  },
  settings: {
    profile: (params: { userId?: string | null }) => scopedKey("settings", { ...params, view: "profile" }),
    page: (params: { role: string; userId?: string | null; institutionId?: string | null }) =>
      scopedKey("settings", { ...params, view: "page" }),
    watermark: (params: { role: string; userId?: string | null; institutionId?: string | null }) =>
      scopedKey("settings", { ...params, view: "watermark" }),
  },
  resolution: {
    failures: (params: { role: string; userId?: string | null; institutionId?: string | null }) =>
      scopedKey("resolution", { ...params, view: "failures" }),
    detail: (params: { role: string; userId?: string | null; institutionId?: string | null; submissionId: string }) =>
      scopedKey("resolution", { ...params, view: "detail" }),
  },
  auditLog: {
    page: (params: {
      role: string;
      userId?: string | null;
      page: number;
      pageSize: number;
      startDate?: string;
      endDate?: string;
      category?: string;
      entityType?: string;
      search?: string;
    }) => scopedKey("audit-log", params),
    metadata: (params: { role: string; userId?: string | null }) =>
      scopedKey("audit-log", { ...params, view: "metadata" }),
  },
  systemHealth: {
    summary: (params: { role: string; userId?: string | null }) => scopedKey("system-health", params),
  },
} as const;

type ScopedQueryParams = Record<string, unknown>;

function scopedKey<TParams extends ScopedQueryParams>(root: string, params: TParams) {
  return [root, params] as const;
}

export const queryKeys = {
  principal: {
    profile: (params: { userId: string }) =>
      scopedKey("principal", { ...params, view: "profile" }),
  },
  messenger: {
    connection: (params: { userId: string }) =>
      scopedKey("messenger", { ...params, view: "connection" }),
  },
  dashboard: {
    resource: (params: { role: string; userId?: string | null; institutionId?: string | null; resource: string }) =>
      scopedKey("dashboard", params),
  },
  institutions: {
    all: (params: { role: string; userId?: string | null }) => scopedKey("institutions", params),
    summaryCounts: (params: { role: string; userId?: string | null }) =>
      scopedKey("institutions", { ...params, view: "summary-counts" }),
    detail: (params: { role: string; userId?: string | null; institutionId: string }) =>
      scopedKey("institutions", { ...params, view: "detail" }),
    pendingInvitations: (params: { role: string; userId?: string | null; institutionId: string }) =>
      scopedKey("institutions", { ...params, view: "pending-invitations" }),
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
    editorDetail: (params: { role: string; userId?: string | null; institutionId?: string | null; submissionId: string }) =>
      scopedKey("submissions", { ...params, view: "editor-detail" }),
    lookups: (params: { role: string; userId: string; institutionId?: string | null }) =>
      scopedKey("submissions", { ...params, view: "lookups" }),
    templates: (params: { role: string; userId?: string | null; institutionId?: string | null }) =>
      scopedKey("submissions", { ...params, view: "templates" }),
    albumNames: (params: { role: string; userId?: string | null; institutionId: string }) =>
      scopedKey("submissions", { ...params, view: "album-names" }),
    engagementRecommendations: (params: { role: string; userId?: string | null; institutionId?: string | null }) =>
      scopedKey("submissions", { ...params, view: "engagement-recommendations" }),
  },
  calendarEvents: {
    all: (params: { role: string; userId?: string | null; institutionId?: string | null }) =>
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
    detail: (params: { role: string; userId?: string | null; assetId: string }) =>
      scopedKey("media-assets", { ...params, view: "detail" }),
    history: (params: { role: string; userId: string; assetId: string }) =>
      scopedKey("media-assets", { ...params, view: "history" }),
  },
  mediaAlbums: {
    all: (params: { role: string; userId?: string | null; institutionId?: string | null; scope?: "network" | "institution" }) =>
      scopedKey("media-albums", params),
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
    report: (params: { role: string; userId?: string | null; institutionId?: string | null; range: string; metric: string }) =>
      scopedKey("analytics", { ...params, view: "report" }),
  },
  ai: {
    similarMedia: (params: { submissionId: string }) =>
      scopedKey("ai", { ...params, view: "similar-media" }),
  },
  settings: {
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
    tokens: (params: { role: string; userId?: string | null }) =>
      scopedKey("system-health", { ...params, view: "tokens" }),
  },
} as const;

export const queryRoots = {
  principal: ["principal"],
  messenger: ["messenger"],
  dashboard: ["dashboard"],
  institutions: ["institutions"],
  users: ["users"],
  administrators: ["administrators"],
  submissions: ["submissions"],
  calendarEvents: ["calendar-events"],
  validation: ["validation"],
  notifications: ["notifications"],
  analytics: ["analytics"],
  resolution: ["resolution"],
} as const;

export const mutationCacheDependencies = {
  identityManagement: [
    queryRoots.users,
    queryRoots.administrators,
    queryRoots.institutions,
    queryRoots.dashboard,
    queryRoots.analytics,
  ],
  validationWorkflow: [
    queryRoots.validation,
    queryRoots.submissions,
    queryRoots.dashboard,
    queryRoots.calendarEvents,
    queryRoots.analytics,
    queryRoots.notifications,
  ],
  resolutionSession: [queryRoots.resolution],
  resolutionOutcome: [
    queryRoots.resolution,
    queryRoots.submissions,
    queryRoots.calendarEvents,
    queryRoots.dashboard,
    queryRoots.validation,
    queryRoots.analytics,
    queryRoots.notifications,
  ],
} as const;

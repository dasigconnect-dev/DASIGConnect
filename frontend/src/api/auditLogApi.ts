import { api } from "./authApi";

export type AuditLogCategory =
  | "APPROVAL"
  | "REJECTION"
  | "EDIT_AND_REVISION"
  | "RESCHEDULE_AND_OVERRIDE"
  | "PUBLISHING"
  | "ACCOUNT_MANAGEMENT"
  | "INSTITUTION_MANAGEMENT"
  | "MEDIA_LIFECYCLE"
  | "CONFIGURATION"
  | "SECURITY"
  | "OTHER";

export type AuditEntityType =
  | "SUBMISSION"
  | "MEDIA_ASSET"
  | "MEDIA_ALBUM"
  | "USER"
  | "INSTITUTION"
  | "FACEBOOK_TOKEN"
  | "WATERMARK_CONFIG"
  | "SYSTEM";

export interface AuditLogActor {
  id: string;
  name: string;
  email: string;
  role: string;
  avatarUrl?: string | null;
  institutionName?: string | null;
}

export interface AuditLogEntity {
  id: string | null;
  type: AuditEntityType;
  typeLabel: string;
  label: string;
  exists: boolean;
  jumpUrl?: string | null;
}

export interface AuditLogClientInfo {
  ipAddress?: string | null;
  userAgent?: string | null;
}

export interface AuditDiffEntry {
  field: string;
  fieldLabel: string;
  fromValue: string;
  toValue: string;
}

export interface AuditLogEntry {
  id: string;
  timestamp: string;
  action: string;
  actionLabel: string;
  category: AuditLogCategory;
  categoryLabel: string;
  actor: AuditLogActor | null;
  entity: AuditLogEntity;
  clientInfo: AuditLogClientInfo;
  summary: string;
  metadata: Record<string, unknown>;
  rawMetadata: string;
  diffs: AuditDiffEntry[];
}

export interface AuditLogFilterParams {
  startDate?: string;
  endDate?: string;
  actorId?: string;
  actorQuery?: string;
  category?: AuditLogCategory;
  action?: string;
  entityType?: AuditEntityType;
  resourceId?: string;
  search?: string;
  page?: number;
  size?: number;
}

export interface AuditLogPageResponse {
  content: AuditLogEntry[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface CategoryOption {
  key: string;
  label: string;
}

export interface AuditMetadataOptions {
  categories: CategoryOption[];
  entityTypes: CategoryOption[];
}

export async function getAuditLogs(
  params: AuditLogFilterParams = {},
  signal?: AbortSignal
): Promise<AuditLogPageResponse> {
  const query = new URLSearchParams();
  if (params.startDate) query.append("startDate", params.startDate);
  if (params.endDate) query.append("endDate", params.endDate);
  if (params.actorId) query.append("actorId", params.actorId);
  if (params.actorQuery) query.append("actorQuery", params.actorQuery);
  if (params.category) query.append("category", params.category);
  if (params.action) query.append("action", params.action);
  if (params.entityType) query.append("entityType", params.entityType);
  if (params.resourceId) query.append("resourceId", params.resourceId);
  if (params.search) query.append("search", params.search);
  if (params.page !== undefined) query.append("page", String(params.page));
  if (params.size !== undefined) query.append("size", String(params.size));

  const url = `/audit-log${query.toString() ? `?${query.toString()}` : ""}`;
  // The shared `api` instance's response interceptor already unwraps the
  // { success, data, error } envelope, so `response.data` IS the page.
  const response = await api.get<AuditLogPageResponse>(url, { signal });
  return response.data;
}

export async function getAuditCategories(signal?: AbortSignal): Promise<AuditMetadataOptions> {
  const response = await api.get<AuditMetadataOptions>("/audit-log/categories", { signal });
  return response.data;
}

export async function downloadAuditLogCsv(params: AuditLogFilterParams = {}): Promise<void> {
  const query = new URLSearchParams();
  if (params.startDate) query.append("startDate", params.startDate);
  if (params.endDate) query.append("endDate", params.endDate);
  if (params.actorId) query.append("actorId", params.actorId);
  if (params.actorQuery) query.append("actorQuery", params.actorQuery);
  if (params.category) query.append("category", params.category);
  if (params.action) query.append("action", params.action);
  if (params.entityType) query.append("entityType", params.entityType);
  if (params.resourceId) query.append("resourceId", params.resourceId);
  if (params.search) query.append("search", params.search);

  const url = `/audit-log/export-csv${query.toString() ? `?${query.toString()}` : ""}`;
  const response = await api.get<string>(url, {
    responseType: "text",
  });

  const blob = new Blob([response.data], { type: "text/csv;charset=utf-8" });
  const downloadUrl = URL.createObjectURL(blob);
  const link = document.createElement("a");
  const contentDisposition = response.headers["content-disposition"];
  const headerFilename = typeof contentDisposition === "string"
    ? contentDisposition.match(/filename="?([^"]+)"?/)?.[1]
    : null;
  link.href = downloadUrl;
  link.download = headerFilename ?? `DASIGConnect_AuditLog_${new Date().toISOString().split("T")[0]}.csv`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(downloadUrl);
}

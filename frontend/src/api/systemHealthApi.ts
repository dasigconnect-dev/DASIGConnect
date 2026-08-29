import { api } from "./authApi";

export type HealthStatus = "HEALTHY" | "WARNING" | "UNHEALTHY" | "UNAVAILABLE" | "SCHEDULED";

export interface StorageMetric {
  name: string;
  status: HealthStatus;
  usedBytes: number;
  limitBytes: number;
  usedPercent: number;
  warningThresholdPercent: number;
  detail: string;
}

export interface ExternalServiceHealth {
  service: string;
  status: HealthStatus;
  detail: string;
  checkedAt: string;
  expiresAt: string | null;
  secondsUntilExpiry: number | null;
}

export interface BackgroundJobHealth {
  jobName: string;
  status: HealthStatus;
  lastStartedAt: string | null;
  lastSuccessAt: string | null;
  lastFailureAt: string | null;
  lastDurationMs: number | null;
  lastError: string | null;
  detail: string;
}

export interface OperationalMetric {
  key: string;
  label: string;
  status: HealthStatus;
  value: number;
  unit: string;
  sampleSize: number;
  detail: string;
}

export interface SystemHealthSummary {
  generatedAt: string;
  overallStatus: HealthStatus;
  storage: StorageMetric[];
  externalServices: ExternalServiceHealth[];
  backgroundJobs: BackgroundJobHealth[];
  operationalMetrics: OperationalMetric[];
  warningCount: number;
  unhealthyCount: number;
  unavailableCount: number;
}

export interface TokenStatus {
  id: string;
  pageId: string;
  tokenStatus: "ACTIVE" | "EXPIRING" | "EXPIRED" | "INVALID";
  expiresAt: string | null;
  lastValidatedAt: string | null;
}

export function getSystemHealthSummary(signal?: AbortSignal) {
  return api.get<SystemHealthSummary>("/system-health/summary", { signal });
}

export function getSystemHealthTokens(signal?: AbortSignal) {
  return api.get<TokenStatus[]>("/system-health/tokens", { signal });
}

/** Runs the Facebook token health check now (bypasses its daily cron) and returns the refreshed jobs. */
export function recheckSystemHealthTokens() {
  return api.post<BackgroundJobHealth[]>("/system-health/tokens/recheck");
}

export function initSystemHealthOAuth(tokenId: string) {
  return api.get<{ authorizationUrl: string }>(`/system-health/tokens/${tokenId}/oauth-init`);
}

export async function downloadSystemHealthSnapshot() {
  const response = await api.get<string>("/system-health/export", {
    responseType: "text",
  });
  const blob = new Blob([response.data], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  const contentDisposition = response.headers["content-disposition"];
  const headerFilename = typeof contentDisposition === "string"
    ? contentDisposition.match(/filename="?([^"]+)"?/)?.[1]
    : null;
  link.href = url;
  link.download = headerFilename ?? "DASIGConnect_System_Health.csv";
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

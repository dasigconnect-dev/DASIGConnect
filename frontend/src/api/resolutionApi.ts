import { api } from "./authApi";

// ── UC-3.4 types ─────────────────────────────────────────────────────────────

export interface FailedPublication {
  submissionId: string;
  eventTitle: string;
  institutionId: string;
  institutionName: string;
  scheduledAt: string | null;
  retryCount: number;
  lastAttemptAt: string | null;
  lastError: string | null;
  manualPublishInProgress: boolean;
  lastManualPublishAbandonedAt: string | null;
}

export interface ManualPublishMediaItem {
  id: string;
  storageUrl: string;
  fileType: string;
  fileName: string;
  displayOrder: number;
}

export interface ManualPublishDetail {
  submissionId: string;
  eventTitle: string;
  caption: string | null;
  status: string;
  scheduledAt: string | null;
  contributorFirstName: string | null;
  contributorLastName: string | null;
  contributorEmail: string;
  institutionId: string;
  institutionName: string;
  mediaAssets: ManualPublishMediaItem[];
  manualPublishInProgress: boolean;
  manualPublishStartedAt: string | null;
  lastManualPublishAbandonedAt: string | null;
}

export interface ManualPublishCompletePayload {
  postUrl?: string;
  notes?: string;
}

// ── UC-3.5 types ─────────────────────────────────────────────────────────────

export interface ResolutionCounts {
  failures: number;
  timeouts: number;
  overrides: number;
}

export interface TimeoutEscalation {
  submissionId: string;
  eventTitle: string;
  institutionName: string;
  contributorFirstName: string | null;
  contributorLastName: string | null;
  contributorEmail: string;
  submittedAt: string | null;
  scheduledAt: string | null;
  status: string;
}

export interface OverrideRequest {
  id: string;
  submissionId: string;
  eventTitle: string;
  contributorFirstName: string | null;
  contributorLastName: string | null;
  contributorEmail: string;
  institutionName: string;
  requestedSlot: string;
  violatedRule: string;
  overrideReason: string | null;
  decision: string;
  createdAt: string;
  overrideRequestCount: number;
}

export interface TokenStatus {
  id: string;
  pageId: string;
  tokenStatus: "ACTIVE" | "EXPIRING" | "EXPIRED" | "INVALID";
  expiresAt: string | null;
  lastValidatedAt: string | null;
}

export interface DirectPostPayload {
  institutionId: string;
  caption: string;
  mediaAssetIds: string[];
  publishImmediately: boolean;
  scheduledAt?: string;
  reason: string;
  acknowledgedGrH1Conflict: boolean;
}

export interface DirectPostResponse {
  submissionId: string;
  status: string;
  grH1ConflictWarning: boolean;
}

export interface TimeoutRejectPayload {
  reasonCode: string;
  notes?: string;
}

export interface OverrideSuggestPayload {
  suggestedSlot: string;
  message?: string;
}

export interface OverrideDenyPayload {
  reason?: string;
}

// ── UC-3.4 functions ──────────────────────────────────────────────────────────

export function getResolutionFailures(signal?: AbortSignal) {
  return api.get<FailedPublication[]>("/resolution/failures", { signal });
}

export function getResolutionDetail(id: string, signal?: AbortSignal) {
  return api.get<ManualPublishDetail>(`/resolution/${id}`, { signal });
}

export interface RetryWithNewSchedulePayload {
  scheduledAt: string;
  overrideReason?: string;
}

export function retryPublication(id: string) {
  return api.post<void>(`/resolution/${id}/retry`);
}

export function retryPublicationWithNewSchedule(id: string, payload: RetryWithNewSchedulePayload) {
  return api.post<void>(`/resolution/${id}/retry-with-new-schedule`, payload);
}

export function startManualPublish(id: string) {
  return api.post<void>(`/resolution/${id}/manual-publish/start`);
}

export function completeManualPublish(id: string, payload: ManualPublishCompletePayload) {
  return api.post<void>(`/resolution/${id}/manual-publish/complete`, payload);
}

export function cancelManualPublish(id: string) {
  return api.post<void>(`/resolution/${id}/manual-publish/cancel`);
}

// ── UC-3.5 functions ──────────────────────────────────────────────────────────

export function getResolutionCounts(signal?: AbortSignal) {
  return api.get<ResolutionCounts>("/admin/resolution/counts", { signal });
}

// Cat. B — Validation Timeouts
export function getTimeoutEscalations(signal?: AbortSignal) {
  return api.get<TimeoutEscalation[]>("/admin/resolution/timeouts", { signal });
}

export function approveTimeout(submissionId: string) {
  return api.post<void>(`/admin/resolution/timeouts/${submissionId}/approve`);
}

export function deferTimeout(submissionId: string) {
  return api.post<void>(`/admin/resolution/timeouts/${submissionId}/defer`);
}

export function rejectTimeout(submissionId: string, payload: TimeoutRejectPayload) {
  return api.post<void>(`/admin/resolution/timeouts/${submissionId}/reject`, payload);
}

// Cat. C — Override Requests
export function getOverrideRequests(signal?: AbortSignal) {
  return api.get<OverrideRequest[]>("/admin/resolution/overrides", { signal });
}

export function approveOverride(requestId: string) {
  return api.post<void>(`/admin/resolution/overrides/${requestId}/approve`);
}

export function suggestOverride(requestId: string, payload: OverrideSuggestPayload) {
  return api.post<void>(`/admin/resolution/overrides/${requestId}/suggest`, payload);
}

export function denyOverride(requestId: string, payload: OverrideDenyPayload) {
  return api.post<void>(`/admin/resolution/overrides/${requestId}/deny`, payload);
}

// Cat. D — Direct Post
export function createDirectPost(payload: DirectPostPayload) {
  return api.post<DirectPostResponse>("/admin/resolution/direct-post", payload);
}

// Cat. E — Token Management
export function getTokenStatuses(signal?: AbortSignal) {
  return api.get<TokenStatus[]>("/admin/resolution/tokens", { signal });
}

export function initOAuth(tokenId: string) {
  return api.get<{ authorizationUrl: string }>(`/admin/resolution/tokens/${tokenId}/oauth-init`);
}

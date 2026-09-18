import { api } from "./authApi";

// Failed-publication recovery. Surfaced in the Review Queue "Failed" tab
// (the standalone Resolution Center was removed; retry + manual-publish
// fallback moved here).

export interface FailedPublication {
  submissionId: string;
  eventTitle: string;
  status: string;
  institutionId: string;
  institutionName: string;
  scheduledAt: string | null;
  retryCount: number;
  lastAttemptAt: string | null;
  lastError: string | null;
  manualPublishInProgress: boolean;
  lastManualPublishAbandonedAt: string | null;
  /** True if this was a Live Event submission — gates the Publishing Mode toggle on retry. */
  fastTrack: boolean;
  /** JSON array string of Facebook photo IDs staged during the last attempt that couldn't be deleted after a failure — orphaned on the Page, need manual cleanup. Null when none. */
  unresolvedPhotoIds: string | null;
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

export interface RetryWithNewSchedulePayload {
  scheduledAt: string;
  overrideReason?: string;
}

export function getResolutionFailures(signal?: AbortSignal) {
  return api.get<FailedPublication[]>("/resolution/failures", { signal });
}

export function getResolutionDetail(id: string, signal?: AbortSignal) {
  return api.get<ManualPublishDetail>(`/resolution/${id}`, { signal });
}

export function retryPublicationWithNewSchedule(id: string, payload: RetryWithNewSchedulePayload) {
  return api.post<void>(`/resolution/${id}/retry-with-new-schedule`, payload);
}

/** Retries a failed publish exactly as it was — no schedule, no mode change. */
export function retryPublication(id: string) {
  return api.post<void>(`/resolution/${id}/retry`);
}

/** Admin-only: overrides a Scheduled failed publish to Live Event and retries immediately. */
export function retryPublicationAsLive(id: string) {
  return api.post<void>(`/resolution/${id}/retry-as-live`);
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

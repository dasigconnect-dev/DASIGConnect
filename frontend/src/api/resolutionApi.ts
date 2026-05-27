import { api } from "./authApi";

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
}

export interface ManualPublishCompletePayload {
  postUrl?: string;
  notes?: string;
}

export function getResolutionFailures(signal?: AbortSignal) {
  return api.get<FailedPublication[]>("/resolution/failures", { signal });
}

export function getResolutionDetail(id: string, signal?: AbortSignal) {
  return api.get<ManualPublishDetail>(`/resolution/${id}`, { signal });
}

export function retryPublication(id: string) {
  return api.post<void>(`/resolution/${id}/retry`);
}

export function startManualPublish(id: string) {
  return api.post<void>(`/resolution/${id}/manual-publish/start`);
}

export function completeManualPublish(
  id: string,
  payload: ManualPublishCompletePayload,
) {
  return api.post<void>(`/resolution/${id}/manual-publish/complete`, payload);
}

export function cancelManualPublish(id: string) {
  return api.post<void>(`/resolution/${id}/manual-publish/cancel`);
}

import { api } from "./authApi";
import type { SubmissionSummary } from "./submissionApi";

export interface ReviewLock {
  id: string;
  submissionId: string;
  lockedById: string;
  lockedByEmail: string;
  acquiredAt: string;
  expiresAt: string;
}

export interface ValidationLog {
  id: string;
  submissionId: string;
  validatorId: string;
  validatorEmail: string;
  action: string;
  remarks?: string | null;
  rejectionReason?: string | null;
  selfReview?: boolean;
  fastTrack?: boolean;
  editDiff?: string | null;
  createdAt: string;
}

export interface RevisionPayload {
  remarks: string;
}

export interface EditSubmissionPayload {
  eventTitle?: string;
  eventDate?: string;
  caption?: string;
  description?: string;
  category?: string;
  tags?: string[];
  scheduledAt?: string;
  /** Admin only: reason for bypassing a hard guard rail on the new slot (audited). */
  overrideReason?: string;
}

export interface RejectionPayload {
  reasonCode: RejectionReasonCode;
  notes?: string;
}

export type RejectionReasonCode =
  | "INCOMPLETE_CONTENT"
  | "INAPPROPRIATE_CONTENT"
  | "WRONG_FORMAT"
  | "DUPLICATE_EVENT"
  | "WRONG_INSTITUTION"
  | "OTHER";

export function getValidationQueue(options?: { history?: boolean; signal?: AbortSignal }) {
  return api.get<SubmissionSummary[]>("/validation/queue", {
    params: options?.history ? { history: true } : undefined,
    signal: options?.signal,
  });
}

export function getReviewLockStatus(submissionId: string, signal?: AbortSignal) {
  return api.get<ReviewLock | null>(`/validation/${submissionId}/lock`, { signal });
}

export function acquireReviewLock(submissionId: string) {
  return api.post<ReviewLock>(`/validation/${submissionId}/lock`);
}

export function releaseReviewLock(submissionId: string) {
  return api.delete<void>(`/validation/${submissionId}/lock`);
}

export function approveSubmission(submissionId: string) {
  return api.post<void>(`/validation/${submissionId}/approve`);
}

export function editSubmission(
  submissionId: string,
  payload: EditSubmissionPayload,
) {
  return api.post<void>(`/validation/${submissionId}/edit`, payload);
}

export function requestSubmissionRevision(
  submissionId: string,
  payload: RevisionPayload,
) {
  return api.post<void>(`/validation/${submissionId}/revise`, payload);
}

export function rejectSubmission(
  submissionId: string,
  payload: RejectionPayload,
) {
  return api.post<void>(`/validation/${submissionId}/reject`, payload);
}

export function getValidationLog(submissionId: string, signal?: AbortSignal) {
  return api.get<ValidationLog[]>(`/validation/${submissionId}/log`, {
    signal,
  });
}

// ── A9: media edits during review (admin only) ──────────────────────────────

function safeFileName(name: string) {
  return name.replace(/[^a-zA-Z0-9._-]/g, "-");
}

function fileType(file: File) {
  const ext = file.name.split(".").pop()?.toLowerCase() || file.type.split("/")[1]?.toLowerCase() || "jpeg";
  return ext === "jpg" ? "jpeg" : ext;
}

/** Upload device files straight to Supabase, then attach each to the in-review submission. */
export async function uploadValidationMedia(
  submissionId: string,
  files: File[],
  albumName?: string,
) {
  let last;
  for (const file of files) {
    const { data } = await api.post<{ signedUrl: string; publicUrl: string; path: string }>(
      `/validation/${submissionId}/media/upload-url`,
      { fileName: safeFileName(file.name), fileType: fileType(file), fileSizeBytes: file.size },
    );
    const put = await fetch(data.signedUrl, {
      method: "PUT",
      headers: { "Content-Type": file.type || "application/octet-stream" },
      body: file,
    });
    if (!put.ok) throw new Error((await put.text().catch(() => "")) || "Supabase media upload failed.");
    last = await api.post(`/validation/${submissionId}/media`, {
      storageUrl: data.publicUrl,
      fileName: file.name,
      fileType: fileType(file),
      fileSizeBytes: file.size,
      albumName: albumName?.trim() || undefined,
    });
  }
  return last;
}

export function attachValidationLibraryAsset(submissionId: string, mediaAssetId: string) {
  return api.post<void>(`/validation/${submissionId}/assets`, { mediaAssetId });
}

export function detachValidationAsset(submissionId: string, mediaAssetId: string) {
  return api.delete<void>(`/validation/${submissionId}/assets/${mediaAssetId}`);
}

export function reorderValidationMedia(
  submissionId: string,
  mediaAssetIds: string[],
  mediaCaptions?: Record<string, string>,
  skipWatermarks?: Record<string, boolean>,
) {
  return api.patch<void>(`/validation/${submissionId}/media/order`, {
    mediaAssetIds,
    mediaCaptions,
    skipWatermarks,
  });
}

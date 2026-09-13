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
  /** A10 governance tier for `edited` / `media_added` rows: quiet | flagged | added_media. */
  editSeverity?: string | null;
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

// ── A9/A10: media edits during review ───────────────────────────────────────
// Moderators may only attach existing, already-vetted Media Library assets.
// Uploading a fresh device file into someone else's submission during review is
// not allowed — new media goes back to the contributor via Request Revision.

/**
 * Attach a Media Library asset to an in-review submission. When the asset was not
 * part of the original submission, `justification` is an optional short note the
 * reviewing moderator can leave — it is recorded on the distinct `media_added`
 * audit event.
 */
export function attachValidationLibraryAsset(
  submissionId: string,
  mediaAssetId: string,
  justification?: string,
) {
  return api.post<void>(`/validation/${submissionId}/assets`, {
    mediaAssetId,
    ...(justification?.trim() ? { justification: justification.trim() } : {}),
  });
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

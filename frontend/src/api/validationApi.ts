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
  /**
   * Admin-only publishing-mode override. Omit unless an Admin explicitly toggled
   * it — the backend rejects this field from a Moderator with a 403.
   */
  fastTrack?: boolean;
}

export interface RejectionPayload {
  reasonCode: RejectionReasonCode;
  notes?: string;
}

/**
 * Reasons a reviewer can reject with (BR-VAL-03) — only problems a revision
 * can't fix; anything missing or fixable goes through Request Revision.
 * Must match ValidationService.REJECTION_REASON_LABELS.
 */
export type RejectionReasonCode =
  | "INAPPROPRIATE_CONTENT"
  | "OUT_OF_SCOPE"
  | "DUPLICATE_EVENT"
  | "NO_LONGER_RELEVANT"
  | "RIGHTS_OR_PRIVACY"
  | "WRONG_INSTITUTION"
  | "OTHER";

export type ValidationQueueView =
  | "all"
  | "pending"
  | "in_review"
  | "needs_revision"
  | "scheduled"
  | "published"
  | "rejected";

export type ValidationQueueSort = "publish_slot" | "submitted";

export interface ValidationQueueCounts {
  all: number;
  pending: number;
  in_review: number;
  needs_revision: number;
  scheduled: number;
  published: number;
  rejected: number;
}

export interface ValidationQueuePage {
  items: SubmissionSummary[];
  page: number;
  pageSize: number;
  totalCount: number;
  totalPages: number;
  hasNext: boolean;
  counts: ValidationQueueCounts;
}

export interface ValidationDashboardSummary {
  awaitingReview: number;
  approvedThisMonth: number;
  rejectedThisMonth: number;
  contributorCount: number;
}

export function getValidationDashboardSummary(signal?: AbortSignal) {
  return api.get<ValidationDashboardSummary>("/validation/dashboard-summary", { signal });
}

export function getValidationQueuePage(
  params: {
    view: ValidationQueueView;
    sort: ValidationQueueSort;
    page?: number;
    pageSize?: number;
    search?: string;
  },
  signal?: AbortSignal,
) {
  return api.get<ValidationQueuePage>("/validation/queue/page", {
    params: {
      view: params.view,
      sort: params.sort,
      page: params.page ?? 0,
      pageSize: params.pageSize ?? 20,
      search: params.search?.trim() ?? "",
    },
    signal,
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

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
  validatorInstitutionId?: string | null;
  validatorInstitutionName?: string | null;
  action: string;
  remarks?: string | null;
  rejectionReason?: string | null;
  createdAt: string;
}

export interface RevisionPayload {
  remarks: string;
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

export function getValidationQueue(signal?: AbortSignal) {
  return api.get<SubmissionSummary[]>("/validation/queue", { signal });
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

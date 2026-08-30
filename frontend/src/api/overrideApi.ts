import { api } from "./authApi";

/**
 * Guard-rail override requests. A moderator rescheduling onto a hard-blocked slot
 * raises one; an administrator approves / suggests an alternative / denies it.
 * (Admins bypass guard rails directly and never file a request; contributors
 * can neither request nor bypass.)
 */

export type OverrideDecision =
  | "pending"
  | "approved"
  | "denied"
  | "suggested"
  | "expired";

export interface OverrideRequest {
  id: string;
  submissionId: string;
  eventTitle: string;
  contributorFirstName: string | null;
  contributorLastName: string | null;
  contributorEmail: string;
  institutionName: string;
  requestedByName: string | null;
  requestedSlot: string;
  violatedRule: string;
  overrideReason: string;
  decision: OverrideDecision;
  decisionReason: string | null;
  suggestedSlot: string | null;
  createdAt: string;
  overrideRequestCount: number;
}

export interface CreateOverrideRequestPayload {
  submissionId: string;
  requestedSlot: string;
  reason: string;
}

/** MODERATOR — raise a request for a hard-blocked reschedule. */
export function createOverrideRequest(payload: CreateOverrideRequestPayload) {
  return api.post<OverrideRequest>("/override-requests", payload);
}

/** The submission's pending override request, if any (empty array = none). */
export function getOverrideRequestForSubmission(submissionId: string, signal?: AbortSignal) {
  return api.get<OverrideRequest[]>(`/override-requests/for-submission/${submissionId}`, { signal });
}

/** ADMIN — every pending override request, soonest requested slot first. */
export function listPendingOverrideRequests(signal?: AbortSignal) {
  return api.get<OverrideRequest[]>("/override-requests", { signal });
}

/** ADMIN — approve: bypasses the rule, reserves the slot, moves the submission onto it. */
export function approveOverrideRequest(id: string) {
  return api.post<void>(`/override-requests/${id}/approve`);
}

/** ADMIN — propose a compliant alternative slot back to the requester. */
export function suggestOverrideSlot(id: string, suggestedSlot: string, message?: string) {
  return api.post<void>(`/override-requests/${id}/suggest`, { suggestedSlot, message });
}

/** ADMIN — deny; the submission keeps its current slot and the hard rule stands. */
export function denyOverrideRequest(id: string, reason?: string) {
  return api.post<void>(`/override-requests/${id}/deny`, { reason });
}

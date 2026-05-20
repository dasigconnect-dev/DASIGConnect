import { api } from './authApi'

export type SubmissionStatus =
  | 'draft'
  | 'pending'
  | 'in_review'
  | 'needs_revision'
  | 'scheduled'
  | 'publish_failed'
  | 'published'
  | 'published_manual'
  | 'admin_direct_post'
  | 'rejected'

export interface SubmissionSummary {
  id: string
  institutionId: string
  institutionName?: string
  eventTitle: string
  eventDate: string
  caption?: string
  description?: string
  status: SubmissionStatus
  scheduledAt?: string
  submittedAt?: string
  updatedAt?: string
  mediaCount?: number
}

export interface SubmissionPayload {
  eventTitle: string
  eventDate: string
  caption: string
  description: string
  scheduledAt?: string
}

export interface SubmissionLookups {
  categories: Array<{ value: string; label: string }>
  tags: Array<{ value: string; label: string }>
  preferredTimes: string[]
  facebookPageName?: string
}

export interface GuardRailViolation {
  code: string
  message: string
  suggestedSlots?: string[]
}

export interface GuardRailResult {
  hardViolations: GuardRailViolation[]
  softWarnings: GuardRailViolation[]
  passed: boolean
}

export function listSubmissions(signal?: AbortSignal) {
  return api.get<SubmissionSummary[]>('/submissions', { signal })
}

export function getSubmission(id: string, signal?: AbortSignal) {
  return api.get<SubmissionSummary>(`/submissions/${id}`, { signal })
}

export function createDraft(payload: SubmissionPayload) {
  return api.post<SubmissionSummary>('/submissions/drafts', payload)
}

export function updateDraft(id: string, payload: SubmissionPayload) {
  return api.patch<SubmissionSummary>(`/submissions/${id}`, payload)
}

export function submitForReview(id: string) {
  return api.post<SubmissionSummary>(`/submissions/${id}/submit`)
}

export function deleteDraft(id: string) {
  return api.delete<void>(`/submissions/${id}`)
}

export function uploadSubmissionMedia(id: string, files: File[]) {
  const data = new FormData()
  files.forEach((file) => data.append('files', file))
  return api.post(`/submissions/${id}/media`, data, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function getSubmissionLookups(signal?: AbortSignal) {
  return api.get<SubmissionLookups>('/submissions/lookups', { signal })
}

export function validateGuardRails(institutionId: string, scheduledAt: string) {
  return api.post<GuardRailResult>('/guardrails/validate', {
    institutionId,
    scheduledAt,
  })
}

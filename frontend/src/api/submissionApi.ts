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
  allowedFileTypes: string[]
  allowedImageTypes: string[]
  allowedVideoTypes: string[]
  maxFileSizeMb: number
  maxMediaAssetsPerSubmission: number
  maxTitleLength: number
  minScheduleLeadTimeHours: number
  maxScheduleDaysAhead: number
}

export interface GuardRailViolation {
  code: string
  message: string
  suggestedSlots?: string[]
}

export interface GuardRailResult {
  hardBlocks: GuardRailViolation[]
  softWarnings: GuardRailViolation[]
  blocked: boolean
  clean: boolean
}

export function listSubmissions(signal?: AbortSignal) {
  return api.get<SubmissionSummary[]>('/submissions', { signal })
}

export function getSubmission(id: string, signal?: AbortSignal) {
  return api.get<SubmissionSummary>(`/submissions/${id}`, { signal })
}

export function createDraft(payload: SubmissionPayload) {
  return api.post<SubmissionSummary>('/submissions', payload)
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

export async function uploadSubmissionMedia(id: string, files: File[]) {
  const responses = []
  for (const file of files) {
    const storageUrl = await uploadToSupabaseStorage(id, file)
    responses.push(await api.post(`/submissions/${id}/media`, {
      storageUrl,
      fileName: file.name,
      fileType: fileTypeFromFile(file),
      fileSizeBytes: file.size,
    }))
  }
  return responses.at(-1)
}

export function getSubmissionLookups(signal?: AbortSignal) {
  return api.get<SubmissionLookups>('/submissions/lookups', { signal })
}

export function validateGuardRails(submissionId: string, scheduledAt: string) {
  return api.post<GuardRailResult>(`/submissions/${submissionId}/evaluate-slot`, { scheduledAt })
}

async function uploadToSupabaseStorage(submissionId: string, file: File) {
  const supabaseUrl = import.meta.env.VITE_SUPABASE_URL
  const bucket = import.meta.env.VITE_SUPABASE_STORAGE_BUCKET
  const anonKey = import.meta.env.VITE_SUPABASE_ANON_KEY

  if (!supabaseUrl || !bucket || !anonKey) {
    throw new Error('Supabase upload is not configured. Set VITE_SUPABASE_URL, VITE_SUPABASE_STORAGE_BUCKET, and VITE_SUPABASE_ANON_KEY.')
  }

  const path = `${submissionId}/${crypto.randomUUID()}-${safeFileName(file.name)}`
  const uploadUrl = `${supabaseUrl.replace(/\/$/, '')}/storage/v1/object/${bucket}/${path}`
  const response = await fetch(uploadUrl, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${anonKey}`,
      apikey: anonKey,
      'Content-Type': file.type || 'application/octet-stream',
      'x-upsert': 'false',
    },
    body: file,
  })

  if (!response.ok) {
    const message = await response.text().catch(() => '')
    throw new Error(message || 'Supabase media upload failed.')
  }

  return `${supabaseUrl.replace(/\/$/, '')}/storage/v1/object/public/${bucket}/${path}`
}

function fileTypeFromFile(file: File) {
  const extension = file.name.split('.').pop()?.toLowerCase()
  if (extension) return extension
  const subtype = file.type.split('/')[1]?.toLowerCase()
  return subtype || 'jpeg'
}

function safeFileName(fileName: string) {
  return fileName.replace(/[^a-zA-Z0-9._-]/g, '-')
}

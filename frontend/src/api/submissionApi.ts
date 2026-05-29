import { api } from "./authApi";

export type SubmissionStatus =
  | "draft"
  | "pending"
  | "in_review"
  | "needs_revision"
  | "scheduled"
  | "publishing"
  | "publish_failed"
  | "published"
  | "published_manual"
  | "admin_direct_post"
  | "direct_post_scheduled"
  | "direct_post_publishing"
  | "direct_post_failed"
  | "rejected";

export interface SavedMediaAsset {
  id: string;
  storageUrl: string;
  fileName: string;
  fileType: string;
  fileSizeBytes: number;
}

export interface SubmissionSummary {
  id: string;
  institutionId: string;
  institutionName?: string;
  contributorEmail?: string;
  eventTitle: string;
  eventDate: string;
  caption?: string;
  description?: string;
  status: SubmissionStatus;
  scheduledAt?: string;
  submittedAt?: string;
  createdAt?: string;
  updatedAt?: string;
  mediaCount?: number;
  category?: string;
  tags?: string[];
  mediaAssets?: SavedMediaAsset[];
}

export interface SubmissionPayload {
  eventTitle: string;
  eventDate: string;
  caption: string;
  description: string;
  scheduledAt?: string;
  category?: string;
  tags?: string[];
}

export interface SubmissionLookups {
  allowedFileTypes: string[];
  allowedImageTypes: string[];
  allowedVideoTypes: string[];
  maxFileSizeMb: number;
  maxMediaAssetsPerSubmission: number;
  maxTitleLength: number;
  minScheduleLeadTimeHours: number;
  maxScheduleDaysAhead: number;
  categories: string[];
  availableTags: string[];
}

export interface GuardRailViolation {
  code: string;
  message: string;
  suggestedSlots?: string[];
}

export interface GuardRailResult {
  hardBlocks: GuardRailViolation[];
  softWarnings: GuardRailViolation[];
  blocked: boolean;
  clean: boolean;
}

export function listSubmissions(signal?: AbortSignal) {
  return api.get<SubmissionSummary[]>("/submissions", { signal });
}

export function getSubmission(id: string, signal?: AbortSignal) {
  return api.get<SubmissionSummary>(`/submissions/${id}`, { signal });
}

export function createDraft(payload: SubmissionPayload) {
  return api.post<SubmissionSummary>("/submissions", payload);
}

export function updateDraft(id: string, payload: SubmissionPayload) {
  return api.patch<SubmissionSummary>(`/submissions/${id}`, payload);
}

export function submitForReview(id: string) {
  return api.post<SubmissionSummary>(`/submissions/${id}/submit`);
}

export function deleteDraft(id: string) {
  return api.delete<void>(`/submissions/${id}`);
}

export function reorderSubmissionMedia(id: string, mediaAssetIds: string[]) {
  return api.patch<SubmissionSummary>(`/submissions/${id}/media/order`, {
    mediaAssetIds,
  });
}

export function attachAsset(id: string, mediaAssetId: string) {
  return api.post<SubmissionSummary>(`/submissions/${id}/assets`, {
    mediaAssetId,
  });
}

export function detachAsset(id: string, mediaAssetId: string) {
  return api.delete(`/submissions/${id}/assets/${mediaAssetId}`);
}

export async function uploadSubmissionMedia(id: string, files: File[]) {
  const responses = [];
  for (const file of files) {
    const {
      data: { signedUrl, publicUrl },
    } = await api.post<{ signedUrl: string; publicUrl: string; path: string }>(
      `/submissions/${id}/media/upload-url`,
      { fileName: safeFileName(file.name), fileType: fileTypeFromFile(file) },
    );
    const upload = await fetch(signedUrl, {
      method: "PUT",
      headers: { "Content-Type": file.type || "application/octet-stream" },
      body: file,
    });
    if (!upload.ok) {
      const msg = await upload.text().catch(() => "");
      throw new Error(msg || "Supabase media upload failed.");
    }
    responses.push(
      await api.post(`/submissions/${id}/media`, {
        storageUrl: publicUrl,
        fileName: file.name,
        fileType: fileTypeFromFile(file),
        fileSizeBytes: file.size,
      }),
    );
  }
  return responses.at(-1);
}

export function getSubmissionLookups(signal?: AbortSignal) {
  return api.get<SubmissionLookups>("/submissions/lookups", { signal });
}

export function validateGuardRails(scheduledAt: string) {
  return api.post<GuardRailResult>("/guardrails/validate", { scheduledAt });
}

function fileTypeFromFile(file: File) {
  const extension = file.name.split(".").pop()?.toLowerCase();
  if (extension) return normalizeFileType(extension);
  const subtype = file.type.split("/")[1]?.toLowerCase();
  return normalizeFileType(subtype || "jpeg");
}

function normalizeFileType(fileType: string) {
  return fileType === "jpg" ? "jpeg" : fileType;
}

function safeFileName(fileName: string) {
  return fileName.replace(/[^a-zA-Z0-9._-]/g, "-");
}

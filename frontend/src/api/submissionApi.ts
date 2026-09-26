import { api } from "./authApi";
import { fetchWithDeadline } from "./requestPolicy";

export type SubmissionStatus =
  | "draft"
  | "pending"
  | "in_review"
  | "needs_revision"
  | "missed_review"
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
  caption?: string | null;
  skipWatermark?: boolean;
  /** MediaAssetStatus name. "STAGED" = uploaded to this draft, not yet bound to an institution. */
  status?: string;
  processingVersion?: string | null;
  /** Media Library album this asset is filed under (null while STAGED). */
  albumId?: string | null;
  albumName?: string | null;
  /** The institution this asset's library row belongs to (null while STAGED). */
  institutionId?: string | null;
  institutionName?: string | null;
}

export interface SubmissionMediaPreview {
  id: string;
  storageUrl?: string | null;
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
  publishedAt?: string;
  submittedAt?: string;
  createdAt?: string;
  updatedAt?: string;
  mediaCount?: number;
  previewMediaAsset?: SubmissionMediaPreview | null;
  category?: string;
  templateId?: string | null;
  fastTrack?: boolean;
  liveEventName?: string | null;
  tags?: string[];
  albumName?: string | null;
  mediaTags?: string[];
  mediaAssets?: SavedMediaAsset[];
  mediaProcessing?: {
    total: number;
    ready: number;
    processing: number;
    failed: number;
  };
  requiresManualPublishing?: boolean;
  /** Reviewer's reason, present when status is "rejected". */
  rejectionReason?: string | null;
  /** Reviewer's notes, present when status is "needs_revision". */
  validatorRemarks?: string | null;
}

export type SubmissionQueueBucket =
  | "all"
  | "drafts"
  | "action-needed"
  | "rejected"
  | "under-review"
  | "scheduled"
  | "submitted"
  | "published"
  | "failed"
  | "failed-or-rejected";

export interface SubmissionBucketCounts {
  all: number;
  drafts: number;
  "action-needed": number;
  rejected: number;
  submitted: number;
  "under-review": number;
  scheduled: number;
  published: number;
  failed: number;
}

export interface SubmissionPage {
  items: SubmissionSummary[];
  page: number;
  pageSize: number;
  totalCount: number;
  totalPages: number;
  hasNext: boolean;
  counts: SubmissionBucketCounts;
}

export interface SubmissionPayload {
  institutionId?: string | null;
  eventTitle: string;
  eventDate: string;
  caption: string;
  description: string;
  scheduledAt?: string;
  category?: string;
  templateId?: string | null;
  fastTrack?: boolean;
  liveEventName?: string | null;
  tags?: string[];
  albumName?: string | null;
  mediaTags?: string[];
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
  /**
   * Network-wide scheduling guard-rail switch (Page Settings). When false the
   * composer treats a preferred schedule and the 8:00 AM–8:00 PM publish window
   * as non-blocking; a future date is still required if one is set.
   */
  guardrailsEnforced: boolean;
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

export interface EngagementRecommendedSlot {
  scheduledAt: string;
  windowLabel: string;
  score: number;
  warnings: string[];
}

export interface EngagementRecommendations {
  available: boolean;
  source: "HISTORICAL" | "DEFAULT" | "UNAVAILABLE";
  notice: string | null;
  timezone: string;
  sampleSize: number;
  slots: EngagementRecommendedSlot[];
}

export function listSubmissionPage(
  params: {
    page?: number;
    pageSize?: number;
    bucket?: SubmissionQueueBucket;
    search?: string;
  },
  signal?: AbortSignal,
) {
  return api.get<SubmissionPage>("/submissions/page", {
    params: {
      page: params.page ?? 0,
      pageSize: params.pageSize ?? 20,
      bucket: params.bucket ?? "all",
      search: params.search?.trim() ?? "",
    },
    signal,
  });
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

export function withdrawSubmission(id: string) {
  return api.post<SubmissionSummary>(`/submissions/${id}/withdraw`);
}

export function deleteDraft(id: string) {
  return api.delete<void>(`/submissions/${id}`);
}

export function reorderSubmissionMedia(
  id: string,
  mediaAssetIds: string[],
  mediaCaptions?: Record<string, string>,
  skipWatermarks?: Record<string, boolean>,
  signal?: AbortSignal,
) {
  return api.patch<SubmissionSummary>(`/submissions/${id}/media/order`, {
    mediaAssetIds,
    mediaCaptions,
    skipWatermarks,
  }, { signal });
}

export function attachAsset(id: string, mediaAssetId: string, signal?: AbortSignal) {
  return api.post<SubmissionSummary>(`/submissions/${id}/assets`, {
    mediaAssetId,
  }, { signal });
}

export function detachAsset(id: string, mediaAssetId: string, signal?: AbortSignal) {
  return api.delete(`/submissions/${id}/assets/${mediaAssetId}`, { signal });
}

export async function uploadSubmissionMedia(id: string, files: File[], signal?: AbortSignal) {
  const responses = [];
  for (const file of files) {
    const {
      data: { signedUrl, publicUrl },
    } = await api.post<{ signedUrl: string; publicUrl: string; path: string }>(
      `/submissions/${id}/media/upload-url`,
      {
        fileName: safeFileName(file.name),
        fileType: fileTypeFromFile(file),
        fileSizeBytes: file.size,
      },
      { signal },
    );
    const uploadStartedAt = performance.now();
    const upload = await fetchWithDeadline(signedUrl, {
      method: "PUT",
      headers: { "Content-Type": file.type || "application/octet-stream" },
      body: file,
      signal,
    });
    if (!upload.ok) {
      const msg = await upload.text().catch(() => "");
      throw new Error(msg || "Media upload to storage failed.");
    }
    const r2UploadDurationMs = Math.round(performance.now() - uploadStartedAt);
    responses.push(
      await api.post(`/submissions/${id}/media`, {
        storageUrl: publicUrl,
        fileName: file.name,
        fileType: fileTypeFromFile(file),
        fileSizeBytes: file.size,
        r2UploadDurationMs,
      }, { signal }),
    );
  }
  return responses.at(-1);
}

export function getSubmissionLookups(signal?: AbortSignal) {
  return api.get<SubmissionLookups>("/submissions/lookups", { signal });
}

export function validateGuardRails(
  scheduledAt: string,
  institutionId?: string | null,
  submissionId?: string | null,
  signal?: AbortSignal,
) {
  return api.post<GuardRailResult>("/guardrails/validate", {
    scheduledAt,
    institutionId,
    submissionId: submissionId || undefined,
  }, {
    signal,
  });
}

export function getEngagementRecommendations(institutionId?: string | null, signal?: AbortSignal) {
  return api.get<EngagementRecommendations>("/engagement-recommendations", {
    signal,
    params: institutionId ? { institutionId } : undefined,
  });
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

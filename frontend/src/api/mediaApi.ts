import { api } from "./authApi";

export type MediaAssetStatus = "processing" | "ready" | "error";

export interface AiTag {
  label: string;
  confidence: number;
}

export interface MediaUsage {
  submissionId: string;
  submissionTitle: string;
  submittedAt: string;
  submissionStatus: string;
  deepLink?: string;
}

export interface MediaAsset {
  id: string;
  code: string;
  title: string;
  fileName: string;
  fileType: string;
  fileSizeBytes: number;
  storageUrl: string;
  institutionId: string;
  institutionName?: string;
  uploaderId?: string;
  uploaderName?: string;
  uploadedAt: string;
  status: MediaAssetStatus;
  aiTags?: AiTag[];
  usedIn?: MediaUsage[];
  folderId?: string | null;
  widthPx?: number;
  heightPx?: number;
  durationSeconds?: number;
  importBatchId?: string | null;
  duplicateOfId?: string | null;
  aiDescription?: string | null;
  blurScore?: number | null;
  curatedAt?: string | null;
  aiClassifiedAt?: string | null;
  embeddingGeneratedAt?: string | null;
  /** UC-4.x consent/visibility: "internal_only" | "cleared_for_public". */
  visibility?: string | null;
}

export type MediaVisibility = "internal_only" | "cleared_for_public";

export interface DeleteCheckResult {
  tier: "blocked" | "warning" | "free";
  blockingUsages: MediaUsage[];
  warningUsages: MediaUsage[];
}

export interface MediaAssetUploadUrlRequest {
  fileName: string;
  fileType: string;
  institutionId?: string | null;
}

export interface MediaAssetUploadUrlResponse {
  signedUrl: string;
  publicUrl: string;
  path: string;
}

export interface MediaAssetRegisterRequest {
  storageUrl: string;
  fileName: string;
  fileType: string;
  fileSizeBytes: number;
  institutionId?: string | null;
  importBatchId?: string | null;
}

export interface MediaImportBatchRequest {
  assetCount: number;
  institutionId?: string | null;
}

export interface MediaImportBatch {
  id: string;
  institutionId: string;
  uploadedBy: string;
  assetCount: number;
  registeredAssetCount?: number;
  readyAssetCount?: number;
  curatedAssetCount?: number;
  createdAt: string;
}

interface MediaAssetPageResponse {
  items: Array<{
    id: string;
    assetCode: string;
    storageUrl: string;
    fileName: string;
    fileType: string;
    fileSizeBytes: number;
    aiCategory?: string | null;
    createdAt: string;
    institutionId?: string | null;
    institutionName?: string | null;
    uploaderId?: string | null;
    uploaderEmail?: string | null;
    folderId?: string | null;
    title?: string | null;
    importBatchId?: string | null;
    duplicateOfId?: string | null;
    curatedAt?: string | null;
    visibility?: string | null;
  }>;
  totalCount: number;
  page: number;
  pageSize: number;
}

export interface MediaAssetSearchParams {
  networkView?: boolean;
  institutionId?: string | null;
  query?: string;
  aiCategory?: string;
  mediaType?: "image" | "video";
  page?: number;
  pageSize?: number;
}

export interface MediaAssetPage {
  items: MediaAsset[];
  totalCount: number;
  page: number;
  pageSize: number;
}

function rawToAsset(raw: MediaAssetPageResponse["items"][0]): MediaAsset {
  return {
    id: raw.id,
    code: raw.assetCode,
    title: raw.title || raw.fileName,
    fileName: raw.fileName,
    fileType: raw.fileType,
    fileSizeBytes: raw.fileSizeBytes,
    storageUrl: raw.storageUrl,
    institutionId: raw.institutionId ?? "",
    institutionName: raw.institutionName ?? undefined,
    uploaderId: raw.uploaderId ?? undefined,
    uploaderName: raw.uploaderEmail ?? undefined,
    uploadedAt: raw.createdAt,
    status: "ready" as const,
    aiTags: raw.aiCategory ? [{ label: raw.aiCategory, confidence: 100 }] : [],
    folderId: raw.folderId ?? null,
    importBatchId: raw.importBatchId ?? null,
    duplicateOfId: raw.duplicateOfId ?? null,
    curatedAt: raw.curatedAt ?? null,
    visibility: raw.visibility ?? null,
  };
}

export function listMediaAssets(params?: { networkView?: boolean; institutionId?: string | null }, signal?: AbortSignal) {
  const scope = params?.networkView ? "network" : undefined;
  const institutionId = params?.institutionId ?? undefined;
  return api
    .get<MediaAssetPageResponse>("/media-assets", {
      params: { ...(scope ? { scope } : {}), ...(institutionId ? { institutionId } : {}) },
      signal,
    })
    .then((response) => ({
      ...response,
      data: (response.data.items ?? []).map(rawToAsset),
    }));
}

export async function searchMediaAssets(
  params: MediaAssetSearchParams = {},
  signal?: AbortSignal
): Promise<MediaAssetPage> {
  const queryParams: Record<string, string | number | undefined> = {
    scope: params.networkView ? "network" : undefined,
    institutionId: params.institutionId ?? undefined,
    query: params.query || undefined,
    aiCategory: params.aiCategory || undefined,
    mediaType: params.mediaType || undefined,
    page: params.page ?? 1,
    pageSize: params.pageSize ?? 24,
  };
  Object.keys(queryParams).forEach(
    (k) => queryParams[k] === undefined && delete queryParams[k]
  );
  const res = await api.get<MediaAssetPageResponse>("/media-assets", {
    params: queryParams,
    signal,
  });
  return {
    items: (res.data.items ?? []).map(rawToAsset),
    totalCount: res.data.totalCount ?? 0,
    page: res.data.page ?? 1,
    pageSize: res.data.pageSize ?? 24,
  };
}

/* ===== UC-4.5 Hybrid (natural-language) search ===== */

export interface MediaSearchHit {
  asset: MediaAsset;
  /** Fused relevance score (RRF + deterministic re-rank). */
  score: number;
  /** Human-readable reasons the asset matched, for UI transparency. */
  matchReasons: string[];
  lexicalRank: number | null;
  semanticRank: number | null;
  imageRank: number | null;
}

export interface MediaSearchResult {
  query: string;
  hits: MediaSearchHit[];
  totalCount: number;
  page: number;
  pageSize: number;
  /** True when results are ordered chronologically (date-only browse), not by relevance. */
  chronological: boolean;
}

export interface HybridSearchParams {
  query: string;
  networkView?: boolean;
  institutionId?: string | null;
  mediaType?: "image" | "video";
  page?: number;
  pageSize?: number;
}

interface MediaAssetSearchRawResponse {
  query: string;
  items: Array<{
    asset: MediaAssetPageResponse["items"][0];
    score: number;
    lexicalRank?: number | null;
    semanticRank?: number | null;
    imageRank?: number | null;
    matchReasons?: string[];
  }>;
  totalCount: number;
  page: number;
  pageSize: number;
  chronological?: boolean;
}

/**
 * UC-4.5 hybrid search: lexical (tsvector) + semantic + cross-modal vectors fused
 * with RRF and deterministically re-ranked on the backend. Returns ranked hits
 * each carrying explainable match reasons. POST so the natural-language query is
 * never logged in a URL or proxy.
 */
export async function hybridSearchMediaAssets(
  params: HybridSearchParams,
  signal?: AbortSignal,
): Promise<MediaSearchResult> {
  const body: Record<string, unknown> = { query: params.query };
  if (params.networkView) body.scope = "network";
  else if (params.institutionId) body.institutionId = params.institutionId;
  if (params.mediaType) body.mediaType = params.mediaType;
  if (params.page) body.page = params.page;
  if (params.pageSize) body.pageSize = params.pageSize;

  const res = await api.post<MediaAssetSearchRawResponse>("/media-assets/search", body, { signal });
  return {
    query: res.data.query,
    hits: (res.data.items ?? []).map((it) => ({
      asset: rawToAsset(it.asset),
      score: it.score,
      matchReasons: it.matchReasons ?? [],
      lexicalRank: it.lexicalRank ?? null,
      semanticRank: it.semanticRank ?? null,
      imageRank: it.imageRank ?? null,
    })),
    totalCount: res.data.totalCount ?? 0,
    page: res.data.page ?? 1,
    pageSize: res.data.pageSize ?? 24,
    chronological: res.data.chronological ?? false,
  };
}

/* ===== UC-4.6 search feedback ===== */

export type SearchFeedbackAction =
  | "thumbs_up"
  | "thumbs_down"
  | "selected"
  | "applied"
  | "dismissed";

/**
 * Records feedback on a media-search result (UC-4.6). Best-effort by design — a logging
 * failure must never disrupt the user's search experience, so errors are swallowed.
 */
export function recordSearchFeedback(input: {
  assetId: string;
  action: SearchFeedbackAction;
  query?: string;
  rank?: number;
}): Promise<void> {
  return api
    .post<void>("/ai/feedback/search", input)
    .then(() => undefined)
    .catch(() => undefined);
}

export interface MediaAssetDetailResponse {
  id: string;
  assetCode: string;
  storageUrl: string;
  fileName: string;
  fileType: string;
  fileSizeBytes: number;
  status?: string | null;
  aiCategory?: string | null;
  aiConfidence?: number | null;
  aiDescription?: string | null;
  blurScore?: number | null;
  curatedAt?: string | null;
  visibility?: string | null;
  aiClassifiedAt?: string | null;
  embeddingGeneratedAt?: string | null;
  createdAt: string;
  institutionId?: string | null;
  institutionName?: string | null;
  uploaderId?: string | null;
  uploaderEmail?: string | null;
  title?: string | null;
  importBatchId?: string | null;
  duplicateOfId?: string | null;
  usedIn?: Array<{
    submissionId: string;
    eventTitle: string;
    submittedAt: string;
    status: string;
    deepLink?: string;
  }>;
  tags?: Array<{ id: string; label: string }>;
}

export interface MediaAssetCurationEdit {
  assetId: string;
  title: string;
  tags: string[];
}

function mapDetailToAsset(raw: MediaAssetDetailResponse): MediaAsset {
  const aiTags: AiTag[] = [];
  if (raw.aiCategory) {
    aiTags.push({
      label: raw.aiCategory,
      confidence: raw.aiConfidence != null ? Math.round(Number(raw.aiConfidence) * 100) : 100,
    });
  }
  for (const tag of raw.tags ?? []) {
    if (!aiTags.some((t) => t.label === tag.label)) {
      aiTags.push({ label: tag.label, confidence: 100 });
    }
  }
  return {
    id: raw.id,
    code: raw.assetCode,
    title: raw.title || raw.fileName,
    fileName: raw.fileName,
    fileType: raw.fileType,
    fileSizeBytes: raw.fileSizeBytes,
    storageUrl: raw.storageUrl,
    institutionId: raw.institutionId ?? "",
    institutionName: raw.institutionName ?? undefined,
    uploaderId: raw.uploaderId ?? undefined,
    uploaderName: raw.uploaderEmail ?? undefined,
    uploadedAt: raw.createdAt,
    status: raw.status?.toLowerCase() === "failed"
      ? "error"
      : raw.status?.toLowerCase() === "processing"
        ? "processing"
        : "ready",
    aiTags: aiTags.length > 0 ? aiTags : undefined,
    usedIn: (raw.usedIn ?? []).map((u) => ({
      submissionId: u.submissionId,
      submissionTitle: u.eventTitle,
      submittedAt: u.submittedAt,
      submissionStatus: u.status,
      deepLink: u.deepLink,
    })),
    importBatchId: raw.importBatchId ?? null,
    duplicateOfId: raw.duplicateOfId ?? null,
    aiDescription: raw.aiDescription ?? null,
    blurScore: raw.blurScore ?? null,
    curatedAt: raw.curatedAt ?? null,
    visibility: raw.visibility ?? null,
    aiClassifiedAt: raw.aiClassifiedAt ?? null,
    embeddingGeneratedAt: raw.embeddingGeneratedAt ?? null,
  };
}

export function getMediaAsset(id: string, signal?: AbortSignal) {
  return api
    .get<MediaAssetDetailResponse>(`/media-assets/${id}`, { signal })
    .then((res) => ({ ...res, data: mapDetailToAsset(res.data) }));
}

/* ===== UC-4.11 provenance / audit trail ===== */

export interface MediaAuditEntry {
  action: string;
  actorName: string | null;
  actorEmail: string | null;
  createdAt: string;
  metadata: string | null;
}

/** UC-4.11: an asset's provenance trail (newest first). */
export function getAssetHistory(id: string, signal?: AbortSignal) {
  return api
    .get<MediaAuditEntry[]>(`/media-assets/${id}/history`, { signal })
    .then((res) => res.data);
}

/** UC-4.x: change an asset's consent/visibility. Returns the updated detail asset. */
export function changeAssetVisibility(id: string, visibility: MediaVisibility) {
  return api
    .patch<MediaAssetDetailResponse>(`/media-assets/${id}/visibility`, { visibility })
    .then((res) => mapDetailToAsset(res.data));
}

export function deleteMediaAsset(id: string, force = false) {
  return api.delete<void>(`/media-assets/${id}`, { params: force ? { force: true } : undefined });
}

export function bulkDeleteMediaAssets(assetIds: string[], force = false) {
  return api.post<{ deletedIds: string[]; deletedCount: number }>("/media-assets/bulk-delete", {
    assetIds,
    force,
  });
}

export interface BulkOperationResult {
  affected: number;
}

/** Bulk-assign assets to a folder, or unfile them by passing null (UC-4.1). */
export function bulkMoveAssets(assetIds: string[], folderId: string | null) {
  return api
    .post<BulkOperationResult>("/media-assets/bulk-move", { assetIds, folderId })
    .then((res) => res.data);
}

/** Bulk-add a single manual tag to many assets (UC-4.1). */
export function bulkTagAssets(assetIds: string[], label: string) {
  return api
    .post<BulkOperationResult>("/media-assets/bulk-tag", { assetIds, label })
    .then((res) => res.data);
}

export function getMediaAssetUploadUrl(payload: MediaAssetUploadUrlRequest) {
  return api.post<MediaAssetUploadUrlResponse>("/media-assets/upload-url", payload);
}

export function registerMediaAsset(payload: MediaAssetRegisterRequest) {
  return api.post<MediaAsset>("/media-assets/upload", payload);
}

export function createMediaImportBatch(payload: MediaImportBatchRequest) {
  return api.post<MediaImportBatch>("/media-assets/import-batches", payload);
}

export function listMediaImportBatches(institutionId?: string | null) {
  return api
    .get<MediaImportBatch[]>("/media-assets/import-batches", {
      params: institutionId ? { institutionId } : undefined,
    })
    .then((res) => res.data);
}

export function listImportBatchAssets(importBatchId: string, institutionId?: string | null) {
  return api
    .get<MediaAssetDetailResponse[]>(`/media-assets/import-batches/${importBatchId}/assets`, {
      params: institutionId ? { institutionId } : undefined,
    })
    .then((res) => res.data);
}

export function markImportBatchCurated(
  importBatchId: string,
  institutionId?: string | null,
  edits?: MediaAssetCurationEdit[],
) {
  return api
    .post<{ curatedCount: number }>(
      `/media-assets/import-batches/${importBatchId}/curate`,
      edits ? { edits } : null,
      { params: institutionId ? { institutionId } : undefined },
    )
    .then((res) => res.data);
}

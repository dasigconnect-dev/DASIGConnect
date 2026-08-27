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
  albumId?: string;
  albumName?: string;
  aiTags?: AiTag[];
  userTags?: Array<{ id: string; label: string }>;
  usedIn?: MediaUsage[];
  widthPx?: number;
  heightPx?: number;
  durationSeconds?: number;
}

export interface DeleteCheckResult {
  tier: "blocked" | "warning" | "free";
  blockingUsages: MediaUsage[];
  warningUsages: MediaUsage[];
}

export interface MediaAssetUploadUrlRequest {
  fileName: string;
  fileType: string;
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
  albumId?: string | null;
  albumName?: string;
  autoMatchAlbum?: boolean;
  tags: string[];
}

export interface MediaAlbum {
  id: string;
  institutionId: string;
  institutionCode: string;
  institutionName: string;
  parentAlbumId: string | null;
  name: string;
  childAlbumCount: number;
  assetCount: number;
  /** Whether the requesting user may delete this folder (admin, or its creator). */
  canDelete: boolean;
  /** True when this folder belongs to the shared default institution. */
  shared: boolean;
  createdAt: string;
  updatedAt: string;
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
    albumId?: string | null;
    albumName?: string | null;
    createdAt: string;
    institutionId?: string | null;
    institutionName?: string | null;
    uploaderId?: string | null;
    uploaderEmail?: string | null;
  }>;
  totalCount: number;
  page: number;
  pageSize: number;
}

export interface MediaAssetSearchParams {
  networkView?: boolean;
  institutionId?: string | null;
  albumId?: string | null;
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
    title: raw.fileName,
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
    albumId: raw.albumId ?? undefined,
    albumName: raw.albumName ?? undefined,
    aiTags: raw.aiCategory ? [{ label: raw.aiCategory, confidence: 100 }] : [],
  };
}

export function listMediaAssets(
  params?: { networkView?: boolean; institutionId?: string | null; albumId?: string | null },
  signal?: AbortSignal,
) {
  const scope = params?.networkView ? "network" : undefined;
  const institutionId = params?.institutionId ?? undefined;
  const albumId = params?.albumId ?? undefined;
  return api
    .get<MediaAssetPageResponse>("/media-assets", {
      params: {
        ...(scope ? { scope } : {}),
        ...(institutionId ? { institutionId } : {}),
        ...(albumId ? { albumId } : {}),
      },
      signal,
    })
    .then((response) => ({
      ...response,
      data: (response.data.items ?? []).map(rawToAsset),
    }));
}

/** Meaning-based asset search (Voyage embedding + pgvector, with keyword fallback). */
export function semanticSearchMediaAssets(
  query: string,
  institutionId?: string | null,
  signal?: AbortSignal,
) {
  return api
    .get<MediaAssetPageResponse>("/media-assets/search", {
      params: { query, ...(institutionId ? { institutionId } : {}) },
      signal,
    })
    .then((response) => (response.data.items ?? []).map(rawToAsset));
}

export async function searchMediaAssets(
  params: MediaAssetSearchParams = {},
  signal?: AbortSignal
): Promise<MediaAssetPage> {
  const queryParams: Record<string, string | number | undefined> = {
    scope: params.networkView ? "network" : undefined,
    institutionId: params.institutionId ?? undefined,
    albumId: params.albumId ?? undefined,
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

interface MediaAssetDetailResponse {
  id: string;
  assetCode: string;
  storageUrl: string;
  fileName: string;
  fileType: string;
  fileSizeBytes: number;
  aiCategory?: string | null;
  albumId?: string | null;
  albumName?: string | null;
  aiConfidence?: number | null;
  createdAt: string;
  institutionId?: string | null;
  institutionName?: string | null;
  uploaderId?: string | null;
  uploaderEmail?: string | null;
  usedIn?: Array<{
    submissionId: string;
    eventTitle: string;
    submittedAt: string;
    status: string;
    deepLink?: string;
  }>;
  tags?: Array<{ id: string; label: string; source: string }>;
}

export interface MediaAssetHistoryEntry {
  action: string;
  actorName: string;
  actorEmail: string | null;
  occurredAt: string;
  summary: string;
}

function mapDetailToAsset(raw: MediaAssetDetailResponse): MediaAsset {
  const aiTags: AiTag[] = [];
  if (raw.aiCategory) {
    aiTags.push({
      label: raw.aiCategory,
      confidence: raw.aiConfidence != null ? Math.round(Number(raw.aiConfidence) * 100) : 100,
    });
  }
  const userTags: Array<{ id: string; label: string }> = [];
  for (const tag of raw.tags ?? []) {
    if (tag.source === "manual") {
      userTags.push({ id: tag.id, label: tag.label });
    } else if (!aiTags.some((t) => t.label === tag.label)) {
      aiTags.push({ label: tag.label, confidence: 100 });
    }
  }
  return {
    id: raw.id,
    code: raw.assetCode,
    title: raw.fileName,
    fileName: raw.fileName,
    fileType: raw.fileType,
    fileSizeBytes: raw.fileSizeBytes,
    storageUrl: raw.storageUrl,
    institutionId: raw.institutionId ?? "",
    institutionName: raw.institutionName ?? undefined,
    uploaderId: raw.uploaderId ?? undefined,
    uploaderName: raw.uploaderEmail ?? undefined,
    uploadedAt: raw.createdAt,
    status: "ready",
    albumId: raw.albumId ?? undefined,
    albumName: raw.albumName ?? undefined,
    aiTags: aiTags.length > 0 ? aiTags : undefined,
    userTags,
    usedIn: (raw.usedIn ?? []).map((u) => ({
      submissionId: u.submissionId,
      submissionTitle: u.eventTitle,
      submittedAt: u.submittedAt,
      submissionStatus: u.status,
      deepLink: u.deepLink,
    })),
  };
}

export function getMediaAsset(id: string, signal?: AbortSignal) {
  return api
    .get<MediaAssetDetailResponse>(`/media-assets/${id}`, { signal })
    .then((res) => ({ ...res, data: mapDetailToAsset(res.data) }));
}

export function getMediaAssetHistory(id: string, signal?: AbortSignal) {
  return api.get<MediaAssetHistoryEntry[]>(`/media-assets/${id}/history`, { signal });
}

export function addMediaAssetTag(id: string, label: string) {
  return api.post<{ id: string; label: string; source: string }>(`/media-assets/${id}/tags`, { label });
}

export function removeMediaAssetTag(id: string, tagId: string) {
  return api.delete<void>(`/media-assets/${id}/tags/${tagId}`);
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

export function getMediaAssetUploadUrl(payload: MediaAssetUploadUrlRequest) {
  return api.post<MediaAssetUploadUrlResponse>("/media-assets/upload-url", payload);
}

export function registerMediaAsset(payload: MediaAssetRegisterRequest) {
  return api.post<MediaAsset>("/media-assets/upload", payload);
}

export function listMediaAlbums(institutionId?: string | null, signal?: AbortSignal) {
  return api.get<MediaAlbum[]>("/media-assets/albums", {
    params: institutionId ? { institutionId } : undefined,
    signal,
  });
}

export function createMediaAlbum(
  name: string,
  institutionId?: string | null,
  parentAlbumId?: string | null,
) {
  return api.post<MediaAlbum>("/media-assets/albums", { name, institutionId, parentAlbumId });
}

export function renameMediaAlbum(id: string, name: string, institutionId?: string | null) {
  return api.post<MediaAlbum>(`/media-assets/albums/${id}`, { name, institutionId });
}

/** Walk/create a folder path and return its leaf album. Backs "Upload folder". */
export function ensureMediaAlbumPath(
  institutionId: string | null | undefined,
  segments: string[],
) {
  return api.post<MediaAlbum>("/media-assets/albums/ensure-path", { institutionId, segments });
}

/** Re-parent an album. `parentAlbumId` null moves it to the institution root. */
export function moveMediaAlbum(
  id: string,
  parentAlbumId: string | null,
  institutionId?: string | null,
) {
  return api.patch<MediaAlbum>(`/media-assets/albums/${id}/parent`, { parentAlbumId, institutionId });
}

export function deleteMediaAlbum(id: string) {
  return api.delete<void>(`/media-assets/albums/${id}`);
}

export function updateMediaAssetAlbum(id: string, albumId: string | null) {
  return api.post<MediaAssetDetailResponse>(`/media-assets/${id}/album`, { albumId })
    .then((res) => ({ ...res, data: mapDetailToAsset(res.data) }));
}

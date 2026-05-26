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
  uploaderName?: string;
  uploadedAt: string;
  status: MediaAssetStatus;
  aiTags?: AiTag[];
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
  }>;
  totalCount: number;
}

export function listMediaAssets(params?: { networkView?: boolean }, signal?: AbortSignal) {
  const scope = params?.networkView ? "network" : undefined;
  return api
    .get<MediaAssetPageResponse>("/media-assets", {
      params: scope ? { scope } : undefined,
      signal,
    })
    .then((response) => ({
      ...response,
      data: (response.data.items ?? []).map((raw) => ({
        id: raw.id,
        code: raw.assetCode,
        title: raw.fileName,
        fileName: raw.fileName,
        fileType: raw.fileType,
        fileSizeBytes: raw.fileSizeBytes,
        storageUrl: raw.storageUrl,
        institutionId: "",
        uploadedAt: raw.createdAt,
        status: "ready" as const,
        aiTags: raw.aiCategory ? [{ label: raw.aiCategory, confidence: 100 }] : [],
      })),
    }));
}

export function getMediaAsset(id: string, signal?: AbortSignal) {
  return api.get<MediaAsset>(`/media-assets/${id}`, { signal });
}

export function updateMediaAssetTitle(id: string, title: string) {
  return api.patch<MediaAsset>(`/media-assets/${id}`, { title });
}

export function checkMediaAssetDeletion(id: string, signal?: AbortSignal) {
  return api.get<DeleteCheckResult>(`/media-assets/${id}/delete-check`, { signal });
}

export function deleteMediaAsset(id: string) {
  return api.delete<void>(`/media-assets/${id}`);
}

export function getMediaAssetUploadUrl(payload: MediaAssetUploadUrlRequest) {
  return api.post<MediaAssetUploadUrlResponse>("/media-assets/upload-url", payload);
}

export function registerMediaAsset(payload: MediaAssetRegisterRequest) {
  return api.post<MediaAsset>("/media-assets/upload", payload);
}

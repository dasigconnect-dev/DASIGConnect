import { api } from "./authApi";

// Mirrors backend AlbumResponseDto / AlbumDetailDto (UC-4.1b). Albums are many-to-many
// curated collections (ADR-0004); source is "manual" or "ai_suggested" (Phase 2).
export type AlbumSource = "manual" | "ai_suggested";

export interface Album {
  id: string;
  name: string;
  description: string | null;
  source: AlbumSource;
  coverAssetId: string | null;
  assetCount: number;
  createdAt: string;
  updatedAt: string;
}

// Mirrors backend MediaAssetSummaryDto.
export interface AlbumAssetSummary {
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
}

export interface AlbumDetail extends Omit<Album, "assetCount"> {
  assetCount: number;
  assets: AlbumAssetSummary[];
}

export interface CreateAlbumRequest {
  name: string;
  description?: string | null;
}

export function listAlbums(signal?: AbortSignal) {
  return api.get<Album[]>("/media-albums", { signal }).then((res) => res.data);
}

export function getAlbum(id: string, signal?: AbortSignal) {
  return api.get<AlbumDetail>(`/media-albums/${id}`, { signal }).then((res) => res.data);
}

export function createAlbum(payload: CreateAlbumRequest) {
  return api
    .post<Album>("/media-albums", {
      name: payload.name,
      description: payload.description ?? null,
    })
    .then((res) => res.data);
}

export function updateAlbum(id: string, payload: CreateAlbumRequest) {
  return api
    .patch<Album>(`/media-albums/${id}`, {
      name: payload.name,
      description: payload.description ?? null,
    })
    .then((res) => res.data);
}

export function deleteAlbum(id: string) {
  return api.delete<void>(`/media-albums/${id}`);
}

export function addAlbumAssets(id: string, assetIds: string[]) {
  return api
    .post<AlbumDetail>(`/media-albums/${id}/assets`, { assetIds })
    .then((res) => res.data);
}

export function removeAlbumAsset(id: string, assetId: string) {
  return api.delete<void>(`/media-albums/${id}/assets/${assetId}`);
}

/** Pass null to clear the cover. */
export function setAlbumCover(id: string, coverAssetId: string | null) {
  return api
    .patch<Album>(`/media-albums/${id}/cover`, { coverAssetId })
    .then((res) => res.data);
}

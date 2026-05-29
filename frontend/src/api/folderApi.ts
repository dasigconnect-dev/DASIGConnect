import { api } from "./authApi";

// Mirrors backend FolderResponseDto (UC-4.1). Folders are single-parent (ADR-0004).
export interface Folder {
  id: string;
  name: string;
  parentFolderId: string | null;
  assetCount: number;
  subfolderCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateFolderRequest {
  name: string;
  parentFolderId?: string | null;
}

export function listFolders(signal?: AbortSignal) {
  return api.get<Folder[]>("/media-folders", { signal }).then((res) => res.data);
}

export function getFolder(id: string, signal?: AbortSignal) {
  return api.get<Folder>(`/media-folders/${id}`, { signal }).then((res) => res.data);
}

export function createFolder(payload: CreateFolderRequest) {
  return api
    .post<Folder>("/media-folders", {
      name: payload.name,
      parentFolderId: payload.parentFolderId ?? null,
    })
    .then((res) => res.data);
}

export function renameFolder(id: string, name: string) {
  return api.patch<Folder>(`/media-folders/${id}`, { name }).then((res) => res.data);
}

/** Pass null to move the folder to the top level. */
export function moveFolder(id: string, parentFolderId: string | null) {
  return api
    .patch<Folder>(`/media-folders/${id}/move`, { parentFolderId })
    .then((res) => res.data);
}

export function deleteFolder(id: string) {
  return api.delete<void>(`/media-folders/${id}`);
}

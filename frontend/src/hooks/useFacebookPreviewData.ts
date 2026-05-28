import { useEffect, useMemo } from "react";
import type { SavedMediaAsset } from "../api/submissionApi";
import type {
  FacebookPreviewMediaItem,
  FacebookPreviewPage,
} from "../types/facebook";
import { fileMediaKey, savedMediaKey } from "./useMediaReorder";

interface UseFacebookPreviewDataArgs {
  caption: string;
  scheduledAt?: string;
  files: File[];
  savedAssets: SavedMediaAsset[];
  mediaOrder?: string[];
  page?: FacebookPreviewPage;
}

export function useFacebookPreviewData({
  caption,
  scheduledAt,
  files,
  savedAssets,
  mediaOrder = [],
  page,
}: UseFacebookPreviewDataArgs) {
  const localUrls = useMemo(
    () =>
      files.map((file) => ({
        file,
        url: URL.createObjectURL(file),
      })),
    [files],
  );

  useEffect(() => {
    return () => {
      localUrls.forEach(({ url }) => URL.revokeObjectURL(url));
    };
  }, [localUrls]);

  const mediaItems = useMemo<FacebookPreviewMediaItem[]>(() => {
    const savedItems = savedAssets.map((asset) => ({
      id: savedMediaKey(asset.id),
      source: "saved" as const,
      sourceId: asset.id,
      url: asset.storageUrl,
      type: mediaTypeFromFileType(asset.fileType),
      alt: asset.fileName || "Saved media asset",
      fileName: asset.fileName,
    }));

    const localItems = localUrls.map(({ file, url }) => ({
      id: fileMediaKey(file),
      source: "local" as const,
      sourceId: fileMediaKey(file),
      url,
      type: mediaTypeFromMime(file.type),
      alt: file.name || "Selected media file",
      fileName: file.name,
    }));

    const items = [...savedItems, ...localItems];
    if (mediaOrder.length === 0) return items;

    const orderMap = new Map(mediaOrder.map((id, index) => [id, index]));
    return [...items].sort((a, b) => {
      const aIndex = orderMap.get(a.id) ?? Number.MAX_SAFE_INTEGER;
      const bIndex = orderMap.get(b.id) ?? Number.MAX_SAFE_INTEGER;
      return aIndex - bIndex;
    });
  }, [localUrls, mediaOrder, savedAssets]);

  return {
    pageName: page?.name || "Facebook Page",
    pageAvatarUrl: page?.avatarUrl,
    publishDate: scheduledAt,
    caption,
    mediaItems,
  };
}

function mediaTypeFromMime(mimeType: string): FacebookPreviewMediaItem["type"] {
  if (mimeType.startsWith("image/")) return "image";
  if (mimeType.startsWith("video/")) return "video";
  return "unknown";
}

function mediaTypeFromFileType(fileType: string): FacebookPreviewMediaItem["type"] {
  const normalized = fileType.toLowerCase();
  if (["jpeg", "jpg", "png", "webp", "gif"].includes(normalized)) {
    return "image";
  }
  if (["mp4", "mov", "webm"].includes(normalized)) {
    return "video";
  }
  if (normalized.startsWith("image")) return "image";
  if (normalized.startsWith("video")) return "video";
  return "unknown";
}

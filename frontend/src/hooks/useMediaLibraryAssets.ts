import { useCallback, useEffect, useRef, useState } from "react";
import { searchMediaAssets, type MediaAsset } from "../api/mediaApi";

const PAGE_SIZE = 24;
const DEBOUNCE_MS = 300;

export interface UseMediaLibraryAssetsReturn {
  assets: MediaAsset[];
  loading: boolean;
  error: boolean;
  totalCount: number;
  hasMore: boolean;
  search: string;
  setSearch: (v: string) => void;
  aiCategory: string;
  setAiCategory: (v: string) => void;
  mediaType: "" | "image" | "video";
  setMediaType: (v: "" | "image" | "video") => void;
  albumId: string;
  setAlbumId: (v: string) => void;
  loadMore: () => void;
  retry: () => void;
  selectedIds: string[];
  toggleSelect: (id: string) => void;
  clearSelection: () => void;
}

export interface UseMediaLibraryAssetsOptions {
  /** Scope the library to a specific institution (used by network-wide admins). */
  institutionId?: string;
}

export function useMediaLibraryAssets(
  options?: UseMediaLibraryAssetsOptions,
): UseMediaLibraryAssetsReturn {
  const institutionId = options?.institutionId;
  const [assets, setAssets] = useState<MediaAsset[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const [totalCount, setTotalCount] = useState(0);
  const pageRef = useRef<number>(1);

  const [search, setSearch] = useState("");
  const [aiCategory, setAiCategory] = useState("");
  const [mediaType, setMediaType] = useState<"" | "image" | "video">("");
  const [albumId, setAlbumId] = useState("");
  const [selectedIds, setSelectedIds] = useState<string[]>([]);

  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [debouncedSearch, setDebouncedSearch] = useState("");

  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => setDebouncedSearch(search), DEBOUNCE_MS);
    return () => { if (debounceRef.current) clearTimeout(debounceRef.current); };
  }, [search]);

  const doFetch = useCallback(
    (
      q: string,
      cat: string,
      type: "" | "image" | "video",
      album: string,
      pageNum: number,
      append: boolean,
    ) => {
      pageRef.current = pageNum;
      setLoading(true);
      setError(false);
      return searchMediaAssets({
        query: q || undefined,
        aiCategory: cat || undefined,
        mediaType: type || undefined,
        albumId: album || undefined,
        institutionId: institutionId || undefined,
        page: pageNum,
        pageSize: PAGE_SIZE,
      })
        .then((result) => {
          setAssets((prev) => (append ? [...prev, ...result.items] : result.items));
          setTotalCount(result.totalCount);
        })
        .catch(() => setError(true))
        .finally(() => setLoading(false));
    },
    [institutionId]
  );

  useEffect(() => {
    const controller = { aborted: false };
    queueMicrotask(() => {
      if (!controller.aborted) void doFetch(debouncedSearch, aiCategory, mediaType, albumId, 1, false);
    });
    return () => { controller.aborted = true; };
  }, [debouncedSearch, aiCategory, mediaType, albumId, doFetch]);

  function loadMore() {
    void doFetch(debouncedSearch, aiCategory, mediaType, albumId, pageRef.current + 1, true);
  }

  function retry() {
    void doFetch(debouncedSearch, aiCategory, mediaType, albumId, pageRef.current, false);
  }

  function toggleSelect(id: string) {
    setSelectedIds((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]
    );
  }

  function clearSelection() {
    setSelectedIds([]);
  }

  return {
    assets,
    loading,
    error,
    totalCount,
    hasMore: assets.length < totalCount,
    search,
    setSearch,
    aiCategory,
    setAiCategory,
    mediaType,
    setMediaType,
    albumId,
    setAlbumId,
    loadMore,
    retry,
    selectedIds,
    toggleSelect,
    clearSelection,
  };
}

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
  loadMore: () => void;
  retry: () => void;
  selectedIds: string[];
  toggleSelect: (id: string) => void;
  clearSelection: () => void;
}

export function useMediaLibraryAssets(): UseMediaLibraryAssetsReturn {
  const [assets, setAssets] = useState<MediaAsset[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const [totalCount, setTotalCount] = useState(0);
  const pageRef = useRef<number>(1);

  const [search, setSearch] = useState("");
  const [aiCategory, setAiCategory] = useState("");
  const [mediaType, setMediaType] = useState<"" | "image" | "video">("");
  const [selectedIds, setSelectedIds] = useState<string[]>([]);

  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [debouncedSearch, setDebouncedSearch] = useState("");

  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => setDebouncedSearch(search), DEBOUNCE_MS);
    return () => { if (debounceRef.current) clearTimeout(debounceRef.current); };
  }, [search]);

  const doFetch = useCallback(
    (q: string, cat: string, type: "" | "image" | "video", pageNum: number, append: boolean) => {
      pageRef.current = pageNum;
      setLoading(true);
      setError(false);
      return searchMediaAssets({
        query: q || undefined,
        aiCategory: cat || undefined,
        mediaType: type || undefined,
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
    []
  );

  useEffect(() => {
    const controller = { aborted: false };
    queueMicrotask(() => {
      if (!controller.aborted) void doFetch(debouncedSearch, aiCategory, mediaType, 1, false);
    });
    return () => { controller.aborted = true; };
  }, [debouncedSearch, aiCategory, mediaType, doFetch]);

  function loadMore() {
    void doFetch(debouncedSearch, aiCategory, mediaType, pageRef.current + 1, true);
  }

  function retry() {
    void doFetch(debouncedSearch, aiCategory, mediaType, pageRef.current, false);
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
    loadMore,
    retry,
    selectedIds,
    toggleSelect,
    clearSelection,
  };
}

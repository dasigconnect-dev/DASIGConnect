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
  /** Show assets across every institution for network-wide roles. */
  networkView?: boolean;
}

export function useMediaLibraryAssets(
  options?: UseMediaLibraryAssetsOptions,
): UseMediaLibraryAssetsReturn {
  const institutionId = options?.institutionId;
  const networkView = options?.networkView ?? false;
  const [assets, setAssets] = useState<MediaAsset[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const [totalCount, setTotalCount] = useState(0);
  const pageRef = useRef<number>(1);
  const requestRef = useRef<{ id: number; controller: AbortController } | null>(null);
  const requestIdRef = useRef(0);

  const [search, setSearch] = useState("");
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
      type: "" | "image" | "video",
      album: string,
      pageNum: number,
      append: boolean,
    ) => {
      requestRef.current?.controller.abort();
      const request = {
        id: ++requestIdRef.current,
        controller: new AbortController(),
      };
      requestRef.current = request;
      pageRef.current = pageNum;
      setLoading(true);
      setError(false);
      return searchMediaAssets({
        query: q || undefined,
        mediaType: type || undefined,
        albumId: album || undefined,
        institutionId: networkView ? undefined : institutionId || undefined,
        networkView,
        page: pageNum,
        pageSize: PAGE_SIZE,
      }, request.controller.signal)
        .then((result) => {
          if (requestRef.current?.id !== request.id) return;
          setAssets((prev) => (append ? [...prev, ...result.items] : result.items));
          setTotalCount(result.totalCount);
        })
        .catch(() => {
          if (requestRef.current?.id === request.id && !request.controller.signal.aborted) {
            setError(true);
          }
        })
        .finally(() => {
          if (requestRef.current?.id === request.id) {
            requestRef.current = null;
            setLoading(false);
          }
        });
    },
    [institutionId, networkView]
  );

  useEffect(() => {
    const controller = { aborted: false };
    queueMicrotask(() => {
      if (!controller.aborted) void doFetch(debouncedSearch, mediaType, albumId, 1, false);
    });
    return () => { controller.aborted = true; };
  }, [debouncedSearch, mediaType, albumId, doFetch]);

  useEffect(() => {
    return () => {
      requestIdRef.current += 1;
      requestRef.current?.controller.abort();
      requestRef.current = null;
    };
  }, []);

  function loadMore() {
    void doFetch(debouncedSearch, mediaType, albumId, pageRef.current + 1, true);
  }

  function retry() {
    void doFetch(debouncedSearch, mediaType, albumId, pageRef.current, false);
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

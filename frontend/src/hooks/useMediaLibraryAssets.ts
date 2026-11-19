import { useCallback, useEffect, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import {
  searchMediaAssets,
  semanticSearchMediaAssets,
  type MediaAsset,
} from "../api/mediaApi";
import { authenticatedQueryMeta } from "../lib/queryClient";
import { queryKeys } from "../lib/queryKeys";

const DEBOUNCE_MS = 300;
const MEDIA_LIBRARY_STALE_TIME_MS = 2 * 60_000;
const MEDIA_LIBRARY_GC_TIME_MS = 15 * 60_000;

export interface UseMediaLibraryAssetsReturn {
  assets: MediaAsset[];
  selectedAssets: MediaAsset[];
  page: number;
  previousPage: () => void;
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
  pageSize?: number;
  /** Scope the library to a specific institution (used by network-wide admins). */
  institutionId?: string;
  /** Show assets across every institution for network-wide roles. */
  networkView?: boolean;
}

export function useMediaLibraryAssets(
  options?: UseMediaLibraryAssetsOptions,
): UseMediaLibraryAssetsReturn {
  const queryClient = useQueryClient();
  const institutionId = options?.institutionId;
  const networkView = options?.networkView ?? false;
  const pageSize = options?.pageSize ?? 24;
  const [assets, setAssets] = useState<MediaAsset[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const [totalCount, setTotalCount] = useState(0);
  const pageRef = useRef<number>(1);
  const [page, setPage] = useState(1);
  const [assetCache, setAssetCache] = useState<Map<string, MediaAsset>>(() => new Map());
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
      const useSemanticSearch = q.trim().length >= 2 && !type && !album;
      const normalizedQuery = q.trim();
      const queryKey = queryKeys.mediaAssets.all({
        role: networkView ? "network" : "institution",
        networkView,
        institutionId: networkView ? null : institutionId ?? null,
        albumId: album || null,
        page: pageNum,
        pageSize,
        search: normalizedQuery,
        sort: useSemanticSearch ? "relevance" : "newest",
        mediaType: type || undefined,
        semantic: useSemanticSearch,
      });
      const requestPromise = queryClient.fetchQuery({
        queryKey,
        queryFn: () => useSemanticSearch
          ? semanticSearchMediaAssets(
              normalizedQuery,
              networkView ? undefined : institutionId || undefined,
              pageNum,
              pageSize,
              request.controller.signal,
            )
          : searchMediaAssets({
              query: normalizedQuery || undefined,
              mediaType: type || undefined,
              albumId: album || undefined,
              institutionId: networkView ? undefined : institutionId || undefined,
              networkView,
              page: pageNum,
              pageSize,
            }, request.controller.signal),
        staleTime: MEDIA_LIBRARY_STALE_TIME_MS,
        gcTime: MEDIA_LIBRARY_GC_TIME_MS,
        meta: authenticatedQueryMeta,
      });
      return requestPromise
        .then((result) => {
          if (requestRef.current?.id !== request.id) return;
          setAssetCache((current) => {
            const next = new Map(current);
            result.items.forEach((asset) => next.set(asset.id, asset));
            return next;
          });
          setPage(pageNum);
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
    [institutionId, networkView, pageSize, queryClient]
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
    void doFetch(debouncedSearch, mediaType, albumId, page + 1, false);
  }

  function previousPage() {
    void doFetch(debouncedSearch, mediaType, albumId, Math.max(1, page - 1), false);
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
    selectedAssets: selectedIds.flatMap((id) => {
      const asset = assetCache.get(id);
      return asset ? [asset] : [];
    }),
    page,
    previousPage,
    loading,
    error,
    totalCount,
    hasMore: page * pageSize < totalCount,
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

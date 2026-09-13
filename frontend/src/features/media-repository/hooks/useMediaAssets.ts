import { useCallback, type Dispatch, type SetStateAction } from "react";
import { useInfiniteQuery, useQuery, useQueryClient, type InfiniteData } from "@tanstack/react-query";
import { listMediaAlbums, listMediaAssets, type MediaAlbum, type MediaAsset, type MediaAssetPage } from "../../../api/mediaApi";
import { authenticatedQueryMeta } from "../../../lib/queryClient";
import { queryKeys } from "../../../lib/queryKeys";
import type { User } from "../../../types/auth.types";

interface ApiError {
  name?: string;
  message?: string;
  response?: {
    data?: {
      error?: string;
      message?: string;
    };
  };
}

function isApiError(error: unknown): error is ApiError {
  return typeof error === "object" && error !== null;
}

function getErrorMessage(error: unknown, fallback: string) {
  if (!isApiError(error)) return fallback;
  return (
    error.response?.data?.error ||
    error.response?.data?.message ||
    error.message ||
    fallback
  );
}

function isCanceledError(error: unknown) {
  return isApiError(error) && error.name === "CanceledError";
}

const MEDIA_ASSETS_STALE_TIME_MS = 60_000;
const MEDIA_ALBUMS_STALE_TIME_MS = 60_000;
const MEDIA_ASSETS_PAGE_SIZE = 25;

export function useMediaAssets(
  user: User,
  networkView = false,
  institutionId?: string | null,
  albumId?: string | null,
  enabled = true,
) {
  const queryClient = useQueryClient();
  const userScope = user.id ?? user.email.trim().toLowerCase();
  const queryKey = queryKeys.mediaAssets.all({
    role: user.role,
    userId: userScope,
    networkView,
    institutionId: institutionId ?? null,
    albumId: albumId ?? null,
  });

  const query = useInfiniteQuery({
    queryKey,
    queryFn: ({ signal, pageParam }) =>
      listMediaAssets({
        networkView,
        institutionId,
        albumId,
        page: pageParam,
        pageSize: MEDIA_ASSETS_PAGE_SIZE,
      }, signal),
    initialPageParam: 1,
    getNextPageParam: (lastPage) =>
      lastPage.page * lastPage.pageSize < lastPage.totalCount
        ? lastPage.page + 1
        : undefined,
    enabled,
    staleTime: MEDIA_ASSETS_STALE_TIME_MS,
    meta: authenticatedQueryMeta,
  });

  const setAssets: Dispatch<SetStateAction<MediaAsset[]>> = useCallback(
    (value) => {
      queryClient.setQueryData<InfiniteData<MediaAssetPage>>(queryKey, (current) => {
        if (!current) return current;
        const previous = current.pages.flatMap((page) => page.items);
        const next = typeof value === "function"
          ? (value as (previous: MediaAsset[]) => MediaAsset[])(previous)
          : value;
        const totalDelta = next.length - previous.length;
        let offset = 0;
        const pages = current.pages.map((page, index) => {
          const isLast = index === current.pages.length - 1;
          const itemCount = isLast ? next.length - offset : Math.min(page.items.length, next.length - offset);
          const items = next.slice(offset, offset + Math.max(itemCount, 0));
          offset += items.length;
          return { ...page, items, totalCount: Math.max(0, page.totalCount + totalDelta) };
        });
        return { ...current, pages };
      });
    },
    [queryClient, queryKey],
  );

  const refresh = useCallback(() => {
    return queryClient.invalidateQueries({ queryKey: ["media-assets"] });
  }, [queryClient]);

  return {
    assets: enabled ? query.data?.pages.flatMap((page) => page.items) ?? [] : [],
    totalCount: query.data?.pages.at(-1)?.totalCount ?? 0,
    hasNextPage: Boolean(query.hasNextPage),
    loadingMore: query.isFetchingNextPage,
    loadMore: query.fetchNextPage,
    setAssets,
    loading: query.isLoading,
    refreshing: query.isFetching && !query.isLoading,
    error: query.error && !isCanceledError(query.error)
      ? getErrorMessage(query.error, "Unable to load media assets.")
      : "",
    refresh,
  };
}

export function useMediaAlbums(
  user: User,
  institutionId?: string | null,
  enabled = true,
) {
  const queryClient = useQueryClient();
  const userScope = user.id ?? user.email.trim().toLowerCase();
  const scope = user.role === "admin" || user.role === "moderator" ? "network" : "institution";
  const queryKey = queryKeys.mediaAlbums.all({
    role: user.role,
    userId: userScope,
    institutionId: institutionId ?? null,
    scope,
  });

  const query = useQuery({
    queryKey,
    queryFn: ({ signal }) =>
      listMediaAlbums(institutionId ?? undefined, signal).then((response) =>
        Array.isArray(response.data) ? response.data : [],
      ),
    enabled,
    staleTime: MEDIA_ALBUMS_STALE_TIME_MS,
    meta: authenticatedQueryMeta,
  });

  const setAlbums: Dispatch<SetStateAction<MediaAlbum[]>> = useCallback(
    (value) => {
      queryClient.setQueryData<MediaAlbum[]>(queryKey, (current = []) => {
        return typeof value === "function"
          ? (value as (previous: MediaAlbum[]) => MediaAlbum[])(current)
          : value;
      });
    },
    [queryClient, queryKey],
  );

  const refresh = useCallback(() => {
    return queryClient.invalidateQueries({ queryKey: ["media-albums"] });
  }, [queryClient]);

  return {
    albums: enabled ? query.data ?? [] : [],
    setAlbums,
    loading: query.isLoading,
    refreshing: query.isFetching && !query.isLoading,
    error: query.error && !isCanceledError(query.error)
      ? getErrorMessage(query.error, "Unable to load media albums.")
      : "",
    refresh,
  };
}

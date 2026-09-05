import { useCallback, type Dispatch, type SetStateAction } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { listMediaAssets, type MediaAsset } from "../../../api/mediaApi";
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

  const query = useQuery({
    queryKey,
    queryFn: ({ signal }) =>
      listMediaAssets({ networkView, institutionId, albumId }, signal).then((response) =>
        Array.isArray(response.data) ? response.data : [],
      ),
    enabled,
    staleTime: MEDIA_ASSETS_STALE_TIME_MS,
    meta: authenticatedQueryMeta,
  });

  const setAssets: Dispatch<SetStateAction<MediaAsset[]>> = useCallback(
    (value) => {
      queryClient.setQueryData<MediaAsset[]>(queryKey, (current = []) => {
        return typeof value === "function"
          ? (value as (previous: MediaAsset[]) => MediaAsset[])(current)
          : value;
      });
    },
    [queryClient, queryKey],
  );

  const refresh = useCallback(() => {
    return queryClient.invalidateQueries({ queryKey: ["media-assets"] });
  }, [queryClient]);

  return {
    assets: enabled ? query.data ?? [] : [],
    setAssets,
    loading: query.isLoading || query.isFetching,
    error: query.error && !isCanceledError(query.error)
      ? getErrorMessage(query.error, "Unable to load media assets.")
      : "",
    refresh,
  };
}

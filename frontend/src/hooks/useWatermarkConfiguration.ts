import { queryOptions, useQuery } from "@tanstack/react-query";
import { getWatermarkConfiguration } from "../api/watermarkApi";
import { authenticatedQueryMeta } from "../lib/queryClient";
import { queryKeys } from "../lib/queryKeys";
import type { User } from "../types/auth.types";

const WATERMARK_STALE_TIME_MS = 5 * 60_000;

type WatermarkConfigurationQueryOptions = {
  user?: User | null;
  institutionId?: string | null;
  enabled?: boolean;
};

function getUserScope(user?: User | null) {
  return user?.id ?? user?.email.trim().toLowerCase() ?? null;
}

export function watermarkConfigurationQueryOptions({
  user = null,
  institutionId = null,
  enabled = true,
}: WatermarkConfigurationQueryOptions = {}) {
  return queryOptions({
    queryKey: queryKeys.settings.watermark({
      role: user?.role ?? "authenticated-preview",
      userId: getUserScope(user),
      institutionId,
    }),
    queryFn: ({ signal }) => getWatermarkConfiguration(institutionId, signal).then((response) => response.data),
    enabled,
    staleTime: WATERMARK_STALE_TIME_MS,
    meta: authenticatedQueryMeta,
  });
}

export function useWatermarkConfiguration(options: WatermarkConfigurationQueryOptions = {}) {
  return useQuery(watermarkConfigurationQueryOptions(options));
}

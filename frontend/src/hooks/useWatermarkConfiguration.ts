import { useQuery } from "@tanstack/react-query";
import { getWatermarkConfiguration } from "../api/watermarkApi";
import { authenticatedQueryMeta } from "../lib/queryClient";
import { queryKeys } from "../lib/queryKeys";
import type { User } from "../types/auth.types";

const WATERMARK_STALE_TIME_MS = 5 * 60_000;

type UseWatermarkConfigurationOptions = {
  user?: User | null;
  institutionId?: string | null;
  enabled?: boolean;
};

function getUserScope(user?: User | null) {
  return user?.id ?? user?.email.trim().toLowerCase() ?? null;
}

export function useWatermarkConfiguration({
  user = null,
  institutionId = null,
  enabled = true,
}: UseWatermarkConfigurationOptions = {}) {
  return useQuery({
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

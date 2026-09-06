import { useEffect, useRef } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  getSimilarMedia,
  logAiInteraction,
  type SimilarMediaAsset,
} from "../api/aiApi";
import { authenticatedQueryMeta } from "../lib/queryClient";
import { queryKeys } from "../lib/queryKeys";

export type SimilarMediaState = "idle" | "loading" | "ready" | "empty" | "error";

export interface UseSimilarMediaReturn {
  state: SimilarMediaState;
  assets: SimilarMediaAsset[];
  refresh: () => void;
}

/**
 * Auto-fetches similar media assets from the library using pgvector cosine search (UC-3.3).
 * Triggers once when submissionId becomes available and savedAssets are present.
 * Logs a media_recommendation/shown event on first successful load.
 */
export function useSimilarMedia(
  submissionId: string | null,
  hasSavedAssets: boolean
): UseSimilarMediaReturn {
  const shouldFetch = Boolean(submissionId && hasSavedAssets);
  const loggedForRef = useRef<string | null>(null);
  const query = useQuery({
    queryKey: queryKeys.ai.similarMedia({ submissionId: submissionId ?? "" }),
    queryFn: ({ signal }) => getSimilarMedia(submissionId!, signal),
    enabled: shouldFetch,
    staleTime: 2 * 60_000,
    meta: authenticatedQueryMeta,
  });
  const assets = shouldFetch ? query.data ?? [] : [];

  useEffect(() => {
    if (!submissionId || assets.length === 0 || loggedForRef.current === submissionId) return;
    loggedForRef.current = submissionId;
    logAiInteraction(submissionId, "media_recommendation", "shown");
  }, [assets.length, submissionId]);

  function refresh() {
    if (!submissionId) return;
    loggedForRef.current = null;
    void query.refetch();
  }

  if (!shouldFetch) return { state: "idle", assets: [], refresh };
  if (query.isLoading || query.isFetching) return { state: "loading", assets, refresh };
  if (query.isError) return { state: "error", assets: [], refresh };
  return { state: assets.length === 0 ? "empty" : "ready", assets, refresh };
}

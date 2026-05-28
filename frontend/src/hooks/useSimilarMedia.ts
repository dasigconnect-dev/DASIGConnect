import { useEffect, useRef, useState } from "react";
import {
  getSimilarMedia,
  logAiInteraction,
  type SimilarMediaAsset,
} from "../api/aiApi";

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
  const [state, setState] = useState<SimilarMediaState>("idle");
  const [assets, setAssets] = useState<SimilarMediaAsset[]>([]);
  // Track which submissionId was last fetched to avoid double-fetches
  const fetchedForRef = useRef<string | null>(null);

  async function doFetch(id: string) {
    setState("loading");
    try {
      const data = await getSimilarMedia(id);
      setAssets(data);
      setState(data.length === 0 ? "empty" : "ready");
      if (data.length > 0) {
        logAiInteraction(id, "media_recommendation", "shown");
      }
    } catch {
      setState("error");
    }
  }

  useEffect(() => {
    if (!submissionId || !hasSavedAssets) return;
    if (fetchedForRef.current === submissionId) return;
    fetchedForRef.current = submissionId;
    doFetch(submissionId);
  }, [submissionId, hasSavedAssets]);

  function refresh() {
    if (!submissionId) return;
    fetchedForRef.current = null;
    doFetch(submissionId);
  }

  return { state, assets, refresh };
}

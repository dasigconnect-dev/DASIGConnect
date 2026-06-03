import { useCallback, useRef, useState } from "react";
import {
  hybridSearchMediaAssets,
  type HybridSearchParams,
  type MediaSearchHit,
} from "../../../api/mediaApi";

interface ApiError {
  name?: string;
  message?: string;
  response?: { data?: { error?: string; message?: string } };
}

function isApiError(error: unknown): error is ApiError {
  return typeof error === "object" && error !== null;
}

function isCanceledError(error: unknown) {
  return isApiError(error) && error.name === "CanceledError";
}

function getErrorMessage(error: unknown, fallback: string) {
  if (!isApiError(error)) return fallback;
  return error.response?.data?.error || error.response?.data?.message || error.message || fallback;
}

/**
 * Owns the natural-language hybrid search lifecycle (UC-4.5): running the query,
 * tracking the submitted term, loading/error state, and the ranked hits. Aborts
 * any in-flight request before starting a new one so out-of-order responses can
 * never clobber the latest results.
 */
export function useMediaSearch() {
  const [hits, setHits] = useState<MediaSearchHit[]>([]);
  const [activeQuery, setActiveQuery] = useState("");
  const [chronological, setChronological] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const controllerRef = useRef<AbortController | null>(null);

  const active = activeQuery.length > 0;

  const run = useCallback(async (params: HybridSearchParams) => {
    const query = params.query.trim();
    if (!query) return;

    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;

    setLoading(true);
    setError("");
    setActiveQuery(query);
    try {
      const result = await hybridSearchMediaAssets({ ...params, query }, controller.signal);
      if (controller.signal.aborted) return;
      setHits(result.hits);
      setChronological(result.chronological);
    } catch (err: unknown) {
      if (isCanceledError(err)) return;
      setHits([]);
      setChronological(false);
      setError(getErrorMessage(err, "Search is unavailable right now. Please try again."));
    } finally {
      if (!controller.signal.aborted) setLoading(false);
    }
  }, []);

  const clear = useCallback(() => {
    controllerRef.current?.abort();
    controllerRef.current = null;
    setHits([]);
    setActiveQuery("");
    setChronological(false);
    setError("");
    setLoading(false);
  }, []);

  return { hits, activeQuery, active, chronological, loading, error, run, clear };
}

import { useCallback, useEffect, useState } from "react";
import { getDuplicateCandidates, type DuplicateCandidatePair } from "../../../api/mediaApi";

interface ApiError {
  name?: string;
  message?: string;
  response?: { data?: { error?: string; message?: string } };
}

function isApiError(error: unknown): error is ApiError {
  return typeof error === "object" && error !== null;
}

function getErrorMessage(error: unknown, fallback: string) {
  if (!isApiError(error)) return fallback;
  return error.response?.data?.error || error.response?.data?.message || error.message || fallback;
}

function isCanceledError(error: unknown) {
  return isApiError(error) && error.name === "CanceledError";
}

/** UC-4.12 Phase 7F: loads pending duplicate candidate pairs for the review workspace. */
export function useDuplicateCandidates(institutionId: string | null, enabled = true) {
  const [pairs, setPairs] = useState<DuplicateCandidatePair[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const refresh = useCallback(
    (signal?: AbortSignal) => {
      if (!enabled) {
        setPairs([]);
        setLoading(false);
        setError("");
        return Promise.resolve();
      }
      setLoading(true);
      setError("");
      return getDuplicateCandidates({ status: "pending", institutionId }, signal)
        .then((data) => setPairs(Array.isArray(data) ? data : []))
        .catch((err: unknown) => {
          if (isCanceledError(err)) return;
          setError(getErrorMessage(err, "Unable to load duplicate candidates."));
        })
        .finally(() => setLoading(false));
    },
    [institutionId, enabled],
  );

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    queueMicrotask(() => {
      if (active) void refresh(controller.signal);
    });
    return () => {
      active = false;
      controller.abort();
    };
  }, [refresh]);

  return { pairs, setPairs, loading, error, refresh };
}

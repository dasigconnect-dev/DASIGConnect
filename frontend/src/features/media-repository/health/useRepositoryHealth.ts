import { useCallback, useEffect, useState } from "react";
import { getRepositoryHealth, type RepositoryHealth } from "../../../api/mediaApi";

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

/**
 * UC-4.12 Phase 7C: loads the tenant-scoped Repository Health snapshot. A null
 * institutionId requests the administrator network view; a concrete id scopes to one
 * institution. {@code enabled} gates the request until the caller's scope is ready.
 */
export function useRepositoryHealth(institutionId: string | null, enabled = true) {
  const [data, setData] = useState<RepositoryHealth | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const refresh = useCallback(
    (signal?: AbortSignal) => {
      if (!enabled) {
        setData(null);
        setLoading(false);
        setError("");
        return Promise.resolve();
      }
      setLoading(true);
      setError("");
      return getRepositoryHealth({ institutionId }, signal)
        .then(setData)
        .catch((err: unknown) => {
          if (isCanceledError(err)) return;
          setError(getErrorMessage(err, "Unable to load repository health."));
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

  return { data, loading, error, refresh };
}

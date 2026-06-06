import { useCallback, useEffect, useState } from "react";
import { getTrash, type TrashItem } from "../../../api/mediaApi";

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

/** UC-2.2 Trash: loads soft-deleted (not yet purged) media for the current scope. */
export function useTrash(institutionId: string | null, enabled = true) {
  const [items, setItems] = useState<TrashItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const refresh = useCallback(
    (signal?: AbortSignal) => {
      if (!enabled) {
        setItems([]);
        setLoading(false);
        setError("");
        return Promise.resolve();
      }
      setLoading(true);
      setError("");
      return getTrash(institutionId, signal)
        .then((data) => setItems(Array.isArray(data) ? data : []))
        .catch((err: unknown) => {
          if (isCanceledError(err)) return;
          setError(getErrorMessage(err, "Unable to load the trash."));
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

  return { items, setItems, loading, error, refresh };
}

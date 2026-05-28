import { useCallback, useEffect, useState } from "react";
import { listMediaAssets, type MediaAsset } from "../../../api/mediaApi";

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

export function useMediaAssets(networkView = false, institutionId?: string | null) {
  const [assets, setAssets] = useState<MediaAsset[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const refresh = useCallback(
    (signal?: AbortSignal) => {
      setLoading(true);
      setError("");
      return listMediaAssets({ networkView, institutionId }, signal)
        .then((response) => setAssets(Array.isArray(response.data) ? response.data : []))
        .catch((err: unknown) => {
          if (isCanceledError(err)) return;
          setError(getErrorMessage(err, "Unable to load media assets."));
        })
        .finally(() => setLoading(false));
    },
    [networkView, institutionId],
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

  return { assets, setAssets, loading, error, refresh };
}

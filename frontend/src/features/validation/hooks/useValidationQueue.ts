import { useCallback, useEffect, useState } from "react";
import {
  getValidationLog,
  getValidationQueue,
  type ValidationLog,
} from "../../../api/validationApi";
import type { SubmissionSummary } from "../../../api/submissionApi";

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

export function useValidationQueue() {
  const [queue, setQueue] = useState<SubmissionSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const refresh = useCallback((signal?: AbortSignal) => {
    setLoading(true);
    setError("");
    return getValidationQueue({ signal })
      .then((response) => setQueue(response.data))
      .catch((err: unknown) => {
        if (isCanceledError(err)) return;
        setError(getErrorMessage(err, "Unable to load the validation queue."));
      })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    void refresh(controller.signal);

    return () => {
      controller.abort();
    };
  }, [refresh]);

  return { queue, setQueue, loading, error, refresh };
}

export function useValidationLog(submissionId?: string | null) {
  const [log, setLog] = useState<ValidationLog[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const refresh = useCallback(
    (signal?: AbortSignal) => {
      if (!submissionId) {
        setLog([]);
        setError("");
        setLoading(false);
        return Promise.resolve();
      }

      setLoading(true);
      setError("");
      return getValidationLog(submissionId, signal)
        .then((response) => setLog(response.data))
        .catch((err: unknown) => {
          if (isCanceledError(err)) return;
          setError(getErrorMessage(err, "Unable to load the validation log."));
        })
        .finally(() => setLoading(false));
    },
    [submissionId],
  );

  useEffect(() => {
    const controller = new AbortController();
    void refresh(controller.signal);

    return () => {
      controller.abort();
    };
  }, [refresh]);

  return { log, loading, error, refresh };
}

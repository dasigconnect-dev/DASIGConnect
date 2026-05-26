import { useCallback, useEffect, useState } from "react";
import {
  cancelManualPublish,
  completeManualPublish,
  getResolutionFailures,
  retryPublication,
  startManualPublish,
  type FailedPublication,
} from "../api/resolutionApi";
import { useToast } from "../context/ToastContext";

export interface UseResolutionFailuresResult {
  failures: FailedPublication[];
  loading: boolean;
  error: string;
  busy: string | null;
  refresh: () => void;
  handleRetry: (item: FailedPublication) => Promise<void>;
  handleStartManual: (item: FailedPublication) => Promise<void>;
  handleCancelManual: (item: FailedPublication) => Promise<void>;
  handleCompleteManual: (
    item: FailedPublication,
    postUrl?: string,
    notes?: string,
  ) => Promise<void>;
}

export function useResolutionFailures(): UseResolutionFailuresResult {
  const toast = useToast();
  const [failures, setFailures] = useState<FailedPublication[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    queueMicrotask(() => {
      setLoading(true);
      setError("");
      getResolutionFailures(controller.signal)
        .then((res) => setFailures(res.data))
        .catch((err: unknown) => {
          if ((err as { name?: string }).name === "CanceledError") return;
          setError("Could not load failed publications. Please try again.");
        })
        .finally(() => setLoading(false));
    });
    return () => controller.abort();
  }, [tick]);

  const refresh = useCallback(() => setTick((n) => n + 1), []);

  async function handleRetry(item: FailedPublication) {
    setBusy(item.submissionId);
    try {
      await retryPublication(item.submissionId);
      toast.success(`Retrying "${item.eventTitle}"...`);
      refresh();
    } catch {
      toast.error("Retry failed. Please try again.");
    } finally {
      setBusy(null);
    }
  }

  async function handleStartManual(item: FailedPublication) {
    setBusy(item.submissionId);
    try {
      await startManualPublish(item.submissionId);
      toast.success("Manual publish session started.");
      refresh();
    } catch {
      toast.error("Could not start manual publish.");
    } finally {
      setBusy(null);
    }
  }

  async function handleCancelManual(item: FailedPublication) {
    setBusy(item.submissionId);
    try {
      await cancelManualPublish(item.submissionId);
      toast.info("Manual publish cancelled.");
      refresh();
    } catch {
      toast.error("Could not cancel manual publish.");
    } finally {
      setBusy(null);
    }
  }

  async function handleCompleteManual(
    item: FailedPublication,
    postUrl?: string,
    notes?: string,
  ) {
    setBusy(item.submissionId);
    try {
      await completeManualPublish(item.submissionId, {
        postUrl: postUrl || undefined,
        notes: notes || undefined,
      });
      toast.success(`"${item.eventTitle}" marked as published.`);
      refresh();
    } catch {
      toast.error("Could not complete manual publish.");
    } finally {
      setBusy(null);
    }
  }

  return {
    failures,
    loading,
    error,
    busy,
    refresh,
    handleRetry,
    handleStartManual,
    handleCancelManual,
    handleCompleteManual,
  };
}

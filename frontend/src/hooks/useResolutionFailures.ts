import { useCallback, useEffect, useState } from "react";
import {
  cancelManualPublish,
  completeManualPublish,
  getResolutionDetail,
  getResolutionFailures,
  retryPublicationWithNewSchedule,
  startManualPublish,
  type FailedPublication,
  type ManualPublishDetail,
} from "../api/resolutionApi";
import { useToast } from "../context/ToastContext";

export interface UseResolutionFailuresResult {
  failures: FailedPublication[];
  loading: boolean;
  error: string;
  busy: string | null;
  activeDetail: ManualPublishDetail | null;
  detailLoading: boolean;
  refresh: () => void;
  handleRetryWithNewSchedule: (
    item: FailedPublication,
    scheduledAt: string,
    overrideReason?: string,
  ) => Promise<void>;
  handleStartManual: (item: FailedPublication) => Promise<void>;
  handleCancelManual: (item: FailedPublication) => Promise<void>;
  handleCompleteManual: (
    item: FailedPublication,
    postUrl?: string,
    notes?: string,
  ) => Promise<void>;
  openWorkflowPanel: (item: FailedPublication) => void;
  closeWorkflowPanel: () => void;
}

export function useResolutionFailures(): UseResolutionFailuresResult {
  const toast = useToast();
  const [failures, setFailures] = useState<FailedPublication[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const [tick, setTick] = useState(0);
  const [activeDetail, setActiveDetail] = useState<ManualPublishDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

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

  function openWorkflowPanel(item: FailedPublication) {
    setActiveDetail(null);
    setDetailLoading(true);
    getResolutionDetail(item.submissionId)
      .then((res) => setActiveDetail(res.data))
      .catch(() => {
        toast.error("Could not load submission details.");
        setDetailLoading(false);
      })
      .finally(() => setDetailLoading(false));
  }

  function closeWorkflowPanel() {
    setActiveDetail(null);
    setDetailLoading(false);
  }

  async function handleRetryWithNewSchedule(
    item: FailedPublication,
    scheduledAt: string,
    overrideReason?: string,
  ) {
    setBusy(item.submissionId);
    try {
      await retryPublicationWithNewSchedule(item.submissionId, {
        scheduledAt,
        overrideReason: overrideReason || undefined,
      });
      toast.success(
        item.status === "missed_review"
          ? `"${item.eventTitle}" rescheduled and sent back to the approval queue.`
          : `"${item.eventTitle}" rescheduled and re-queued.`,
      );
      refresh();
    } catch (err: unknown) {
      const data = (err as { response?: { data?: unknown } })?.response?.data as
        | { message?: string; error?: string | { message?: string } }
        | undefined;
      const message =
        (typeof data?.error === "object" ? data?.error?.message : data?.error) ||
        data?.message ||
        "Could not reschedule this submission — the new time may break a guard rail.";
      toast.error(message);
      throw err;
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
      // Automatically open the workflow panel after starting
      openWorkflowPanel({ ...item, manualPublishInProgress: true });
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
      closeWorkflowPanel();
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
      closeWorkflowPanel();
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
    activeDetail,
    detailLoading,
    refresh,
    handleRetryWithNewSchedule,
    handleStartManual,
    handleCancelManual,
    handleCompleteManual,
    openWorkflowPanel,
    closeWorkflowPanel,
  };
}

import { useCallback, useEffect, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
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
import { authenticatedQueryMeta } from "../lib/queryClient";
import { queryKeys } from "../lib/queryKeys";
import type { User } from "../types/auth.types";

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

const RESOLUTION_FAILURES_STALE_TIME_MS = 30_000;
const RESOLUTION_DETAIL_STALE_TIME_MS = 15_000;

function userScope(user: User) {
  return user.id ?? user.email.trim().toLowerCase();
}

export function useResolutionFailures(user: User): UseResolutionFailuresResult {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [busy, setBusy] = useState<string | null>(null);
  const [activeDetailId, setActiveDetailId] = useState<string | null>(null);
  const detailErrorNotifiedRef = useRef<string | null>(null);
  const userId = userScope(user);
  const resolutionScope = {
    role: user.role,
    userId,
    institutionId: user.institutionId ?? null,
  };

  const failuresQuery = useQuery({
    queryKey: queryKeys.resolution.failures(resolutionScope),
    queryFn: ({ signal }) => getResolutionFailures(signal).then((response) => response.data),
    staleTime: RESOLUTION_FAILURES_STALE_TIME_MS,
    meta: authenticatedQueryMeta,
  });

  const detailQuery = useQuery({
    queryKey: queryKeys.resolution.detail({
      ...resolutionScope,
      submissionId: activeDetailId ?? "",
    }),
    queryFn: ({ signal }) => getResolutionDetail(activeDetailId ?? "", signal).then((response) => response.data),
    enabled: Boolean(activeDetailId),
    staleTime: RESOLUTION_DETAIL_STALE_TIME_MS,
    meta: authenticatedQueryMeta,
  });

  useEffect(() => {
    if (!activeDetailId || !detailQuery.isError || detailErrorNotifiedRef.current === activeDetailId) return;
    detailErrorNotifiedRef.current = activeDetailId;
    toast.error("Could not load submission details.");
  }, [activeDetailId, detailQuery.isError, toast]);

  const refresh = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: ["resolution"] });
  }, [queryClient]);

  function openWorkflowPanel(item: FailedPublication) {
    detailErrorNotifiedRef.current = null;
    setActiveDetailId(item.submissionId);
  }

  function closeWorkflowPanel() {
    setActiveDetailId(null);
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
      await queryClient.invalidateQueries({ queryKey: ["resolution"] });
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
      await queryClient.invalidateQueries({ queryKey: ["resolution"] });
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
      await queryClient.invalidateQueries({ queryKey: ["resolution"] });
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
      await queryClient.invalidateQueries({ queryKey: ["resolution"] });
    } catch {
      toast.error("Could not complete manual publish.");
    } finally {
      setBusy(null);
    }
  }

  return {
    failures: failuresQuery.data ?? [],
    loading: failuresQuery.isLoading || failuresQuery.isFetching,
    error: failuresQuery.error ? "Could not load failed publications. Please try again." : "",
    busy,
    activeDetail: detailQuery.data ?? null,
    detailLoading: detailQuery.isLoading || detailQuery.isFetching,
    refresh,
    handleRetryWithNewSchedule,
    handleStartManual,
    handleCancelManual,
    handleCompleteManual,
    openWorkflowPanel,
    closeWorkflowPanel,
  };
}

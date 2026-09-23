import { useCallback, useEffect, useRef, useState } from "react";
import { useInfiniteQuery, useQuery, useQueryClient, type InfiniteData } from "@tanstack/react-query";
import {
  cancelManualPublish,
  completeManualPublish,
  getResolutionDetail,
  getResolutionFailurePage,
  retryPublication,
  retryPublicationAsLive,
  retryPublicationWithNewSchedule,
  startManualPublish,
  type FailedPublication,
  type FailedPublicationPage,
  type ManualPublishDetail,
} from "../api/resolutionApi";
import { useToast } from "../context/ToastContext";
import { authenticatedQueryMeta } from "../lib/queryClient";
import { invalidateQueryRoots } from "../lib/queryInvalidation";
import { mutationCacheDependencies, queryKeys } from "../lib/queryKeys";
import type { User } from "../types/auth.types";

export interface UseResolutionFailuresResult {
  failures: FailedPublication[];
  loading: boolean;
  error: string;
  busy: string | null;
  activeDetail: ManualPublishDetail | null;
  detailLoading: boolean;
  totalCount: number;
  failureCount: number;
  hasNextPage: boolean;
  loadingMore: boolean;
  loadMoreError: boolean;
  loadMore: () => Promise<unknown>;
  refresh: () => void;
  handleRetryWithNewSchedule: (
    item: FailedPublication,
    scheduledAt: string,
    overrideReason?: string,
  ) => Promise<void>;
  /** Retries exactly as it was — no schedule, no mode change. */
  handleRetry: (item: FailedPublication) => Promise<void>;
  /** Admin-only: overrides a Scheduled failed publish to Live Event and retries immediately. */
  handleRetryAsLive: (item: FailedPublication) => Promise<void>;
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
const RESOLUTION_FAILURES_PAGE_SIZE = 20;

function userScope(user: User) {
  return user.id ?? user.email.trim().toLowerCase();
}

export function useResolutionFailures(
  user: User,
  enabled = true,
  search = "",
): UseResolutionFailuresResult {
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

  const failurePageKey = queryKeys.resolution.failurePage({
    ...resolutionScope,
    search,
    pageSize: RESOLUTION_FAILURES_PAGE_SIZE,
  });
  const failureCountKey = queryKeys.resolution.failureCount(resolutionScope);
  const failureCountQuery = useQuery<number>({
    queryKey: failureCountKey,
    queryFn: () => Promise.resolve(0),
    initialData: 0,
    enabled: false,
    staleTime: Infinity,
    meta: authenticatedQueryMeta,
  });
  const failuresQuery = useInfiniteQuery({
    queryKey: failurePageKey,
    queryFn: ({ signal, pageParam }) => getResolutionFailurePage({
      page: pageParam,
      pageSize: RESOLUTION_FAILURES_PAGE_SIZE,
      search,
    }, signal).then((response) => {
      queryClient.setQueryData(failureCountKey, response.data.failureCount);
      return response.data;
    }),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => lastPage.hasNext ? lastPage.page + 1 : undefined,
    staleTime: RESOLUTION_FAILURES_STALE_TIME_MS,
    enabled,
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
    void queryClient.resetQueries({ queryKey: failurePageKey, exact: true });
  }, [failurePageKey, queryClient]);

  const invalidateResolutionSession = useCallback(
    () => invalidateQueryRoots(queryClient, mutationCacheDependencies.resolutionSession),
    [queryClient],
  );

  const updateFailurePages = useCallback((
    submissionId: string,
    update: ((item: FailedPublication) => FailedPublication) | null,
  ) => {
    queryClient.setQueriesData<InfiniteData<FailedPublicationPage, number>>(
      {
        queryKey: ["resolution"],
        predicate: (query) => {
          const params = query.queryKey[1];
          return typeof params === "object" && params !== null
            && "view" in params && params.view === "failures-page";
        },
      },
      (current) => {
        if (!current?.pages.length) return current;
        const matched = current.pages.some((page) =>
          page.items.some((item) => item.submissionId === submissionId));
        const pages = current.pages.map((page) => ({
          ...page,
          items: update
            ? page.items.map((item) => item.submissionId === submissionId ? update(item) : item)
            : page.items.filter((item) => item.submissionId !== submissionId),
          totalCount: !update && matched ? Math.max(0, page.totalCount - 1) : page.totalCount,
          failureCount: !update && matched ? Math.max(0, page.failureCount - 1) : page.failureCount,
        }));
        return {
          pages: [pages[0]],
          pageParams: [current.pageParams[0] ?? 0],
        };
      },
    );
    if (!update) {
      queryClient.setQueryData<number>(failureCountKey, (current) => Math.max(0, (current ?? 0) - 1));
    }
  }, [failureCountKey, queryClient]);

  const invalidateResolutionOutcome = useCallback(
    () => invalidateQueryRoots(queryClient, mutationCacheDependencies.resolutionOutcome),
    [queryClient],
  );

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
      updateFailurePages(item.submissionId, null);
      await invalidateResolutionOutcome();
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

  async function handleRetry(item: FailedPublication) {
    setBusy(item.submissionId);
    try {
      await retryPublication(item.submissionId);
      toast.success(`"${item.eventTitle}" re-queued for publishing.`);
      updateFailurePages(item.submissionId, null);
      await invalidateResolutionOutcome();
    } catch (err: unknown) {
      const data = (err as { response?: { data?: unknown } })?.response?.data as
        | { message?: string; error?: string | { message?: string } }
        | undefined;
      const message =
        (typeof data?.error === "object" ? data?.error?.message : data?.error) ||
        data?.message ||
        "Could not retry this submission.";
      toast.error(message);
      throw err;
    } finally {
      setBusy(null);
    }
  }

  async function handleRetryAsLive(item: FailedPublication) {
    setBusy(item.submissionId);
    try {
      await retryPublicationAsLive(item.submissionId);
      toast.success(`"${item.eventTitle}" switched to Live Event and re-queued.`);
      updateFailurePages(item.submissionId, null);
      await invalidateResolutionOutcome();
    } catch (err: unknown) {
      const data = (err as { response?: { data?: unknown } })?.response?.data as
        | { message?: string; error?: string | { message?: string } }
        | undefined;
      const message =
        (typeof data?.error === "object" ? data?.error?.message : data?.error) ||
        data?.message ||
        "Could not switch this submission to Live Event.";
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
      updateFailurePages(item.submissionId, (current) => ({
        ...current,
        manualPublishInProgress: true,
      }));
      await invalidateResolutionSession();
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
      updateFailurePages(item.submissionId, (current) => ({
        ...current,
        manualPublishInProgress: false,
      }));
      await invalidateResolutionSession();
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
      updateFailurePages(item.submissionId, null);
      await invalidateResolutionOutcome();
    } catch {
      toast.error("Could not complete manual publish.");
    } finally {
      setBusy(null);
    }
  }

  return {
    failures: failuresQuery.data?.pages.flatMap((page) => page.items) ?? [],
    loading: failuresQuery.isLoading,
    error: failuresQuery.error && !failuresQuery.data
      ? "Could not load failed publications. Please try again."
      : "",
    busy,
    activeDetail: detailQuery.data ?? null,
    detailLoading: detailQuery.isLoading,
    totalCount: failuresQuery.data?.pages[0]?.totalCount ?? 0,
    failureCount: failureCountQuery.data,
    hasNextPage: Boolean(failuresQuery.hasNextPage),
    loadingMore: failuresQuery.isFetchingNextPage,
    loadMoreError: failuresQuery.isFetchNextPageError,
    loadMore: failuresQuery.fetchNextPage,
    refresh,
    handleRetryWithNewSchedule,
    handleRetry,
    handleRetryAsLive,
    handleStartManual,
    handleCancelManual,
    handleCompleteManual,
    openWorkflowPanel,
    closeWorkflowPanel,
  };
}

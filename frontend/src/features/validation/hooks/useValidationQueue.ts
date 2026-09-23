import { useCallback } from "react";
import { useInfiniteQuery, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  getValidationLog,
  getValidationQueuePage,
} from "../../../api/validationApi";
import type {
  ValidationLog,
  ValidationQueueCounts,
  ValidationQueueSort,
  ValidationQueueView,
} from "../../../api/validationApi";
import { authenticatedQueryMeta } from "../../../lib/queryClient";
import { queryKeys } from "../../../lib/queryKeys";
import type { User } from "../../../types/auth.types";

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

const VALIDATION_QUEUE_STALE_TIME_MS = 5_000;
const VALIDATION_LOG_STALE_TIME_MS = 30_000;
const VALIDATION_QUEUE_PAGE_SIZE = 20;

const emptyValidationCounts: ValidationQueueCounts = {
  all: 0,
  pending: 0,
  in_review: 0,
  needs_revision: 0,
  scheduled: 0,
  published: 0,
  rejected: 0,
};

function userScope(user: User) {
  return user.id ?? user.email.trim().toLowerCase();
}

export function useValidationQueue(
  user: User,
  view: ValidationQueueView,
  sort: ValidationQueueSort,
  search: string,
  enabled = true,
) {
  const queryClient = useQueryClient();
  const queryKey = queryKeys.validation.page({
    role: user.role,
    userId: userScope(user),
    institutionId: user.institutionId ?? null,
    queueView: view,
    sort,
    search,
    pageSize: VALIDATION_QUEUE_PAGE_SIZE,
  });
  const countsKey = queryKeys.validation.counts({
    role: user.role,
    userId: userScope(user),
    institutionId: user.institutionId ?? null,
  });
  const countsQuery = useQuery<ValidationQueueCounts>({
    queryKey: countsKey,
    queryFn: () => Promise.resolve(emptyValidationCounts),
    initialData: emptyValidationCounts,
    enabled: false,
    staleTime: Infinity,
    meta: authenticatedQueryMeta,
  });

  const query = useInfiniteQuery({
    queryKey,
    queryFn: ({ signal, pageParam }) =>
      getValidationQueuePage({
        view,
        sort,
        search,
        page: pageParam,
        pageSize: VALIDATION_QUEUE_PAGE_SIZE,
      }, signal).then((response) => {
        queryClient.setQueryData(countsKey, response.data.counts);
        return response.data;
      }),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => lastPage.hasNext ? lastPage.page + 1 : undefined,
    staleTime: VALIDATION_QUEUE_STALE_TIME_MS,
    enabled,
    meta: authenticatedQueryMeta,
  });

  const firstPage = query.data?.pages[0];
  const refresh = useCallback(() => {
    return queryClient.resetQueries({ queryKey, exact: true });
  }, [queryClient, queryKey]);

  return {
    queue: query.data?.pages.flatMap((page) => page.items) ?? [],
    counts: firstPage?.counts ?? countsQuery.data,
    totalCount: firstPage?.totalCount ?? 0,
    hasNextPage: Boolean(query.hasNextPage),
    loadingMore: query.isFetchingNextPage,
    loadMoreError: query.isFetchNextPageError,
    loadMore: query.fetchNextPage,
    loading: query.isLoading,
    refreshing: query.isFetching && !query.isLoading && !query.isFetchingNextPage,
    error: query.error && !query.data && !isCanceledError(query.error)
      ? getErrorMessage(query.error, "Unable to load the validation queue.")
      : "",
    refresh,
  };
}

export function useValidationLog(user: User, submissionId?: string | null) {
  const queryClient = useQueryClient();
  const queryKey = queryKeys.validation.log({
    role: user.role,
    userId: userScope(user),
    submissionId: submissionId ?? "pending",
  });

  const query = useQuery<ValidationLog[]>({
    queryKey,
    queryFn: ({ signal }) =>
      getValidationLog(submissionId ?? "", signal).then((response) =>
        Array.isArray(response.data) ? response.data : [],
      ),
    enabled: Boolean(submissionId),
    staleTime: VALIDATION_LOG_STALE_TIME_MS,
    meta: authenticatedQueryMeta,
  });

  const refresh = useCallback(() => {
    return queryClient.invalidateQueries({ queryKey: ["validation"] });
  }, [queryClient]);

  return {
    log: query.data ?? [],
    loading: query.isLoading,
    refreshing: query.isFetching,
    error: query.error && !isCanceledError(query.error)
      ? getErrorMessage(query.error, "Unable to load the validation log.")
      : "",
    refresh,
  };
}

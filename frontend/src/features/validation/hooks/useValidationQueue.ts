import { useCallback, type Dispatch, type SetStateAction } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  getValidationLog,
  getValidationQueue,
} from "../../../api/validationApi";
import type { ValidationLog } from "../../../api/validationApi";
import type { SubmissionSummary } from "../../../api/submissionApi";
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

function userScope(user: User) {
  return user.id ?? user.email.trim().toLowerCase();
}

export function useValidationQueue(user: User, history = false) {
  const queryClient = useQueryClient();
  const queryKey = queryKeys.validation.queue({
    role: user.role,
    userId: userScope(user),
    institutionId: user.institutionId ?? null,
    scope: history ? "history" : "network",
  });

  const query = useQuery<SubmissionSummary[]>({
    queryKey,
    queryFn: ({ signal }) =>
      getValidationQueue({ history, signal }).then((response) =>
        Array.isArray(response.data) ? response.data : [],
      ),
    staleTime: VALIDATION_QUEUE_STALE_TIME_MS,
    meta: authenticatedQueryMeta,
  });

  const setQueue: Dispatch<SetStateAction<SubmissionSummary[]>> = useCallback(
    (value) => {
      queryClient.setQueryData<SubmissionSummary[]>(queryKey, (current = []) => {
        return typeof value === "function"
          ? (value as (previous: SubmissionSummary[]) => SubmissionSummary[])(current)
          : value;
      });
    },
    [queryClient, queryKey],
  );

  const refresh = useCallback(() => {
    return queryClient.invalidateQueries({ queryKey: ["validation"] });
  }, [queryClient]);

  return {
    queue: query.data ?? [],
    setQueue,
    loading: query.isLoading || query.isFetching,
    error: query.error && !isCanceledError(query.error)
      ? getErrorMessage(query.error, history ? "Unable to load all submissions." : "Unable to load the validation queue.")
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
    loading: query.isLoading || query.isFetching,
    error: query.error && !isCanceledError(query.error)
      ? getErrorMessage(query.error, "Unable to load the validation log.")
      : "",
    refresh,
  };
}

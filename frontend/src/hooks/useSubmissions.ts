import { useCallback, type Dispatch, type SetStateAction } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  getSubmissionLookups,
  listSubmissions,
  type SubmissionLookups,
  type SubmissionSummary,
} from "../api/submissionApi";
import { authenticatedQueryMeta } from "../lib/queryClient";
import { queryKeys } from "../lib/queryKeys";
import type { User } from "../types/auth.types";

const emptyLookups: SubmissionLookups = {
  allowedFileTypes: [],
  allowedImageTypes: [],
  allowedVideoTypes: [],
  maxFileSizeMb: 50,
  maxMediaAssetsPerSubmission: 10,
  maxTitleLength: 255,
  minScheduleLeadTimeHours: 2,
  maxScheduleDaysAhead: 30,
  categories: [],
  availableTags: [],
};

const SUBMISSIONS_STALE_TIME_MS = 30_000;
const LOOKUPS_STALE_TIME_MS = 5 * 60_000;

function userScope(user: User) {
  return user.id ?? user.email.trim().toLowerCase();
}

export function useSubmissions(user: User) {
  const queryClient = useQueryClient();
  const queryKey = queryKeys.submissions.all({
    role: user.role,
    userId: userScope(user),
    institutionId: user.institutionId ?? null,
  });

  const submissionsQuery = useQuery({
    queryKey,
    queryFn: ({ signal }) => listSubmissions(signal).then((response) => response.data),
    staleTime: SUBMISSIONS_STALE_TIME_MS,
    meta: authenticatedQueryMeta,
  });

  const setSubmissions = useCallback<Dispatch<SetStateAction<SubmissionSummary[]>>>(
    (action) => {
      queryClient.setQueryData<SubmissionSummary[]>(queryKey, (prev = []) => {
        return typeof action === "function"
          ? (action as (p: SubmissionSummary[]) => SubmissionSummary[])(prev)
          : action;
      });
    },
    [queryClient, queryKey],
  );

  const refresh = useCallback(async () => {
    return queryClient.fetchQuery({
      queryKey,
      queryFn: ({ signal }) => listSubmissions(signal).then((response) => response.data),
      staleTime: 0,
      meta: authenticatedQueryMeta,
    });
  }, [queryClient, queryKey]);

  return {
    submissions: submissionsQuery.data ?? [],
    setSubmissions,
    loading: submissionsQuery.isLoading || submissionsQuery.isFetching,
    error: submissionsQuery.error ? "Unable to load submissions." : "",
    refresh,
  };
}

export function useSubmissionLookups(user: User) {
  const queryClient = useQueryClient();
  const queryKey = queryKeys.submissions.lookups({
    role: user.role,
    institutionId: user.institutionId ?? null,
  });

  const lookupsQuery = useQuery({
    queryKey,
    queryFn: ({ signal }) => getSubmissionLookups(signal).then((response) => response.data),
    staleTime: LOOKUPS_STALE_TIME_MS,
    meta: authenticatedQueryMeta,
  });

  const refresh = useCallback(async () => {
    return queryClient.fetchQuery({
      queryKey,
      queryFn: ({ signal }) => getSubmissionLookups(signal).then((response) => response.data),
      staleTime: 0,
      meta: authenticatedQueryMeta,
    });
  }, [queryClient, queryKey]);

  return {
    lookups: lookupsQuery.data ?? emptyLookups,
    loading: lookupsQuery.isLoading || lookupsQuery.isFetching,
    error: lookupsQuery.error ? "Unable to load submission settings." : "",
    refresh,
  };
}

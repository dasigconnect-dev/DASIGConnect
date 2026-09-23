import { useCallback } from "react";
import { useInfiniteQuery, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  getSubmissionLookups,
  listSubmissionPage,
  type SubmissionBucketCounts,
  type SubmissionQueueBucket,
  type SubmissionLookups,
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
  guardrailsEnforced: true,
};

const SUBMISSIONS_STALE_TIME_MS = 30_000;
const SUBMISSIONS_PAGE_SIZE = 20;
const LOOKUPS_STALE_TIME_MS = 5 * 60_000;

const emptySubmissionCounts: SubmissionBucketCounts = {
  all: 0,
  drafts: 0,
  "action-needed": 0,
  submitted: 0,
  published: 0,
  failed: 0,
};

function userScope(user: User) {
  return user.id ?? user.email.trim().toLowerCase();
}

export function useSubmissions(
  user: User,
  bucket: SubmissionQueueBucket,
  search: string,
  enabled = true,
) {
  const queryClient = useQueryClient();
  const queryKey = queryKeys.submissions.page({
    role: user.role,
    userId: userScope(user),
    institutionId: user.institutionId ?? null,
    bucket,
    search,
    pageSize: SUBMISSIONS_PAGE_SIZE,
  });

  const query = useInfiniteQuery({
    queryKey,
    queryFn: ({ signal, pageParam }) =>
      listSubmissionPage({
        page: pageParam,
        pageSize: SUBMISSIONS_PAGE_SIZE,
        bucket,
        search,
      }, signal).then((response) => response.data),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => lastPage.hasNext ? lastPage.page + 1 : undefined,
    enabled,
    staleTime: SUBMISSIONS_STALE_TIME_MS,
    meta: authenticatedQueryMeta,
  });

  const firstPage = query.data?.pages[0];
  const refresh = useCallback(
    () => queryClient.resetQueries({ queryKey, exact: true }),
    [queryClient, queryKey],
  );

  return {
    submissions: query.data?.pages.flatMap((page) => page.items) ?? [],
    counts: firstPage?.counts ?? emptySubmissionCounts,
    totalCount: firstPage?.totalCount ?? 0,
    hasNextPage: Boolean(query.hasNextPage),
    loadingMore: query.isFetchingNextPage,
    loadMoreError: query.isFetchNextPageError,
    loadMore: query.fetchNextPage,
    loading: query.isLoading,
    refreshing: query.isFetching && !query.isLoading && !query.isFetchingNextPage,
    error: query.error ? "Unable to load submissions." : "",
    refresh,
  };
}

export function useSubmissionLookups(user: User, enabled = true) {
  const queryClient = useQueryClient();
  const queryKey = queryKeys.submissions.lookups({
    role: user.role,
    userId: userScope(user),
    institutionId: user.institutionId ?? null,
  });

  const lookupsQuery = useQuery({
    queryKey,
    queryFn: ({ signal }) => getSubmissionLookups(signal).then((response) => response.data),
    enabled,
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
    loading: lookupsQuery.isLoading,
    refreshing: lookupsQuery.isFetching && !lookupsQuery.isLoading,
    error: lookupsQuery.error ? "Unable to load submission settings." : "",
    refresh,
  };
}

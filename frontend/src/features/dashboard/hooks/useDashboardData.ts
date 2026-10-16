import { useQuery, type UseQueryResult } from "@tanstack/react-query";
import {
  listInstitutions,
  listNetworkUsers,
  listPendingAdminInvitations,
  listPendingNetworkInvitations,
} from "../../../api/authApi";
import { getAnalyticsSummary } from "../../../api/analyticsApi";
import { listSubmissionPage, type SubmissionSummary } from "../../../api/submissionApi";
import {
  getValidationDashboardSummary,
  getValidationQueuePage,
} from "../../../api/validationApi";
import { authenticatedQueryMeta } from "../../../lib/queryClient";
import { queryKeys } from "../../../lib/queryKeys";
import type { User } from "../../../types/auth.types";

export interface DashboardInstitution {
  id: string;
  name: string;
}

export interface DashboardStats {
  submissions: SubmissionSummary[] | null;
  totalSubmissions: number | null;
  publishedSubmissions: number | null;
  scheduledSubmissions: number | null;
  underReviewSubmissions: number | null;
  needsRevisionSubmissions: number | null;
  contributors: number | null;
  moderators: number | null;
  activeMembers: number | null;
  pendingInvitations: number | null;
  reviewQueuePending: number | null;
  reviewedApprovedThisMonth: number | null;
  reviewedRejectedThisMonth: number | null;
  scheduledNetwork: number | null;
  publishedLast30d: number | null;
  publishingSuccessRate: number | null;
  reviewRecent: SubmissionSummary[] | null;
}

export interface DashboardResourceState {
  loading: boolean;
  refreshing: boolean;
  error: boolean;
}

export const emptyDashboardStats: DashboardStats = {
  submissions: null,
  totalSubmissions: null,
  publishedSubmissions: null,
  scheduledSubmissions: null,
  underReviewSubmissions: null,
  needsRevisionSubmissions: null,
  contributors: null,
  moderators: null,
  activeMembers: null,
  pendingInvitations: null,
  reviewQueuePending: null,
  reviewedApprovedThisMonth: null,
  reviewedRejectedThisMonth: null,
  scheduledNetwork: null,
  publishedLast30d: null,
  publishingSuccessRate: null,
  reviewRecent: null,
};

const DASHBOARD_STALE_TIME_MS = 30_000;

export function useDashboardData(user: User) {
  const userId = user.id ?? user.email.trim().toLowerCase();
  const scope = { role: user.role, userId, institutionId: user.institutionId ?? null };
  const options = (resource: string, enabled: boolean) => ({
    queryKey: queryKeys.dashboard.resource({ ...scope, resource }),
    enabled,
    staleTime: DASHBOARD_STALE_TIME_MS,
    meta: authenticatedQueryMeta,
  });

  const submissionsQuery = useQuery({
    queryKey: queryKeys.submissions.page({
      ...scope,
      bucket: "all",
      search: "",
      pageSize: 5,
    }),
    enabled: user.role === "contributor",
    queryFn: ({ signal }) => listSubmissionPage(
      { page: 0, pageSize: 5, bucket: "all" },
      signal,
    ).then((response) => response.data),
    staleTime: DASHBOARD_STALE_TIME_MS,
    meta: authenticatedQueryMeta,
  });
  const queueQuery = useQuery({
    queryKey: queryKeys.validation.page({
      ...scope,
      queueView: "all",
      sort: "submitted",
      search: "",
      pageSize: 5,
    }),
    enabled: user.role !== "contributor",
    queryFn: ({ signal }) => getValidationQueuePage(
      { view: "all", sort: "submitted", page: 0, pageSize: 5 },
      signal,
    ).then((response) => response.data),
    staleTime: DASHBOARD_STALE_TIME_MS,
    meta: authenticatedQueryMeta,
  });
  const validationSummaryQuery = useQuery({
    queryKey: queryKeys.validation.dashboardSummary(scope),
    enabled: user.role !== "contributor",
    queryFn: ({ signal }) => getValidationDashboardSummary(signal).then((response) => response.data),
    staleTime: DASHBOARD_STALE_TIME_MS,
    meta: authenticatedQueryMeta,
  });
  const institutionsQuery = useQuery({
    ...options("institutions", user.role === "admin"),
    queryFn: ({ signal }) => listInstitutions(signal).then((response) => response.data),
  });
  const usersQuery = useQuery({
    ...options("users", user.role === "admin"),
    queryFn: ({ signal }) => listNetworkUsers(signal).then((response) => response.data),
  });
  const networkInvitesQuery = useQuery({
    ...options("network-invitations", user.role === "admin"),
    queryFn: ({ signal }) => listPendingNetworkInvitations(signal).then((response) => response.data),
  });
  const adminInvitesQuery = useQuery({
    ...options("admin-invitations", user.role === "admin"),
    queryFn: ({ signal }) => listPendingAdminInvitations(signal).then((response) => response.data),
  });
  const analyticsQuery = useQuery({
    ...options("analytics-30d", user.role === "admin"),
    queryFn: ({ signal }) => getAnalyticsSummary("30d", [], signal).then((response) => response.data),
  });

  const users = usersQuery.data;
  const analytics = analyticsQuery.data;
  const breakdown = analytics?.statusBreakdown ?? [];
  const scheduledFromAnalytics = breakdown.find((item) => item.status.toLowerCase() === "scheduled")?.count;
  const activeOf = (role: string) =>
    users?.filter((item) => item.role.toLowerCase() === role && item.accountState.toLowerCase() === "active").length ?? null;
  const activeContributors = activeOf("contributor");
  const activeModerators = activeOf("moderator");
  const activeAdmins = activeOf("admin");
  const submissionCounts = submissionsQuery.data?.counts;
  const validationSummary = validationSummaryQuery.data;

  const stats: DashboardStats = {
    ...emptyDashboardStats,
    submissions: submissionsQuery.data?.items ?? null,
    totalSubmissions: submissionCounts?.all ?? null,
    publishedSubmissions: submissionCounts?.published ?? null,
    scheduledSubmissions: submissionCounts?.scheduled ?? null,
    underReviewSubmissions: submissionCounts?.["under-review"] ?? null,
    needsRevisionSubmissions: submissionCounts?.["action-needed"] ?? null,
    contributors: user.role === "admin" ? activeContributors : validationSummary?.contributorCount ?? null,
    moderators: activeModerators,
    activeMembers:
      activeContributors !== null && activeModerators !== null && activeAdmins !== null
        ? activeContributors + activeModerators + activeAdmins
        : null,
    pendingInvitations:
      networkInvitesQuery.data && adminInvitesQuery.data
        ? networkInvitesQuery.data.length + adminInvitesQuery.data.length
        : null,
    reviewQueuePending: validationSummary?.awaitingReview ?? null,
    reviewedApprovedThisMonth: validationSummary?.approvedThisMonth ?? null,
    reviewedRejectedThisMonth: validationSummary?.rejectedThisMonth ?? null,
    scheduledNetwork: scheduledFromAnalytics ?? queueQuery.data?.counts.scheduled ?? null,
    publishedLast30d: analytics?.totalPostsPublished.value ?? null,
    publishingSuccessRate:
      analytics?.operationalHealth && analytics.operationalHealth.publicationAttempts > 0
        ? Math.round(analytics.operationalHealth.publishingSuccessRate)
        : null,
    reviewRecent: queueQuery.data?.items ?? null,
  };

  const primaryQuery = user.role === "contributor" ? submissionsQuery : queueQuery;
  return {
    institutions: institutionsQuery.data?.map((item) => ({ id: item.id, name: item.name })) ?? [],
    stats,
    activity: {
      ...queryState(primaryQuery),
      retry: () => primaryQuery.refetch(),
    },
    resources: {
      submissions: queryState(submissionsQuery),
      queue: queryState(queueQuery),
      history: queryState(validationSummaryQuery),
      institutions: queryState(institutionsQuery),
      users: queryState(usersQuery),
      invitations: combineStates(networkInvitesQuery, adminInvitesQuery),
      analytics: queryState(analyticsQuery),
    },
  };
}

function queryState(query: UseQueryResult<unknown>): DashboardResourceState {
  return {
    loading: query.isLoading,
    refreshing: query.isFetching && !query.isLoading,
    error: query.isError && query.data === undefined,
  };
}

function combineStates(...queries: UseQueryResult<unknown>[]): DashboardResourceState {
  return {
    loading: queries.some((query) => query.isLoading),
    refreshing: queries.some((query) => query.isFetching && !query.isLoading),
    error: queries.some((query) => query.isError && query.data === undefined),
  };
}

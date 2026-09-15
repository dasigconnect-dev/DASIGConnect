import { useQuery, type UseQueryResult } from "@tanstack/react-query";
import {
  listInstitutions,
  listNetworkUsers,
  listPendingAdminInvitations,
  listPendingNetworkInvitations,
} from "../../../api/authApi";
import { getAnalyticsSummary } from "../../../api/analyticsApi";
import { listSubmissions, type SubmissionSummary } from "../../../api/submissionApi";
import { getValidationQueue } from "../../../api/validationApi";
import { authenticatedQueryMeta } from "../../../lib/queryClient";
import { queryKeys } from "../../../lib/queryKeys";
import type { User } from "../../../types/auth.types";

export interface DashboardInstitution {
  id: string;
  name: string;
}

export interface DashboardStats {
  submissions: SubmissionSummary[] | null;
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
    ...options("submissions", user.role === "contributor"),
    queryFn: ({ signal }) => listSubmissions(signal).then((response) => response.data),
  });
  const queueQuery = useQuery({
    queryKey: queryKeys.validation.queue({
      role: user.role,
      userId,
      institutionId: user.institutionId ?? null,
      scope: "network",
    }),
    enabled: user.role !== "contributor",
    queryFn: ({ signal }) => getValidationQueue({ signal }).then((response) => response.data),
    staleTime: DASHBOARD_STALE_TIME_MS,
    meta: authenticatedQueryMeta,
  });
  const historyQuery = useQuery({
    queryKey: queryKeys.validation.queue({
      role: user.role,
      userId,
      institutionId: user.institutionId ?? null,
      scope: "history",
    }),
    enabled: user.role !== "contributor",
    queryFn: ({ signal }) => getValidationQueue({ history: true, signal }).then((response) => response.data),
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
    queryFn: ({ signal }) => getAnalyticsSummary("30d", null, signal).then((response) => response.data),
  });

  const queue = queueQuery.data ?? [];
  const history = historyQuery.data ?? [];
  const reviewItems = [...queue, ...history];
  const users = usersQuery.data;
  const analytics = analyticsQuery.data;
  const breakdown = analytics?.statusBreakdown ?? [];
  const historyScheduled = historyQuery.data?.filter((item) => item.status === "scheduled").length;
  const scheduledFromAnalytics = breakdown.find((item) => item.status.toLowerCase() === "scheduled")?.count;
  const activeOf = (role: string) =>
    users?.filter((item) => item.role.toLowerCase() === role && item.accountState.toLowerCase() === "active").length ?? null;
  const activeContributors = activeOf("contributor");
  const activeModerators = activeOf("moderator");
  const activeAdmins = activeOf("admin");
  const moderatorContributorCount = reviewItems.length > 0 || (queueQuery.data && historyQuery.data)
    ? new Set(
        reviewItems
          .map((item) => item.contributorEmail?.trim().toLowerCase())
          .filter((email): email is string => Boolean(email)),
      ).size
    : null;

  const stats: DashboardStats = {
    ...emptyDashboardStats,
    submissions: submissionsQuery.data ?? null,
    contributors: user.role === "admin" ? activeContributors : moderatorContributorCount,
    moderators: activeModerators,
    activeMembers:
      activeContributors !== null && activeModerators !== null && activeAdmins !== null
        ? activeContributors + activeModerators + activeAdmins
        : null,
    pendingInvitations:
      networkInvitesQuery.data && adminInvitesQuery.data
        ? networkInvitesQuery.data.length + adminInvitesQuery.data.length
        : null,
    reviewQueuePending: queueQuery.data?.length ?? null,
    reviewedApprovedThisMonth: historyQuery.data
      ? history.filter(
          (item) =>
            inCurrentMonth(item) &&
            ["scheduled", "published", "published_manual", "admin_direct_post"].includes(item.status),
        ).length
      : null,
    reviewedRejectedThisMonth: historyQuery.data
      ? history.filter((item) => inCurrentMonth(item) && item.status === "rejected").length
      : null,
    scheduledNetwork: scheduledFromAnalytics ?? historyScheduled ?? null,
    publishedLast30d: analytics?.totalPostsPublished.value ?? null,
    publishingSuccessRate:
      analytics?.operationalHealth && analytics.operationalHealth.publicationAttempts > 0
        ? Math.round(analytics.operationalHealth.publishingSuccessRate)
        : null,
    reviewRecent: queueQuery.data || historyQuery.data ? newestSubmissions(reviewItems, 5) : null,
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
      history: queryState(historyQuery),
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

function newestSubmissions(items: SubmissionSummary[], limit: number) {
  return [...items]
    .sort((a, b) => (b.submittedAt ?? b.createdAt ?? "").localeCompare(a.submittedAt ?? a.createdAt ?? ""))
    .slice(0, limit);
}

function inCurrentMonth(submission: SubmissionSummary) {
  const monthKey = new Date().toISOString().slice(0, 7);
  return (
    submission.publishedAt ??
    submission.scheduledAt ??
    submission.submittedAt ??
    submission.createdAt ??
    ""
  ).slice(0, 7) === monthKey;
}

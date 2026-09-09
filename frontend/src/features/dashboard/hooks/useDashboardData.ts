import { useQuery } from "@tanstack/react-query";
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
  /** Contributor only: the caller's own submissions (GET /submissions). */
  submissions: SubmissionSummary[];
  /** Admin: active contributor accounts. Moderator: contributors in visible review activity. */
  contributors: number;
  /** Active moderator accounts, network-wide. */
  moderators: number;
  /** Admin only: all active accounts (contributors + moderators + admins). */
  activeMembers: number;
  /** Open invitations, network-wide (institution + network-role). */
  pendingInvitations: number;
  /** Reviewer + admin: live PENDING + IN_REVIEW count from the network queue. */
  reviewQueuePending: number;
  /** Moderator only: this-month approved + rejected from the review history. */
  reviewedApprovedThisMonth: number;
  reviewedRejectedThisMonth: number;
  /** Admin only: SCHEDULED submissions network-wide (upcoming pipeline). */
  scheduledNetwork: number;
  /** Admin only: posts published in the last 30 days, network-wide. */
  publishedLast30d: number;
  /** Admin only: publishing success rate (%) over the last 30 days, or null. */
  publishingSuccessRate: number | null;
  /** Reviewer + admin: recent network submissions (queue + history) for the activity table. */
  reviewRecent: SubmissionSummary[];
}

interface DashboardData {
  institutions: DashboardInstitution[];
  stats: DashboardStats;
}

export const emptyDashboardStats: DashboardStats = {
  submissions: [],
  contributors: 0,
  moderators: 0,
  activeMembers: 0,
  pendingInvitations: 0,
  reviewQueuePending: 0,
  reviewedApprovedThisMonth: 0,
  reviewedRejectedThisMonth: 0,
  scheduledNetwork: 0,
  publishedLast30d: 0,
  publishingSuccessRate: null,
  reviewRecent: [],
};

const DASHBOARD_STALE_TIME_MS = 30_000;

export function useDashboardData(user: User) {
  const userScope = user.id ?? user.email.trim().toLowerCase();

  return useQuery({
    queryKey: queryKeys.dashboard.summary({
      role: user.role,
      userId: userScope,
      institutionId: user.institutionId ?? null,
    }),
    queryFn: ({ signal }) => fetchDashboardData(user, signal),
    enabled: Boolean(user),
    staleTime: DASHBOARD_STALE_TIME_MS,
    meta: authenticatedQueryMeta,
  });
}

async function fetchDashboardData(user: User, signal?: AbortSignal): Promise<DashboardData> {
  if (user.role === "admin") {
    return fetchAdminDashboard(signal);
  }
  if (user.role === "moderator") {
    return fetchModeratorDashboard(signal);
  }
  return fetchContributorDashboard(signal);
}

async function fetchContributorDashboard(signal?: AbortSignal): Promise<DashboardData> {
  try {
    const response = await listSubmissions(signal);
    return {
      institutions: [],
      stats: {
        ...emptyDashboardStats,
        submissions: response.data,
      },
    };
  } catch (error) {
    if (isCanceledError(error, signal)) throw error;
    return { institutions: [], stats: emptyDashboardStats };
  }
}

async function fetchModeratorDashboard(signal?: AbortSignal): Promise<DashboardData> {
  try {
    const [queueRes, historyRes] = await Promise.all([
      getValidationQueue({ signal }),
      getValidationQueue({ history: true, signal }),
    ]);
    const history = historyRes.data;
    const allReviewItems = [...queueRes.data, ...history];
    const recent = newestSubmissions(allReviewItems, 5);
    const visibleContributorCount = new Set(
      allReviewItems
        .map((s) => s.contributorEmail?.trim().toLowerCase())
        .filter((email): email is string => Boolean(email)),
    ).size;

    return {
      institutions: [],
      stats: {
        ...emptyDashboardStats,
        reviewRecent: recent,
        reviewQueuePending: queueRes.data.length,
        reviewedApprovedThisMonth: history.filter(
          (s) =>
            inCurrentMonth(s) &&
            (s.status === "scheduled" ||
              s.status === "published" ||
              s.status === "published_manual" ||
              s.status === "admin_direct_post"),
        ).length,
        reviewedRejectedThisMonth: history.filter((s) => inCurrentMonth(s) && s.status === "rejected").length,
        contributors: visibleContributorCount,
      },
    };
  } catch (error) {
    if (isCanceledError(error, signal)) throw error;
    return { institutions: [], stats: emptyDashboardStats };
  }
}

async function fetchAdminDashboard(signal?: AbortSignal): Promise<DashboardData> {
  try {
    const [institutionsRes, queueRes, historyRes, usersRes, netInvites, adminInvites, summary] =
      await Promise.all([
        listInstitutions(signal),
        getValidationQueue({ signal }),
        getValidationQueue({ history: true, signal }),
        listNetworkUsers(signal),
        listPendingNetworkInvitations(signal),
        listPendingAdminInvitations(signal),
        getAnalyticsSummary("30d", null, signal).then(
          (response) => response,
          () => null,
        ),
      ]);

    const users = usersRes.data;
    const activeOf = (role: string) =>
      users.filter((u) => u.role.toLowerCase() === role && u.accountState.toLowerCase() === "active").length;
    const contributors = activeOf("contributor");
    const moderators = activeOf("moderator");
    const admins = activeOf("admin");
    const breakdown = summary?.data.statusBreakdown ?? [];
    const scheduledNetwork =
      breakdown.find((s) => s.status.toLowerCase() === "scheduled")?.count ??
      historyRes.data.filter((s) => s.status === "scheduled").length;
    const op = summary?.data.operationalHealth ?? null;

    return {
      institutions: institutionsRes.data.map((item) => ({
        id: item.id,
        name: item.name,
      })),
      stats: {
        ...emptyDashboardStats,
        contributors,
        moderators,
        activeMembers: contributors + moderators + admins,
        pendingInvitations: netInvites.data.length + adminInvites.data.length,
        reviewQueuePending: queueRes.data.length,
        scheduledNetwork,
        publishedLast30d: summary?.data.totalPostsPublished.value ?? 0,
        publishingSuccessRate: op && op.publicationAttempts > 0 ? Math.round(op.publishingSuccessRate) : null,
        reviewRecent: newestSubmissions([...queueRes.data, ...historyRes.data], 5),
      },
    };
  } catch (error) {
    if (isCanceledError(error, signal)) throw error;
    return { institutions: [], stats: emptyDashboardStats };
  }
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

function isCanceledError(error: unknown, signal?: AbortSignal) {
  if (signal?.aborted) return true;
  if (typeof error !== "object" || error === null) return false;
  const maybeCanceled = error as { code?: string; name?: string };
  return maybeCanceled.code === "ERR_CANCELED" || maybeCanceled.name === "CanceledError" || maybeCanceled.name === "AbortError";
}

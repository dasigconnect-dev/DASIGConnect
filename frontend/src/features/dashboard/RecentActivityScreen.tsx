import { useEffect, useMemo, useRef, useState } from "react";
import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import "../../styles/institution-management.css";
import type { User } from "../../types/auth.types";
import { listInstitutions } from "../../api/authApi";
import {
  listSubmissionPage,
  type SubmissionQueueBucket,
  type SubmissionSummary,
} from "../../api/submissionApi";
import { useDebouncedValue } from "../../hooks/useDebouncedValue";
import { authenticatedQueryMeta } from "../../lib/queryClient";
import { queryKeys } from "../../lib/queryKeys";

interface RecentActivityScreenProps {
  user: User;
}

interface ActivityItem {
  id: string;
  title: string;
  subtitle: string;
  institution: string;
  submitted: string;
  status: {
    label: string;
    icon: string;
    className: string;
  };
}

const RECENT_ACTIVITY_STALE_TIME_MS = 60_000;
const EMPTY_SUBMISSIONS: SubmissionSummary[] = [];
const EMPTY_INSTITUTIONS: { id: string; name: string; code: string; emailDomain: string }[] = [];

export default function RecentActivityScreen({ user }: RecentActivityScreenProps) {
  const navigate = useNavigate();
  const [searchQuery, setSearchQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("all");
  const loadMoreRef = useRef<HTMLDivElement | null>(null);
  const debouncedSearch = useDebouncedValue(searchQuery.trim(), 350);
  const userScope = user.id ?? user.email.trim().toLowerCase();
  const bucket = bucketForStatusFilter(statusFilter);
  const submissionsQuery = useInfiniteQuery({
    queryKey: queryKeys.submissions.page({
      role: user.role,
      userId: userScope,
      institutionId: user.institutionId ?? null,
      bucket,
      search: debouncedSearch,
      pageSize: 50,
    }),
    queryFn: ({ pageParam, signal }) => listSubmissionPage(
      { page: pageParam, pageSize: 50, bucket, search: debouncedSearch },
      signal,
    ).then((response) => response.data),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => lastPage.hasNext ? lastPage.page + 1 : undefined,
    staleTime: RECENT_ACTIVITY_STALE_TIME_MS,
    meta: authenticatedQueryMeta,
  });
  const institutionsQuery = useQuery({
    queryKey: queryKeys.dashboard.resource({
      role: user.role,
      userId: userScope,
      institutionId: user.institutionId ?? null,
      resource: "recent-activity-institutions",
    }),
    queryFn: ({ signal }) => listInstitutions(signal).then((response) =>
      response.data.map((item) => ({
        id: item.id,
        name: item.name,
        code: item.institutionCode,
        emailDomain: item.emailDomain,
      }))),
    enabled: user.role === "admin",
    staleTime: RECENT_ACTIVITY_STALE_TIME_MS,
    meta: authenticatedQueryMeta,
  });

  const submissions = submissionsQuery.data?.pages.flatMap((page) => page.items) ?? EMPTY_SUBMISSIONS;
  const institutions = institutionsQuery.data ?? EMPTY_INSTITUTIONS;
  const loading = submissionsQuery.isLoading;
  const loadError = submissionsQuery.isError && !submissionsQuery.data;

  const allActivities: ActivityItem[] = useMemo(() => {
    return [...submissions]
      .sort((a, b) => {
        const dateA = a.submittedAt ?? a.createdAt ?? "";
        const dateB = b.submittedAt ?? b.createdAt ?? "";
        return dateB.localeCompare(dateA);
      })
      .map((s) => {
        const institutionName =
          s.institutionName ||
          institutions.find((i) => i.id === s.institutionId)?.name ||
          getInstitutionName(user);

        const submitted = s.submittedAt ?? s.createdAt ?? "";
        const submittedLabel = submitted
          ? new Date(submitted).toLocaleDateString(undefined, {
              month: "short",
              day: "numeric",
              year: "numeric",
            })
          : "—";

        return {
          id: s.id,
          title: s.eventTitle,
          subtitle: s.category ?? "",
          institution: institutionName,
          submitted: submittedLabel,
          status: statusDisplay(s.status),
        };
      });
  }, [submissions, institutions, user]);

  const statusCounts = useMemo(() => {
    const counts = submissionsQuery.data?.pages[0]?.counts;
    return {
      all: counts?.all ?? 0,
      published: counts?.published ?? 0,
      scheduled: counts?.scheduled ?? 0,
      review: counts?.["under-review"] ?? 0,
      revision: counts?.["action-needed"] ?? 0,
      draft: counts?.drafts ?? 0,
      failed: (counts?.failed ?? 0) + (counts?.rejected ?? 0),
    };
  }, [submissionsQuery.data]);

  const statusTabs = useMemo(() => {
    const tabs = [
      { id: "all", label: "All", count: statusCounts.all },
      { id: "published", label: "Published", count: statusCounts.published },
      { id: "scheduled", label: "Scheduled", count: statusCounts.scheduled },
      { id: "review", label: "Under Review", count: statusCounts.review },
      { id: "revision", label: "Needs Revision", count: statusCounts.revision },
      { id: "draft", label: "Draft", count: statusCounts.draft },
      { id: "failed", label: "Failed", count: statusCounts.failed },
    ];
    return tabs.filter(
      (tab) =>
        tab.id === "all" ||
        tab.count > 0 ||
        tab.id === "published" ||
        tab.id === "scheduled" ||
        tab.id === "review",
    );
  }, [statusCounts]);

  const filteredActivities = allActivities;
  const { fetchNextPage, hasNextPage, isFetchingNextPage } = submissionsQuery;

  useEffect(() => {
    const target = loadMoreRef.current;
    if (!target || !hasNextPage) return;
    const observer = new IntersectionObserver((entries) => {
      if (entries[0]?.isIntersecting && !isFetchingNextPage) {
        void fetchNextPage();
      }
    });
    observer.observe(target);
    return () => observer.disconnect();
  }, [fetchNextPage, hasNextPage, isFetchingNextPage]);

  return (
    <div id="screen-recent-activity" style={{ background: "var(--d-bg)" }}>
      <div className="dash-body">
        <button
          type="button"
          className="dash-back-btn"
          onClick={() => navigate("/dashboard")}
        >
          <i className="ti ti-arrow-left"></i> Back to Dashboard
        </button>

        <div className="dash-view-header">
          <h1 className="dash-view-title">Recent Activity</h1>
          <p className="dash-view-desc">
            Complete overview of submissions and event activities across your workspace.
          </p>
        </div>

        <div className="card-wrap" style={{ marginBottom: "16px" }}>
          <div className="dash-card-toolbar">
            <div className="im-status-tabs" role="group" aria-label="Filter activities by status">
              {statusTabs.map((tab) => (
                <button
                  key={tab.id}
                  type="button"
                  className={`im-status-tab${statusFilter === tab.id ? " is-active" : ""}`}
                  onClick={() => setStatusFilter(tab.id)}
                  aria-pressed={statusFilter === tab.id}
                >
                  {tab.label}
                  <span className="im-status-tab-count">{tab.count}</span>
                </button>
              ))}
            </div>

            <div className="im-search-wrap">
              <i className="ti ti-search im-search-icon" aria-hidden="true"></i>
              <input
                className="im-search-input"
                type="search"
                placeholder="Search activities..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                aria-label="Search activities"
              />
            </div>
          </div>
        </div>

        <div className="card-wrap">
          <table className="data-table" id="activity-full-table">
            <thead>
              <tr>
                <th>Event / Post</th>
                <th>Institution</th>
                <th>Submitted</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody
              id="activity-full-body"
              key={`${statusFilter}-${searchQuery}`}
              className="act-table-animate"
            >
              {loading ? (
                Array.from({ length: 6 }).map((_, index) => (
                  <tr key={`skel-act-${index}`} className="dash-skeleton-row">
                    <td>
                      <div className="dash-skeleton dash-skeleton-line is-title"></div>
                      <br />
                      <div className="dash-skeleton dash-skeleton-line is-sub"></div>
                    </td>
                    <td>
                      <div className="dash-skeleton dash-skeleton-line is-inst"></div>
                    </td>
                    <td>
                      <div className="dash-skeleton dash-skeleton-line is-date"></div>
                    </td>
                    <td>
                      <div className="dash-skeleton dash-skeleton-line is-pill"></div>
                    </td>
                  </tr>
                ))
              ) : loadError ? (
                <tr>
                  <td colSpan={4} style={{ textAlign: "center", padding: "44px 20px" }}>
                    <div style={{ color: "var(--d-muted)", fontSize: 13, marginBottom: 10 }}>
                      Unable to load recent activities.
                    </div>
                    <button type="button" className="btn-ghost" onClick={() => void submissionsQuery.refetch()}>
                      Retry
                    </button>
                  </td>
                </tr>
              ) : filteredActivities.length === 0 ? (
                <tr>
                  <td
                    colSpan={4}
                    style={{
                      textAlign: "center",
                      padding: "44px 20px",
                      color: "var(--d-muted)",
                      fontSize: 13,
                    }}
                  >
                    <div
                      style={{
                        width: 46,
                        height: 46,
                        borderRadius: "50%",
                        background: "#eff6ff",
                        border: "1px solid #dbeafe",
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        margin: "0 auto 12px",
                      }}
                    >
                      <i
                        className="ti ti-photo-off"
                        style={{
                          fontSize: 24,
                          color: "#3b82f6",
                        }}
                      ></i>
                    </div>
                    <div
                      style={{
                        fontWeight: 600,
                        color: "#1e293b",
                        marginBottom: 4,
                      }}
                    >
                      {searchQuery || statusFilter !== "all"
                        ? "No matching activities found"
                        : "No recent activities yet"}
                    </div>
                    <div style={{ color: "#64748b", fontSize: 12.5 }}>
                      {searchQuery || statusFilter !== "all"
                        ? "Try adjusting your search query or status filter."
                        : "Submissions will appear here once created."}
                    </div>
                  </td>
                </tr>
              ) : (
                filteredActivities.map((row) => (
                  <tr key={`${row.id}-${row.title}-${row.submitted}`}>
                    <td>
                      <div className="act-title">{row.title}</div>
                      {row.subtitle && (
                        <span className="act-category">{row.subtitle}</span>
                      )}
                    </td>
                    <td className="act-institution">{row.institution}</td>
                    <td className="act-date">{row.submitted}</td>
                    <td>
                      <span className={`status-pill ${row.status.className}`}>
                        <i
                          className={row.status.icon}
                          style={{ fontSize: 13.5 }}
                        ></i>{" "}
                        {row.status.label}
                      </span>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
        <div ref={loadMoreRef} aria-hidden="true" style={{ height: 1 }} />
      </div>
    </div>
  );
}

function bucketForStatusFilter(statusFilter: string): SubmissionQueueBucket {
  switch (statusFilter) {
    case "published": return "published";
    case "scheduled": return "scheduled";
    case "review": return "under-review";
    case "revision": return "action-needed";
    case "draft": return "drafts";
    case "failed": return "failed-or-rejected";
    default: return "all";
  }
}

function statusDisplay(status: SubmissionSummary["status"]): ActivityItem["status"] {
  switch (status) {
    case "draft":
      return { label: "Draft", icon: "ti ti-pencil", className: "pill-draft" };
    case "pending":
      return { label: "Pending Review", icon: "ti ti-clock", className: "pill-pending" };
    case "in_review":
      return { label: "In Review", icon: "ti ti-eye", className: "pill-review" };
    case "needs_revision":
      return { label: "Needs Revision", icon: "ti ti-pencil-minus", className: "pill-revision" };
    case "scheduled":
      return { label: "Scheduled", icon: "ti ti-calendar-event", className: "pill-scheduled" };
    case "publish_failed":
      return { label: "Publish Failed", icon: "ti ti-alert-circle", className: "pill-failed" };
    case "published":
    case "published_manual":
    case "admin_direct_post":
      return { label: "Published", icon: "ti ti-circle-check", className: "pill-published" };
    case "rejected":
      return { label: "Rejected", icon: "ti ti-x", className: "pill-rejected" };
    default:
      return { label: status, icon: "ti ti-circle", className: "" };
  }
}

function getInstitutionName(user: User | null): string {
  if (!user) return "Institution";
  if (user.role === "admin" || user.role === "moderator") return "DASIG Network";
  const explicit = user.inst?.trim();
  if (explicit && explicit !== user.institutionId) return explicit;
  const emailDomain =
    user.email.split("@")[1]?.split(".")[0]?.toLowerCase() || "";
  return emailDomain.toUpperCase() || "Institution";
}

import { useState, useEffect, type CSSProperties } from "react";
import { useNavigate } from "react-router-dom";
import type { User } from "../../types/auth.types";
import {
  listInstitutions,
  listNetworkUsers,
  listPendingAdminInvitations,
  listPendingNetworkInvitations,
} from "../../api/authApi";
import {
  listSubmissions,
  type SubmissionSummary,
} from "../../api/submissionApi";
import { getValidationQueue } from "../../api/validationApi";
import { getAnalyticsSummary } from "../../api/analyticsApi";
import { getGreetingName } from "../../lib/userIdentity";

interface DashboardScreenProps {
  user: User;
}

interface StatItem {
  icon: string;
  color: string;
  label: string;
  value: string;
  highlight?: boolean;
  valueStyle?: CSSProperties;
}

interface ActionItem {
  icon: string;
  accent: string;
  title: string;
  subtitle: string;
  emphasized?: boolean;
}

interface ActivityItem {
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

interface DashboardStats {
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


export default function DashboardScreen({ user }: DashboardScreenProps) {
  const navigate = useNavigate();
  const [institutions, setInstitutions] = useState<
    { id: string; name: string }[]
  >([]);
  const [loadingSubmissions, setLoadingSubmissions] = useState(
    user?.role === "contributor",
  );
  const [loadingReview, setLoadingReview] = useState(
    user?.role === "moderator" || user?.role === "admin",
  );
  const [dashboardStats, setDashboardStats] = useState<DashboardStats>({
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
  });

  useEffect(() => {
    // Moderators are network-wide (no owning institution); only admins load the
    // institution list for the network-wide roll-ups below.
    if (user?.role !== "admin") return;
    listInstitutions()
      .then((response) => {
        const mapped = response.data.map((item) => ({
          id: item.id,
          name: item.name,
        }));
        setInstitutions(mapped);
      })
      .catch(() => {
        setInstitutions([]);
      });
  }, [user?.role, user?.institutionId, user?.inst]);

  // GET /submissions returns the caller's OWN submissions only, so it only
  // feeds the contributor view. Moderators/admins get network data below.
  useEffect(() => {
    if (user?.role !== "contributor") return;
    listSubmissions()
      .then((response) => {
        setDashboardStats((current) => ({
          ...current,
          submissions: response.data,
        }));
      })
      .catch(() => {
        setDashboardStats((current) => ({ ...current, submissions: [] }));
      })
      .finally(() => {
        setLoadingSubmissions(false);
      });
  }, [user?.role]);

  // Moderator home: live review-queue + this-month review history. The
  // admin-only /users/network directory is intentionally not called here.
  useEffect(() => {
    if (user?.role !== "moderator") return;
    let active = true;

    const monthKey = new Date().toISOString().slice(0, 7); // YYYY-MM
    // The summary DTO carries no "decided at" timestamp; approximate with the
    // most decision-relevant date available (published/scheduled, else submitted).
    const inThisMonth = (s: SubmissionSummary) =>
      (s.publishedAt ?? s.scheduledAt ?? s.submittedAt ?? s.createdAt ?? "").slice(0, 7) ===
      monthKey;

    Promise.all([
      getValidationQueue(),
      getValidationQueue({ history: true }),
    ])
      .then(([queueRes, historyRes]) => {
        if (!active) return;
        const history = historyRes.data;
        const recent = [...queueRes.data, ...history]
          .sort((a, b) =>
            (b.submittedAt ?? b.createdAt ?? "").localeCompare(
              a.submittedAt ?? a.createdAt ?? "",
            ),
          )
          .slice(0, 5);
        const visibleContributorCount = new Set(
          [...queueRes.data, ...history]
            .map((s) => s.contributorEmail?.trim().toLowerCase())
            .filter((email): email is string => Boolean(email)),
        ).size;
        setDashboardStats((current) => ({
          ...current,
          reviewRecent: recent,
          reviewQueuePending: queueRes.data.length,
          reviewedApprovedThisMonth: history.filter(
            (s) =>
              inThisMonth(s) &&
              (s.status === "scheduled" ||
                s.status === "published" ||
                s.status === "published_manual" ||
                s.status === "admin_direct_post"),
          ).length,
          reviewedRejectedThisMonth: history.filter(
            (s) => inThisMonth(s) && s.status === "rejected",
          ).length,
          contributors: visibleContributorCount,
        }));
      })
      .catch(() => {
        if (active) {
          setDashboardStats((current) => ({
            ...current,
            reviewQueuePending: 0,
            reviewedApprovedThisMonth: 0,
            reviewedRejectedThisMonth: 0,
            contributors: 0,
          }));
        }
      })
      .finally(() => {
        if (active) setLoadingReview(false);
      });

    return () => {
      active = false;
    };
  }, [user?.role]);

  // Admin home: everything network-wide. GET /submissions is own-only, so the
  // pipeline figures come from the analytics summary and the (network-wide)
  // validation queue, and the member counts from one /users/network call
  // instead of a per-institution fan-out.
  useEffect(() => {
    if (user?.role !== "admin") return;
    let active = true;

    Promise.all([
      getValidationQueue(),
      getValidationQueue({ history: true }),
      listNetworkUsers(),
      listPendingNetworkInvitations(),
      listPendingAdminInvitations(),
      getAnalyticsSummary("30d").then(
        (r) => r,
        () => null,
      ),
    ])
      .then(
        ([queueRes, historyRes, usersRes, netInvites, adminInvites, summary]) => {
          if (!active) return;
          const users = usersRes.data;
          const activeOf = (role: string) =>
            users.filter(
              (u) =>
                u.role.toLowerCase() === role &&
                u.accountState.toLowerCase() === "active",
            ).length;
          const contributors = activeOf("contributor");
          const moderators = activeOf("moderator");
          const admins = activeOf("admin");

          const recent = [...queueRes.data, ...historyRes.data]
            .sort((a, b) =>
              (b.submittedAt ?? b.createdAt ?? "").localeCompare(
                a.submittedAt ?? a.createdAt ?? "",
              ),
            )
            .slice(0, 5);

          const breakdown = summary?.data.statusBreakdown ?? [];
          const scheduledNetwork =
            breakdown.find((s) => s.status.toLowerCase() === "scheduled")?.count ??
            historyRes.data.filter((s) => s.status === "scheduled").length;

          const op = summary?.data.operationalHealth ?? null;

          setDashboardStats((current) => ({
            ...current,
            contributors,
            moderators,
            activeMembers: contributors + moderators + admins,
            pendingInvitations: netInvites.data.length + adminInvites.data.length,
            reviewQueuePending: queueRes.data.length,
            scheduledNetwork,
            publishedLast30d: summary?.data.totalPostsPublished.value ?? 0,
            publishingSuccessRate:
              op && op.publicationAttempts > 0
                ? Math.round(op.publishingSuccessRate)
                : null,
            reviewRecent: recent,
          }));
        },
      )
      .catch(() => {
        /* leave stats at their defaults */
      })
      .finally(() => {
        if (active) setLoadingReview(false);
      });

    return () => {
      active = false;
    };
  }, [user?.role]);


  const actionRoutes: Record<string, string> = {
    "Submit Event Content": "/submissions/new",
    "Add Institution": "/admin/institution-management",
    "Institution Management": "/admin/institution-management",
    "Review Queue": "/validation/queue",
    "View Calendar": "/scheduler/calendar",
    "Analytics": "/analytics",
  };

  const handleActionClick = (title: string) => {
    const path = actionRoutes[title];
    if (!path) return;
    if (title === "Add Institution") {
      navigate(path, { state: { openAddInstitution: true } });
      return;
    }
    navigate(path);
  };

  // Only contributors author content, so only they get a personal submission
  // feed. Moderators and admins see the network review stream (queue + history).
  const isNetworkView = user?.role === "moderator" || user?.role === "admin";
  const activitySource = isNetworkView
    ? dashboardStats.reviewRecent
    : dashboardStats.submissions;
  const activityRows = activityForRole(user, activitySource, institutions);
  const activityLoading = isNetworkView ? loadingReview : loadingSubmissions;

  return (
    <div id="screen-dashboard" style={{ background: "var(--d-bg)" }}>
      <div className="dash-body">
        <div className="dash-page-header">
          <div className="dash-greeting" id="dash-greeting">
            {greeting(user)}
          </div>
          <div className="dash-subline" id="dash-subline">
            {subline(user)}
          </div>
        </div>

        <div className="first-login-notice" id="first-login-notice">
          <i className={notice(user, dashboardStats).icon}></i>
          <div dangerouslySetInnerHTML={{ __html: notice(user, dashboardStats).html }}></div>
        </div>



        <div className="stat-grid" id="stat-grid">
          {statsForRole(user, dashboardStats, institutions.length).map(
            (stat) => (
              <div className="stat-card" key={stat.label}>
                <div className="stat-icon" style={{ color: stat.color }}>
                  <i className={stat.icon}></i>
                </div>
                <div className="stat-label">{stat.label}</div>
                <div
                  className={`stat-value${stat.highlight ? " highlight" : ""}`}
                  style={stat.valueStyle}
                >
                  {stat.value}
                </div>
              </div>
            ),
          )}
        </div>

        <div className="section-title">
          <i className="ti ti-bolt"></i> Quick Actions
        </div>
        <div className="action-grid" id="action-grid">
          {actionsForRole(user).map((action) => (
            <button
              key={action.title}
              type="button"
              className="action-card action-card-clickable"
              style={action.emphasized ? { border: "1.5px solid #BFDBFE" } : undefined}
              onClick={() => handleActionClick(action.title)}
            >
              <div className={`action-card-icon ${action.accent}`}>
                <i className={action.icon}></i>
              </div>
              <div className="action-card-text">
                <div className="action-title">{action.title}</div>
                <div className="action-sub">{action.subtitle}</div>
              </div>
            </button>
          ))}
        </div>

        <div className="section-header-row">
          <div className="section-title" style={{ margin: 0 }}>
            <i className="ti ti-history"></i> Recent Activity
          </div>
          {user?.role === "contributor" && dashboardStats.submissions.length > 0 && (
            <button
              type="button"
              className="section-link-btn"
              onClick={() => navigate("/dashboard/recent-activity")}
            >
              View All <i className="ti ti-arrow-right" style={{ fontSize: 13 }}></i>
            </button>
          )}
          {isNetworkView && activityRows.length > 0 && (
            <button
              type="button"
              className="section-link-btn"
              onClick={() =>
                navigate(user?.role === "admin" ? "/analytics" : "/validation/queue")
              }
            >
              {user?.role === "admin" ? "Open Analytics" : "Open Review Queue"}{" "}
              <i className="ti ti-arrow-right" style={{ fontSize: 13 }}></i>
            </button>
          )}
        </div>
        <div className="card-wrap">
          <table className="data-table" id="activity-table">
            <thead>
              <tr>
                <th>Event / Post</th>
                <th>Institution</th>
                <th>Submitted</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody id="activity-body">
              {activityLoading ? (
                Array.from({ length: 5 }).map((_, index) => (
                  <tr key={`skel-row-${index}`} className="dash-skeleton-row">
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
              ) : activityRows.length === 0 ? (
                <tr>
                  <td
                    colSpan={4}
                    style={{
                      textAlign: "center",
                      padding: "36px 20px",
                      color: "var(--d-muted)",
                      fontSize: 13,
                    }}
                  >
                    <div
                      style={{
                        width: 44,
                        height: 44,
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
                          fontSize: 22,
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
                      {isNetworkView ? "No recent activity" : "No submissions yet"}
                    </div>
                    <div style={{ color: "#64748b", fontSize: 12.5 }}>
                      {isNetworkView
                        ? "Submissions from contributors across the network will appear here."
                        : "Start by submitting your first event content."}
                    </div>
                  </td>
                </tr>
              ) : (
                activityRows.map((row) => (
                  <tr key={`${row.title}-${row.submitted}`}>
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

      </div>
    </div>
  );
}

function getInstitutionName(user: User | null): string {
  if (!user) return "Institution";
  // Admins and moderators are network-wide — not bound to one HEI workspace.
  if (user.role === "admin" || user.role === "moderator") return "DASIG Network";

  // Institution name comes from GET /api/v1/me (User.inst). No email-domain
  // guessing fallback — an unpopulated inst just shows the generic label.
  const explicitInstitution = user.inst?.trim();
  if (explicitInstitution && explicitInstitution !== user.institutionId) {
    return explicitInstitution;
  }
  return "Institution";
}


function greeting(user: User | null) {
  const hour = new Date().getHours();
  const label =
    hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";
  const name = getGreetingName(user);
  return `${label}, ${name}.`;
}

function subline(user: User | null) {
  if (!user) return "";
  const instName = getInstitutionName(user);
  return `${capitalize(user.role)} · ${instName}`;
}

function notice(user: User | null, stats: DashboardStats) {
  if (!user) {
    return {
      icon: "ti ti-confetti",
      html: "<strong>Welcome to DASIGConnect!</strong> Your account is now active. Explore your dashboard and start submitting content for your institution's events.",
    };
  }
  if (user.role === "admin") {
    const waiting = stats.reviewQueuePending;
    const rate = stats.publishingSuccessRate;
    const parts: string[] = [];
    if (waiting > 0) {
      parts.push(
        `<strong>${waiting} submission${waiting === 1 ? "" : "s"}</strong> ${waiting === 1 ? "is" : "are"} waiting in the review queue`,
      );
    }
    if (rate !== null && rate < 100) {
      parts.push(`publishing success is at <strong>${rate}%</strong> over the last 30 days`);
    }
    const tail = parts.length
      ? ` Right now, ${parts.join(" and ")}.`
      : " Everything across the network is on track.";
    return {
      icon: "ti ti-shield-check",
      html: `<strong>Administrator workspace.</strong> Full network-wide visibility across all member institutions.${tail}`,
    };
  }
  if (user.role === "moderator") {
    const pending = stats.reviewQueuePending;
    const pendingText = pending > 0
      ? `You have <strong>${pending} submission${pending === 1 ? "" : "s"} awaiting review</strong> from contributors across the network.`
      : `The review queue is clear — no submissions are waiting for review.`;
    return {
      icon: "ti ti-clipboard-check",
      html: `${pendingText} Approved content is scheduled automatically for the DASIG Facebook Page.`,
    };
  }
  const instName = getInstitutionName(user);
  const needsRevision = stats.submissions.filter(
    (s) => s.status === "needs_revision",
  ).length;
  if (needsRevision > 0) {
    return {
      icon: "ti ti-pencil-minus",
      html: `<strong>${needsRevision} submission${needsRevision === 1 ? "" : "s"}</strong> ${needsRevision === 1 ? "was" : "were"} sent back for revision by your Moderator. Update ${needsRevision === 1 ? "it" : "them"} and resubmit for review.`,
    };
  }
  const underReview = stats.submissions.filter(
    (s) => s.status === "pending" || s.status === "in_review",
  ).length;
  if (underReview > 0) {
    return {
      icon: "ti ti-clock",
      html: `You have <strong>${underReview} submission${underReview === 1 ? "" : "s"}</strong> awaiting review from your Moderator. You'll be notified once ${underReview === 1 ? "it's reviewed" : "they're reviewed"}.`,
    };
  }
  return {
    icon: "ti ti-confetti",
    html: `<strong>Welcome to DASIGConnect!</strong> Your account is active and bound to ${instName}'s workspace. Submit photos and videos from your institution's events — your Moderator will review them before they go to the DASIG Facebook page.`,
  };
}

function statsForRole(
  user: User | null,
  stats: DashboardStats,
  institutionCount: number,
): StatItem[] {
  if (!user) return [];
  const accessibleBlue = "var(--d-blue, #0B5FCC)";
  const submissions = stats.submissions;
  const publishedCount = submissions.filter(
    (item) =>
      item.status === "published" ||
      item.status === "published_manual" ||
      item.status === "admin_direct_post",
  ).length;
  const scheduledCount = submissions.filter(
    (item) => item.status === "scheduled",
  ).length;
  const reviewCount = submissions.filter(
    (item) => item.status === "pending" || item.status === "in_review",
  ).length;
  const needsRevisionCount = submissions.filter(
    (item) => item.status === "needs_revision",
  ).length;
  if (user.role === "admin") {
    return [
      {
        icon: "ti ti-building",
        color: accessibleBlue,
        label: "Member Institutions",
        value: String(institutionCount),
      },
      {
        icon: "ti ti-users",
        color: accessibleBlue,
        label: "Active Members",
        value: String(stats.activeMembers),
      },
      {
        icon: "ti ti-clock-pause",
        color: accessibleBlue,
        label: "Pending Invites",
        value: String(stats.pendingInvitations),
      },
      {
        icon: "ti ti-file-time",
        color: accessibleBlue,
        label: "Awaiting Review",
        value: String(stats.reviewQueuePending),
        highlight: stats.reviewQueuePending > 0,
      },
      {
        icon: "ti ti-calendar-event",
        color: accessibleBlue,
        label: "Scheduled Posts",
        value: String(stats.scheduledNetwork),
      },
      {
        icon: "ti ti-photo-check",
        color: accessibleBlue,
        label: "Published (30 days)",
        value: String(stats.publishedLast30d),
      },
    ];
  }
  if (user.role === "moderator") {
    return [
      {
        icon: "ti ti-file-time",
        color: accessibleBlue,
        label: "Pending Review",
        value: String(stats.reviewQueuePending),
        highlight: stats.reviewQueuePending > 0,
      },
      {
        icon: "ti ti-circle-check",
        color: accessibleBlue,
        label: "Approved This Month",
        value: String(stats.reviewedApprovedThisMonth),
      },
      {
        icon: "ti ti-circle-x",
        color: accessibleBlue,
        label: "Rejected This Month",
        value: String(stats.reviewedRejectedThisMonth),
      },
      {
        icon: "ti ti-users",
        color: accessibleBlue,
        label: "Recent Contributors",
        value: String(stats.contributors),
      },
    ];
  }
  return [
    {
      icon: "ti ti-photo-up",
      color: accessibleBlue,
      label: "My Submissions",
      value: String(submissions.length),
    },
    {
      icon: "ti ti-circle-check",
      color: accessibleBlue,
      label: "Approved",
      value: String(scheduledCount + publishedCount),
    },
    {
      icon: "ti ti-clock",
      color: accessibleBlue,
      label: "Under Review",
      value: String(reviewCount),
    },
    {
      icon: "ti ti-pencil-minus",
      color: accessibleBlue,
      label: "Needs Revision",
      value: String(needsRevisionCount),
      highlight: needsRevisionCount > 0,
    },
    {
      icon: "ti ti-brand-facebook",
      color: accessibleBlue,
      label: "Published",
      value: String(publishedCount),
    },
  ];
}

function actionsForRole(user: User | null): ActionItem[] {
  if (!user) return [];
  if (user.role === "admin") {
    return [
      {
        icon: "ti ti-clipboard-check",
        accent: "ac-blue",
        title: "Review Queue",
        subtitle: "Approve or reschedule submissions network-wide",
        emphasized: true,
      },
      {
        icon: "ti ti-building-community",
        accent: "ac-green",
        title: "Add Institution",
        subtitle: "Provision a new HEI workspace",
      },
      {
        icon: "ti ti-chart-bar",
        accent: "ac-purple",
        title: "Analytics",
        subtitle: "Network engagement, publishing, and workflow health",
      },
    ];
  }
  if (user.role === "moderator") {
    return [
      {
        icon: "ti ti-clipboard-check",
        accent: "ac-blue",
        title: "Review Queue",
        subtitle: "Review pending submissions from contributors",
        emphasized: true,
      },
      {
        icon: "ti ti-user-plus",
        accent: "ac-green",
        title: "Institution Management",
        subtitle: "Invite contributors and manage your invitations",
      },
      {
        icon: "ti ti-chart-bar",
        accent: "ac-purple",
        title: "Analytics",
        subtitle: "Facebook engagement and workflow metrics",
      },
      {
        icon: "ti ti-calendar-event",
        accent: "ac-purple",
        title: "View Calendar",
        subtitle: "View scheduled and published events",
      },
    ];
  }
  return [
    {
      icon: "ti ti-photo-up",
      accent: "ac-blue",
      title: "Submit Event Content",
      subtitle: "Upload photos, videos & captions",
      emphasized: true,
    },
  ];
}

function activityForRole(
  user: User | null,
  submissions: SubmissionSummary[],
  institutions: { id: string; name: string }[],
): ActivityItem[] {
  if (!user || submissions.length === 0) return [];

  const sorted = [...submissions]
    .sort((a, b) => {
      const dateA = a.submittedAt ?? a.createdAt ?? "";
      const dateB = b.submittedAt ?? b.createdAt ?? "";
      return dateB.localeCompare(dateA);
    })
    .slice(0, 5);

  return sorted.map((s) => {
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
      title: s.eventTitle,
      subtitle: s.category ?? "",
      institution: institutionName,
      submitted: submittedLabel,
      status: statusDisplay(s.status),
    };
  });
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

function capitalize(value: string) {
  return value.charAt(0).toUpperCase() + value.slice(1);
}


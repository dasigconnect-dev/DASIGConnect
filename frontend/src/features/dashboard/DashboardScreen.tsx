import { useState, useEffect, type CSSProperties } from "react";
import { useNavigate } from "react-router-dom";
import type { User } from "../../types/auth.types";
import {
  getPendingInvitationCount,
  getUserCounts,
  listInstitutions,
  listNetworkUsers,
} from "../../api/authApi";
import {
  listSubmissions,
  type SubmissionSummary,
} from "../../api/submissionApi";
import { getValidationQueue } from "../../api/validationApi";
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
  submissions: SubmissionSummary[];
  contributors: number;
  moderators: number;
  pendingInvitations: number;
  /** Reviewer roles only: live PENDING + IN_REVIEW count from the network queue. */
  reviewQueuePending: number;
  /** Reviewer roles only: this-month approved + rejected from the review history. */
  reviewedApprovedThisMonth: number;
  reviewedRejectedThisMonth: number;
  /** Reviewer roles only: recent network submissions (queue + history) for the activity table. */
  reviewRecent: SubmissionSummary[];
}


export default function DashboardScreen({ user }: DashboardScreenProps) {
  const navigate = useNavigate();
  const [institutions, setInstitutions] = useState<
    { id: string; name: string; code: string; emailDomain: string }[]
  >([]);
  const [loadingSubmissions, setLoadingSubmissions] = useState(true);
  const [loadingReview, setLoadingReview] = useState(user?.role === "moderator");
  const [dashboardStats, setDashboardStats] = useState<DashboardStats>({
    submissions: [],
    contributors: 0,
    moderators: 0,
    pendingInvitations: 0,
    reviewQueuePending: 0,
    reviewedApprovedThisMonth: 0,
    reviewedRejectedThisMonth: 0,
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
          code: item.institutionCode,
          emailDomain: item.emailDomain,
        }));
        setInstitutions(mapped);
      })
      .catch(() => {
        setInstitutions([]);
      });
  }, [user?.role, user?.institutionId, user?.inst]);

  useEffect(() => {
    if (!user) return;
    setLoadingSubmissions(true);
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
  }, [user?.role, user?.inst]);

  // Moderator home: live review-queue + this-month review history + the
  // network-wide contributor count. (Admins get their roll-ups from the
  // institution effect below; the review queue is the moderator's whole job.)
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
      listNetworkUsers().then((r) =>
        r.data.filter(
          (u) =>
            u.role.toLowerCase() === "contributor" &&
            u.accountState.toLowerCase() === "active",
        ).length,
      ),
    ])
      .then(([queueRes, historyRes, activeContributors]) => {
        if (!active) return;
        const history = historyRes.data;
        const recent = [...queueRes.data, ...history]
          .sort((a, b) =>
            (b.submittedAt ?? b.createdAt ?? "").localeCompare(
              a.submittedAt ?? a.createdAt ?? "",
            ),
          )
          .slice(0, 5);
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
          contributors: activeContributors,
        }));
      })
      .catch(() => {
        if (active) {
          setDashboardStats((current) => ({
            ...current,
            reviewQueuePending: 0,
            reviewedApprovedThisMonth: 0,
            reviewedRejectedThisMonth: 0,
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

  useEffect(() => {
    if (!user) return;
    if (user.role !== "admin") {
      return;
    }

    const institutionIds = institutions.map((institution) => institution.id);

    if (institutionIds.length === 0) {
      setDashboardStats((current) => ({
        ...current,
        contributors: 0,
        moderators: 0,
        pendingInvitations: 0,
      }));
      return;
    }

    let active = true;
    // Moderators are network-wide, so per-institution counts always report 0
    // moderators; fetch the real total once instead of summing it per institution.
    const networkModeratorCount = listNetworkUsers().then(
      (response) =>
        response.data.filter(
          (u) =>
            u.role.toLowerCase() === "moderator" &&
            u.accountState.toLowerCase() === "active",
        ).length,
    );
    Promise.all([
      Promise.all(
        institutionIds.map(async (institutionId) => {
          const [countsResponse, pendingResponse] = await Promise.all([
            getUserCounts(institutionId),
            getPendingInvitationCount(institutionId),
          ]);
          return {
            contributors: countsResponse.data.contributors,
            pendingInvitations: pendingResponse.data.pendingInvitations,
          };
        }),
      ),
      networkModeratorCount,
    ])
      .then(([responses, moderators]) => {
        if (!active) return;
        const totals = responses.reduce(
          (sum, item) => ({
            contributors: sum.contributors + item.contributors,
            pendingInvitations:
              sum.pendingInvitations + item.pendingInvitations,
          }),
          { contributors: 0, pendingInvitations: 0 },
        );
        setDashboardStats((current) => ({ ...current, ...totals, moderators }));
      })
      .catch(() => {
        if (active) {
          setDashboardStats((current) => ({
            ...current,
            contributors: 0,
            moderators: 0,
            pendingInvitations: 0,
          }));
        }
      });

    return () => {
      active = false;
    };
  }, [user?.role, user?.institutionId, institutions]);


  const actionRoutes: Record<string, string> = {
    "Submit Event Content": "/submissions/new",
    "Add Institution": "/admin/institution-management",
    "Invite Members": "/admin/institution-management",
    "Institution Overview": "/admin/institution-management",
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

  // Moderators don't author content — their "Recent Activity" is the network
  // review stream (queue + history), not listSubmissions() (own drafts only).
  const isReviewer = user?.role === "moderator";
  const activitySource = isReviewer
    ? dashboardStats.reviewRecent
    : dashboardStats.submissions;
  const activityRows = activityForRole(user, activitySource, institutions);
  const activityLoading = isReviewer ? loadingReview : loadingSubmissions;

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
          {!isReviewer && dashboardStats.submissions.length > 0 && (
            <button
              type="button"
              className="section-link-btn"
              onClick={() => navigate("/dashboard/recent-activity")}
            >
              View All <i className="ti ti-arrow-right" style={{ fontSize: 13 }}></i>
            </button>
          )}
          {isReviewer && activityRows.length > 0 && (
            <button
              type="button"
              className="section-link-btn"
              onClick={() => navigate("/validation/queue")}
            >
              Open Review Queue <i className="ti ti-arrow-right" style={{ fontSize: 13 }}></i>
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
                      {isReviewer ? "Nothing to review yet" : "No submissions yet"}
                    </div>
                    <div style={{ color: "#64748b", fontSize: 12.5 }}>
                      {isReviewer
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

const DOMAIN_MAP: Record<string, string> = {
  citu: "CIT-U",
  su: "Silliman University",
  silliman: "Silliman University",
  usc: "University of San Carlos",
  vsu: "Visayas State University",
  uc: "University of Cebu",
  dasigconnect: "DASIG Connect",
};

function getInstitutionName(user: User | null): string {
  if (!user) return "Institution";
  // Admins and moderators are network-wide — not bound to one HEI workspace.
  if (user.role === "admin" || user.role === "moderator") return "DASIG Network";

  const explicitInstitution = user.inst?.trim();
  if (explicitInstitution && explicitInstitution !== user.institutionId) {
    return explicitInstitution;
  }

  const emailDomain =
    user.email.split("@")[1]?.split(".")[0]?.toLowerCase() || "";
  return DOMAIN_MAP[emailDomain] || emailDomain.toUpperCase() || "Institution";
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
    return {
      icon: "ti ti-shield-check",
      html: "<strong>Administrator workspace.</strong> You have full network-wide visibility across all member institutions.",
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
  if (user.role === "admin") {
    return [
      {
        icon: "ti ti-building",
        color: "#1877F2",
        label: "Member Institutions",
        value: String(institutionCount),
      },
      {
        icon: "ti ti-users",
        color: "#1877F2",
        label: "Total Users",
        value: String(stats.contributors + stats.moderators),
      },
      {
        icon: "ti ti-clock-pause",
        color: "#1877F2",
        label: "Pending Invites",
        value: String(stats.pendingInvitations),
      },
      {
        icon: "ti ti-calendar-event",
        color: "#1877F2",
        label: "Scheduled Posts",
        value: String(scheduledCount),
      },
      {
        icon: "ti ti-photo-check",
        color: "#1877F2",
        label: "Published This Month",
        value: String(publishedCount),
      },
    ];
  }
  if (user.role === "moderator") {
    return [
      {
        icon: "ti ti-file-time",
        color: "#1877F2",
        label: "Pending Review",
        value: String(stats.reviewQueuePending),
        highlight: stats.reviewQueuePending > 0,
      },
      {
        icon: "ti ti-circle-check",
        color: "#1877F2",
        label: "Approved This Month",
        value: String(stats.reviewedApprovedThisMonth),
      },
      {
        icon: "ti ti-circle-x",
        color: "#1877F2",
        label: "Rejected This Month",
        value: String(stats.reviewedRejectedThisMonth),
      },
      {
        icon: "ti ti-users",
        color: "#1877F2",
        label: "Active Contributors",
        value: String(stats.contributors),
      },
    ];
  }
  return [
    {
      icon: "ti ti-photo-up",
      color: "#1877F2",
      label: "My Submissions",
      value: String(submissions.length),
    },
    {
      icon: "ti ti-circle-check",
      color: "#1877F2",
      label: "Approved",
      value: String(scheduledCount + publishedCount),
    },
    {
      icon: "ti ti-clock",
      color: "#1877F2",
      label: "Under Review",
      value: String(reviewCount),
    },
    {
      icon: "ti ti-brand-facebook",
      color: "#1877F2",
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
        icon: "ti ti-building-community",
        accent: "ac-green",
        title: "Add Institution",
        subtitle: "Provision a new HEI workspace",
      },
      {
        icon: "ti ti-user-plus",
        accent: "ac-blue",
        title: "Invite Members",
        subtitle: "Send invitations to contributors and moderators",
      },
      {
        icon: "ti ti-layout-grid",
        accent: "ac-purple",
        title: "Institution Overview",
        subtitle: "Browse and manage all registered workspaces",
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


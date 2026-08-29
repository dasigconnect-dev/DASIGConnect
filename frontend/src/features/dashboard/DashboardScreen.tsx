import { useState, useEffect, type CSSProperties } from "react";
import { useNavigate } from "react-router-dom";
import type { User } from "../../types/auth.types";
import {
  getPendingInvitationCount,
  getUserCounts,
  listInstitutions,
} from "../../api/authApi";
import {
  listSubmissions,
  type SubmissionSummary,
} from "../../api/submissionApi";
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
}


export default function DashboardScreen({ user }: DashboardScreenProps) {
  const navigate = useNavigate();
  const [institutions, setInstitutions] = useState<
    { id: string; name: string; code: string; emailDomain: string }[]
  >([]);
  const [loadingSubmissions, setLoadingSubmissions] = useState(true);
  const [dashboardStats, setDashboardStats] = useState<DashboardStats>({
    submissions: [],
    contributors: 0,
    moderators: 0,
    pendingInvitations: 0,
  });

  useEffect(() => {
    if (user?.role === "moderator" && user.institutionId) {
      setInstitutions([
        {
          id: user.institutionId,
          name: getInstitutionName(user),
          code: "",
          emailDomain: "",
        },
      ]);
      return;
    }
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

  useEffect(() => {
    if (!user) return;
    if (user.role === "contributor") {
      setDashboardStats((current) => ({
        ...current,
        contributors: 0,
        moderators: 0,
        pendingInvitations: 0,
      }));
      return;
    }

    const institutionIds =
      user.role === "admin"
        ? institutions.map((institution) => institution.id)
        : user.institutionId
          ? [user.institutionId]
          : [];

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
    Promise.all(
      institutionIds.map(async (institutionId) => {
        const [countsResponse, pendingResponse] = await Promise.all([
          getUserCounts(institutionId),
          getPendingInvitationCount(institutionId),
        ]);
        return {
          contributors: countsResponse.data.contributors,
          moderators: countsResponse.data.moderators,
          pendingInvitations: pendingResponse.data.pendingInvitations,
        };
      }),
    )
      .then((responses) => {
        if (!active) return;
        const totals = responses.reduce(
          (sum, item) => ({
            contributors: sum.contributors + item.contributors,
            moderators: sum.moderators + item.moderators,
            pendingInvitations:
              sum.pendingInvitations + item.pendingInvitations,
          }),
          { contributors: 0, moderators: 0, pendingInvitations: 0 },
        );
        setDashboardStats((current) => ({ ...current, ...totals }));
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
          {dashboardStats.submissions.length > 0 && (
            <button
              type="button"
              className="section-link-btn"
              onClick={() => navigate("/dashboard/recent-activity")}
            >
              View All <i className="ti ti-arrow-right" style={{ fontSize: 13 }}></i>
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
              {loadingSubmissions ? (
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
              ) : activityForRole(user, dashboardStats.submissions, institutions).length === 0 ? (
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
                      No submissions yet
                    </div>
                    <div style={{ color: "#64748b", fontSize: 12.5 }}>
                      Start by submitting your first event content.
                    </div>
                  </td>
                </tr>
              ) : (
                activityForRole(user, dashboardStats.submissions, institutions).map((row) => (
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
  if (user.role === "admin") return "DASIG";

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
      html: "<strong>Moderator workspace.</strong> You have full network-wide visibility across all member institutions.",
    };
  }
  if (user.role === "moderator") {
    const instName = getInstitutionName(user);
    const pending = stats.submissions.filter(
      (s) => s.status === "pending" || s.status === "in_review",
    ).length;
    const pendingText = pending > 0
      ? `You have <strong>${pending} submission${pending === 1 ? "" : "s"} awaiting your review</strong> from ${instName} contributors.`
      : `No submissions are currently pending review from ${instName} contributors.`;
    return {
      icon: "ti ti-clipboard-check",
      html: `${pendingText} Approved content moves to the DASIG Moderator for scheduling.`,
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
        value: String(reviewCount),
      },
      {
        icon: "ti ti-circle-check",
        color: "#1877F2",
        label: "Approved This Month",
        value: String(scheduledCount + publishedCount),
      },
      {
        icon: "ti ti-users",
        color: "#1877F2",
        label: "Contributors",
        value: String(stats.contributors),
      },
      {
        icon: "ti ti-building",
        color: "#1877F2",
        label: "Institution",
        value: getInstitutionName(user),
        valueStyle: { fontSize: 16, paddingTop: 6 },
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
        icon: "ti ti-building",
        accent: "ac-green",
        title: "Institution Management",
        subtitle: "Manage HEI workspace and contributors",
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


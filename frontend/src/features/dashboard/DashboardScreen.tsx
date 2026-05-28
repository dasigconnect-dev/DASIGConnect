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
  validators: number;
  pendingInvitations: number;
}

const emptyContributorActivity: ActivityItem[] = [];

export default function DashboardScreen({ user }: DashboardScreenProps) {
  const navigate = useNavigate();
  const [institutions, setInstitutions] = useState<
    { id: string; name: string; code: string; emailDomain: string }[]
  >([]);
  const [dashboardStats, setDashboardStats] = useState<DashboardStats>({
    submissions: [],
    contributors: 0,
    validators: 0,
    pendingInvitations: 0,
  });

  useEffect(() => {
    if (user?.role === "validator" && user.institutionId) {
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
    listSubmissions()
      .then((response) => {
        setDashboardStats((current) => ({
          ...current,
          submissions: response.data,
        }));
      })
      .catch(() => {
        setDashboardStats((current) => ({ ...current, submissions: [] }));
      });
  }, [user?.role, user?.inst]);

  useEffect(() => {
    if (!user) return;
    if (user.role === "contributor") {
      setDashboardStats((current) => ({
        ...current,
        contributors: 0,
        validators: 0,
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
        validators: 0,
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
          validators: countsResponse.data.validators,
          pendingInvitations: pendingResponse.data.pendingInvitations,
        };
      }),
    )
      .then((responses) => {
        if (!active) return;
        const totals = responses.reduce(
          (sum, item) => ({
            contributors: sum.contributors + item.contributors,
            validators: sum.validators + item.validators,
            pendingInvitations:
              sum.pendingInvitations + item.pendingInvitations,
          }),
          { contributors: 0, validators: 0, pendingInvitations: 0 },
        );
        setDashboardStats((current) => ({ ...current, ...totals }));
      })
      .catch(() => {
        if (active) {
          setDashboardStats((current) => ({
            ...current,
            contributors: 0,
            validators: 0,
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
    "User Management": "/admin/user-management/invitations",
    "Invite Contributor": "/admin/user-management/invitations",
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
          <i className={notice(user).icon}></i>
          <div dangerouslySetInnerHTML={{ __html: notice(user).html }}></div>
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

        <div className="section-title">
          <i className="ti ti-history"></i> Recent Activity
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
              {activityForRole(user).length === 0 ? (
                <tr>
                  <td
                    colSpan={4}
                    style={{
                      textAlign: "center",
                      padding: 28,
                      color: "var(--d-muted)",
                      fontSize: 13,
                    }}
                  >
                    <i
                      className="ti ti-photo-off"
                      style={{
                        fontSize: 28,
                        display: "block",
                        marginBottom: 8,
                        opacity: 0.4,
                      }}
                    ></i>
                    No submissions yet. Start by submitting your first event
                    content.
                  </td>
                </tr>
              ) : (
                activityForRole(user).map((row) => (
                  <tr key={`${row.title}-${row.submitted}`}>
                    <td>
                      <strong>{row.title}</strong>
                      <br />
                      <span style={{ fontSize: 11, color: "var(--d-muted)" }}>
                        {row.subtitle}
                      </span>
                    </td>
                    <td>{row.institution}</td>
                    <td>{row.submitted}</td>
                    <td>
                      <span className={`status-pill ${row.status.className}`}>
                        <i
                          className={row.status.icon}
                          style={{ fontSize: 11 }}
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
  return `${capitalize(user.role)} · ${instName} · First login today`;
}

function notice(user: User | null) {
  if (!user) {
    return {
      icon: "ti ti-confetti",
      html: "<strong>Welcome to DASIGConnect!</strong> Your account is now active and bound to your institution's workspace. This is your first login — explore your dashboard and start submitting content for your institution's events.",
    };
  }
  if (user.role === "admin") {
    return {
      icon: "ti ti-alert-circle",
      html: "<strong>Administrator workspace.</strong> You have full network-wide visibility. <strong>0 failed batch invitations</strong> require attention. <strong>0 submissions</strong> are pending scheduler assignment.",
    };
  }
  if (user.role === "validator") {
    const instName = getInstitutionName(user);
    return {
      icon: "ti ti-clipboard-check",
      html: `Welcome back, <strong>Validator.</strong> You have <strong>0 submissions pending your review</strong> from ${instName} contributors. Approved content moves to the DASIG Administrator for scheduling.`,
    };
  }
  const instName = getInstitutionName(user);
  return {
    icon: "ti ti-confetti",
    html: `<strong>Welcome to DASIGConnect!</strong> Your account is active and bound to ${instName}'s workspace. Submit photos and videos from your institution's events — your Validator will review them before they go to the DASIG Facebook page.`,
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
        color: "#16A34A",
        label: "Total Users",
        value: String(stats.contributors + stats.validators),
      },
      {
        icon: "ti ti-clock-pause",
        color: "#D97706",
        label: "Pending Invites",
        value: String(stats.pendingInvitations),
        highlight: stats.pendingInvitations > 0,
      },
      {
        icon: "ti ti-calendar-event",
        color: "#7C3AED",
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
  if (user.role === "validator") {
    return [
      {
        icon: "ti ti-file-time",
        color: "#D97706",
        label: "Pending Review",
        value: String(reviewCount),
        highlight: true,
      },
      {
        icon: "ti ti-circle-check",
        color: "#16A34A",
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
        color: "#7C3AED",
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
      color: "#16A34A",
      label: "Approved",
      value: String(scheduledCount + publishedCount),
    },
    {
      icon: "ti ti-clock",
      color: "#D97706",
      label: "Under Review",
      value: String(reviewCount),
    },
    {
      icon: "ti ti-brand-facebook",
      color: "#7C3AED",
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
        subtitle: "Send invitations to contributors and validators",
      },
      {
        icon: "ti ti-layout-grid",
        accent: "ac-purple",
        title: "Institution Overview",
        subtitle: "Browse and manage all registered workspaces",
      },
    ];
  }
  if (user.role === "validator") {
    return [
      {
        icon: "ti ti-users",
        accent: "ac-blue",
        title: "User Management",
        subtitle: "View and manage institution members",
      },
      {
        icon: "ti ti-user-plus",
        accent: "ac-green",
        title: "Invite Contributor",
        subtitle: "Send an invitation to a new contributor",
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

function activityForRole(user: User | null): ActivityItem[] {
  if (!user) return emptyContributorActivity;
  return emptyContributorActivity;
}

function capitalize(value: string) {
  return value.charAt(0).toUpperCase() + value.slice(1);
}


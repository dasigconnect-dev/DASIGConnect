import { useState, useEffect, type CSSProperties } from "react";
import { useNavigate } from "react-router-dom";
import type { User } from "../../types/auth.types";
import {
  createInstitution,
  getPendingInvitationCount,
  getUserCounts,
  listInstitutions,
} from "../../api/authApi";
import {
  listSubmissions,
  type SubmissionSummary,
} from "../../api/submissionApi";

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
  const [openModal, setOpenModal] = useState<"institution" | null>(null);
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

  const [instName, setInstName] = useState("");
  const [instDomain, setInstDomain] = useState("");
  const [instLoading, setInstLoading] = useState(false);
  const [instError, setInstError] = useState("");
  const [instSuccess, setInstSuccess] = useState<{
    id: string;
    name: string;
    code: string;
  } | null>(null);

  const handleCloseModals = () => {
    setOpenModal(null);
    setInstName("");
    setInstDomain("");
    setInstError("");
    setInstSuccess(null);
  };

  const handleProvisionInstitution = async (e: React.FormEvent) => {
    e.preventDefault();
    setInstError("");
    setInstSuccess(null);

    const name = instName.trim();
    const domain = normalizeDomain(instDomain);
    const code = generateInstitutionCode(name, domain);

    if (!name || !domain) {
      setInstError("All fields are required.");
      return;
    }

    if (!isValidDomain(domain)) {
      setInstError("Enter a valid email domain (e.g. su.edu.ph).");
      return;
    }

    setInstLoading(true);
    try {
      const response = await createInstitution(name, code, domain);
      const newInst = {
        id: response.data.id,
        name: response.data.name,
        code: response.data.institutionCode,
      };
      const updated = [
        ...institutions,
        { ...newInst, emailDomain: response.data.emailDomain },
      ];
      setInstitutions(updated);
      setInstSuccess(newInst);
      setInstName("");
      setInstDomain("");
    } catch (err: unknown) {
      setInstError(
        getApiErrorMessage(
          err,
          "An error occurred while provisioning the workspace.",
        ),
      );
    } finally {
      setInstLoading(false);
    }
  };

  const handleActionClick = (title: string) => {
    if (title === "Submit Event Content") {
      navigate("/submissions/new");
      return;
    }
    if (title === "Invite Users") {
      navigate("/admin/user-management/invitations");
      return;
    }
    if (title === "Manage Contributors") {
      navigate("/admin/user-management/invitations");
      return;
    }
    if (title === "Add Institution") {
      setOpenModal("institution");
    }
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

        <div
          className={`fb-bar${roleChip(user).className === "chip-admin" ? "" : " hidden"}`}
          id="fb-bar"
        >
          <div className="fb-bar-left">
            <i className="ti ti-brand-facebook fb-icon"></i>
            <div className="fb-bar-text">
              <div className="fb-bar-title">DASIG Facebook Page Connected</div>
              <div className="fb-bar-sub">
                Approved content can be scheduled directly to the DASIG Facebook
                page · Last synced 2 min ago
              </div>
            </div>
          </div>
          <button type="button" className="fb-btn">
            <i className="ti ti-settings" style={{ marginRight: 5 }}></i>Manage
            Connection
          </button>
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
          {actionsForRole(user).map((action) => {
            const isClickable =
              (user?.role === "admin" &&
                (action.title === "Invite Users" ||
                  action.title === "Add Institution")) ||
              (user?.role === "validator" &&
                action.title === "Manage Contributors");
            const Element = isClickable ? "button" : "div";
            return (
              <Element
                key={action.title}
                className={`action-card${isClickable ? " action-card-clickable" : ""}`}
                style={
                  action.emphasized
                    ? { border: "1.5px solid #BFDBFE" }
                    : undefined
                }
                onClick={
                  isClickable
                    ? () => handleActionClick(action.title)
                    : undefined
                }
                type={isClickable ? "button" : undefined}
              >
                <div className={`action-card-icon ${action.accent}`}>
                  <i className={action.icon}></i>
                </div>
                <div className="action-card-text">
                  <div className="action-title">{action.title}</div>
                  <div className="action-sub">{action.subtitle}</div>
                </div>
              </Element>
            );
          })}
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

        {openModal === "institution" && (
          <div className="dash-modal-backdrop" onClick={handleCloseModals}>
            <div
              className="dash-modal-card"
              onClick={(event) => event.stopPropagation()}
            >
              <div className="dash-modal-header">
                <div>
                  <div className="dash-modal-title">Add Institution</div>
                  <div className="dash-modal-sub">
                    Provision a new HEI workspace and bind its email domain.
                  </div>
                </div>
                <button
                  type="button"
                  className="dash-modal-close"
                  onClick={handleCloseModals}
                  aria-label="Close"
                >
                  <i className="ti ti-x"></i>
                </button>
              </div>

              <form onSubmit={handleProvisionInstitution}>
                <div className="dash-field">
                  <label className="dash-field-label">Institution Name</label>
                  <input
                    className="dash-input"
                    placeholder="Silliman University"
                    value={instName}
                    onChange={(event) => setInstName(event.target.value)}
                  />
                </div>

                <div className="dash-field">
                  <label className="dash-field-label">
                    Associated Email Domain
                  </label>
                  <input
                    className="dash-input"
                    placeholder="su.edu.ph"
                    value={instDomain}
                    onChange={(event) => setInstDomain(event.target.value)}
                  />
                  <div className="dash-field-hint">
                    Used to auto-route contributors and apply institution
                    branding.
                  </div>
                </div>

                <div className="dash-inline-field">
                  <div>
                    <div className="dash-inline-label">
                      Generated Institution Code
                    </div>
                    <div className="dash-inline-sub">
                      Based on name/domain. You can edit later.
                    </div>
                  </div>
                  <div className="dash-pill">
                    {generateInstitutionCode(
                      instName,
                      normalizeDomain(instDomain),
                    ) || "AUTO"}
                  </div>
                </div>

                {instError && (
                  <div className="alert alert-err">
                    <i className="ti ti-alert-circle"></i>
                    <div>{instError}</div>
                  </div>
                )}

                {instSuccess && (
                  <div className="alert alert-ok">
                    <i className="ti ti-circle-check"></i>
                    <div>
                      {instSuccess.name} was provisioned. Code:{" "}
                      {instSuccess.code}
                    </div>
                  </div>
                )}

                <div className="dash-modal-actions">
                  <button
                    type="button"
                    className="btn-ghost"
                    onClick={handleCloseModals}
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="btn-primary"
                    disabled={instLoading}
                  >
                    {instLoading ? "Provisioning..." : "Add Institution"}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}
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

function roleChip(user: User | null) {
  if (!user) {
    return { className: "chip-contributor", label: "Contributor" };
  }
  if (user.role === "admin") {
    return { className: "chip-admin", label: "Administrator" };
  }
  if (user.role === "validator") {
    return { className: "chip-validator", label: "Validator" };
  }
  return { className: "chip-contributor", label: "Contributor" };
}

function greeting(user: User | null) {
  const hour = new Date().getHours();
  const label =
    hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";
  const name = user?.name?.split(" ")[0] ?? "there";
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
        icon: "ti ti-user-plus",
        accent: "ac-blue",
        title: "Invite Users",
        subtitle: "Batch invite Contributors by institution",
      },
      {
        icon: "ti ti-building-plus",
        accent: "ac-green",
        title: "Add Institution",
        subtitle: "Provision a new HEI workspace",
      },
      {
        icon: "ti ti-calendar-plus",
        accent: "ac-gold",
        title: "Schedule Post",
        subtitle: "Assign approved content to Facebook queue",
      },
      {
        icon: "ti ti-chart-bar",
        accent: "ac-purple",
        title: "View Analytics",
        subtitle: "Facebook reach & engagement overview",
      },
    ];
  }
  if (user.role === "validator") {
    return [
      {
        icon: "ti ti-clipboard-list",
        accent: "ac-gold",
        title: "Review Queue",
        subtitle: "0 submissions awaiting your approval",
      },
      {
        icon: "ti ti-user-check",
        accent: "ac-blue",
        title: "Manage Contributors",
        subtitle: "View and manage institution members",
      },
      {
        icon: "ti ti-history",
        accent: "ac-green",
        title: "Approval History",
        subtitle: "View all past approvals and rejections",
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
    {
      icon: "ti ti-history",
      accent: "ac-green",
      title: "My Submissions",
      subtitle: "Track status of your submissions",
    },
    {
      icon: "ti ti-bulb",
      accent: "ac-gold",
      title: "Content Guidelines",
      subtitle: "What to submit and how to tag it",
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

function normalizeDomain(domain: string) {
  return domain.trim().toLowerCase().replace(/^@/, "");
}

function isValidDomain(domain: string) {
  return /^[a-z0-9.-]+\.[a-z]{2,}$/.test(domain);
}

function getApiErrorMessage(error: unknown, fallback: string) {
  if (!isRecord(error)) return fallback;
  const response = error.response;
  if (isRecord(response)) {
    const data = response.data;
    if (isRecord(data)) {
      if (typeof data.error === "string") return data.error;
      if (typeof data.message === "string") return data.message;
    }
  }
  return typeof error.message === "string" ? error.message : fallback;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function generateInstitutionCode(name: string, domain: string) {
  const domainPrefix = domain.split(".")[0] || "";
  if (domainPrefix.length > 1) {
    return domainPrefix.toUpperCase();
  }
  const parts = name
    .replace(/[^A-Za-z0-9 ]/g, " ")
    .split(" ")
    .filter(Boolean);
  const initials = parts.map((part) => part.charAt(0)).join("");
  return initials.toUpperCase() || "INST";
}

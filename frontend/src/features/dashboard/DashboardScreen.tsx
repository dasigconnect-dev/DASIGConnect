import type { CSSProperties } from "react";
import { useNavigate } from "react-router-dom";
import type { User } from "../../types/auth.types";
import type { SubmissionSummary } from "../../api/submissionApi";
import { getGreetingName } from "../../lib/userIdentity";
import {
  emptyDashboardStats,
  useDashboardData,
  type DashboardStats,
} from "./hooks/useDashboardData";
import SpotlightTour from "../onboarding/components/SpotlightTour";
import { useScreenTour } from "../onboarding/hooks/useScreenTour";
import { dashboardTourSteps } from "../onboarding/tours/dashboardTour";

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

export default function DashboardScreen({ user }: DashboardScreenProps) {
  const navigate = useNavigate();
  const dashboardData = useDashboardData(user);
  const institutions = dashboardData.institutions;
  const dashboardStats = dashboardData.stats ?? emptyDashboardStats;

  const {
    startTour: startDashboardTour,
    tourProps: dashboardTourProps,
  } = useScreenTour({
    screenId: "dashboard",
    steps: dashboardTourSteps,
    autoStartDelayMs: 700,
    canStart: !dashboardData.resources.institutions.loading,
  });


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
    ? dashboardStats.reviewRecent ?? []
    : dashboardStats.submissions ?? [];
  const activityRows = activityForRole(user, activitySource, institutions);
  const activityLoading = dashboardData.activity.loading;
  const activityError = dashboardData.activity.error;

  return (
    <div id="screen-dashboard" style={{ background: "var(--d-bg)" }}>
      <div className="dash-body">
        <div className="dash-page-header" style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: "16px" }}>
          <div>
            <div className="dash-greeting" id="dash-greeting">
              {greeting(user)}
            </div>
            <div className="dash-subline" id="dash-subline">
              {subline(user)}
            </div>
          </div>
          <button
            type="button"
            className="sub-btn-ghost"
            onClick={() => startDashboardTour(true)}
            title="Show interactive dashboard guide"
            aria-label="Show interactive dashboard guide"
            style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
          >
            <i className="ti ti-help-circle" style={{ fontSize: 14 }} />
            <span>Guide</span>
          </button>
        </div>

        <div className="first-login-notice" id="first-login-notice">
          <i className={notice(user, dashboardStats).icon}></i>
          <div dangerouslySetInnerHTML={{ __html: notice(user, dashboardStats).html }}></div>
        </div>



        <div className="stat-grid" id="stat-grid">
          {statsForRole(
            user,
            dashboardStats,
            dashboardData.resources.institutions.loading || dashboardData.resources.institutions.error
              ? null
              : institutions.length,
          ).map(
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
          {(activityRows.length > 0 || (dashboardStats.submissions?.length ?? 0) > 0) && (
            <button
              type="button"
              className="section-link-btn"
              onClick={() => navigate("/dashboard/recent-activity")}
            >
              Visit All Recent Activity{" "}
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
              ) : activityError ? (
                <tr>
                  <td colSpan={4} style={{ textAlign: "center", padding: "36px 20px" }}>
                    <div style={{ color: "var(--d-muted)", fontSize: 13, marginBottom: 10 }}>
                      Unable to load recent activity.
                    </div>
                    <button type="button" className="btn-ghost" onClick={() => void dashboardData.activity.retry()}>
                      Retry
                    </button>
                  </td>
                </tr>
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

        <SpotlightTour {...dashboardTourProps} />
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
    if (waiting !== null && waiting > 0) {
      parts.push(
        `<strong>${waiting} submission${waiting === 1 ? "" : "s"}</strong> ${waiting === 1 ? "is" : "are"} waiting in the review queue`,
      );
    }
    if (rate !== null && rate < 100) {
      parts.push(`publishing success is at <strong>${rate}%</strong> over the last 30 days`);
    }
    const tail = parts.length
      ? ` Right now, ${parts.join(" and ")}.`
      : waiting === null || stats.publishedLast30d === null
        ? " Current dashboard metrics are temporarily unavailable."
      : " Everything across the network is on track.";
    return {
      icon: "ti ti-shield-check",
      html: `<strong>Administrator workspace.</strong> Full network-wide visibility across all member institutions.${tail}`,
    };
  }
  if (user.role === "moderator") {
    const pending = stats.reviewQueuePending;
    const pendingText = pending === null
      ? `Review queue metrics are temporarily unavailable.`
      : pending > 0
      ? `You have <strong>${pending} submission${pending === 1 ? "" : "s"} awaiting review</strong> from contributors across the network.`
      : `The review queue is clear — no submissions are waiting for review.`;
    return {
      icon: "ti ti-clipboard-check",
      html: `${pendingText} Approved content is scheduled automatically for the DASIG Facebook Page.`,
    };
  }
  const instName = getInstitutionName(user);
  if (stats.submissions === null) {
    return {
      icon: "ti ti-photo-up",
      html: `<strong>Contributor workspace.</strong> Submission activity is temporarily unavailable.`,
    };
  }
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
  institutionCount: number | null,
): StatItem[] {
  if (!user) return [];
  const accessibleBlue = "var(--d-blue, #1877f2)";
  const submissions = stats.submissions ?? [];
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
        value: metricValue(institutionCount),
      },
      {
        icon: "ti ti-users",
        color: accessibleBlue,
        label: "Active Members",
        value: metricValue(stats.activeMembers),
      },
      {
        icon: "ti ti-clock-pause",
        color: accessibleBlue,
        label: "Pending Invites",
        value: metricValue(stats.pendingInvitations),
      },
      {
        icon: "ti ti-file-time",
        color: accessibleBlue,
        label: "Awaiting Review",
        value: metricValue(stats.reviewQueuePending),
        highlight: (stats.reviewQueuePending ?? 0) > 0,
      },
      {
        icon: "ti ti-calendar-event",
        color: accessibleBlue,
        label: "Scheduled Posts",
        value: metricValue(stats.scheduledNetwork),
      },
      {
        icon: "ti ti-photo-check",
        color: accessibleBlue,
        label: "Published (30 days)",
        value: metricValue(stats.publishedLast30d),
      },
    ];
  }
  if (user.role === "moderator") {
    return [
      {
        icon: "ti ti-file-time",
        color: accessibleBlue,
        label: "Pending Review",
        value: metricValue(stats.reviewQueuePending),
        highlight: (stats.reviewQueuePending ?? 0) > 0,
      },
      {
        icon: "ti ti-circle-check",
        color: accessibleBlue,
        label: "Approved This Month",
        value: metricValue(stats.reviewedApprovedThisMonth),
      },
      {
        icon: "ti ti-circle-x",
        color: accessibleBlue,
        label: "Rejected This Month",
        value: metricValue(stats.reviewedRejectedThisMonth),
      },
      {
        icon: "ti ti-users",
        color: accessibleBlue,
        label: "Recent Contributors",
        value: metricValue(stats.contributors),
      },
    ];
  }
  return [
    {
      icon: "ti ti-photo-up",
      color: accessibleBlue,
      label: "My Submissions",
      value: stats.submissions === null ? "—" : String(submissions.length),
    },
    {
      icon: "ti ti-circle-check",
      color: accessibleBlue,
      label: "Approved",
      value: stats.submissions === null ? "—" : String(scheduledCount + publishedCount),
    },
    {
      icon: "ti ti-clock",
      color: accessibleBlue,
      label: "Under Review",
      value: stats.submissions === null ? "—" : String(reviewCount),
    },
    {
      icon: "ti ti-pencil-minus",
      color: accessibleBlue,
      label: "Needs Revision",
      value: stats.submissions === null ? "—" : String(needsRevisionCount),
      highlight: needsRevisionCount > 0,
    },
    {
      icon: "ti ti-brand-facebook",
      color: accessibleBlue,
      label: "Published",
      value: stats.submissions === null ? "—" : String(publishedCount),
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

function metricValue(value: number | null) {
  return value !== null ? String(value) : "—";
}


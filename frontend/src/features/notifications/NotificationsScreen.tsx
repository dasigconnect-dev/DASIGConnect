import "../../styles/notifications.css";
import { useNotifications } from "./hooks/useNotifications";
import FilterTabs from "./components/FilterTabs";
import NotificationList from "./components/NotificationList";
import AuditLog from "./components/AuditLog";
import type { User } from "../../types/auth.types";
import type { Notification, NotificationFilter, SseStatus } from "./types";

interface NotificationsScreenProps {
  user: User;
}

const CONTRIBUTOR_FILTERS: NotificationFilter[] = [
  "all",
  "unread",
  "submissions",
  "publishing",
  "deadline",
];

const VALIDATOR_FILTERS: NotificationFilter[] = [
  "all",
  "unread",
  "submissions",
  "deadline",
  "system",
];

const ADMIN_FILTERS: NotificationFilter[] = [
  "all",
  "unread",
  "submissions",
  "publishing",
  "system",
  "overrides",
  "deadline",
];

function isContributorWorkflowNotification(notification: Notification) {
  return ["submissions", "publishing", "deadline", "overrides"].includes(notification.category);
}

function isValidatorWorkflowNotification(notification: Notification) {
  return ["submissions", "deadline", "system"].includes(notification.category);
}

function ContributorActivitySummary({ notifications }: { notifications: Notification[] }) {
  const awaitingReview = notifications.filter((n) =>
    ["submission_pending", "submission_approved", "submission_scheduled"].includes(n.eventType),
  ).length;
  const needsAction = notifications.filter((n) =>
    ["submission_needs_revision", "submission_rejected", "submission_publish_failed", "override_denied"].includes(n.eventType),
  ).length;
  const publishedPosts = notifications.filter((n) =>
    ["submission_published", "submission_published_manual"].includes(n.eventType),
  ).length;
  const upcomingScheduled = notifications.filter((n) =>
    ["submission_approved", "submission_scheduled", "submission_rescheduled", "override_slot_suggested"].includes(n.eventType),
  ).length;

  const items = [
    { label: "Awaiting Review", value: awaitingReview, icon: "ti ti-clock", tone: "info" },
    { label: "Needs Action", value: needsAction, icon: "ti ti-alert-triangle", tone: "warning" },
    { label: "Published Posts", value: publishedPosts, icon: "ti ti-circle-check", tone: "success" },
    { label: "Upcoming Scheduled Posts", value: upcomingScheduled, icon: "ti ti-calendar-event", tone: "blue" },
  ];

  return (
    <aside className="notif-card contributor-summary-card" aria-label="Contributor activity summary">
      <div className="notif-card-header">
        <div>
          <div className="notif-card-title">Activity Summary</div>
          <div className="notif-card-sub">Your content workflow at a glance</div>
        </div>
      </div>
      <div className="contributor-summary-list">
        {items.map((item) => (
          <div className="contributor-summary-row" key={item.label}>
            <div className={`contributor-summary-icon ${item.tone}`}>
              <i className={item.icon} aria-hidden="true" />
            </div>
            <div>
              <div className="contributor-summary-value">{item.value}</div>
              <div className="contributor-summary-label">{item.label}</div>
            </div>
          </div>
        ))}
      </div>
      <div className="contributor-summary-note">
        Important updates will link you back to the related submission or feedback.
      </div>
    </aside>
  );
}

function ValidatorWorkloadSummary({ notifications }: { notifications: Notification[] }) {
  const pendingReview = notifications.filter(
    (n) => n.eventType === "submission_pending" && n.unread,
  ).length;
  const deadlineAlerts = notifications.filter((n) => n.category === "deadline").length;
  const totalSubmissions = notifications.filter((n) => n.eventType === "submission_pending").length;
  const systemAlerts = notifications.filter((n) => n.category === "system" && n.unread).length;

  const items = [
    { label: "Awaiting My Review", value: pendingReview, icon: "ti ti-file-search", tone: "info" },
    { label: "Deadline Alerts", value: deadlineAlerts, icon: "ti ti-clock", tone: deadlineAlerts > 0 ? "warning" : "info" },
    { label: "Total Submissions", value: totalSubmissions, icon: "ti ti-files", tone: "blue" },
    { label: "System Alerts", value: systemAlerts, icon: "ti ti-alert-triangle", tone: systemAlerts > 0 ? "error" : "info" },
  ];

  return (
    <aside className="notif-card validator-summary-card" aria-label="Validator workload summary">
      <div className="notif-card-header">
        <div>
          <div className="notif-card-title">Workload Summary</div>
          <div className="notif-card-sub">Your validation queue at a glance</div>
        </div>
      </div>
      <div className="contributor-summary-list">
        {items.map((item) => (
          <div className="contributor-summary-row" key={item.label}>
            <div className={`contributor-summary-icon ${item.tone}`}>
              <i className={item.icon} aria-hidden="true" />
            </div>
            <div>
              <div className="contributor-summary-value">{item.value}</div>
              <div className="contributor-summary-label">{item.label}</div>
            </div>
          </div>
        ))}
      </div>
      <div className="validator-summary-note">
        New submissions appear instantly. Resolve deadline alerts from the validation queue.
      </div>
    </aside>
  );
}

function AdminNotificationSummary({
  notifications,
  unreadCount,
  criticalCount,
  sseStatus,
}: {
  notifications: Notification[];
  unreadCount: number;
  criticalCount: number;
  sseStatus: SseStatus;
}) {
  const publishingFailures = notifications.filter(
    (n) => n.eventType === "submission_publish_failed",
  ).length;
  const systemAlerts = notifications.filter(
    (n) => n.category === "system" && n.unread,
  ).length;

  const items = [
    {
      label: "Unread Notifications",
      value: unreadCount,
      icon: "ti ti-bell",
      tone: unreadCount > 0 ? "warning" : "info",
    },
    {
      label: "Critical Alerts",
      value: criticalCount,
      icon: "ti ti-alert-octagon",
      tone: criticalCount > 0 ? "error" : "info",
    },
    {
      label: "Publishing Failures",
      value: publishingFailures,
      icon: "ti ti-circle-x",
      tone: publishingFailures > 0 ? "error" : "info",
    },
    {
      label: "System Events",
      value: systemAlerts,
      icon: "ti ti-server",
      tone: systemAlerts > 0 ? "warning" : "info",
    },
  ];

  return (
    <aside className="notif-card admin-summary-card" aria-label="Admin notification overview">
      <div className="notif-card-header">
        <div>
          <div className="notif-card-title">Notification Overview</div>
          <div className="notif-card-sub">Platform health at a glance</div>
        </div>
      </div>
      <div className="contributor-summary-list">
        {items.map((item) => (
          <div className="contributor-summary-row" key={item.label}>
            <div className={`contributor-summary-icon ${item.tone}`}>
              <i className={item.icon} aria-hidden="true" />
            </div>
            <div>
              <div className="contributor-summary-value">{item.value}</div>
              <div className="contributor-summary-label">{item.label}</div>
            </div>
          </div>
        ))}
      </div>
      <div className="admin-summary-sse">
        <div className={`sse-dot ${sseStatus === "connected" ? "" : sseStatus}`} />
        <span className="admin-summary-sse-label">
          SSE Stream &mdash;{" "}
          {sseStatus === "connected"
            ? "Live updates active"
            : sseStatus === "connecting"
            ? "Connecting..."
            : "Disconnected — API fallback active"}
        </span>
      </div>
    </aside>
  );
}

export default function NotificationsScreen({ user }: NotificationsScreenProps) {
  const {
    allNotifications,
    auditLog,
    loading,
    fetchError,
    sseStatus,
    activeFilter,
    setActiveFilter,
    unreadCount,
    criticalCount,
    counts,
    markAllRead,
    markRead,
    refreshNotifications,
  } = useNotifications();

  const isContributor = user.role === "contributor";
  const isValidator = user.role === "validator";
  const isAdmin = !isContributor && !isValidator;

  const workflowNotifications = isContributor
    ? allNotifications.filter(isContributorWorkflowNotification)
    : isValidator
    ? allNotifications.filter(isValidatorWorkflowNotification)
    : allNotifications;

  const visibleNotifications = (() => {
    if (isContributor) {
      return workflowNotifications.filter((n) => {
        if (activeFilter === "all") return true;
        if (activeFilter === "unread") return n.unread;
        if (activeFilter === "submissions") return n.category === "submissions" || n.category === "overrides";
        return n.category === activeFilter;
      });
    }
    if (isValidator) {
      return workflowNotifications.filter((n) => {
        if (activeFilter === "all") return true;
        if (activeFilter === "unread") return n.unread;
        return n.category === activeFilter;
      });
    }
    return workflowNotifications.filter((n) => {
      if (activeFilter === "all") return true;
      if (activeFilter === "unread") return n.unread;
      return n.category === activeFilter;
    });
  })();

  const contributorCounts = isContributor
    ? {
        ...counts,
        all: workflowNotifications.length,
        unread: workflowNotifications.filter((n) => n.unread).length,
        submissions: workflowNotifications.filter((n) => n.category === "submissions" || n.category === "overrides").length,
        publishing: workflowNotifications.filter((n) => n.category === "publishing").length,
        deadline: workflowNotifications.filter((n) => n.category === "deadline").length,
      }
    : counts;

  const validatorCounts = isValidator
    ? {
        ...counts,
        all: workflowNotifications.length,
        unread: workflowNotifications.filter((n) => n.unread).length,
        submissions: workflowNotifications.filter((n) => n.category === "submissions").length,
        deadline: workflowNotifications.filter((n) => n.category === "deadline").length,
        system: workflowNotifications.filter((n) => n.category === "system").length,
      }
    : counts;

  const displayCounts = isContributor ? contributorCounts : isValidator ? validatorCounts : counts;
  const displayFilters = isContributor
    ? CONTRIBUTOR_FILTERS
    : isValidator
    ? VALIDATOR_FILTERS
    : ADMIN_FILTERS;

  const pageClass = [
    "notif-page",
    isContributor ? "notif-page-contributor" : "",
    isValidator ? "notif-page-validator" : "",
    isAdmin ? "notif-page-admin" : "",
  ]
    .filter(Boolean)
    .join(" ");

  const pageTitle = isContributor
    ? "Workflow Inbox"
    : isValidator
    ? "Validation Inbox"
    : "Notification Center";

  const pageSubtitle = isContributor
    ? "Track submission feedback, publishing updates, and schedule changes in one place."
    : isValidator
    ? "Monitor incoming submissions, track validation deadlines, and stay on top of your review queue."
    : "Monitor platform-wide alerts, publishing events, system health, and workflow activity.";

  const panelTitle = isContributor
    ? "Latest Workflow Updates"
    : isValidator
    ? "Latest Validation Alerts"
    : "All Notifications";

  return (
    <div className={pageClass}>
      <div className="notif-page-header">
        <div>
          <h1>{pageTitle}</h1>
          <p>{pageSubtitle}</p>
        </div>
        <div className="notif-page-actions">
          <button type="button" className="notif-btn notif-btn-ghost" onClick={markAllRead}>
            <i className="ti ti-checks" style={{ fontSize: 14 }} />
            <span>Mark all read</span>
          </button>
        </div>
      </div>

      <div className="notif-layout">
        <div className="notif-panel">
          <div className="notif-panel-header">
            <div>
              <div className="notif-panel-title">{panelTitle}</div>
              <div className="notif-panel-meta">
                {workflowNotifications.length} updates &mdash;{" "}
                {workflowNotifications.filter((n) => n.unread).length} unread
              </div>
            </div>
            <div className="notif-panel-actions">
              <button
                type="button"
                className="notif-icon-btn"
                title="Refresh"
                onClick={refreshNotifications}
              >
                <i className="ti ti-refresh" style={{ fontSize: 14 }} />
              </button>
            </div>
          </div>

          <FilterTabs
            activeFilter={activeFilter}
            counts={displayCounts}
            onChange={setActiveFilter}
            filters={displayFilters}
          />

          <NotificationList
            notifications={visibleNotifications}
            onNotificationClick={markRead}
            loading={loading}
            error={fetchError}
            emptyTitle={
              isContributor
                ? "No workflow updates yet"
                : isValidator
                ? "No validation alerts yet"
                : "No notifications yet"
            }
            emptyMessage={
              isContributor
                ? "When your submissions are reviewed, scheduled, revised, or published, they will appear here."
                : isValidator
                ? "When contributors from your institution submit content, it will appear here for review."
                : "Platform-wide events, publishing alerts, and system notifications will appear here."
            }
          />
        </div>

        <div className="notif-right-sidebar">
          {isContributor ? (
            <ContributorActivitySummary notifications={workflowNotifications} />
          ) : isValidator ? (
            <ValidatorWorkloadSummary notifications={workflowNotifications} />
          ) : (
            <>
              <AdminNotificationSummary
                notifications={workflowNotifications}
                unreadCount={unreadCount}
                criticalCount={criticalCount}
                sseStatus={sseStatus}
              />
              <AuditLog entries={auditLog} />
            </>
          )}
        </div>
      </div>
    </div>
  );
}

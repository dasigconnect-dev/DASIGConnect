import "../../styles/notifications.css";
import { useNotifications } from "./hooks/useNotifications";
import FilterTabs from "./components/FilterTabs";
import NotificationList from "./components/NotificationList";
import SseStatusBar from "./components/SseStatusBar";
import BellWidget from "./components/BellWidget";
import DeliveryChannels from "./components/DeliveryChannels";
import AuditLog from "./components/AuditLog";
import type { User } from "../../types/auth.types";
import type { Notification, NotificationFilter } from "./types";

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

function isContributorWorkflowNotification(notification: Notification) {
  return ["submissions", "publishing", "deadline", "overrides"].includes(notification.category);
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

export default function NotificationsScreen({ user }: NotificationsScreenProps) {
  const {
    notifications,
    allNotifications,
    auditLog,
    loading,
    fetchError,
    sseStatus,
    eventCount,
    lastEventTime,
    activeFilter,
    setActiveFilter,
    unreadCount,
    criticalCount,
    totalCount,
    counts,
    markAllRead,
    markRead,
    refreshNotifications,
  } = useNotifications();
  const isContributor = user.role === "contributor";
  const workflowNotifications = isContributor
    ? allNotifications.filter(isContributorWorkflowNotification)
    : notifications;
  const visibleNotifications = isContributor
    ? workflowNotifications.filter((n) => {
        if (activeFilter === "all") return true;
        if (activeFilter === "unread") return n.unread;
        if (activeFilter === "submissions") return n.category === "submissions" || n.category === "overrides";
        return n.category === activeFilter;
      })
    : notifications;
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

  return (
    <div className={`notif-page${isContributor ? " notif-page-contributor" : ""}`}>
      <div className="notif-page-header">
        <div>
          <h1>{isContributor ? "Workflow Inbox" : "Notifications"}</h1>
          <p>
            {isContributor
              ? "Track submission feedback, publishing updates, and schedule changes in one place."
              : "System and submission notifications with real-time SSE delivery"}
          </p>
        </div>
        <div className="notif-page-actions">
          <button type="button" className="notif-btn notif-btn-ghost" onClick={markAllRead}>
            <i className="ti ti-checks" style={{ fontSize: 14 }} />
            <span>Mark all read</span>
          </button>
          {!isContributor && <button type="button" className="notif-btn notif-btn-ghost">
            <i className="ti ti-settings" style={{ fontSize: 14 }} />
            <span>Preferences</span>
          </button>}
        </div>
      </div>

      {!isContributor && (
        <SseStatusBar
          status={sseStatus}
          eventCount={eventCount}
          unreadCount={unreadCount}
          lastEventTime={lastEventTime}
        />
      )}

      <div className="notif-layout">
        <div className="notif-panel">
          <div className="notif-panel-header">
            <div>
              <div className="notif-panel-title">
                {isContributor ? "Latest Workflow Updates" : "All Notifications"}
              </div>
              <div className="notif-panel-meta">
                {workflowNotifications.length} updates - {workflowNotifications.filter((n) => n.unread).length} unread
              </div>
            </div>
            <div className="notif-panel-actions">
              {!isContributor && <button type="button" className="notif-icon-btn" title="Filter">
                <i className="ti ti-adjustments" style={{ fontSize: 14 }} />
              </button>}
              {!isContributor && <button type="button" className="notif-icon-btn" title="Sort">
                <i className="ti ti-arrows-sort" style={{ fontSize: 14 }} />
              </button>}
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
            counts={contributorCounts}
            onChange={setActiveFilter}
            filters={isContributor ? CONTRIBUTOR_FILTERS : undefined}
          />

          <NotificationList
            notifications={visibleNotifications}
            onNotificationClick={markRead}
            loading={loading}
            error={fetchError}
            emptyTitle={isContributor ? "No workflow updates yet" : undefined}
            emptyMessage={
              isContributor
                ? "When your submissions are reviewed, scheduled, revised, or published, they will appear here."
                : undefined
            }
          />
        </div>

        <div className="notif-right-sidebar">
          {isContributor ? (
            <ContributorActivitySummary notifications={workflowNotifications} />
          ) : (
            <>
              <BellWidget
                unreadCount={unreadCount}
                criticalCount={criticalCount}
                totalCount={totalCount}
              />
              <DeliveryChannels
                totalCount={totalCount}
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

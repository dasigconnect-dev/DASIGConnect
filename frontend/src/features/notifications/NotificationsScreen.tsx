import { useState, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import "../../styles/notifications.css";
import "../../styles/dasig-loader.css";
import { useNotifications } from "./hooks/useNotifications";
import type { User } from "../../types/auth.types";
import type { Notification, NotificationFilter } from "./types";
import { FILTER_LABELS } from "./types";

interface NotificationsScreenProps {
  user: User;
}

const PAGE_SIZE = 7;

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

function formatNotificationDate(isoString?: string): string {
  if (!isoString) return "—";
  const date = new Date(isoString);
  if (isNaN(date.getTime())) return "—";
  return date.toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

function getEventStatusBadge(eventType: string) {
  switch (eventType) {
    case "submission_approved":
      return { label: "Approved", icon: "ti ti-circle-check", className: "sp-approved" };
    case "submission_published":
    case "submission_published_manual":
      return { label: "Published", icon: "ti ti-circle-check", className: "pill-published" };
    case "submission_scheduled":
      return { label: "Scheduled", icon: "ti ti-calendar", className: "sp-scheduled" };
    case "submission_pending":
      return { label: "Under Review", icon: "ti ti-clock", className: "sp-review" };
    case "submission_needs_revision":
      return { label: "Needs Revision", icon: "ti ti-pencil", className: "pill-revision" };
    case "submission_rejected":
      return { label: "Rejected", icon: "ti ti-circle-x", className: "pill-rejected" };
    case "submission_publish_failed":
      return { label: "Publish Failed", icon: "ti ti-alert-triangle", className: "pill-failed" };
    case "token_expiring":
    case "token_invalid":
      return { label: "Token Warning", icon: "ti ti-key", className: "pill-failed" };
    case "empty_schedule_warning":
      return { label: "Schedule Notice", icon: "ti ti-calendar-off", className: "sp-pending" };
    case "deadline_warning":
      return { label: "Deadline Alert", icon: "ti ti-alert-circle", className: "pill-failed" };
    case "override_approved":
      return { label: "Override Approved", icon: "ti ti-check", className: "sp-approved" };
    case "override_denied":
      return { label: "Override Denied", icon: "ti ti-ban", className: "pill-rejected" };
    case "override_slot_suggested":
      return { label: "Slot Suggested", icon: "ti ti-calendar-plus", className: "sp-scheduled" };
    default:
      return { label: "Update", icon: "ti ti-bell", className: "sp-pending" };
  }
}

function getNotificationTargetRoute(n: Notification, userRole: User["role"]): string {
  if (n.link) {
    if (n.link.startsWith("http://") || n.link.startsWith("https://")) {
      return n.link;
    }
    if (n.link !== "/dashboard" && n.link !== "/notifications" && n.link !== "/") {
      if (n.link.startsWith("/submissions/")) {
        const subId = n.link.replace("/submissions/", "");
        if (userRole === "administrator" || userRole === "super_administrator") {
          return `/validation/queue?submissionId=${subId}`;
        }
        return `/submissions?id=${subId}`;
      }
      return n.link;
    }
  }

  const eventType = n.eventType;
  const isAdmin = userRole === "administrator" || userRole === "super_administrator";

  if (
    eventType === "submission_pending" ||
    eventType === "fast_track_submission" ||
    eventType === "validation_timeout" ||
    eventType === "submission_missed_review"
  ) {
    return isAdmin ? "/validation/queue" : "/submissions";
  }

  if (eventType === "submission_publish_failed") {
    return isAdmin ? "/validation/queue?tab=failed" : "/submissions?tab=failed";
  }

  if (
    eventType === "submission_approved" ||
    eventType === "submission_scheduled" ||
    eventType === "submission_rescheduled" ||
    eventType === "empty_schedule_warning" ||
    eventType === "admin_direct_post"
  ) {
    return "/scheduler/calendar";
  }

  if (eventType === "submission_published" || eventType === "submission_published_manual") {
    return isAdmin ? "/scheduler/calendar" : "/submissions?tab=published";
  }

  if (eventType === "submission_needs_revision") {
    return "/submissions?tab=drafts";
  }

  if (eventType === "submission_rejected") {
    return "/submissions";
  }

  if (eventType === "token_expiring" || eventType === "token_invalid") {
    return "/settings#page";
  }

  if (eventType === "institution_onboarded" || eventType === "institution_no_validator") {
    return "/admin/institution-management";
  }

  if (eventType === "embedding_failure_digest") {
    return "/media-repository";
  }

  if (n.category === "submissions" || n.category === "overrides") {
    return isAdmin ? "/validation/queue" : "/submissions";
  }

  if (n.category === "publishing" || n.category === "deadline") {
    return "/scheduler/calendar";
  }

  if (n.category === "system") {
    return isAdmin ? "/admin/system-health" : "/settings";
  }

  return isAdmin ? "/validation/queue" : "/submissions";
}

export default function NotificationsScreen({ user }: NotificationsScreenProps) {
  const navigate = useNavigate();
  const {
    allNotifications,
    loading,
    fetchError,
    activeFilter,
    setActiveFilter,
    counts,
    markAllRead,
    markRead,
    refreshNotifications,
  } = useNotifications();

  const [searchQuery, setSearchQuery] = useState("");
  const [showAll, setShowAll] = useState(false);

  const isContributor = user.role === "contributor";
  const isValidator = user.role === "administrator";

  const workflowNotifications = useMemo(() => {
    if (isContributor) {
      return allNotifications.filter(isContributorWorkflowNotification);
    }
    if (isValidator) {
      return allNotifications.filter(isValidatorWorkflowNotification);
    }
    return allNotifications;
  }, [allNotifications, isContributor, isValidator]);

  const contributorCounts = useMemo(() => {
    return isContributor
      ? {
          ...counts,
          all: workflowNotifications.length,
          unread: workflowNotifications.filter((n) => n.unread).length,
          submissions: workflowNotifications.filter(
            (n) => n.category === "submissions" || n.category === "overrides",
          ).length,
          publishing: workflowNotifications.filter((n) => n.category === "publishing").length,
          deadline: workflowNotifications.filter((n) => n.category === "deadline").length,
        }
      : counts;
  }, [counts, isContributor, workflowNotifications]);

  const validatorCounts = useMemo(() => {
    return isValidator
      ? {
          ...counts,
          all: workflowNotifications.length,
          unread: workflowNotifications.filter((n) => n.unread).length,
          submissions: workflowNotifications.filter((n) => n.category === "submissions").length,
          deadline: workflowNotifications.filter((n) => n.category === "deadline").length,
          system: workflowNotifications.filter((n) => n.category === "system").length,
        }
      : counts;
  }, [counts, isValidator, workflowNotifications]);

  const displayCounts = isContributor ? contributorCounts : isValidator ? validatorCounts : counts;
  const displayFilters = isContributor
    ? CONTRIBUTOR_FILTERS
    : isValidator
    ? VALIDATOR_FILTERS
    : ADMIN_FILTERS;

  const filteredNotifications = useMemo(() => {
    return workflowNotifications.filter((n) => {
      let matchesFilter = true;
      if (activeFilter === "unread") {
        matchesFilter = n.unread;
      } else if (activeFilter === "submissions") {
        matchesFilter = n.category === "submissions" || n.category === "overrides";
      } else if (activeFilter !== "all") {
        matchesFilter = n.category === activeFilter;
      }

      if (!matchesFilter) return false;

      const term = searchQuery.trim().toLowerCase();
      if (!term) return true;

      return (
        n.text.toLowerCase().includes(term) ||
        n.sender.toLowerCase().includes(term) ||
        n.category.toLowerCase().includes(term) ||
        n.eventType.toLowerCase().includes(term) ||
        n.trigger.toLowerCase().includes(term)
      );
    });
  }, [workflowNotifications, activeFilter, searchQuery]);

  const displayedNotifications = useMemo(() => {
    if (showAll) return filteredNotifications;
    return filteredNotifications.slice(0, PAGE_SIZE);
  }, [filteredNotifications, showAll]);

  function handleFilterChange(filter: NotificationFilter) {
    setActiveFilter(filter);
  }

  function handleSearchChange(val: string) {
    setSearchQuery(val);
  }

  function handleRowClick(n: Notification) {
    if (n.unread) {
      markRead(n.id);
    }
    const target = getNotificationTargetRoute(n, user.role);
    if (target.startsWith("http://") || target.startsWith("https://")) {
      window.open(target, "_blank", "noopener,noreferrer");
    } else {
      navigate(target);
    }
  }

  const pageTitle = isContributor
    ? "Workflow Inbox"
    : isValidator
    ? "Validation Inbox"
    : "Notifications";

  const pageSubtitle = isContributor
    ? "Complete overview of submission feedback and publishing updates across your workspace."
    : isValidator
    ? "Complete overview of incoming submissions and validation deadlines across your workspace."
    : "Complete overview of notifications, alerts, and publishing activities across your workspace.";

  // Initial Full Screen Loading State
  if (loading) {
    return (
      <div id="screen-notifications" style={{ background: "var(--d-bg)" }}>
        <div className="dash-body">
          <div className="dash-view-header">
            <h1 className="dash-view-title">{pageTitle}</h1>
            <p className="dash-view-desc">{pageSubtitle}</p>
          </div>

          <div
            className="card-wrap"
            style={{
              minHeight: "380px",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              padding: "60px 20px",
              background: "var(--d-surface, #ffffff)",
            }}
          >
            <div className="dc-dot-triangle-container">
              <div className="loader-dots" />
              <div className="dc-dot-triangle-label">
                Loading Notifications
                <span className="dc-dot-triangle-label-dots">
                  <span className="dc-dot-triangle-dot-char">.</span>
                  <span className="dc-dot-triangle-dot-char">.</span>
                  <span className="dc-dot-triangle-dot-char">.</span>
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div id="screen-notifications" style={{ background: "var(--d-bg)" }}>
      <div className="dash-body">
        {/* Page Header */}
        <div className="dash-view-header" style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", flexWrap: "wrap", gap: "16px" }}>
          <div>
            <h1 className="dash-view-title">{pageTitle}</h1>
            <p className="dash-view-desc">{pageSubtitle}</p>
          </div>

          <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
            <button
              type="button"
              className="notif-btn notif-btn-ghost"
              onClick={markAllRead}
              title="Mark all notifications as read"
            >
              <i className="ti ti-checks" style={{ fontSize: 14 }} />
              <span>Mark all read</span>
            </button>

            <button
              type="button"
              className="notif-btn notif-btn-ghost notif-btn-icon"
              onClick={refreshNotifications}
              title="Refresh"
              aria-label="Refresh"
            >
              <i className="ti ti-refresh" style={{ fontSize: 14 }} />
            </button>
          </div>
        </div>

        {/* Card 1: Filter Tabs & Search Toolbar */}
        <div className="card-wrap" style={{ marginBottom: "16px" }}>
          <div className="dash-card-toolbar">
            <div className="im-status-tabs" role="group" aria-label="Filter notifications by category">
              {displayFilters.map((f) => (
                    <button
                      key={f}
                      type="button"
                      className={`im-status-tab${activeFilter === f ? " is-active" : ""}`}
                      onClick={() => handleFilterChange(f)}
                      aria-pressed={activeFilter === f}
                    >
                      {FILTER_LABELS[f]}
                      <span className="im-status-tab-count">{displayCounts[f]}</span>
                    </button>
                  ))}
                </div>

                <div className="im-search-wrap">
                  <i className="ti ti-search im-search-icon" aria-hidden="true" />
                  <input
                    className="im-search-input"
                    type="search"
                    placeholder="Search notifications..."
                    value={searchQuery}
                    onChange={(e) => handleSearchChange(e.target.value)}
                    aria-label="Search notifications"
                  />
                </div>
              </div>
            </div>

            {/* Independent Action Row between Card 1 and Card 2 */}
            {filteredNotifications.length > PAGE_SIZE && (
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "space-between",
                  marginBottom: "14px",
                  padding: "0 2px",
                }}
              >
                <span style={{ fontSize: "13px", color: "var(--d-muted, #5a6f8a)", fontWeight: 500 }}>
                  Showing {displayedNotifications.length} of {filteredNotifications.length} notifications
                </span>

                <button
                  type="button"
                  className="notif-view-all-pill"
                  onClick={() => setShowAll((prev) => !prev)}
                  title={showAll ? "Show top 7 notifications" : "Show all notifications"}
                >
                  <i className={showAll ? "ti ti-chevron-up" : "ti ti-list-details"} />
                  <span>{showAll ? `Show Top ${PAGE_SIZE}` : `View All Notifications (${filteredNotifications.length})`}</span>
                </button>
              </div>
            )}

            {/* Card 2: Data Table */}
            <div className="card-wrap">
              <table className="data-table" id="activity-full-table">
                <thead>
                  <tr>
                    <th style={{ width: "42%" }}>NOTIFICATION / EVENT</th>
                    <th style={{ width: "24%" }}>SOURCE</th>
                    <th style={{ width: "16%" }}>RECEIVED</th>
                    <th style={{ width: "18%" }}>STATUS</th>
                  </tr>
                </thead>
                <tbody
                  id="activity-full-body"
                  key={`${activeFilter}-${searchQuery}-${showAll}`}
                  className="act-table-animate"
                >
                  {fetchError ? (
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
                            background: "#fef2f2",
                            border: "1px solid #fee2e2",
                            display: "flex",
                            alignItems: "center",
                            justifyContent: "center",
                            margin: "0 auto 12px",
                            color: "#ef4444",
                            fontSize: 24,
                          }}
                        >
                          <i className="ti ti-wifi-off" />
                        </div>
                        <div style={{ fontWeight: 600, color: "#1e293b", marginBottom: 4 }}>
                          Connection error
                        </div>
                        <div style={{ color: "#64748b", fontSize: 12.5, marginBottom: 12 }}>
                          {fetchError}
                        </div>
                        <button
                          type="button"
                          className="notif-btn notif-btn-ghost"
                          onClick={refreshNotifications}
                        >
                          <i className="ti ti-refresh" /> Retry
                        </button>
                      </td>
                    </tr>
                  ) : filteredNotifications.length === 0 ? (
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
                            className="ti ti-bell-off"
                            style={{
                              fontSize: 24,
                              color: "#3b82f6",
                            }}
                          />
                        </div>
                        <div
                          style={{
                            fontWeight: 600,
                            color: "#1e293b",
                            marginBottom: 4,
                          }}
                        >
                          {searchQuery.trim() || activeFilter !== "all"
                            ? "No matching notifications"
                            : "No notifications yet"}
                        </div>
                        <div style={{ color: "#64748b", fontSize: 12.5 }}>
                          {searchQuery.trim() || activeFilter !== "all"
                            ? "Try clearing your search or switching to all categories."
                            : "Platform-wide events, publishing alerts, and system notifications will appear here."}
                        </div>
                      </td>
                    </tr>
                  ) : (
                    displayedNotifications.map((n) => {
                      const statusBadge = getEventStatusBadge(n.eventType);
                      return (
                        <tr
                          key={n.id}
                          onClick={() => handleRowClick(n)}
                          style={{ cursor: "pointer" }}
                          title={n.link ? `Click to view: ${n.linkLabel}` : undefined}
                        >
                          <td>
                            <div style={{ display: "flex", alignItems: "flex-start", gap: "10px" }}>
                              {n.unread && (
                                <span
                                  style={{
                                    width: 7,
                                    height: 7,
                                    borderRadius: "50%",
                                    background: "#2563eb",
                                    marginTop: 6,
                                    flexShrink: 0,
                                  }}
                                  title="Unread"
                                />
                              )}
                              <div style={{ minWidth: 0 }}>
                                <div className="act-title">{n.text}</div>
                                <span className="act-category">
                                  {n.trigger && <span className="notif-trigger-code">{n.trigger}</span>}{" "}
                                  {n.time}
                                </span>
                              </div>
                            </div>
                          </td>
                          <td className="act-institution">{n.sender}</td>
                          <td className="act-date">{formatNotificationDate(n.createdAt)}</td>
                          <td>
                            <span className={`status-pill ${statusBadge.className}`}>
                              <i className={statusBadge.icon} /> {statusBadge.label}
                            </span>
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>
      </div>
    </div>
  );
}

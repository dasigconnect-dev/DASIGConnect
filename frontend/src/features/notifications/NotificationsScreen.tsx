import "../../styles/notifications.css";
import { useNotifications } from "./hooks/useNotifications";
import FilterTabs from "./components/FilterTabs";
import NotificationList from "./components/NotificationList";
import SseStatusBar from "./components/SseStatusBar";
import BellWidget from "./components/BellWidget";
import DeliveryChannels from "./components/DeliveryChannels";
import AuditLog from "./components/AuditLog";

export default function NotificationsScreen() {
  const {
    notifications,
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

  return (
    <div className="notif-page">
      <div className="notif-page-header">
        <div>
          <h1>Notifications</h1>
          <p>System and submission notifications with real-time SSE delivery</p>
        </div>
        <div className="notif-page-actions">
          <button type="button" className="notif-btn notif-btn-ghost" onClick={markAllRead}>
            <i className="ti ti-checks" style={{ fontSize: 14 }} />
            <span>Mark all read</span>
          </button>
          <button type="button" className="notif-btn notif-btn-ghost">
            <i className="ti ti-settings" style={{ fontSize: 14 }} />
            <span>Preferences</span>
          </button>
        </div>
      </div>

      <SseStatusBar
        status={sseStatus}
        eventCount={eventCount}
        unreadCount={unreadCount}
        lastEventTime={lastEventTime}
      />

      <div className="notif-layout">
        <div className="notif-panel">
          <div className="notif-panel-header">
            <div>
              <div className="notif-panel-title">All Notifications</div>
              <div className="notif-panel-meta">
                {totalCount} notifications - {unreadCount} unread
              </div>
            </div>
            <div className="notif-panel-actions">
              <button type="button" className="notif-icon-btn" title="Filter">
                <i className="ti ti-adjustments" style={{ fontSize: 14 }} />
              </button>
              <button type="button" className="notif-icon-btn" title="Sort">
                <i className="ti ti-arrows-sort" style={{ fontSize: 14 }} />
              </button>
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

          <FilterTabs activeFilter={activeFilter} counts={counts} onChange={setActiveFilter} />

          <NotificationList
            notifications={notifications}
            onNotificationClick={markRead}
            loading={loading}
            error={fetchError}
          />
        </div>

        <div className="notif-right-sidebar">
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
        </div>
      </div>
    </div>
  );
}

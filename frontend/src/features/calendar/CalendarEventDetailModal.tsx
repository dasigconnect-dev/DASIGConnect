import type { CalendarEvent } from "../../api/calendarApi";
import type { User } from "../../types/auth.types";
import { statusColor, statusLabel } from "./calendarStatus";

function formatDatetime(iso: string) {
  return new Date(iso).toLocaleString("en-PH", {
    month: "short",
    day: "numeric",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
    hour12: true,
  });
}

interface CalendarEventDetailModalProps {
  event: CalendarEvent | null;
  role: User["role"];
  onClose: () => void;
}

export default function CalendarEventDetailModal({
  event,
  role,
  onClose,
}: CalendarEventDetailModalProps) {
  if (!event) return null;

  return (
    <div
      className="modal-backdrop"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-label="Event detail"
    >
      <div
        className="modal-card cal-detail-card"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="modal-card-header">
          <h2 className="modal-card-title">
            {event.title ?? "Slot Reserved"}
          </h2>
          <button
            type="button"
            className="modal-close-btn"
            onClick={onClose}
            aria-label="Close"
          >
            <i className="ti ti-x" />
          </button>
        </div>
        <div className="modal-card-body">
          <div className="cal-detail-row">
            <span className="cal-detail-label">Institution</span>
            <span className="cal-detail-value">
              {event.institutionName}
              {event.institutionCode && (
                <span className="cal-detail-code">
                  {" "}({event.institutionCode})
                </span>
              )}
            </span>
          </div>
          <div className="cal-detail-row">
            <span className="cal-detail-label">Scheduled</span>
            <span className="cal-detail-value">
              {formatDatetime(event.scheduledAt)}
            </span>
          </div>
          {event.publishedAt && (
            <div className="cal-detail-row">
              <span className="cal-detail-label">Published</span>
              <span className="cal-detail-value">
                {formatDatetime(event.publishedAt)}
              </span>
            </div>
          )}
          <div className="cal-detail-row">
            <span className="cal-detail-label">Status</span>
            <span
              className="status-badge"
              style={{
                background: statusColor(event.status).bg,
                color: statusColor(event.status).text,
              }}
            >
              {statusLabel(event.status)}
            </span>
          </div>
          {role === "admin" && (
            <div className="cal-detail-row">
              <span className="cal-detail-label">ID</span>
              <span className="cal-detail-value cal-detail-mono">
                {event.id}
              </span>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

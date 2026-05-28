import type { CalendarEvent } from "../../api/calendarApi";
import type { User } from "../../types/auth.types";
import { statusColor, statusLabel } from "./calendarStatus";
import { useEffect, useLayoutEffect, useRef } from "react";
import { createPortal } from "react-dom";

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
  const drawerBodyRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!event) return;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const onKeyDown = (keyboardEvent: KeyboardEvent) => {
      if (keyboardEvent.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", onKeyDown);
    };
  }, [event, onClose]);

  useLayoutEffect(() => {
    if (!event) return;
    drawerBodyRef.current?.scrollTo({ top: 0, left: 0 });
  }, [event]);

  if (!event) return null;

  return createPortal(
    <div
      className="cal-drawer-backdrop"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-label="Calendar event workflow detail"
    >
      <aside
        className="cal-workflow-drawer"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="cal-drawer-header">
          <div>
            <p className="cal-detail-kicker">Publishing workflow detail</p>
            <h2>{event.title ?? "Reserved publishing slot"}</h2>
            <div className="cal-drawer-header-meta">
              <span>{event.institutionName}</span>
              <span>{formatDatetime(event.scheduledAt)}</span>
            </div>
          </div>
          <button
            type="button"
            className="modal-close-btn"
            onClick={onClose}
            aria-label="Close"
          >
            <i className="ti ti-x" />
          </button>
        </div>

        <div className="cal-drawer-body" ref={drawerBodyRef}>
          <section className="cal-drawer-priority">
            <span
              className="status-badge"
              style={{
                background: statusColor(event.status).bg,
                color: statusColor(event.status).text,
              }}
            >
              {statusLabel(event.status)}
            </span>
            <p>{workflowHint(event.status)}</p>
          </section>

          <section className="cal-drawer-section">
            <div className="cal-modal-section-label">Primary Details</div>
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
            <DetailRow label="Contributor" value={role === "admin" ? "Available in submission record" : "Your institution workspace"} />
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
          </section>

          <details className="cal-drawer-disclosure" open>
            <summary>Workflow Notes</summary>
            <div className="cal-modal-section-label">Workflow Notes</div>
            <div className="cal-detail-row">
              <span className="cal-detail-label">Next Step</span>
              <span className="cal-detail-value">{workflowCopy(event.status)}</span>
            </div>
            <div className="cal-detail-row">
              <span className="cal-detail-label">Caption</span>
              <span className="cal-detail-value cal-detail-muted">
                Caption preview is not included in the calendar feed yet.
              </span>
            </div>
          </details>

          <details className="cal-drawer-disclosure">
            <summary>Media Preview</summary>
            <div className="cal-detail-media-placeholder">
              <i className="ti ti-photo" aria-hidden="true" />
              <span>Media preview unavailable from calendar feed</span>
            </div>
          </details>

          {role === "admin" && (
            <details className="cal-drawer-disclosure">
              <summary>Metadata</summary>
              <div className="cal-detail-row">
                <span className="cal-detail-label">ID</span>
                <span className="cal-detail-value cal-detail-mono">
                  {event.id}
                </span>
              </div>
            </details>
          )}
        </div>
      </aside>
    </div>,
    document.body,
  );
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="cal-detail-row">
      <span className="cal-detail-label">{label}</span>
      <span className="cal-detail-value">{value}</span>
    </div>
  );
}

function workflowHint(status: string) {
  const value = status.toLowerCase();
  if (value.includes("failed")) return "Needs attention before this content can move forward.";
  if (value === "published" || value === "published_manual") return "Completed publishing workflow.";
  if (value === "admin_direct_post" || value === "direct_post_scheduled") return "Administrator-managed post.";
  return "Queued in the publishing schedule.";
}

function workflowCopy(status: string) {
  const value = status.toLowerCase();
  if (value.includes("failed")) return "Review the Resolution Center or related submission record for recovery steps.";
  if (value === "published") return "This content was published through the automated publishing pipeline.";
  if (value === "published_manual") return "This content was completed through the manual publishing fallback.";
  if (value === "admin_direct_post" || value === "direct_post_scheduled") return "This item was created through an administrator direct-post flow.";
  return "This content is scheduled and waiting for its publishing slot.";
}

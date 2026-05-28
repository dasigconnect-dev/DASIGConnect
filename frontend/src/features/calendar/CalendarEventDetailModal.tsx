import type { CalendarEvent } from "../../api/calendarApi";
import { getSubmission } from "../../api/submissionApi";
import type { SavedMediaAsset, SubmissionSummary } from "../../api/submissionApi";
import type { User } from "../../types/auth.types";
import { visibleStatusColor, visibleStatusLabel, visibleCalendarStatus } from "./calendarStatus";
import { useEffect, useLayoutEffect, useRef, useState } from "react";
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
  user: User;
  onClose: () => void;
}

export default function CalendarEventDetailModal({
  event,
  user,
  onClose,
}: CalendarEventDetailModalProps) {
  const drawerBodyRef = useRef<HTMLDivElement>(null);
  const [submissionDetail, setSubmissionDetail] = useState<SubmissionSummary | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState(false);

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

  useEffect(() => {
    if (!event) {
      setSubmissionDetail(null);
      setDetailError(false);
      setDetailLoading(false);
      return;
    }

    const controller = new AbortController();
    setSubmissionDetail(null);
    setDetailError(false);
    setDetailLoading(true);

    getSubmission(event.id, controller.signal)
      .then((response) => {
        setSubmissionDetail(response.data);
      })
      .catch((error) => {
        if (controller.signal.aborted || error?.name === "CanceledError") return;
        setDetailError(true);
      })
      .finally(() => {
        if (!controller.signal.aborted) setDetailLoading(false);
      });

    return () => controller.abort();
  }, [event]);

  useLayoutEffect(() => {
    if (!event) return;
    drawerBodyRef.current?.scrollTo({ top: 0, left: 0 });
  }, [event]);

  if (!event) return null;

  const mediaAssets = submissionDetail?.mediaAssets ?? [];
  const caption = submissionDetail?.caption?.trim();
  const isOwnInstitution = Boolean(user.institutionId && event.institutionId && user.institutionId === event.institutionId);
  const displayStatus = visibleCalendarStatus(event.status, user.role, isOwnInstitution);
  const displayColor = visibleStatusColor(event.status, user.role, isOwnInstitution);

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
                background: displayColor.bg,
                color: displayColor.text,
              }}
            >
              {visibleStatusLabel(event.status, user.role, isOwnInstitution)}
            </span>
            <p>{workflowHint(displayStatus)}</p>
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
            <DetailRow label="Contributor" value={user.role === "admin" ? "Available in submission record" : "Your institution workspace"} />
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
              <span className="cal-detail-value">{workflowCopy(displayStatus)}</span>
            </div>
            <div className="cal-detail-row">
              <span className="cal-detail-label">Caption</span>
              <span className="cal-detail-value cal-detail-muted">
                {detailLoading
                  ? "Loading caption..."
                  : caption || "No caption attached to this scheduled post."}
              </span>
            </div>
          </details>

          <details className="cal-drawer-disclosure" open>
            <summary>Media Preview</summary>
            <CalendarMediaPreview
              assets={mediaAssets}
              loading={detailLoading}
              error={detailError}
            />
          </details>

          {user.role === "admin" && (
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

function CalendarMediaPreview({
  assets,
  loading,
  error,
}: {
  assets: SavedMediaAsset[];
  loading: boolean;
  error: boolean;
}) {
  if (loading) {
    return (
      <div className="cal-media-preview-loading" aria-label="Loading media preview">
        <span />
        <span />
      </div>
    );
  }

  if (assets.length > 0) {
    return (
      <div className={`cal-media-preview-grid${assets.length === 1 ? " is-single" : ""}`}>
        {assets.map((asset, index) => (
          <figure className="cal-media-preview-item" key={asset.id}>
            {isVideoAsset(asset) ? (
              <video
                src={asset.storageUrl}
                controls
                preload="metadata"
                playsInline
                aria-label={`Video attachment ${index + 1}: ${asset.fileName}`}
              />
            ) : (
              <img
                src={asset.storageUrl}
                alt={asset.fileName || `Media attachment ${index + 1}`}
                loading="lazy"
              />
            )}
            <figcaption>
              <span>{index + 1}</span>
              {isVideoAsset(asset) ? "Video" : "Image"}
            </figcaption>
          </figure>
        ))}
      </div>
    );
  }

  return (
    <div className="cal-detail-media-placeholder">
      <i className={error ? "ti ti-lock" : "ti ti-photo"} aria-hidden="true" />
      <span>
        {error
          ? "Media preview is unavailable for this calendar item."
          : "No media attached. This may be a text-only post."}
      </span>
    </div>
  );
}

function isVideoAsset(asset: SavedMediaAsset) {
  const type = asset.fileType.toLowerCase();
  return ["mp4", "mov", "webm", "video"].some((value) => type.includes(value));
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

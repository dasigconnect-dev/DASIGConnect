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

  const isContributor = user.role !== "administrator" && user.role !== "super_administrator";
  const isOwnInstitution = Boolean(user.institutionId && event?.institutionId && user.institutionId === event.institutionId);
  const isCrossInstitutionIsolated = isContributor && !isOwnInstitution;

  useEffect(() => {
    if (!event || isCrossInstitutionIsolated) {
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
  }, [event, isCrossInstitutionIsolated]);

  useLayoutEffect(() => {
    if (!event) return;
    drawerBodyRef.current?.scrollTo({ top: 0, left: 0 });
  }, [event]);

  if (!event) return null;

  const mediaAssets = submissionDetail?.mediaAssets ?? [];
  const caption = submissionDetail?.caption?.trim();
  const displayStatus = visibleCalendarStatus(event.status, user.role, isOwnInstitution);
  const displayColor = visibleStatusColor(event.status, user.role, isOwnInstitution);
  const isPendingApproval = (event.status || "").toLowerCase() === "pending" || (event.status || "").toLowerCase() === "in_review";

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
            <h2>{isCrossInstitutionIsolated ? "Reserved publishing slot" : (event.title ?? "Reserved publishing slot")}</h2>
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
            {event.locked && (
              <span className="status-badge cal-locked-badge">
                <i className="ti ti-lock" /> Slot Locked
              </span>
            )}
            <p>{workflowHint(displayStatus)}</p>
          </section>

          {isCrossInstitutionIsolated ? (
            <section className="cal-drawer-section" style={{ marginTop: "14px" }}>
              <div
                style={{
                  display: "flex",
                  gap: "12px",
                  padding: "16px",
                  background: "#f8fafc",
                  border: "1px solid #e2e8f0",
                  borderRadius: "12px",
                  color: "#334155",
                }}
              >
                <i className="ti ti-shield-lock" style={{ fontSize: "24px", color: "#1877f2", flexShrink: 0 }} />
                <div>
                  <strong style={{ display: "block", fontSize: "14px", marginBottom: "4px" }}>
                    Content Privacy Protected (UC-3.1 A1)
                  </strong>
                  <p style={{ margin: 0, fontSize: "12px", lineHeight: 1.45, color: "#64748b" }}>
                    To preserve member institution privacy across the shared network, detailed captions, media files, and contributor identities from other institutions are kept confidential.
                  </p>
                </div>
              </div>

              <div style={{ marginTop: "16px" }}>
                <div className="cal-modal-section-label">Slot Timing Information</div>
                <div className="cal-detail-row">
                  <span className="cal-detail-label">Institution</span>
                  <span className="cal-detail-value">{event.institutionName}</span>
                </div>
                <div className="cal-detail-row">
                  <span className="cal-detail-label">Reserved Time</span>
                  <span className="cal-detail-value">{formatDatetime(event.scheduledAt)}</span>
                </div>
                <div className="cal-detail-row">
                  <span className="cal-detail-label">State</span>
                  <span className="cal-detail-value">{visibleStatusLabel(event.status, user.role, isOwnInstitution)}</span>
                </div>
              </div>
            </section>
          ) : (
            <>
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
                <DetailRow
                  label="Contributor"
                  value={event.contributorName || submissionDetail?.contributorEmail || (isOwnInstitution ? "Your institution workspace" : "Available in submission record")}
                />
                <div className="cal-detail-row">
                  <span className="cal-detail-label">Scheduled</span>
                  <span className="cal-detail-value">
                    {formatDatetime(event.scheduledAt)}
                  </span>
                </div>
                {submissionDetail?.submittedAt && (
                  <div className="cal-detail-row">
                    <span className="cal-detail-label">Submitted</span>
                    <span className="cal-detail-value">
                      {formatDatetime(submissionDetail.submittedAt)}
                    </span>
                  </div>
                )}
                {event.publishedAt && (
                  <div className="cal-detail-row">
                    <span className="cal-detail-label">Published</span>
                    <span className="cal-detail-value">
                      {formatDatetime(event.publishedAt)}
                    </span>
                  </div>
                )}
              </section>

              {isPendingApproval && (user.role === "administrator" || user.role === "super_administrator") && (
                <section style={{ marginTop: "12px", marginBottom: "12px" }}>
                  <a
                    href="/validation/queue"
                    style={{
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "space-between",
                      padding: "12px 16px",
                      background: "linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%)",
                      border: "1px solid #bfdbfe",
                      borderRadius: "10px",
                      color: "#1d4ed8",
                      textDecoration: "none",
                      fontWeight: 600,
                      fontSize: "13px",
                      boxShadow: "0 1px 3px rgba(0,0,0,0.05)",
                    }}
                  >
                    <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                      <i className="ti ti-checklist" style={{ fontSize: "16px" }} />
                      <span>Review in Approval Queue (UC-2.4)</span>
                    </div>
                    <i className="ti ti-arrow-right" />
                  </a>
                </section>
              )}

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

              {user.role === "super_administrator" && (
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
            </>
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

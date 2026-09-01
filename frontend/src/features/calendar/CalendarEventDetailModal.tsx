import type { CalendarEvent } from "../../api/calendarApi";
import { getSubmission } from "../../api/submissionApi";
import type { SavedMediaAsset, SubmissionSummary } from "../../api/submissionApi";
import type { User } from "../../types/auth.types";
import { visibleStatusColor, visibleStatusLabel } from "./calendarStatus";
import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useToast } from "../../context/ToastContext";
import "../../styles/calendar.css";

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
  const toast = useToast();
  const drawerBodyRef = useRef<HTMLDivElement>(null);
  const [submissionDetail, setSubmissionDetail] = useState<SubmissionSummary | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState(false);
  const [copied, setCopied] = useState(false);

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

  const isContributor = user.role !== "moderator" && user.role !== "admin";
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

  function handleCopyCaption(text: string) {
    if (!text) return;
    navigator.clipboard.writeText(text).then(() => {
      setCopied(true);
      toast.success("Caption copied to clipboard");
      setTimeout(() => setCopied(false), 2000);
    }).catch(() => {
      toast.error("Unable to copy caption");
    });
  }

  if (!event) return null;

  const mediaAssets = submissionDetail?.mediaAssets ?? [];
  // GET /submissions/{id} only allows the author (or a same-institution
  // moderator/admin), so a contributor opening a same-institution peer's post
  // gets a 403. The calendar DTO already carries the caption in full for
  // own-institution events — fall back to it so the drawer isn't blank.
  const caption = (submissionDetail?.caption ?? event.caption ?? "").trim() || undefined;
  const displayColor = visibleStatusColor(event.status, user.role, event.mine);
  const rawStatus = (event.status || "").toLowerCase();
  const isPendingApproval = rawStatus === "pending" || rawStatus === "in_review" || rawStatus === "needs_revision";
  const isPublished = rawStatus === "published" || rawStatus === "published_manual";
  const isAdmin = user.role === "moderator" || user.role === "admin";

  return createPortal(
    <div
      className="cal-drawer-backdrop"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-label="Calendar event detail"
    >
      <aside className="cal-workflow-drawer" onClick={(e) => e.stopPropagation()}>
        {/* Drawer Header */}
        <div className="cal-drawer-header">
          <div className="cal-drawer-header-content">
            <div className="cal-drawer-header-tags">
              <span className="cal-drawer-inst-tag">
                <i className="ti ti-building" aria-hidden="true" />
                {event.institutionName}
                {event.institutionCode && <span className="cal-inst-code">({event.institutionCode})</span>}
              </span>
              <span
                className="status-badge"
                style={{ background: displayColor.bg, color: displayColor.text, fontSize: "11.5px", fontWeight: 700 }}
              >
                {visibleStatusLabel(event.status, user.role, event.mine)}
              </span>
              {event.locked && (
                <span className="cal-drawer-lock-pill" title="Slot permanently locked">
                  <i className="ti ti-lock" aria-hidden="true" />
                  Locked
                </span>
              )}
            </div>

            <h2 className="cal-drawer-title">
              {isCrossInstitutionIsolated ? "Reserved publishing slot" : (event.title ?? "Reserved publishing slot")}
            </h2>

            <div className="cal-drawer-header-sub">
              <i className="ti ti-clock" aria-hidden="true" />
              <span>Scheduled for {formatDatetime(event.scheduledAt)}</span>
            </div>
          </div>

          <button type="button" className="modal-close-btn" onClick={onClose} aria-label="Close">
            <i className="ti ti-x" />
          </button>
        </div>

        {/* Drawer Clean Scrollable Body */}
        <div className="cal-drawer-body" ref={drawerBodyRef}>
          {/* Frameless Facebook Post Content */}
          {!isCrossInstitutionIsolated && (
            <div className="cal-post-flow">
              <div className="cal-post-header">
                <div className="cal-post-header-author">
                  <div className="cal-post-avatar">
                    <i className="ti ti-brand-facebook" />
                  </div>
                  <div>
                    <strong className="cal-post-author-name">{event.institutionName}</strong>
                    <span className="cal-post-sub-meta">
                      <i className="ti ti-world" /> Facebook Page Post · {formatDatetime(event.scheduledAt)}
                    </span>
                  </div>
                </div>

                {caption && (
                  <button
                    type="button"
                    className="cal-post-copy-btn"
                    onClick={() => handleCopyCaption(caption)}
                    title="Copy caption text"
                  >
                    <i className={copied ? "ti ti-check" : "ti ti-copy"} />
                    <span>{copied ? "Copied" : "Copy"}</span>
                  </button>
                )}
              </div>

              {/* Post Caption */}
              <div className="cal-post-caption">
                {detailLoading ? (
                  <div className="cal-loading-shimmer" style={{ height: "48px" }} />
                ) : caption ? (
                  <p className="cal-post-text">{caption}</p>
                ) : (
                  <p className="cal-post-empty-text">No caption attached to this submission.</p>
                )}
              </div>

              {/* Post Media Attachment */}
              <div className="cal-post-media">
                <CalendarMediaPreview
                  assets={mediaAssets}
                  loading={detailLoading}
                  error={detailError}
                />
              </div>
            </div>
          )}

          {/* Minimal Contributor Metadata Footer */}
          {!isCrossInstitutionIsolated && (event.contributorName || submissionDetail?.contributorEmail) && (
            <div className="cal-meta-strip">
              <span><i className="ti ti-user" /> Contributor: <strong>{event.contributorName || submissionDetail?.contributorEmail}</strong></span>
              {event.publishedAt && (
                <span><i className="ti ti-circle-check" /> Published: <strong>{formatDatetime(event.publishedAt)}</strong></span>
              )}
            </div>
          )}

          {isCrossInstitutionIsolated && (
            <div className="cal-private-callout">
              <i className="ti ti-shield-lock" aria-hidden="true" />
              <div>
                <strong>Cross-Institution Privacy</strong>
                <p>Caption, media attachments, and contributor identity are restricted to members of {event.institutionName}.</p>
              </div>
            </div>
          )}
        </div>

        {/* Drawer Sticky Footer Toolbar */}
        <div className="cal-drawer-footer">
          {isPendingApproval && isAdmin && (
            <a className="cal-drawer-cta-btn" href="/validation/queue">
              <i className="ti ti-checklist" aria-hidden="true" />
              <span>Open in Approval Queue</span>
              <i className="ti ti-arrow-right" aria-hidden="true" />
            </a>
          )}

          {isPublished && (
            <div className="cal-drawer-footer-actions">
              <button
                type="button"
                className="notif-btn notif-btn-ghost notif-btn-sm"
                onClick={onClose}
              >
                <span>Close Details</span>
              </button>
            </div>
          )}

          {!isPendingApproval && !isPublished && (
            <button
              type="button"
              className="notif-btn notif-btn-ghost notif-btn-sm"
              style={{ width: "100%", justifyContent: "center" }}
              onClick={onClose}
            >
              <span>Close</span>
            </button>
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
      <div className="cal-media-loading-skeleton" aria-label="Loading media preview">
        <div className="dc-dot-triangle-container" style={{ padding: "30px 0" }}>
          <div className="loader-dots" />
        </div>
      </div>
    );
  }

  if (assets.length > 0) {
    return (
      <div className="cal-media-gallery">
        {assets.map((asset, index) => (
          <figure className="cal-media-tile" key={asset.id}>
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
            <figcaption className="cal-media-tile-badge">
              <i className={isVideoAsset(asset) ? "ti ti-video" : "ti ti-photo"} />
              <span>{assets.length > 1 ? `${index + 1} of ${assets.length}` : isVideoAsset(asset) ? "Video" : "Image"}</span>
            </figcaption>
          </figure>
        ))}
      </div>
    );
  }

  return (
    <div className="cal-post-no-media">
      <i className={error ? "ti ti-lock" : "ti ti-photo-off"} aria-hidden="true" />
      <span>{error ? "Media preview is unavailable for this item." : "No media attachments."}</span>
    </div>
  );
}

function isVideoAsset(asset: SavedMediaAsset) {
  const type = asset.fileType?.toLowerCase() || "";
  return ["mp4", "mov", "webm", "video"].some((value) => type.includes(value));
}

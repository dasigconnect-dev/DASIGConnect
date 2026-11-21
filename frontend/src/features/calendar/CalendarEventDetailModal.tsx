import type { CalendarEvent } from "../../api/calendarApi";
import { getSubmission } from "../../api/submissionApi";
import type { SavedMediaAsset, SubmissionSummary } from "../../api/submissionApi";
import type { User } from "../../types/auth.types";
import OptimizedImage, { canTransformImageType } from "../../components/media/OptimizedImage";
import { visibleStatusColor, visibleStatusLabel } from "./calendarStatus";
import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { useToast } from "../../context/ToastContext";
import { authenticatedQueryMeta } from "../../lib/queryClient";
import { queryKeys } from "../../lib/queryKeys";
import "../../styles/calendar.css";

const DEFAULT_FACEBOOK_PAGE_URL = "https://www.facebook.com/DostDasig";

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

function formatEventDate(value: string) {
  const [year, month, day] = value.split("-").map(Number);
  if (year && month && day) {
    return new Date(year, month - 1, day).toLocaleDateString("en-PH", {
      month: "long",
      day: "numeric",
      year: "numeric",
    });
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return parsed.toLocaleDateString("en-PH", {
    month: "long",
    day: "numeric",
    year: "numeric",
  });
}

function resolveFacebookPostUrl(
  publishedManualUrl?: string | null,
  platformPostId?: string | null,
): string | null {
  const manual = publishedManualUrl?.trim();
  if (manual && (manual.startsWith("https://") || manual.startsWith("http://"))) {
    return manual;
  }
  const postId = platformPostId?.trim();
  if (postId) {
    return `https://www.facebook.com/${postId.includes("_") ? postId.replace("_", "/posts/") : postId}`;
  }
  return null;
}

interface CalendarEventDetailModalProps {
  event: CalendarEvent | null;
  user: User;
  onClose: () => void;
}

function userScope(user: User) {
  return user.id ?? user.email.trim().toLowerCase();
}

export default function CalendarEventDetailModal({
  event,
  user,
  onClose,
}: CalendarEventDetailModalProps) {
  const toast = useToast();
  const drawerBodyRef = useRef<HTMLDivElement>(null);
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
  const detailQuery = useQuery<SubmissionSummary>({
    queryKey: queryKeys.submissions.editorDetail({
      role: user.role,
      userId: userScope(user),
      institutionId: event?.institutionId ?? user.institutionId ?? null,
      submissionId: event?.id ?? "",
    }),
    queryFn: ({ signal }) => getSubmission(event!.id, signal).then((response) => response.data),
    enabled: Boolean(event && !isCrossInstitutionIsolated),
    staleTime: 60_000,
    meta: authenticatedQueryMeta,
  });
  const submissionDetail = event && !isCrossInstitutionIsolated ? detailQuery.data ?? null : null;
  const detailLoading = Boolean(event && !isCrossInstitutionIsolated && (detailQuery.isLoading || detailQuery.isFetching));
  const detailError = Boolean(event && !isCrossInstitutionIsolated && detailQuery.isError);

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
  const isPublished =
    rawStatus === "published" ||
    rawStatus === "published_manual" ||
    rawStatus === "admin_direct_post";
  const isAdmin = user.role === "moderator" || user.role === "admin";
  const eventDate = submissionDetail?.eventDate ?? event.eventDate ?? null;
  const authorName = event.contributorName || submissionDetail?.contributorEmail || null;
  const facebookPostUrl = resolveFacebookPostUrl(
    submissionDetail?.publishedManualUrl ?? event.publishedManualUrl,
    submissionDetail?.platformPostId ?? event.platformPostId,
  );
  const canOpenInAppSubmission = !isCrossInstitutionIsolated && (isAdmin || event.mine);

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
              {event.locked && !isPublished && (
                <span className="cal-drawer-lock-pill" title="Slot permanently locked">
                  <i className="ti ti-lock" aria-hidden="true" />
                  Locked
                </span>
              )}
            </div>

            <h2 className="cal-drawer-title">
              {isCrossInstitutionIsolated ? "Reserved publishing slot" : (event.title ?? "Reserved publishing slot")}
            </h2>

            {!isCrossInstitutionIsolated && eventDate ? (
              <div className="cal-drawer-header-sub">
                <i className="ti ti-calendar-event" aria-hidden="true" />
                <span>Event date: {formatEventDate(eventDate)}</span>
              </div>
            ) : isCrossInstitutionIsolated ? (
              <div className="cal-drawer-header-sub">
                <i className="ti ti-clock" aria-hidden="true" />
                <span>Reserved slot · {formatDatetime(event.scheduledAt)}</span>
              </div>
            ) : null}
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
                      <i className="ti ti-world" />{" "}
                      {isPublished
                        ? `Published · ${formatDatetime(event.publishedAt ?? event.scheduledAt)}`
                        : `Scheduled for ${formatDatetime(event.scheduledAt)}`}
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
                  onNavigate={onClose}
                />
              </div>
            </div>
          )}

          {/* Minimal Author Metadata Footer */}
          {!isCrossInstitutionIsolated && authorName && (
            <div className="cal-meta-strip">
              <span><i className="ti ti-user" /> Author: <strong>{authorName}</strong></span>
            </div>
          )}

          {isCrossInstitutionIsolated && (
            <div className="cal-private-callout">
              <i className="ti ti-shield-lock" aria-hidden="true" />
              <div>
                <strong>Cross-Institution Privacy</strong>
                <p>Caption, media attachments, and author identity are restricted to members of {event.institutionName}.</p>
              </div>
            </div>
          )}
        </div>

        {/* Drawer Sticky Footer Toolbar */}
        <div className="cal-drawer-footer">
          {isPendingApproval && isAdmin ? (
            <Link
              className="cal-drawer-cta-btn"
              to={`/queue?submissionId=${encodeURIComponent(event.id)}`}
              onClick={onClose}
            >
              <i className="ti ti-checklist" aria-hidden="true" />
              <span>Open in Approval Queue</span>
              <i className="ti ti-arrow-right" aria-hidden="true" />
            </Link>
          ) : isPublished && facebookPostUrl ? (
            <div className="cal-drawer-footer-actions">
              {canOpenInAppSubmission && (
                <Link
                  className="cal-drawer-cta-btn cal-drawer-cta-btn--secondary"
                  to={`/submissions/${encodeURIComponent(event.id)}`}
                  onClick={onClose}
                >
                  <i className="ti ti-file-text" aria-hidden="true" />
                  <span>View Submission</span>
                </Link>
              )}
              <a
                className="cal-drawer-cta-btn"
                href={facebookPostUrl}
                target="_blank"
                rel="noopener noreferrer"
              >
                <i className="ti ti-brand-facebook" aria-hidden="true" />
                <span>View on Facebook</span>
                <i className="ti ti-external-link" aria-hidden="true" />
              </a>
            </div>
          ) : canOpenInAppSubmission ? (
            <Link
              className="cal-drawer-cta-btn"
              to={`/submissions/${encodeURIComponent(event.id)}`}
              onClick={onClose}
            >
              <i className="ti ti-external-link" aria-hidden="true" />
              <span>View Post</span>
              <i className="ti ti-arrow-right" aria-hidden="true" />
            </Link>
          ) : isPublished ? (
            <a
              className="cal-drawer-cta-btn"
              href={DEFAULT_FACEBOOK_PAGE_URL}
              target="_blank"
              rel="noopener noreferrer"
            >
              <i className="ti ti-brand-facebook" aria-hidden="true" />
              <span>View on Facebook Page</span>
              <i className="ti ti-external-link" aria-hidden="true" />
            </a>
          ) : (
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
  onNavigate,
}: {
  assets: SavedMediaAsset[];
  loading: boolean;
  error: boolean;
  onNavigate: () => void;
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
    const allAssetIdsParam =
      assets.length > 1
        ? `&assets=${encodeURIComponent(assets.map((a) => a.id).join(","))}`
        : "";
    return (
      <div className="cal-media-gallery">
        {assets.map((asset, index) => {
          const libraryHref = `/media-repository?asset=${encodeURIComponent(asset.id)}${allAssetIdsParam}`;
          return (
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
                <Link
                  className="cal-media-tile-img-wrap"
                  to={libraryHref}
                  onClick={onNavigate}
                  title={`View ${asset.fileName || "photo"} in Media Library`}
                >
                  <OptimizedImage
                    src={asset.storageUrl}
                    alt={asset.fileName || `Media attachment ${index + 1}`}
                    width={360}
                    height={270}
                    sizes="(max-width: 768px) 100vw, 260px"
                    candidateWidths={[260, 360, 520]}
                    transform={canTransformImageType(asset.fileType)}
                    loading="lazy"
                  />
                </Link>
              )}
              <figcaption className="cal-media-tile-badge">
                <i className={isVideoAsset(asset) ? "ti ti-video" : "ti ti-photo"} />
                <span>{assets.length > 1 ? `${index + 1} of ${assets.length}` : isVideoAsset(asset) ? "Video" : "Image"}</span>
              </figcaption>
              <Link
                className="cal-media-tile-link"
                to={libraryHref}
                onClick={onNavigate}
                title={`View ${asset.fileName || "media"} in Media Library`}
              >
                <span>View in Library</span>
                <i className="ti ti-arrow-up-right" aria-hidden="true" />
              </Link>
            </figure>
          );
        })}
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

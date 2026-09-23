import { useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import OptimizedImage, { canTransformImageType } from "../../../components/media/OptimizedImage";
import FacebookPreviewCard from "../../../components/facebook/FacebookPreviewCard";
import type { FacebookPreviewMediaItem } from "../../../types/facebook";
import type { SubmissionMediaItem } from "../../../types/media";
import type { FormState } from "../types";
import { formatRevisionRemarksForDisplay } from "../utils/revisionComments";
import "./SubmissionReadOnlyView.css";

interface FacebookPreviewData {
  pageName: string;
  pageAvatarUrl?: string;
  publishDate?: string;
  caption: string;
  mediaItems: FacebookPreviewMediaItem[];
}

interface SubmissionReadOnlyBodyProps {
  form: FormState;
  scheduledAt?: string;
  mediaItems: SubmissionMediaItem[];
  captionHashtags: string[];
  mediaTags: string[];
  facebookPreview: FacebookPreviewData;
  activeMediaIndex: number;
  onMediaIndexChange: (index: number) => void;
  /** Shown when the submission was rejected. */
  rejectionReason?: string | null;
  /** Shown when the submission needs revision. */
  revisionNotes?: string | null;
  /** Callback to transition from read-only rejected view into editing mode. */
  onEditRejected?: () => void;
}

type ReadOnlyTab = "details" | "preview";

/**
 * Static, non-editable presentation of a submission that has moved beyond draft.
 * Deliberately does not reuse the editor inputs so it never reads as editable:
 * no field borders, no template picker, no media recommendations.
 */
export default function SubmissionReadOnlyBody({
  form,
  scheduledAt,
  mediaItems,
  captionHashtags,
  mediaTags,
  facebookPreview,
  activeMediaIndex,
  onMediaIndexChange,
  rejectionReason,
  revisionNotes,
  onEditRejected,
}: SubmissionReadOnlyBodyProps) {
  const [tab, setTab] = useState<ReadOnlyTab>("details");

  const isRejected = form.status === "rejected";
  const needsRevision = form.status === "needs_revision";
  const feedbackText = isRejected
    ? rejectionReason
    : needsRevision
      ? formatRevisionRemarksForDisplay(revisionNotes)
      : null;

  // A library-picked asset keeps its original album on submit (only fresh
  // uploads are filed into the post's album), so the two can differ.
  const postAlbum = form.albumName.trim().toLowerCase();
  const isInOtherAlbum = (item: SubmissionMediaItem) =>
    Boolean(item.albumName && item.albumName.trim().toLowerCase() !== postAlbum);
  const hasMediaInOtherAlbums = mediaItems.some(isInOtherAlbum);

  const videoCount = mediaItems.filter((item) => item.mediaType === "video").length;
  const imageCount = mediaItems.length - videoCount;

  return (
    <>
      <div className="sub-ro-switch" role="tablist" aria-label="Submission view">
        <TabButton active={tab === "details"} icon="ti-file-text" onClick={() => setTab("details")}>
          Submission details
        </TabButton>
        <TabButton active={tab === "preview"} icon="ti-brand-facebook" onClick={() => setTab("preview")}>
          Facebook Preview
        </TabButton>
      </div>

      {tab === "preview" ? (
        <div className="sub-ro-preview" role="tabpanel" aria-label="Facebook Preview">
          <FacebookPreviewCard
            pageName={facebookPreview.pageName}
            pageAvatarUrl={facebookPreview.pageAvatarUrl}
            publishDate={facebookPreview.publishDate}
            caption={facebookPreview.caption}
            mediaItems={facebookPreview.mediaItems}
            activeMediaIndex={activeMediaIndex}
            onMediaIndexChange={onMediaIndexChange}
            size="large"
          />
        </div>
      ) : (
        <div className="sub-ro-stack" role="tabpanel" aria-label="Submission details">
          {(isRejected || needsRevision) && (
            <section
              className={`sub-ro-card sub-ro-feedback ${isRejected ? "is-rejected" : "is-revision"}`}
              role={isRejected ? "alert" : "status"}
            >
              <div className="sub-ro-feedback-head">
                <span className="sub-ro-feedback-icon" aria-hidden>
                  <i className={isRejected ? "ti ti-circle-x" : "ti ti-message-2-exclamation"} />
                </span>
                <div>
                  <h2 className="sub-ro-feedback-title">
                    {isRejected ? "Reason for rejection" : "Requested changes"}
                  </h2>
                  <p className="sub-ro-feedback-sub">
                    {isRejected
                      ? "A reviewer declined this post. Address the feedback, then resubmit."
                      : "A reviewer asked for changes before this post can be approved."}
                  </p>
                </div>
              </div>
              {feedbackText && feedbackText.trim() ? (
                <blockquote className="sub-ro-feedback-body">{feedbackText}</blockquote>
              ) : (
                <p className="sub-ro-empty">
                  {isRejected
                    ? "No reason was recorded. Check your email for details."
                    : "No notes were recorded. Check your email for details."}
                </p>
              )}
              {isRejected && onEditRejected && (
                <div className="sub-ro-feedback-actions">
                  <button type="button" className="sub-ro-edit-rejected-btn" onClick={onEditRejected}>
                    <i className="ti ti-pencil" aria-hidden />
                    Edit &amp; Resubmit
                  </button>
                </div>
              )}
            </section>
          )}

          <section className="sub-ro-card sub-ro-hero" aria-labelledby="sub-ro-event-title">
            <span className="sub-ro-eyebrow">Event</span>
            <h2 id="sub-ro-event-title" className="sub-ro-hero-title">
              {form.eventTitle || <span className="sub-ro-empty">Untitled event</span>}
            </h2>
            <dl className="sub-ro-facts">
              <Fact icon="ti-calendar-event" label="Event date">
                {form.eventDate ? formatLongDate(form.eventDate) : "—"}
              </Fact>
              <Fact icon={form.fastTrack ? "ti-bolt" : "ti-clock"} label="Publishing">
                {form.fastTrack ? (
                  <span className="sub-ro-live-pill">
                    <i className="ti ti-bolt" aria-hidden />
                    Live event
                  </span>
                ) : scheduledAt ? (
                  formatDateTime(scheduledAt)
                ) : (
                  "Not scheduled"
                )}
              </Fact>
              <Fact icon="ti-photo" label="Media">
                {mediaItems.length === 0 ? "None" : describeMediaCount(imageCount, videoCount)}
              </Fact>
            </dl>
          </section>

          <section className="sub-ro-card" aria-labelledby="sub-ro-caption-title">
            <CardHead id="sub-ro-caption-title" icon="ti-align-left" title="Caption" />
            {form.caption.trim() ? (
              <p className="sub-ro-caption">{form.caption}</p>
            ) : (
              <p className="sub-ro-empty">No caption.</p>
            )}
            <div className="sub-ro-subfield">
              <span className="sub-ro-label">Hashtags</span>
              <Chips values={captionHashtags} empty="No hashtags." tone="accent" />
            </div>
          </section>

          <section className="sub-ro-card" aria-labelledby="sub-ro-media-title">
            <CardHead
              id="sub-ro-media-title"
              icon="ti-photo"
              title="Media"
              count={mediaItems.length > 0 ? mediaItems.length : undefined}
              hint={mediaItems.length > 1 ? "Shown in publishing order" : undefined}
            />
            {mediaItems.length > 0 ? (
              <ol className="sub-ro-media-grid">
                {mediaItems.map((item, index) => (
                  <li className="sub-ro-media" key={item.clientId}>
                    <div className="sub-ro-media-frame">
                      {item.mediaType === "video" ? (
                        <video src={item.previewUrl} muted playsInline preload="metadata" />
                      ) : (
                        <OptimizedImage
                          src={item.previewUrl}
                          alt={item.fileName}
                          width={320}
                          height={320}
                          sizes="(max-width: 768px) 50vw, 220px"
                          candidateWidths={[220, 320, 440]}
                          transform={item.source !== "upload" && canTransformImageType(item.fileName.split(".").pop())}
                        />
                      )}
                      <span className="sub-ro-media-order" aria-label={`Position ${index + 1}`}>
                        {index + 1}
                      </span>
                      {item.mediaType === "video" && (
                        <span className="sub-ro-media-kind">
                          <i className="ti ti-player-play-filled" aria-hidden />
                          Video
                        </span>
                      )}
                    </div>
                    <div className="sub-ro-media-meta">
                      <span className="sub-ro-media-name" title={item.fileName}>
                        {item.fileName}
                      </span>
                      {isInOtherAlbum(item) && (
                        <span className="sub-ro-media-album" title={`Stored in the "${item.albumName}" album`}>
                          <i className="ti ti-folder" aria-hidden />
                          <span>{item.albumName}</span>
                        </span>
                      )}
                      {item.assetId && (
                        <Link className="sub-ro-media-link" to={`/media-repository?asset=${item.assetId}`}>
                          View in library
                          <i className="ti ti-arrow-up-right" aria-hidden />
                        </Link>
                      )}
                    </div>
                  </li>
                ))}
              </ol>
            ) : (
              <p className="sub-ro-empty">No media attached.</p>
            )}

            <div className="sub-ro-filing">
              <span className="sub-ro-filing-title">
                <i className="ti ti-folders" aria-hidden />
                Library filing
              </span>
              <dl className="sub-ro-dl">
                <Row label="Album">
                  <span className="sub-ro-album-name">{form.albumName || "—"}</span>
                  {hasMediaInOtherAlbums && (
                    <span className="sub-ro-album-note">
                      <i className="ti ti-info-circle" aria-hidden />
                      Media reused from the library stays in its original album, shown under each file.
                    </span>
                  )}
                </Row>
                <Row label="Media tags">
                  <Chips values={mediaTags} empty="—" />
                </Row>
              </dl>
            </div>
          </section>
        </div>
      )}
    </>
  );
}

function TabButton({
  active,
  icon,
  onClick,
  children,
}: {
  active: boolean;
  icon: string;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      role="tab"
      aria-selected={active}
      className={`sub-ro-switch-btn${active ? " is-active" : ""}`}
      onClick={onClick}
    >
      <i className={`ti ${icon}`} aria-hidden />
      {children}
    </button>
  );
}

function CardHead({
  id,
  icon,
  title,
  count,
  hint,
}: {
  id: string;
  icon: string;
  title: string;
  count?: number;
  hint?: string;
}) {
  return (
    <div className="sub-ro-card-head">
      <h2 id={id} className="sub-ro-card-title">
        <span className="sub-ro-card-icon" aria-hidden>
          <i className={`ti ${icon}`} />
        </span>
        {title}
        {count !== undefined && <span className="sub-ro-count">{count}</span>}
      </h2>
      {hint && <span className="sub-ro-card-hint">{hint}</span>}
    </div>
  );
}

function Fact({ icon, label, children }: { icon: string; label: string; children: ReactNode }) {
  return (
    <div className="sub-ro-fact">
      <span className="sub-ro-fact-icon" aria-hidden>
        <i className={`ti ${icon}`} />
      </span>
      <div className="sub-ro-fact-text">
        <dt>{label}</dt>
        <dd>{children}</dd>
      </div>
    </div>
  );
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="sub-ro-row">
      <dt>{label}</dt>
      <dd>{children}</dd>
    </div>
  );
}

function Chips({ values, empty, tone }: { values: string[]; empty: string; tone?: "accent" }) {
  if (values.length === 0) {
    return <span className="sub-ro-empty">{empty}</span>;
  }
  return (
    <ul className={`sub-ro-chips${tone === "accent" ? " is-accent" : ""}`}>
      {values.map((value) => (
        <li className="sub-ro-chip" key={value}>
          {value}
        </li>
      ))}
    </ul>
  );
}

function describeMediaCount(images: number, videos: number) {
  const parts: string[] = [];
  if (images > 0) parts.push(`${images} ${images === 1 ? "image" : "images"}`);
  if (videos > 0) parts.push(`${videos} ${videos === 1 ? "video" : "videos"}`);
  return parts.join(" · ");
}

function formatDateTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat(undefined, {
    weekday: "short",
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(date);
}

function formatLongDate(value: string) {
  const [year, month, day] = value.split("-").map(Number);
  if (!year || !month || !day) return value;
  const date = new Date(year, month - 1, day);
  return new Intl.DateTimeFormat(undefined, {
    month: "long",
    day: "numeric",
    year: "numeric",
  }).format(date);
}

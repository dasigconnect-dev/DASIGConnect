import { useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import FacebookPreviewCard from "../../../components/facebook/FacebookPreviewCard";
import type { FacebookPreviewMediaItem } from "../../../types/facebook";
import type { SubmissionMediaItem } from "../../../types/media";
import type { FormState } from "../types";
import { formatDateTime, formatLongDate } from "../utils";

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
}

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
}: SubmissionReadOnlyBodyProps) {
  const [tab, setTab] = useState<"details" | "preview">("details");

  const schedule = form.fastTrack
    ? "Live event — no scheduled slot"
    : scheduledAt
      ? formatDateTime(scheduledAt)
      : "Not scheduled";

  return (
    <>
      <div className="sub-ro-tabs" role="tablist" aria-label="Submission view">
        <button
          type="button"
          role="tab"
          aria-selected={tab === "details"}
          className={`sub-ro-tab${tab === "details" ? " is-active" : ""}`}
          onClick={() => setTab("details")}
        >
          Submission details
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={tab === "preview"}
          className={`sub-ro-tab${tab === "preview" ? " is-active" : ""}`}
          onClick={() => setTab("preview")}
        >
          Facebook Preview
        </button>
      </div>

      {tab === "preview" ? (
        <section className="sub-ro-card">
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
        </section>
      ) : (
        <>
      <section className="sub-ro-card">
        <h2 className="sub-ro-card-title">Post Details</h2>
        <dl className="sub-ro-dl">
          <Row label="Event title" value={form.eventTitle || "—"} />
          <Row
            label="Event date"
            value={form.eventDate ? formatLongDate(form.eventDate) : "—"}
          />
        </dl>
        <div className="sub-ro-field">
          <span className="sub-ro-label">Caption</span>
          {form.caption.trim() ? (
            <p className="sub-ro-caption">{form.caption}</p>
          ) : (
            <p className="sub-ro-value is-empty">No caption.</p>
          )}
        </div>
        <div className="sub-ro-field">
          <span className="sub-ro-label">Tags</span>
          <Chips values={captionHashtags} empty="No tags." />
        </div>
      </section>

      <section className="sub-ro-card">
        <h2 className="sub-ro-card-title">
          Media
          {mediaItems.length > 0 && (
            <span className="sub-ro-count">{mediaItems.length}</span>
          )}
        </h2>
        {mediaItems.length > 0 ? (
          <div className="sub-ro-media-grid">
            {mediaItems.map((item) => (
              <figure className="sub-ro-media" key={item.clientId}>
                {item.mediaType === "video" ? (
                  <video
                    src={item.previewUrl}
                    muted
                    playsInline
                    preload="metadata"
                  />
                ) : (
                  <img src={item.previewUrl} alt={item.fileName} loading="lazy" />
                )}
                <figcaption>
                  <span className="sub-ro-media-name">{item.fileName}</span>
                  {item.assetId && (
                    <Link
                      className="sub-ro-media-link"
                      to={`/media-repository?asset=${item.assetId}`}
                    >
                      <i className="ti ti-external-link" aria-hidden />
                      View in library
                    </Link>
                  )}
                </figcaption>
              </figure>
            ))}
          </div>
        ) : (
          <p className="sub-ro-value is-empty">No media attached.</p>
        )}
      </section>

      <section className="sub-ro-card">
        <h2 className="sub-ro-card-title">Schedule</h2>
        <dl className="sub-ro-dl">
          <Row label="Album" value={form.albumName || "—"} />
          <Row label="Media tags" value={<Chips values={mediaTags} empty="—" />} />
          <Row label="Publishing" value={schedule} />
        </dl>
      </section>
        </>
      )}
    </>
  );
}

function Row({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="sub-ro-row">
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}

function Chips({ values, empty }: { values: string[]; empty: string }) {
  if (values.length === 0) {
    return <span className="sub-ro-value is-empty">{empty}</span>;
  }
  return (
    <div className="sub-ro-chips">
      {values.map((value) => (
        <span className="sub-ro-chip" key={value}>
          {value}
        </span>
      ))}
    </div>
  );
}

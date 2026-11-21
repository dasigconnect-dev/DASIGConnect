import { useState } from "react";
import type { FacebookPreviewMediaItem } from "../../types/facebook";
import { useWatermarkConfiguration } from "../../hooks/useWatermarkConfiguration";
import FacebookPreviewCaption from "./FacebookPreviewCaption";
import FacebookPreviewHeader from "./FacebookPreviewHeader";
import FacebookPreviewMedia from "./FacebookPreviewMedia";
import "../../styles/submission.css";

interface FacebookPreviewCardProps {
  pageName: string;
  pageAvatarUrl?: string;
  publishDate?: string;
  publishLabel?: string;
  caption: string;
  mediaItems: FacebookPreviewMediaItem[];
  activeMediaIndex?: number;
  isLoading?: boolean;
  error?: string;
  onOpen?: () => void;
  onMediaIndexChange?: (index: number) => void;
  showWatermark?: boolean;
  onToggleWatermark?: () => void;
  size?: "compact" | "large";
}

export default function FacebookPreviewCard({
  pageName,
  pageAvatarUrl,
  publishDate,
  publishLabel,
  caption,
  mediaItems,
  activeMediaIndex = 0,
  isLoading = false,
  error,
  onOpen,
  onMediaIndexChange,
  showWatermark,
  onToggleWatermark,
  size = "compact",
}: FacebookPreviewCardProps) {
  const watermarkQuery = useWatermarkConfiguration();
  const watermarkConfig = watermarkQuery.data ?? null;
  const [internalShowWatermark, setInternalShowWatermark] = useState(true);
  const effectiveShowWatermark =
    showWatermark !== undefined ? showWatermark : internalShowWatermark;
  const handleToggleWatermark =
    onToggleWatermark ?? (() => setInternalShowWatermark((prev) => !prev));
  const hasWatermarkableImage = mediaItems.some(
    (item) => item.type === "image" && !item.skipWatermark,
  );
  const canToggleWatermark = Boolean(watermarkConfig?.enabled && hasWatermarkableImage);

  const cardClassName = `fb-preview-card fb-preview-${size}${
    onOpen ? " fb-preview-trigger" : ""
  }`;

  if (isLoading) {
    return (
      <div className={`${cardClassName} fb-preview-loading`} aria-live="polite">
        <div className="fb-preview-skeleton head" />
        <div className="fb-preview-skeleton media" />
        <div className="fb-preview-skeleton text" />
      </div>
    );
  }

  const content = (
    <>
      {error && (
        <div className="fb-preview-error" role="status">
          {error}
        </div>
      )}
      <FacebookPreviewHeader
        pageName={pageName}
        pageAvatarUrl={pageAvatarUrl}
        publishDate={publishDate}
        publishLabel={publishLabel}
        showWatermarkToggle={canToggleWatermark}
        showWatermark={effectiveShowWatermark}
        onToggleWatermark={handleToggleWatermark}
      />
      {size === "large" && <FacebookPreviewCaption caption={caption} />}
      <FacebookPreviewMedia
        mediaItems={mediaItems}
        activeIndex={activeMediaIndex}
        onActiveIndexChange={onMediaIndexChange}
        size={size}
        showWatermark={effectiveShowWatermark}
      />
      {size !== "large" && <FacebookPreviewCaption caption={caption} />}
      {size === "large" && (
        <>
        <div className="fb-preview-engagement" aria-hidden="true">
          <div className="fb-preview-engagement-actions">
            <span><i className="ti ti-thumb-up" /> Like</span>
            <span><i className="ti ti-message-circle" /> Comment</span>
            <span><i className="ti ti-share-3" /> Share</span>
          </div>
          <div className="fb-preview-comment-row">
            <span className="fb-preview-comment-avatar">
              <i className="ti ti-brand-facebook" />
            </span>
            <div className="fb-preview-comment-box">
              <span>Comment as {pageName}</span>
              <i className="ti ti-send" />
            </div>
          </div>
        </div>
        </>
      )}
      {onOpen && (
        <div className="fb-preview-affordance">
          <span>View full preview</span>
          <i className="ti ti-arrow-up-right" aria-hidden="true" />
        </div>
      )}
    </>
  );

  if (onOpen) {
    return (
      <article
        className={cardClassName}
        role="button"
        tabIndex={0}
        aria-label="Open full Facebook preview"
        onClick={onOpen}
        onKeyDown={(event) => {
          if (event.key === "Enter" || event.key === " ") {
            event.preventDefault();
            onOpen();
          }
        }}
      >
        {content}
      </article>
    );
  }

  return (
    <article className={cardClassName} aria-label="Facebook post preview">
      {content}
    </article>
  );
}

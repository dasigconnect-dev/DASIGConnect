import type { FacebookPreviewMediaItem } from "../../types/facebook";
import FacebookPreviewCaption from "./FacebookPreviewCaption";
import FacebookPreviewHeader from "./FacebookPreviewHeader";
import FacebookPreviewMedia from "./FacebookPreviewMedia";

interface FacebookPreviewCardProps {
  pageName: string;
  pageAvatarUrl?: string;
  publishDate?: string;
  caption: string;
  mediaItems: FacebookPreviewMediaItem[];
  activeMediaIndex?: number;
  isLoading?: boolean;
  error?: string;
  onOpen?: () => void;
  onMediaIndexChange?: (index: number) => void;
  size?: "compact" | "large";
}

export default function FacebookPreviewCard({
  pageName,
  pageAvatarUrl,
  publishDate,
  caption,
  mediaItems,
  activeMediaIndex = 0,
  isLoading = false,
  error,
  onOpen,
  onMediaIndexChange,
  size = "compact",
}: FacebookPreviewCardProps) {
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
      />
      <FacebookPreviewMedia
        mediaItems={mediaItems}
        activeIndex={activeMediaIndex}
        onActiveIndexChange={onMediaIndexChange}
        size={size}
      />
      <FacebookPreviewCaption caption={caption} />
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

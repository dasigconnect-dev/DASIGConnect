import { useRef } from "react";
import type { FacebookPreviewMediaItem } from "../../types/facebook";
import FacebookPreviewEmptyState from "./FacebookPreviewEmptyState";

interface FacebookPreviewMediaCarouselProps {
  mediaItems: FacebookPreviewMediaItem[];
  activeIndex: number;
  onActiveIndexChange?: (index: number) => void;
  size?: "compact" | "large";
}

export default function FacebookPreviewMediaCarousel({
  mediaItems,
  activeIndex,
  onActiveIndexChange,
  size = "compact",
}: FacebookPreviewMediaCarouselProps) {
  const touchStartX = useRef<number | null>(null);
  const currentIndex = clampIndex(activeIndex, mediaItems.length);
  const current = mediaItems[currentIndex];
  const hasMultiple = mediaItems.length > 1;

  function goTo(index: number) {
    if (!onActiveIndexChange || mediaItems.length === 0) return;
    onActiveIndexChange((index + mediaItems.length) % mediaItems.length);
  }

  function handlePointerDown(event: React.PointerEvent<HTMLDivElement>) {
    touchStartX.current = event.clientX;
  }

  function handlePointerUp(event: React.PointerEvent<HTMLDivElement>) {
    if (touchStartX.current === null || !hasMultiple) return;
    const delta = event.clientX - touchStartX.current;
    touchStartX.current = null;
    if (Math.abs(delta) < 44) return;
    goTo(delta < 0 ? currentIndex + 1 : currentIndex - 1);
  }

  if (!current) return <FacebookPreviewEmptyState />;

  return (
    <div
      className={`fb-preview-carousel fb-preview-carousel-${size}`}
      onPointerDown={handlePointerDown}
      onPointerUp={handlePointerUp}
    >
      <div className="fb-preview-media">
        {current.type === "video" ? (
          <video
            src={current.url}
            muted
            playsInline
            preload="metadata"
            aria-label={current.alt}
            controls={size === "large"}
          />
        ) : current.type === "image" ? (
          <img src={current.url} alt={current.alt} />
        ) : (
          <FacebookPreviewEmptyState />
        )}
        {current.type === "video" && (
          <div className="fb-preview-video-badge">
            <i className="ti ti-player-play-filled" aria-hidden="true" />
            Video
          </div>
        )}
        {hasMultiple && (
          <>
            <button
              className="fb-preview-nav prev"
              type="button"
              aria-label="Show previous media"
              onClick={(event) => {
                event.stopPropagation();
                goTo(currentIndex - 1);
              }}
            >
              <i className="ti ti-chevron-left" aria-hidden="true" />
            </button>
            <button
              className="fb-preview-nav next"
              type="button"
              aria-label="Show next media"
              onClick={(event) => {
                event.stopPropagation();
                goTo(currentIndex + 1);
              }}
            >
              <i className="ti ti-chevron-right" aria-hidden="true" />
            </button>
            <div className="fb-preview-counter">
              {currentIndex + 1} / {mediaItems.length}
            </div>
          </>
        )}
      </div>
      {hasMultiple && (
        <div className="fb-preview-dots" aria-label="Media position">
          {mediaItems.map((item, index) => (
            <button
              key={item.id}
              className={index === currentIndex ? "active" : ""}
              type="button"
              aria-label={`Show media ${index + 1}`}
              onClick={(event) => {
                event.stopPropagation();
                goTo(index);
              }}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function clampIndex(index: number, length: number) {
  if (length <= 0) return 0;
  return Math.min(Math.max(index, 0), length - 1);
}

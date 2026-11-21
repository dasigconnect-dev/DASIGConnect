import { useRef, useState } from "react";
import type { FacebookPreviewMediaItem } from "../../types/facebook";
import FacebookPreviewEmptyState from "./FacebookPreviewEmptyState";
import type { WatermarkConfiguration } from "../../types/watermark.types";
import WatermarkOverlay from "../watermark/WatermarkOverlay";
import { useWatermarkConfiguration } from "../../hooks/useWatermarkConfiguration";

interface FacebookPreviewMediaCarouselProps {
  mediaItems: FacebookPreviewMediaItem[];
  activeIndex: number;
  onActiveIndexChange?: (index: number) => void;
  size?: "compact" | "large";
  showWatermark?: boolean;
}

function FacebookPreviewImageItem({
  item,
  watermarkConfig,
  showWatermark = true,
  fillFrame = false,
  onMeasure,
}: {
  item: FacebookPreviewMediaItem;
  watermarkConfig: WatermarkConfiguration | null;
  showWatermark?: boolean;
  fillFrame?: boolean;
  onMeasure?: (width: number, height: number) => void;
}) {
  const [aspectRatio, setAspectRatio] = useState<number | null>(null);
  const [loadFailed, setLoadFailed] = useState(false);
  const [retryToken, setRetryToken] = useState(0);
  const maxHeightVar = fillFrame
    ? "var(--fb-preview-max-h, 420px)"
    : "var(--fb-preview-max-h, 148px)";

  if (loadFailed) {
    return (
      <div className="fb-preview-empty-media">
        <i className="ti ti-photo-off" aria-hidden="true" />
        <span>This media asset failed to load.</span>
        <button
          type="button"
          className="fb-preview-retry-btn"
          onClick={(e) => {
            e.stopPropagation();
            setLoadFailed(false);
            setRetryToken((n) => n + 1);
          }}
        >
          <i className="ti ti-refresh" aria-hidden="true" /> Retry
        </button>
      </div>
    );
  }

  const imageSrc =
    retryToken > 0
      ? `${item.url}${item.url.includes("?") ? "&" : "?"}retry=${retryToken}`
      : item.url;

  return (
    <div
      className="fb-preview-img-container"
      style={{
        position: "relative",
        width: aspectRatio
          ? `min(100%, calc(${maxHeightVar} * ${aspectRatio}))`
          : fillFrame
            ? "100%"
            : undefined,
        maxWidth: "100%",
        maxHeight: maxHeightVar,
        aspectRatio: aspectRatio ? `${aspectRatio}` : undefined,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
      }}
    >
      <img
        key={retryToken}
        src={imageSrc}
        alt={item.alt}
        onLoad={(e) => {
          const { naturalWidth, naturalHeight } = e.currentTarget;
          if (naturalWidth && naturalHeight) {
            setAspectRatio(naturalWidth / naturalHeight);
            onMeasure?.(naturalWidth, naturalHeight);
          }
        }}
        onError={() => setLoadFailed(true)}
        style={{
          width: "100%",
          height: "100%",
          maxHeight: maxHeightVar,
          objectFit: "contain",
          display: "block",
        }}
      />
      {showWatermark && watermarkConfig?.enabled && !item.skipWatermark && (
        <WatermarkOverlay elements={watermarkConfig.elements} />
      )}
    </div>
  );
}

function FacebookPreviewCollage({
  mediaItems,
  watermarkConfig,
  showWatermark = true,
  onSelectMedia,
}: {
  mediaItems: FacebookPreviewMediaItem[];
  watermarkConfig: WatermarkConfiguration | null;
  showWatermark?: boolean;
  onSelectMedia: (index: number) => void;
}) {
  const count = mediaItems.length;
  if (count <= 1) return null;
  const shouldRenderWatermark = (item: FacebookPreviewMediaItem) =>
    Boolean(showWatermark && watermarkConfig?.enabled && item.type === "image" && !item.skipWatermark);

  if (count === 2) {
    return (
      <div className="fb-preview-collage fb-preview-collage--2">
        {mediaItems.slice(0, 2).map((item, idx) => (
          <div
            key={item.id}
            className="fb-preview-collage-cell"
            onClick={() => onSelectMedia(idx)}
            role="button"
            tabIndex={0}
            title={`${item.alt || item.fileName || "Photo"} (Click to view full photo)`}
          >
            {item.type === "image" ? (
              <img src={item.url} alt={item.alt} />
            ) : (
              <div className="fb-preview-video-wrapper">
                <video src={item.url} muted playsInline />
                <span className="fb-preview-collage-video-badge"><i className="ti ti-video" /></span>
              </div>
            )}
            {shouldRenderWatermark(item) && (
              <WatermarkOverlay elements={watermarkConfig!.elements} />
            )}
          </div>
        ))}
      </div>
    );
  }

  if (count === 3) {
    return (
      <div className="fb-preview-collage fb-preview-collage--3">
        <div
          className="fb-preview-collage-cell fb-preview-collage-cell--featured"
          onClick={() => onSelectMedia(0)}
          role="button"
          tabIndex={0}
          title={`${mediaItems[0].alt || mediaItems[0].fileName || "Photo"} (Click to view)`}
        >
          {mediaItems[0].type === "image" ? (
            <img src={mediaItems[0].url} alt={mediaItems[0].alt} />
          ) : (
            <div className="fb-preview-video-wrapper">
              <video src={mediaItems[0].url} muted playsInline />
              <span className="fb-preview-collage-video-badge"><i className="ti ti-video" /></span>
            </div>
          )}
          {shouldRenderWatermark(mediaItems[0]) && (
            <WatermarkOverlay elements={watermarkConfig!.elements} />
          )}
        </div>
        <div className="fb-preview-collage-row">
          {mediaItems.slice(1, 3).map((item, idx) => (
            <div
              key={item.id}
              className="fb-preview-collage-cell"
              onClick={() => onSelectMedia(idx + 1)}
              role="button"
              tabIndex={0}
              title={`${item.alt || item.fileName || "Photo"} (Click to view)`}
            >
              {item.type === "image" ? (
                <img src={item.url} alt={item.alt} />
              ) : (
                <div className="fb-preview-video-wrapper">
                  <video src={item.url} muted playsInline />
                  <span className="fb-preview-collage-video-badge"><i className="ti ti-video" /></span>
                </div>
              )}
              {shouldRenderWatermark(item) && (
                <WatermarkOverlay elements={watermarkConfig!.elements} />
              )}
            </div>
          ))}
        </div>
      </div>
    );
  }

  if (count === 4) {
    return (
      <div className="fb-preview-collage fb-preview-collage--4">
        <div
          className="fb-preview-collage-cell fb-preview-collage-cell--featured"
          onClick={() => onSelectMedia(0)}
          role="button"
          tabIndex={0}
          title={`${mediaItems[0].alt || mediaItems[0].fileName || "Photo"} (Click to view)`}
        >
          {mediaItems[0].type === "image" ? (
            <img src={mediaItems[0].url} alt={mediaItems[0].alt} />
          ) : (
            <div className="fb-preview-video-wrapper">
              <video src={mediaItems[0].url} muted playsInline />
              <span className="fb-preview-collage-video-badge"><i className="ti ti-video" /></span>
            </div>
          )}
          {shouldRenderWatermark(mediaItems[0]) && (
            <WatermarkOverlay elements={watermarkConfig!.elements} />
          )}
        </div>
        <div className="fb-preview-collage-row">
          {mediaItems.slice(1, 4).map((item, idx) => (
            <div
              key={item.id}
              className="fb-preview-collage-cell"
              onClick={() => onSelectMedia(idx + 1)}
              role="button"
              tabIndex={0}
              title={`${item.alt || item.fileName || "Photo"} (Click to view)`}
            >
              {item.type === "image" ? (
                <img src={item.url} alt={item.alt} />
              ) : (
                <div className="fb-preview-video-wrapper">
                  <video src={item.url} muted playsInline />
                  <span className="fb-preview-collage-video-badge"><i className="ti ti-video" /></span>
                </div>
              )}
              {shouldRenderWatermark(item) && (
                <WatermarkOverlay elements={watermarkConfig!.elements} />
              )}
            </div>
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className="fb-preview-collage fb-preview-collage--5plus">
      <div
        className="fb-preview-collage-cell fb-preview-collage-cell--featured"
        onClick={() => onSelectMedia(0)}
        role="button"
        tabIndex={0}
        title={`${mediaItems[0].alt || mediaItems[0].fileName || "Photo"} (Click to view)`}
      >
        {mediaItems[0].type === "image" ? (
          <img src={mediaItems[0].url} alt={mediaItems[0].alt} />
        ) : (
          <div className="fb-preview-video-wrapper">
            <video src={mediaItems[0].url} muted playsInline />
            <span className="fb-preview-collage-video-badge"><i className="ti ti-video" /></span>
          </div>
        )}
        {shouldRenderWatermark(mediaItems[0]) && (
          <WatermarkOverlay elements={watermarkConfig!.elements} />
        )}
      </div>
      <div className="fb-preview-collage-row">
        {mediaItems.slice(1, 3).map((item, idx) => (
          <div
            key={item.id}
            className="fb-preview-collage-cell"
            onClick={() => onSelectMedia(idx + 1)}
            role="button"
            tabIndex={0}
            title={`${item.alt || item.fileName || "Photo"} (Click to view)`}
          >
            {item.type === "image" ? (
              <img src={item.url} alt={item.alt} />
            ) : (
              <div className="fb-preview-video-wrapper">
                <video src={item.url} muted playsInline />
                <span className="fb-preview-collage-video-badge"><i className="ti ti-video" /></span>
              </div>
            )}
            {shouldRenderWatermark(item) && (
              <WatermarkOverlay elements={watermarkConfig!.elements} />
            )}
          </div>
        ))}
        <div
          className="fb-preview-collage-cell fb-preview-collage-cell--more"
          onClick={() => onSelectMedia(3)}
          role="button"
          tabIndex={0}
          title={`View all ${count} photos`}
        >
          {mediaItems[3].type === "image" ? (
            <img src={mediaItems[3].url} alt={mediaItems[3].alt} />
          ) : (
            <div className="fb-preview-video-wrapper">
              <video src={mediaItems[3].url} muted playsInline />
            </div>
          )}
          {shouldRenderWatermark(mediaItems[3]) && (
            <WatermarkOverlay elements={watermarkConfig!.elements} />
          )}
          <div className="fb-preview-collage-more-overlay">
            +{count - 3}
          </div>
        </div>
      </div>
    </div>
  );
}

export default function FacebookPreviewMediaCarousel({
  mediaItems,
  activeIndex,
  onActiveIndexChange,
  size = "compact",
  showWatermark = true,
}: FacebookPreviewMediaCarouselProps) {
  const watermarkQuery = useWatermarkConfiguration();
  const watermarkConfig = watermarkQuery.data ?? null;
  const touchStartX = useRef<number | null>(null);
  const [internalIndex, setInternalIndex] = useState(0);
  const [viewMode, setViewMode] = useState<"grid" | "carousel">("grid");
  const hasMultiple = mediaItems.length > 1;
  const activeIdx = onActiveIndexChange !== undefined ? activeIndex : internalIndex;
  const currentIndex = clampIndex(activeIdx, mediaItems.length);
  const current = mediaItems[currentIndex];
  // Natural width/height ratio per media URL, so the large preview's frame can
  // match the media instead of letterboxing it in a fixed-height box while
  // respecting --fb-preview-max-h for non-grid images.
  const [mediaRatios, setMediaRatios] = useState<Record<string, number>>({});
  const currentRatio = current ? mediaRatios[current.url] : undefined;
  const mediaFrameStyle =
    size === "large" && currentRatio
      ? { height: "auto" }
      : undefined;

  function recordRatio(url: string, width: number, height: number) {
    if (!width || !height) return;
    const ratio = width / height;
    setMediaRatios((prev) => (prev[url] === ratio ? prev : { ...prev, [url]: ratio }));
  }

  function goTo(index: number) {
    if (mediaItems.length === 0) return;
    const nextIdx = (index + mediaItems.length) % mediaItems.length;
    setInternalIndex(nextIdx);
    onActiveIndexChange?.(nextIdx);
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
      {hasMultiple && (
        <div className="fb-preview-layout-toggle" role="group" aria-label="Media layout">
          <button
            type="button"
            className={`fb-preview-layout-btn ${viewMode === "grid" ? "active" : ""}`}
            onClick={(e) => {
              e.stopPropagation();
              setViewMode("grid");
            }}
            title="Facebook multi-photo grid layout"
          >
            <i className="ti ti-layout-grid" /> Grid
          </button>
          <button
            type="button"
            className={`fb-preview-layout-btn ${viewMode === "carousel" ? "active" : ""}`}
            onClick={(e) => {
              e.stopPropagation();
              setViewMode("carousel");
            }}
            title="Single photo with navigation arrows"
          >
            <i className="ti ti-arrows-left-right" /> Arrows
          </button>
        </div>
      )}

      {hasMultiple && viewMode === "grid" ? (
        <FacebookPreviewCollage
          mediaItems={mediaItems}
          watermarkConfig={watermarkConfig}
          showWatermark={showWatermark}
          onSelectMedia={(idx) => {
            goTo(idx);
            setViewMode("carousel");
          }}
        />
      ) : (
        <div className="fb-preview-media" style={mediaFrameStyle}>
          {current.type === "video" ? (
            <>
            <video
              src={current.url}
              muted
              playsInline
              preload="metadata"
              aria-label={current.alt}
              controls={size === "large"}
              style={
                size === "large" && currentRatio
                  ? {
                      aspectRatio: `${currentRatio}`,
                      width: `min(100%, calc(var(--fb-preview-max-h, 420px) * ${currentRatio}))`,
                      height: "auto",
                      maxHeight: "var(--fb-preview-max-h, 420px)",
                    }
                  : undefined
              }
              onLoadedMetadata={(event) =>
                recordRatio(current.url, event.currentTarget.videoWidth, event.currentTarget.videoHeight)
              }
            />
            {size === "large" && (
              <span className="fb-preview-play-overlay" aria-hidden="true">
                <i className="ti ti-player-play-filled" />
              </span>
            )}
            </>
          ) : current.type === "image" ? (
            <FacebookPreviewImageItem
              key={current.url}
              item={current}
              watermarkConfig={watermarkConfig}
              showWatermark={showWatermark}
              fillFrame={size === "large"}
              onMeasure={(width, height) => recordRatio(current.url, width, height)}
            />
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
      )}
      {hasMultiple && viewMode === "carousel" && (
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

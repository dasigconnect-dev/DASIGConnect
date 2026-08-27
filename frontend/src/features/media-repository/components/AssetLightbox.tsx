import { useCallback, useEffect } from "react";
import { createPortal } from "react-dom";
import type { MediaAsset } from "../../../api/mediaApi";
import { formatFileSize, isVideoType } from "../utils";

interface AssetLightboxProps {
  assets: MediaAsset[];
  index: number;
  onIndexChange: (index: number) => void;
  onClose: () => void;
  onDownload: (asset: MediaAsset) => void;
}

export default function AssetLightbox({
  assets,
  index,
  onIndexChange,
  onClose,
  onDownload,
}: AssetLightboxProps) {
  const asset = assets[index];
  const hasPrev = index > 0;
  const hasNext = index < assets.length - 1;

  const goPrev = useCallback(() => {
    if (index > 0) onIndexChange(index - 1);
  }, [index, onIndexChange]);

  const goNext = useCallback(() => {
    if (index < assets.length - 1) onIndexChange(index + 1);
  }, [index, assets.length, onIndexChange]);

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
      else if (e.key === "ArrowLeft") goPrev();
      else if (e.key === "ArrowRight") goNext();
    }
    window.addEventListener("keydown", onKey);
    const { overflow } = document.body.style;
    document.body.style.overflow = "hidden";
    return () => {
      window.removeEventListener("keydown", onKey);
      document.body.style.overflow = overflow;
    };
  }, [onClose, goPrev, goNext]);

  if (!asset) return null;

  const isVideo = isVideoType(asset.fileType);

  return createPortal(
    <div
      className="med-lightbox"
      role="dialog"
      aria-modal="true"
      aria-label={asset.title || asset.code}
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="med-lightbox-bar">
        <div className="med-lightbox-info">
          <span className="med-lightbox-title">{asset.title || asset.fileName}</span>
          <span className="med-lightbox-sub">
            {asset.code} · {asset.fileType.toUpperCase()} · {formatFileSize(asset.fileSizeBytes)}
            {assets.length > 1 ? ` · ${index + 1} / ${assets.length}` : ""}
          </span>
        </div>
        <div className="med-lightbox-actions">
          <button
            type="button"
            className="med-lightbox-btn"
            onClick={() => onDownload(asset)}
            title="Download original"
            aria-label="Download original"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
              <polyline points="7 10 12 15 17 10" />
              <line x1="12" y1="15" x2="12" y2="3" />
            </svg>
          </button>
          <button
            type="button"
            className="med-lightbox-btn"
            onClick={onClose}
            title="Close"
            aria-label="Close viewer"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>
      </div>

      {hasPrev && (
        <button
          type="button"
          className="med-lightbox-nav prev"
          onClick={goPrev}
          aria-label="Previous asset"
        >
          <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="15 18 9 12 15 6" />
          </svg>
        </button>
      )}

      <div className="med-lightbox-stage">
        {isVideo ? (
          <video
            key={asset.id}
            className="med-lightbox-media"
            src={asset.storageUrl}
            controls
            autoPlay
            playsInline
          />
        ) : (
          <img
            key={asset.id}
            className="med-lightbox-media"
            src={asset.storageUrl}
            alt={asset.title || asset.code}
          />
        )}
      </div>

      {hasNext && (
        <button
          type="button"
          className="med-lightbox-nav next"
          onClick={goNext}
          aria-label="Next asset"
        >
          <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="9 18 15 12 9 6" />
          </svg>
        </button>
      )}
    </div>,
    document.body,
  );
}

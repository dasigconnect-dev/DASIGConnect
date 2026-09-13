import { useRef } from "react";
import type { MediaAsset } from "../../../api/mediaApi";
import OptimizedImage, { canTransformImageType } from "../../../components/media/OptimizedImage";
import { formatFileSize, formatUploadDate, isVideoType } from "../utils";

interface AssetCardProps {
  asset: MediaAsset;
  selected: boolean;
  checked?: boolean;
  listView: boolean;
  animationDelay?: number;
  showInstitutionChip?: boolean;
  /** Single click / Enter — select the asset and show its detail panel. */
  onClick: () => void;
  /** Double click — open the full-screen viewer. */
  onOpen?: () => void;
}

export default function AssetCard({
  asset,
  selected,
  checked = false,
  listView,
  animationDelay = 0,
  showInstitutionChip = false,
  onClick,
  onOpen,
}: AssetCardProps) {
  const isVideo = isVideoType(asset.fileType);
  const primaryTag = asset.aiTags?.[0];

  // A double click also fires two single clicks first; defer the select briefly
  // so a double click opens the viewer without leaving the panel flickering.
  const clickTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  function handleClick() {
    if (!onOpen) {
      onClick();
      return;
    }
    if (clickTimer.current) return;
    clickTimer.current = setTimeout(() => {
      clickTimer.current = null;
      onClick();
    }, 200);
  }

  function handleDoubleClick() {
    if (clickTimer.current) {
      clearTimeout(clickTimer.current);
      clickTimer.current = null;
    }
    onOpen?.();
  }

  return (
    <div
      className={`med-card${selected ? " selected" : ""}${checked ? " checked" : ""}`}
      style={{ animationDelay: `${animationDelay}ms` }}
      onClick={handleClick}
      onDoubleClick={handleDoubleClick}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") onClick();
      }}
      aria-pressed={selected}
    >
      <div className="med-card-thumb">
        <span className={`med-card-check${checked ? " checked" : ""}`} aria-hidden="true">
          {checked && (
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3.5" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="20,6 9,17 4,12" />
            </svg>
          )}
        </span>
        {asset.storageUrl ? (
          isVideo ? (
            <video
              className="med-card-thumb-img"
              src={asset.storageUrl}
              muted
              playsInline
              preload="none"
              aria-label={asset.title}
            />
          ) : (
            <OptimizedImage
              className="med-card-thumb-img"
              src={asset.storageUrl}
              alt={asset.title}
              width={listView ? 180 : 360}
              height={listView ? 135 : 225}
              sizes={listView ? "120px" : "(max-width: 768px) 50vw, 280px"}
              candidateWidths={listView ? [180, 240] : [240, 360, 560]}
              transform={canTransformImageType(asset.fileType)}
            />
          )
        ) : (
          <div
            className="med-card-thumb-placeholder"
            style={{ background: placeholderGradient(asset.id) }}
          >
            {isVideo ? (
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="rgba(255,255,255,0.4)" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <polygon points="23,7 16,12 23,17 23,7" />
                <rect x="1" y="5" width="15" height="14" rx="2" ry="2" />
              </svg>
            ) : (
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="rgba(255,255,255,0.4)" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <rect x="3" y="3" width="18" height="18" rx="2" />
                <circle cx="8.5" cy="8.5" r="1.5" />
                <polyline points="21,15 16,10 5,21" />
              </svg>
            )}
          </div>
        )}

        {isVideo && (
          <span className="med-video-badge">
            <svg width="9" height="9" viewBox="0 0 24 24" fill="currentColor">
              <polygon points="5,3 19,12 5,21 5,3" />
            </svg>
            VIDEO
          </span>
        )}

        {showInstitutionChip && asset.institutionName && (
          <span className="med-inst-chip">
            {institutionAbbr(asset.institutionName)}
          </span>
        )}
      </div>

      <div className="med-card-body">
        <div className="med-card-code">{asset.code}</div>
        <div className="med-card-title">{asset.title}</div>
        {asset.albumName && <div className="med-card-album">{asset.albumName}</div>}
        <div className="med-card-meta">
          {!listView && (
            <div className="med-card-meta-left">
              <span className="med-card-date">{formatUploadDate(asset.uploadedAt)}</span>
              <span className="med-card-size">{formatFileSize(asset.fileSizeBytes)} · {asset.fileType.toUpperCase()}</span>
            </div>
          )}
          {listView && (
            <div className="med-card-meta-left">
              <span className="med-card-date">{formatUploadDate(asset.uploadedAt)}</span>
              <span className="med-card-size">{formatFileSize(asset.fileSizeBytes)} · {asset.fileType.toUpperCase()}</span>
            </div>
          )}
          {asset.status === "processing" ? (
            <span className="med-badge med-badge-processing">Processing…</span>
          ) : primaryTag ? (
            <span className="med-badge med-badge-tag">{primaryTag.label}</span>
          ) : null}
        </div>
      </div>
    </div>
  );
}

const GRADIENTS = [
  "linear-gradient(135deg,#1e3a5f 0%,#2563EB 60%,#3B82F6 100%)",
  "linear-gradient(135deg,#064e3b 0%,#10B981 70%,#6EE7B7 100%)",
  "linear-gradient(135deg,#1e1b4b 0%,#7C3AED 65%,#A78BFA 100%)",
  "linear-gradient(135deg,#422006 0%,#D97706 65%,#FCD34D 100%)",
  "linear-gradient(135deg,#134e4a 0%,#0D9488 65%,#5EEAD4 100%)",
  "linear-gradient(135deg,#1a0533 0%,#9333EA 65%,#D946EF 100%)",
  "linear-gradient(135deg,#14532d 0%,#16A34A 65%,#86EFAC 100%)",
  "linear-gradient(135deg,#0f172a 0%,#334155 65%,#94A3B8 100%)",
];

function placeholderGradient(id: string) {
  const index = id.charCodeAt(id.length - 1) % GRADIENTS.length;
  return GRADIENTS[index];
}

function institutionAbbr(name: string) {
  return name
    .split(/\s+/)
    .filter((w) => w.length > 2)
    .map((w) => w[0].toUpperCase())
    .join("")
    .slice(0, 4);
}

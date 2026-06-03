import type { MediaAsset } from "../../../api/mediaApi";
import { formatFileSize, formatUploadDate, isVideoType } from "../utils";

interface AssetCardProps {
  asset: MediaAsset;
  selected: boolean;
  checked?: boolean;
  listView: boolean;
  animationDelay?: number;
  showInstitutionChip?: boolean;
  /** UC-4.5: why this asset matched the search query (shown in search results only). */
  matchReasons?: string[];
  /** UC-4.6: current feedback for this search result, for pressed styling. */
  feedback?: "up" | "down" | null;
  /** UC-4.6: when provided, renders thumbs-up/down feedback controls (search mode). */
  onFeedback?: (action: "thumbs_up" | "thumbs_down") => void;
  onClick: () => void;
}

export default function AssetCard({
  asset,
  selected,
  checked = false,
  listView,
  animationDelay = 0,
  showInstitutionChip = false,
  matchReasons,
  feedback = null,
  onFeedback,
  onClick,
}: AssetCardProps) {
  const isVideo = isVideoType(asset.fileType);
  const primaryTag = asset.aiTags?.[0];

  return (
    <div
      className={`med-card${selected ? " selected" : ""}${checked ? " checked" : ""}`}
      style={{ animationDelay: `${animationDelay}ms` }}
      onClick={onClick}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => { if (e.key === "Enter" || e.key === " ") onClick(); }}
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
              preload="metadata"
              aria-label={asset.title}
            />
          ) : (
          <img
            className="med-card-thumb-img"
            src={asset.storageUrl}
            alt={asset.title}
            loading="lazy"
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

        {matchReasons && matchReasons.length > 0 && (
          <div className="med-card-reasons" title={`Matched on: ${matchReasons.join(", ")}`}>
            <svg className="med-card-reasons-icon" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M5 3v4M3 5h4M6 17v4M4 19h4M13 3l2.5 6.5L22 12l-6.5 2.5L13 21l-2.5-6.5L4 12l6.5-2.5L13 3z" />
            </svg>
            {matchReasons.slice(0, 3).map((reason) => (
              <span key={reason} className="med-card-reason">{reason}</span>
            ))}
            {matchReasons.length > 3 && (
              <span className="med-card-reason med-card-reason-more">+{matchReasons.length - 3}</span>
            )}
          </div>
        )}

        {onFeedback && (
          <div
            className="med-card-feedback"
            onClick={(e) => e.stopPropagation()}
            onKeyDown={(e) => e.stopPropagation()}
          >
            <span className="med-card-feedback-label">Helpful?</span>
            <button
              type="button"
              className={`med-feedback-btn${feedback === "up" ? " is-up" : ""}`}
              aria-label="Mark this result helpful"
              aria-pressed={feedback === "up"}
              onClick={(e) => { e.stopPropagation(); onFeedback("thumbs_up"); }}
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M7 10v12" />
                <path d="M15 5.88 14 10h5.83a2 2 0 0 1 1.92 2.56l-2.33 8A2 2 0 0 1 17.5 22H4a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2h2.76a2 2 0 0 0 1.79-1.11L12 2a3.13 3.13 0 0 1 3 3.88Z" />
              </svg>
            </button>
            <button
              type="button"
              className={`med-feedback-btn${feedback === "down" ? " is-down" : ""}`}
              aria-label="Mark this result not helpful"
              aria-pressed={feedback === "down"}
              onClick={(e) => { e.stopPropagation(); onFeedback("thumbs_down"); }}
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M17 14V2" />
                <path d="M9 18.12 10 14H4.17a2 2 0 0 1-1.92-2.56l2.33-8A2 2 0 0 1 6.5 2H20a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-2.76a2 2 0 0 0-1.79 1.11L12 22a3.13 3.13 0 0 1-3-3.88Z" />
              </svg>
            </button>
          </div>
        )}
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

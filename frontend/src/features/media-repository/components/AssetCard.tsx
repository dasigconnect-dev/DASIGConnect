import { useRef, useState } from "react";
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
  /** Double click (desktop) or the expand button (any device) — open the full-screen viewer. */
  onOpen?: () => void;
  /** Kebab menu — omitted entirely (no button rendered) when not provided. */
  canManage?: boolean;
  canDelete?: boolean;
  onAddToDraft?: () => void;
  onNewSubmission?: () => void;
  onDownload?: () => void;
  onRename?: () => void;
  onMove?: () => void;
  onDelete?: () => void;
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
  canManage = false,
  canDelete = false,
  onAddToDraft,
  onNewSubmission,
  onDownload,
  onRename,
  onMove,
  onDelete,
}: AssetCardProps) {
  const isVideo = isVideoType(asset.fileType);
  const [menuOpen, setMenuOpen] = useState(false);
  const hasMenu = Boolean(onAddToDraft || onNewSubmission || onDownload || onRename || onMove || onDelete);

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

  // Double-click has no touch equivalent, so this is the only way a mobile
  // user can reach the full-screen viewer — always visible (not hover-only)
  // for that reason, on both grid and list thumbnails.
  const expandButton = onOpen && (
    <button
      type="button"
      className="med-card-expand"
      aria-label={`View ${asset.title} full size`}
      onClick={(e) => {
        e.stopPropagation();
        if (clickTimer.current) {
          clearTimeout(clickTimer.current);
          clickTimer.current = null;
        }
        onOpen();
      }}
    >
      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7" />
      </svg>
    </button>
  );

  const kebabMenu = hasMenu && (
    <div
      className="med-folder-menu-wrap med-card-menu-wrap"
      onClick={(e) => e.stopPropagation()}
      onBlur={(e) => {
        if (!e.currentTarget.contains(e.relatedTarget as Node)) setMenuOpen(false);
      }}
    >
      <button
        className="med-folder-kebab"
        type="button"
        aria-haspopup="menu"
        aria-expanded={menuOpen}
        aria-label={`${asset.title} actions`}
        onClick={() => setMenuOpen((v) => !v)}
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
          <circle cx="12" cy="5" r="1.6" />
          <circle cx="12" cy="12" r="1.6" />
          <circle cx="12" cy="19" r="1.6" />
        </svg>
      </button>
      {menuOpen && (
        <div className="med-folder-menu" role="menu">
          {onAddToDraft && (
            <button type="button" role="menuitem" onClick={() => { setMenuOpen(false); onAddToDraft(); }}>
              Add to Draft
            </button>
          )}
          {onNewSubmission && (
            <button type="button" role="menuitem" onClick={() => { setMenuOpen(false); onNewSubmission(); }}>
              New Submission
            </button>
          )}
          {onDownload && (
            <button type="button" role="menuitem" onClick={() => { setMenuOpen(false); onDownload(); }}>
              Download
            </button>
          )}
          {onRename && (
            <button type="button" role="menuitem" disabled={!canManage} onClick={() => { setMenuOpen(false); onRename(); }}>
              Rename
            </button>
          )}
          {onMove && (
            <button type="button" role="menuitem" disabled={!canManage} onClick={() => { setMenuOpen(false); onMove(); }}>
              Move to…
            </button>
          )}
          {onDelete && (
            <button
              type="button"
              role="menuitem"
              className="med-folder-menu-danger"
              disabled={!canDelete}
              title={canDelete ? undefined : "You don't have permission to delete this asset"}
              onClick={() => { setMenuOpen(false); onDelete(); }}
            >
              Delete
            </button>
          )}
        </div>
      )}
    </div>
  );

  const checkbox = (
    <span className={`med-card-check${checked ? " checked" : ""}`} aria-hidden="true">
      {checked && (
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3.5" strokeLinecap="round" strokeLinejoin="round">
          <polyline points="20,6 9,17 4,12" />
        </svg>
      )}
    </span>
  );

  const thumbContent = asset.storageUrl ? (
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
      <OptimizedImage
        className="med-card-thumb-img"
        src={asset.storageUrl}
        alt={asset.title}
        width={listView ? 96 : 360}
        height={listView ? 96 : 225}
        sizes={listView ? "48px" : "(max-width: 768px) 50vw, 280px"}
        candidateWidths={listView ? [48, 96] : [240, 360, 560]}
        transform={canTransformImageType(asset.fileType)}
      />
    )
  ) : (
    <div
      className="med-card-thumb-placeholder"
      style={{ background: placeholderGradient(asset.id) }}
    >
      {isVideo ? (
        <svg width={listView ? 18 : 32} height={listView ? 18 : 32} viewBox="0 0 24 24" fill="none" stroke="rgba(255,255,255,0.4)" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
          <polygon points="23,7 16,12 23,17 23,7" />
          <rect x="1" y="5" width="15" height="14" rx="2" ry="2" />
        </svg>
      ) : (
        <svg width={listView ? 18 : 32} height={listView ? 18 : 32} viewBox="0 0 24 24" fill="none" stroke="rgba(255,255,255,0.4)" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
          <rect x="3" y="3" width="18" height="18" rx="2" />
          <circle cx="8.5" cy="8.5" r="1.5" />
          <polyline points="21,15 16,10 5,21" />
        </svg>
      )}
    </div>
  );

  if (listView) {
    return (
      <div
        className={`med-card list-view${selected ? " selected" : ""}${checked ? " checked" : ""}`}
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
        {checkbox}

        <div className="med-card-row-thumb">
          {thumbContent}
          {isVideo && (
            <span className="med-video-badge sm" aria-label="Video">
              <svg width="8" height="8" viewBox="0 0 24 24" fill="currentColor">
                <polygon points="5,3 19,12 5,21 5,3" />
              </svg>
            </span>
          )}
          {expandButton}
        </div>

        <div className="med-card-row-body">
          <div className="med-card-row-title">{asset.title}</div>
          <div className="med-card-row-sub">
            <span className="med-card-code">{asset.code}</span>
            {asset.albumName && (
              <>
                <span aria-hidden="true">·</span>
                <span className="med-card-album inline">{asset.albumName}</span>
              </>
            )}
            {showInstitutionChip && asset.institutionName && (
              <span className="med-inst-chip inline">{institutionAbbr(asset.institutionName)}</span>
            )}
          </div>
        </div>

        {asset.status === "processing" && (
          <span className="med-badge med-badge-processing">Processing…</span>
        )}

        <div className="med-card-row-meta">
          {formatUploadDate(asset.uploadedAt)} · {formatFileSize(asset.fileSizeBytes)} · {asset.fileType.toUpperCase()}
        </div>

        {kebabMenu}
      </div>
    );
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
        {checkbox}
        {thumbContent}

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

        {expandButton}
      </div>

      <div className="med-card-body">
        <div className="med-card-code">{asset.code}</div>
        <div className="med-card-title">{asset.title}</div>
        {asset.albumName && <div className="med-card-album">{asset.albumName}</div>}
        <div className="med-card-meta">
          <div className="med-card-meta-left">
            <span className="med-card-date">{formatUploadDate(asset.uploadedAt)}</span>
            <span className="med-card-size">{formatFileSize(asset.fileSizeBytes)} · {asset.fileType.toUpperCase()}</span>
          </div>
          <div className="med-card-meta-right">
            {asset.status === "processing" && (
              <span className="med-badge med-badge-processing">Processing…</span>
            )}
            {kebabMenu}
          </div>
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

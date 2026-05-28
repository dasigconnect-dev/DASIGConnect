import { createPortal } from "react-dom";
import type { MediaAsset } from "../../../api/mediaApi";
import { formatFileSize, formatUploadDate, formatResolution, formatFileTypeName, isVideoType } from "../utils";

interface AssetDetailPanelProps {
  asset: MediaAsset | null;
  open: boolean;
  selectionMode?: boolean;
  selectedAssets?: MediaAsset[];
  canAddToDraft?: boolean;
  onClose: () => void;
  onViewAsset?: (asset: MediaAsset) => void;
  onDeselectAsset?: (id: string) => void;
  onNewPost?: () => void;
  onClearSelection?: () => void;
  onAddToDraft: () => void;
  onDownload: () => void;
  canDelete?: boolean;
  onRequestDelete: () => void;
  canBulkDelete?: boolean;
  onRequestBulkDelete?: () => void;
}

const submissionStatusLabel: Record<string, string> = {
  published: "Published",
  scheduled: "Scheduled",
  draft: "Draft",
  pending: "Pending Review",
  in_review: "In Review",
  needs_revision: "Needs Revision",
  rejected: "Rejected",
};

const submissionStatusBadge: Record<string, string> = {
  published: "med-badge-published",
  scheduled: "med-badge-scheduled",
  draft: "med-badge-draft",
  pending: "med-badge-pending",
  in_review: "med-badge-processing",
  needs_revision: "med-badge-revision",
  rejected: "med-badge-revision",
};

export default function AssetDetailPanel({
  asset,
  open,
  selectionMode = false,
  selectedAssets = [],
  canAddToDraft = false,
  onClose,
  onViewAsset,
  onDeselectAsset,
  onNewPost,
  onClearSelection,
  onAddToDraft,
  onDownload,
  canDelete = false,
  onRequestDelete,
  canBulkDelete = false,
  onRequestBulkDelete,
}: AssetDetailPanelProps) {
  const newPostCount = selectionMode ? selectedAssets.length : asset ? 1 : 0;
  const panel = (
    <div className={`med-panel${open ? " open" : ""}`} role="dialog" aria-modal="true" aria-label="Asset Detail">
      <div className="med-panel-header">
        <span className="med-panel-title">{selectionMode ? "Selected Assets" : "Asset Detail"}</span>
        <button className="med-panel-close" onClick={onClose} type="button" aria-label="Close panel">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <line x1="18" y1="6" x2="6" y2="18" />
            <line x1="6" y1="6" x2="18" y2="18" />
          </svg>
        </button>
      </div>

      <div className="med-panel-body">
        {selectionMode && (
          <div className="med-sel-block">
            <div className="med-sel-head">
              <div className="med-panel-section-label" style={{ marginBottom: 0 }}>
                Selected for New Post · {selectedAssets.length}
              </div>
              {onClearSelection && (
                <button className="med-sel-clear" onClick={onClearSelection} type="button">
                  Clear all
                </button>
              )}
            </div>
            <div className="med-sel-list">
              {selectedAssets.map((sel) => (
                <div
                  key={sel.id}
                  className={`med-sel-row${asset?.id === sel.id ? " active" : ""}`}
                  onClick={() => onViewAsset?.(sel)}
                  role="button"
                  tabIndex={0}
                  onKeyDown={(e) => { if (e.key === "Enter" || e.key === " ") onViewAsset?.(sel); }}
                  title={`View ${sel.title}`}
                >
                  <div className="med-sel-thumb">
                    {sel.storageUrl ? (
                      <img src={sel.storageUrl} alt={sel.title} loading="lazy" />
                    ) : (
                      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="rgba(255,255,255,0.5)" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                        <rect x="3" y="3" width="18" height="18" rx="2" />
                        <circle cx="8.5" cy="8.5" r="1.5" />
                        <polyline points="21,15 16,10 5,21" />
                      </svg>
                    )}
                  </div>
                  <div className="med-sel-info">
                    <div className="med-sel-name">{sel.title}</div>
                    <div className="med-sel-meta">
                      {formatFileTypeName(sel.fileType)} · {formatFileSize(sel.fileSizeBytes)}
                    </div>
                  </div>
                  <div className="med-sel-row-right">
                    <svg className="med-sel-chevron" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <polyline points="9,18 15,12 9,6" />
                    </svg>
                    {onDeselectAsset && (
                      <button
                        className="med-sel-remove"
                        onClick={(e) => { e.stopPropagation(); onDeselectAsset(sel.id); }}
                        type="button"
                        aria-label={`Remove ${sel.title} from selection`}
                      >
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                          <line x1="18" y1="6" x2="6" y2="18" />
                          <line x1="6" y1="6" x2="18" y2="18" />
                        </svg>
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
            {asset && <div className="med-sel-divider" />}
          </div>
        )}
        {asset && (
          <>
            {/* Preview */}
            <div className="med-panel-preview">
              <div className="med-panel-preview-inner" style={{ background: previewBackground(asset) }}>
                {asset.storageUrl ? (
                  isVideoType(asset.fileType) ? (
                    <video src={asset.storageUrl} style={{ width: "100%", height: "100%", objectFit: "cover" }} controls muted playsInline preload="metadata" />
                  ) : (
                    <img src={asset.storageUrl} alt={asset.title} style={{ width: "100%", height: "100%", objectFit: "cover" }} />
                  )
                ) : (
                  <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="rgba(255,255,255,0.4)" strokeWidth="1" strokeLinecap="round" strokeLinejoin="round">
                    <rect x="3" y="3" width="18" height="18" rx="2" />
                    <circle cx="8.5" cy="8.5" r="1.5" />
                    <polyline points="21,15 16,10 5,21" />
                  </svg>
                )}
              </div>
            </div>

            {/* Identity */}
            <div>
              <div className="med-panel-section-label">Asset Identity</div>
              <span className="med-asset-code-display">
                <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <rect x="9" y="9" width="13" height="13" rx="2" />
                  <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
                </svg>
                {asset.code}
              </span>
              <div className="med-editable-title" title={asset.title}>
                {asset.title}
              </div>
            </div>

            {/* Metadata */}
            <div>
              <div className="med-panel-section-label">Metadata</div>
              <div className="med-meta-grid">
                <div>
                  <div className="med-meta-key">Filename</div>
                  <div className="med-meta-val" style={{ wordBreak: "break-all" }}>{asset.fileName}</div>
                </div>
                <div>
                  <div className="med-meta-key">File Type</div>
                  <div className="med-meta-val">{formatFileTypeName(asset.fileType)}</div>
                </div>
                {asset.uploaderName && (
                  <div>
                    <div className="med-meta-key">Uploader</div>
                    <div className="med-meta-val">{asset.uploaderName}{asset.institutionName ? ` · ${asset.institutionName}` : ""}</div>
                  </div>
                )}
                <div>
                  <div className="med-meta-key">Upload Date</div>
                  <div className="med-meta-val">{formatUploadDate(asset.uploadedAt)}</div>
                </div>
                <div>
                  <div className="med-meta-key">File Size</div>
                  <div className="med-meta-val">{formatFileSize(asset.fileSizeBytes)}</div>
                </div>
                <div>
                  <div className="med-meta-key">Resolution</div>
                  <div className="med-meta-val">{formatResolution(asset)}</div>
                </div>
              </div>
            </div>

            {/* AI Classification */}
            {(asset.aiTags && asset.aiTags.length > 0) || asset.status === "processing" ? (
              <div>
                <div className="med-panel-section-label">AI Classification</div>
                {asset.status === "processing" ? (
                  <span className="med-badge med-badge-processing">Classification in progress…</span>
                ) : (
                  <>
                    <div className="med-ai-tags">
                      {asset.aiTags!.map((tag) => (
                        <div key={tag.label} className="med-ai-tag">
                          <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                            <path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z" />
                            <line x1="7" y1="7" x2="7.01" y2="7" />
                          </svg>
                          {tag.label}
                          <span className="med-ai-tag-conf">{tag.confidence}%</span>
                        </div>
                      ))}
                    </div>
                    <input
                      type="text"
                      className="med-custom-tag-input"
                      placeholder="+ Add custom tag (e.g., dost-region7)"
                    />
                  </>
                )}
              </div>
            ) : null}

            {/* Used In */}
            {asset.usedIn && asset.usedIn.length > 0 && (
              <div>
                <div className="med-panel-section-label">Used In</div>
                <div className="med-used-in-list">
                  {asset.usedIn.map((usage) => (
                    <div key={usage.submissionId} className="med-used-in-item">
                      <div className="med-used-in-info">
                        <div className="med-used-in-title">{usage.submissionTitle}</div>
                        <div className="med-used-in-date">Submitted {formatUploadDate(usage.submittedAt)}</div>
                      </div>
                      <div className="med-used-in-right">
                        <span className={`med-badge ${submissionStatusBadge[usage.submissionStatus] ?? "med-badge-tag"}`}>
                          {submissionStatusLabel[usage.submissionStatus] ?? usage.submissionStatus}
                        </span>
                        <a className="med-jump-link" href={`/submissions/${usage.submissionId}`}>
                          View
                          <svg width="9" height="9" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                            <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
                            <polyline points="15,3 21,3 21,9" />
                            <line x1="10" y1="14" x2="21" y2="3" />
                          </svg>
                        </a>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </>
        )}
      </div>

      {/* Actions */}
      {asset && (
      <div className="med-panel-actions">
        {/* Row 1: Delete · Download · Add to Draft */}
        <div className="med-panel-action-row">
          {(selectionMode ? canBulkDelete : canDelete) && (
            <button
              className="med-btn med-btn-danger med-btn-sm"
              onClick={selectionMode ? onRequestBulkDelete : onRequestDelete}
              title={selectionMode ? `Delete ${selectedAssets.length} selected assets` : "Delete this asset"}
              type="button"
            >
              <TrashIcon />
              {selectionMode && selectedAssets.length > 1 ? `Delete (${selectedAssets.length})` : "Delete"}
            </button>
          )}
          <button className="med-btn med-btn-ghost med-btn-sm" onClick={onDownload} type="button">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
              <polyline points="7,10 12,15 17,10" />
              <line x1="12" y1="15" x2="12" y2="3" />
            </svg>
            Download
          </button>
          {canAddToDraft && (
            <button className="med-btn med-btn-ghost med-btn-sm" onClick={onAddToDraft} type="button">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
                <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
              </svg>
              Add to Draft
            </button>
          )}
        </div>

        {/* Row 2: New Submission */}
        <button
          className="med-btn med-btn-primary med-btn-sm"
          onClick={() => onNewPost?.()}
          type="button"
          style={{ width: "100%", justifyContent: "center" }}
        >
          New Submission ({newPostCount})
        </button>
      </div>
      )}
    </div>
  );

  return createPortal(panel, document.body);
}

function TrashIcon() {
  return (
    <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="3,6 5,6 21,6" />
      <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6" />
      <path d="M10 11v6" />
      <path d="M14 11v6" />
      <path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2" />
    </svg>
  );
}

function previewBackground(asset: MediaAsset) {
  const gradients = [
    "linear-gradient(135deg,#1e3a5f 0%,#2563EB 60%,#3B82F6 100%)",
    "linear-gradient(135deg,#064e3b 0%,#10B981 70%,#6EE7B7 100%)",
    "linear-gradient(135deg,#1e1b4b 0%,#7C3AED 65%,#A78BFA 100%)",
    "linear-gradient(135deg,#422006 0%,#D97706 65%,#FCD34D 100%)",
  ];
  if (asset.storageUrl) return "transparent";
  const idx = asset.id.charCodeAt(asset.id.length - 1) % gradients.length;
  return gradients[idx];
}

import { createPortal } from "react-dom";
import type { MediaAssetDetailResponse } from "../../../api/mediaApi";

interface BatchCurationModalProps {
  open: boolean;
  loading: boolean;
  saving: boolean;
  assets: MediaAssetDetailResponse[];
  onClose: () => void;
  onRefresh: () => void;
  onConfirmAll: () => void;
}

export default function BatchCurationModal({
  open,
  loading,
  saving,
  assets,
  onClose,
  onRefresh,
  onConfirmAll,
}: BatchCurationModalProps) {
  if (!open) return null;

  const readyCount = assets.filter((asset) => asset.status === "READY").length;
  const curatedCount = assets.filter((asset) => Boolean(asset.curatedAt)).length;
  const hasPending = assets.some((asset) => asset.status !== "READY");
  const hasDuplicates = assets.some((asset) => Boolean(asset.duplicateOfId));

  const modal = (
    <div className="med-modal-overlay open" onClick={(event) => { if (event.target === event.currentTarget && !saving) onClose(); }}>
      <div className="med-modal-card med-batch-review-card" role="dialog" aria-modal="true" aria-label="Review upload batch">
        <div className="med-modal-header">
          <div>
            <span className="med-modal-title">Review upload batch</span>
            <div className="med-batch-review-sub">
              {loading ? "Loading AI metadata..." : `${readyCount} ready, ${curatedCount} curated, ${assets.length} total`}
            </div>
          </div>
          <button className="med-modal-close" onClick={onClose} type="button" aria-label="Close" disabled={saving}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>

        <div className="med-modal-body">
          {hasPending && (
            <div className="med-batch-review-banner">
              Some assets are still processing. Refresh after a few seconds before confirming the batch.
            </div>
          )}
          {hasDuplicates && (
            <div className="med-batch-review-banner warn">
              Possible duplicates were detected in this batch.
            </div>
          )}

          {loading ? (
            <div className="med-empty" style={{ padding: "36px 0" }}>
              <div className="med-empty-title">Loading batch metadata...</div>
            </div>
          ) : assets.length === 0 ? (
            <div className="med-empty" style={{ padding: "36px 0" }}>
              <div className="med-empty-title">No assets found</div>
              <p className="med-empty-sub">This upload batch may not have completed registration.</p>
            </div>
          ) : (
            <div className="med-batch-review-list">
              {assets.map((asset) => (
                <BatchReviewRow asset={asset} key={asset.id} />
              ))}
            </div>
          )}
        </div>

        <div className="med-modal-footer">
          <button className="med-btn med-btn-ghost" type="button" onClick={onRefresh} disabled={loading || saving}>
            Refresh
          </button>
          <button className="med-btn med-btn-primary" type="button" onClick={onConfirmAll} disabled={loading || saving || assets.length === 0 || hasPending}>
            {saving ? "Confirming..." : "Confirm all"}
          </button>
        </div>
      </div>
    </div>
  );

  return createPortal(modal, document.body);
}

function BatchReviewRow({ asset }: { asset: MediaAssetDetailResponse }) {
  const tags = asset.tags?.map((tag) => tag.label) ?? [];
  const statusLabel = asset.curatedAt ? "Curated" : asset.status === "READY" ? "Ready" : asset.status === "FAILED" ? "Failed" : "Processing";

  return (
    <article className={`med-batch-review-row status-${asset.status?.toLowerCase() ?? "processing"}`}>
      <div className="med-batch-review-thumb">
        {isImage(asset.fileType) ? (
          <img src={asset.storageUrl} alt={asset.title || asset.fileName} loading="lazy" />
        ) : (
          <div className="med-card-thumb-placeholder">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
              <rect x="3" y="3" width="18" height="18" rx="2" />
              <path d="m10 8 6 4-6 4Z" />
            </svg>
          </div>
        )}
      </div>
      <div className="med-batch-review-main">
        <div className="med-batch-review-head">
          <strong>{asset.title || asset.fileName}</strong>
          <span className={`med-upload-status-pill status-${asset.curatedAt ? "success" : asset.status === "READY" ? "success" : "uploading"}`}>
            {statusLabel}
          </span>
        </div>
        <div className="med-batch-review-meta">
          <span>{asset.aiCategory || "No category yet"}</span>
          {asset.blurScore != null && <span>Blur {Number(asset.blurScore).toFixed(0)}</span>}
          {asset.duplicateOfId && <span>Possible duplicate</span>}
        </div>
        {asset.aiDescription && <p>{asset.aiDescription}</p>}
        {tags.length > 0 && (
          <div className="med-batch-review-tags">
            {tags.slice(0, 8).map((tag) => (
              <span key={tag}>{tag}</span>
            ))}
          </div>
        )}
      </div>
    </article>
  );
}

function isImage(fileType: string) {
  return ["jpeg", "jpg", "png", "gif", "webp"].includes(fileType.toLowerCase());
}

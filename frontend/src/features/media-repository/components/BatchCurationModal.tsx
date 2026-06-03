import { useEffect, useMemo, useState } from "react";
import { createPortal } from "react-dom";
import type {
  MediaAssetCurationEdit,
  MediaAssetDetailResponse,
  MediaImportBatch,
} from "../../../api/mediaApi";

interface BatchCurationModalProps {
  open: boolean;
  batches: MediaImportBatch[];
  batchesLoading: boolean;
  selectedBatchId: string | null;
  loading: boolean;
  saving: boolean;
  assets: MediaAssetDetailResponse[];
  onClose: () => void;
  onRefresh: () => void;
  onSelectBatch: (batchId: string) => void;
  onBackToBatches: () => void;
  onConfirmAll: (edits: MediaAssetCurationEdit[]) => void;
}

interface DraftEdit {
  title: string;
  tagsText: string;
}

export default function BatchCurationModal({
  open,
  batches,
  batchesLoading,
  selectedBatchId,
  loading,
  saving,
  assets,
  onClose,
  onRefresh,
  onSelectBatch,
  onBackToBatches,
  onConfirmAll,
}: BatchCurationModalProps) {
  const [drafts, setDrafts] = useState<Record<string, DraftEdit>>({});

  useEffect(() => {
    if (!open) return;
    setDrafts(Object.fromEntries(assets.map((asset) => [
      asset.id,
      {
        title: asset.title || titleFromFileName(asset.fileName),
        tagsText: (asset.tags?.map((tag) => tag.label) ?? []).join(", "),
      },
    ])));
  }, [assets, open]);

  const readyCount = assets.filter((asset) => asset.status === "READY").length;
  const curatedCount = assets.filter((asset) => Boolean(asset.curatedAt)).length;
  const hasPending = assets.some((asset) => asset.status !== "READY");
  const hasDuplicates = assets.some((asset) => Boolean(asset.duplicateOfId));
  const allCurated = assets.length > 0 && curatedCount === assets.length;
  const selectedBatch = batches.find((batch) => batch.id === selectedBatchId) ?? null;
  const showingBatchList = !selectedBatchId;
  const dirtyCount = useMemo(() => assets.filter((asset) => {
    const draft = drafts[asset.id];
    if (!draft) return false;
    const originalTitle = asset.title || titleFromFileName(asset.fileName);
    const originalTags = (asset.tags?.map((tag) => tag.label) ?? []).join(", ");
    return draft.title.trim() !== originalTitle.trim() || draft.tagsText.trim() !== originalTags.trim();
  }).length, [assets, drafts]);

  if (!open) return null;

  function updateDraft(assetId: string, patch: Partial<DraftEdit>) {
    setDrafts((current) => ({
      ...current,
      [assetId]: {
        title: current[assetId]?.title ?? "",
        tagsText: current[assetId]?.tagsText ?? "",
        ...patch,
      },
    }));
  }

  function confirmAll() {
    onConfirmAll(assets.map((asset) => {
      const draft = drafts[asset.id] ?? {
        title: asset.title || titleFromFileName(asset.fileName),
        tagsText: (asset.tags?.map((tag) => tag.label) ?? []).join(", "),
      };
      return {
        assetId: asset.id,
        title: draft.title.trim() || titleFromFileName(asset.fileName),
        tags: parseTags(draft.tagsText),
      };
    }));
  }

  const modal = (
    <div className="med-modal-overlay open" onClick={(event) => { if (event.target === event.currentTarget && !saving) onClose(); }}>
      <div className="med-modal-card med-batch-review-card" role="dialog" aria-modal="true" aria-label="Review upload batch">
        <div className="med-modal-header">
          <div>
            <span className="med-modal-title">{showingBatchList ? "Review upload batches" : "Review upload batch"}</span>
            <div className="med-batch-review-sub">
              {showingBatchList
                ? batchesLoading ? "Loading recent batch groups..." : `${batches.length} recent ${batches.length === 1 ? "batch" : "batches"}`
                : loading
                ? "Loading AI metadata..."
                : `${readyCount} ready, ${curatedCount} curated, ${assets.length} total${dirtyCount > 0 ? `, ${dirtyCount} edited` : ""}`}
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
          {showingBatchList ? (
            <BatchGroupList
              batches={batches}
              loading={batchesLoading}
              onSelectBatch={onSelectBatch}
            />
          ) : (
            <>
          {selectedBatch && (
            <div className="med-batch-review-selected">
              <button className="med-batch-back-btn" type="button" onClick={onBackToBatches} disabled={saving}>
                Back to batches
              </button>
              <span>{formatBatchDate(selectedBatch.createdAt)} · {selectedBatch.registeredAssetCount ?? selectedBatch.assetCount} photos</span>
            </div>
          )}
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
          {allCurated && (
            <div className="med-batch-review-banner success">
              This upload batch has already been confirmed.
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
                <BatchReviewRow
                  asset={asset}
                  draft={drafts[asset.id] ?? { title: asset.title || asset.fileName, tagsText: "" }}
                  disabled={saving}
                  key={asset.id}
                  onDraftChange={(patch) => updateDraft(asset.id, patch)}
                />
              ))}
            </div>
          )}
            </>
          )}
        </div>

        <div className="med-modal-footer">
          <button className="med-btn med-btn-ghost" type="button" onClick={onRefresh} disabled={loading || saving}>
            Refresh
          </button>
          {showingBatchList ? (
            <button className="med-btn med-btn-primary" type="button" onClick={onClose} disabled={saving}>
              Done
            </button>
          ) : allCurated ? (
            <button className="med-btn med-btn-primary" type="button" onClick={onClose} disabled={saving}>
              Done
            </button>
          ) : (
            <button className="med-btn med-btn-primary" type="button" onClick={confirmAll} disabled={loading || saving || assets.length === 0 || hasPending}>
              {saving ? "Confirming..." : "Save edits and confirm"}
            </button>
          )}
        </div>
      </div>
    </div>
  );

  return createPortal(modal, document.body);
}

function BatchGroupList({
  batches,
  loading,
  onSelectBatch,
}: {
  batches: MediaImportBatch[];
  loading: boolean;
  onSelectBatch: (batchId: string) => void;
}) {
  if (loading) {
    return (
      <div className="med-empty" style={{ padding: "36px 0" }}>
        <div className="med-empty-title">Loading batch groups...</div>
      </div>
    );
  }
  if (batches.length === 0) {
    return (
      <div className="med-empty" style={{ padding: "36px 0" }}>
        <div className="med-empty-title">No upload batches yet</div>
        <p className="med-empty-sub">Upload multiple media files to create a batch group for review.</p>
      </div>
    );
  }

  return (
    <div className="med-batch-group-list">
      {batches.map((batch) => {
        const registered = batch.registeredAssetCount ?? batch.assetCount;
        const ready = batch.readyAssetCount ?? 0;
        const curated = batch.curatedAssetCount ?? 0;
        const complete = registered > 0 && curated === registered;
        return (
          <button className="med-batch-group-row" type="button" key={batch.id} onClick={() => onSelectBatch(batch.id)}>
            <div>
              <strong>{formatBatchDate(batch.createdAt)}</strong>
              <span>{registered} uploaded · {ready} ready · {curated} curated</span>
            </div>
            <span className={`med-upload-status-pill status-${complete ? "success" : "uploading"}`}>
              {complete ? "Curated" : "Review"}
            </span>
          </button>
        );
      })}
    </div>
  );
}

function BatchReviewRow({
  asset,
  draft,
  disabled,
  onDraftChange,
}: {
  asset: MediaAssetDetailResponse;
  draft: DraftEdit;
  disabled: boolean;
  onDraftChange: (patch: Partial<DraftEdit>) => void;
}) {
  const statusLabel = asset.curatedAt ? "Curated" : asset.status === "READY" ? "Ready" : asset.status === "FAILED" ? "Failed" : "Processing";

  return (
    <article className={`med-batch-review-row status-${asset.status?.toLowerCase() ?? "processing"}`}>
      <div className="med-batch-review-thumb">
        {isImage(asset.fileType) ? (
          <img src={asset.storageUrl} alt={draft.title || asset.fileName} loading="lazy" />
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
          <label className="med-batch-review-field">
            <span>Title</span>
            <input
              type="text"
              value={draft.title}
              maxLength={140}
              disabled={disabled}
              onChange={(event) => onDraftChange({ title: event.target.value })}
            />
          </label>
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
        <label className="med-batch-review-field">
          <span>Tags</span>
          <input
            type="text"
            value={draft.tagsText}
            disabled={disabled}
            placeholder="award, students, research"
            onChange={(event) => onDraftChange({ tagsText: event.target.value })}
          />
        </label>
      </div>
    </article>
  );
}

function parseTags(value: string) {
  return Array.from(new Set(value
    .split(",")
    .map((tag) => tag.trim().replace(/\s+/g, " "))
    .filter(Boolean)))
    .slice(0, 20);
}

function titleFromFileName(fileName: string) {
  const dot = fileName.lastIndexOf(".");
  const base = dot > 0 ? fileName.slice(0, dot) : fileName;
  return base.replace(/[_-]/g, " ").replace(/\s+/g, " ").trim() || fileName;
}

function formatBatchDate(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(new Date(value));
}

function isImage(fileType: string) {
  return ["jpeg", "jpg", "png", "gif", "webp"].includes(fileType.toLowerCase());
}

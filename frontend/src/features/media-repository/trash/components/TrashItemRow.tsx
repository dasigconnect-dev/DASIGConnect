import { useState } from "react";
import { purgeMediaAsset, restoreMediaAsset, type TrashItem } from "../../../../api/mediaApi";
import { useToast } from "../../../../context/ToastContext";

interface TrashItemRowProps {
  item: TrashItem;
  onRemoved: (assetId: string) => void;
}

function isImageType(fileType: string) {
  return ["jpeg", "jpg", "png", "webp", "gif"].includes(fileType.toLowerCase());
}

export default function TrashItemRow({ item, onRemoved }: TrashItemRowProps) {
  const toast = useToast();
  const { asset } = item;
  const [busy, setBusy] = useState(false);
  const [confirmingPurge, setConfirmingPurge] = useState(false);

  async function restore() {
    setBusy(true);
    try {
      await restoreMediaAsset(asset.id);
      toast.success("Asset restored to the library.");
      onRemoved(asset.id);
    } catch {
      toast.error("Could not restore this asset.");
    } finally {
      setBusy(false);
    }
  }

  async function purge() {
    setBusy(true);
    try {
      await purgeMediaAsset(asset.id);
      toast.success("Asset permanently deleted.");
      onRemoved(asset.id);
    } catch {
      toast.error("Could not delete this asset.");
    } finally {
      setBusy(false);
    }
  }

  const daysLabel =
    item.daysUntilPurge <= 0
      ? "Deletes today"
      : `Auto-deletes in ${item.daysUntilPurge} day${item.daysUntilPurge === 1 ? "" : "s"}`;

  return (
    <div className="trash-row">
      <div className="trash-thumb">
        {isImageType(asset.fileType) ? (
          <img src={asset.storageUrl} alt="" loading="lazy" />
        ) : (
          <span className="trash-thumb-fallback">{asset.fileType.toUpperCase()}</span>
        )}
      </div>
      <div className="trash-meta">
        <span className="trash-code">{asset.assetCode}</span>
        <span className="trash-name">{asset.title || asset.fileName}</span>
        <span className="trash-days">{daysLabel}</span>
      </div>

      {confirmingPurge ? (
        <div className="trash-confirm">
          <span>Delete forever? This can’t be undone.</span>
          <button type="button" className="med-btn med-btn-danger med-btn-sm" disabled={busy} onClick={() => void purge()}>
            {busy ? "Deleting…" : "Confirm"}
          </button>
          <button type="button" className="med-btn med-btn-ghost med-btn-sm" disabled={busy} onClick={() => setConfirmingPurge(false)}>
            Cancel
          </button>
        </div>
      ) : (
        <div className="trash-actions">
          <button type="button" className="med-btn med-btn-ghost med-btn-sm" disabled={busy} onClick={() => void restore()}>
            Restore
          </button>
          <button type="button" className="med-btn med-btn-danger med-btn-sm" disabled={busy} onClick={() => setConfirmingPurge(true)}>
            Delete forever
          </button>
        </div>
      )}
    </div>
  );
}

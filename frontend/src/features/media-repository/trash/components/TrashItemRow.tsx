import { useState } from "react";
import { AlertTriangle, ImageOff, RotateCcw, Trash2 } from "lucide-react";
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
      setBusy(false);
    }
  }

  const urgent = item.daysUntilPurge <= 3;
  const daysLabel =
    item.daysUntilPurge <= 0
      ? "Deletes today"
      : `${item.daysUntilPurge} day${item.daysUntilPurge === 1 ? "" : "s"} left`;

  return (
    <li className={`trash-row${busy ? " is-busy" : ""}`}>
      <div className="trash-thumb">
        {isImageType(asset.fileType) ? (
          <img src={asset.storageUrl} alt="" loading="lazy" />
        ) : (
          <span className="trash-thumb-fallback">
            <ImageOff size={18} aria-hidden="true" />
          </span>
        )}
      </div>

      <div className="trash-meta">
        <span className="trash-name" title={asset.title || asset.fileName}>
          {asset.title || asset.fileName}
        </span>
        <span className="trash-sub">
          <span className="trash-code">{asset.assetCode}</span>
          <span className={`trash-days ${urgent ? "urgent" : ""}`}>
            <Trash2 size={11} aria-hidden="true" />
            Auto-deletes in {daysLabel}
          </span>
        </span>
      </div>

      {confirmingPurge ? (
        <div className="trash-confirm" role="group" aria-label="Confirm permanent deletion">
          <span className="trash-confirm-msg">
            <AlertTriangle size={14} aria-hidden="true" />
            Delete forever? This can’t be undone.
          </span>
          <button type="button" className="med-btn med-btn-ghost med-btn-sm" disabled={busy} onClick={() => setConfirmingPurge(false)}>
            Cancel
          </button>
          <button type="button" className="med-btn med-btn-danger med-btn-sm" disabled={busy} onClick={() => void purge()}>
            {busy ? "Deleting…" : "Delete forever"}
          </button>
        </div>
      ) : (
        <div className="trash-actions">
          <button
            type="button"
            className="med-btn med-btn-ghost med-btn-sm"
            disabled={busy}
            onClick={() => void restore()}
          >
            <RotateCcw size={14} aria-hidden="true" />
            Restore
          </button>
          <button
            type="button"
            className="trash-danger-link"
            disabled={busy}
            aria-label={`Delete ${asset.assetCode} forever`}
            onClick={() => setConfirmingPurge(true)}
          >
            <Trash2 size={14} aria-hidden="true" />
            Delete forever
          </button>
        </div>
      )}
    </li>
  );
}

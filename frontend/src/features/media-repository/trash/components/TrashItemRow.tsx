import { ImageOff, RotateCcw, Trash2 } from "lucide-react";
import type { TrashItem } from "../../../../api/mediaApi";

interface TrashItemRowProps {
  item: TrashItem;
  busy: boolean;
  selected: boolean;
  onToggleSelect: (assetId: string) => void;
  onRestore: (assetId: string) => void;
  onRequestDelete: (item: TrashItem) => void;
}

function isImageType(fileType: string) {
  return ["jpeg", "jpg", "png", "webp", "gif"].includes(fileType.toLowerCase());
}

export default function TrashItemRow({ item, busy, selected, onToggleSelect, onRestore, onRequestDelete }: TrashItemRowProps) {
  const { asset } = item;

  const urgent = item.daysUntilPurge <= 3;
  const daysLabel =
    item.daysUntilPurge <= 0
      ? "Deletes today"
      : `${item.daysUntilPurge} day${item.daysUntilPurge === 1 ? "" : "s"} left`;

  return (
    <li className={`trash-row${busy ? " is-busy" : ""}${selected ? " is-selected" : ""}`}>
      <label className="trash-select" title="Select">
        <input
          type="checkbox"
          checked={selected}
          disabled={busy}
          onChange={() => onToggleSelect(asset.id)}
          aria-label={`Select ${asset.assetCode}`}
        />
      </label>

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

      <div className="trash-actions">
        <button
          type="button"
          className="med-btn med-btn-ghost med-btn-sm"
          disabled={busy}
          onClick={() => onRestore(asset.id)}
        >
          <RotateCcw size={14} aria-hidden="true" />
          Restore
        </button>
        <button
          type="button"
          className="trash-danger-link"
          disabled={busy}
          aria-label={`Delete ${asset.assetCode}`}
          onClick={() => onRequestDelete(item)}
        >
          <Trash2 size={14} aria-hidden="true" />
          Delete
        </button>
      </div>
    </li>
  );
}

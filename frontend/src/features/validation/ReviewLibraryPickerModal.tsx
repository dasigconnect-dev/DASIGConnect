import { useEffect, useMemo } from "react";
import { createPortal } from "react-dom";
import { useToast } from "../../context/ToastContext";
import MediaLibraryTab from "../../components/media/MediaLibraryTab";
import type { SubmissionMediaItem } from "../../types/media";

interface Props {
  excludeIds: string[];
  totalMediaCount?: number;
  onAdd: (items: SubmissionMediaItem[]) => void;
  onRemove?: (assetId: string) => void;
  onClose: () => void;
}

/**
 * Media Library picker for the review-queue editor. Reuses the composer's
 * MediaLibraryTab (search + category / type filters + grid + multi-select add
 * bar) — no device upload, no AI recommendations. Reviewers are network-wide,
 * so it opens across all institutions.
 */
export default function ReviewLibraryPickerModal({
  excludeIds,
  totalMediaCount,
  onAdd,
  onRemove,
  onClose,
}: Props) {
  const toast = useToast();
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    window.addEventListener("keydown", onKey);
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      window.removeEventListener("keydown", onKey);
      document.body.style.overflow = prev;
    };
  }, [onClose]);

  const alreadyAddedIds = useMemo(() => new Set(excludeIds), [excludeIds]);

  function handleAddItems(items: SubmissionMediaItem[]) {
    if (items.length > 0) onAdd(items);
    onClose();
  }

  function handleRemoveItem(assetId: string) {
    if (totalMediaCount != null && totalMediaCount <= 1) {
      toast.warning("At least 1 media must be selected.");
      return;
    }
    onRemove?.(assetId);
  }

  return createPortal(
    <div
      className="val-libpick-backdrop"
      role="dialog"
      aria-modal="true"
      aria-label="Add media from library"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="val-libpick">
        <header className="val-libpick-head">
          <span className="val-libpick-head-icon" aria-hidden="true">
            <i className="ti ti-photo" />
          </span>
          <div>
            <h3>Add from Media Library</h3>
            <p>Assets across all institutions</p>
          </div>
          <button type="button" className="val-libpick-x" onClick={onClose} aria-label="Close">
            <i className="ti ti-x" />
          </button>
        </header>

        <div className="val-libpick-body">
          <MediaLibraryTab
            institutionId={undefined}
            networkView
            showAlbumFilter
            alreadyAddedIds={alreadyAddedIds}
            onAddItems={handleAddItems}
            onRemoveItem={handleRemoveItem}
          />
        </div>
      </div>
    </div>,
    document.body,
  );
}

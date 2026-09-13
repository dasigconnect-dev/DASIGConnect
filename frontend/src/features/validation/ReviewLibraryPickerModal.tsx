import { useEffect, useMemo } from "react";
import { createPortal } from "react-dom";
import MediaLibraryTab from "../../components/media/MediaLibraryTab";
import type { SubmissionMediaItem } from "../../types/media";

interface Props {
  institutionId: string;
  excludeIds: string[];
  onAdd: (items: SubmissionMediaItem[]) => void;
  onClose: () => void;
}

/**
 * Media Library picker for the review-queue editor. Reuses the composer's
 * MediaLibraryTab (search + category / type filters + grid + multi-select add
 * bar) — no device upload, no AI recommendations. Scoped to the submission's
 * institution because reviewers are network-wide.
 */
export default function ReviewLibraryPickerModal({
  institutionId,
  excludeIds,
  onAdd,
  onClose,
}: Props) {
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
            institutionId={institutionId || undefined}
            networkView
            showAlbumFilter
            alreadyAddedIds={alreadyAddedIds}
            onAddItems={handleAddItems}
          />
        </div>
      </div>
    </div>,
    document.body,
  );
}

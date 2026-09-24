import { useEffect, useMemo } from "react";
import { createPortal } from "react-dom";
import "../../styles/media-picker.css";
import AiSuggestedMediaTab from "../../components/media/AiSuggestedMediaTab";
import { useAiMediaSuggestions } from "../../hooks/useAiMediaSuggestions";
import type { SubmissionMediaItem } from "../../types/media";

interface Props {
  submissionId: string;
  excludeIds: string[];
  eventTitle: string;
  caption: string;
  category: string;
  tags: string[];
  onAdd: (items: SubmissionMediaItem[]) => void;
  onClose: () => void;
}

/**
 * AI-suggested media for the review-queue editor, opened from the Media tab's
 * "AI suggestions" button. It used to render inline under the selected media,
 * where its wide result grid overflowed the edit panel; as a dialog it gets the
 * same room as the Media Library picker. Suggestions are only fetched while
 * this is open.
 */
export default function ReviewAiSuggestionsModal({
  submissionId,
  excludeIds,
  eventTitle,
  caption,
  category,
  tags,
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

  const suggestions = useAiMediaSuggestions(submissionId, eventTitle, caption, category, tags);
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
      aria-label="AI media suggestions"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="val-libpick">
        <header className="val-libpick-head">
          <span className="val-libpick-head-icon is-ai" aria-hidden="true">
            <i className="ti ti-sparkles" />
          </span>
          <div>
            <h3>AI media suggestions</h3>
            <p>Library assets that match this post's title, caption, and hashtags</p>
          </div>
          <button type="button" className="val-libpick-x" onClick={onClose} aria-label="Close">
            <i className="ti ti-x" />
          </button>
        </header>

        <div className="val-libpick-body">
          <AiSuggestedMediaTab
            suggestions={suggestions}
            submissionId={submissionId}
            alreadyAddedIds={alreadyAddedIds}
            eventTitle={eventTitle}
            caption={caption}
            category={category}
            tags={tags}
            onAddItems={handleAddItems}
          />
        </div>
      </div>
    </div>,
    document.body,
  );
}

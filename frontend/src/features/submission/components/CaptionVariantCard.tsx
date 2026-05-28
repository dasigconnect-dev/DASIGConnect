import { useState } from "react";
import type { CaptionVariant, CaptionTone } from "../../../api/aiApi";

interface Props {
  variant: CaptionVariant;
  submissionId: string;
  onApply: (caption: string, action: "use" | "use_then_edited", tone: CaptionTone) => void;
  onRegenerate: () => void;
  onDismiss: () => void;
}

const TONE_LABELS: Record<CaptionTone, string> = {
  professional: "Professional",
  community: "Community",
  energetic: "Energetic",
};

const TONE_COLORS: Record<CaptionTone, string> = {
  professional: "caption-tone-professional",
  community: "caption-tone-community",
  energetic: "caption-tone-energetic",
};

export default function CaptionVariantCard({
  variant,
  onApply,
  onRegenerate,
  onDismiss,
}: Props) {
  const [editMode, setEditMode] = useState(false);
  const [editedText, setEditedText] = useState(variant.caption);

  const charCount = editMode ? editedText.length : variant.caption.length;
  const charClass =
    charCount < 80 ? "char-warn" : charCount > 280 ? "char-over" : "char-ok";

  function handleApply() {
    onApply(variant.caption, "use", variant.tone);
  }

  function handleApplyEdited() {
    onApply(editedText, "use_then_edited", variant.tone);
    setEditMode(false);
  }

  return (
    <div className="caption-card">
      <div className="caption-card-header">
        <span className={`caption-tone-badge ${TONE_COLORS[variant.tone]}`}>
          {TONE_LABELS[variant.tone]}
        </span>
        <span className={`caption-char-count ${charClass}`}>
          {charCount} / 280
        </span>
      </div>

      {editMode ? (
        <textarea
          className="caption-edit-area"
          value={editedText}
          onChange={(e) => setEditedText(e.target.value)}
          rows={4}
          autoFocus
        />
      ) : (
        <p className="caption-text">{variant.caption}</p>
      )}

      <div className="caption-card-actions">
        {editMode ? (
          <>
            <button
              className="caption-btn caption-btn-primary"
              onClick={handleApplyEdited}
            >
              Apply Edited Caption
            </button>
            <button
              className="caption-btn caption-btn-ghost"
              onClick={() => { setEditMode(false); setEditedText(variant.caption); }}
            >
              Cancel
            </button>
          </>
        ) : (
          <>
            <button
              className="caption-btn caption-btn-primary"
              onClick={handleApply}
            >
              Apply
            </button>
            <button
              className="caption-btn caption-btn-secondary"
              onClick={() => setEditMode(true)}
            >
              Edit
            </button>
            <button
              className="caption-btn caption-btn-secondary"
              onClick={onRegenerate}
            >
              Re-generate
            </button>
            <button
              className="caption-btn caption-btn-ghost"
              onClick={onDismiss}
            >
              Dismiss
            </button>
          </>
        )}
      </div>
    </div>
  );
}

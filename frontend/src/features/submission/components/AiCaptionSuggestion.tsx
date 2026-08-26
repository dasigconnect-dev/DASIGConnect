import { useState } from "react";
import type { CaptionVariant, CaptionTone } from "../../../api/aiApi";

interface Props {
  variants: CaptionVariant[];
  onApply: (caption: string, tone: CaptionTone, action: "use" | "use_then_edited") => void;
  onDismissOne: (tone: CaptionTone) => void;
  onDismissAll: () => void;
  onRegenerate: () => void;
}

const TONE_LABELS: Record<CaptionTone, string> = {
  professional: "Professional",
  community: "Community",
  energetic: "Energetic",
};

export default function AiCaptionSuggestion({
  variants,
  onApply,
  onDismissOne,
  onDismissAll,
  onRegenerate,
}: Props) {
  const [expanded, setExpanded] = useState<CaptionTone | null>(null);

  return (
    <div className="ai-sugg-panel">
      <div className="ai-sugg-header">
        <span className="ai-sugg-header-title">
          <i className="ti ti-sparkles" aria-hidden />
          AI Suggestions
          <span className="ai-sugg-disclaimer">Review before applying</span>
        </span>
        <div className="ai-sugg-header-actions">
          <button
            type="button"
            className="ai-sugg-action-btn"
            onClick={(e) => { e.preventDefault(); onRegenerate(); }}
            title="Generate new suggestions"
          >
            <i className="ti ti-refresh" aria-hidden /> Regenerate
          </button>
          <button
            type="button"
            className="ai-sugg-action-btn ai-sugg-action-btn--close"
            onClick={(e) => { e.preventDefault(); onDismissAll(); }}
            title="Dismiss suggestions"
          >
            <i className="ti ti-x" aria-hidden />
          </button>
        </div>
      </div>

      <div className="ai-sugg-list">
        {variants.map((v) => (
          <SuggestionRow
            key={v.tone}
            variant={v}
            expanded={expanded === v.tone}
            onToggle={() =>
              setExpanded((prev) => (prev === v.tone ? null : v.tone))
            }
            onApply={(caption, action) => onApply(caption, v.tone, action)}
            onDismiss={() => onDismissOne(v.tone)}
          />
        ))}
      </div>
    </div>
  );
}

function SuggestionRow({
  variant,
  expanded,
  onToggle,
  onApply,
  onDismiss,
}: {
  variant: CaptionVariant;
  expanded: boolean;
  onToggle: () => void;
  onApply: (caption: string, action: "use" | "use_then_edited") => void;
  onDismiss: () => void;
}) {
  const [editText, setEditText] = useState(variant.caption);
  const [editing, setEditing] = useState(false);

  function handleCancelEdit() {
    setEditing(false);
    setEditText(variant.caption);
  }

  return (
    <div className={`ai-sugg-row${expanded ? " ai-sugg-row--open" : ""}`}>
      <div className="ai-sugg-row-main">
        <span className={`ai-sugg-tone-badge ai-sugg-tone--${variant.tone}`}>
          {TONE_LABELS[variant.tone]}
        </span>

        <button
          type="button"
          className="ai-sugg-preview-btn"
          onClick={(e) => { e.preventDefault(); onToggle(); }}
          title={expanded ? "Collapse" : "Expand to edit"}
        >
          <span className="ai-sugg-preview-text">{variant.caption}</span>
          <i
            className={`ti ${expanded ? "ti-chevron-up" : "ti-chevron-down"}`}
            aria-hidden
          />
        </button>

        <div className="ai-sugg-row-actions">
          <button
            type="button"
            className="ai-sugg-use-btn"
            onClick={(e) => {
              e.preventDefault();
              onApply(variant.caption.slice(0, 500), "use");
            }}
          >
            Use
          </button>
          <button
            type="button"
            className="ai-sugg-dismiss-btn"
            onClick={(e) => { e.preventDefault(); onDismiss(); }}
            title="Dismiss this suggestion"
          >
            <i className="ti ti-x" aria-hidden />
          </button>
        </div>
      </div>

      {expanded && (
        <div className="ai-sugg-expanded">
          {editing ? (
            <textarea
              className="ai-sugg-edit-area"
              value={editText}
              onChange={(e) =>
                setEditText(e.target.value.slice(0, 500))
              }
              rows={3}
              autoFocus
            />
          ) : (
            <p className="ai-sugg-full-text">{variant.caption}</p>
          )}

          <div className="ai-sugg-expanded-footer">
            <span className="ai-sugg-char-count">
              {(editing ? editText : variant.caption).length} / 500
            </span>
            {editing ? (
              <>
                <button
                  type="button"
                  className="ai-sugg-use-btn"
                  onClick={(e) => {
                    e.preventDefault();
                    onApply(editText.slice(0, 500), "use_then_edited");
                    setEditing(false);
                  }}
                >
                  Apply Edited
                </button>
                <button
                  type="button"
                  className="ai-sugg-action-btn"
                  onClick={(e) => { e.preventDefault(); handleCancelEdit(); }}
                >
                  Cancel
                </button>
              </>
            ) : (
              <button
                type="button"
                className="ai-sugg-action-btn"
                onClick={(e) => { e.preventDefault(); setEditing(true); }}
              >
                <i className="ti ti-pencil" aria-hidden /> Edit
              </button>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

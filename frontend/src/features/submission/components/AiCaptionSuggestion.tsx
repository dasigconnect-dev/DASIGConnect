import { useState } from "react";
import type { CaptionVariant, CaptionTone } from "../../../api/aiApi";
import "./AiCaptionSuggestion.css";

interface Props {
  variants: CaptionVariant[];
  onApply: (
    caption: string,
    tone: CaptionTone,
    action: "use" | "use_then_edited",
    qualityRating?: number | null,
  ) => void;
  onDismissOne: (tone: CaptionTone, qualityRating?: number | null) => void;
  onDismissAll: (qualityRating?: number | null) => void;
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
  const [qualityRating, setQualityRating] = useState<number | null>(null);

  return (
    <div className="ai-sugg-panel" data-testid="ai-caption-suggestion-panel">
      <div className="ai-sugg-header">
        <span className="ai-sugg-header-title">
          <i className="ti ti-sparkles" aria-hidden />
          AI draft ready — Use / Edit / Dismiss
          <span className="ai-sugg-disclaimer">Review before applying</span>
        </span>
        <div className="ai-sugg-header-actions">
          <div
            className="ai-sugg-rating-group"
            role="group"
            aria-label="Rate AI caption quality (1 to 5)"
          >
            {[1, 2, 3, 4, 5].map((score) => (
              <button
                key={score}
                type="button"
                className={`ai-sugg-action-btn${qualityRating === score ? " active" : ""}`}
                aria-pressed={qualityRating === score}
                onClick={(e) => {
                  e.preventDefault();
                  setQualityRating((prev) => (prev === score ? null : score));
                }}
                title={`Rate AI caption ${score} out of 5`}
              >
                {score}★
              </button>
            ))}
          </div>
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
            onClick={(e) => { e.preventDefault(); onDismissAll(qualityRating); }}
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
            onApply={(caption, action) =>
              onApply(caption, v.tone, action, qualityRating)
            }
            onDismiss={() => onDismissOne(v.tone, qualityRating)}
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
              onApply(variant.caption, "use");
            }}
          >
            Use
          </button>
          <button
            type="button"
            className="ai-sugg-action-btn"
            onClick={(e) => {
              e.preventDefault();
              if (!expanded) onToggle();
              setEditing(true);
            }}
          >
            Edit
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
              onChange={(e) => setEditText(e.target.value)}
              rows={3}
              autoFocus
            />
          ) : (
            <p className="ai-sugg-full-text">{variant.caption}</p>
          )}

          <div className="ai-sugg-expanded-footer">
            <span className="ai-sugg-char-count">
              {(editing ? editText : variant.caption).length} chars
            </span>
            {editing ? (
              <>
                <button
                  type="button"
                  className="ai-sugg-use-btn"
                  onClick={(e) => {
                    e.preventDefault();
                    onApply(editText, "use_then_edited");
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

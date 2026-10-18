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
  onBrowseTemplates?: () => void;
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
  onBrowseTemplates,
}: Props) {
  const [qualityRating, setQualityRating] = useState<number | null>(null);

  return (
    <div className="ai-sugg-panel" data-testid="ai-caption-suggestion-panel">
      {/* Visual Accent Top Bar */}
      <div className="ai-sugg-accent-bar" aria-hidden="true" />

      {/* Header */}
      <div className="ai-sugg-header">
        <div className="ai-sugg-header-main">
          <span className="ai-sugg-badge-icon" aria-hidden="true">
            <i className="ti ti-sparkles" />
          </span>
          <div className="ai-sugg-header-text">
            <span className="ai-sugg-header-title">
              AI draft ready — Use / Edit / Dismiss
            </span>
            <span className="ai-sugg-disclaimer">Review before applying</span>
          </div>
        </div>

        <div className="ai-sugg-header-actions">
          <div
            className="ai-sugg-rating-group"
            role="group"
            aria-label="Rate AI caption quality (1 to 5)"
          >
            <span className="ai-sugg-rating-label">Rating:</span>
            {[1, 2, 3, 4, 5].map((score) => (
              <button
                key={score}
                type="button"
                className={`ai-sugg-rating-star${qualityRating && qualityRating >= score ? " filled" : ""}${qualityRating === score ? " selected" : ""}`}
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
            className="ai-sugg-header-btn ai-sugg-btn--regenerate"
            onClick={(e) => {
              e.preventDefault();
              onRegenerate();
            }}
            title="Generate new suggestions"
          >
            <i className="ti ti-refresh" aria-hidden="true" />
            <span>Regenerate</span>
          </button>

          <button
            type="button"
            className="ai-sugg-header-btn ai-sugg-btn--close"
            onClick={(e) => {
              e.preventDefault();
              onDismissAll(qualityRating);
            }}
            title="Dismiss suggestions"
            aria-label="Dismiss AI draft"
          >
            <i className="ti ti-x" aria-hidden="true" />
          </button>
        </div>
      </div>

      {/* Suggestions List */}
      <div className="ai-sugg-list">
        {variants.map((v) => (
          <SuggestionCard
            key={v.tone}
            variant={v}
            onApply={(caption, action) =>
              onApply(caption, v.tone, action, qualityRating)
            }
            onDismiss={() => onDismissOne(v.tone, qualityRating)}
          />
        ))}
      </div>

      {/* Footer Helper: Browse templates if preferred */}
      {onBrowseTemplates && (
        <div className="ai-sugg-footer-note">
          <i className="ti ti-template" aria-hidden="true" />
          <span>Prefer a standard structure?</span>
          <button
            type="button"
            className="ai-sugg-template-link"
            onClick={(e) => {
              e.preventDefault();
              onBrowseTemplates();
            }}
          >
            Choose from templates &rarr;
          </button>
        </div>
      )}
    </div>
  );
}

function SuggestionCard({
  variant,
  onApply,
  onDismiss,
}: {
  variant: CaptionVariant;
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
    <div className="ai-sugg-card">
      <div className="ai-sugg-card-head">
        <span className={`ai-sugg-tone-badge ai-sugg-tone--${variant.tone}`}>
          <i className="ti ti-tag" aria-hidden="true" />
          {TONE_LABELS[variant.tone]}
        </span>
        <div className="ai-sugg-card-actions">
          <button
            type="button"
            className="ai-sugg-use-btn"
            onClick={(e) => {
              e.preventDefault();
              onApply(editing ? editText : variant.caption, editing ? "use_then_edited" : "use");
            }}
          >
            <i className="ti ti-check" aria-hidden="true" />
            Use
          </button>
          {!editing ? (
            <button
              type="button"
              className="ai-sugg-btn-secondary"
              onClick={(e) => {
                e.preventDefault();
                setEditing(true);
              }}
            >
              <i className="ti ti-pencil" aria-hidden="true" />
              Edit
            </button>
          ) : (
            <button
              type="button"
              className="ai-sugg-btn-secondary"
              onClick={(e) => {
                e.preventDefault();
                handleCancelEdit();
              }}
            >
              Cancel
            </button>
          )}
          <button
            type="button"
            className="ai-sugg-dismiss-btn"
            onClick={(e) => {
              e.preventDefault();
              onDismiss();
            }}
            title="Dismiss this suggestion"
            aria-label="Dismiss this suggestion"
          >
            <i className="ti ti-x" aria-hidden="true" />
          </button>
        </div>
      </div>

      <div className="ai-sugg-card-body">
        {editing ? (
          <div className="ai-sugg-edit-wrap">
            <textarea
              className="ai-sugg-edit-area"
              value={editText}
              onChange={(e) => setEditText(e.target.value)}
              rows={4}
              autoFocus
            />
            <div className="ai-sugg-edit-footer">
              <span className="ai-sugg-char-count">{editText.length} characters</span>
            </div>
          </div>
        ) : (
          <p className="ai-sugg-caption-text">{variant.caption}</p>
        )}
      </div>
    </div>
  );
}

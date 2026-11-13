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
  onOpenDialog?: () => void;
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
  onDismissAll,
  onRegenerate,
  onOpenDialog,
}: Props) {
  const [qualityRating, setQualityRating] = useState<number | null>(null);
  const variant = variants[0];

  if (!variant) return null;

  const handleOpenDialog = () => {
    if (onOpenDialog) {
      onOpenDialog();
    } else {
      onRegenerate();
    }
  };

  return (
    <div
      className="ai-sugg-panel"
      data-testid="ai-caption-suggestion-panel"
      role="button"
      tabIndex={0}
      onClick={handleOpenDialog}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          handleOpenDialog();
        }
      }}
      title="Click to edit or refine in AI Caption Assistant"
    >
      <div className="ai-sugg-header">
        <div className="ai-sugg-header-main">
          <span className="ai-sugg-badge-icon" aria-hidden="true">
            <i className="ti ti-sparkles" />
          </span>
          <div className="ai-sugg-header-text">
            <div className="ai-sugg-header-title-row">
              <span className="ai-sugg-header-title">AI Draft Ready</span>
              <span className="ai-sugg-tone-pill">{TONE_LABELS[variant.tone]}</span>
            </div>
            <span className="ai-sugg-disclaimer">
              Click to edit or refine with AI, or use directly
            </span>
          </div>
        </div>

        <div
          className="ai-sugg-header-actions"
          onClick={(e) => e.stopPropagation()}
        >
          <div
            className="ai-sugg-rating-group"
            role="group"
            aria-label="Rate AI caption quality (1 to 5)"
          >
            {[1, 2, 3, 4, 5].map((score) => (
              <button
                key={score}
                type="button"
                className={`ai-sugg-rating-star${qualityRating && qualityRating >= score ? " filled" : ""}${qualityRating === score ? " selected" : ""}`}
                aria-pressed={qualityRating === score}
                onClick={(e) => {
                  e.preventDefault();
                  e.stopPropagation();
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
            className="ai-sugg-btn ai-sugg-btn--edit"
            onClick={(e) => {
              e.preventDefault();
              e.stopPropagation();
              handleOpenDialog();
            }}
            title="Edit or refine in AI Caption Assistant"
          >
            <i className="ti ti-pencil" aria-hidden="true" />
            <span>Edit</span>
          </button>

          <button
            type="button"
            className="ai-sugg-btn ai-sugg-btn--dismiss"
            onClick={(e) => {
              e.preventDefault();
              e.stopPropagation();
              onDismissAll(qualityRating);
            }}
            title="Dismiss AI draft"
          >
            <span>Dismiss</span>
          </button>

          <button
            type="button"
            className="ai-sugg-btn ai-sugg-btn--use"
            onClick={(e) => {
              e.preventDefault();
              e.stopPropagation();
              onApply(variant.caption, variant.tone, "use", qualityRating);
            }}
            title="Apply this caption to your post"
          >
            <i className="ti ti-check" aria-hidden="true" />
            <span>Use</span>
          </button>
        </div>
      </div>

      <div className="ai-sugg-body">
        <p className="ai-sugg-caption-text">{variant.caption}</p>
      </div>
    </div>
  );
}

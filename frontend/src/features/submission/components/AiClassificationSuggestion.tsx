import type { ClassificationSuggestions } from "../../../api/aiApi";

interface Props {
  suggestions: ClassificationSuggestions;
  currentCategory: string;
  currentTags: string[];
  onAcceptCategory: (category: string) => void;
  onAcceptTags: (tags: string[]) => void;
  onDismiss: () => void;
}

/**
 * Inline panel displaying AI-suggested category and tags for a submission (UC-3.3).
 * Reusable — accepts suggestions and callbacks; all state lives in the parent hook.
 */
export default function AiClassificationSuggestion({
  suggestions,
  currentCategory,
  currentTags,
  onAcceptCategory,
  onAcceptTags,
  onDismiss,
}: Props) {
  const { suggestedCategory, suggestedTags, confidence, assetCount } = suggestions;

  const hasCategory =
    !!suggestedCategory && suggestedCategory !== currentCategory;
  const newTags = suggestedTags.filter((t) => !currentTags.includes(t));
  const hasNewTags = newTags.length > 0;

  if (!hasCategory && !hasNewTags) {
    const stillAnalyzing = assetCount === 0;
    return (
      <div className="ai-sugg-panel">
        <div className="ai-sugg-header">
          <span className="ai-sugg-header-title">
            <i className="ti ti-sparkles" aria-hidden />
            AI Suggestions
            <span className="ai-sugg-disclaimer">
              {stillAnalyzing ? "Analyzing…" : "Already applied"}
            </span>
          </span>
          <button
            type="button"
            className="ai-sugg-action-btn ai-sugg-action-btn--close"
            onClick={(e) => { e.preventDefault(); onDismiss(); }}
            title="Dismiss"
          >
            <i className="ti ti-x" aria-hidden />
          </button>
        </div>
        <p className="ai-sugg-empty-note">
          {stillAnalyzing
            ? "Images are still being analyzed. Try again in a moment."
            : "The suggested category and tags are already set on this submission."}
        </p>
      </div>
    );
  }

  const confidencePct =
    confidence != null ? Math.round(confidence * 100) : null;

  return (
    <div className="ai-sugg-panel">
      <div className="ai-sugg-header">
        <span className="ai-sugg-header-title">
          <i className="ti ti-sparkles" aria-hidden />
          AI Suggestions
          {assetCount > 0 && (
            <span className="ai-sugg-disclaimer">
              based on {assetCount} image{assetCount !== 1 ? "s" : ""}
              {confidencePct != null ? ` · ${confidencePct}% confidence` : ""}
            </span>
          )}
        </span>
        <button
          type="button"
          className="ai-sugg-action-btn ai-sugg-action-btn--close"
          onClick={(e) => { e.preventDefault(); onDismiss(); }}
          title="Dismiss suggestions"
        >
          <i className="ti ti-x" aria-hidden />
        </button>
      </div>

      <div className="ai-classify-body">
        {hasCategory && (
          <div className="ai-classify-row">
            <span className="ai-classify-label">Category</span>
            <span className="ai-sugg-tone-badge ai-sugg-tone--professional">
              {suggestedCategory}
            </span>
            <button
              type="button"
              className="ai-sugg-use-btn"
              onClick={(e) => {
                e.preventDefault();
                onAcceptCategory(suggestedCategory!);
              }}
            >
              Apply
            </button>
          </div>
        )}

        {hasNewTags && (
          <div className="ai-classify-row ai-classify-row--wrap">
            <span className="ai-classify-label">Tags</span>
            <div className="ai-classify-tags">
              {newTags.map((tag) => (
                <button
                  key={tag}
                  type="button"
                  className="ai-classify-tag-chip"
                  onClick={(e) => {
                    e.preventDefault();
                    onAcceptTags([tag]);
                  }}
                  title={`Add tag: ${tag}`}
                >
                  <i className="ti ti-plus" aria-hidden />
                  {tag}
                </button>
              ))}
            </div>
            {newTags.length > 1 && (
              <button
                type="button"
                className="ai-sugg-use-btn"
                onClick={(e) => {
                  e.preventDefault();
                  onAcceptTags(newTags);
                }}
              >
                Add All
              </button>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

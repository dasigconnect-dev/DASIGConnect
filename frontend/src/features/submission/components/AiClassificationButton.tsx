import type { AiClassificationState } from "../../../hooks/useAiClassification";

interface Props {
  state: AiClassificationState;
  canShow: boolean;
  canFetch: boolean;
  onFetch: () => void;
}

/**
 * Pill button that triggers AI category and tag suggestions (UC-3.3).
 * Hidden when there is no media at all; disabled (with tooltip) when draft is not yet saved.
 */
export default function AiClassificationButton({ state, canShow, canFetch, onFetch }: Props) {
  if (!canShow) return null;

  const isLoading = state === "loading";
  const isError = state === "error";
  const isSaveNeeded = !canFetch;

  return (
    <button
      type="button"
      className={[
        "ai-caption-btn",
        isLoading ? "ai-caption-btn--loading" : "",
        isError ? "ai-caption-btn--error" : "",
      ]
        .filter(Boolean)
        .join(" ")}
      onClick={(e) => {
        e.preventDefault();
        e.stopPropagation();
        onFetch();
      }}
      disabled={isLoading || isSaveNeeded}
      title={
        isSaveNeeded
          ? "Save your draft first to get AI suggestions"
          : isError
          ? "Suggestions unavailable — click to retry"
          : "Suggest category and tags based on your attached media"
      }
    >
      {isLoading ? (
        <>
          <span className="ai-caption-spinner" aria-hidden />
          Suggesting...
        </>
      ) : isError ? (
        <>
          <i className="ti ti-refresh" aria-hidden />
          Retry
        </>
      ) : (
        <>
          <i className="ti ti-sparkles" aria-hidden />
          AI Suggest
        </>
      )}
    </button>
  );
}

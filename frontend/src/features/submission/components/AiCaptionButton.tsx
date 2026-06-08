import type { AiCaptionState } from "../../../hooks/useAiCaptionAssist";

interface Props {
  state: AiCaptionState;
  canSuggest: boolean;
  rateLimitReset: number | null;
  onSuggest: () => void;
  /** Shown as a tooltip on the disabled button when suggestion isn't available yet. */
  disabledHint?: string;
}

function formatResetTime(epochSeconds: number): string {
  return new Date(epochSeconds * 1000).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
  });
}

export default function AiCaptionButton({
  state,
  canSuggest,
  rateLimitReset,
  onSuggest,
  disabledHint,
}: Props) {
  // Caption suggestion is image-based; when it isn't available yet (no media / unsaved draft)
  // show a disabled affordance with a hint instead of hiding it, so the action is discoverable.
  if (!canSuggest) {
    return (
      <button
        type="button"
        className="ai-caption-btn ai-caption-btn--disabled"
        disabled
        style={{ opacity: 0.55, cursor: "not-allowed" }}
        title={disabledHint ?? "Add media first to generate a caption."}
      >
        <i className="ti ti-sparkles" aria-hidden />
        Suggest Caption
      </button>
    );
  }

  if (state === "rate-limited") {
    const resetStr = rateLimitReset ? formatResetTime(rateLimitReset) : null;
    return (
      <span
        className="ai-caption-btn ai-caption-btn--limited"
        title={resetStr ? `Available again at ${resetStr}` : "Hourly limit reached"}
      >
        <i className="ti ti-clock" />
        {resetStr ? `Retry at ${resetStr}` : "Limit reached"}
      </span>
    );
  }

  const isLoading = state === "loading";
  const isError =
    state === "error-timeout" || state === "error-unavailable";

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
        onSuggest();
      }}
      disabled={isLoading}
      title={
        isError
          ? "Caption generation failed — click to retry"
          : "Generate a suggested caption based on your selected media and event details."
      }
    >
      {isLoading ? (
        <>
          <span className="ai-caption-spinner" aria-hidden />
          Generating...
        </>
      ) : isError ? (
        <>
          <i className="ti ti-refresh" aria-hidden />
          Retry
        </>
      ) : (
        <>
          <i className="ti ti-sparkles" aria-hidden />
          Suggest Caption
        </>
      )}
    </button>
  );
}

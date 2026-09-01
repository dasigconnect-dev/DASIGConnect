import type { AiCaptionState } from "../../../hooks/useAiCaptionAssist";

interface Props {
  state: AiCaptionState;
  canSuggest: boolean;
  rateLimitReset: number | null;
  notice?: string | null;
  onSuggest: () => void;
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
  notice,
  onSuggest,
}: Props) {
  if (!canSuggest) return null;

  if (state === "rate-limited") {
    const resetStr = rateLimitReset ? formatResetTime(rateLimitReset) : null;
    return (
      <span className="ai-caption-control">
        <span
          className="ai-caption-btn ai-caption-btn--limited"
          title={resetStr ? `Available again at ${resetStr}` : "Hourly limit reached"}
        >
          <i className="ti ti-clock" aria-hidden />
          {resetStr ? `Retry at ${resetStr}` : "Limit reached"}
        </span>
      </span>
    );
  }

  const isLoading = state === "loading";
  const isTimeout = state === "error-timeout";
  const isUnavailable = state === "error-unavailable";
  const isError = isTimeout || isUnavailable;

  return (
    <span className="ai-caption-control">
      <button
        type="button"
        className={[
          "ai-caption-btn",
          isLoading ? "ai-caption-btn--loading" : "",
          isError ? "ai-caption-btn--error" : "",
        ]
          .filter(Boolean)
          .join(" ")}
        onClick={(event) => {
          event.preventDefault();
          event.stopPropagation();
          onSuggest();
        }}
        disabled={isLoading || isUnavailable}
        title={
          isUnavailable
            ? "AI caption service is unavailable."
            : isTimeout
              ? "AI request timed out. Click to retry."
              : "Generate a suggested caption based on selected media and event details."
        }
      >
        {isLoading ? (
          <>
            <span className="ai-caption-spinner" aria-hidden />
            Generating...
          </>
        ) : isUnavailable ? (
          <>
            <i className="ti ti-cloud-off" aria-hidden />
            AI unavailable
          </>
        ) : isTimeout ? (
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
      {notice && (
        <span className="ai-caption-notice" role="status">
          {notice}
        </span>
      )}
    </span>
  );
}

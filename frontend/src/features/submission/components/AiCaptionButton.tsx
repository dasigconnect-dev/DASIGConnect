import type { AiCaptionState } from "../../../hooks/useAiCaptionAssist";

interface Props {
  state: AiCaptionState;
  canSuggest: boolean;
  rateLimitReset: number | null;
  notice?: string | null;
  hideInlineNotice?: boolean;
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
  hideInlineNotice = false,
  onSuggest,
}: Props) {
  if (!canSuggest) return null;

  if (state === "rate-limited") {
    const resetStr = rateLimitReset ? formatResetTime(rateLimitReset) : null;
    return (
      <span className="ai-caption-control" style={{ flexShrink: 0, minWidth: "max-content" }}>
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
  const isUnavailable =
    state === "error-unavailable" ||
    Boolean(notice && notice.toLowerCase().includes("unavailable"));
  const isError = isTimeout || isUnavailable;

  return (
    <span className="ai-caption-control" style={{ flexShrink: 0, minWidth: "max-content" }}>
      <button
        type="button"
        className={[
          "ai-caption-btn",
          isLoading ? "ai-caption-btn--loading" : "",
          isUnavailable ? "ai-caption-btn--unavailable" : "",
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
            ? notice ?? "AI caption service is unavailable. You can still write captions manually."
            : isTimeout
              ? "AI request timed out. Click to retry."
              : "Generate a suggested caption based on selected media and event details (auto-saves draft if needed)."
        }
      >
        {isLoading ? (
          <>
            <span className="ai-caption-spinner" aria-hidden />
            <span className="ai-caption-text">Generating...</span>
          </>
        ) : isUnavailable ? (
          <>
            <i className="ti ti-cloud-x" aria-hidden />
            <span className="ai-caption-text">AI Caption Unavailable</span>
          </>
        ) : isTimeout ? (
          <>
            <i className="ti ti-refresh" aria-hidden />
            <span className="ai-caption-text">Retry</span>
          </>
        ) : (
          <>
            <i className="ti ti-sparkles" aria-hidden />
            <span className="ai-caption-text">Suggest Caption</span>
          </>
        )}
      </button>
      {notice && !hideInlineNotice && !isUnavailable && (
        <span className="ai-caption-notice" role="status">
          {notice}
        </span>
      )}
    </span>
  );
}

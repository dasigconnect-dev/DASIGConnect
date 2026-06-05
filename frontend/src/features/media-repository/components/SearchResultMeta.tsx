interface SearchResultMetaProps {
  /** Explainable reasons the asset matched the query (UC-4.5 transparency). */
  matchReasons?: string[];
  /** Current thumbs state for this result, if any (UC-4.6). */
  feedback?: "up" | "down" | null;
  /** When provided, renders the thumbs up/down feedback controls. */
  onFeedback?: (action: "thumbs_up" | "thumbs_down") => void;
}

/**
 * Shared search-result affordances for media cards/tiles: the match-reason chips
 * (UC-4.5) and the thumbs up/down feedback row (UC-4.6). Reused by the Media Library
 * AssetCard and the Collection AlbumAssetTile so search behaves identically in both.
 * Clicks on the feedback controls stop propagation so they don't trigger the parent's
 * select/open handler.
 */
export default function SearchResultMeta({ matchReasons, feedback = null, onFeedback }: SearchResultMetaProps) {
  const hasReasons = Boolean(matchReasons && matchReasons.length > 0);
  if (!hasReasons && !onFeedback) return null;

  return (
    <>
      {hasReasons && (
        <div className="med-card-reasons" title={`Matched on: ${matchReasons!.join(", ")}`}>
          <svg className="med-card-reasons-icon" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M5 3v4M3 5h4M6 17v4M4 19h4M13 3l2.5 6.5L22 12l-6.5 2.5L13 21l-2.5-6.5L4 12l6.5-2.5L13 3z" />
          </svg>
          {matchReasons!.slice(0, 3).map((reason) => (
            <span key={reason} className="med-card-reason">{reason}</span>
          ))}
          {matchReasons!.length > 3 && (
            <span className="med-card-reason med-card-reason-more">+{matchReasons!.length - 3}</span>
          )}
        </div>
      )}

      {onFeedback && (
        <div
          className="med-card-feedback"
          onClick={(e) => e.stopPropagation()}
          onKeyDown={(e) => e.stopPropagation()}
        >
          <span className="med-card-feedback-label">Helpful?</span>
          <button
            type="button"
            className={`med-feedback-btn${feedback === "up" ? " is-up" : ""}`}
            aria-label="Mark this result helpful"
            aria-pressed={feedback === "up"}
            onClick={(e) => { e.stopPropagation(); onFeedback("thumbs_up"); }}
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M7 10v12" />
              <path d="M15 5.88 14 10h5.83a2 2 0 0 1 1.92 2.56l-2.33 8A2 2 0 0 1 17.5 22H4a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2h2.76a2 2 0 0 0 1.79-1.11L12 2a3.13 3.13 0 0 1 3 3.88Z" />
            </svg>
          </button>
          <button
            type="button"
            className={`med-feedback-btn${feedback === "down" ? " is-down" : ""}`}
            aria-label="Mark this result not helpful"
            aria-pressed={feedback === "down"}
            onClick={(e) => { e.stopPropagation(); onFeedback("thumbs_down"); }}
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M17 14V2" />
              <path d="M9 18.12 10 14H4.17a2 2 0 0 1-1.92-2.56l2.33-8A2 2 0 0 1 6.5 2H20a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-2.76a2 2 0 0 0-1.79 1.11L12 22a3.13 3.13 0 0 1-3-3.88Z" />
            </svg>
          </button>
        </div>
      )}
    </>
  );
}

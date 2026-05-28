interface MediaAssetCardProps {
  id: string;
  storageUrl: string;
  fileName: string;
  fileType: string;
  aiCategory?: string | null;
  similarityScore?: number;
  matchReasons?: string[];
  selected: boolean;
  alreadyAdded: boolean;
  onToggle: () => void;
}

function isVideoType(fileType: string) {
  return ["mp4", "mov", "webm"].includes(fileType.toLowerCase());
}

function matchLabel(score: number) {
  if (score >= 0.8) return "Strong match";
  if (score >= 0.6) return "Good match";
  if (score >= 0.4) return "Related";
  return "Possible match";
}

export default function MediaAssetCard({
  id,
  storageUrl,
  fileName,
  fileType,
  aiCategory,
  similarityScore,
  matchReasons = [],
  selected,
  alreadyAdded,
  onToggle,
}: MediaAssetCardProps) {
  const isVideo = isVideoType(fileType);
  const shortName = fileName.length > 20 ? fileName.slice(0, 17) + "..." : fileName;
  const scorePct = similarityScore != null ? Math.round(similarityScore * 100) : null;
  const scoreLabel = similarityScore != null ? matchLabel(similarityScore) : null;
  const visibleReasons = matchReasons.filter(Boolean).slice(0, 3);
  const matchSummary =
    scorePct != null && scoreLabel
      ? [`${scoreLabel} (${scorePct}% relevance)`, ...visibleReasons].join(" ")
      : fileName;
  const reasonId = `mac-match-${id}`;

  return (
    <button
      type="button"
      className={[
        "mac-card",
        selected ? "mac-card--selected" : "",
        alreadyAdded ? "mac-card--added" : "",
      ]
        .filter(Boolean)
        .join(" ")}
      onClick={onToggle}
      title={alreadyAdded ? `${fileName} (already in post). ${matchSummary}` : matchSummary}
      aria-pressed={selected}
      aria-describedby={visibleReasons.length > 0 ? reasonId : undefined}
    >
      <div className="mac-thumb">
        {isVideo ? (
          <div className="mac-video-thumb">
            <i className="ti ti-video" aria-hidden />
          </div>
        ) : (
          <img
            src={storageUrl}
            alt={fileName}
            className="mac-img"
            loading="lazy"
          />
        )}
        {(selected || alreadyAdded) && (
          <div className="mac-check" aria-hidden>
            <i className={`ti ${alreadyAdded ? "ti-check" : "ti-check"}`} />
          </div>
        )}
        {scorePct != null && scoreLabel && (
          <span className="mac-score">
            {scoreLabel}
          </span>
        )}
        {visibleReasons.length > 0 && (
          <span className="mac-match-tooltip" id={reasonId}>
            <span className="mac-match-title">Why this match</span>
            {visibleReasons.map((reason) => (
              <span className="mac-match-reason" key={reason}>
                {reason}
              </span>
            ))}
          </span>
        )}
      </div>
      <p className="mac-name" title={fileName}>{shortName}</p>
      {aiCategory && <p className="mac-category">{aiCategory}</p>}
      {alreadyAdded && <p className="mac-added-label">In post</p>}
    </button>
  );
}

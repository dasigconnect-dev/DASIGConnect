import OptimizedImage, { canTransformImageType } from "./OptimizedImage";

interface MediaAssetCardProps {
  id: string;
  storageUrl: string;
  fileName: string;
  fileType: string;
  aiCategory?: string | null;
  assetType?: string | null;
  similarityScore?: number;
  matchReasons?: string[];
  institutionName?: string | null;
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

function reasonPriority(reason: string) {
  const normalized = reason.toLowerCase();
  if (normalized.includes("same format")) return 0;
  if (normalized.includes("gemini") || normalized.includes("media format")) return 1;
  if (normalized.includes("visual")) return 2;
  if (/context|category|tag|metadata|details|mention/.test(normalized)) return 3;
  if (/recent|variety|used/.test(normalized)) return 8;
  return 5;
}

function readableReasons(reasons: string[]) {
  return [...new Set(reasons.map((reason) => reason.trim()).filter(Boolean))]
    .sort((a, b) => reasonPriority(a) - reasonPriority(b))
    .slice(0, 2);
}

export default function MediaAssetCard({
  id,
  storageUrl,
  fileName,
  fileType,
  aiCategory,
  assetType,
  similarityScore,
  matchReasons = [],
  institutionName,
  selected,
  alreadyAdded,
  onToggle,
}: MediaAssetCardProps) {
  const isVideo = isVideoType(fileType);
  const shortName = fileName.length > 20 ? fileName.slice(0, 17) + "..." : fileName;
  const scorePct = similarityScore != null ? Math.round(similarityScore * 100) : null;
  const scoreLabel = similarityScore != null ? matchLabel(similarityScore) : null;
  const visibleReasons = readableReasons(matchReasons);
  const primaryReason = visibleReasons[0];
  const reasonId = `mac-match-${id}`;

  const isSelectedOrAdded = selected || alreadyAdded;

  return (
    <button
      type="button"
      className={[
        "mac-card",
        isSelectedOrAdded ? "mac-card--selected" : "",
        alreadyAdded ? "mac-card--added" : "",
      ]
        .filter(Boolean)
        .join(" ")}
      onClick={onToggle}
      aria-label={[
        fileName,
        assetType ? `Gemini format ${assetType}` : null,
        scoreLabel,
        primaryReason,
        alreadyAdded ? "attached to post (click to deselect)" : selected ? "selected (click to deselect)" : null,
      ].filter(Boolean).join(", ")}
      aria-pressed={isSelectedOrAdded}
      aria-describedby={visibleReasons.length > 0 ? reasonId : undefined}
      title={
        alreadyAdded
          ? "Attached to this post — click to deselect"
          : selected
            ? "Selected — click to deselect"
            : "Click to select"
      }
    >
      <div className="mac-thumb">
        {isVideo ? (
          <div className="mac-video-thumb">
            <i className="ti ti-video" aria-hidden />
          </div>
        ) : (
          <OptimizedImage
            src={storageUrl}
            alt={fileName}
            className="mac-img"
            width={176}
            height={132}
            sizes="176px"
            candidateWidths={[176, 352]}
            transform={canTransformImageType(fileType)}
          />
        )}
        <span
          className={`mac-check${selected || alreadyAdded ? " checked" : ""}`}
          aria-hidden="true"
        >
          {(selected || alreadyAdded) && (
            <svg
              width="14"
              height="14"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="3.5"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <polyline points="20,6 9,17 4,12" />
            </svg>
          )}
        </span>
        {scorePct != null && scoreLabel && (
          <span className="mac-score">
            {scoreLabel}
          </span>
        )}
        {assetType && assetType.toLowerCase() !== "other" && (
          <span className="mac-format">{assetType}</span>
        )}
        {visibleReasons.length > 0 && (
          <span className="mac-match-tooltip" id={reasonId}>
            <span className="mac-match-title">
              Why this matches
              {scorePct != null && <span className="mac-match-score"> · {scorePct}%</span>}
            </span>
            {visibleReasons.map((reason) => (
              <span className="mac-match-reason" key={reason}>
                {reason}
              </span>
            ))}
          </span>
        )}
      </div>
      <p className="mac-name">{shortName}</p>
      {primaryReason && (
        <p className="mac-reason-preview">
          <i className="ti ti-sparkles" aria-hidden />
          <span>{primaryReason}</span>
        </p>
      )}
      {aiCategory && <p className="mac-category">{aiCategory}</p>}
      {institutionName && <p className="mac-institution">{institutionName}</p>}
      {alreadyAdded && <p className="mac-added-label">In post</p>}
    </button>
  );
}

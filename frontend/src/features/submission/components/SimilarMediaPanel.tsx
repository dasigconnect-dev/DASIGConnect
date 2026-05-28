import { useState } from "react";
import type { SimilarMediaAsset } from "../../../api/aiApi";
import type { SimilarMediaState } from "../../../hooks/useSimilarMedia";
import { logAiInteraction } from "../../../api/aiApi";

interface Props {
  state: SimilarMediaState;
  assets: SimilarMediaAsset[];
  submissionId: string;
  onAdd: (asset: SimilarMediaAsset) => Promise<void>;
  onRefresh: () => void;
}

/**
 * Horizontal panel of library assets similar to the current submission's media (UC-3.3).
 * Reusable — renders nothing when state is "empty" or "idle" (no noise for users).
 * Each card has an "Add to Post" action; the parent handles attachAsset() logic.
 */
export default function SimilarMediaPanel({
  state,
  assets,
  submissionId,
  onAdd,
  onRefresh,
}: Props) {
  if (state === "idle" || state === "empty") return null;

  return (
    <div className="similar-media-panel">
      <div className="similar-media-header">
        <span className="similar-media-title">
          <i className="ti ti-sparkles" aria-hidden />
          Similar in Your Library
        </span>
        {state === "ready" && (
          <button
            type="button"
            className="ai-sugg-action-btn"
            onClick={(e) => { e.preventDefault(); onRefresh(); }}
            title="Refresh recommendations"
          >
            <i className="ti ti-refresh" aria-hidden />
          </button>
        )}
      </div>

      <div className="similar-media-list">
        {state === "loading" && <SkeletonCards />}
        {state === "error" && (
          <p className="similar-media-error">
            Could not load recommendations.{" "}
            <button
              type="button"
              className="link-btn"
              onClick={(e) => { e.preventDefault(); onRefresh(); }}
            >
              Retry
            </button>
          </p>
        )}
        {state === "ready" &&
          assets.map((asset) => (
            <SimilarAssetCard
              key={asset.id}
              asset={asset}
              submissionId={submissionId}
              onAdd={onAdd}
            />
          ))}
      </div>
    </div>
  );
}

function SimilarAssetCard({
  asset,
  submissionId,
  onAdd,
}: {
  asset: SimilarMediaAsset;
  submissionId: string;
  onAdd: (asset: SimilarMediaAsset) => Promise<void>;
}) {
  const [adding, setAdding] = useState(false);
  const [added, setAdded] = useState(false);

  async function handleAdd(e: React.MouseEvent) {
    e.preventDefault();
    if (adding || added) return;
    setAdding(true);
    try {
      await onAdd(asset);
      setAdded(true);
      logAiInteraction(submissionId, "media_recommendation", "accepted");
    } catch {
      // parent shows toast on error
    } finally {
      setAdding(false);
    }
  }

  const shortName =
    asset.fileName.length > 22
      ? asset.fileName.slice(0, 19) + "..."
      : asset.fileName;

  return (
    <div className="similar-media-card">
      <div className="similar-media-thumb">
        <img
          src={asset.storageUrl}
          alt={asset.fileName}
          loading="lazy"
          className="similar-media-img"
        />
      </div>
      <p className="similar-media-name" title={asset.fileName}>
        {shortName}
      </p>
      {asset.aiCategory && (
        <p className="similar-media-category">{asset.aiCategory}</p>
      )}
      <button
        type="button"
        className={`similar-media-add-btn${added ? " similar-media-add-btn--added" : ""}`}
        onClick={handleAdd}
        disabled={adding || added}
        title={added ? "Added" : `Add ${asset.fileName} to this post`}
      >
        {adding ? (
          <span className="ai-caption-spinner" aria-hidden />
        ) : added ? (
          <><i className="ti ti-check" aria-hidden /> Added</>
        ) : (
          <><i className="ti ti-plus" aria-hidden /> Add</>
        )}
      </button>
    </div>
  );
}

function SkeletonCards() {
  return (
    <>
      {Array.from({ length: 4 }).map((_, i) => (
        <div key={i} className="similar-media-card similar-media-card--skeleton">
          <div className="similar-media-thumb skeleton-block" />
          <div className="skeleton-line skeleton-line--short" />
          <div className="skeleton-line skeleton-line--shorter" />
        </div>
      ))}
    </>
  );
}

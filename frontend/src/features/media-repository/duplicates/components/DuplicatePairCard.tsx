import { useState } from "react";
import {
  decideDuplicate,
  type DuplicateAssetSummary,
  type DuplicateCandidatePair,
  type DuplicateDecision,
} from "../../../../api/mediaApi";
import { useToast } from "../../../../context/ToastContext";

interface DuplicatePairCardProps {
  pair: DuplicateCandidatePair;
  onResolved: (candidateId: string) => void;
}

function isImageType(fileType: string) {
  return ["jpeg", "jpg", "png", "webp", "gif"].includes(fileType.toLowerCase());
}

function AssetTile({
  asset,
  selected,
  onSelect,
  selectable,
}: {
  asset: DuplicateAssetSummary;
  selected: boolean;
  onSelect: () => void;
  selectable: boolean;
}) {
  return (
    <button
      type="button"
      className={`dup-tile ${selected ? "selected" : ""}`}
      onClick={selectable ? onSelect : undefined}
      aria-pressed={selected}
      disabled={!selectable}
    >
      <div className="dup-tile-thumb">
        {isImageType(asset.fileType) ? (
          <img src={asset.storageUrl} alt="" loading="lazy" />
        ) : (
          <span className="dup-tile-fallback">{asset.fileType.toUpperCase()}</span>
        )}
      </div>
      <span className="dup-tile-code">{asset.assetCode}</span>
      <span className="dup-tile-name">{asset.title || asset.fileName}</span>
      {selectable && selected && <span className="dup-tile-canonical">Canonical (keep)</span>}
    </button>
  );
}

/**
 * UC-4.12 Phase 7F: one duplicate candidate pair, side by side, with an explainable Hamming
 * distance and the three human decisions. A decision never deletes an asset.
 */
export default function DuplicatePairCard({ pair, onResolved }: DuplicatePairCardProps) {
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  const [mergeTags, setMergeTags] = useState(false);
  // Default the original (the compared/duplicate-of target) as canonical when present.
  const [canonicalId, setCanonicalId] = useState<string>(
    pair.compared?.id ?? pair.candidate.id,
  );

  async function decide(decision: DuplicateDecision) {
    setBusy(true);
    try {
      await decideDuplicate(pair.candidateAssetId, {
        decision,
        canonicalAssetId: decision === "duplicate" ? canonicalId : undefined,
        mergeTags: decision === "duplicate" ? mergeTags : undefined,
      });
      const label =
        decision === "duplicate" ? "Marked as duplicate." : decision === "keep_both" ? "Kept both." : "Marked as not a duplicate.";
      toast.success(label);
      onResolved(pair.candidateAssetId);
    } catch (err: unknown) {
      const message =
        (err as { response?: { data?: { error?: string; message?: string } } })?.response?.data
          ?.error ?? "Could not record the decision.";
      toast.error(message);
    } finally {
      setBusy(false);
    }
  }

  const canMarkDuplicate = pair.compared != null;

  return (
    <div className="dup-card">
      <div className="dup-card-head">
        {pair.hammingDistance != null ? (
          <span className="dup-hamming">Hamming distance {pair.hammingDistance}</span>
        ) : (
          <span className="dup-hamming muted">Similarity unavailable</span>
        )}
      </div>

      <div className="dup-pair">
        <AssetTile
          asset={pair.candidate}
          selected={canonicalId === pair.candidate.id}
          onSelect={() => setCanonicalId(pair.candidate.id)}
          selectable={canMarkDuplicate}
        />
        {pair.compared ? (
          <AssetTile
            asset={pair.compared}
            selected={canonicalId === pair.compared.id}
            onSelect={() => setCanonicalId(pair.compared!.id)}
            selectable
          />
        ) : (
          <div className="dup-tile dup-tile--missing">Compared asset unavailable</div>
        )}
      </div>

      {canMarkDuplicate && (
        <label className="dup-merge">
          <input type="checkbox" checked={mergeTags} onChange={(e) => setMergeTags(e.target.checked)} />
          Merge the duplicate's tags into the canonical asset
        </label>
      )}

      <div className="dup-actions">
        <button
          type="button"
          className="med-btn med-btn-primary med-btn-sm"
          disabled={busy || !canMarkDuplicate}
          onClick={() => void decide("duplicate")}
        >
          Mark duplicate
        </button>
        <button type="button" className="med-btn med-btn-ghost med-btn-sm" disabled={busy} onClick={() => void decide("keep_both")}>
          Keep both
        </button>
        <button type="button" className="med-btn med-btn-ghost med-btn-sm" disabled={busy} onClick={() => void decide("not_duplicate")}>
          Not a duplicate
        </button>
      </div>
    </div>
  );
}

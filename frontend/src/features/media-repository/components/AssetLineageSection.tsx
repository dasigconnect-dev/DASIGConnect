import { useEffect, useState } from "react";
import {
  createAssetRelation,
  getAssetRelations,
  listMediaAssets,
  type LineageEdge,
  type MediaAsset,
  type MediaAssetLineage,
  type RelationType,
} from "../../../api/mediaApi";
import { useToast } from "../../../context/ToastContext";

interface AssetLineageSectionProps {
  assetId: string;
  institutionId: string;
}

const RELATION_LABEL: Record<RelationType, string> = {
  new_version: "New version",
  replacement_for: "Replacement",
  derived_from: "Derived from",
  component_of: "Component of",
};

const RELATION_OPTIONS: RelationType[] = [
  "new_version",
  "replacement_for",
  "derived_from",
  "component_of",
];

function isImageType(fileType: string) {
  return ["jpeg", "jpg", "png", "webp", "gif"].includes(fileType.toLowerCase());
}

function EdgeRow({ edge }: { edge: LineageEdge }) {
  const { asset } = edge;
  return (
    <li className="med-lineage-item">
      <div className="med-lineage-thumb">
        {isImageType(asset.fileType) ? (
          <img src={asset.storageUrl} alt="" loading="lazy" />
        ) : (
          <span className="med-lineage-thumb-fallback">{asset.fileType.toUpperCase()}</span>
        )}
      </div>
      <div className="med-lineage-meta">
        <span className={`med-lineage-badge ${edge.relationType}`}>{RELATION_LABEL[edge.relationType]}</span>
        <span className="med-lineage-code">{asset.assetCode}</span>
        <span className="med-lineage-name">{asset.title || asset.fileName}</span>
      </div>
    </li>
  );
}

/**
 * UC-4.12 Phase 7E: self-contained lineage panel for the shared Asset Detail sidebar. Shows what
 * this asset derives from and its own versions/derivatives, and links an existing library asset as
 * a related asset. Originals are never overwritten — a relation is a link between separate assets.
 */
export default function AssetLineageSection({ assetId, institutionId }: AssetLineageSectionProps) {
  const toast = useToast();
  const [lineage, setLineage] = useState<MediaAssetLineage | null>(null);
  const [loading, setLoading] = useState(true);
  const [linking, setLinking] = useState(false);
  const [saving, setSaving] = useState(false);
  const [candidates, setCandidates] = useState<MediaAsset[]>([]);
  const [childId, setChildId] = useState("");
  const [relationType, setRelationType] = useState<RelationType>("new_version");

  useEffect(() => {
    let active = true;
    queueMicrotask(() => {
      if (!active) return;
      setLoading(true);
      setLinking(false);
      getAssetRelations(assetId)
        .then((l) => {
          if (active) setLineage(l);
        })
        .catch(() => {
          if (active) setLineage(null);
        })
        .finally(() => {
          if (active) setLoading(false);
        });
    });
    return () => {
      active = false;
    };
  }, [assetId]);

  function openLink() {
    setLinking(true);
    setChildId("");
    if (candidates.length === 0) {
      listMediaAssets({ institutionId })
        .then((res) => setCandidates(res.data.filter((a) => a.id !== assetId)))
        .catch(() => setCandidates([]));
    }
  }

  async function save() {
    if (!childId) return;
    setSaving(true);
    try {
      const updated = await createAssetRelation(assetId, { childAssetId: childId, relationType });
      setLineage(updated);
      setLinking(false);
      toast.success("Related asset linked.");
    } catch (err: unknown) {
      const message =
        (err as { response?: { data?: { error?: string; message?: string } } })?.response?.data
          ?.error ?? "Could not link the related asset.";
      toast.error(message);
    } finally {
      setSaving(false);
    }
  }

  const hasLineage = (lineage?.parents.length ?? 0) + (lineage?.children.length ?? 0) > 0;

  return (
    <div className="med-panel-section">
      <div className="med-panel-section-label">Versions &amp; Lineage</div>

      {loading ? (
        <p className="med-rights-muted">Loading lineage…</p>
      ) : (
        <>
          {lineage && lineage.parents.length > 0 && (
            <div className="med-lineage-group">
              <span className="med-lineage-group-label">Source / original</span>
              <ul className="med-lineage-list">
                {lineage.parents.map((e) => (
                  <EdgeRow key={e.relationId} edge={e} />
                ))}
              </ul>
            </div>
          )}
          {lineage && lineage.children.length > 0 && (
            <div className="med-lineage-group">
              <span className="med-lineage-group-label">Versions &amp; derivatives</span>
              <ul className="med-lineage-list">
                {lineage.children.map((e) => (
                  <EdgeRow key={e.relationId} edge={e} />
                ))}
              </ul>
            </div>
          )}
          {!hasLineage && (
            <p className="med-rights-muted">
              No linked versions or derivatives. The original stays intact — link a separately
              uploaded crop, replacement, or new version below.
            </p>
          )}

          {linking ? (
            <div className="med-lineage-form">
              <label className="med-rights-field">
                <span>Relationship</span>
                <select value={relationType} onChange={(e) => setRelationType(e.target.value as RelationType)}>
                  {RELATION_OPTIONS.map((t) => (
                    <option key={t} value={t}>
                      {RELATION_LABEL[t]}
                    </option>
                  ))}
                </select>
              </label>
              <label className="med-rights-field">
                <span>Related asset</span>
                <select value={childId} onChange={(e) => setChildId(e.target.value)}>
                  <option value="">Select an asset…</option>
                  {candidates.map((a) => (
                    <option key={a.id} value={a.id}>
                      {a.code} — {a.title || a.fileName}
                    </option>
                  ))}
                </select>
              </label>
              <div className="med-rights-actions">
                <button type="button" className="med-btn med-btn-ghost med-btn-sm" onClick={() => setLinking(false)} disabled={saving}>
                  Cancel
                </button>
                <button type="button" className="med-btn med-btn-primary med-btn-sm" onClick={() => void save()} disabled={saving || !childId}>
                  {saving ? "Linking…" : "Link asset"}
                </button>
              </div>
            </div>
          ) : (
            <button type="button" className="med-btn med-btn-ghost med-btn-sm" onClick={openLink}>
              Link related asset
            </button>
          )}
        </>
      )}
    </div>
  );
}

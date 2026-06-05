import type { MediaAuditEntry } from "../../../api/mediaApi";
import { formatUploadDate } from "../utils";

interface AssetActivityLogProps {
  entries: MediaAuditEntry[];
  loading: boolean;
}

/** Friendly labels for audit action codes (UC-4.11). Unknown codes are prettified. */
const ACTION_LABELS: Record<string, string> = {
  MEDIA_ASSET_UPLOADED: "Uploaded",
  MEDIA_ASSET_DELETED: "Deleted",
  MEDIA_ASSETS_BULK_DELETED: "Deleted (bulk)",
  MEDIA_BATCH_CURATED: "Curated",
  MEDIA_ASSET_TAG_ADDED: "Tag added",
  MEDIA_ASSET_TAG_REMOVED: "Tag removed",
  MEDIA_ASSET_PURGED: "Purged (retention)",
  ASSETS_MOVED: "Moved to folder",
  ASSETS_TAGGED: "Tagged",
};

function actionLabel(action: string): string {
  return (
    ACTION_LABELS[action] ??
    action
      .toLowerCase()
      .replace(/_/g, " ")
      .replace(/^\w/, (c) => c.toUpperCase())
  );
}

/** Pull a short human detail (e.g. a tag label) from the audit metadata JSON, if present. */
function metaDetail(metadata: string | null): string | null {
  if (!metadata) return null;
  try {
    const parsed = JSON.parse(metadata) as Record<string, unknown>;
    if (typeof parsed.label === "string") return `"${parsed.label}"`;
    if (typeof parsed.curatedCount === "string") return `${parsed.curatedCount} assets`;
    if (typeof parsed.count === "string") return `${parsed.count} assets`;
    return null;
  } catch {
    return null;
  }
}

/**
 * UC-4.11: renders an asset's provenance trail (who did what, when) in the detail panel.
 * Presentational — data is fetched by the screen and passed in.
 */
export default function AssetActivityLog({ entries, loading }: AssetActivityLogProps) {
  if (loading) {
    return <p className="med-activity-empty">Loading activity…</p>;
  }
  if (entries.length === 0) {
    return <p className="med-activity-empty">No recorded activity yet.</p>;
  }

  return (
    <ol className="med-activity-list">
      {entries.map((entry, idx) => {
        const detail = metaDetail(entry.metadata);
        const actor = entry.actorName ?? entry.actorEmail ?? "System";
        return (
          <li key={`${entry.createdAt}-${idx}`} className="med-activity-item">
            <span className="med-activity-dot" aria-hidden="true" />
            <div className="med-activity-body">
              <div className="med-activity-head">
                <span className="med-activity-action">{actionLabel(entry.action)}</span>
                {detail && <span className="med-activity-detail">{detail}</span>}
              </div>
              <div className="med-activity-meta">
                <span className="med-activity-actor">{actor}</span>
                <span aria-hidden="true"> · </span>
                <time dateTime={entry.createdAt}>{formatUploadDate(entry.createdAt)}</time>
              </div>
            </div>
          </li>
        );
      })}
    </ol>
  );
}

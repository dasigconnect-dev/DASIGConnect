import type { AlbumAssetSummary } from "../../../api/albumApi";
import { isImage } from "../mediaType";
import AssetMetaColumns from "../../media-repository/components/AssetMetaColumns";
import SearchResultMeta from "../../media-repository/components/SearchResultMeta";

interface AlbumAssetTileProps {
  asset: AlbumAssetSummary;
  isCover: boolean;
  checked?: boolean;
  listView?: boolean;
  /** UC-4.5 search-result match reasons (when the collection is being searched). */
  matchReasons?: string[];
  /** UC-4.6 thumbs feedback state/handler for search results. */
  feedback?: "up" | "down" | null;
  onFeedback?: (action: "thumbs_up" | "thumbs_down") => void;
  onSetCover: (assetId: string) => void;
  onRemove: (assetId: string) => void;
  onOpen?: (asset: AlbumAssetSummary) => void;
}

/** One media item within an opened collection. Clicking the tile toggles its selection
 *  (mirrors the Media Library card logic) and reveals the shared detail panel; hover actions
 *  (set cover / remove) stop propagation so they don't toggle selection. In list view the
 *  reusable AssetMetaColumns renders Date · Size · Type · Category beside the title. */
export default function AlbumAssetTile({
  asset,
  isCover,
  checked = false,
  listView = false,
  matchReasons,
  feedback = null,
  onFeedback,
  onSetCover,
  onRemove,
  onOpen,
}: AlbumAssetTileProps) {
  return (
    <figure
      className={`alb-asset${checked ? " selected" : ""}`}
      role={onOpen ? "button" : undefined}
      tabIndex={onOpen ? 0 : undefined}
      onClick={() => onOpen?.(asset)}
      onKeyDown={(e) => { if (onOpen && (e.key === "Enter" || e.key === " ")) { e.preventDefault(); onOpen(asset); } }}
      title={onOpen ? `Select ${asset.fileName}` : undefined}
      style={onOpen ? { cursor: "pointer" } : undefined}
    >
      <div className="alb-asset-thumb">
        {isImage(asset.fileType) ? (
          <img src={asset.storageUrl} alt={asset.fileName} loading="lazy" />
        ) : (
          <span className="alb-asset-file">{asset.fileType.toUpperCase()}</span>
        )}
        {isCover && <span className="alb-cover-badge">Cover</span>}
        {onOpen && (
          <span className={`alb-asset-check${checked ? " checked" : ""}`} aria-hidden="true">
            {checked && (
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
                <polyline points="20,6 9,17 4,12" />
              </svg>
            )}
          </span>
        )}
        <div className="alb-asset-actions">
          <button type="button" title="Set as cover" aria-label="Set as cover" onClick={(e) => { e.stopPropagation(); onSetCover(asset.id); }}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
            </svg>
          </button>
          <button type="button" title="Remove from collection" aria-label="Remove from collection" onClick={(e) => { e.stopPropagation(); onRemove(asset.id); }}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>
      </div>
      <figcaption className="alb-asset-name">{asset.fileName}</figcaption>
      {listView && (
        <AssetMetaColumns
          uploadedAt={asset.createdAt}
          fileSizeBytes={asset.fileSizeBytes}
          fileType={asset.fileType}
          category={asset.aiCategory}
        />
      )}
      <SearchResultMeta matchReasons={matchReasons} feedback={feedback} onFeedback={onFeedback} />
    </figure>
  );
}

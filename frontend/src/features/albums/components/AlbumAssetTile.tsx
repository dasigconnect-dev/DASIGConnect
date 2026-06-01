import type { AlbumAssetSummary } from "../../../api/albumApi";
import { isImage } from "../mediaType";

interface AlbumAssetTileProps {
  asset: AlbumAssetSummary;
  isCover: boolean;
  onSetCover: (assetId: string) => void;
  onRemove: (assetId: string) => void;
}

/** One media item within an opened collection, with hover actions (presentational). */
export default function AlbumAssetTile({ asset, isCover, onSetCover, onRemove }: AlbumAssetTileProps) {
  return (
    <figure className="alb-asset">
      <div className="alb-asset-thumb">
        {isImage(asset.fileType) ? (
          <img src={asset.storageUrl} alt={asset.fileName} loading="lazy" />
        ) : (
          <span className="alb-asset-file">{asset.fileType.toUpperCase()}</span>
        )}
        {isCover && <span className="alb-cover-badge">Cover</span>}
        <div className="alb-asset-actions">
          <button type="button" title="Set as cover" aria-label="Set as cover" onClick={() => onSetCover(asset.id)}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
            </svg>
          </button>
          <button type="button" title="Remove from collection" aria-label="Remove from collection" onClick={() => onRemove(asset.id)}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>
      </div>
      <figcaption className="alb-asset-name">{asset.fileName}</figcaption>
    </figure>
  );
}

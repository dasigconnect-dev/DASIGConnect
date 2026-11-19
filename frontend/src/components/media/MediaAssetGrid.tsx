import MediaAssetCard from "./MediaAssetCard";

export interface GridAsset {
  id: string;
  storageUrl: string;
  fileName: string;
  fileType: string;
  aiCategory?: string | null;
  assetType?: string | null;
  similarityScore?: number;
  matchReasons?: string[];
  /** Shown as a badge when browsing across more than one institution. */
  institutionName?: string | null;
}

interface MediaAssetGridProps {
  assets: GridAsset[];
  selectedIds: Set<string>;
  alreadyAddedIds: Set<string>;
  onToggle: (id: string) => void;
  loading?: boolean;
  skeletonCount?: number;
  columns?: number;
}

export default function MediaAssetGrid({
  assets,
  selectedIds,
  alreadyAddedIds,
  onToggle,
  loading = false,
  skeletonCount = 8,
  columns,
}: MediaAssetGridProps) {
  const gridStyle = columns ? { gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))` } : undefined;
  if (loading && assets.length === 0) {
    return (
      <div className="mag-scroll-wrap">
        <div className="mag-grid" style={gridStyle}>
          {Array.from({ length: skeletonCount }).map((_, i) => (
            <div key={i} className="mac-card mac-card--skeleton">
              <div className="mac-thumb skeleton-block" />
              <div className="skeleton-line skeleton-line--short" />
            </div>
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className="mag-scroll-wrap">
      <div className="mag-grid" style={gridStyle}>
        {assets.map((asset) => (
          <MediaAssetCard
            key={asset.id}
            id={asset.id}
            storageUrl={asset.storageUrl}
            fileName={asset.fileName}
            fileType={asset.fileType}
            aiCategory={asset.aiCategory}
            assetType={asset.assetType}
            similarityScore={asset.similarityScore}
            matchReasons={asset.matchReasons}
            institutionName={asset.institutionName}
            selected={selectedIds.has(asset.id)}
            alreadyAdded={alreadyAddedIds.has(asset.id)}
            onToggle={() => onToggle(asset.id)}
          />
        ))}
      </div>
    </div>
  );
}

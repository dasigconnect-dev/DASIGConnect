import MediaAssetCard from "./MediaAssetCard";

export interface GridAsset {
  id: string;
  storageUrl: string;
  fileName: string;
  fileType: string;
  aiCategory?: string | null;
  similarityScore?: number;
  matchReasons?: string[];
}

interface MediaAssetGridProps {
  assets: GridAsset[];
  selectedIds: Set<string>;
  alreadyAddedIds: Set<string>;
  onToggle: (id: string) => void;
  loading?: boolean;
  skeletonCount?: number;
}

export default function MediaAssetGrid({
  assets,
  selectedIds,
  alreadyAddedIds,
  onToggle,
  loading = false,
  skeletonCount = 8,
}: MediaAssetGridProps) {
  if (loading && assets.length === 0) {
    return (
      <div className="mag-scroll-wrap">
        <div className="mag-grid">
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
      <div className="mag-grid">
        {assets.map((asset) => (
          <MediaAssetCard
            key={asset.id}
            id={asset.id}
            storageUrl={asset.storageUrl}
            fileName={asset.fileName}
            fileType={asset.fileType}
            aiCategory={asset.aiCategory}
            similarityScore={asset.similarityScore}
            matchReasons={asset.matchReasons}
            selected={selectedIds.has(asset.id)}
            alreadyAdded={alreadyAddedIds.has(asset.id)}
            onToggle={() => onToggle(asset.id)}
          />
        ))}
      </div>
    </div>
  );
}

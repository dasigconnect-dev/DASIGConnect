import { useRef } from "react";
import type { SubmissionMediaItem } from "../../types/media";
import { useMediaLibraryAssets } from "../../hooks/useMediaLibraryAssets";
import MediaAssetGrid, { type GridAsset } from "./MediaAssetGrid";
import BrandedSelect from "../ui/BrandedSelect";

interface MediaLibraryTabProps {
  alreadyAddedIds: Set<string>;
  onAddItems: (items: SubmissionMediaItem[]) => void;
  disabled?: boolean;
}

const CATEGORY_OPTIONS = [
  { value: "", label: "All categories" },
  { value: "event", label: "Event" },
  { value: "award", label: "Award" },
  { value: "facility", label: "Facility" },
  { value: "people", label: "People" },
  { value: "research", label: "Research" },
  { value: "training", label: "Training" },
];

const MEDIA_TYPE_OPTIONS = [
  { value: "", label: "All types" },
  { value: "image", label: "Images" },
  { value: "video", label: "Videos" },
];

export default function MediaLibraryTab({
  alreadyAddedIds,
  onAddItems,
  disabled,
}: MediaLibraryTabProps) {
  const {
    assets,
    loading,
    error,
    totalCount,
    hasMore,
    search,
    setSearch,
    aiCategory,
    setAiCategory,
    mediaType,
    setMediaType,
    loadMore,
    retry,
    selectedIds,
    toggleSelect,
    clearSelection,
  } = useMediaLibraryAssets();

  const searchRef = useRef<HTMLInputElement>(null);

  const pendingSelected = [...selectedIds].filter((id) => !alreadyAddedIds.has(id));

  function handleAdd() {
    if (disabled || pendingSelected.length === 0) return;
    const toAdd = assets.filter((a) => pendingSelected.includes(a.id));
    const items: SubmissionMediaItem[] = toAdd.map((a) => ({
      clientId: `library-${a.id}`,
      source: "library" as const,
      assetId: a.id,
      previewUrl: a.storageUrl,
      mediaType: ["mp4", "mov", "webm"].includes(a.fileType.toLowerCase()) ? "video" : "image",
      fileName: a.fileName,
      aiCategory: a.aiTags?.[0]?.label ?? undefined,
    }));
    onAddItems(items);
    clearSelection();
  }

  return (
    <div className="mlt-root">
      <div className="mlt-filters">
        <div className="mlt-search-wrap">
          <i className="ti ti-search mlt-search-icon" aria-hidden />
          <input
            ref={searchRef}
            type="search"
            className="mlt-search"
            placeholder="Search by filename or category…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            aria-label="Search media library"
            disabled={disabled}
          />
          {search && (
            <button
              type="button"
              className="mlt-search-clear"
              onClick={() => { setSearch(""); searchRef.current?.focus(); }}
              aria-label="Clear search"
            >
              <i className="ti ti-x" aria-hidden />
            </button>
          )}
        </div>

        <BrandedSelect
          className="mlt-select"
          value={aiCategory}
          options={CATEGORY_OPTIONS}
          onChange={setAiCategory}
          ariaLabel="Filter by category"
          disabled={disabled}
        />

        <BrandedSelect
          className="mlt-select"
          value={mediaType}
          options={MEDIA_TYPE_OPTIONS}
          onChange={(value) => setMediaType(value as "" | "image" | "video")}
          ariaLabel="Filter by media type"
          disabled={disabled}
        />
      </div>

      {totalCount > 0 && (
        <p className="mlt-count" aria-live="polite">
          {totalCount} asset{totalCount !== 1 ? "s" : ""} found
        </p>
      )}

      {error ? (
        <div className="mlt-error" role="alert">
          <i className="ti ti-alert-circle" aria-hidden />
          <span>Failed to load media library.</span>
          <button type="button" className="mlt-retry-btn" onClick={retry}>Retry</button>
        </div>
      ) : (
        <>
          <MediaAssetGrid
            assets={assets.map((a): GridAsset => ({
              id: a.id,
              storageUrl: a.storageUrl,
              fileName: a.fileName,
              fileType: a.fileType,
              aiCategory: a.aiTags?.[0]?.label ?? null,
            }))}
            selectedIds={new Set(selectedIds)}
            alreadyAddedIds={alreadyAddedIds}
            onToggle={toggleSelect}
            loading={loading}
            skeletonCount={8}
          />

          {!loading && assets.length === 0 && !error && (
            <div className="mlt-empty" aria-live="polite">
              <i className="ti ti-photo-off" aria-hidden />
              <span>No assets match your filters.</span>
            </div>
          )}

          {hasMore && !loading && (
            <button
              type="button"
              className="mlt-load-more"
              onClick={loadMore}
              disabled={disabled}
            >
              Load more
            </button>
          )}
        </>
      )}

      {pendingSelected.length > 0 && (
        <div className="mlt-action-bar" role="status" aria-live="polite">
          <span className="mlt-action-count">
            {pendingSelected.length} selected
          </span>
          <button
            type="button"
            className="mlt-action-clear"
            onClick={clearSelection}
          >
            Clear
          </button>
          <button
            type="button"
            className="mlt-action-add"
            onClick={handleAdd}
            disabled={disabled}
          >
            <i className="ti ti-plus" aria-hidden />
            Add Selected ({pendingSelected.length})
          </button>
        </div>
      )}
    </div>
  );
}

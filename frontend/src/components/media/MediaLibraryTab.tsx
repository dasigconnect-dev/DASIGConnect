import { useEffect, useMemo, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import type { SubmissionMediaItem } from "../../types/media";
import { useMediaLibraryAssets } from "../../hooks/useMediaLibraryAssets";
import { listMediaAlbums, type MediaAlbum } from "../../api/mediaApi";
import { listInstitutions } from "../../api/authApi";
import { buildAlbumOptions } from "../../features/media-repository/albumTree";
import { authenticatedQueryMeta } from "../../lib/queryClient";
import { queryKeys } from "../../lib/queryKeys";
import MediaAssetGrid, { type GridAsset } from "./MediaAssetGrid";
import BrandedSelect from "../ui/BrandedSelect";
import "../../styles/media-picker.css";

interface InstitutionOption {
  id: string;
  name: string;
}

interface MediaLibraryTabProps {
  alreadyAddedIds: Set<string>;
  onAddItems: (items: SubmissionMediaItem[]) => void;
  onRemoveItem?: (assetId: string) => void;
  disabled?: boolean;
  /**
   * The institution this picker is opened "for" (e.g. the submission's own
   * institution) — used as the institution filter's starting value when
   * `networkView` is on, and as the sole scope when it's off.
   */
  institutionId?: string;
  /** Show assets across every institution for network-wide roles. */
  networkView?: boolean;
  /** Show a folder/album filter dropdown above the grid. */
  showAlbumFilter?: boolean;
  /**
   * Institutions to populate the institution filter with, when `networkView`
   * is on. If omitted, fetched internally — pass this when the caller already
   * has the list (e.g. the composer) to avoid a duplicate request.
   */
  institutions?: InstitutionOption[];
}

const MEDIA_TYPE_OPTIONS = [
  { value: "", label: "All types" },
  { value: "image", label: "Images" },
  { value: "video", label: "Videos" },
];
const MEDIA_LIBRARY_METADATA_STALE_TIME_MS = 5 * 60_000;
const MEDIA_LIBRARY_METADATA_GC_TIME_MS = 15 * 60_000;

export default function MediaLibraryTab({
  alreadyAddedIds,
  onAddItems,
  onRemoveItem,
  disabled,
  institutionId,
  networkView,
  showAlbumFilter,
  institutions: institutionsProp,
}: MediaLibraryTabProps) {
  const queryClient = useQueryClient();
  // Defaults to the institution this picker was opened for (least surprising),
  // not "All institutions" — see the composer/Review Queue conversation this
  // was built for. "" means "All institutions".
  const [institutionFilter, setInstitutionFilter] = useState(institutionId ?? "");

  const [fetchedInstitutions, setFetchedInstitutions] = useState<InstitutionOption[]>([]);
  useEffect(() => {
    if (!networkView || institutionsProp) return;
    const controller = new AbortController();
    queryClient.fetchQuery({
      queryKey: queryKeys.institutions.composerOptions({ role: "network" }),
      queryFn: () => listInstitutions(controller.signal).then((res) => res.data ?? []),
      staleTime: MEDIA_LIBRARY_METADATA_STALE_TIME_MS,
      gcTime: MEDIA_LIBRARY_METADATA_GC_TIME_MS,
      meta: authenticatedQueryMeta,
    })
      .then(setFetchedInstitutions)
      .catch(() => setFetchedInstitutions([]));
    return () => controller.abort();
  }, [networkView, institutionsProp, queryClient]);
  const institutionOptions = institutionsProp ?? fetchedInstitutions;

  // Narrowing to one institution disables networkView so the hook sends that
  // institutionId through; "" (All institutions) keeps true network-wide —
  // useMediaLibraryAssets strips institutionId whenever networkView is on.
  const effectiveNetworkView = networkView && institutionFilter === "";
  const effectiveInstitutionId = networkView ? institutionFilter || undefined : institutionId;
  const libraryRef = useRef<HTMLDivElement>(null);
  const [columns, setColumns] = useState(2);
  useEffect(() => {
    const element = libraryRef.current;
    if (!element) return;
    const observer = new ResizeObserver(([entry]) => {
      // Match the grid's 148px minimum card width and 8px gap.
      setColumns(Math.max(1, Math.floor((entry.contentRect.width + 8) / 156)));
    });
    observer.observe(element);
    return () => observer.disconnect();
  }, []);
  const pageSize = columns * 2;

  const {
    assets,
    selectedAssets,
    page,
    previousPage,
    loading,
    error,
    totalCount,
    hasMore,
    search,
    setSearch,
    mediaType,
    setMediaType,
    albumId,
    setAlbumId,
    loadMore,
    retry,
    selectedIds,
    toggleSelect,
    clearSelection,
  } = useMediaLibraryAssets({ institutionId: effectiveInstitutionId, networkView: effectiveNetworkView, pageSize });

  const searchRef = useRef<HTMLInputElement>(null);

  const [albums, setAlbums] = useState<MediaAlbum[]>([]);
  useEffect(() => {
    if (!showAlbumFilter) return;
    const controller = new AbortController();
    queryClient.fetchQuery({
      queryKey: queryKeys.mediaAlbums.all({
        role: networkView ? "network" : "institution",
        institutionId: effectiveInstitutionId ?? null,
        scope: networkView ? "network" : "institution",
      }),
      queryFn: () => listMediaAlbums(effectiveInstitutionId, controller.signal)
        .then((res) => res.data ?? []),
      staleTime: MEDIA_LIBRARY_METADATA_STALE_TIME_MS,
      gcTime: MEDIA_LIBRARY_METADATA_GC_TIME_MS,
      meta: authenticatedQueryMeta,
    })
      .then(setAlbums)
      .catch(() => setAlbums([]));
    return () => controller.abort();
  }, [showAlbumFilter, effectiveInstitutionId, networkView, queryClient]);

  const albumOptions = useMemo(
    () => [
      { value: "", label: "All folders" },
      ...buildAlbumOptions(albums).map((o) => ({ value: o.id, label: o.label })),
    ],
    [albums],
  );

  const institutionFilterOptions = useMemo(
    () => [
      { value: "", label: "All institutions" },
      ...[...institutionOptions]
        .sort((a, b) => a.name.localeCompare(b.name))
        .map((inst) => ({ value: inst.id, label: inst.name })),
    ],
    [institutionOptions],
  );

  const pendingSelected = [...selectedIds].filter((id) => !alreadyAddedIds.has(id));

  function handleToggleCard(id: string) {
    if (disabled) return;
    if (alreadyAddedIds.has(id)) {
      onRemoveItem?.(id);
      return;
    }
    toggleSelect(id);
  }

  function handleAdd() {
    if (disabled || pendingSelected.length === 0) return;
    const toAdd = selectedAssets.filter((a) => pendingSelected.includes(a.id));
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
    <div className="mlt-root" ref={libraryRef}>
      <div className="mlt-filters">
        <div className="mlt-search-wrap">
          <i className="ti ti-search mlt-search-icon" aria-hidden />
          <input
            ref={searchRef}
            type="search"
            className="mlt-search"
            placeholder="Search by meaning, filename, or tags…"
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
          value={mediaType}
          options={MEDIA_TYPE_OPTIONS}
          onChange={(value) => setMediaType(value as "" | "image" | "video")}
          ariaLabel="Filter by media type"
          disabled={disabled}
        />

        {networkView && (
          <BrandedSelect
            className="mlt-select"
            value={institutionFilter}
            options={institutionFilterOptions}
            onChange={setInstitutionFilter}
            ariaLabel="Filter by institution"
            disabled={disabled || institutionFilterOptions.length <= 1}
          />
        )}

        {showAlbumFilter && (
          <BrandedSelect
            className="mlt-select"
            value={albumId}
            options={albumOptions}
            onChange={setAlbumId}
            ariaLabel="Filter by folder"
            disabled={disabled || albumOptions.length <= 1}
          />
        )}
      </div>

      {/* Selection toolbar sits above the grid (it used to be a bar under
          "Load more", easy to miss). Always rendered once there are results,
          so the grid doesn't jump when the first asset is selected. */}
      {(totalCount > 0 || pendingSelected.length > 0) && (
        <div
          className={`mlt-selection-bar${pendingSelected.length > 0 ? " has-selection" : ""}`}
          role="status"
          aria-live="polite"
        >
          <span className="mlt-action-count">
            {pendingSelected.length > 0
              ? `${pendingSelected.length} selected`
              : `${totalCount} asset${totalCount !== 1 ? "s" : ""} found`}
          </span>
          {pendingSelected.length > 0 && (
            <>
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
            </>
          )}
        </div>
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
            columns={columns}
            assets={assets.slice(0, pageSize).map((a): GridAsset => ({
              id: a.id,
              storageUrl: a.storageUrl,
              fileName: a.fileName,
              fileType: a.fileType,
              aiCategory: a.aiTags?.[0]?.label ?? null,
              // Only worth a badge when results can actually mix institutions.
              institutionName: effectiveNetworkView ? a.institutionName ?? null : null,
            }))}
            selectedIds={new Set(selectedIds)}
            alreadyAddedIds={alreadyAddedIds}
            onToggle={handleToggleCard}
            loading={loading}
            skeletonCount={pageSize}
          />

          {!loading && assets.length === 0 && !error && (
            <div className="mlt-empty" aria-live="polite">
              <i className="ti ti-photo-off" aria-hidden />
              <span>No assets match your filters.</span>
            </div>
          )}

          {totalCount > 0 && (
            <nav className="ast-pagination" aria-label="My Library pages">
              <button
                type="button"
                className="ast-pagination-btn"
                onClick={previousPage}
                disabled={disabled || loading || page <= 1}
                aria-label="Previous library page"
              >
                <i className="ti ti-chevron-left" aria-hidden />
              </button>
              <span aria-live="polite" aria-label={`Page ${page}`} aria-current="page">
                {page}
              </span>
              <button
                type="button"
                className="ast-pagination-btn"
                onClick={loadMore}
                disabled={disabled || loading || !hasMore}
                aria-label="Next library page"
              >
                <i className="ti ti-chevron-right" aria-hidden />
              </button>
            </nav>
          )}
        </>
      )}
    </div>
  );
}

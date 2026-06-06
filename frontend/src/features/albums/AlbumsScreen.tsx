import { useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import type { User } from "../../types/auth.types";
import {
  listAlbums,
  getAlbum,
  createAlbum,
  updateAlbum,
  deleteAlbum,
  addAlbumAssets,
  removeAlbumAsset,
  setAlbumCover,
  type Album,
  type AlbumAssetSummary,
  type AlbumDetail,
} from "../../api/albumApi";
import { listInstitutions, type InstitutionResponse } from "../../api/authApi";
import {
  searchMediaAssets,
  getMediaAsset,
  getAssetHistory,
  getMediaAssetIntegrityHistory,
  changeAssetVisibility,
  recordSearchFeedback,
  verifyMediaAssetIntegrity,
  acknowledgeMediaAssetIntegrityReview,
  type MediaAsset,
  type MediaAuditEntry,
  type MediaIntegrityCheck,
  type MediaIntegrityResult,
  type MediaVisibility,
} from "../../api/mediaApi";
import { useToast } from "../../context/ToastContext";
import { useMediaSearch } from "../media-repository/hooks/useMediaSearch";
import AlbumCard from "./components/AlbumCard";
import AlbumAssetTile from "./components/AlbumAssetTile";
import CreateAlbumModal from "./components/CreateAlbumModal";
import AssetPickerModal from "./components/AssetPickerModal";
import PromptCollectionBuilderContainer from "./components/PromptCollectionBuilderContainer";
import AssetDetailPanel from "../media-repository/components/AssetDetailPanel";
import FilterBar from "../media-repository/components/FilterBar";
import LibraryScopeSelector from "../media-repository/components/LibraryScopeSelector";
import ConfirmDialog from "../user-management/components/ConfirmDialog";
import type { SortOption, ViewMode } from "../media-repository/types";

interface ConfirmState {
  title: string;
  message: string;
  confirmLabel: string;
  dangerous?: boolean;
  onConfirm: () => void;
}
import "../../styles/media-repository.css";
import "../../styles/albums.css";

interface AlbumsScreenProps {
  user: User;
}

export default function AlbumsScreen({ user }: AlbumsScreenProps) {
  const toast = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const routeState = location.state as { openAiBuilder?: boolean; institutionId?: string | null } | null;
  const isAdmin = user.role === "admin";

  const [albums, setAlbums] = useState<Album[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [institutions, setInstitutions] = useState<InstitutionResponse[]>([]);
  const [institutionsLoading, setInstitutionsLoading] = useState(isAdmin);
  const [selectedInstitutionId, setSelectedInstitutionId] = useState<string | null>(
    isAdmin ? routeState?.institutionId ?? null : null,
  );

  const [openAlbum, setOpenAlbum] = useState<AlbumDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  // Within-collection FilterBar state (reuses the Media Library filter component).
  const [collectionSearch, setCollectionSearch] = useState("");
  const [collectionSort, setCollectionSort] = useState<SortOption>("newest");
  const [collectionView, setCollectionView] = useState<ViewMode>("grid");
  const [collectionTags, setCollectionTags] = useState<Set<string>>(new Set());
  // Reuses the Media Library NL/hybrid search lifecycle (UC-4.5), scoped to this collection's assets.
  const colSearch = useMediaSearch();
  const searchActive = colSearch.active;
  const [searchFeedback, setSearchFeedback] = useState<Map<string, "up" | "down">>(new Map());

  // Real-data corpus for the related-search dropdown, drawn from the open collection's assets.
  const collectionCorpus = useMemo(() => {
    const assetsList = openAlbum?.assets ?? [];
    const tags = new Set<string>();
    const filenames = new Set<string>();
    const uploaders = new Set<string>();
    for (const a of assetsList) {
      if (a.aiCategory) tags.add(a.aiCategory);
      if (a.fileName) filenames.add(a.fileName);
      if (a.uploaderEmail) uploaders.add(a.uploaderEmail);
    }
    return {
      tags: [...tags],
      filenames: [...filenames],
      uploaders: [...uploaders],
      collections: openAlbum ? [openAlbum.name] : [],
    };
  }, [openAlbum]);

  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);

  // Edit (rename / description) — the "U" in collection CRUD.
  const [editOpen, setEditOpen] = useState(false);
  const [editing, setEditing] = useState(false);

  // Shared confirmation dialog for destructive actions (delete collection / remove item).
  const [confirm, setConfirm] = useState<ConfirmState | null>(null);

  // Reused asset detail panel (photo click inside a collection).
  const [selectedAsset, setSelectedAsset] = useState<MediaAsset | null>(null);
  const [panelOpen, setPanelOpen] = useState(false);
  // Multi-select inside a collection (mirrors the Media Library card selection logic).
  const [checkedIds, setCheckedIds] = useState<Set<string>>(new Set());
  const [assetHistory, setAssetHistory] = useState<MediaAuditEntry[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [visibilityBusy, setVisibilityBusy] = useState(false);
  const [integrityBusy, setIntegrityBusy] = useState(false);
  const [integrityHistory, setIntegrityHistory] = useState<MediaIntegrityCheck[]>([]);
  const [integrityHistoryLoading, setIntegrityHistoryLoading] = useState(false);
  const [integrityReviewBusy, setIntegrityReviewBusy] = useState(false);

  const [pickerOpen, setPickerOpen] = useState(false);
  const [pickerAssets, setPickerAssets] = useState<MediaAsset[]>([]);
  const [pickerLoading, setPickerLoading] = useState(false);
  const [pickerSelected, setPickerSelected] = useState<Set<string>>(new Set());
  const [adding, setAdding] = useState(false);

  const [builderOpen, setBuilderOpen] = useState(false);

  function fetchAlbums(signal?: AbortSignal) {
    setLoading(true);
    setError(null);
    if (isAdmin && !selectedInstitutionId) {
      setAlbums([]);
      setLoading(false);
      return;
    }
    listAlbums(isAdmin ? selectedInstitutionId : undefined, signal)
      .then(setAlbums)
      .catch(() => setError("Could not load collections."))
      .finally(() => setLoading(false));
  }

  // Event-handler reload (sets loading/error then fetches) — kept out of the effect body.
  function reload() {
    fetchAlbums();
  }

  useEffect(() => {
    if (!isAdmin) return;
    listInstitutions()
      .then((res) => {
        setInstitutions(res.data);
        setSelectedInstitutionId((current) => current ?? res.data[0]?.id ?? null);
      })
      .catch(() => toast.error("Could not load institution filters."))
      .finally(() => setInstitutionsLoading(false));
  }, [isAdmin, toast]);

  useEffect(() => {
    if (!routeState?.openAiBuilder) return;
    if (isAdmin && !selectedInstitutionId) return;
    // Route-driven modal open (navigated from Media Library "AI Builder").
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setBuilderOpen(true);
    navigate(location.pathname, { replace: true, state: null });
  }, [isAdmin, location.pathname, navigate, routeState, selectedInstitutionId]);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;

    queueMicrotask(() => {
      if (active) fetchAlbums(controller.signal);
    });

    return () => {
      active = false;
      controller.abort();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAdmin, selectedInstitutionId]);

  function exitCollection() {
    setOpenAlbum(null);
    setCollectionSearch("");
    setCollectionTags(new Set());
    setCollectionSort("newest");
    setCollectionView("grid");
    setCheckedIds(new Set());
    setSearchFeedback(new Map());
    colSearch.clear();
    closePanel();
  }

  // ----- Within-collection smart search (reuses the Media Library search logic) -----
  function runCollectionSearch(queryOverride?: string) {
    const q = (queryOverride ?? collectionSearch).trim();
    if (!q) {
      colSearch.clear();
      return;
    }
    if (isAdmin && !selectedInstitutionId) {
      toast.error("Select an institution before searching.");
      return;
    }
    setSearchFeedback(new Map());
    void colSearch.run({ query: q, institutionId: isAdmin ? selectedInstitutionId : undefined });
  }

  function clearCollectionSearch() {
    setCollectionSearch("");
    setSearchFeedback(new Map());
    colSearch.clear();
  }

  function handleSearchFeedback(asset: AlbumAssetSummary, rank: number, action: "thumbs_up" | "thumbs_down") {
    const dir = action === "thumbs_up" ? "up" : "down";
    setSearchFeedback((prev) => {
      const next = new Map(prev);
      next.set(asset.id, dir);
      return next;
    });
    void recordSearchFeedback({ assetId: asset.id, action, query: colSearch.activeQuery, rank });
    toast.success("Thanks — your feedback improves future search.");
  }

  function logSearchSelection(asset: AlbumAssetSummary, rank: number) {
    if (!searchActive || checkedIds.has(asset.id)) return;
    void recordSearchFeedback({ assetId: asset.id, action: "selected", query: colSearch.activeQuery, rank });
  }

  function toggleCollectionTag(tag: string) {
    setCollectionTags((prev) => {
      const next = new Set(prev);
      if (next.has(tag)) next.delete(tag);
      else next.add(tag);
      return next;
    });
  }

  async function openDetail(id: string) {
    setDetailLoading(true);
    try {
      setOpenAlbum(await getAlbum(id));
    } catch {
      toast.error("Could not open that collection.");
    } finally {
      setDetailLoading(false);
    }
  }

  async function refreshDetail() {
    if (!openAlbum) return;
    try {
      setOpenAlbum(await getAlbum(openAlbum.id));
    } catch {
      /* keep current view on refresh error */
    }
  }

  async function handleCreate(name: string, description: string) {
    if (isAdmin && !selectedInstitutionId) {
      toast.error("Select an institution before creating a collection.");
      return;
    }
    if (!name.trim()) return;
    setCreating(true);
    try {
      const album = await createAlbum({
        name: name.trim(),
        description: description.trim() || null,
        institutionId: isAdmin ? selectedInstitutionId : undefined,
      });
      setCreateOpen(false);
      setAlbums((prev) => [album, ...prev]);
      toast.success(`Collection "${album.name}" created.`);
    } catch {
      toast.error("Could not create the collection.");
    } finally {
      setCreating(false);
    }
  }

  function openBuilder() {
    if (isAdmin && !selectedInstitutionId) {
      toast.error("Select an institution before using AI Builder.");
      return;
    }
    setBuilderOpen(true);
  }

  function handleDeleteAlbum(album: Album) {
    setConfirm({
      title: "Delete collection",
      message: `Delete collection "${album.name}"? The media files themselves are not deleted.`,
      confirmLabel: "Delete",
      dangerous: true,
      onConfirm: () => void performDeleteAlbum(album),
    });
  }

  async function performDeleteAlbum(album: Album) {
    try {
      await deleteAlbum(album.id);
      setAlbums((prev) => prev.filter((a) => a.id !== album.id));
      if (openAlbum?.id === album.id) exitCollection();
      toast.success("Collection deleted.");
    } catch {
      toast.error("Could not delete the collection.");
    }
  }

  function openPicker() {
    if (isAdmin && !selectedInstitutionId) {
      toast.error("Select an institution before adding media.");
      return;
    }
    setPickerOpen(true);
    setPickerSelected(new Set());
    setPickerLoading(true);
    searchMediaAssets({
      pageSize: 100,
      institutionId: isAdmin ? selectedInstitutionId : undefined,
    })
      .then((page) => setPickerAssets(page.items))
      .catch(() => toast.error("Could not load media."))
      .finally(() => setPickerLoading(false));
  }

  function togglePick(id: string) {
    setPickerSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function selectAllPicker() {
    setPickerSelected(new Set(pickerAssets.map((a) => a.id)));
  }

  function clearPicker() {
    setPickerSelected(new Set());
  }

  async function handleAddSelected() {
    if (!openAlbum || pickerSelected.size === 0) return;
    setAdding(true);
    try {
      const detail = await addAlbumAssets(openAlbum.id, [...pickerSelected]);
      setOpenAlbum(detail);
      setAlbums((prev) => prev.map((a) => (a.id === detail.id ? { ...a, assetCount: detail.assetCount } : a)));
      setPickerOpen(false);
      toast.success(`Added ${pickerSelected.size} ${pickerSelected.size === 1 ? "item" : "items"}.`);
    } catch {
      toast.error("Could not add media to the collection.");
    } finally {
      setAdding(false);
    }
  }

  // Single-tile remove (hover ×) delegates to the shared multi-aware remover.
  function requestRemoveAsset(assetId: string) {
    requestRemoveFromCollection([assetId]);
  }

  // Confirm + remove one or many assets from the open collection (media files stay in the library).
  function requestRemoveFromCollection(ids: string[]) {
    if (ids.length === 0) return;
    const multi = ids.length > 1;
    setConfirm({
      title: multi ? `Remove ${ids.length} items` : "Remove from collection",
      message: multi
        ? `Remove these ${ids.length} items from the collection? The media files stay in the library.`
        : "Remove this item from the collection? The media file stays in the library.",
      confirmLabel: "Remove",
      dangerous: true,
      onConfirm: () => void handleRemoveFromCollection(ids),
    });
  }

  async function handleRemoveFromCollection(ids: string[]) {
    if (!openAlbum) return;
    const albumId = openAlbum.id;
    try {
      for (const id of ids) {
        await removeAlbumAsset(albumId, id);
      }
      await refreshDetail();
      setAlbums((prev) =>
        prev.map((a) => (a.id === albumId ? { ...a, assetCount: Math.max(0, a.assetCount - ids.length) } : a)),
      );
      setCheckedIds((prev) => {
        const next = new Set(prev);
        ids.forEach((id) => next.delete(id));
        return next;
      });
      if (selectedAsset && ids.includes(selectedAsset.id)) closePanel();
    } catch {
      toast.error(ids.length > 1 ? "Could not remove the selected assets." : "Could not remove the asset.");
    }
  }

  async function handleSetCover(assetId: string) {
    if (!openAlbum) return;
    try {
      await setAlbumCover(openAlbum.id, assetId);
      await refreshDetail();
      toast.success("Cover updated.");
    } catch {
      toast.error("Could not set the cover.");
    }
  }

  async function handleUpdateAlbum(name: string, description: string) {
    if (!openAlbum || !name.trim()) return;
    setEditing(true);
    try {
      const updated = await updateAlbum(openAlbum.id, {
        name: name.trim(),
        description: description.trim() || null,
      });
      setOpenAlbum((current) => (current ? { ...current, name: updated.name, description: updated.description } : current));
      setAlbums((prev) => prev.map((a) => (a.id === updated.id ? { ...a, name: updated.name, description: updated.description } : a)));
      setEditOpen(false);
      toast.success("Collection updated.");
    } catch {
      toast.error("Could not update the collection.");
    } finally {
      setEditing(false);
    }
  }

  // Open the shared asset detail panel for a photo inside a collection (reuses MediaLibrary panel).
  function openAssetDetail(asset: AlbumAssetSummary) {
    setSelectedAsset(summaryToMediaAsset(asset));
    setPanelOpen(true);
    getMediaAsset(asset.id)
      .then((res) => setSelectedAsset(res.data))
      .catch(() => { /* keep summary view on fetch error */ });
    setAssetHistory([]);
    setHistoryLoading(true);
    getAssetHistory(asset.id)
      .then(setAssetHistory)
      .catch(() => setAssetHistory([]))
      .finally(() => setHistoryLoading(false));
    setIntegrityHistory([]);
    setIntegrityHistoryLoading(true);
    getMediaAssetIntegrityHistory(asset.id)
      .then(setIntegrityHistory)
      .catch(() => setIntegrityHistory([]))
      .finally(() => setIntegrityHistoryLoading(false));
  }

  function closePanel() {
    setPanelOpen(false);
  }

  // ----- Multi-select (mirrors Media Library: click toggles selection + opens the panel) -----
  function toggleCheck(id: string) {
    setCheckedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function clearChecked() {
    setCheckedIds(new Set());
    closePanel();
  }

  function viewAssetById(id: string) {
    const summary = openAlbum?.assets.find((a) => a.id === id);
    if (summary) openAssetDetail(summary);
  }

  function handleDeselect(id: string) {
    const remaining = new Set(checkedIds);
    remaining.delete(id);
    toggleCheck(id);
    if (remaining.size === 0) {
      closePanel();
    } else if (selectedAsset?.id === id) {
      const nextId = [...remaining][0];
      if (nextId) viewAssetById(nextId);
      else closePanel();
    }
  }

  function handleToggleCheck(asset: AlbumAssetSummary) {
    if (checkedIds.has(asset.id)) {
      handleDeselect(asset.id);
    } else {
      toggleCheck(asset.id);
      openAssetDetail(asset);
    }
  }

  function activeAssetIds(): string[] {
    if (checkedIds.size > 0) return [...checkedIds];
    return selectedAsset ? [selectedAsset.id] : [];
  }

  function handleNewPost() {
    const ids = activeAssetIds();
    if (isAdmin) {
      const base = "/admin/resolution?tab=direct-post";
      navigate(ids.length > 0 ? `${base}&assetIds=${encodeURIComponent(ids.join(","))}` : base);
    } else {
      navigate(ids.length > 0 ? `/submissions/new?assetIds=${encodeURIComponent(ids.join(","))}` : "/submissions/new");
    }
  }

  async function handlePanelDownload() {
    if (!selectedAsset?.storageUrl) {
      toast.error("No file URL available.");
      return;
    }
    const { storageUrl, fileName } = selectedAsset;
    try {
      const response = await fetch(storageUrl);
      if (!response.ok) throw new Error(`Status ${response.status}`);
      const blob = await response.blob();
      const objectUrl = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = objectUrl;
      anchor.download = fileName || "asset";
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(objectUrl);
    } catch {
      window.open(storageUrl, "_blank");
    }
  }

  async function handlePanelVisibility(visibility: MediaVisibility) {
    if (!selectedAsset) return;
    setVisibilityBusy(true);
    try {
      const updated = await changeAssetVisibility(selectedAsset.id, visibility);
      setSelectedAsset(updated);
      toast.success(
        visibility === "cleared_for_public" ? "Asset cleared for public use." : "Asset marked internal only.",
      );
    } catch (err: unknown) {
      const message =
        (err as { response?: { data?: { error?: string; message?: string } } })?.response?.data
          ?.error ?? "Could not update visibility.";
      toast.error(message);
    } finally {
      setVisibilityBusy(false);
    }
  }

  async function handlePanelIntegrity() {
    if (!selectedAsset) return;
    setIntegrityBusy(true);
    try {
      const result = await verifyMediaAssetIntegrity(selectedAsset.id);
      applyIntegrityResult(result);
      getMediaAssetIntegrityHistory(result.assetId)
        .then(setIntegrityHistory)
        .catch(() => undefined);
      if (result.integrityStatus === "verified") {
        toast.success("File integrity verified.");
      } else {
        toast.error(result.integrityFailureReason || "File integrity could not be verified.");
      }
    } catch {
      toast.error("Could not verify file integrity.");
    } finally {
      setIntegrityBusy(false);
    }
  }

  async function handlePanelIntegrityAcknowledge() {
    if (!selectedAsset) return;
    setIntegrityReviewBusy(true);
    try {
      const result = await acknowledgeMediaAssetIntegrityReview(selectedAsset.id);
      applyIntegrityResult(result);
      toast.success("Integrity review acknowledged.");
    } catch {
      toast.error("Could not acknowledge this integrity review.");
    } finally {
      setIntegrityReviewBusy(false);
    }
  }

  function applyIntegrityResult(result: MediaIntegrityResult) {
    setSelectedAsset((current) => current ? {
      ...current,
      contentSha256: result.contentSha256,
      checksumGeneratedAt: result.checksumGeneratedAt,
      integrityStatus: result.integrityStatus,
      integrityCheckedAt: result.integrityCheckedAt,
      integrityFailureReason: result.integrityFailureReason,
      integrityReviewStatus: result.integrityReviewStatus,
      integrityReviewAcknowledgedAt: result.integrityReviewAcknowledgedAt,
      integrityReviewAcknowledgedBy: result.integrityReviewAcknowledgedBy,
    } : current);
  }

  function renderTopNav(collectionName?: string | null) {
    return (
      <nav className="med-topnav">
        <div className="med-nav-left">
          <button
            className="med-back-btn"
            type="button"
            onClick={() => { if (collectionName) exitCollection(); else navigate("/media-repository"); }}
          >
            <i className="ti ti-arrow-left"></i>
            <span>Back</span>
          </button>
          <div className="med-nav-brand">
            <div className="med-nav-brand-icon">
              <BrandMark />
            </div>
            <div className="med-nav-brand-name">
              DASIG<em>Connect</em>
            </div>
          </div>
          <div className="med-nav-breadcrumb" aria-label="Breadcrumb">
            <span className="med-nav-chevron">&gt;</span>
            <button className="alb-crumb-link" type="button" onClick={() => navigate("/media-repository")}>
              Media Library
            </button>
            <span className="med-nav-chevron">&gt;</span>
            {collectionName ? (
              <button className="alb-crumb-link" type="button" onClick={exitCollection}>
                Collections
              </button>
            ) : (
              <span>Collections</span>
            )}
            {collectionName && (
              <>
                <span className="med-nav-chevron">&gt;</span>
                <span className="med-nav-crumb-current" title={collectionName}>{collectionName}</span>
              </>
            )}
          </div>
        </div>
        <div className="med-nav-right">
          <div className="med-nav-chip">{formatRole(user.role)}</div>
          <div className="med-nav-avatar" aria-label={user.email}>
            {user.initials}
          </div>
        </div>
      </nav>
    );
  }

  const confirmDialog = confirm ? (
    <ConfirmDialog
      title={confirm.title}
      message={confirm.message}
      confirmLabel={confirm.confirmLabel}
      dangerous={confirm.dangerous}
      onCancel={() => setConfirm(null)}
      onConfirm={() => { const run = confirm.onConfirm; setConfirm(null); run(); }}
    />
  ) : null;

  // ── Detail view ───────────────────────────────────────────────────────────
  if (openAlbum) {
    // Tag chips from the collection's AI categories — reuses FilterBar's chip UI.
    const tagCounts = new Map<string, number>();
    for (const a of openAlbum.assets) {
      if (a.aiCategory) tagCounts.set(a.aiCategory, (tagCounts.get(a.aiCategory) ?? 0) + 1);
    }
    const tagChips = Array.from(tagCounts.entries())
      .sort((x, y) => y[1] - x[1])
      .map(([label, count]) => ({ label, count }));

    // Match reasons keyed by asset id, from the hybrid search hits.
    const reasonsByAssetId = new Map<string, string[]>();
    for (const hit of colSearch.hits) reasonsByAssetId.set(hit.asset.id, hit.matchReasons);

    // When searching, reuse the Media Library hybrid results but scope them to this
    // collection's assets, preserving the backend relevance order. Otherwise fall back
    // to the local tag filter + sort browse.
    const albumById = new Map(openAlbum.assets.map((a) => [a.id, a] as const));
    const visibleAssets = searchActive
      ? colSearch.hits
          .map((hit) => albumById.get(hit.asset.id))
          .filter((a): a is AlbumAssetSummary => Boolean(a))
      : openAlbum.assets
          .filter((a) => {
            if (collectionTags.size > 0 && !(a.aiCategory && collectionTags.has(a.aiCategory))) return false;
            return true;
          })
          .sort((a, b) => {
            if (collectionSort === "newest") return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
            if (collectionSort === "oldest") return new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
            if (collectionSort === "name") return (a.title || a.fileName).localeCompare(b.title || b.fileName);
            if (collectionSort === "size") return b.fileSizeBytes - a.fileSizeBytes;
            return 0;
          });

    return (
      <div className={`med-page alb-page${panelOpen ? " panel-open" : ""}`}>
        {renderTopNav(openAlbum.name)}
        <div className="alb-page-canvas">
          <div className="med-header">
            <div>
              <h1 className="med-title">{openAlbum.name}</h1>
              <p className="med-subtitle">
                {openAlbum.assetCount} {openAlbum.assetCount === 1 ? "item" : "items"}
                {openAlbum.source === "ai_suggested" ? " - AI-suggested" : ""}
                {openAlbum.description ? ` - ${openAlbum.description}` : ""}
              </p>
            </div>
            <div className="med-header-actions">
              <button className="med-btn med-btn-ghost med-btn-sm" type="button" onClick={() => setEditOpen(true)}>
                Edit
              </button>
              <button className="med-btn med-btn-primary med-btn-sm" type="button" onClick={openPicker}>
                Add media
              </button>
            </div>
          </div>

          <FilterBar
            search={collectionSearch}
            sort={collectionSort}
            viewMode={collectionView}
            activeTags={collectionTags}
            tagChips={tagChips}
            searching={colSearch.loading}
            searchActive={searchActive}
            onSearchChange={setCollectionSearch}
            onSearchSubmit={runCollectionSearch}
            onSearchClear={clearCollectionSearch}
            onSortChange={setCollectionSort}
            onViewModeChange={setCollectionView}
            onTagToggle={toggleCollectionTag}
            suggestionCorpus={collectionCorpus}
          />

          {searchActive && (
            <div className="med-result-strip">
              <p className="med-result-count">
                {colSearch.loading ? (
                  <>Searching this collection…</>
                ) : (
                  <>
                    <strong>{visibleAssets.length}</strong>{" "}
                    {visibleAssets.length === 1 ? "result" : "results"} for{" "}
                    <span className="med-result-query">“{colSearch.activeQuery}”</span>
                    <span className="med-result-hint">
                      {colSearch.chronological ? " · newest first" : " · ranked by relevance"}
                    </span>
                  </>
                )}
              </p>
              <button className="med-btn med-btn-ghost med-btn-sm" type="button" onClick={clearCollectionSearch}>
                Clear search
              </button>
            </div>
          )}

          {searchActive && colSearch.loading ? (
            <div className="med-empty">
              <div className="med-empty-title">Searching this collection…</div>
            </div>
          ) : searchActive && colSearch.error ? (
            <div className="med-empty">
              <div className="med-empty-title">Search is unavailable</div>
              <p className="med-empty-sub">{colSearch.error}</p>
              <button className="med-btn med-btn-ghost med-btn-sm" type="button" onClick={() => runCollectionSearch()} style={{ marginTop: 12 }}>
                Try again
              </button>
            </div>
          ) : openAlbum.assets.length === 0 ? (
            <div className="med-empty">
              <div className="med-empty-title">This collection is empty</div>
              <p className="med-empty-sub">Add media when this collection is ready to reuse.</p>
            </div>
          ) : visibleAssets.length === 0 ? (
            <div className="med-empty">
              <div className="med-empty-title">
                {searchActive ? `No results for “${colSearch.activeQuery}”` : "No items match your filters"}
              </div>
              <p className="med-empty-sub">
                {searchActive ? "Try a different prompt, or clear the search." : "Try a different tag, or clear the filters."}
              </p>
            </div>
          ) : (
            <div className={`alb-grid${collectionView === "list" ? " list-view" : ""}`}>
              {visibleAssets.map((asset, idx) => (
                <AlbumAssetTile
                  key={asset.id}
                  asset={asset}
                  isCover={openAlbum.coverAssetId === asset.id}
                  checked={checkedIds.has(asset.id)}
                  listView={collectionView === "list"}
                  matchReasons={searchActive ? reasonsByAssetId.get(asset.id) : undefined}
                  feedback={searchActive ? (searchFeedback.get(asset.id) ?? null) : undefined}
                  onFeedback={searchActive ? (action) => handleSearchFeedback(asset, idx + 1, action) : undefined}
                  onSetCover={(id) => void handleSetCover(id)}
                  onRemove={requestRemoveAsset}
                  onOpen={(a) => { logSearchSelection(a, idx + 1); handleToggleCheck(a); }}
                />
              ))}
            </div>
          )}
        </div>

        <AssetDetailPanel
          renderMode="inline"
          asset={selectedAsset}
          open={panelOpen}
          isAdmin={isAdmin}
          selectionMode={checkedIds.size > 0}
          selectedAssets={openAlbum.assets.filter((a) => checkedIds.has(a.id)).map(summaryToMediaAsset)}
          onViewAsset={(a) => viewAssetById(a.id)}
          onDeselectAsset={handleDeselect}
          onClearSelection={clearChecked}
          onNewPost={handleNewPost}
          onClose={closePanel}
          onAddToDraft={() => {}}
          onDownload={() => void handlePanelDownload()}
          canDelete={Boolean(selectedAsset)}
          onRequestDelete={() => { if (selectedAsset) requestRemoveFromCollection([selectedAsset.id]); }}
          canBulkDelete={checkedIds.size > 0}
          onRequestBulkDelete={() => requestRemoveFromCollection(activeAssetIds())}
          history={assetHistory}
          historyLoading={historyLoading}
          onChangeVisibility={(v) => void handlePanelVisibility(v)}
          visibilityBusy={visibilityBusy}
          onVerifyIntegrity={() => void handlePanelIntegrity()}
          integrityBusy={integrityBusy}
          integrityHistory={integrityHistory}
          integrityHistoryLoading={integrityHistoryLoading}
          canAcknowledgeIntegrity={user.role === "validator" || user.role === "admin"}
          onAcknowledgeIntegrity={() => void handlePanelIntegrityAcknowledge()}
          integrityReviewBusy={integrityReviewBusy}
          onUploadReplacement={() => {
            if (selectedAsset) {
              navigate(`/media-repository?replaceAssetId=${encodeURIComponent(selectedAsset.id)}`);
            }
          }}
        />

        {editOpen && (
          <CreateAlbumModal
            creating={editing}
            initialName={openAlbum.name}
            initialDescription={openAlbum.description ?? ""}
            title="Edit Collection"
            submitLabel="Save"
            busyLabel="Saving..."
            onCancel={() => { if (!editing) setEditOpen(false); }}
            onCreate={(name, description) => void handleUpdateAlbum(name, description)}
          />
        )}

        {pickerOpen && (
          <AssetPickerModal
            assets={pickerAssets}
            loading={pickerLoading}
            selected={pickerSelected}
            adding={adding}
            onToggle={togglePick}
            onSelectAll={selectAllPicker}
            onClearSelection={clearPicker}
            onClose={() => { if (!adding) setPickerOpen(false); }}
            onConfirm={() => void handleAddSelected()}
          />
        )}

        {confirmDialog}
      </div>
    );
  }

  // ── List view ─────────────────────────────────────────────────────────────
  return (
    <div className="med-page alb-page">
      {renderTopNav()}
      <div className="alb-page-canvas">
        <div className="med-header">
          <div>
            <h1 className="med-title">Collections</h1>
            <p className="med-subtitle">Reusable media sets for campaigns and events</p>
          </div>
          <div className="med-header-actions">
            <button className="med-btn med-btn-ghost med-btn-sm" type="button" onClick={openBuilder}>
              AI Builder
            </button>
            <button className="med-btn med-btn-primary med-btn-sm" type="button" onClick={() => setCreateOpen(true)}>
              New Collection
            </button>
          </div>
        </div>

      {isAdmin && (
        <LibraryScopeSelector
          institutions={institutions}
          selectedInstitutionId={selectedInstitutionId}
          loading={institutionsLoading}
          onInstitutionChange={(institutionId) => {
            setOpenAlbum(null);
            setSelectedInstitutionId(institutionId);
          }}
        />
      )}

      {loading ? (
        <div className="med-empty"><div className="med-empty-title">Loading collections...</div></div>
      ) : isAdmin && !selectedInstitutionId ? (
        <div className="med-empty">
          <div className="med-empty-title">Select an institution</div>
          <p className="med-empty-sub">Choose an institution to view its collections.</p>
        </div>
      ) : error ? (
        <div className="med-empty">
          <div className="med-empty-title">Failed to load collections</div>
          <p className="med-empty-sub">{error}</p>
          <button className="med-btn med-btn-ghost" type="button" style={{ marginTop: 20 }} onClick={reload}>Try again</button>
        </div>
      ) : albums.length === 0 ? (
        <div className="med-empty">
          <div className="med-empty-title">No collections yet</div>
          <p className="med-empty-sub">Create a reusable set for related media.</p>
          <button className="med-btn med-btn-primary" type="button" style={{ marginTop: 20 }} onClick={() => setCreateOpen(true)}>New Collection</button>
        </div>
      ) : (
        <div className="alb-list">
          {albums.map((album) => (
            <AlbumCard
              key={album.id}
              album={album}
              onOpen={(id) => void openDetail(id)}
              onDelete={(a) => void handleDeleteAlbum(a)}
            />
          ))}
        </div>
      )}
      </div>

      {createOpen && (
        <CreateAlbumModal
          creating={creating}
          onCancel={() => setCreateOpen(false)}
          onCreate={(name, description) => void handleCreate(name, description)}
        />
      )}

      <PromptCollectionBuilderContainer
        open={builderOpen}
        isAdmin={isAdmin}
        institutionId={isAdmin ? selectedInstitutionId : null}
        onClose={() => setBuilderOpen(false)}
        onCreated={(album, detail) => {
          setAlbums((prev) => [{ ...album, assetCount: detail.assetCount }, ...prev]);
          setOpenAlbum(detail);
        }}
      />

      {detailLoading && <div className="alb-loading-veil">Opening collection...</div>}

      {confirmDialog}
    </div>
  );
}

function BrandMark() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 2L22 7V17L12 22L2 17V7L12 2Z" />
    </svg>
  );
}

function formatRole(role: User["role"]) {
  if (role === "admin") return "Administrator";
  return role.charAt(0).toUpperCase() + role.slice(1);
}

/** Map a collection asset summary to the MediaAsset shape the shared detail panel expects,
 *  for instant display before the full detail fetch resolves. */
function summaryToMediaAsset(a: AlbumAssetSummary): MediaAsset {
  return {
    id: a.id,
    code: a.assetCode,
    title: a.title || a.fileName,
    fileName: a.fileName,
    fileType: a.fileType,
    fileSizeBytes: a.fileSizeBytes,
    storageUrl: a.storageUrl,
    institutionId: a.institutionId ?? "",
    institutionName: a.institutionName ?? undefined,
    uploaderId: a.uploaderId ?? undefined,
    uploaderName: a.uploaderEmail ?? undefined,
    uploadedAt: a.createdAt,
    status: "ready",
    aiTags: a.aiCategory ? [{ label: a.aiCategory, confidence: 100 }] : [],
  };
}

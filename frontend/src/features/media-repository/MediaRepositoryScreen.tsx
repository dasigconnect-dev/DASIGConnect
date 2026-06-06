import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import type { User } from "../../types/auth.types";
import type { MediaAsset, MediaUsage } from "../../api/mediaApi";
import {
  bulkDeleteMediaAssets,
  bulkMoveAssets,
  changeAssetVisibility,
  createMediaImportBatch,
  deleteMediaAsset,
  deleteMediaImportBatch,
  fetchMediaSearchSuggestions,
  getAssetHistory,
  getMediaAssetIntegrityHistory,
  getMediaAsset,
  getMediaAssetUploadUrl,
  listImportBatchAssets,
  listMediaImportBatches,
  markImportBatchCurated,
  recordSearchFeedback,
  registerMediaAsset,
  verifyMediaAssetIntegrity,
  acknowledgeMediaAssetIntegrityReview,
  type MediaAssetDetailResponse,
  type MediaAssetCurationEdit,
  type MediaAuditEntry,
  type MediaImportBatch,
  type MediaIntegrityCheck,
  type MediaIntegrityResult,
  type MediaVisibility,
} from "../../api/mediaApi";
import {
  listFolders,
  createFolder,
  renameFolder,
  deleteFolder,
  type Folder,
} from "../../api/folderApi";
import { listInstitutions, type InstitutionResponse } from "../../api/authApi";
import FolderSidebar, { type FolderFilterMode } from "./components/FolderSidebar";
import FolderFormModal from "./components/FolderFormModal";
import {
  attachAsset,
  listSubmissions,
  type SubmissionSummary,
} from "../../api/submissionApi";
import { useToast } from "../../context/ToastContext";
import { usePersistentSelection } from "../../hooks/usePersistentSelection";
import { useMediaAssets } from "./hooks/useMediaAssets";
import { useMediaSearch } from "./hooks/useMediaSearch";
import type { SortOption, ViewMode, DeleteTier } from "./types";
import AssetCard from "./components/AssetCard";
import FilterBar from "./components/FilterBar";
import LibraryScopeSelector from "./components/LibraryScopeSelector";
import AssetDetailPanel from "./components/AssetDetailPanel";
import UploadModal from "./components/UploadModal";
import DeleteModal from "./components/DeleteModal";
import AddToDraftModal from "./components/AddToDraftModal";
import BatchCurationModal from "./components/BatchCurationModal";
import PromptCollectionBuilderContainer from "../albums/components/PromptCollectionBuilderContainer";
import ConfirmDialog from "../user-management/components/ConfirmDialog";

interface ConfirmState {
  title: string;
  message: string;
  confirmLabel: string;
  dangerous?: boolean;
  onConfirm: () => void;
}
import "../../styles/media-repository.css";
import "../../styles/folders.css";

interface MediaRepositoryScreenProps {
  user: User;
}

const MAX_UPLOAD_MB = 50;

function isConflict(error: unknown) {
  if (typeof error !== "object" || error === null) return false;
  return (error as { response?: { status?: number } }).response?.status === 409;
}

function safeFileName(fileName: string) {
  return fileName.replace(/[^a-zA-Z0-9._-]/g, "-");
}

function fileTypeFromFile(file: File) {
  const ext = file.name.split(".").pop()?.toLowerCase() ?? "";
  return ext === "jpg" ? "jpeg" : ext;
}

// PUT the file straight to Supabase using XHR so we can report real upload
// progress (fetch() cannot) and surface the actual Supabase status on failure.
function putToSupabase(
  signedUrl: string,
  file: File,
  onProgress?: (pct: number) => void,
) {
  return new Promise<void>((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("PUT", signedUrl);
    xhr.setRequestHeader("Content-Type", file.type || "application/octet-stream");
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable && onProgress) {
        onProgress(Math.round((event.loaded / event.total) * 100));
      }
    };
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve();
      } else {
        const detail = xhr.responseText ? `: ${xhr.responseText.slice(0, 160)}` : "";
        reject(new Error(`Storage rejected the upload (${xhr.status})${detail}`));
      }
    };
    xhr.onerror = () => reject(new Error("Network error during upload."));
    xhr.ontimeout = () => reject(new Error("Upload timed out."));
    xhr.send(file);
  });
}

export default function MediaRepositoryScreen({ user }: MediaRepositoryScreenProps) {
  const toast = useToast();
  const navigate = useNavigate();
  const isAdmin = user.role === "admin";

  const [searchParams, setSearchParams] = useSearchParams();
  // UC-4.12 Phase 7C drill-down: Repository Health tiles deep-link here with ?health=<key>.
  const healthFilter = searchParams.get("health");

  const [networkView, setNetworkView] = useState(false);
  const [institutions, setInstitutions] = useState<InstitutionResponse[]>([]);
  const [institutionsLoading, setInstitutionsLoading] = useState(isAdmin);
  const [selectedInstitutionId, setSelectedInstitutionId] = useState<string | null>(null);
  const mediaScopeReady = !isAdmin || networkView || Boolean(selectedInstitutionId);
  const mediaInstitutionId = isAdmin && networkView ? null : selectedInstitutionId;
  const { assets, setAssets, loading, error, refresh } = useMediaAssets(
    networkView,
    mediaInstitutionId,
    mediaScopeReady,
    healthFilter,
  );

  const [search, setSearch] = useState("");
  const [sort, setSort] = useState<SortOption>("newest");
  const [viewMode, setViewMode] = useState<ViewMode>("grid");
  const [activeTags, setActiveTags] = useState<Set<string>>(new Set());

  // UC-4.5 natural-language hybrid search (server-side). Distinct from the
  // browse list above: results are ranked by relevance with match reasons.
  const mediaSearch = useMediaSearch();
  const searchActive = mediaSearch.active;
  // UC-4.6: per-result thumbs feedback, keyed by asset id; reset each new search.
  const [searchFeedback, setSearchFeedback] = useState<Map<string, "up" | "down">>(new Map());

  function runSmartSearch(queryOverride?: string) {
    const q = (queryOverride ?? search).trim();
    if (!q) {
      mediaSearch.clear();
      return;
    }
    if (!mediaScopeReady) {
      toast.error("Select an institution or network scope before searching.");
      return;
    }
    setSearchFeedback(new Map());
    void mediaSearch.run({ query: q, networkView, institutionId: mediaInstitutionId });
  }

  function clearSmartSearch() {
    setSearch("");
    setSearchFeedback(new Map());
    mediaSearch.clear();
  }

  function handleSearchFeedback(asset: MediaAsset, rank: number, action: "thumbs_up" | "thumbs_down") {
    const dir = action === "thumbs_up" ? "up" : "down";
    setSearchFeedback((prev) => {
      const next = new Map(prev);
      next.set(asset.id, dir);
      return next;
    });
    void recordSearchFeedback({ assetId: asset.id, action, query: mediaSearch.activeQuery, rank });
    toast.success("Thanks — your feedback improves future search.");
  }

  // UC-4.6: a result the user opens is an implicit "selected" signal.
  function logSearchSelection(asset: MediaAsset, rank: number) {
    if (!searchActive || checkedIds.has(asset.id)) return;
    void recordSearchFeedback({ assetId: asset.id, action: "selected", query: mediaSearch.activeQuery, rank });
  }

  const [folders, setFolders] = useState<Folder[]>([]);
  const [foldersLoading, setFoldersLoading] = useState(true);
  const [filterMode, setFilterMode] = useState<FolderFilterMode>("all");
  const [selectedFolderId, setSelectedFolderId] = useState<string | null>(null);
  // Folder create/rename modal (delete uses the shared ConfirmDialog).
  const [folderModal, setFolderModal] = useState<{ mode: "create" | "rename"; folder: Folder | null } | null>(null);
  const [folderSaving, setFolderSaving] = useState(false);

  const [selectedAsset, setSelectedAsset] = useState<MediaAsset | null>(null);
  const [panelOpen, setPanelOpen] = useState(false);
  const [assetHistory, setAssetHistory] = useState<MediaAuditEntry[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [visibilityBusy, setVisibilityBusy] = useState(false);
  const [integrityBusy, setIntegrityBusy] = useState(false);
  const [integrityHistory, setIntegrityHistory] = useState<MediaIntegrityCheck[]>([]);
  const [integrityHistoryLoading, setIntegrityHistoryLoading] = useState(false);
  const [integrityReviewBusy, setIntegrityReviewBusy] = useState(false);

  const {
    selected: checkedIds,
    toggle: toggleCheck,
    clear: clearSelection,
  } = usePersistentSelection("dasigconnect:media-selection");

  // Always start with an empty selection when the page mounts.
  // IDs are already captured in the ?assetIds= URL before navigating away,
  // so there is no reason to restore a stale sessionStorage selection.
  useEffect(() => {
    clearSelection();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const [addToDraftOpen, setAddToDraftOpen] = useState(false);
  const [drafts, setDrafts] = useState<SubmissionSummary[]>([]);
  const [draftsLoading, setDraftsLoading] = useState(false);
  const [busyDraftId, setBusyDraftId] = useState<string | null>(null);

  const [uploadOpen, setUploadOpen] = useState(false);
  const [aiBuilderOpen, setAiBuilderOpen] = useState(false);
  // Shared confirmation dialog for folder / batch deletion.
  const [confirm, setConfirm] = useState<ConfirmState | null>(null);
  const [latestUploadBatchId, setLatestUploadBatchId] = useState<string | null>(null);
  const [batchReviewOpen, setBatchReviewOpen] = useState(false);
  const [batchGroups, setBatchGroups] = useState<MediaImportBatch[]>([]);
  const [batchListLoading, setBatchListLoading] = useState(false);
  const [selectedReviewBatchId, setSelectedReviewBatchId] = useState<string | null>(null);
  const [batchAssets, setBatchAssets] = useState<MediaAssetDetailResponse[]>([]);
  const [batchReviewLoading, setBatchReviewLoading] = useState(false);
  const [batchCurating, setBatchCurating] = useState(false);

  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleteTier, setDeleteTier] = useState<DeleteTier | null>(null);
  const [deleteAsset, setDeleteAsset] = useState<MediaAsset | null>(null);
  const [deleteAssets, setDeleteAssets] = useState<MediaAsset[]>([]);
  const [blockingUsages, setBlockingUsages] = useState<MediaUsage[]>([]);
  const [warningUsages, setWarningUsages] = useState<MediaUsage[]>([]);
  const [deleting, setDeleting] = useState(false);

  const tagChips = useMemo(() => {
    const counts = new Map<string, number>();
    for (const asset of assets) {
      for (const tag of asset.aiTags ?? []) {
        counts.set(tag.label, (counts.get(tag.label) ?? 0) + 1);
      }
    }
    return Array.from(counts.entries())
      .sort((a, b) => b[1] - a[1])
      .map(([label, count]) => ({ label, count }));
  }, [assets]);

  // Whole-library related-search autocomplete (UC-4.5): backend suggestions across all tags,
  // filenames, uploaders, collections, and folders — not just the loaded page.
  const fetchSuggestions = useCallback(
    (q: string, signal?: AbortSignal) => {
      if (!mediaScopeReady) return Promise.resolve([]);
      return fetchMediaSearchSuggestions(
        { query: q, networkView, institutionId: mediaInstitutionId },
        signal,
      );
    },
    [mediaScopeReady, networkView, mediaInstitutionId],
  );

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

  function changeInstitutionScope(institutionId: string | null) {
    setSelectedInstitutionId(institutionId);
    selectAllFolders();
    clearSelection();
    closePanel();
    clearSmartSearch(); // search results are scope-bound; reset on scope change
  }

  function refreshFolders() {
    if (isAdmin && (networkView || !selectedInstitutionId)) {
      setFolders([]);
      setFoldersLoading(false);
      return;
    }
    setFoldersLoading(true);
    listFolders(isAdmin ? selectedInstitutionId : undefined)
      .then(setFolders)
      .catch(() => { /* sidebar shows empty state on error */ })
      .finally(() => setFoldersLoading(false));
  }

  useEffect(() => {
    let active = true;

    queueMicrotask(() => {
      if (active) refreshFolders();
    });

    return () => {
      active = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [networkView, selectedInstitutionId]);

  const unfiledCount = useMemo(() => assets.filter((a) => !a.folderId).length, [assets]);

  function selectAllFolders() { setFilterMode("all"); setSelectedFolderId(null); }
  function selectUnfiled() { setFilterMode("unfiled"); setSelectedFolderId(null); }
  function selectFolder(id: string) { setFilterMode("folder"); setSelectedFolderId(id); }

  function openCreateFolder() {
    if (isAdmin && (networkView || !selectedInstitutionId)) {
      toast.error("Select an institution before creating a folder.");
      return;
    }
    setFolderModal({ mode: "create", folder: null });
  }

  function openRenameFolder(folder: Folder) {
    setFolderModal({ mode: "rename", folder });
  }

  async function submitFolderForm(name: string) {
    if (!folderModal) return;
    setFolderSaving(true);
    try {
      if (folderModal.mode === "create") {
        await createFolder({
          name,
          institutionId: isAdmin ? selectedInstitutionId : undefined,
        });
        toast.success("Folder created.");
      } else if (folderModal.folder) {
        await renameFolder(folderModal.folder.id, name);
        toast.success("Folder renamed.");
      }
      refreshFolders();
      setFolderModal(null);
    } catch {
      toast.error(folderModal.mode === "create" ? "Could not create the folder." : "Could not rename the folder.");
    } finally {
      setFolderSaving(false);
    }
  }

  function handleDeleteFolder(folder: Folder) {
    setConfirm({
      title: "Delete folder",
      message: `Delete folder "${folder.name}"? Assets inside are moved to Unfiled, not deleted.`,
      confirmLabel: "Delete",
      dangerous: true,
      onConfirm: () => void performDeleteFolder(folder),
    });
  }

  async function performDeleteFolder(folder: Folder) {
    try {
      await deleteFolder(folder.id);
      if (selectedFolderId === folder.id) selectAllFolders();
      refreshFolders();
      void refresh(); // assets in the folder become unfiled (folder_id → null)
      toast.success("Folder deleted.");
    } catch {
      toast.error("Could not delete the folder.");
    }
  }

  async function handleMoveSelected(folderId: string | null) {
    const ids = [...checkedIds];
    if (ids.length === 0) return;
    if (isAdmin && networkView) {
      toast.error("Select one institution before moving media.");
      return;
    }
    try {
      const res = await bulkMoveAssets(ids, folderId);
      toast.success(`Moved ${res.affected} ${res.affected === 1 ? "asset" : "assets"}.`);
      clearChecked();
      void refresh();
      refreshFolders();
    } catch {
      toast.error("Could not move the selected assets.");
    }
  }

  // Browse mode: folder + tag + sort filtering. Free-text narrowing now happens
  // through the server-side hybrid search (mediaSearch) rather than substring match.
  const filteredAssets = useMemo(() => {
    let result = assets.filter((a) => {
      if (filterMode === "unfiled" && a.folderId) return false;
      if (filterMode === "folder" && selectedFolderId && a.folderId !== selectedFolderId) return false;
      if (activeTags.size > 0) {
        const assetTagLabels = new Set((a.aiTags ?? []).map((t) => t.label.toLowerCase()));
        const selectedTags = [...activeTags].map((tag) => tag.toLowerCase());
        if (!selectedTags.some((tag) => assetTagLabels.has(tag))) return false;
      }
      return true;
    });

    result = [...result].sort((a, b) => {
      if (sort === "newest") return new Date(b.uploadedAt).getTime() - new Date(a.uploadedAt).getTime();
      if (sort === "oldest") return new Date(a.uploadedAt).getTime() - new Date(b.uploadedAt).getTime();
      if (sort === "name") return a.title.localeCompare(b.title);
      if (sort === "size") return b.fileSizeBytes - a.fileSizeBytes;
      return 0;
    });

    return result;
  }, [assets, sort, activeTags, filterMode, selectedFolderId]);

  // Search results keep the backend relevance order; reasons are keyed by asset id.
  const searchHitAssets = useMemo(() => mediaSearch.hits.map((h) => h.asset), [mediaSearch.hits]);
  const reasonsByAssetId = useMemo(() => {
    const map = new Map<string, string[]>();
    for (const hit of mediaSearch.hits) map.set(hit.asset.id, hit.matchReasons);
    return map;
  }, [mediaSearch.hits]);
  const gridAssets = searchActive ? searchHitAssets : filteredAssets;

  // Selection lookups must resolve assets that exist only in search results, not
  // just the browse list, so bulk actions on a searched-then-checked asset work.
  const assetPool = useMemo(() => {
    if (!searchActive) return assets;
    const byId = new Map(assets.map((a) => [a.id, a] as const));
    for (const a of searchHitAssets) if (!byId.has(a.id)) byId.set(a.id, a);
    return [...byId.values()];
  }, [assets, searchHitAssets, searchActive]);

  // Not manually memoized: checkedIds is a Set the selection hook mutates, which the React
  // Compiler can't prove stable — let it auto-memoize rather than a manual useMemo it rejects.
  const selectedAssets = assetPool.filter((a) => checkedIds.has(a.id));
  const selectionMode = checkedIds.size > 0;
  const canBulkDelete = selectedAssets.length > 0 && selectedAssets.every(canDeleteAsset);
  const latestReviewBatchId = latestUploadBatchId
    ?? [...assets]
      .sort((a, b) => new Date(b.uploadedAt).getTime() - new Date(a.uploadedAt).getTime())
      .find((asset) => Boolean(asset.importBatchId) && !asset.curatedAt)?.importBatchId
    ?? null;

  function openAsset(asset: MediaAsset) {
    setSelectedAsset(asset);
    setPanelOpen(true);
    getMediaAsset(asset.id)
      .then((res) => setSelectedAsset(res.data))
      .catch(() => { /* panel stays with summary data on fetch error */ });

    // UC-4.11: load the provenance trail for this asset.
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

  const replacementAssetId = searchParams.get("replaceAssetId");
  const deepLinkedAssetId = searchParams.get("assetId") ?? replacementAssetId;
  useEffect(() => {
    if (!deepLinkedAssetId) return;
    getMediaAsset(deepLinkedAssetId)
      .then((response) => {
        openAsset(response.data);
        if (replacementAssetId) {
          if (isAdmin) {
            setNetworkView(false);
            setSelectedInstitutionId(response.data.institutionId);
          }
          setUploadOpen(true);
          toast.info("Upload the replacement as a new asset. The original remains preserved.");
        }
        const next = new URLSearchParams(searchParams);
        next.delete("assetId");
        next.delete("replaceAssetId");
        setSearchParams(next, { replace: true });
      })
      .catch(() => toast.error("The integrity alert asset could not be opened."));
    // The asset ID is the one-shot deep-link trigger.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [deepLinkedAssetId, replacementAssetId]);

  function closePanel() {
    setPanelOpen(false);
    // selectedAsset is intentionally kept so the panel slides out with its
    // content visible instead of going blank mid-animation. openAsset() always
    // sets a fresh asset before the panel is shown again, so no stale data
    // is ever displayed to the user.
  }

  function clearChecked() {
    clearSelection();
    closePanel();
  }

  // Deselects a single asset by ID and keeps panel state consistent:
  // - nothing left checked  → slide the panel closed
  // - others still checked, panel was showing this asset → switch to another selected asset
  // - others still checked, panel shows something else  → leave panel alone
  function handleDeselect(id: string) {
    const remainingIds = new Set(checkedIds);
    remainingIds.delete(id);
    toggleCheck(id);

    if (remainingIds.size === 0) {
      closePanel();
    } else if (selectedAsset?.id === id) {
      const nextAsset = assetPool.find((a) => remainingIds.has(a.id));
      if (nextAsset) openAsset(nextAsset);
      else closePanel();
    }
    // else: panel already shows a different, still-selected asset — no change needed
  }

  function handleToggleCheck(asset: MediaAsset) {
    if (checkedIds.has(asset.id)) {
      handleDeselect(asset.id);
    } else {
      toggleCheck(asset.id);
      openAsset(asset);
    }
  }

  function activeAssetIds() {
    if (checkedIds.size > 0) return [...checkedIds];
    return selectedAsset ? [selectedAsset.id] : [];
  }

  function canDeleteAsset(asset: MediaAsset) {
    if (isAdmin) return true;
    if (user.role === "validator") {
      return Boolean(user.institutionId && asset.institutionId === user.institutionId);
    }
    if (user.role === "contributor") {
      return Boolean(asset.uploaderName && asset.uploaderName.toLowerCase() === user.email.toLowerCase());
    }
    return false;
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

  function openUploadModal() {
    if (isAdmin && (networkView || !selectedInstitutionId)) {
      toast.error("Select an institution before uploading media.");
      return;
    }
    setUploadOpen(true);
  }

  function openAddToDraft() {
    if (activeAssetIds().length === 0) return;
    setAddToDraftOpen(true);
    setDraftsLoading(true);
    listSubmissions()
      .then((res) => setDrafts(res.data.filter((item) => item.status === "draft")))
      .catch(() => toast.error("Could not load your drafts."))
      .finally(() => setDraftsLoading(false));
  }

  async function handleSelectDraft(draftId: string) {
    const ids = activeAssetIds();
    if (ids.length === 0) return;
    setBusyDraftId(draftId);
    let added = 0;
    let alreadyThere = 0;
    try {
      for (const assetId of ids) {
        try {
          await attachAsset(draftId, assetId);
          added += 1;
        } catch (err: unknown) {
          if (isConflict(err)) alreadyThere += 1;
          else throw err;
        }
      }
      setAddToDraftOpen(false);
      clearSelection();
      const summary =
        added > 0
          ? `Added ${added} ${added === 1 ? "asset" : "assets"} to the draft.`
          : "Those assets are already in that draft.";
      toast.success(alreadyThere > 0 && added > 0 ? `${summary} ${alreadyThere} already there.` : summary);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Could not add to draft.";
      toast.error(message);
    } finally {
      setBusyDraftId(null);
    }
  }

  function toggleTag(tag: string) {
    setActiveTags((prev) => {
      const next = new Set(prev);
      if (next.has(tag)) next.delete(tag);
      else next.add(tag);
      return next;
    });
  }

  function clearTag(tag: string) {
    setActiveTags((prev) => {
      const next = new Set(prev);
      next.delete(tag);
      return next;
    });
  }

  async function createUploadBatch(assetCount: number) {
    if (isAdmin && (networkView || !selectedInstitutionId)) {
      throw new Error("Select an institution before uploading media.");
    }
    try {
      const { data } = await createMediaImportBatch({
        assetCount,
        institutionId: isAdmin ? selectedInstitutionId : undefined,
      });
      return data.id;
    } catch {
      toast.error("Batch grouping is unavailable right now. Uploading files without AI collection grouping.");
      return null;
    }
  }

  function openAiCollectionBuilder() {
    if (isAdmin && (networkView || !selectedInstitutionId)) {
      toast.error("Select one institution before using AI Builder.");
      return;
    }
    setAiBuilderOpen(true);
  }

  async function loadBatchGroups() {
    setBatchListLoading(true);
    try {
      const response = await listMediaImportBatches(isAdmin ? selectedInstitutionId : undefined);
      setBatchGroups(response);
    } catch {
      const fallback = batchGroupsFromLoadedAssets(assets);
      setBatchGroups(fallback);
      if (fallback.length === 0) {
        toast.error("Could not load upload batch groups.");
      }
    } finally {
      setBatchListLoading(false);
    }
  }

  async function loadBatchReview(batchId = selectedReviewBatchId) {
    if (!batchId) return;
    setBatchReviewLoading(true);
    try {
      const response = await listImportBatchAssets(
        batchId,
        isAdmin ? selectedInstitutionId : undefined,
      );
      setBatchAssets(response);
    } catch {
      toast.error("Could not load the latest upload batch.");
    } finally {
      setBatchReviewLoading(false);
    }
  }

  function openBatchReview() {
    setSelectedReviewBatchId(null);
    setBatchAssets([]);
    setBatchReviewOpen(true);
    void loadBatchGroups();
  }

  function selectReviewBatch(batchId: string) {
    setSelectedReviewBatchId(batchId);
    void loadBatchReview(batchId);
  }

  function refreshBatchReviewModal() {
    if (selectedReviewBatchId) {
      void loadBatchReview(selectedReviewBatchId);
    } else {
      void loadBatchGroups();
    }
  }

  function handleDeleteBatch(batch: MediaImportBatch) {
    const count = batch.registeredAssetCount ?? batch.assetCount;
    setConfirm({
      title: "Delete upload batch",
      message: `Delete this upload batch? The ${count} ${count === 1 ? "photo" : "photos"} stay in the library — only the batch grouping is removed.`,
      confirmLabel: "Delete batch",
      dangerous: true,
      onConfirm: () => void performDeleteBatch(batch),
    });
  }

  async function performDeleteBatch(batch: MediaImportBatch) {
    try {
      await deleteMediaImportBatch(batch.id, isAdmin ? selectedInstitutionId : undefined);
      if (selectedReviewBatchId === batch.id) {
        setSelectedReviewBatchId(null);
        setBatchAssets([]);
      }
      setLatestUploadBatchId((current) => (current === batch.id ? null : current));
      void loadBatchGroups();
      void refresh();
      toast.success("Batch removed. The photos remain in the library.");
    } catch {
      toast.error("Could not delete the batch.");
    }
  }

  async function handleConfirmBatchCuration(edits: MediaAssetCurationEdit[]) {
    if (!selectedReviewBatchId) return;
    setBatchCurating(true);
    try {
      const response = await markImportBatchCurated(
        selectedReviewBatchId,
        isAdmin ? selectedInstitutionId : undefined,
        edits,
      );
      toast.success(`Confirmed ${response.curatedCount} ${response.curatedCount === 1 ? "asset" : "assets"} as curated.`);
      setSelectedReviewBatchId(null);
      setBatchAssets([]);
      setLatestUploadBatchId(null);
      void loadBatchGroups();
      void refresh();
    } catch {
      toast.error("Could not confirm this batch.");
    } finally {
      setBatchCurating(false);
    }
  }

  async function handleUpload(
    file: File,
    onProgress?: (pct: number) => void,
    options?: { silent?: boolean; importBatchId?: string | null },
  ) {
    if (isAdmin && (networkView || !selectedInstitutionId)) {
      const message = "Select an institution before uploading media.";
      if (!options?.silent) toast.error(message);
      throw new Error(message);
    }
    if (file.size > MAX_UPLOAD_MB * 1024 * 1024) {
      const message = `${file.name} is ${(file.size / (1024 * 1024)).toFixed(1)} MB — over the ${MAX_UPLOAD_MB} MB limit.`;
      if (!options?.silent) toast.error(message);
      throw new Error(message);
    }

    try {
      onProgress?.(0);
      const { data: urlData } = await getMediaAssetUploadUrl({
        fileName: safeFileName(file.name),
        fileType: fileTypeFromFile(file),
        institutionId: isAdmin ? selectedInstitutionId : undefined,
      });

      // Reserve the last 10% for the metadata-register call below.
      await putToSupabase(urlData.signedUrl, file, (pct) =>
        onProgress?.(Math.round(pct * 0.9)),
      );

      await registerMediaAsset({
        storageUrl: urlData.publicUrl,
        fileName: file.name,
        fileType: fileTypeFromFile(file),
        fileSizeBytes: file.size,
        institutionId: isAdmin ? selectedInstitutionId : undefined,
        importBatchId: options?.importBatchId ?? undefined,
      });
      onProgress?.(100);

      if (!options?.silent) {
        toast.success("Media uploaded. AI classification in progress...");
        void refresh();
      }
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Upload failed.";
      if (!options?.silent) toast.error(message);
      throw err;
    }
  }

  async function handleChangeVisibility(visibility: MediaVisibility) {
    if (!selectedAsset) return;
    setVisibilityBusy(true);
    try {
      const updated = await changeAssetVisibility(selectedAsset.id, visibility);
      setSelectedAsset(updated);
      setAssets((prev) => prev.map((a) => (a.id === updated.id ? { ...a, visibility: updated.visibility } : a)));
      toast.success(
        visibility === "cleared_for_public" ? "Asset cleared for public use." : "Asset marked internal only.",
      );
    } catch {
      toast.error("Could not update visibility.");
    } finally {
      setVisibilityBusy(false);
    }
  }

  async function handleVerifyIntegrity() {
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

  async function handleAcknowledgeIntegrity() {
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
    const integrityUpdate = {
      contentSha256: result.contentSha256,
      checksumGeneratedAt: result.checksumGeneratedAt,
      integrityStatus: result.integrityStatus,
      integrityCheckedAt: result.integrityCheckedAt,
      integrityFailureReason: result.integrityFailureReason,
      integrityReviewStatus: result.integrityReviewStatus,
      integrityReviewAcknowledgedAt: result.integrityReviewAcknowledgedAt,
      integrityReviewAcknowledgedBy: result.integrityReviewAcknowledgedBy,
    };
    setSelectedAsset((current) => current ? { ...current, ...integrityUpdate } : current);
    setAssets((prev) => prev.map((asset) => (
      asset.id === result.assetId ? { ...asset, ...integrityUpdate } : asset
    )));
  }

  function handleUploadReplacement() {
    if (!selectedAsset) return;
    if (isAdmin) {
      setNetworkView(false);
      setSelectedInstitutionId(selectedAsset.institutionId);
    }
    setUploadOpen(true);
    toast.info("Upload the replacement as a new asset. The original remains preserved.");
  }

  async function handleDownload() {
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
      // CORS or network blocked the blob fetch — fall back to opening the file.
      window.open(storageUrl, "_blank");
    }
  }

  function openDeleteModal(tier: DeleteTier) {
    if (!selectedAsset) return;
    setDeleteAsset(selectedAsset);
    setDeleteAssets([selectedAsset]);
    setDeleteTier(tier);

    if (tier === "blocked") {
      const blocking = selectedAsset.usedIn?.filter(
        (u) => u.submissionStatus === "scheduled" || u.submissionStatus === "in_review",
      ) ?? [];
      setBlockingUsages(blocking);
      setWarningUsages([]);
    } else if (tier === "warning") {
      setBlockingUsages([]);
      const warning = selectedAsset.usedIn?.filter(
        (u) => u.submissionStatus === "draft" || u.submissionStatus === "needs_revision",
      ) ?? [];
      setWarningUsages(warning);
    } else {
      setBlockingUsages([]);
      setWarningUsages([]);
    }

    setDeleteOpen(true);
  }

  function deleteTierForAsset(asset: MediaAsset): DeleteTier {
    const usedIn = asset.usedIn ?? [];
    if (usedIn.some((u) => u.submissionStatus === "scheduled" || u.submissionStatus === "in_review" || u.submissionStatus === "pending")) {
      return "blocked";
    }
    if (usedIn.some((u) => u.submissionStatus === "draft" || u.submissionStatus === "needs_revision")) {
      return "warning";
    }
    return "free";
  }

  function openSingleDeleteModal() {
    if (!selectedAsset || !canDeleteAsset(selectedAsset)) return;
    openDeleteModal(deleteTierForAsset(selectedAsset));
  }

  function openBulkDeleteModal() {
    if (selectedAssets.length === 0) return;
    const deletable = selectedAssets.filter(canDeleteAsset);
    if (deletable.length !== selectedAssets.length) {
      toast.error("One or more selected assets cannot be deleted by your role.");
      return;
    }
    setDeleteAssets(deletable);
    setDeleteAsset(deletable[0] ?? null);
    setBlockingUsages([]);
    setWarningUsages([]);
    setDeleteTier("warning");
    setDeleteOpen(true);
  }

  function handleBack() {
    navigate("/dashboard", { replace: true });
  }

  async function handleConfirmDelete() {
    if (!deleteAsset) return;
    setDeleting(true);
    try {
      const ids = deleteAssets.length > 0 ? deleteAssets.map((asset) => asset.id) : [deleteAsset.id];
      if (ids.length > 1) {
        await bulkDeleteMediaAssets(ids, true);
      } else {
        await deleteMediaAsset(deleteAsset.id, deleteTier === "warning");
      }
      setAssets((prev) => prev.filter((a) => !ids.includes(a.id)));
      ids.forEach((id) => {
        if (checkedIds.has(id)) toggleCheck(id);
      });
      if (selectedAsset && ids.includes(selectedAsset.id)) closePanel();
      setDeleteOpen(false);
      toast.success(
        ids.length > 1
          ? `${ids.length} assets deleted from the media library.`
          : deleteTier === "warning"
            ? "Asset deleted. Broken reference flagged in draft."
            : "Asset deleted. Terminal submission records updated.",
      );
    } catch {
      toast.error("Failed to delete asset. It may be referenced by an active submission or outside your delete scope.");
    } finally {
      setDeleting(false);
    }
  }

  return (
    <div className={`med-page${panelOpen ? " panel-open" : ""}`}>
      <nav className="med-topnav">
        <div className="med-nav-left">
          <button className="med-back-btn" type="button" onClick={handleBack}>
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
            <span>Media Library</span>
          </div>
        </div>
        <div className="med-nav-right">
          <div className="med-nav-chip">{formatRole(user.role)}</div>
          <div className="med-nav-avatar" aria-label={user.email}>
            {user.initials}
          </div>
        </div>
      </nav>

      <div className="med-workspace">
        <FolderSidebar
          folders={folders}
          loading={foldersLoading}
          filterMode={filterMode}
          selectedFolderId={selectedFolderId}
          allCount={assets.length}
          unfiledCount={unfiledCount}
          selectionCount={checkedIds.size}
          disabledReason={isAdmin && networkView ? "Select one institution to organize storage folders." : null}
          onSelectAll={selectAllFolders}
          onSelectUnfiled={selectUnfiled}
          onSelectFolder={selectFolder}
          onOpenCollections={() => navigate("/media-albums")}
          onCreate={openCreateFolder}
          onRename={openRenameFolder}
          onDelete={(f) => void handleDeleteFolder(f)}
          onMoveSelected={(id) => void handleMoveSelected(id)}
        />

        <main className="med-content-canvas">
          <div className="med-header">
            <div>
              <h1 className="med-title">Media Library</h1>
              <p className="med-subtitle">Find, file, and reuse institution media</p>
            </div>
            <div className="med-header-actions">
              {user.role !== "contributor" && (
                <button
                  className="med-btn med-btn-ghost med-btn-sm"
                  onClick={() => navigate("/media-repository/health")}
                  type="button"
                >
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M22 12h-4l-3 9L9 3l-3 9H2" />
                  </svg>
                  Repository Health
                </button>
              )}
              <button
                className="med-btn med-btn-ghost med-btn-sm"
                onClick={() => navigate("/media-albums")}
                type="button"
              >
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <rect x="3" y="3" width="18" height="18" rx="2" />
                  <circle cx="8.5" cy="8.5" r="1.5" />
                  <polyline points="21,15 16,10 5,21" />
                </svg>
                Collections
              </button>
              <button
                className="med-btn med-btn-ghost med-btn-sm"
                onClick={openUploadModal}
                type="button"
              >
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <polyline points="16,16 12,12 8,16" />
                  <line x1="12" y1="12" x2="12" y2="21" />
                  <path d="M20.39 18.39A5 5 0 0 0 18 9h-1.26A8 8 0 1 0 3 16.3" />
                </svg>
                Upload media
              </button>
              <button
                className="med-btn med-btn-primary med-btn-sm"
                onClick={() => isAdmin ? navigate("/admin/resolution?tab=direct-post") : navigate("/submissions/new")}
                type="button"
              >
                {isAdmin ? "Direct Post" : "New Submission"}
              </button>
            </div>
          </div>

          {healthFilter && (
            <div className="med-health-filter" role="status">
              <span className="med-health-filter-label">
                Repository Health: <strong>{healthFilterLabel(healthFilter)}</strong>
              </span>
              <button
                type="button"
                className="med-health-filter-clear"
                onClick={() => {
                  const next = new URLSearchParams(searchParams);
                  next.delete("health");
                  setSearchParams(next, { replace: true });
                }}
              >
                Clear filter &times;
              </button>
            </div>
          )}

          {isAdmin && (
            <LibraryScopeSelector
              institutions={institutions}
              selectedInstitutionId={selectedInstitutionId}
              networkScope={networkView}
              allowNetworkScope
              loading={institutionsLoading}
              onInstitutionChange={changeInstitutionScope}
              onNetworkScopeChange={(enabled) => {
                setNetworkView(enabled);
                if (enabled) selectAllFolders();
                clearSmartSearch();
              }}
            />
          )}

          <FilterBar
            search={search}
            sort={sort}
            viewMode={viewMode}
            activeTags={activeTags}
            tagChips={tagChips}
            searching={mediaSearch.loading}
            searchActive={searchActive}
            onSearchChange={setSearch}
            onSearchSubmit={runSmartSearch}
            onSearchClear={clearSmartSearch}
            onSortChange={setSort}
            onViewModeChange={setViewMode}
            onTagToggle={toggleTag}
            fetchSuggestions={fetchSuggestions}
          />

          {latestReviewBatchId && (
            <div className="med-result-strip med-batch-strip">
              <p className="med-result-count">
                Upload batches are ready for review and suggested Collections.
              </p>
              <div className="med-active-filters">
                <button
                  className="med-btn med-btn-ghost med-btn-sm"
                  type="button"
                  onClick={openBatchReview}
                  disabled={batchReviewLoading}
                >
                  Review batch
                </button>
                <button
                  className="med-btn med-btn-ghost med-btn-sm"
                  type="button"
                  onClick={openAiCollectionBuilder}
                >
                  AI Builder
                </button>
              </div>
            </div>
          )}

          <div className="med-main">
            <div className="med-result-strip">
              {searchActive ? (
                <>
                  <p className="med-result-count">
                    {mediaSearch.loading ? (
                      <>Searching the library…</>
                    ) : (
                      <>
                        <strong>{mediaSearch.hits.length}</strong>{" "}
                        {mediaSearch.hits.length === 1 ? "result" : "results"} for{" "}
                        <span className="med-result-query">“{mediaSearch.activeQuery}”</span>
                        <span className="med-result-hint">
                          {mediaSearch.chronological ? " · newest first" : " · ranked by relevance"}
                        </span>
                      </>
                    )}
                  </p>
                  <button
                    className="med-btn med-btn-ghost med-btn-sm"
                    type="button"
                    onClick={clearSmartSearch}
                  >
                    Clear search
                  </button>
                </>
              ) : (
                <>
                  <p className="med-result-count">
                    <strong>{filteredAssets.length}</strong> of {assets.length} assets
                  </p>
                  {activeTags.size > 0 && (
                    <div className="med-active-filters">
                      {[...activeTags].map((tag) => (
                        <div key={tag} className="med-filter-tag">
                          {tag}
                          <button onClick={() => clearTag(tag)} type="button" aria-label={`Remove ${tag} filter`}>
                            <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                              <line x1="18" y1="6" x2="6" y2="18" />
                              <line x1="6" y1="6" x2="18" y2="18" />
                            </svg>
                          </button>
                        </div>
                      ))}
                    </div>
                  )}
                </>
              )}
            </div>

            {(searchActive ? mediaSearch.loading : loading) ? (
              <SkeletonGrid viewMode={viewMode} />
            ) : (searchActive ? mediaSearch.error : error) ? (
              <ErrorState
                message={searchActive ? mediaSearch.error : error}
                onRetry={searchActive ? () => runSmartSearch() : () => void refresh()}
              />
            ) : gridAssets.length === 0 ? (
              <EmptyState
                hasSearch={searchActive || activeTags.size > 0}
                searchQuery={searchActive ? mediaSearch.activeQuery : null}
                onUpload={openUploadModal}
              />
            ) : (
              <div className={`med-grid${viewMode === "list" ? " list-view" : ""}${checkedIds.size > 0 ? " selecting" : ""}`}>
                {gridAssets.map((asset, idx) => (
                  <AssetCard
                    key={asset.id}
                    asset={asset}
                    selected={selectedAsset?.id === asset.id}
                    checked={checkedIds.has(asset.id)}
                    listView={viewMode === "list"}
                    animationDelay={Math.min(idx * 40, 480)}
                    showInstitutionChip={networkView && isAdmin}
                    matchReasons={searchActive ? reasonsByAssetId.get(asset.id) : undefined}
                    feedback={searchActive ? (searchFeedback.get(asset.id) ?? null) : undefined}
                    onFeedback={searchActive ? (action) => handleSearchFeedback(asset, idx + 1, action) : undefined}
                    onClick={() => { logSearchSelection(asset, idx + 1); handleToggleCheck(asset); }}
                  />
                ))}
              </div>
            )}
          </div>
        </main>

        <AssetDetailPanel
          renderMode="inline"
          asset={selectedAsset}
          open={panelOpen}
          isAdmin={isAdmin}
          selectionMode={selectionMode}
          selectedAssets={selectedAssets}
          onViewAsset={(a) => openAsset(a)}
          onViewSubmission={(submissionId) =>
            navigate(`/submissions/${encodeURIComponent(submissionId)}`, {
              state: { returnTo: "/media-repository" },
            })
          }
          onDeselectAsset={(id) => handleDeselect(id)}
          onNewPost={handleNewPost}
          onClearSelection={clearChecked}
          onClose={closePanel}
          canAddToDraft={user.role === "contributor"}
          onAddToDraft={openAddToDraft}
          onDownload={() => void handleDownload()}
          canDelete={selectedAsset ? canDeleteAsset(selectedAsset) : false}
          onRequestDelete={openSingleDeleteModal}
          canBulkDelete={canBulkDelete}
          onRequestBulkDelete={openBulkDeleteModal}
          history={assetHistory}
          historyLoading={historyLoading}
          onChangeVisibility={(v) => void handleChangeVisibility(v)}
          visibilityBusy={visibilityBusy}
          onVerifyIntegrity={() => void handleVerifyIntegrity()}
          integrityBusy={integrityBusy}
          integrityHistory={integrityHistory}
          integrityHistoryLoading={integrityHistoryLoading}
          canAcknowledgeIntegrity={user.role === "validator" || user.role === "admin"}
          onAcknowledgeIntegrity={() => void handleAcknowledgeIntegrity()}
          integrityReviewBusy={integrityReviewBusy}
          onUploadReplacement={handleUploadReplacement}
        />
      </div>

      {/* Upload Modal (portal) */}
      <UploadModal
        open={uploadOpen}
        institutionName={
          isAdmin
            ? institutions.find((institution) => institution.id === selectedInstitutionId)?.name ?? "selected institution"
            : user.inst
        }
        onClose={() => setUploadOpen(false)}
        onUpload={handleUpload}
        onCreateBatch={createUploadBatch}
        onBatchComplete={(importBatchId) => {
          setLatestUploadBatchId(importBatchId);
          void refresh();
        }}
      />

      {/* Delete Modal (portal) */}
      <DeleteModal
        open={deleteOpen}
        tier={deleteTier}
        asset={deleteAsset}
        blockingUsages={blockingUsages}
        warningUsages={warningUsages}
        deleting={deleting}
        assetCount={deleteAssets.length || (deleteAsset ? 1 : 0)}
        onClose={() => { if (!deleting) setDeleteOpen(false); }}
        onConfirmDelete={() => void handleConfirmDelete()}
      />

      {/* Add to Draft Modal (portal) */}
      <AddToDraftModal
        open={addToDraftOpen}
        assetCount={activeAssetIds().length}
        drafts={drafts}
        loading={draftsLoading}
        busyDraftId={busyDraftId}
        onClose={() => { if (busyDraftId === null) setAddToDraftOpen(false); }}
        onSelectDraft={(id) => void handleSelectDraft(id)}
        onNewPostInstead={() => { setAddToDraftOpen(false); handleNewPost(); }}
      />

      <BatchCurationModal
        open={batchReviewOpen}
        batches={batchGroups}
        batchesLoading={batchListLoading}
        selectedBatchId={selectedReviewBatchId}
        loading={batchReviewLoading}
        saving={batchCurating}
        assets={batchAssets}
        onClose={() => { if (!batchCurating) setBatchReviewOpen(false); }}
        onRefresh={refreshBatchReviewModal}
        onSelectBatch={selectReviewBatch}
        onBackToBatches={() => {
          setSelectedReviewBatchId(null);
          setBatchAssets([]);
        }}
        onConfirmAll={(edits) => void handleConfirmBatchCuration(edits)}
        onDeleteBatch={(batch) => void handleDeleteBatch(batch)}
      />

      <PromptCollectionBuilderContainer
        open={aiBuilderOpen}
        isAdmin={isAdmin}
        institutionId={isAdmin ? selectedInstitutionId : null}
        onClose={() => setAiBuilderOpen(false)}
      />

      {folderModal && (
        <FolderFormModal
          key={`${folderModal.mode}-${folderModal.folder?.id ?? "new"}`}
          open
          mode={folderModal.mode}
          busy={folderSaving}
          initialName={folderModal.folder?.name ?? ""}
          onCancel={() => { if (!folderSaving) setFolderModal(null); }}
          onSubmit={(name) => void submitFolderForm(name)}
        />
      )}

      {confirm && (
        <ConfirmDialog
          title={confirm.title}
          message={confirm.message}
          confirmLabel={confirm.confirmLabel}
          dangerous={confirm.dangerous}
          onCancel={() => setConfirm(null)}
          onConfirm={() => { const run = confirm.onConfirm; setConfirm(null); run(); }}
        />
      )}

    </div>
  );
}

/* ===== Skeleton Loading ===== */
function SkeletonGrid({ viewMode }: { viewMode: ViewMode }) {
  return (
    <div className={`med-loading${viewMode === "list" ? " list-view" : ""}`}>
      {Array.from({ length: 8 }).map((_, i) => (
        <div key={i} className="med-skeleton">
          <div className="med-skeleton-thumb" />
          <div className="med-skeleton-body">
            <div className="med-skeleton-line short" />
            <div className="med-skeleton-line medium" />
            <div className="med-skeleton-line short" style={{ marginBottom: 0 }} />
          </div>
        </div>
      ))}
    </div>
  );
}

/* ===== Empty State ===== */
function EmptyState({
  hasSearch,
  searchQuery,
  onUpload,
}: {
  hasSearch: boolean;
  searchQuery?: string | null;
  onUpload: () => void;
}) {
  return (
    <div className="med-empty">
      <div className="med-empty-icon">
        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
          <rect x="3" y="3" width="18" height="18" rx="2" />
          <circle cx="8.5" cy="8.5" r="1.5" />
          <polyline points="21,15 16,10 5,21" />
        </svg>
      </div>
      {searchQuery ? (
        <>
          <div className="med-empty-title">No results for “{searchQuery}”</div>
          <p className="med-empty-sub">Try fewer or different words, or describe the photo's subject, people, or event.</p>
        </>
      ) : hasSearch ? (
        <>
          <div className="med-empty-title">No assets match your filters</div>
          <p className="med-empty-sub">Try adjusting your search term or removing an active tag filter.</p>
        </>
      ) : (
        <>
          <div className="med-empty-title">No media yet</div>
          <p className="med-empty-sub">Upload the first file for this library scope.</p>
          <button className="med-btn med-btn-primary" onClick={onUpload} type="button" style={{ marginTop: 20 }}>
            Upload media
          </button>
        </>
      )}
    </div>
  );
}

/* ===== Error State ===== */
function ErrorState({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="med-empty">
      <div className="med-empty-icon" style={{ background: "#FEE2E2" }}>
        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#EF4444" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
          <circle cx="12" cy="12" r="10" />
          <line x1="12" y1="8" x2="12" y2="12" />
          <line x1="12" y1="16" x2="12.01" y2="16" />
        </svg>
      </div>
      <div className="med-empty-title">Failed to load assets</div>
      <p className="med-empty-sub">{message}</p>
      <button className="med-btn med-btn-ghost" onClick={onRetry} type="button" style={{ marginTop: 20 }}>
        Try again
      </button>
    </div>
  );
}

function batchGroupsFromLoadedAssets(assets: MediaAsset[]): MediaImportBatch[] {
  const groups = new Map<string, MediaAsset[]>();
  for (const asset of assets) {
    if (!asset.importBatchId) continue;
    groups.set(asset.importBatchId, [...(groups.get(asset.importBatchId) ?? []), asset]);
  }

  return Array.from(groups.entries())
    .map(([id, groupAssets]) => {
      const sorted = [...groupAssets].sort(
        (a, b) => new Date(b.uploadedAt).getTime() - new Date(a.uploadedAt).getTime(),
      );
      const registered = groupAssets.length;
      const curated = groupAssets.filter((asset) => Boolean(asset.curatedAt)).length;
      return {
        id,
        institutionId: sorted[0]?.institutionId ?? "",
        uploadedBy: sorted[0]?.uploaderId ?? "",
        assetCount: registered,
        registeredAssetCount: registered,
        readyAssetCount: registered,
        curatedAssetCount: curated,
        createdAt: sorted[0]?.uploadedAt ?? new Date().toISOString(),
      };
    })
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
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

/** Human label for a UC-4.12 Phase 7C Repository Health drill-down filter key. */
function healthFilterLabel(key: string): string {
  const labels: Record<string, string> = {
    integrity_failures: "Integrity failures",
    review_open: "Open integrity reviews",
    processing_failed: "Processing failures",
    uncurated: "Uncurated assets",
    unorganized: "Unorganized assets",
    internal_only: "Internal-only assets",
    duplicates: "Duplicate candidates",
  };
  return labels[key] ?? key;
}

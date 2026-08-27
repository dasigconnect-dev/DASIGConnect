import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import type { User } from "../../types/auth.types";
import type { MediaAsset, MediaUsage } from "../../api/mediaApi";
import {
  bulkDeleteMediaAssets,
  createMediaAlbum,
  deleteMediaAlbum,
  deleteMediaAsset,
  ensureMediaAlbumPath,
  getMediaAsset,
  getMediaAssetUploadUrl,
  listMediaAlbums,
  moveMediaAlbum,
  registerMediaAsset,
  renameMediaAlbum,
  semanticSearchMediaAssets,
  updateMediaAssetAlbum,
  type MediaAlbum,
} from "../../api/mediaApi";
import { listInstitutions, getInstitutionLogoUrl, type InstitutionResponse } from "../../api/authApi";
import BrandedSelect from "../../components/ui/BrandedSelect";
import {
  attachAsset,
  listSubmissions,
  type SubmissionSummary,
} from "../../api/submissionApi";
import { useToast } from "../../context/ToastContext";
import { usePersistentSelection } from "../../hooks/usePersistentSelection";
import { useMediaAssets } from "./hooks/useMediaAssets";
import type { SortOption, ViewMode, DeleteTier } from "./types";
import AssetCard from "./components/AssetCard";
import AlbumCard from "./components/AlbumCard";
import { buildAlbumOptions, albumSubtreeIds } from "./albumTree";
import MediaToolbar from "./components/MediaToolbar";
import AssetDetailPanel from "./components/AssetDetailPanel";
import UploadModal, { type UploadMetadata } from "./components/UploadModal";
import DeleteModal from "./components/DeleteModal";
import AddToDraftModal from "./components/AddToDraftModal";
import "../../styles/media-repository.css";

interface MediaRepositoryScreenProps {
  user: User;
}

const MAX_UPLOAD_MB = 50;

function isConflict(error: unknown) {
  if (typeof error !== "object" || error === null) return false;
  return (error as { response?: { status?: number } }).response?.status === 409;
}

function isCanceledRequest(error: unknown) {
  if (typeof error !== "object" || error === null) return false;
  return (error as { name?: string }).name === "CanceledError";
}

function safeFileName(fileName: string) {
  return fileName.replace(/[^a-zA-Z0-9._-]/g, "-");
}

function getErrorText(error: unknown, fallback: string) {
  const response = (error as { response?: { data?: { error?: unknown; message?: unknown } } })?.response;
  const detail = response?.data?.error ?? response?.data?.message;
  if (typeof detail === "string" && detail.trim()) return detail;
  if (error instanceof Error && error.message) return error.message;
  return fallback;
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
  const isAdmin = user.role === "administrator" || user.role === "super_administrator";

  // Admins browse network-wide by default; the per-institution filter narrows it.
  const networkView = isAdmin;
  const [institutions, setInstitutions] = useState<InstitutionResponse[]>([]);
  const [selectedInstitutionId, setSelectedInstitutionId] = useState<string | null>(null);

  const [searchParams, setSearchParams] = useSearchParams();
  const currentAlbumId = searchParams.get("album");

  const [search, setSearch] = useState("");
  const [sort, setSort] = useState<SortOption>("newest");
  const [viewMode, setViewMode] = useState<ViewMode>("grid");
  const [activeTags, setActiveTags] = useState<Set<string>>(new Set());

  // Meaning-based (Voyage embedding) search — explicit: toggle on, then press Enter.
  const [semantic, setSemantic] = useState(false);
  const [semanticResults, setSemanticResults] = useState<MediaAsset[] | null>(null);
  const [semanticQuery, setSemanticQuery] = useState("");
  const [semanticBusy, setSemanticBusy] = useState(false);

  // Admin with no institution filter: the repository shows every institution's
  // top-level albums together, each card badged with its institution.
  const networkAlbumMode = isAdmin && !selectedInstitutionId;
  // At that network root (no folder open, no search/tag filter) only folder cards
  // are shown, so the network-wide asset fetch is skipped.
  const skipAssetFetch = networkAlbumMode && !currentAlbumId && !search.trim() && activeTags.size === 0;

  // Folder scoping is dropped while searching so matches are never hidden by the current folder.
  const listAlbumId = search.trim() ? null : currentAlbumId;
  const { assets, setAssets, loading, error, refresh } = useMediaAssets(
    networkView,
    selectedInstitutionId,
    listAlbumId,
    !skipAssetFetch,
  );

  const [selectedAsset, setSelectedAsset] = useState<MediaAsset | null>(null);
  const [panelOpen, setPanelOpen] = useState(false);

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

  // Deep link: ?asset=<id> opens that asset's detail panel (e.g. from a
  // read-only submission's "View in library" link). Consume the param after.
  useEffect(() => {
    const assetId = searchParams.get("asset");
    if (!assetId) return;
    let active = true;
    getMediaAsset(assetId)
      .then((res) => {
        if (!active) return;
        setSelectedAsset(res.data);
        setPanelOpen(true);
      })
      .catch(() => {
        if (active) toast.error("That media asset could not be opened.");
      })
      .finally(() => {
        const next = new URLSearchParams(searchParams);
        next.delete("asset");
        setSearchParams(next, { replace: true });
      });
    return () => {
      active = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const [addToDraftOpen, setAddToDraftOpen] = useState(false);
  const [drafts, setDrafts] = useState<SubmissionSummary[]>([]);
  const [draftsLoading, setDraftsLoading] = useState(false);
  const [busyDraftId, setBusyDraftId] = useState<string | null>(null);

  const [uploadOpen, setUploadOpen] = useState(false);
  const [albums, setAlbums] = useState<MediaAlbum[]>([]);
  const [albumModal, setAlbumModal] = useState<
    | { mode: "create"; album: null }
    | { mode: "rename"; album: MediaAlbum }
    | null
  >(null);
  const [albumName, setAlbumName] = useState("");
  const [albumModalInstitutionId, setAlbumModalInstitutionId] = useState<string>("");
  const [savingAlbum, setSavingAlbum] = useState(false);
  const [moveAlbumTarget, setMoveAlbumTarget] = useState<MediaAlbum | null>(null);
  const [newMenuOpen, setNewMenuOpen] = useState(false);
  const [folderUploadBusy, setFolderUploadBusy] = useState(false);
  const folderInputRef = useRef<HTMLInputElement>(null);

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

  useEffect(() => {
    if (!isAdmin) return;
    const controller = new AbortController();
    let active = true;

    listInstitutions(controller.signal)
      .then((res) => {
        if (active) setInstitutions(res.data);
      })
      .catch((err: unknown) => {
        if (!active || isCanceledRequest(err)) return;
        toast.error("Could not load institution filters.");
      });

    return () => {
      active = false;
      controller.abort();
    };
  }, [isAdmin, toast]);

  // Which institution's albums to load. null + admin ⇒ every institution's albums.
  const albumScopeInstitutionId = isAdmin ? selectedInstitutionId : (user.institutionId ?? null);

  const reloadAlbums = useCallback(() => {
    if (!isAdmin && !albumScopeInstitutionId) {
      setAlbums([]);
      return Promise.resolve();
    }
    return listMediaAlbums(albumScopeInstitutionId ?? undefined)
      .then((res) => setAlbums(res.data ?? []))
      .catch(() => toast.error("Could not load media albums."));
  }, [isAdmin, albumScopeInstitutionId, toast]);

  useEffect(() => {
    let active = true;
    // microtask defer so the fetch/clear never runs synchronously in the effect body
    queueMicrotask(() => {
      if (active) void reloadAlbums();
    });
    return () => {
      active = false;
    };
  }, [reloadAlbums]);

  const currentAlbum = useMemo(
    () => albums.find((a) => a.id === currentAlbumId) ?? null,
    [albums, currentAlbumId],
  );

  // For create/upload/move: the institution comes from the folder currently open
  // (each album carries its own institutionId — could be the shared default),
  // otherwise the admin's selected institution or the user's own.
  const targetInstitutionId =
    currentAlbum?.institutionId ??
    (isAdmin ? selectedInstitutionId : user.institutionId) ??
    null;

  // Sub-folders directly under the folder being viewed (root = parentAlbumId null).
  const childAlbums = useMemo(
    () =>
      albums
        .filter((a) => (a.parentAlbumId ?? null) === (currentAlbumId ?? null))
        .sort((a, b) => a.name.localeCompare(b.name)),
    [albums, currentAlbumId],
  );

  // Root → current folder, for the breadcrumb.
  const breadcrumbTrail = useMemo(() => {
    const trail: MediaAlbum[] = [];
    let node = currentAlbum;
    const guard = new Set<string>();
    while (node && !guard.has(node.id)) {
      guard.add(node.id);
      trail.unshift(node);
      node = albums.find((a) => a.id === node?.parentAlbumId) ?? null;
    }
    return trail;
  }, [albums, currentAlbum]);

  function navigateToAlbum(albumId: string | null) {
    const next = new URLSearchParams(searchParams);
    if (albumId) next.set("album", albumId);
    else next.delete("album");
    next.delete("asset");
    setSearchParams(next);
    closePanel();
    clearSelection();
    setSemanticResults(null);
  }

  async function runSemanticSearch() {
    const q = search.trim();
    if (q.length < 2) {
      setSemanticResults(null);
      return;
    }
    setSemanticBusy(true);
    try {
      const results = await semanticSearchMediaAssets(q, selectedInstitutionId);
      setSemanticResults(results);
      setSemanticQuery(q);
    } catch (err: unknown) {
      toast.error(getErrorText(err, "Semantic search failed. Try again."));
    } finally {
      setSemanticBusy(false);
    }
  }

  function openInstitution(institutionId: string) {
    setSelectedInstitutionId(institutionId);
    navigateToAlbum(null);
  }

  function goToAllInstitutions() {
    setSelectedInstitutionId(null);
    navigateToAlbum(null);
  }

  // Opening a folder card from the "All institutions" view scopes the
  // institution filter to that folder's institution, so the dropdown and
  // breadcrumb stay in sync.
  function openFolder(album: MediaAlbum) {
    if (isAdmin && selectedInstitutionId !== album.institutionId) {
      setSelectedInstitutionId(album.institutionId);
    }
    navigateToAlbum(album.id);
  }

  const selectedInstitution = institutions.find((i) => i.id === selectedInstitutionId) ?? null;

  const institutionById = useMemo(() => {
    const map = new Map<string, InstitutionResponse>();
    for (const inst of institutions) map.set(inst.id, inst);
    return map;
  }, [institutions]);

  // Show institution badges once the grid mixes albums from more than one institution
  // (admin "All institutions", or a contributor who also sees the shared default library).
  const multiInstitution = useMemo(
    () => new Set(albums.map((a) => a.institutionId)).size > 1,
    [albums],
  );

  function albumInstitutionProps(album: MediaAlbum) {
    if (!multiInstitution) return {};
    const inst = institutionById.get(album.institutionId); // admin-only, for the faded logo
    return {
      institutionCode: album.institutionCode,
      institutionLogoUrl: inst?.hasLogo ? getInstitutionLogoUrl(inst.id, inst.logoUpdatedAt) : null,
    };
  }

  // Rename/move/delete: admins anywhere; everyone else only their own institution's folders.
  function canManageAlbum(album: MediaAlbum) {
    return isAdmin || album.institutionId === user.institutionId;
  }

  const currentAlbumInstitution = currentAlbum ? institutionById.get(currentAlbum.institutionId) ?? null : null;
  // Institution shown as the 2nd breadcrumb crumb for admins.
  const crumbInstitution = selectedInstitution ?? (networkAlbumMode ? currentAlbumInstitution : null);

  const filteredAssets = useMemo(() => {
    const term = search.trim().toLowerCase();
    let result = assets.filter((a) => {
      if (activeTags.size > 0) {
        const assetTagLabels = new Set((a.aiTags ?? []).map((t) => t.label.toLowerCase()));
        const selectedTags = [...activeTags].map((tag) => tag.toLowerCase());
        if (!selectedTags.some((tag) => assetTagLabels.has(tag))) return false;
      }
      if (!term) return true;
      return [a.title, a.fileName, a.uploaderName, a.institutionName, ...(a.aiTags ?? []).map((t) => t.label)]
        .filter(Boolean)
        .some((val) => val!.toLowerCase().includes(term));
    });

    result = [...result].sort((a, b) => {
      if (sort === "newest") return new Date(b.uploadedAt).getTime() - new Date(a.uploadedAt).getTime();
      if (sort === "oldest") return new Date(a.uploadedAt).getTime() - new Date(b.uploadedAt).getTime();
      if (sort === "name") return a.title.localeCompare(b.title);
      if (sort === "size") return b.fileSizeBytes - a.fileSizeBytes;
      return 0;
    });

    return result;
  }, [assets, search, sort, activeTags]);

  // At the library root (no folder, no search) every asset lives in some folder,
  // so the root shows folders only — loose asset tiles would just be noise.
  const atRootNoSearch = !currentAlbumId && !search.trim() && activeTags.size === 0;
  const visibleAssets = atRootNoSearch ? [] : filteredAssets;

  // Folder-name and tag matches for the current search term (both search modes).
  const searchTerm = search.trim().toLowerCase();
  const searchActive = searchTerm.length >= 2;
  const matchingAlbums = useMemo(
    () =>
      searchActive
        ? albums
            .filter((a) => a.name.toLowerCase().includes(searchTerm))
            .sort((a, b) => a.name.localeCompare(b.name))
        : [],
    [albums, searchTerm, searchActive],
  );
  const matchingTagChips = useMemo(
    () => (searchActive ? tagChips.filter((c) => c.label.toLowerCase().includes(searchTerm)) : []),
    [tagChips, searchTerm, searchActive],
  );

  const selectedAssets = useMemo(
    () => assets.filter((a) => checkedIds.has(a.id)),
    [assets, checkedIds],
  );
  const selectionMode = checkedIds.size > 0;
  const canBulkDelete = selectedAssets.length > 0 && selectedAssets.every(canDeleteAsset);

  function openAsset(asset: MediaAsset) {
    setSelectedAsset(asset);
    setPanelOpen(true);
    getMediaAsset(asset.id)
      .then((res) => setSelectedAsset(res.data))
      .catch(() => { /* panel stays with summary data on fetch error */ });
  }

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
      const nextAsset = assets.find((a) => remainingIds.has(a.id));
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
    if (user.role === "contributor") {
      return Boolean(asset.uploaderName && asset.uploaderName.toLowerCase() === user.email.toLowerCase());
    }
    return false;
  }

  function handleNewPost() {
    const ids = activeAssetIds();
    if (ids.length === 0) return;
    navigate(`/submissions/new?assetIds=${encodeURIComponent(ids.join(","))}`);
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

  async function handleCreateAlbum(
    name: string,
    parentAlbumId: string | null = currentAlbumId,
    institutionId: string | null = targetInstitutionId,
  ) {
    if (!institutionId) {
      const message = "Select an institution before creating a folder.";
      toast.error(message);
      throw new Error(message);
    }
    const { data } = await createMediaAlbum(name, institutionId, parentAlbumId);
    setAlbums((prev) => [...prev.filter((album) => album.id !== data.id), data]
      .sort((a, b) => a.name.localeCompare(b.name)));
    return data;
  }

  function openCreateAlbumModal() {
    setAlbumName("");
    setAlbumModalInstitutionId(targetInstitutionId ?? "");
    setAlbumModal({ mode: "create", album: null });
  }

  function openRenameAlbumModal(album: MediaAlbum) {
    setAlbumName(album.name);
    setAlbumModal({ mode: "rename", album });
  }

  function closeAlbumModal() {
    if (savingAlbum) return;
    setAlbumModal(null);
    setAlbumName("");
  }

  async function handleSaveAlbum() {
    const name = albumName.trim();
    if (!albumModal || !name) return;
    if (albumModal.mode === "rename" && name === albumModal.album.name) {
      closeAlbumModal();
      return;
    }

    setSavingAlbum(true);
    try {
      if (albumModal.mode === "create") {
        await handleCreateAlbum(name, currentAlbumId, albumModalInstitutionId || targetInstitutionId);
        toast.success("Folder created.");
      } else {
        const { data } = await renameMediaAlbum(albumModal.album.id, name, targetInstitutionId);
        setAlbums((prev) => prev.map((item) => item.id === data.id ? { ...item, ...data } : item)
          .sort((a, b) => a.name.localeCompare(b.name)));
        setAssets((prev) => prev.map((asset) =>
          asset.albumId === data.id ? { ...asset, albumName: data.name } : asset
        ));
        setSelectedAsset((prev) =>
          prev?.albumId === data.id ? { ...prev, albumName: data.name } : prev
        );
        toast.success("Folder renamed.");
      }
      closeAlbumModal();
    } catch (err: unknown) {
      const message = err instanceof Error
        ? err.message
        : albumModal.mode === "create"
          ? "Could not create album."
          : "Could not rename album.";
      toast.error(message);
    } finally {
      setSavingAlbum(false);
    }
  }

  async function handleRenameAlbum(album: MediaAlbum) {
    openRenameAlbumModal(album);
  }

  async function handleMoveAlbum(
    albumId: string,
    newParentId: string | null,
    institutionId: string | null = null,
  ) {
    try {
      const { data } = await moveMediaAlbum(albumId, newParentId, institutionId);
      setAlbums((prev) => prev.map((item) => item.id === data.id ? { ...item, ...data } : item)
        .sort((a, b) => a.name.localeCompare(b.name)));
      setMoveAlbumTarget(null);
      toast.success("Folder moved.");
      void reloadAlbums();
      void refresh();
    } catch (err: unknown) {
      toast.error(getErrorText(err, "Could not move that folder."));
    }
  }

  async function handleDeleteAlbum(album: MediaAlbum) {
    try {
      await deleteMediaAlbum(album.id);
      setAlbums((prev) => prev.filter((item) => item.id !== album.id));
      toast.success("Folder deleted.");
    } catch (err: unknown) {
      toast.error(getErrorText(err, "Could not delete that folder."));
    }
  }

  async function handleUpdateAssetAlbum(assetId: string, albumId: string | null) {
    if (!albumId) return;
    try {
      const { data } = await updateMediaAssetAlbum(assetId, albumId);
      // The asset leaves the folder currently being viewed, so drop it from the grid.
      setAssets((prev) =>
        currentAlbumId && data.albumId !== currentAlbumId
          ? prev.filter((asset) => asset.id !== data.id)
          : prev.map((asset) => (asset.id === data.id ? { ...asset, ...data } : asset)),
      );
      setSelectedAsset(data);
      toast.success("Moved to folder.");
      void reloadAlbums();
    } catch {
      toast.error("Could not update the folder assignment.");
    }
  }

  async function handleUpload(
    file: File,
    metadata: UploadMetadata,
    onProgress?: (pct: number) => void,
    opts?: { silent?: boolean },
  ) {
    if (!targetInstitutionId) {
      const message = "Select an institution before uploading to the media library.";
      toast.error(message);
      throw new Error(message);
    }
    if (file.size > MAX_UPLOAD_MB * 1024 * 1024) {
      const message = `${file.name} is ${(file.size / (1024 * 1024)).toFixed(1)} MB — over the ${MAX_UPLOAD_MB} MB limit.`;
      toast.error(message);
      throw new Error(message);
    }

    try {
      onProgress?.(0);
      const { data: urlData } = await getMediaAssetUploadUrl({
        fileName: safeFileName(file.name),
        fileType: fileTypeFromFile(file),
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
        institutionId: targetInstitutionId,
        albumId: metadata.albumId,
        albumName: metadata.albumName,
        autoMatchAlbum: metadata.autoMatchAlbum,
        tags: metadata.tags,
      });
      onProgress?.(100);

      if (!opts?.silent) {
        toast.success("Asset uploaded! AI classification in progress…");
        void refresh();
        void reloadAlbums();
      }
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Upload failed.";
      if (!opts?.silent) toast.error(message);
      throw err;
    }
  }

  // "Upload folder": mirror the picked directory tree into nested albums under
  // the current folder, then upload each file into its matching album. Tags are
  // auto-derived from the folder names (editable per asset afterwards).
  async function handleUploadFolder(fileList: FileList | null) {
    const files = Array.from(fileList ?? []).filter((file) => {
      const ext = file.name.split(".").pop()?.toLowerCase() ?? "";
      return ["jpg", "jpeg", "png", "webp", "gif", "mp4", "mov", "webm"].includes(ext);
    });
    if (files.length === 0) {
      toast.error("That folder has no supported image or video files.");
      return;
    }
    if (!targetInstitutionId) {
      toast.error("Select an institution before uploading.");
      return;
    }

    setFolderUploadBusy(true);
    const basePath = breadcrumbTrail.map((a) => a.name);
    const leafCache = new Map<string, { id: string; name: string }>();
    let done = 0;
    let failed = 0;

    try {
      for (const file of files) {
        const rel = (file as File & { webkitRelativePath?: string }).webkitRelativePath || file.name;
        const dirSegments = rel.split("/").slice(0, -1).filter(Boolean);
        const key = dirSegments.join("/") || "__root__";

        let leaf = leafCache.get(key);
        if (!leaf) {
          const segments = [...basePath, ...dirSegments];
          if (segments.length === 0) {
            failed += 1;
            continue;
          }
          const { data } = await ensureMediaAlbumPath(targetInstitutionId, segments);
          leaf = { id: data.id, name: data.name };
          leafCache.set(key, leaf);
        }

        const folderTag = dirSegments[dirSegments.length - 1] || leaf.name;
        try {
          await handleUpload(
            file,
            { albumId: leaf.id, albumName: leaf.name, autoMatchAlbum: false, tags: [folderTag] },
            undefined,
            { silent: true },
          );
          done += 1;
        } catch {
          failed += 1;
        }
      }
      toast.success(
        `Uploaded ${done} file${done === 1 ? "" : "s"} into folders${failed > 0 ? ` · ${failed} failed` : ""}.`,
      );
      await reloadAlbums();
      void refresh();
    } catch (err: unknown) {
      toast.error(getErrorText(err, "Folder upload could not be completed."));
    } finally {
      setFolderUploadBusy(false);
    }
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
      {/* Page Header */}
      <div className="med-header">
        <div>
          <h1 className="med-title">Media Repository</h1>
          <p className="med-subtitle">Institution assets · AI-classified</p>
        </div>
        <div className="med-header-actions">
          <div
            className="med-new-menu-wrap"
            onBlur={(e) => {
              if (!e.currentTarget.contains(e.relatedTarget as Node)) setNewMenuOpen(false);
            }}
          >
            <button
              className="med-btn med-btn-primary med-btn-sm"
              type="button"
              aria-haspopup="menu"
              aria-expanded={newMenuOpen}
              disabled={folderUploadBusy}
              onClick={() => setNewMenuOpen((v) => !v)}
            >
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round">
                <line x1="12" y1="5" x2="12" y2="19" />
                <line x1="5" y1="12" x2="19" y2="12" />
              </svg>
              {folderUploadBusy ? "Uploading folder…" : "New"}
            </button>
            {newMenuOpen && (
              <div className="med-new-menu" role="menu">
                <button
                  type="button"
                  role="menuitem"
                  onClick={() => { setNewMenuOpen(false); openCreateAlbumModal(); }}
                >
                  <FolderPlusIcon /> New folder
                </button>
                <button
                  type="button"
                  role="menuitem"
                  onClick={() => { setNewMenuOpen(false); setUploadOpen(true); }}
                >
                  <UploadIcon /> Upload files
                </button>
                <button
                  type="button"
                  role="menuitem"
                  disabled={!targetInstitutionId}
                  title={targetInstitutionId ? undefined : "Open an institution or folder first"}
                  onClick={() => { setNewMenuOpen(false); folderInputRef.current?.click(); }}
                >
                  <FolderUploadIcon /> Upload folder
                </button>
              </div>
            )}
          </div>
          <input
            ref={folderInputRef}
            type="file"
            multiple
            // @ts-expect-error non-standard directory-picker attributes
            webkitdirectory=""
            directory=""
            style={{ display: "none" }}
            onChange={(e) => {
              void handleUploadFolder(e.target.files);
              e.target.value = "";
            }}
          />
        </div>
      </div>

      {/* Network View bar */}
      {/* {isAdmin && (
        <div className={`med-network-bar${networkView ? " visible" : ""}`}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="12" r="10" />
            <line x1="2" y1="12" x2="22" y2="12" />
            <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z" />
          </svg>
          <span>
            <strong>Network View active</strong> — Showing assets across all DASIG member institutions. This session is being logged in the access audit log (BR-MED-01).
          </span>
        </div>
      )} */}

      {/* Toolbar: institution · search (+ semantic) · sort · view · tags */}
      <MediaToolbar
        isAdmin={isAdmin}
        institutions={institutions}
        selectedInstitutionId={selectedInstitutionId}
        onInstitutionChange={(id) => (id ? openInstitution(id) : goToAllInstitutions())}
        search={search}
        onSearchChange={(v) => {
          setSearch(v);
          if (!v.trim()) setSemanticResults(null);
        }}
        semantic={semantic}
        onSemanticToggle={() => {
          setSemantic((on) => {
            if (on) setSemanticResults(null);
            return !on;
          });
        }}
        onSemanticSearch={() => void runSemanticSearch()}
        semanticBusy={semanticBusy}
        sort={sort}
        onSortChange={setSort}
        viewMode={viewMode}
        onViewModeChange={setViewMode}
        activeTags={activeTags}
        tagChips={tagChips}
        onTagToggle={toggleTag}
      />

      {/* Breadcrumb */}
      <nav className="med-breadcrumb" aria-label="Folder path">
        <button
          type="button"
          className={`med-crumb${(isAdmin ? !selectedInstitutionId : !currentAlbumId) ? " current" : ""}`}
          onClick={isAdmin ? goToAllInstitutions : () => navigateToAlbum(null)}
        >
          {isAdmin ? "All institutions" : "Library"}
        </button>
        {isAdmin && crumbInstitution && (
          <span className="med-crumb-part">
            <span className="med-crumb-sep">/</span>
            <button
              type="button"
              className={`med-crumb${!currentAlbumId ? " current" : ""}`}
              onClick={() =>
                selectedInstitution ? navigateToAlbum(null) : openInstitution(crumbInstitution.id)
              }
            >
              {crumbInstitution.name}
            </button>
          </span>
        )}
        {breadcrumbTrail.map((album, idx) => (
          <span key={album.id} className="med-crumb-part">
            <span className="med-crumb-sep">/</span>
            <button
              type="button"
              className={`med-crumb${idx === breadcrumbTrail.length - 1 ? " current" : ""}`}
              onClick={() => navigateToAlbum(album.id)}
            >
              {album.name}
            </button>
          </span>
        ))}
      </nav>

      {/* Semantic search banner */}
      {semanticResults !== null && (
        <div className="med-semantic-banner">
          <span>
            <strong>{semanticResults.length}</strong> meaning-based {semanticResults.length === 1 ? "match" : "matches"} for
            {" "}<em>“{semanticQuery}”</em>
          </span>
          <button type="button" onClick={() => setSemanticResults(null)}>Clear</button>
        </div>
      )}

      {/* Matching tags */}
      {matchingTagChips.length > 0 && (
        <div className="med-filter-row2 med-match-tags">
          <span className="med-filter-label">Matching tags</span>
          {matchingTagChips.map((chip) => (
            <button
              key={chip.label}
              className={`med-chip${activeTags.has(chip.label) ? " active" : ""}`}
              onClick={() => toggleTag(chip.label)}
              type="button"
            >
              {chip.label}
              <span className="med-chip-count">{chip.count}</span>
            </button>
          ))}
        </div>
      )}

      {/* Result strip */}
      <div className="med-result-strip">
        <p className="med-result-count">
          {(() => {
            const folderN = searchActive ? matchingAlbums.length : childAlbums.length;
            const assetN = (semanticResults ?? visibleAssets).length;
            const label = semanticResults !== null || searchActive ? "results" : "items";
            const parts: string[] = [];
            if (folderN > 0) parts.push(`${folderN} folder${folderN === 1 ? "" : "s"}`);
            parts.push(`${assetN} ${label}`);
            return parts.join(" · ");
          })()}
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
      </div>

      {/* Folders + Media Grid / States */}
      {(() => {
        const folderCards = searchActive
          ? matchingAlbums
          : activeTags.size === 0
            ? childAlbums
            : [];
        const showFolders = folderCards.length > 0;
        const gridAssets = semanticResults ?? visibleAssets;

        if (loading || (semanticBusy && semanticResults === null)) return <SkeletonGrid viewMode={viewMode} />;
        if (error) return <ErrorState message={error} onRetry={() => void refresh()} />;
        if (gridAssets.length === 0 && !showFolders) {
          return (
            <EmptyState
              hasSearch={searchActive || activeTags.size > 0 || semanticResults !== null}
              inFolder={Boolean(currentAlbumId)}
              onUpload={() => setUploadOpen(true)}
            />
          );
        }
        const listView = viewMode === "list";
        const gridClass = `med-grid${listView ? " list-view" : ""}${checkedIds.size > 0 ? " selecting" : ""}`;
        return (
          <div className="med-sections">
            {showFolders && (
              <section className="med-section">
                <div className={gridClass}>
                  {folderCards.map((album, idx) => (
                    <AlbumCard
                      key={album.id}
                      album={album}
                      animationDelay={Math.min(idx * 30, 240)}
                      canManage={canManageAlbum(album)}
                      {...albumInstitutionProps(album)}
                      onOpen={() => openFolder(album)}
                      onRename={() => void handleRenameAlbum(album)}
                      onMove={() => setMoveAlbumTarget(album)}
                      onDelete={() => void handleDeleteAlbum(album)}
                    />
                  ))}
                </div>
              </section>
            )}
            {gridAssets.length > 0 && (
              <section className="med-section">
                <div className={gridClass}>
                  {gridAssets.map((asset, idx) => (
                    <AssetCard
                      key={asset.id}
                      asset={asset}
                      selected={selectedAsset?.id === asset.id}
                      checked={checkedIds.has(asset.id)}
                      listView={listView}
                      animationDelay={Math.min(idx * 40, 480)}
                      showInstitutionChip={networkView && isAdmin}
                      onClick={() => handleToggleCheck(asset)}
                    />
                  ))}
                </div>
              </section>
            )}
          </div>
        );
      })()}

      {/* Detail Panel (portal) */}
      <AssetDetailPanel
        asset={selectedAsset}
        open={panelOpen}
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
        albums={albums}
        onUpdateAlbum={(assetId, albumId) => void handleUpdateAssetAlbum(assetId, albumId)}
        onRenameAlbum={(album) => void handleRenameAlbum(album)}
      />

      {/* Upload Modal (portal) */}
      <UploadModal
        open={uploadOpen}
        institutionName={user.inst}
        albums={albums}
        currentAlbum={currentAlbum}
        institutions={isAdmin ? institutions : []}
        defaultInstitutionId={targetInstitutionId}
        onClose={() => setUploadOpen(false)}
        onCreateAlbum={(name, institutionId, parentAlbumId) =>
          handleCreateAlbum(name, parentAlbumId, institutionId)
        }
        onUpload={handleUpload}
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

      <AlbumNameModal
        open={albumModal !== null}
        mode={albumModal?.mode ?? "create"}
        parentName={albumModal?.mode === "create" ? currentAlbum?.name ?? null : null}
        value={albumName}
        saving={savingAlbum}
        institutions={albumModal?.mode === "create" && !targetInstitutionId && isAdmin ? institutions : []}
        institutionId={albumModalInstitutionId}
        onInstitutionChange={setAlbumModalInstitutionId}
        onChange={setAlbumName}
        onClose={closeAlbumModal}
        onSubmit={() => void handleSaveAlbum()}
      />

      {moveAlbumTarget && (
        <MoveAlbumModal
          key={moveAlbumTarget.id}
          album={moveAlbumTarget}
          albums={albums}
          onClose={() => setMoveAlbumTarget(null)}
          onMove={(parentId, institutionId) =>
            void handleMoveAlbum(moveAlbumTarget.id, parentId, institutionId)
          }
        />
      )}

    </div>
  );
}

function AlbumNameModal({
  open,
  mode,
  parentName,
  value,
  saving,
  institutions = [],
  institutionId = "",
  onInstitutionChange,
  onChange,
  onClose,
  onSubmit,
}: {
  open: boolean;
  mode: "create" | "rename";
  parentName?: string | null;
  value: string;
  saving: boolean;
  institutions?: InstitutionResponse[];
  institutionId?: string;
  onInstitutionChange?: (id: string) => void;
  onChange: (value: string) => void;
  onClose: () => void;
  onSubmit: () => void;
}) {
  if (!open) return null;

  const showInstitutionPicker = mode === "create" && institutions.length > 0;
  const title = mode === "create" ? "New folder" : "Rename folder";
  const description = mode === "create"
    ? parentName
      ? `New folder inside "${parentName}".`
      : "New folder at the library root."
    : "Update this folder name across the media library.";
  const actionLabel = mode === "create" ? "Create folder" : "Save changes";
  const disabled = saving || value.trim().length === 0 || (showInstitutionPicker && !institutionId);

  return (
    <div className="med-modal-overlay" role="presentation">
      <form
        className="med-modal-card med-album-modal"
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onSubmit={(event) => {
          event.preventDefault();
          if (!disabled) onSubmit();
        }}
      >
        <div className="med-modal-header">
          <div>
            <span className="med-modal-title">{title}</span>
            <p className="med-album-modal-sub">{description}</p>
          </div>
          <button
            className="med-modal-close"
            type="button"
            onClick={onClose}
            aria-label="Close album modal"
            disabled={saving}
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>

        <div className="med-modal-body">
          {showInstitutionPicker && (
            <>
              <label className="med-form-label" htmlFor="media-album-institution">Institution</label>
              <select
                id="media-album-institution"
                className="med-form-input"
                value={institutionId}
                onChange={(event) => onInstitutionChange?.(event.target.value)}
                disabled={saving}
                style={{ marginBottom: 12 }}
              >
                <option value="">Select institution</option>
                {institutions.map((inst) => (
                  <option key={inst.id} value={inst.id}>{inst.name}</option>
                ))}
              </select>
            </>
          )}
          <label className="med-form-label" htmlFor="media-album-name">Folder name</label>
          <input
            id="media-album-name"
            className="med-form-input"
            value={value}
            onChange={(event) => onChange(event.target.value)}
            placeholder="e.g. Startup Summit 2026"
            autoFocus
            maxLength={80}
            disabled={saving}
          />
          <p className="med-album-modal-hint">Use a clear event or campaign name so assets are easier to find later.</p>
        </div>

        <div className="med-modal-footer">
          <button className="med-btn med-btn-ghost" type="button" onClick={onClose} disabled={saving}>
            Cancel
          </button>
          <button className="med-btn med-btn-primary" type="submit" disabled={disabled}>
            {saving ? "Saving..." : actionLabel}
          </button>
        </div>
      </form>
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
function EmptyState({ hasSearch, inFolder = false, onUpload }: { hasSearch: boolean; inFolder?: boolean; onUpload: () => void }) {
  return (
    <div className="med-empty">
      <div className="med-empty-icon">
        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
          <rect x="3" y="3" width="18" height="18" rx="2" />
          <circle cx="8.5" cy="8.5" r="1.5" />
          <polyline points="21,15 16,10 5,21" />
        </svg>
      </div>
      {hasSearch ? (
        <>
          <div className="med-empty-title">No assets match your filters</div>
          <p className="med-empty-sub">Try adjusting your search term or removing an active tag filter.</p>
        </>
      ) : inFolder ? (
        <>
          <div className="med-empty-title">This folder is empty</div>
          <p className="med-empty-sub">Use <strong>New</strong> to add a sub-folder or upload files here.</p>
          <button className="med-btn med-btn-primary" onClick={onUpload} type="button" style={{ marginTop: 20 }}>
            Upload files
          </button>
        </>
      ) : (
        <>
          <div className="med-empty-title">No media assets yet</div>
          <p className="med-empty-sub">Use <strong>New</strong> to create a folder or upload your first files.</p>
          <button className="med-btn med-btn-primary" onClick={onUpload} type="button" style={{ marginTop: 20 }}>
            Upload files
          </button>
        </>
      )}
    </div>
  );
}

/* ===== Move-folder modal (mount fresh per album via key) ===== */
const SHARED_ROOT = "__shared_root__";

function MoveAlbumModal({
  album,
  albums,
  onClose,
  onMove,
}: {
  album: MediaAlbum;
  albums: MediaAlbum[];
  onClose: () => void;
  onMove: (parentAlbumId: string | null, institutionId: string | null) => void;
}) {
  const [target, setTarget] = useState<string>(album.parentAlbumId ?? "");

  const albumById = useMemo(() => new Map(albums.map((a) => [a.id, a])), [albums]);
  const sharedAlbum = useMemo(() => albums.find((a) => a.shared) ?? null, [albums]);

  // Exclude the folder itself and its descendants — you can't move a folder into itself.
  const blocked = albumSubtreeIds(album.id, albums);
  const folderOptions = buildAlbumOptions(albums)
    .filter((o) => !blocked.has(o.id))
    .map((o) => ({
      value: o.id,
      label: o.label,
      badge: albumById.get(o.id)?.institutionCode,
    }));

  const selectOptions = [
    { value: "", label: "Library root", badge: album.institutionCode },
    ...(sharedAlbum && !album.shared
      ? [{ value: SHARED_ROOT, label: `${sharedAlbum.institutionName} root`, badge: sharedAlbum.institutionCode }]
      : []),
    ...folderOptions,
  ];

  function resolve(value: string): { parentAlbumId: string | null; institutionId: string | null } {
    if (value === "") return { parentAlbumId: null, institutionId: album.institutionId };
    if (value === SHARED_ROOT) return { parentAlbumId: null, institutionId: sharedAlbum?.institutionId ?? null };
    return { parentAlbumId: value, institutionId: albumById.get(value)?.institutionId ?? null };
  }

  const resolved = resolve(target);
  const unchanged =
    resolved.parentAlbumId === (album.parentAlbumId ?? null) &&
    resolved.institutionId === album.institutionId;

  return (
    <div className="med-modal-overlay" role="presentation">
      <div className="med-modal-card med-album-modal" role="dialog" aria-modal="true" aria-label="Move folder">
        <div className="med-modal-header">
          <div>
            <span className="med-modal-title">Move "{album.name}"</span>
            <p className="med-album-modal-sub">Choose the folder this should live inside.</p>
          </div>
          <button className="med-modal-close" type="button" onClick={onClose} aria-label="Close move modal">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <line x1="18" y1="6" x2="6" y2="18" />
              <line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>
        <div className="med-modal-body">
          <label className="med-form-label" htmlFor="move-folder-target">Destination</label>
          <BrandedSelect
            className="med-move-select"
            value={target}
            onChange={setTarget}
            ariaLabel="Move destination folder"
            options={selectOptions}
          />
        </div>
        <div className="med-modal-footer">
          <button className="med-btn med-btn-ghost" type="button" onClick={onClose}>Cancel</button>
          <button
            className="med-btn med-btn-primary"
            type="button"
            disabled={unchanged}
            onClick={() => onMove(resolved.parentAlbumId, resolved.institutionId)}
          >
            Move here
          </button>
        </div>
      </div>
    </div>
  );
}

function FolderPlusIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      <path d="M3 7a2 2 0 0 1 2-2h4l2 3h8a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
      <line x1="12" y1="11" x2="12" y2="17" />
      <line x1="9" y1="14" x2="15" y2="14" />
    </svg>
  );
}

function UploadIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
      <polyline points="17 8 12 3 7 8" />
      <line x1="12" y1="3" x2="12" y2="15" />
    </svg>
  );
}

function FolderUploadIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      <path d="M3 7a2 2 0 0 1 2-2h4l2 3h8a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
      <polyline points="9 14 12 11 15 14" />
      <line x1="12" y1="11" x2="12" y2="18" />
    </svg>
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

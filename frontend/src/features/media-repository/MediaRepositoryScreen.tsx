import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import type { User } from "../../types/auth.types";
import type { MediaAsset, MediaUsage } from "../../api/mediaApi";
import {
  deleteMediaAsset,
  getMediaAsset,
  getMediaAssetUploadUrl,
  registerMediaAsset,
} from "../../api/mediaApi";
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
import FilterBar from "./components/FilterBar";
import AssetDetailPanel from "./components/AssetDetailPanel";
import UploadModal from "./components/UploadModal";
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

  const [networkView, setNetworkView] = useState(false);
  const { assets, setAssets, loading, error, refresh } = useMediaAssets(networkView);

  const [search, setSearch] = useState("");
  const [sort, setSort] = useState<SortOption>("newest");
  const [viewMode, setViewMode] = useState<ViewMode>("grid");
  const [activeTags, setActiveTags] = useState<Set<string>>(new Set());

  const [selectedAsset, setSelectedAsset] = useState<MediaAsset | null>(null);
  const [panelOpen, setPanelOpen] = useState(false);

  const {
    selected: checkedIds,
    toggle: toggleCheck,
    clear: clearSelection,
  } = usePersistentSelection("dasigconnect:media-selection");

  const [addToDraftOpen, setAddToDraftOpen] = useState(false);
  const [drafts, setDrafts] = useState<SubmissionSummary[]>([]);
  const [draftsLoading, setDraftsLoading] = useState(false);
  const [busyDraftId, setBusyDraftId] = useState<string | null>(null);

  const [uploadOpen, setUploadOpen] = useState(false);

  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleteTier, setDeleteTier] = useState<DeleteTier | null>(null);
  const [deleteAsset, setDeleteAsset] = useState<MediaAsset | null>(null);
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

  const filteredAssets = useMemo(() => {
    const term = search.trim().toLowerCase();
    let result = assets.filter((a) => {
      if (activeTags.size > 0) {
        const assetTagLabels = new Set((a.aiTags ?? []).map((t) => t.label));
        if (![...activeTags].every((tag) => assetTagLabels.has(tag))) return false;
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

  const selectedAssets = useMemo(
    () => assets.filter((a) => checkedIds.has(a.id)),
    [assets, checkedIds],
  );
  const selectionMode = checkedIds.size > 0;

  function openAsset(asset: MediaAsset) {
    setSelectedAsset(asset);
    setPanelOpen(true);
    getMediaAsset(asset.id)
      .then((res) => setSelectedAsset(res.data))
      .catch(() => { /* panel stays with summary data on fetch error */ });
  }

  function closePanel() {
    setPanelOpen(false);
    setSelectedAsset(null);
  }

  function clearChecked() {
    clearSelection();
    if (!selectedAsset) setPanelOpen(false);
  }

  function handleToggleCheck(asset: MediaAsset) {
    const willCheck = !checkedIds.has(asset.id);
    toggleCheck(asset.id);
    if (willCheck) openAsset(asset);
  }

  function activeAssetIds() {
    if (checkedIds.size > 0) return [...checkedIds];
    return selectedAsset ? [selectedAsset.id] : [];
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

  async function handleUpload(file: File, onProgress?: (pct: number) => void) {
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
      });
      onProgress?.(100);

      toast.success("Asset uploaded! AI classification in progress…");
      void refresh();
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Upload failed.";
      toast.error(message);
      throw err;
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
        (u) => u.submissionStatus === "draft" || u.submissionStatus === "pending",
      ) ?? [];
      setWarningUsages(warning);
    } else {
      setBlockingUsages([]);
      setWarningUsages([]);
    }

    setDeleteOpen(true);
  }

  async function handleConfirmDelete() {
    if (!deleteAsset) return;
    setDeleting(true);
    try {
      await deleteMediaAsset(deleteAsset.id, deleteTier === "warning");
      setAssets((prev) => prev.filter((a) => a.id !== deleteAsset.id));
      if (selectedAsset?.id === deleteAsset.id) closePanel();
      setDeleteOpen(false);
      toast.success(
        deleteTier === "warning"
          ? "Asset deleted. Broken reference flagged in draft."
          : "Asset deleted. Terminal submission records updated.",
      );
    } catch {
      toast.error("Failed to delete asset.");
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
          <p className="med-subtitle">Institution assets · AI-classified · UC-2.2</p>
        </div>
        <div className="med-header-actions">
          <button
            className="med-btn med-btn-ghost med-btn-sm"
            onClick={() => setUploadOpen(true)}
            type="button"
          >
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="16,16 12,12 8,16" />
              <line x1="12" y1="12" x2="12" y2="21" />
              <path d="M20.39 18.39A5 5 0 0 0 18 9h-1.26A8 8 0 1 0 3 16.3" />
            </svg>
            Upload Asset
          </button>
          <button
            className="med-btn med-btn-primary med-btn-sm"
            onClick={() => navigate("/submissions/new")}
            type="button"
          >
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <line x1="12" y1="5" x2="12" y2="19" />
              <line x1="5" y1="12" x2="19" y2="12" />
            </svg>
            New Submission
          </button>
        </div>
      </div>

      {/* Network View bar */}
      {isAdmin && (
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
      )}

      {/* Filter Bar */}
      <FilterBar
        search={search}
        sort={sort}
        viewMode={viewMode}
        networkView={networkView}
        isAdmin={isAdmin}
        activeTags={activeTags}
        tagChips={tagChips}
        onSearchChange={setSearch}
        onSortChange={setSort}
        onViewModeChange={setViewMode}
        onNetworkViewToggle={() => setNetworkView((v) => !v)}
        onTagToggle={toggleTag}
      />

      {/* Result strip */}
      <div className="med-result-strip">
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
      </div>

      {/* Media Grid / States */}
      {loading ? (
        <SkeletonGrid viewMode={viewMode} />
      ) : error ? (
        <ErrorState message={error} onRetry={() => void refresh()} />
      ) : filteredAssets.length === 0 ? (
        <EmptyState hasSearch={search.length > 0 || activeTags.size > 0} onUpload={() => setUploadOpen(true)} />
      ) : (
        <div className={`med-grid${viewMode === "list" ? " list-view" : ""}${checkedIds.size > 0 ? " selecting" : ""}`}>
          {filteredAssets.map((asset, idx) => (
            <AssetCard
              key={asset.id}
              asset={asset}
              selected={selectedAsset?.id === asset.id}
              checked={checkedIds.has(asset.id)}
              listView={viewMode === "list"}
              animationDelay={Math.min(idx * 40, 480)}
              showInstitutionChip={networkView && isAdmin}
              onClick={() => openAsset(asset)}
              onToggleCheck={() => handleToggleCheck(asset)}
            />
          ))}
        </div>
      )}

      {/* Detail Panel (portal) */}
      <AssetDetailPanel
        asset={selectedAsset}
        open={panelOpen}
        isAdmin={isAdmin}
        selectionMode={selectionMode}
        selectedAssets={selectedAssets}
        onViewAsset={(a) => openAsset(a)}
        onDeselectAsset={(id) => toggleCheck(id)}
        onNewPost={handleNewPost}
        onClearSelection={clearChecked}
        onClose={closePanel}
        canAddToDraft={user.role === "contributor"}
        onAddToDraft={openAddToDraft}
        onDownload={() => void handleDownload()}
        onDeleteBlocked={() => openDeleteModal("blocked")}
        onDeleteWarning={() => openDeleteModal("warning")}
        onDeleteFree={() => openDeleteModal("free")}
      />

      {/* Upload Modal (portal) */}
      <UploadModal
        open={uploadOpen}
        institutionName={user.inst}
        onClose={() => setUploadOpen(false)}
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

      {/* Selection action bar */}
      <div className={`med-selbar${checkedIds.size > 0 ? " visible" : ""}`} role="region" aria-label="Selected assets">
        <button
          className="med-selbar-info"
          type="button"
          onClick={() => setPanelOpen(true)}
          title="View selected assets"
        >
          <span className="med-selbar-count">{checkedIds.size}</span>
          <span>{checkedIds.size === 1 ? "asset selected" : "assets selected"}</span>
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ opacity: 0.7 }}>
            <polyline points="9,18 15,12 9,6" />
          </svg>
        </button>
        <div className="med-selbar-actions">
          <button className="med-btn med-btn-ghost med-btn-sm" onClick={clearChecked} type="button">
            Clear
          </button>
          <button className="med-btn med-btn-primary med-btn-sm" onClick={handleNewPost} type="button">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <line x1="12" y1="5" x2="12" y2="19" />
              <line x1="5" y1="12" x2="19" y2="12" />
            </svg>
            New Post
          </button>
        </div>
      </div>
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
function EmptyState({ hasSearch, onUpload }: { hasSearch: boolean; onUpload: () => void }) {
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
      ) : (
        <>
          <div className="med-empty-title">No media assets yet</div>
          <p className="med-empty-sub">Upload your first asset to start building the institutional media library.</p>
          <button className="med-btn med-btn-primary" onClick={onUpload} type="button" style={{ marginTop: 20 }}>
            Upload Asset
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

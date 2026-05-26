import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import type { User } from "../../types/auth.types";
import type { MediaAsset, MediaUsage } from "../../api/mediaApi";
import {
  deleteMediaAsset,
  getMediaAssetUploadUrl,
  registerMediaAsset,
} from "../../api/mediaApi";
import { useToast } from "../../context/ToastContext";
import { useMediaAssets } from "./hooks/useMediaAssets";
import type { SortOption, ViewMode, DeleteTier } from "./types";
import AssetCard from "./components/AssetCard";
import FilterBar from "./components/FilterBar";
import AssetDetailPanel from "./components/AssetDetailPanel";
import UploadModal from "./components/UploadModal";
import DeleteModal from "./components/DeleteModal";
import "../../styles/media-repository.css";

interface MediaRepositoryScreenProps {
  user: User;
}

function safeFileName(fileName: string) {
  return fileName.replace(/[^a-zA-Z0-9._-]/g, "-");
}

function fileTypeFromFile(file: File) {
  const ext = file.name.split(".").pop()?.toLowerCase() ?? "";
  return ext === "jpg" ? "jpeg" : ext;
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

  function openAsset(asset: MediaAsset) {
    setSelectedAsset(asset);
    setPanelOpen(true);
  }

  function closePanel() {
    setPanelOpen(false);
    setSelectedAsset(null);
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

  function handleTitleChange(title: string) {
    if (!selectedAsset) return;
    setSelectedAsset({ ...selectedAsset, title });
    setAssets((prev) => prev.map((a) => (a.id === selectedAsset.id ? { ...a, title } : a)));
  }

  async function handleUpload(file: File) {
    try {
      const { data: urlData } = await getMediaAssetUploadUrl({
        fileName: safeFileName(file.name),
        fileType: fileTypeFromFile(file),
      });

      const upload = await fetch(urlData.signedUrl, {
        method: "PUT",
        headers: { "Content-Type": file.type || "application/octet-stream" },
        body: file,
      });
      if (!upload.ok) throw new Error("Supabase upload failed.");

      await registerMediaAsset({
        storageUrl: urlData.publicUrl,
        fileName: file.name,
        fileType: fileTypeFromFile(file),
        fileSizeBytes: file.size,
      });

      toast.success("Asset uploaded! AI classification in progress…");
      void refresh();
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Upload failed.";
      toast.error(message);
      throw err;
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
      await deleteMediaAsset(deleteAsset.id);
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
        <div className={`med-grid${viewMode === "list" ? " list-view" : ""}`}>
          {filteredAssets.map((asset, idx) => (
            <AssetCard
              key={asset.id}
              asset={asset}
              selected={selectedAsset?.id === asset.id}
              listView={viewMode === "list"}
              animationDelay={Math.min(idx * 40, 480)}
              showInstitutionChip={networkView && isAdmin}
              onClick={() => openAsset(asset)}
            />
          ))}
        </div>
      )}

      {/* Detail Panel (portal) */}
      <AssetDetailPanel
        asset={selectedAsset}
        open={panelOpen}
        isAdmin={isAdmin}
        onClose={closePanel}
        onTitleChange={handleTitleChange}
        onUseInNewPost={() => {
          toast.success("Opening submission form with this asset pre-loaded…");
          navigate("/submissions/new");
        }}
        onAddToDraft={() => toast.success("Appended to active draft.")}
        onDownload={() => {
          if (selectedAsset?.storageUrl) window.open(selectedAsset.storageUrl, "_blank");
          else toast.error("No file URL available.");
        }}
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

import { useCallback, useEffect, useState } from "react";
import { createPortal } from "react-dom";
import type { User } from "../../../types/auth.types";
import {
  listTrashMediaAssets,
  restoreMediaAsset,
  bulkRestoreMediaAssets,
  purgeTrashMediaAsset,
  bulkPurgeTrashMediaAssets,
  emptyTrashMediaAssets,
  type TrashAsset,
} from "../../../api/mediaApi";
import { formatFileSize, formatUploadDate, isVideoType } from "../utils";
import BrandedSelect from "../../../components/ui/BrandedSelect";
import { useToast } from "../../../context/ToastContext";

interface TrashViewProps {
  user: User;
  institutions: { id: string; name: string }[];
  selectedInstitutionId: string | null;
  onInstitutionChange: (id: string | null) => void;
  onBack?: () => void;
  onRestored: () => void;
}

type PurgeTarget =
  | { type: "single"; asset: TrashAsset }
  | { type: "bulk"; count: number }
  | { type: "empty" };

export default function TrashView({
  user,
  institutions,
  selectedInstitutionId,
  onInstitutionChange,
  onBack: _onBack,
  onRestored,
}: TrashViewProps) {
  const toast = useToast();
  const isAdmin = user.role === "admin";

  const [search, setSearch] = useState("");
  const [page, setPage] = useState(1);
  const pageSize = 24;

  const [items, setItems] = useState<TrashAsset[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [loading, setLoading] = useState(true);

  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [busyAction, setBusyAction] = useState(false);
  const [confirmPurgeTarget, setConfirmPurgeTarget] = useState<PurgeTarget | null>(null);

  const fetchTrash = useCallback(async () => {
    setLoading(true);
    try {
      const res = await listTrashMediaAssets({
        query: search.trim() || undefined,
        institutionId: selectedInstitutionId,
        page,
        pageSize,
      });
      setItems(res.items);
      setTotalCount(res.totalCount);
    } catch {
      toast.error("Failed to load trash items.");
    } finally {
      setLoading(false);
    }
  }, [search, selectedInstitutionId, page, pageSize, toast]);

  useEffect(() => {
    void fetchTrash();
  }, [fetchTrash]);

  // Deselect any selected items when items list changes
  useEffect(() => {
    setSelectedIds((prev) => {
      const existing = new Set(items.map((i) => i.id));
      const next = new Set<string>();
      prev.forEach((id) => {
        if (existing.has(id)) next.add(id);
      });
      return next;
    });
  }, [items]);

  const toggleSelectAll = () => {
    if (selectedIds.size === items.length && items.length > 0) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(items.map((i) => i.id)));
    }
  };

  const toggleSelectItem = (id: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const handleRestoreSingle = async (asset: TrashAsset) => {
    setBusyAction(true);
    try {
      await restoreMediaAsset(asset.id);
      toast.success(`Restored "${asset.title || asset.fileName}" to the library.`);
      void fetchTrash();
      onRestored();
    } catch {
      toast.error("Failed to restore asset.");
    } finally {
      setBusyAction(false);
    }
  };

  const handleRestoreBulk = async () => {
    if (selectedIds.size === 0) return;
    setBusyAction(true);
    try {
      const count = selectedIds.size;
      await bulkRestoreMediaAssets(Array.from(selectedIds));
      toast.success(`Restored ${count} ${count === 1 ? "asset" : "assets"} to the library.`);
      setSelectedIds(new Set());
      void fetchTrash();
      onRestored();
    } catch {
      toast.error("Failed to restore selected assets.");
    } finally {
      setBusyAction(false);
    }
  };

  const handleConfirmPurge = async () => {
    if (!confirmPurgeTarget) return;
    setBusyAction(true);
    try {
      if (confirmPurgeTarget.type === "single") {
        await purgeTrashMediaAsset(confirmPurgeTarget.asset.id);
        toast.success(`Permanently deleted "${confirmPurgeTarget.asset.title || confirmPurgeTarget.asset.fileName}".`);
      } else if (confirmPurgeTarget.type === "bulk") {
        const count = selectedIds.size;
        await bulkPurgeTrashMediaAssets(Array.from(selectedIds));
        toast.success(`Permanently deleted ${count} ${count === 1 ? "asset" : "assets"}.`);
        setSelectedIds(new Set());
      } else if (confirmPurgeTarget.type === "empty") {
        const purgedCount = await emptyTrashMediaAssets(selectedInstitutionId);
        toast.success(`Emptied trash (${purgedCount} assets permanently deleted).`);
        setSelectedIds(new Set());
      }
      setConfirmPurgeTarget(null);
      void fetchTrash();
    } catch {
      toast.error("Failed to delete permanently.");
    } finally {
      setBusyAction(false);
    }
  };

  const totalPages = Math.max(1, Math.ceil(totalCount / pageSize));

  return (
    <div className="med-trash-view">

      {/* Retention notice alert */}
      <div className="med-trash-retention-banner">
        <div className="med-trash-banner-icon">
          <i className="ti ti-info-circle" />
        </div>
        <div className="med-trash-banner-text">
          <strong>30-Day Retention Window:</strong> Items in trash are automatically purged permanently after 30 days. Restoring an asset brings it back to the active media library with its existing album and tags intact.
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className="med-filter-bar med-trash-filter-bar">
        <div className="med-filter-row1">
          <div className="med-search-wrap">
            <svg
              width="15"
              height="15"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.5"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <circle cx="11" cy="11" r="8" />
              <line x1="21" y1="21" x2="16.65" y2="16.65" />
            </svg>
            <input
              type="text"
              className="med-search-input"
              placeholder="Search deleted files by name, code, or title…"
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(1);
              }}
            />
          </div>

          {isAdmin && (
            <BrandedSelect
              className="med-inst-select"
              value={selectedInstitutionId ?? ""}
              onChange={(val) => {
                onInstitutionChange(val || null);
                setPage(1);
              }}
              ariaLabel="Filter trash by institution"
              options={[
                { value: "", label: "All institutions" },
                ...institutions.map((inst) => ({ value: inst.id, label: inst.name })),
              ]}
            />
          )}
        </div>
      </div>

      {/* Selection & Batch Action Strip */}
      <div className="med-result-strip med-trash-strip">
        <div className="med-result-strip-left">
          <label className="med-trash-select-all">
            <input
              type="checkbox"
              checked={items.length > 0 && selectedIds.size === items.length}
              onChange={toggleSelectAll}
              disabled={items.length === 0}
            />
            <span>
              {selectedIds.size > 0
                ? `${selectedIds.size} selected`
                : `${totalCount} ${totalCount === 1 ? "item" : "items"} in trash`}
            </span>
          </label>
        </div>

        {selectedIds.size > 0 ? (
          <div className="med-trash-bulk-actions">
            <button
              type="button"
              className="med-btn med-btn-primary med-btn-sm"
              onClick={handleRestoreBulk}
              disabled={busyAction}
            >
              <i className="ti ti-arrow-back-up" />
              Restore Selected ({selectedIds.size})
            </button>
            <button
              type="button"
              className="med-btn med-btn-danger med-btn-sm"
              onClick={() => setConfirmPurgeTarget({ type: "bulk", count: selectedIds.size })}
              disabled={busyAction}
            >
              <i className="ti ti-trash-x" />
              Delete Permanently ({selectedIds.size})
            </button>
          </div>
        ) : (
          totalCount > 0 && (
            <button
              type="button"
              className="med-btn med-btn-danger-outline med-btn-sm"
              onClick={() => setConfirmPurgeTarget({ type: "empty" })}
              disabled={busyAction || loading}
            >
              <i className="ti ti-trash-x" />
              Empty Trash
            </button>
          )
        )}
      </div>

      {/* Grid of Trashed Items */}
      {loading ? (
        <div className="med-trash-empty">
          <div className="med-trash-spinner" />
          <p>Loading trashed assets…</p>
        </div>
      ) : items.length === 0 ? (
        <div className="med-trash-empty">
          <div className="med-trash-empty-icon">
            <i className="ti ti-trash-off" />
          </div>
          <h2 className="med-trash-empty-title">Trash is empty</h2>
          <p className="med-trash-empty-desc">
            {search
              ? "No deleted items match your search."
              : "Deleted assets will appear here and be stored for 30 days before being permanently purged."}
          </p>
        </div>
      ) : (
        <div className="med-trash-grid">
          {items.map((asset) => {
            const isVideo = isVideoType(asset.fileType);
            const isSelected = selectedIds.has(asset.id);
            const isUrgent = asset.daysRemaining <= 3;
            const isWarning = asset.daysRemaining <= 7 && asset.daysRemaining > 3;

            return (
              <div
                key={asset.id}
                className={`med-trash-card${isSelected ? " is-selected" : ""}`}
                onClick={() => toggleSelectItem(asset.id)}
              >
                {/* Checkbox overlay */}
                <div
                  className="med-trash-card-checkbox"
                  onClick={(e) => e.stopPropagation()}
                >
                  <input
                    type="checkbox"
                    checked={isSelected}
                    onChange={() => toggleSelectItem(asset.id)}
                    aria-label={`Select ${asset.title || asset.fileName}`}
                  />
                </div>

                {/* Thumbnail */}
                <div className="med-trash-card-thumb">
                  {isVideo ? (
                    <div className="med-trash-card-video-thumb">
                      <i className="ti ti-video" />
                      <span className="med-trash-format-pill">{asset.fileType.toUpperCase()}</span>
                    </div>
                  ) : (
                    <img
                      src={asset.storageUrl}
                      alt={asset.title || asset.fileName}
                      loading="lazy"
                    />
                  )}
                  {/* Days remaining badge */}
                  <div
                    className={`med-trash-retention-pill${
                      isUrgent ? " urgent" : isWarning ? " warning" : ""
                    }`}
                    title={`Scheduled for permanent deletion after 30 days`}
                  >
                    <i className="ti ti-clock" />
                    {asset.daysRemaining <= 0
                      ? "Purging soon"
                      : asset.daysRemaining === 1
                        ? "1 day left"
                        : `${asset.daysRemaining} days left`}
                  </div>
                </div>

                {/* Card Content */}
                <div className="med-trash-card-body">
                  <h3 className="med-trash-card-title" title={asset.title || asset.fileName}>
                    {asset.title || asset.fileName}
                  </h3>

                  <div className="med-trash-card-meta">
                    <span className="med-trash-meta-code">{asset.assetCode}</span>
                    <span>·</span>
                    <span>{formatFileSize(asset.fileSizeBytes)}</span>
                  </div>

                  <div className="med-trash-card-dates">
                    <div className="med-trash-date-row">
                      <i className="ti ti-trash" />
                      <span>Deleted {formatUploadDate(asset.deletedAt)}</span>
                    </div>
                    {asset.deletedByName && (
                      <div className="med-trash-date-row" title={`Deleted by ${asset.deletedByName}`}>
                        <i className="ti ti-user-x" />
                        <span>By {asset.deletedByName}</span>
                      </div>
                    )}
                    {asset.institutionName && isAdmin && (
                      <div className="med-trash-date-row" title={asset.institutionName}>
                        <i className="ti ti-building" />
                        <span className="med-trash-inst-name">{asset.institutionName}</span>
                      </div>
                    )}
                  </div>

                  {/* Actions */}
                  <div className="med-trash-card-actions" onClick={(e) => e.stopPropagation()}>
                    <button
                      type="button"
                      className="med-btn med-btn-sm med-btn-restore"
                      title="Restore to active library"
                      disabled={busyAction}
                      onClick={() => handleRestoreSingle(asset)}
                    >
                      <i className="ti ti-arrow-back-up" />
                      <span>Restore</span>
                    </button>
                    <button
                      type="button"
                      className="med-btn med-btn-sm med-btn-purge"
                      title="Delete permanently from storage"
                      disabled={busyAction}
                      onClick={() => setConfirmPurgeTarget({ type: "single", asset })}
                    >
                      <i className="ti ti-trash-x" />
                      <span>Purge</span>
                    </button>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Pagination Footer */}
      {totalPages > 1 && (
        <div className="med-trash-pagination">
          <button
            type="button"
            className="med-btn med-btn-ghost med-btn-sm"
            disabled={page <= 1 || loading}
            onClick={() => setPage((p) => Math.max(1, p - 1))}
          >
            Previous
          </button>
          <span className="med-trash-page-info">
            Page {page} of {totalPages}
          </span>
          <button
            type="button"
            className="med-btn med-btn-ghost med-btn-sm"
            disabled={page >= totalPages || loading}
            onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
          >
            Next
          </button>
        </div>
      )}

      {/* Permanent Delete Confirmation Modal */}
      {confirmPurgeTarget &&
        createPortal(
          <div
            className="med-modal-overlay open"
            onClick={(e) => {
              if (e.target === e.currentTarget && !busyAction) {
                setConfirmPurgeTarget(null);
              }
            }}
          >
            <div className="med-modal-card med-trash-confirm-modal" role="dialog" aria-modal="true">
              <div className="med-modal-header">
                <span className="med-modal-title" style={{ color: "var(--med-red, #dc2626)", display: "flex", alignItems: "center", gap: 8 }}>
                  <i className="ti ti-alert-triangle" />
                  {confirmPurgeTarget.type === "empty"
                    ? "Empty All Items in Trash?"
                    : confirmPurgeTarget.type === "bulk"
                      ? `Permanently Delete ${confirmPurgeTarget.count} Assets?`
                      : "Permanently Delete Asset?"}
                </span>
                <button
                  type="button"
                  className="med-modal-close"
                  onClick={() => setConfirmPurgeTarget(null)}
                  disabled={busyAction}
                  aria-label="Close"
                >
                  <i className="ti ti-x" />
                </button>
              </div>

              <div className="med-modal-body">
                <p className="med-trash-warning-text">
                  {confirmPurgeTarget.type === "empty"
                    ? "This will permanently delete all files in the trash. Storage objects in Cloudflare R2 will be deleted immediately and can never be recovered."
                    : confirmPurgeTarget.type === "bulk"
                      ? `This will permanently delete ${confirmPurgeTarget.count} selected files. Storage objects in Cloudflare R2 will be deleted immediately and can never be recovered.`
                      : `Are you sure you want to permanently delete "${confirmPurgeTarget.asset.title || confirmPurgeTarget.asset.fileName}"? This action cannot be undone.`}
                </p>
                <div className="med-trash-warning-alert">
                  <i className="ti ti-alert-circle" />
                  <span>Permanent deletion is irreversible. You will not be able to restore these files.</span>
                </div>
              </div>

              <div className="med-modal-footer">
                <button
                  type="button"
                  className="med-btn med-btn-ghost med-btn-sm"
                  onClick={() => setConfirmPurgeTarget(null)}
                  disabled={busyAction}
                >
                  Cancel
                </button>
                <button
                  type="button"
                  className="med-btn med-btn-danger med-btn-sm"
                  onClick={handleConfirmPurge}
                  disabled={busyAction}
                >
                  {busyAction ? "Deleting…" : "Delete Permanently"}
                </button>
              </div>
            </div>
          </div>,
          document.body,
        )}
    </div>
  );
}

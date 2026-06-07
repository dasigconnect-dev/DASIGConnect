import { useEffect, useMemo, useState } from "react";
import { Trash2 } from "lucide-react";
import MediaSubNav from "../components/MediaSubNav";
import { listInstitutions, type InstitutionResponse } from "../../../api/authApi";
import type { TrashItem } from "../../../api/mediaApi";
import type { User } from "../../../types/auth.types";
import "../../../styles/trash.css";
import TrashItemRow from "./components/TrashItemRow";
import { useTrash } from "./useTrash";
import { useConfirmModal } from "../../../hooks/useConfirmModal";

interface TrashScreenProps {
  user: User;
}

export default function TrashScreen({ user }: TrashScreenProps) {
  const isAdmin = user.role === "admin";
  const [institutionId, setInstitutionId] = useState<string | null>(null);
  const [institutions, setInstitutions] = useState<InstitutionResponse[]>([]);

  const { items, loading, error, refresh, busyIds, restore, purge, purgeMany } = useTrash(institutionId, true);
  const { confirm, modal, busy } = useConfirmModal();

  const [selected, setSelected] = useState<Set<string>>(new Set());

  useEffect(() => {
    if (!isAdmin) return;
    let active = true;
    listInstitutions()
      .then((res) => {
        if (active) setInstitutions(res.data ?? []);
      })
      .catch(() => undefined);
    return () => {
      active = false;
    };
  }, [isAdmin]);

  // Items leave the list on restore/purge/scope change, so derive the live selection by
  // intersecting with what's present rather than syncing state in an effect.
  const presentIds = useMemo(() => new Set(items.map((i) => i.asset.id)), [items]);
  const selectedIds = useMemo(() => Array.from(selected).filter((id) => presentIds.has(id)), [selected, presentIds]);
  const selectedCount = selectedIds.length;
  const allSelected = items.length > 0 && selectedCount === items.length;

  function toggleSelect(assetId: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(assetId)) next.delete(assetId);
      else next.add(assetId);
      return next;
    });
  }

  function toggleSelectAll() {
    setSelected(allSelected ? new Set() : new Set(items.map((i) => i.asset.id)));
  }

  function requestDelete(item: TrashItem) {
    confirm(
      {
        title: "Delete permanently?",
        message: (
          <>
            This permanently deletes <strong>{item.asset.title || item.asset.fileName}</strong> ({item.asset.assetCode}).
            This can’t be undone.
          </>
        ),
        confirmLabel: "Delete",
        busyLabel: "Deleting…",
        dangerous: true,
      },
      async () => {
        await purge(item.asset.id);
      },
    );
  }

  function requestBulkDelete() {
    const count = selectedCount;
    confirm(
      {
        title: `Delete ${count} item${count === 1 ? "" : "s"}?`,
        message: `This permanently deletes ${count} selected item${count === 1 ? "" : "s"} from trash. This can’t be undone.`,
        confirmLabel: `Delete ${count}`,
        busyLabel: "Deleting…",
        dangerous: true,
      },
      async () => {
        const result = await purgeMany(selectedIds);
        setSelected(new Set(result.failed));
      },
    );
  }

  const selectAllRef = useMemo(
    () => (el: HTMLInputElement | null) => {
      if (el) el.indeterminate = selectedCount > 0 && !allSelected;
    },
    [selectedCount, allSelected],
  );

  const showSkeleton = loading && items.length === 0;

  return (
    <>
      <MediaSubNav user={user} page="Trash" />
      <div className="trash-screen">
      <header className="trash-header">
        <div className="trash-heading">
          <h1 className="trash-title">
            <Trash2 size={22} aria-hidden="true" />
            Trash
            {items.length > 0 && <span className="trash-count">{items.length}</span>}
          </h1>
          <p className="trash-subtitle">
            Deleted media stays here and is permanently removed automatically after the retention
            window. Restore an item, or delete it now.
          </p>
        </div>
        <div className="trash-controls">
          {isAdmin && (
            <label className="trash-scope">
              <span className="trash-scope-label">Scope</span>
              <select
                className="trash-scope-select"
                value={institutionId ?? ""}
                onChange={(e) => setInstitutionId(e.target.value || null)}
              >
                <option value="">All institutions</option>
                {institutions.map((inst) => (
                  <option key={inst.id} value={inst.id}>
                    {inst.name}
                  </option>
                ))}
              </select>
            </label>
          )}
          <button type="button" className="med-btn med-btn-ghost med-btn-sm" onClick={() => void refresh()} disabled={loading}>
            {loading ? "Refreshing…" : "Refresh"}
          </button>
        </div>
      </header>

      {items.length > 0 && (
        <div className="trash-selectbar">
          <label className="trash-selectall">
            <input
              ref={selectAllRef}
              type="checkbox"
              checked={allSelected}
              disabled={busy}
              onChange={toggleSelectAll}
              aria-label="Select all trashed items"
            />
            <span>{selectedCount > 0 ? `${selectedCount} selected` : "Select all"}</span>
          </label>

          {selectedCount > 0 && (
            <div className="trash-selectbar-actions">
              <button type="button" className="med-btn med-btn-ghost med-btn-sm" disabled={busy} onClick={() => setSelected(new Set())}>
                Clear
              </button>
              <button type="button" className="trash-danger-link" disabled={busy} onClick={requestBulkDelete}>
                <Trash2 size={14} aria-hidden="true" />
                Delete {selectedCount}
              </button>
            </div>
          )}
        </div>
      )}

      {showSkeleton && (
        <ul className="trash-list" aria-hidden="true">
          {[0, 1, 2].map((i) => (
            <li key={i} className="trash-row trash-row--skeleton">
              <div className="trash-thumb skeleton-box" />
              <div className="trash-meta">
                <span className="skeleton-line" style={{ width: "45%" }} />
                <span className="skeleton-line" style={{ width: "25%" }} />
              </div>
            </li>
          ))}
        </ul>
      )}

      {error && !loading && (
        <div className="trash-state trash-state--error" role="alert">
          <p>{error}</p>
          <button type="button" className="med-btn med-btn-ghost med-btn-sm" onClick={() => void refresh()}>
            Retry
          </button>
        </div>
      )}

      {!loading && !error && items.length === 0 && (
        <div className="trash-empty">
          <div className="trash-empty-icon">
            <Trash2 size={28} aria-hidden="true" />
          </div>
          <p className="trash-empty-title">Trash is empty</p>
          <p className="trash-empty-sub">Deleted media will appear here and can be restored before it’s purged.</p>
        </div>
      )}

      {items.length > 0 && (
        <ul className="trash-list">
          {items.map((item) => (
            <TrashItemRow
              key={item.asset.id}
              item={item}
              busy={busyIds.has(item.asset.id)}
              selected={selected.has(item.asset.id)}
              onToggleSelect={toggleSelect}
              onRestore={(id) => void restore(id)}
              onRequestDelete={requestDelete}
            />
          ))}
        </ul>
      )}
      </div>
      {modal}
    </>
  );
}

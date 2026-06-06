import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { ArrowLeft, Trash2 } from "lucide-react";
import { listInstitutions, type InstitutionResponse } from "../../../api/authApi";
import type { User } from "../../../types/auth.types";
import "../../../styles/trash.css";
import TrashItemRow from "./components/TrashItemRow";
import { useTrash } from "./useTrash";

interface TrashScreenProps {
  user: User;
}

export default function TrashScreen({ user }: TrashScreenProps) {
  const isAdmin = user.role === "admin";
  const [institutionId, setInstitutionId] = useState<string | null>(null);
  const [institutions, setInstitutions] = useState<InstitutionResponse[]>([]);

  const { items, setItems, loading, error, refresh } = useTrash(institutionId, true);

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

  function handleRemoved(assetId: string) {
    setItems((prev) => prev.filter((i) => i.asset.id !== assetId));
  }

  const showSkeleton = loading && items.length === 0;

  return (
    <div className="trash-screen">
      <Link to="/media-repository" className="trash-back">
        <ArrowLeft size={15} aria-hidden="true" />
        Media Library
      </Link>

      <header className="trash-header">
        <div className="trash-heading">
          <h1 className="trash-title">
            <Trash2 size={22} aria-hidden="true" />
            Trash
            {items.length > 0 && <span className="trash-count">{items.length}</span>}
          </h1>
          <p className="trash-subtitle">
            Deleted media stays here and is permanently removed automatically after the retention
            window. Restore an item, or delete it forever now.
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
            <TrashItemRow key={item.asset.id} item={item} onRemoved={handleRemoved} />
          ))}
        </ul>
      )}
    </div>
  );
}

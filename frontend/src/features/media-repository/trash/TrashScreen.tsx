import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
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

  return (
    <div className="trash-screen">
      <header className="trash-header">
        <div>
          <Link to="/media-repository" className="trash-back">&larr; Media Library</Link>
          <h1 className="trash-title">Trash</h1>
          <p className="trash-subtitle">
            Deleted media is kept here, then permanently removed automatically after the retention
            window. Restore it, or delete it forever now.
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

      {loading && items.length === 0 && <div className="trash-state" aria-live="polite">Loading trash…</div>}

      {error && !loading && (
        <div className="trash-state trash-state--error" role="alert">
          <p>{error}</p>
          <button type="button" className="med-btn med-btn-ghost med-btn-sm" onClick={() => void refresh()}>
            Retry
          </button>
        </div>
      )}

      {!loading && !error && items.length === 0 && (
        <div className="trash-state">Trash is empty. Deleted media will appear here.</div>
      )}

      {items.length > 0 && (
        <div className="trash-list">
          {items.map((item) => (
            <TrashItemRow key={item.asset.id} item={item} onRemoved={handleRemoved} />
          ))}
        </div>
      )}
    </div>
  );
}

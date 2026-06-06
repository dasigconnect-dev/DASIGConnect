import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listInstitutions, type InstitutionResponse } from "../../../api/authApi";
import type { User } from "../../../types/auth.types";
import "../../../styles/duplicate-review.css";
import DuplicatePairCard from "./components/DuplicatePairCard";
import { useDuplicateCandidates } from "./useDuplicateCandidates";

interface DuplicateReviewScreenProps {
  user: User;
}

export default function DuplicateReviewScreen({ user }: DuplicateReviewScreenProps) {
  const isAdmin = user.role === "admin";
  const [institutionId, setInstitutionId] = useState<string | null>(null);
  const [institutions, setInstitutions] = useState<InstitutionResponse[]>([]);

  const { pairs, setPairs, loading, error, refresh } = useDuplicateCandidates(institutionId, true);

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

  function handleResolved(candidateId: string) {
    setPairs((prev) => prev.filter((p) => p.candidateAssetId !== candidateId));
  }

  return (
    <div className="dup-screen">
      <header className="dup-header">
        <div>
          <Link to="/media-repository" className="dup-back">&larr; Media Library</Link>
          <h1 className="dup-title">Duplicate Review</h1>
          <p className="dup-subtitle">
            Adjudicate perceptual-hash duplicate candidates. Decisions are recorded and reversible —
            an asset is never deleted here.
          </p>
        </div>
        <div className="dup-header-controls">
          {isAdmin && (
            <label className="dup-scope">
              <span className="dup-scope-label">Scope</span>
              <select
                className="dup-scope-select"
                value={institutionId ?? ""}
                onChange={(e) => setInstitutionId(e.target.value || null)}
              >
                <option value="">Network (all institutions)</option>
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

      {loading && pairs.length === 0 && <div className="dup-state" aria-live="polite">Loading duplicate candidates…</div>}

      {error && !loading && (
        <div className="dup-state dup-state--error" role="alert">
          <p>{error}</p>
          <button type="button" className="med-btn med-btn-ghost med-btn-sm" onClick={() => void refresh()}>
            Retry
          </button>
        </div>
      )}

      {!loading && !error && pairs.length === 0 && (
        <div className="dup-state">
          No pending duplicate candidates in this scope. New flagged duplicates appear here for review.
        </div>
      )}

      {pairs.length > 0 && (
        <div className="dup-list">
          {pairs.map((pair) => (
            <DuplicatePairCard key={pair.candidateAssetId} pair={pair} onResolved={handleResolved} />
          ))}
        </div>
      )}
    </div>
  );
}

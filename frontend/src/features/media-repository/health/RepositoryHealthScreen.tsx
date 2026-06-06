import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listInstitutions, type InstitutionResponse } from "../../../api/authApi";
import type { User } from "../../../types/auth.types";
import "../../../styles/repository-health.css";
import HealthSection from "./components/HealthSection";
import HealthMetricCard, { type MetricTone } from "./components/HealthMetricCard";
import CoverageMeter, { type MeterTone } from "./components/CoverageMeter";
import { useRepositoryHealth } from "./useRepositoryHealth";

interface RepositoryHealthScreenProps {
  user: User;
}

const NUMBER = new Intl.NumberFormat();

function formatNumber(n: number) {
  return NUMBER.format(n);
}

function formatBytes(bytes: number) {
  if (bytes <= 0) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  const exp = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  const value = bytes / Math.pow(1024, exp);
  return `${value.toFixed(value >= 100 || exp === 0 ? 0 : 1)} ${units[exp]}`;
}

function coverageTone(pct: number, warnBelow: number, dangerBelow: number): MeterTone {
  if (pct < dangerBelow) return "danger";
  if (pct < warnBelow) return "warn";
  return "good";
}

function countTone(n: number, danger = false): MetricTone {
  if (n <= 0) return "good";
  return danger ? "danger" : "warn";
}

const mediaLink = (health: string) => `/media-repository?health=${health}`;

export default function RepositoryHealthScreen({ user }: RepositoryHealthScreenProps) {
  const isAdmin = user.role === "admin";
  const [institutionId, setInstitutionId] = useState<string | null>(null);
  const [institutions, setInstitutions] = useState<InstitutionResponse[]>([]);

  const { data, loading, error, refresh } = useRepositoryHealth(institutionId, true);

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

  const scopeName = data
    ? data.scope === "network"
      ? "Network-wide"
      : institutions.find((i) => i.id === data.institutionId)?.name ?? "Your institution"
    : "";

  return (
    <div className="rh-screen">
      <header className="rh-header">
        <div className="rh-header-main">
          <Link to="/media-repository" className="rh-back">&larr; Media Library</Link>
          <h1 className="rh-title">Repository Health</h1>
          <p className="rh-subtitle">
            Preservation, curation, and governance signals across the managed media repository.
          </p>
        </div>
        <div className="rh-header-controls">
          {isAdmin && (
            <label className="rh-scope">
              <span className="rh-scope-label">Scope</span>
              <select
                className="rh-scope-select"
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
          <button
            type="button"
            className="rh-refresh"
            onClick={() => void refresh()}
            disabled={loading}
          >
            {loading ? "Refreshing…" : "Refresh"}
          </button>
        </div>
      </header>

      {loading && !data && <div className="rh-state" aria-live="polite">Loading repository health…</div>}

      {error && !loading && (
        <div className="rh-state rh-state--error" role="alert">
          <p>{error}</p>
          <button type="button" className="rh-refresh" onClick={() => void refresh()}>
            Retry
          </button>
        </div>
      )}

      {data && !error && (
        <>
          <div className="rh-scope-banner">
            <span className="rh-scope-chip">{scopeName}</span>
            <span className="rh-generated">
              Updated {new Date(data.generatedAt).toLocaleString()}
            </span>
          </div>

          {data.totals.activeAssets === 0 ? (
            <div className="rh-state">
              No active media assets in this scope yet. Health metrics will appear once assets are
              uploaded.
            </div>
          ) : (
            <>
              <HealthSection title="Repository totals">
                <HealthMetricCard
                  label="Active assets"
                  value={formatNumber(data.totals.activeAssets)}
                  to="/media-repository"
                  toLabel="Open library"
                />
                <HealthMetricCard
                  label="Storage volume"
                  value={formatBytes(data.totals.storageBytes)}
                  caption="Across active, non-purged assets"
                />
                <HealthMetricCard label="Ready assets" value={formatNumber(data.totals.readyAssets)} />
              </HealthSection>

              <HealthSection
                title="File integrity (fixity)"
                subtitle="SHA-256 verification of stored bytes — distinct from perceptual duplicate hashing."
              >
                <HealthMetricCard
                  label="Checksum coverage"
                  value={`${formatNumber(data.integrity.checksumCovered)} / ${formatNumber(data.totals.activeAssets)}`}
                  tone={coverageTone(data.integrity.checksumCoveragePct, 95, 80) === "good" ? "good" : "warn"}
                >
                  <CoverageMeter
                    label="Checksum coverage"
                    pct={data.integrity.checksumCoveragePct}
                    tone={coverageTone(data.integrity.checksumCoveragePct, 95, 80)}
                  />
                </HealthMetricCard>
                <HealthMetricCard
                  label="Integrity failures"
                  value={formatNumber(data.integrity.failuresTotal)}
                  tone={countTone(data.integrity.failuresTotal, true)}
                  caption={`${data.integrity.mismatch} mismatch · ${data.integrity.missing} missing · ${data.integrity.error} error`}
                  to={data.integrity.failuresTotal > 0 ? mediaLink("integrity_failures") : undefined}
                  toLabel="Review failures"
                />
                <HealthMetricCard
                  label="Open review"
                  value={formatNumber(data.integrity.reviewOpen)}
                  tone={countTone(data.integrity.reviewOpen)}
                  caption={`${data.integrity.reviewAcknowledged} acknowledged`}
                  to={data.integrity.reviewOpen > 0 ? mediaLink("review_open") : undefined}
                  toLabel="Triage now"
                />
                <HealthMetricCard
                  label="Verified"
                  value={formatNumber(data.integrity.verified)}
                  caption={`${data.integrity.pending} pending first check`}
                />
              </HealthSection>

              <HealthSection title="Processing">
                <HealthMetricCard
                  label="Processing failures"
                  value={formatNumber(data.processing.failed)}
                  tone={countTone(data.processing.failed, true)}
                  to={data.processing.failed > 0 ? mediaLink("processing_failed") : undefined}
                  toLabel="View failed"
                />
                <HealthMetricCard
                  label="Still processing"
                  value={formatNumber(data.processing.processing)}
                  caption="Awaiting enrichment / embeddings"
                />
              </HealthSection>

              <HealthSection
                title="Curation"
                subtitle="Human-confirmed metadata makes assets findable beyond AI guesses."
              >
                <HealthMetricCard
                  label="Metadata completeness"
                  value={`${formatNumber(data.curation.curated)} / ${formatNumber(data.totals.readyAssets)}`}
                  tone={coverageTone(data.curation.completenessPct, 80, 50) === "good" ? "good" : "warn"}
                >
                  <CoverageMeter
                    label="Metadata completeness"
                    pct={data.curation.completenessPct}
                    tone={coverageTone(data.curation.completenessPct, 80, 50)}
                  />
                </HealthMetricCard>
                <HealthMetricCard
                  label="Uncurated"
                  value={formatNumber(data.curation.uncurated)}
                  tone={countTone(data.curation.uncurated)}
                  caption="Ready assets without human review"
                  to={data.curation.uncurated > 0 ? mediaLink("uncurated") : undefined}
                  toLabel="Curate"
                />
                <HealthMetricCard
                  label="Unorganized"
                  value={formatNumber(data.curation.unorganized)}
                  tone={countTone(data.curation.unorganized)}
                  caption="Not in any storage folder"
                  to={data.curation.unorganized > 0 ? mediaLink("unorganized") : undefined}
                  toLabel="Organize"
                />
              </HealthSection>

              <HealthSection title="Governance">
                <HealthMetricCard
                  label="Internal-only"
                  value={formatNumber(data.governance.internalOnly)}
                  caption="Not cleared for public publishing"
                  to={data.governance.internalOnly > 0 ? mediaLink("internal_only") : undefined}
                  toLabel="Review visibility"
                />
                <HealthMetricCard
                  label="Duplicate candidates"
                  value={formatNumber(data.governance.duplicateCandidates)}
                  tone={countTone(data.governance.duplicateCandidates)}
                  caption="Flagged by perceptual hash"
                  to={data.governance.duplicateCandidates > 0 ? mediaLink("duplicates") : undefined}
                  toLabel="Review duplicates"
                />
              </HealthSection>

              <HealthSection
                title="Retention"
                subtitle="Soft-deleted assets are purged after the retention window."
              >
                <HealthMetricCard
                  label="Pending purge"
                  value={formatNumber(data.retention.pendingPurge)}
                  caption="Soft-deleted, not yet purged"
                />
                <HealthMetricCard
                  label="Approaching purge"
                  value={formatNumber(data.retention.approachingPurge)}
                  tone={countTone(data.retention.approachingPurge)}
                  caption="Within 7 days of permanent purge"
                />
              </HealthSection>
            </>
          )}
        </>
      )}
    </div>
  );
}

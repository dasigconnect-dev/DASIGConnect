import { useEffect, useMemo, useState, type ReactNode } from "react";
import {
  downloadSystemHealthSnapshot,
  getSystemHealthSummary,
  getSystemHealthTokens,
  initSystemHealthOAuth,
  type BackgroundJobHealth,
  type ExternalServiceHealth,
  type HealthStatus,
  type OperationalMetric,
  type StorageMetric,
  type SystemHealthSummary,
  type TokenStatus,
} from "../../api/systemHealthApi";
import { useToast } from "../../context/ToastContext";
import type { User } from "../../types/auth.types";
import "../../styles/system-health.css";

interface Props {
  user: User;
}

function isAbortError(reason: unknown): boolean {
  const name = (reason as { name?: string; code?: string } | null)?.name;
  const code = (reason as { code?: string } | null)?.code;
  return name === "CanceledError" || name === "AbortError" || code === "ERR_CANCELED";
}

// Module-level cache so re-opening the screen renders instantly and only
// refreshes in the background when stale.
const CACHE_TTL_MS = 60_000;
let cachedSummary: SystemHealthSummary | null = null;
let cachedTokens: TokenStatus[] = [];
let cachedAt = 0;

export default function SystemHealthScreen({ user }: Props) {
  const toast = useToast();
  const [summary, setSummary] = useState<SystemHealthSummary | null>(cachedSummary);
  const [tokens, setTokens] = useState<TokenStatus[]>(cachedTokens);
  const [loading, setLoading] = useState(!cachedSummary);
  const [exporting, setExporting] = useState(false);
  const [busyTokenId, setBusyTokenId] = useState<string | null>(null);

  const canReauthorize = user.role === "admin";

  useEffect(() => {
    if (cachedSummary && Date.now() - cachedAt < CACHE_TTL_MS) return;
    const controller = new AbortController();
    void load(controller.signal, Boolean(cachedSummary));
    return () => controller.abort();
  }, []);

  async function load(signal?: AbortSignal, background = false) {
    if (!background) setLoading(true);

    const [summaryResponse, tokenResponse] = await Promise.allSettled([
      getSystemHealthSummary(signal),
      getSystemHealthTokens(signal),
    ]);

    // This request was superseded (StrictMode remount, fast re-navigation, or an
    // explicit refresh). Leave the loading state and data to the newer load().
    if (signal?.aborted) return;

    if (summaryResponse.status === "fulfilled") {
      setSummary(summaryResponse.value.data);
      cachedSummary = summaryResponse.value.data;
      cachedAt = Date.now();
    }
    if (tokenResponse.status === "fulfilled") {
      setTokens(tokenResponse.value.data);
      cachedTokens = tokenResponse.value.data;
    }
    if (
      summaryResponse.status === "rejected" &&
      !isAbortError(summaryResponse.reason) &&
      !cachedSummary
    ) {
      toast.error("Unable to load system health metrics.");
    }

    if (!background) setLoading(false);
  }

  async function handleExport() {
    setExporting(true);
    try {
      await downloadSystemHealthSnapshot();
      toast.success("Snapshot exported.");
    } catch {
      toast.error("Unable to export snapshot.");
    } finally {
      setExporting(false);
    }
  }

  async function handleReauthorize(token: TokenStatus) {
    setBusyTokenId(token.id);
    try {
      const response = await initSystemHealthOAuth(token.id);
      window.open(response.data.authorizationUrl, "_blank", "noopener,noreferrer");
      toast.info("Facebook OAuth opened in a new tab.");
    } catch {
      toast.error("Unable to start reauthorization.");
    } finally {
      setBusyTokenId(null);
    }
  }

  const counts = useMemo(() => {
    if (!summary) return [];
    return [
      ["Warnings", summary.warningCount],
      ["Unhealthy", summary.unhealthyCount],
      ["Unavailable", summary.unavailableCount],
    ] as const;
  }, [summary]);

  return (
    <main className="sys-screen">
      <header className="screen-header sys-header">
        <div>
          <h1 className="screen-title">System Health</h1>
          <p className="screen-subtitle">Infrastructure, integrations, and background jobs</p>
          {summary && <span className="sys-meta">Updated {formatDate(summary.generatedAt)}</span>}
        </div>
        <div className="sys-toolbar">
          <button type="button" className="btn-secondary" onClick={() => void load()} disabled={loading}>
            <i className={loading ? "ti ti-loader-2 sys-spin" : "ti ti-refresh"} aria-hidden="true" />
            Refresh
          </button>
          <button type="button" className="btn-secondary" onClick={() => void handleExport()} disabled={exporting}>
            <i className={exporting ? "ti ti-loader-2 sys-spin" : "ti ti-download"} aria-hidden="true" />
            Export
          </button>
        </div>
      </header>

      {loading && !summary ? (
        <>
          <p className="sys-loading-label">
            <i className="ti ti-loader-2 sys-spin" aria-hidden="true" />
            Loading system health…
          </p>
          <div className="sys-loading">
            <div className="sys-skeleton" />
            <div className="sys-skeleton" />
            <div className="sys-skeleton" />
          </div>
        </>
      ) : summary ? (
        <>
          <section className={`sys-overview sys-status-${summary.overallStatus.toLowerCase()}`}>
            <div className="sys-overview-status">
              <span className={`sys-badge sys-badge-${summary.overallStatus.toLowerCase()}`}>
                {labelStatus(summary.overallStatus)}
              </span>
              <strong>{labelStatus(summary.overallStatus)}</strong>
            </div>
            <div className="sys-overview-counts">
              {counts.map(([label, value]) => (
                <div key={label}>
                  <span>{label}</span>
                  <strong>{value}</strong>
                </div>
              ))}
            </div>
          </section>
          <Section title="Operational · last 30 days">
            <div className="sys-grid sys-grid-3">
              {summary.operationalMetrics.map((item) => (
                <MetricCard item={item} key={item.key} />
              ))}
            </div>
          </Section>

          <Section title="Storage">
            <div className="sys-grid sys-grid-2">
              {summary.storage.map((item) => (
                <StorageCard item={item} key={item.name} />
              ))}
            </div>
          </Section>

          <Section title="Integrations">
            <div className="sys-grid sys-grid-4">
              {summary.externalServices.map((item) => (
                <ServiceCard item={item} key={item.service} />
              ))}
            </div>
          </Section>

          <Section title="Facebook Token">
            <TokenTable
              tokens={tokens}
              busyTokenId={busyTokenId}
              canReauthorize={canReauthorize}
              onReauthorize={handleReauthorize}
            />
          </Section>

          <Section title="Background Jobs">
            <JobTable jobs={summary.backgroundJobs} />
          </Section>

          
        </>
      ) : (
        <div className="sys-state">
          <i className="ti ti-alert-triangle" aria-hidden="true" />
          <p>System health data is unavailable.</p>
        </div>
      )}
    </main>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="sys-section">
      <div className="sys-section-label">
        <h2>{title}</h2>
      </div>
      {children}
    </section>
  );
}

function StorageCard({ item }: { item: StorageMetric }) {
  return (
    <article className="sys-card">
      <div className="sys-card-top">
        <h3>{item.name}</h3>
        <StatusBadge status={item.status} />
      </div>
      <div className="sys-card-value">
        {formatBytes(item.usedBytes)} / {formatBytes(item.limitBytes)}
      </div>
      <div className={`sys-meter sys-meter-${item.status.toLowerCase()}`} aria-label={`${item.usedPercent}% used`}>
        <span style={{ width: `${Math.min(item.usedPercent, 100)}%` }} />
      </div>
      <small>{item.usedPercent}%</small>
    </article>
  );
}

function ServiceCard({ item }: { item: ExternalServiceHealth }) {
  return (
    <article className="sys-card">
      <div className="sys-card-top">
        <span className="sys-chip">
          <i className={serviceIcon(item.service)} aria-hidden="true" />
        </span>
        <StatusBadge status={item.status} />
      </div>
      <h3>{item.service}</h3>
      <small>{item.detail}</small>
      {item.expiresAt && <small>Expires {formatDate(item.expiresAt)}</small>}
    </article>
  );
}

function MetricCard({ item }: { item: OperationalMetric }) {
  const unavailable = item.status === "UNAVAILABLE";
  const noActivity = isNoActivityMetric(item);
  return (
    <article
      className={`sys-card sys-metric sys-metric-${item.status.toLowerCase()}`}
      title={item.detail || undefined}
    >
      <div className="sys-card-top">
        <span className="sys-chip">
          <i className={metricIcon(item.key)} aria-hidden="true" />
        </span>
        <StatusBadge status={item.status} />
      </div>
      <h3>{item.label}</h3>
      <div className="sys-card-value">
        {unavailable ? "No data" : noActivity ? "No activity" : formatMetricValue(item)}
      </div>
      <small>{sampleLabel(item)}</small>
    </article>
  );
}

function JobTable({ jobs }: { jobs: BackgroundJobHealth[] }) {
  return (
    <div className="sys-table-wrap">
      <table className="sys-table">
        <thead>
          <tr>
            <th>Job</th>
            <th>Status</th>
            <th>Last Run</th>
            <th>Last Success</th>
            <th>Duration</th>
          </tr>
        </thead>
        <tbody>
          {jobs.map((job) => (
            <tr key={job.jobName}>
              <td>{formatJobName(job.jobName)}</td>
              <td><StatusBadge status={job.status} /></td>
              <td>{formatDate(job.lastStartedAt)}</td>
              <td>{formatDate(job.lastSuccessAt)}</td>
              <td>{job.lastDurationMs == null ? "—" : `${job.lastDurationMs} ms`}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function TokenTable({
  tokens,
  busyTokenId,
  canReauthorize,
  onReauthorize,
}: {
  tokens: TokenStatus[];
  busyTokenId: string | null;
  canReauthorize: boolean;
  onReauthorize: (token: TokenStatus) => void;
}) {
  if (tokens.length === 0) {
    return <div className="sys-state sys-state-inline">No Facebook Page Access Token configured.</div>;
  }
  return (
    <div className="sys-table-wrap">
      <table className="sys-table">
        <thead>
          <tr>
            <th>Page</th>
            <th>Status</th>
            <th>Expires</th>
            <th>Action</th>
          </tr>
        </thead>
        <tbody>
          {tokens.map((token) => (
            <tr key={token.id}>
              <td>Page ····{token.pageId.slice(-4) || "----"}</td>
              <td><StatusBadge status={tokenStatusToHealth(token.tokenStatus)} /></td>
              <td>{formatDate(token.expiresAt)}</td>
              <td>
                <button
                  type="button"
                  className="btn-secondary sys-btn-sm"
                  disabled={!canReauthorize || busyTokenId === token.id}
                  onClick={() => onReauthorize(token)}
                >
                  <i className={busyTokenId === token.id ? "ti ti-loader-2 sys-spin" : "ti ti-refresh"} aria-hidden="true" />
                  Reauthorize
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function StatusBadge({ status }: { status: HealthStatus }) {
  return <span className={`sys-badge sys-badge-${status.toLowerCase()}`}>{labelStatus(status)}</span>;
}

function tokenStatusToHealth(status: TokenStatus["tokenStatus"]): HealthStatus {
  if (status === "ACTIVE") return "HEALTHY";
  if (status === "EXPIRING") return "WARNING";
  return "UNHEALTHY";
}

function labelStatus(status: HealthStatus) {
  return status.toLowerCase().replace("_", " ");
}

function formatBytes(bytes: number) {
  if (!Number.isFinite(bytes) || bytes <= 0) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${value.toFixed(value >= 10 ? 0 : 1)} ${units[unit]}`;
}

function formatDate(value: string | null) {
  if (!value) return "—";
  return new Date(value).toLocaleString("en-PH", {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
    hour12: true,
  });
}

function formatMetricValue(item: OperationalMetric) {
  if (item.unit === "percent") return `${item.value}%`;
  if (item.unit === "hours") return `${item.value}h`;
  return `${item.value}`;
}

function metricIcon(key: string) {
  switch (key) {
    case "approval_turnaround_time":
      return "ti ti-hourglass";
    case "edit_approve_rate":
      return "ti ti-edit";
    case "manual_fallback_resolution_rate":
      return "ti ti-tool";
    case "publish_success_rate":
      return "ti ti-circle-check";
    case "live_event_fast_track_volume":
      return "ti ti-bolt";
    default:
      return "ti ti-chart-bar";
  }
}

function serviceIcon(service: string) {
  const name = service.toLowerCase();
  if (name.includes("claude") || name.includes("anthropic")) return "ti ti-sparkles";
  if (name.includes("voyage")) return "ti ti-vector";
  if (name.includes("email") || name.includes("smtp") || name.includes("mail")) return "ti ti-mail";
  if (name.includes("facebook")) return "ti ti-brand-facebook";
  return "ti ti-plug-connected";
}

function sampleLabel(item: OperationalMetric) {
  if (item.status === "UNAVAILABLE") return "No current sample";
  if (isNoActivityMetric(item)) return "No records in period";
  if (item.key === "live_event_fast_track_volume") {
    return `${item.sampleSize} fast-track submission${item.sampleSize === 1 ? "" : "s"}`;
  }
  return `${item.sampleSize} record${item.sampleSize === 1 ? "" : "s"}`;
}

function isNoActivityMetric(item: OperationalMetric) {
  return item.sampleSize === 0 && item.key !== "live_event_fast_track_volume";
}

function formatJobName(jobName: string) {
  if (!/[a-z][A-Z]/.test(jobName) && !jobName.endsWith("Job")) return jobName;
  return jobName.replace(/([a-z])([A-Z])/g, "$1 $2").replace(/\s?Job$/, "");
}

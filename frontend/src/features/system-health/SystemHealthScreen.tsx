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

export default function SystemHealthScreen({ user }: Props) {
  const toast = useToast();
  const [summary, setSummary] = useState<SystemHealthSummary | null>(null);
  const [tokens, setTokens] = useState<TokenStatus[]>([]);
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);
  const [busyTokenId, setBusyTokenId] = useState<string | null>(null);
  const [error, setError] = useState("");

  const isSuperAdmin = user.role === "super_administrator";

  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
  }, []);

  async function load(signal?: AbortSignal) {
    setLoading(true);
    setError("");
    try {
      const [summaryResponse, tokenResponse] = await Promise.allSettled([
        getSystemHealthSummary(signal),
        getSystemHealthTokens(signal),
      ]);

      if (summaryResponse.status === "fulfilled") {
        setSummary(summaryResponse.value.data);
      } else {
        setSummary(null);
      }

      if (tokenResponse.status === "fulfilled") {
        setTokens(tokenResponse.value.data);
      } else {
        setTokens([]);
      }

      if (summaryResponse.status === "rejected") {
        setError("Unable to load system health metrics.");
      }
    } finally {
      setLoading(false);
    }
  }

  async function handleExport() {
    setExporting(true);
    try {
      await downloadSystemHealthSnapshot();
      toast.success("System health snapshot exported.");
    } catch {
      toast.error("Unable to export system health snapshot.");
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
      toast.error("Unable to start Facebook reauthorization.");
    } finally {
      setBusyTokenId(null);
    }
  }

  const topLine = useMemo(() => {
    if (!summary) return [];
    return [
      ["Warnings", summary.warningCount],
      ["Unhealthy", summary.unhealthyCount],
      ["Unavailable", summary.unavailableCount],
    ] as const;
  }, [summary]);

  return (
    <main className="sys-health-screen">
      <header className="sys-health-header">
        <div>
          <h1>System Health</h1>
          <p>Infrastructure status, API health, scheduled jobs, and operational analytics.</p>
        </div>
        <div className="sys-health-actions">
          <button type="button" className="btn-secondary" onClick={() => void load()} disabled={loading}>
            <i className="ti ti-refresh" aria-hidden="true"></i>
            Refresh
          </button>
          <button type="button" className="btn-primary" onClick={() => void handleExport()} disabled={exporting}>
            <i className={exporting ? "ti ti-loader-2 sys-spin" : "ti ti-download"} aria-hidden="true"></i>
            Export Snapshot
          </button>
        </div>
      </header>

      {error && (
        <div className="sys-alert sys-alert-error" role="alert">
          <i className="ti ti-alert-circle" aria-hidden="true"></i>
          <span>{error}</span>
        </div>
      )}

      {loading && !summary ? (
        <div className="sys-loading">
          <div className="spinner-ring"></div>
          <span>Loading system health...</span>
        </div>
      ) : summary ? (
        <>
          <section className={`sys-overview sys-status-${summary.overallStatus.toLowerCase()}`}>
            <div>
              <span className="sys-eyebrow">Overall Status</span>
              <strong>{labelStatus(summary.overallStatus)}</strong>
              <p>Last refreshed {formatDate(summary.generatedAt)}</p>
            </div>
            <div className="sys-overview-metrics">
              {topLine.map(([label, value]) => (
                <div key={label}>
                  <span>{label}</span>
                  <strong>{value}</strong>
                </div>
              ))}
            </div>
          </section>

          <Section title="Storage Capacity" icon="ti ti-database">
            <div className="sys-grid sys-grid-2">
              {summary.storage.map((item) => (
                <StorageCard item={item} key={item.name} />
              ))}
            </div>
          </Section>

          <Section title="API Connection Status" icon="ti ti-plug-connected">
            <div className="sys-grid sys-grid-4">
              {summary.externalServices.map((item) => (
                <ServiceCard item={item} key={item.service} />
              ))}
            </div>
          </Section>

          <Section title="Facebook Token Reauthorization" icon="ti ti-brand-facebook">
            <TokenTable
              tokens={tokens}
              busyTokenId={busyTokenId}
              canReauthorize={isSuperAdmin || user.role === "administrator"}
              onReauthorize={handleReauthorize}
            />
          </Section>

          <Section title="Background Job Health" icon="ti ti-clock-cog">
            <JobTable jobs={summary.backgroundJobs} />
          </Section>

          <Section title="Operational Health Metrics" icon="ti ti-activity">
            <div className="sys-grid sys-grid-5">
              {summary.operationalMetrics.map((item) => (
                <MetricCard item={item} key={item.key} />
              ))}
            </div>
          </Section>
        </>
      ) : (
        <div className="sys-empty">
          <i className="ti ti-alert-triangle" aria-hidden="true"></i>
          <span>System health data is unavailable.</span>
        </div>
      )}
    </main>
  );
}

function Section({ title, icon, children }: { title: string; icon: string; children: ReactNode }) {
  return (
    <section className="sys-section">
      <div className="sys-section-header">
        <h2>
          <i className={icon} aria-hidden="true"></i>
          {title}
        </h2>
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
      <div className="sys-storage-value">{formatBytes(item.usedBytes)} / {formatBytes(item.limitBytes)}</div>
      <div className="sys-meter" aria-label={`${item.usedPercent}% used`}>
        <span style={{ width: `${Math.min(item.usedPercent, 100)}%` }}></span>
      </div>
      <p>{item.usedPercent}% used. Warning threshold is {item.warningThresholdPercent}%.</p>
    </article>
  );
}

function ServiceCard({ item }: { item: ExternalServiceHealth }) {
  return (
    <article className="sys-card">
      <div className="sys-card-top">
        <h3>{item.service}</h3>
        <StatusBadge status={item.status} />
      </div>
      <p>{item.detail}</p>
      {item.expiresAt && <small>Expires {formatDate(item.expiresAt)}</small>}
    </article>
  );
}

function MetricCard({ item }: { item: OperationalMetric }) {
  return (
    <article className="sys-card sys-metric-card">
      <div className="sys-card-top">
        <h3>{item.label}</h3>
        <StatusBadge status={item.status} />
      </div>
      <strong>{formatMetricValue(item)}</strong>
      <p>{item.detail}</p>
      <small>Sample size: {item.sampleSize}</small>
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
            <th>Last Started</th>
            <th>Last Success</th>
            <th>Duration</th>
            <th>Detail</th>
          </tr>
        </thead>
        <tbody>
          {jobs.map((job) => (
            <tr key={job.jobName}>
              <td>{job.jobName}</td>
              <td><StatusBadge status={job.status} /></td>
              <td>{formatDate(job.lastStartedAt)}</td>
              <td>{formatDate(job.lastSuccessAt)}</td>
              <td>{job.lastDurationMs == null ? "-" : `${job.lastDurationMs} ms`}</td>
              <td>{job.lastError || job.detail}</td>
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
    return <div className="sys-empty-inline">No Facebook Page Access Token is configured.</div>;
  }
  return (
    <div className="sys-table-wrap">
      <table className="sys-table">
        <thead>
          <tr>
            <th>Page ID</th>
            <th>Status</th>
            <th>Expires</th>
            <th>Last Validated</th>
            <th>Action</th>
          </tr>
        </thead>
        <tbody>
          {tokens.map((token) => (
            <tr key={token.id}>
              <td className="sys-mono">{token.pageId}</td>
              <td><StatusBadge status={tokenStatusToHealth(token.tokenStatus)} /></td>
              <td>{formatDate(token.expiresAt)}</td>
              <td>{formatDate(token.lastValidatedAt)}</td>
              <td>
                <button
                  type="button"
                  className="sys-icon-btn"
                  disabled={!canReauthorize || busyTokenId === token.id}
                  onClick={() => onReauthorize(token)}
                  title="Reauthorize Facebook token"
                >
                  <i className={busyTokenId === token.id ? "ti ti-loader-2 sys-spin" : "ti ti-refresh"} aria-hidden="true"></i>
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
  if (!value) return "-";
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

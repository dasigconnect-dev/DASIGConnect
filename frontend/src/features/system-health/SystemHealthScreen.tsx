import { useEffect, useMemo, useState, type ReactNode } from "react";
import {
  downloadSystemHealthSnapshot,
  getSystemHealthSummary,
  getSystemHealthTokens,
  initSystemHealthOAuth,
  runSystemHealthJob,
  type BackgroundJobHealth,
  type ExternalServiceHealth,
  type HealthStatus,
  type OperationalMetric,
  type StorageMetric,
  type SystemHealthSummary,
  type TokenStatus,
} from "../../api/systemHealthApi";
import { useToast } from "../../context/ToastContext";
import { registerAppCacheReset } from "../../lib/appCache";
import type { User } from "../../types/auth.types";
import "../../styles/system-health.css";
import "../../styles/dasig-loader.css";

interface Props {
  user: User;
}

type SystemHealthTab = "jobs" | "integrations" | "performance" | "storage";

function isAbortError(reason: unknown): boolean {
  const name = (reason as { name?: string; code?: string } | null)?.name;
  const code = (reason as { code?: string } | null)?.code;
  return name === "CanceledError" || name === "AbortError" || code === "ERR_CANCELED";
}

const CACHE_TTL_MS = 60_000;
let cachedSummary: SystemHealthSummary | null = null;
let cachedTokens: TokenStatus[] = [];
let cachedAt = 0;
registerAppCacheReset(() => {
  cachedSummary = null;
  cachedTokens = [];
  cachedAt = 0;
});

export default function SystemHealthScreen({ user }: Props) {
  const toast = useToast();
  const [summary, setSummary] = useState<SystemHealthSummary | null>(cachedSummary);
  const [tokens, setTokens] = useState<TokenStatus[]>(cachedTokens);
  const [loading, setLoading] = useState(!cachedSummary);
  const [exporting, setExporting] = useState(false);
  const [runningJobKey, setRunningJobKey] = useState<string | null>(null);
  const [busyTokenId, setBusyTokenId] = useState<string | null>(null);

  // Active top-level tab (jobs | integrations | performance | storage)
  const [activeTab, setActiveTab] = useState<SystemHealthTab>(() => {
    const hash = window.location.hash.replace("#", "");
    if (hash === "integrations" || hash === "performance" || hash === "storage" || hash === "jobs") {
      return hash as SystemHealthTab;
    }
    return "performance";
  });

  // Background Jobs search & status filter
  const [jobSearch, setJobSearch] = useState("");
  const [jobStatusFilter, setJobStatusFilter] = useState<string>("ALL");

  const canReauthorize = user.role === "admin";

  useEffect(() => {
    if (cachedSummary && Date.now() - cachedAt < CACHE_TTL_MS) return;
    const controller = new AbortController();
    void load(controller.signal, Boolean(cachedSummary));
    return () => controller.abort();
  }, []);

  function handleTabChange(tab: SystemHealthTab) {
    setActiveTab(tab);
    window.location.hash = tab;
  }

  async function load(signal?: AbortSignal, background = false) {
    if (!background) setLoading(true);

    const [summaryResponse, tokenResponse] = await Promise.allSettled([
      getSystemHealthSummary(signal),
      getSystemHealthTokens(signal),
    ]);

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

  async function handleRunJob(job: BackgroundJobHealth) {
    setRunningJobKey(job.key);
    try {
      await runSystemHealthJob(job.key);
      await load(undefined, true);
      toast.success(`Ran ${job.jobName}.`);
    } catch {
      toast.error(`Unable to run ${job.jobName}.`);
    } finally {
      setRunningJobKey(null);
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

  const executiveCards = useMemo(() => {
    if (!summary) return [];
    return [
      {
        id: "overall",
        icon: overallStatusIcon(summary.overallStatus),
        label: "OVERALL STATUS",
        value: labelStatus(summary.overallStatus),
        sub: overallStatusHeadline(summary.overallStatus),
        status: summary.overallStatus,
      },
      {
        id: "warnings",
        icon: "ti ti-alert-triangle",
        label: "WARNINGS",
        value: String(summary.warningCount),
        sub: "Thresholds nearing limit",
        status: (summary.warningCount > 0 ? "WARNING" : "HEALTHY") as HealthStatus,
      },
      {
        id: "unhealthy",
        icon: "ti ti-alert-circle",
        label: "UNHEALTHY",
        value: String(summary.unhealthyCount),
        sub: summary.unhealthyCount > 0 ? "Failing routines or APIs" : "No critical failures",
        status: (summary.unhealthyCount > 0 ? "UNHEALTHY" : "HEALTHY") as HealthStatus,
      },
      {
        id: "unavailable",
        icon: "ti ti-plug-connected-x",
        label: "UNAVAILABLE",
        value: String(summary.unavailableCount),
        sub: "Offline or unconfigured",
        status: (summary.unavailableCount > 0 ? "WARNING" : "HEALTHY") as HealthStatus,
      },
      {
        id: "jobs",
        icon: "ti ti-cpu",
        label: "BACKGROUND JOBS",
        value: `${summary.backgroundJobs.length} Jobs`,
        sub: "Automated cron routines",
        status: "SCHEDULED" as HealthStatus,
      },
    ];
  }, [summary]);

  const tabAlerts = useMemo(() => {
    if (!summary) return { jobs: false, integrations: false, performance: false, storage: false };
    const hasJobAlert = summary.backgroundJobs.some((j) => j.status === "UNHEALTHY" || j.status === "WARNING");
    const hasIntegrationAlert =
      summary.externalServices.some((s) => s.status === "UNHEALTHY" || s.status === "WARNING") ||
      tokens.some((t) => t.tokenStatus === "EXPIRED" || t.tokenStatus === "EXPIRING" || t.tokenStatus === "INVALID");
    const hasPerfAlert = summary.operationalMetrics.some((m) => m.status === "UNHEALTHY" || m.status === "WARNING");
    const hasStorageAlert = summary.storage.some((st) => st.status === "UNHEALTHY" || st.status === "WARNING");

    return {
      jobs: hasJobAlert,
      integrations: hasIntegrationAlert,
      performance: hasPerfAlert,
      storage: hasStorageAlert,
    };
  }, [summary, tokens]);

  const filteredJobs = useMemo(() => {
    if (!summary) return [];
    return summary.backgroundJobs.filter((job) => {
      const formatted = formatJobName(job.jobName).toLowerCase();
      const raw = job.jobName.toLowerCase();
      const query = jobSearch.trim().toLowerCase();
      const matchesQuery = !query || formatted.includes(query) || raw.includes(query);

      const matchesStatus =
        jobStatusFilter === "ALL" ||
        job.status.toUpperCase() === jobStatusFilter.toUpperCase();

      return matchesQuery && matchesStatus;
    });
  }, [summary, jobSearch, jobStatusFilter]);

  const jobCounts = useMemo(() => {
    if (!summary) return { ALL: 0, HEALTHY: 0, SCHEDULED: 0, WARNING: 0, UNHEALTHY: 0 };
    const res: Record<string, number> = { ALL: summary.backgroundJobs.length, HEALTHY: 0, SCHEDULED: 0, WARNING: 0, UNHEALTHY: 0 };
    for (const job of summary.backgroundJobs) {
      const st = job.status.toUpperCase();
      if (res[st] !== undefined) {
        res[st] += 1;
      }
    }
    return res;
  }, [summary]);

  if (loading && !summary) {
    return (
      <div id="screen-system-health" style={{ background: "var(--d-bg)" }}>
        <div className="dash-body sys-page">
          <div className="dash-view-header">
            <div>
              <h1 className="dash-view-title">System Health</h1>
              <p className="dash-view-desc">Infrastructure, integrations, capacity telemetry, and background jobs</p>
            </div>
          </div>
          <div className="sys-cardless-loader" aria-live="polite">
            <div className="dc-dot-triangle-container">
              <div className="loader-dots" />
              <div className="dc-dot-triangle-label">
                Loading System Health
                <span className="dc-dot-triangle-label-dots">
                  <span className="dc-dot-triangle-dot-char">.</span>
                  <span className="dc-dot-triangle-dot-char">.</span>
                  <span className="dc-dot-triangle-dot-char">.</span>
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div id="screen-system-health" style={{ background: "var(--d-bg)" }}>
      <div className="dash-body sys-page">
        {/* Page Header */}
        <div className="dash-view-header sys-header">
          <div>
            <h1 className="dash-view-title">System Health</h1>
            <p className="dash-view-desc">Infrastructure, integrations, capacity telemetry, and background jobs</p>
            {summary && (
              <div className="sys-meta-row">
                <span className="sys-meta-pill">
                  <i className="ti ti-clock" aria-hidden="true" />
                  Updated {formatDate(summary.generatedAt)}
                </span>
                <span className="sys-meta-pill">
                  <i className="ti ti-shield-check" aria-hidden="true" />
                  Diagnostics Active
                </span>
              </div>
            )}
          </div>
          <div className="sys-toolbar">
            <button
              type="button"
              className="notif-btn notif-btn-ghost"
              onClick={() => void load()}
              disabled={loading}
              title="Refresh system health metrics"
            >
              <i className={loading ? "ti ti-loader-2 sys-spin" : "ti ti-refresh"} aria-hidden="true" />
              <span>{loading ? "Refreshing..." : "Refresh"}</span>
            </button>
            <button
              type="button"
              className="notif-btn notif-btn-ghost"
              onClick={() => void handleExport()}
              disabled={exporting}
              title="Export health snapshot CSV"
            >
              <i className={exporting ? "ti ti-loader-2 sys-spin" : "ti ti-download"} aria-hidden="true" />
              <span>{exporting ? "Exporting..." : "Export Snapshot"}</span>
            </button>
          </div>
        </div>

        {summary ? (
          <>
            {/* Top Executive Summary Strip */}
            <div className="sys-strip-grid" aria-label="System status summary">
              {executiveCards.map((card) => (
                <div className="sys-strip-card" key={card.id}>
                  <div className="sys-strip-top">
                    <div className="sys-strip-icon-box">
                      <i className={card.icon} aria-hidden="true" />
                    </div>
                    <StatusBadge status={card.status} />
                  </div>
                  <div className="sys-strip-label">{card.label}</div>
                  <div className="sys-strip-val">{card.value}</div>
                  <div className="sys-strip-sub">{card.sub}</div>
                </div>
              ))}
            </div>

            {/* ── 4-Tab Segmented Navigation Bar (Separated Performance & Storage) ── */}
            <div className="sys-main-tabs-wrapper">
              <div className="sys-main-tabs" role="tablist" aria-label="System Health Navigation">
                <button
                  type="button"
                  role="tab"
                  className={`sys-main-tab${activeTab === "performance" ? " is-active" : ""}`}
                  onClick={() => handleTabChange("performance")}
                  aria-selected={activeTab === "performance"}
                >
                  <i className="ti ti-chart-dots" aria-hidden="true" />
                  <span>Operational Performance</span>
                  <span className={`sys-main-tab-badge${tabAlerts.performance ? " has-alert" : ""}`}>
                    {tabAlerts.performance && <span className="sys-tab-alert-dot" aria-hidden="true" />}
                    {summary.operationalMetrics.length}
                  </span>
                </button>

                <button
                  type="button"
                  role="tab"
                  className={`sys-main-tab${activeTab === "storage" ? " is-active" : ""}`}
                  onClick={() => handleTabChange("storage")}
                  aria-selected={activeTab === "storage"}
                >
                  <i className="ti ti-database" aria-hidden="true" />
                  <span>Storage & Capacity</span>
                  <span className={`sys-main-tab-badge${tabAlerts.storage ? " has-alert" : ""}`}>
                    {tabAlerts.storage && <span className="sys-tab-alert-dot" aria-hidden="true" />}
                    {summary.storage.length}
                  </span>
                </button>

                <button
                  type="button"
                  role="tab"
                  className={`sys-main-tab${activeTab === "integrations" ? " is-active" : ""}`}
                  onClick={() => handleTabChange("integrations")}
                  aria-selected={activeTab === "integrations"}
                >
                  <i className="ti ti-plug-connected" aria-hidden="true" />
                  <span>Integrations & Tokens</span>
                  <span className={`sys-main-tab-badge${tabAlerts.integrations ? " has-alert" : ""}`}>
                    {tabAlerts.integrations && <span className="sys-tab-alert-dot" aria-hidden="true" />}
                    {summary.externalServices.length + tokens.length}
                  </span>
                </button>

                <button
                  type="button"
                  role="tab"
                  className={`sys-main-tab${activeTab === "jobs" ? " is-active" : ""}`}
                  onClick={() => handleTabChange("jobs")}
                  aria-selected={activeTab === "jobs"}
                >
                  <i className="ti ti-cpu" aria-hidden="true" />
                  <span>Background Jobs</span>
                  <span className={`sys-main-tab-badge${tabAlerts.jobs ? " has-alert" : ""}`}>
                    {tabAlerts.jobs && <span className="sys-tab-alert-dot" aria-hidden="true" />}
                    {summary.backgroundJobs.length}
                  </span>
                </button>
              </div>
            </div>

            {/* ── TAB 1: Operational Performance (Expanded, Large High-Fidelity Cards) ── */}
            {activeTab === "performance" && (
              <div className="sys-tab-content">
                <Section
                  title="Operational Performance Telemetry · Last 30 Days"
                  icon="ti ti-chart-dots"
                  subtitle="Key operational benchmarks across moderation, editorial approval, publication throughput, and fast-track routing"
                >
                  <div className="sys-perf-large-grid">
                    {summary.operationalMetrics.map((item) => (
                      <LargePerformanceCard item={item} key={item.key} />
                    ))}
                  </div>
                </Section>
              </div>
            )}

            {/* ── TAB 2: Storage & Capacity ── */}
            {activeTab === "storage" && (
              <div className="sys-tab-content">
                <Section
                  title="Database & Storage Asset Capacity"
                  icon="ti ti-database"
                  subtitle="Current utilization metrics and threshold limits across Postgres database storage and Cloudflare R2 media storage"
                >
                  <div className="sys-storage-grid">
                    {summary.storage.map((item) => (
                      <StorageCard item={item} key={item.name} />
                    ))}
                  </div>
                </Section>
              </div>
            )}

            {/* ── TAB 3: Integrations & Tokens ── */}
            {activeTab === "integrations" && (
              <div className="sys-tab-content sys-grouped-content">
                {/* Facebook Page Tokens */}
                <Section
                  title="Facebook Access Tokens"
                  icon="ti ti-brand-facebook"
                  subtitle="OAuth page access tokens used for automatic publication dispatch and social engagement telemetry"
                >
                  <TokenTable
                    tokens={tokens}
                    busyTokenId={busyTokenId}
                    canReauthorize={canReauthorize}
                    onReauthorize={handleReauthorize}
                  />
                </Section>

                {/* External API Services */}
                <Section
                  title="External Integrations & Services"
                  icon="ti ti-plug-connected"
                  subtitle="Reachability and probe status of external third-party APIs connected to the DASIGConnect platform"
                >
                  <div className="sys-services-grid">
                    {summary.externalServices.map((item) => (
                      <ServiceCard item={item} key={item.service} />
                    ))}
                  </div>
                </Section>
              </div>
            )}

            {/* ── TAB 4: Scheduled Background Jobs ── */}
            {activeTab === "jobs" && (
              <div className="sys-tab-content">
                <div className="card-wrap sys-jobs-card">
                  {/* Jobs Toolbar: Status Filter Tabs & Search */}
                  <div className="dash-card-toolbar sys-jobs-toolbar">
                    <div className="im-status-tabs" role="group" aria-label="Filter background jobs by status">
                      {(["ALL", "HEALTHY", "SCHEDULED", "WARNING", "UNHEALTHY"] as const).map((st) => {
                        const count = jobCounts[st] ?? 0;
                        if (st !== "ALL" && count === 0) return null;
                        return (
                          <button
                            key={st}
                            type="button"
                            className={`im-status-tab${jobStatusFilter === st ? " is-active" : ""}`}
                            onClick={() => setJobStatusFilter(st)}
                            aria-pressed={jobStatusFilter === st}
                          >
                            {st === "ALL" ? "All Jobs" : labelStatus(st as HealthStatus)}
                            <span className="im-status-tab-count">{count}</span>
                          </button>
                        );
                      })}
                    </div>

                    <div className="im-search-wrap">
                      <i className="ti ti-search im-search-icon" aria-hidden="true" />
                      <input
                        className="im-search-input"
                        type="search"
                        placeholder="Search jobs..."
                        value={jobSearch}
                        onChange={(e) => setJobSearch(e.target.value)}
                        aria-label="Search background jobs"
                      />
                    </div>
                  </div>

                  <JobTable
                    jobs={filteredJobs}
                    runningJobKey={runningJobKey}
                    onRun={(job) => void handleRunJob(job)}
                  />
                </div>
              </div>
            )}
          </>
        ) : (
          <div className="card-wrap sys-error-card">
            <i className="ti ti-alert-circle sys-error-icon" aria-hidden="true" />
            <h3>System Health Unavailable</h3>
            <p>Unable to retrieve real-time system health metrics from the backend.</p>
            <button type="button" className="notif-btn notif-btn-ghost" onClick={() => void load()}>
              <i className="ti ti-refresh" aria-hidden="true" />
              <span>Retry Connection</span>
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

function Section({
  title,
  subtitle,
  icon,
  children,
}: {
  title: string;
  subtitle?: string;
  icon?: string;
  children: ReactNode;
}) {
  return (
    <div className="sys-section-block">
      <div className="sys-section-title-row">
        {icon && <i className={`${icon} sys-section-icon`} aria-hidden="true" />}
        <div>
          <h2 className="sys-section-title">{title}</h2>
          {subtitle && <p className="sys-section-subtitle">{subtitle}</p>}
        </div>
      </div>
      {children}
    </div>
  );
}

/* ---------- Large Informative Performance Graph Card ---------- */
function LargePerformanceCard({ item }: { item: OperationalMetric }) {
  const unavailable = item.status === "UNAVAILABLE";
  const noActivity = isNoActivityMetric(item);

  return (
    <div className={`card-wrap sys-perf-large-card sys-perf-${item.status.toLowerCase()}`}>
      <div className="sys-perf-top-row">
        <div className="sys-perf-title-group">
          <span className="sys-card-icon-box">
            <i className={metricIcon(item.key)} aria-hidden="true" />
          </span>
          <div>
            <h3 className="sys-perf-name">{item.label}</h3>
            <span className="sys-perf-meta-sub">{sampleLabel(item)}</span>
          </div>
        </div>
        <StatusBadge status={item.status} />
      </div>

      <div className="sys-perf-main-body">
        <div className="sys-perf-hero-value">
          <span className="sys-perf-num">
            {unavailable ? "No data" : noActivity ? "No activity" : formatMetricValue(item)}
          </span>
          <span className="sys-perf-target-badge">{getMetricBenchmark(item)}</span>
        </div>

        {/* Detailed High-Resolution Graph */}
        {!unavailable && !noActivity && (
          <div className="sys-perf-graph-container">
            <HighResMetricGraph item={item} />
          </div>
        )}
      </div>

      <div className="sys-perf-footer-note">
        <i className="ti ti-info-circle" aria-hidden="true" />
        <span>{getMetricExplanation(item)}</span>
      </div>
    </div>
  );
}

function HighResMetricGraph({ item }: { item: OperationalMetric }) {
  if (item.key === "approval_turnaround_time") {
    // 0h to 48h SLA Gauge
    const maxScale = 48;
    const value = Math.min(Math.max(item.value, 0), maxScale);
    const percent = (value / maxScale) * 100;
    const targetPercent = (24 / maxScale) * 100;

    return (
      <div className="sys-hires-sla-wrap">
        <div className="sys-hires-sla-bar-bg">
          {/* Healthy zone 0 to 24h */}
          <div className="sys-sla-zone sys-sla-zone-healthy" style={{ width: "50%" }} />
          {/* Warning zone 24 to 36h */}
          <div className="sys-sla-zone sys-sla-zone-warning" style={{ width: "25%" }} />
          {/* Critical zone 36 to 48h */}
          <div className="sys-sla-zone sys-sla-zone-critical" style={{ width: "25%" }} />

          {/* Current Indicator Marker */}
          <div className="sys-sla-needle" style={{ left: `${percent}%` }}>
            <span className="sys-sla-needle-badge">{item.value.toFixed(1)}h</span>
            <div className="sys-sla-needle-pin" />
          </div>

          {/* Target Pinned Line */}
          <div className="sys-sla-target-marker" style={{ left: `${targetPercent}%` }}>
            <span className="sys-sla-target-tag">Target SLA (24h)</span>
          </div>
        </div>

        <div className="sys-hires-scale-labels">
          <span>0h (Instant)</span>
          <span>12h</span>
          <span className="sys-bold-label">24h SLA</span>
          <span>36h</span>
          <span>48h+</span>
        </div>
      </div>
    );
  }

  if (item.key === "edit_approve_rate") {
    const percent = Math.min(Math.max(item.value, 0), 100);
    const editedCount = Math.round((percent / 100) * item.sampleSize);
    const directCount = Math.max(item.sampleSize - editedCount, 0);

    // SVG Donut calculation: Radius = 30, Circumference ≈ 188.5
    const radius = 30;
    const circumference = 2 * Math.PI * radius;
    const editedDash = (percent / 100) * circumference;

    return (
      <div className="sys-hires-donut-layout">
        <div className="sys-hires-donut-chart">
          <svg viewBox="0 0 76 76" className="sys-hires-svg-donut">
            {/* Background circle (Direct approval) */}
            <circle
              cx="38"
              cy="38"
              r={radius}
              fill="none"
              stroke="#dbeafe"
              strokeWidth="7"
            />
            {/* Foreground circle (Edited) */}
            <circle
              cx="38"
              cy="38"
              r={radius}
              fill="none"
              stroke="#1877f2"
              strokeWidth="7"
              strokeDasharray={`${editedDash} ${circumference}`}
              strokeDashoffset="0"
              strokeLinecap="round"
              transform="rotate(-90 38 38)"
            />
          </svg>
          <div className="sys-donut-center-stat">
            <strong>{percent.toFixed(1)}%</strong>
            <small>Edited</small>
          </div>
        </div>

        <div className="sys-hires-donut-legend">
          <div className="sys-legend-item">
            <span className="sys-legend-dot sys-dot-blue" />
            <div>
              <strong>{editedCount} Submissions Edited</strong>
              <span>Adjusted by moderators prior to approval</span>
            </div>
          </div>
          <div className="sys-legend-item">
            <span className="sys-legend-dot sys-dot-light-blue" />
            <div>
              <strong>{directCount} Approved As-Is</strong>
              <span>Passed review with zero revisions</span>
            </div>
          </div>
        </div>
      </div>
    );
  }

  if (item.key === "publish_success_rate") {
    const percent = Math.min(Math.max(item.value, 0), 100);
    const radius = 30;
    const circumference = 2 * Math.PI * radius;
    const strokeDash = (percent / 100) * circumference;
    const succeeded = Math.round((percent / 100) * item.sampleSize);
    const failed = Math.max(item.sampleSize - succeeded, 0);
    const allClean = failed === 0;

    return (
      <div className="sys-hires-donut-layout">
        <div className="sys-hires-donut-chart">
          <svg viewBox="0 0 76 76" className="sys-hires-svg-donut">
            <circle
              cx="38"
              cy="38"
              r={radius}
              fill="none"
              stroke="#f1f5f9"
              strokeWidth="7"
            />
            <circle
              cx="38"
              cy="38"
              r={radius}
              fill="none"
              stroke={allClean ? "#10b981" : "#f59e0b"}
              strokeWidth="7"
              strokeDasharray={`${strokeDash} ${circumference}`}
              strokeDashoffset="0"
              strokeLinecap="round"
              transform="rotate(-90 38 38)"
            />
          </svg>
          <div className="sys-donut-center-stat">
            {allClean && <i className="ti ti-check sys-check-green" />}
            <strong>{percent.toFixed(0)}%</strong>
          </div>
        </div>

        <div className="sys-hires-donut-legend">
          <div className="sys-legend-item">
            <span className="sys-legend-dot sys-dot-emerald" />
            <div>
              <strong>{succeeded} of {item.sampleSize} Published Successfully</strong>
              <span>Direct automated Facebook Graph API dispatches</span>
            </div>
          </div>
          <div className="sys-legend-item">
            <span className="sys-legend-dot sys-dot-slate" />
            <div>
              <strong>{failed} Failed Post Attempt{failed === 1 ? "" : "s"}</strong>
              <span>
                {allClean
                  ? "Zero network timeouts or API rejections recorded"
                  : "Network timeouts or Graph API rejections — see the Review Queue's Failed tab"}
              </span>
            </div>
          </div>
        </div>
      </div>
    );
  }

  if (item.key === "manual_fallback_resolution_rate") {
    const percent = Math.min(Math.max(item.value, 0), 100);
    const resolved = Math.round((percent / 100) * item.sampleSize);
    const pending = item.sampleSize - resolved;

    return (
      <div className="sys-hires-fallback-layout">
        <div className="sys-fallback-progress-bar">
          <div className="sys-fallback-track">
            <div
              className="sys-fallback-fill"
              style={{
                width: `${percent}%`,
                background: percent === 100 ? "#10b981" : percent > 0 ? "#f59e0b" : "#ef4444",
              }}
            />
          </div>
          <div className="sys-fallback-markers">
            <span>0% Resolution</span>
            <span>Target: 100% Resolved</span>
          </div>
        </div>

        <div className="sys-fallback-stats-grid">
          <div className="sys-fallback-stat-box sys-stat-pending">
            <span className="sys-stat-title">Pending Incidents</span>
            <strong className="sys-stat-number">{pending}</strong>
            <small>Requires admin action</small>
          </div>
          <div className="sys-fallback-stat-box sys-stat-resolved">
            <span className="sys-stat-title">Resolved Fallbacks</span>
            <strong className="sys-stat-number">{resolved}</strong>
            <small>Manually remediated</small>
          </div>
        </div>
      </div>
    );
  }

  if (item.key === "live_event_fast_track_volume") {
    const count = item.value;
    return (
      <div className="sys-hires-fasttrack-layout">
        <div className="sys-fasttrack-bars">
          {[...Array(6)].map((_, i) => (
            <div key={i} className="sys-ft-bar-col">
              <div
                className={`sys-ft-bar-fill${i < count ? " is-active" : ""}`}
                style={{ height: `${28 + i * 8}px` }}
              />
              <span className="sys-ft-bar-label">Slot {i + 1}</span>
            </div>
          ))}
        </div>

        <div className="sys-fasttrack-summary-card">
          <div className="sys-ft-icon-box">
            <i className="ti ti-bolt" />
          </div>
          <div>
            <strong>{count} Fast-Track Posts Dispatched</strong>
            <p>Expedited past normal approval queues for real-time live institution coverage.</p>
          </div>
        </div>
      </div>
    );
  }

  return null;
}

function getMetricExplanation(item: OperationalMetric): string {
  switch (item.key) {
    case "approval_turnaround_time":
      return item.value <= 24
        ? "Moderator review latency is operating well within the 24-hour SLA benchmark."
        : `Average turnaround time is currently ${item.value.toFixed(1)}h (+${(item.value - 24).toFixed(1)}h above the 24h SLA target). Reviewing pending queues is advised.`;
    case "edit_approve_rate":
      return `${item.value.toFixed(1)}% of submissions required revisions before approval. Standard direct-approval threshold is ≥85%.`;
    case "manual_fallback_resolution_rate":
      return "Percentage of automated posting failures that were resolved via manual fallback.";
    case "publish_success_rate": {
      const succeeded = Math.round((Math.min(Math.max(item.value, 0), 100) / 100) * item.sampleSize);
      const failed = Math.max(item.sampleSize - succeeded, 0);
      return failed === 0
        ? `All ${item.sampleSize} approved post${item.sampleSize === 1 ? "" : "s"} published cleanly to connected social channels with zero dispatch errors.`
        : `${item.value.toFixed(1)}% published cleanly — ${failed} of ${item.sampleSize} dispatch${failed === 1 ? "" : "es"} failed and need review in the Review Queue's Failed tab.`;
    }
    case "live_event_fast_track_volume":
      return "High-priority live event posts routed through expedited moderator workflows.";
    default:
      return item.detail || "Operational health telemetry for the last 30 days.";
  }
}

function getMetricBenchmark(item: OperationalMetric): string {
  if (item.key === "approval_turnaround_time") return item.value <= 24 ? "Target SLA: ≤ 24h (Met)" : "Target SLA: ≤ 24h (Over)";
  if (item.key === "publish_success_rate") return item.value >= 98 ? "Target: ≥ 98% (Met)" : "Target: ≥ 98% (Below)";
  if (item.key === "edit_approve_rate") return "Benchmark: ≤ 15%";
  if (item.key === "manual_fallback_resolution_rate") return "Target: 100% Resolved";
  if (item.key === "live_event_fast_track_volume") return "Expedited Window";
  return "Operational Benchmark";
}

function StorageCard({ item }: { item: StorageMetric }) {
  const percentCapped = Math.min(Math.max(item.usedPercent, 0), 100);
  return (
    <div className="card-wrap sys-card sys-storage-card">
      <div className="sys-card-header-row">
        <div className="sys-card-header-left">
          <span className="sys-card-icon-box">
            <i className="ti ti-server" aria-hidden="true" />
          </span>
          <div>
            <h3 className="sys-card-title">{item.name}</h3>
            <span className="sys-card-subtitle">{item.detail || "Capacity metric"}</span>
          </div>
        </div>
        <StatusBadge status={item.status} />
      </div>

      <div className="sys-storage-values-row">
        <div className="sys-storage-val">
          {formatBytes(item.usedBytes)} <span className="sys-storage-sub">/ {formatBytes(item.limitBytes)}</span>
        </div>
        <span className="sys-percent-tag">{item.usedPercent}%</span>
      </div>

      <div className={`sys-meter sys-meter-${item.status.toLowerCase()}`} aria-label={`${item.usedPercent}% used`}>
        <span style={{ width: `${percentCapped}%` }} />
      </div>
    </div>
  );
}

function ServiceCard({ item }: { item: ExternalServiceHealth }) {
  return (
    <div className="card-wrap sys-card sys-service-card">
      <div className="sys-card-header-row">
        <span className="sys-card-icon-box">
          <i className={serviceIcon(item.service)} aria-hidden="true" />
        </span>
        <StatusBadge status={item.status} />
      </div>

      <div className="sys-service-content">
        <h3 className="sys-card-title">{item.service}</h3>
        <p className="sys-service-detail">{item.detail || "Connected and responsive"}</p>
        {item.expiresAt && (
          <div className="sys-service-expiry">
            <i className="ti ti-calendar-time" aria-hidden="true" />
            <span>Expires {formatDate(item.expiresAt)}</span>
          </div>
        )}
      </div>
    </div>
  );
}

function JobTable({
  jobs,
  runningJobKey,
  onRun,
}: {
  jobs: BackgroundJobHealth[];
  runningJobKey: string | null;
  onRun: (job: BackgroundJobHealth) => void;
}) {
  if (jobs.length === 0) {
    return (
      <div className="sys-table-empty">
        <i className="ti ti-search-off" aria-hidden="true" />
        <p>No background jobs match your current filter.</p>
      </div>
    );
  }

  const anyRunning = runningJobKey !== null;

  return (
    <table className="data-table sys-jobs-table">
      <thead>
        <tr>
          <th style={{ width: "30%" }}>JOB NAME</th>
          <th style={{ width: "14%" }}>STATUS</th>
          <th style={{ width: "16%" }}>LAST RUN</th>
          <th style={{ width: "16%" }}>LAST SUCCESS</th>
          <th style={{ width: "12%" }}>DURATION</th>
          <th style={{ width: "12%", textAlign: "right" }}>ACTIONS</th>
        </tr>
      </thead>
      <tbody>
        {jobs.map((job) => {
          const running = runningJobKey === job.key;
          return (
            <tr key={job.key} className="notif-table-row">
              <td>
                <div className="sys-job-cell">
                  <span className={`sys-job-dot sys-dot-${job.status.toLowerCase()}`} />
                  <div>
                    <strong className="sys-job-name">{formatJobName(job.jobName)}</strong>
                    {job.lastError && (
                      <span className="sys-job-error-hint" title={job.lastError}>
                        <i className="ti ti-alert-triangle" /> {job.lastError}
                      </span>
                    )}
                  </div>
                </div>
              </td>
              <td><StatusBadge status={job.status} /></td>
              <td><span className="sys-date-text">{formatDate(job.lastStartedAt)}</span></td>
              <td><span className="sys-date-text">{formatDate(job.lastSuccessAt)}</span></td>
              <td>
                <span className="sys-duration-pill">
                  {job.lastDurationMs == null ? "—" : `${job.lastDurationMs} ms`}
                </span>
              </td>
              <td style={{ textAlign: "right" }}>
                <button
                  type="button"
                  className="notif-btn notif-btn-ghost notif-btn-sm"
                  onClick={() => onRun(job)}
                  disabled={anyRunning}
                  title={`Run ${formatJobName(job.jobName)} now instead of waiting for its schedule`}
                >
                  <i className={running ? "ti ti-loader-2 sys-spin" : "ti ti-player-play"} aria-hidden="true" />
                  <span>{running ? "Running..." : "Re-run"}</span>
                </button>
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
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
    return (
      <div className="card-wrap sys-token-empty-card">
        <i className="ti ti-brand-facebook" aria-hidden="true" />
        <div>
          <strong>No Facebook Page Tokens Configured</strong>
          <p>Connect a Facebook Page in Settings to activate automated publishing and engagement sync.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="card-wrap">
      <table className="data-table sys-token-table">
        <thead>
          <tr>
            <th style={{ width: "30%" }}>CONNECTED PAGE</th>
            <th style={{ width: "20%" }}>STATUS</th>
            <th style={{ width: "22%" }}>EXPIRATION</th>
            <th style={{ width: "16%" }}>LAST VALIDATED</th>
            <th style={{ width: "12%", textAlign: "right" }}>ACTION</th>
          </tr>
        </thead>
        <tbody>
          {tokens.map((token) => (
            <tr key={token.id}>
              <td>
                <div className="sys-page-cell">
                  <i className="ti ti-brand-facebook" />
                  <strong>Page ····{token.pageId.slice(-4) || "----"}</strong>
                </div>
              </td>
              <td><StatusBadge status={tokenStatusToHealth(token.tokenStatus)} /></td>
              <td><span className="sys-date-text">{formatDate(token.expiresAt)}</span></td>
              <td><span className="sys-date-text">{formatDate(token.lastValidatedAt)}</span></td>
              <td style={{ textAlign: "right" }}>
                <button
                  type="button"
                  className="notif-btn notif-btn-ghost notif-btn-sm"
                  disabled={!canReauthorize || busyTokenId === token.id}
                  onClick={() => onReauthorize(token)}
                  title="Renew Facebook Page Access Token"
                >
                  <i className={busyTokenId === token.id ? "ti ti-loader-2 sys-spin" : "ti ti-refresh"} aria-hidden="true" />
                  <span>Reauthorize</span>
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

function overallStatusHeadline(status: HealthStatus) {
  switch (status) {
    case "HEALTHY":
      return "All systems normal";
    case "WARNING":
      return "Degraded warnings";
    case "UNHEALTHY":
      return "Attention required";
    case "UNAVAILABLE":
      return "Telemetry offline";
    default:
      return "Systems operational";
  }
}

function overallStatusIcon(status: HealthStatus) {
  switch (status) {
    case "HEALTHY":
      return "ti ti-circle-check";
    case "WARNING":
      return "ti ti-alert-triangle";
    case "UNHEALTHY":
      return "ti ti-alert-circle";
    default:
      return "ti ti-activity";
  }
}

function formatBytes(bytes: number) {
  if (!Number.isFinite(bytes) || bytes <= 0) return "0 B";
  // Decimal (1000) units so the figures line up with how Cloudflare R2 and
  // Supabase report quota in their dashboards (10 GB, 500 MB, …).
  const units = ["B", "KB", "MB", "GB", "TB"];
  let value = bytes;
  let unit = 0;
  while (value >= 1000 && unit < units.length - 1) {
    value /= 1000;
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
      return "ti ti-hourglass-high";
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
  if (name.includes("email") || name.includes("smtp") || name.includes("mail") || name.includes("resend")) return "ti ti-mail";
  if (name.includes("facebook") || name.includes("meta")) return "ti ti-brand-facebook";
  if (name.includes("r2") || name.includes("cloudflare") || name.includes("s3") || name.includes("storage")) return "ti ti-cloud";
  if (name.includes("database") || name.includes("postgres") || name.includes("sql")) return "ti ti-database";
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

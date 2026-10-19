import { Fragment, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  connectFacebookPage,
  downloadSystemHealthSnapshot,
  getSystemHealthSummary,
  getSystemHealthTokens,
  initSystemHealthOAuth,
  runSystemHealthJob,
  setSystemHealthTokenManually,
  type BackgroundJobHealth,
  type ExternalServiceHealth,
  type HealthStatus,
  type OperationalMetric,
  type StorageMetric,
  type TokenStatus,
} from "../../api/systemHealthApi";
import { useToast } from "../../context/ToastContext";
import { authenticatedQueryMeta } from "../../lib/queryClient";
import { queryKeys } from "../../lib/queryKeys";
import type { User } from "../../types/auth.types";
import "../../styles/user-management.css";
import "../../styles/system-health.css";
import "../../styles/dasig-loader.css";

interface Props {
  user: User;
}

type SystemHealthTab = "jobs" | "integrations" | "performance" | "storage";

const SYSTEM_HEALTH_STALE_TIME_MS = 60_000;
const EMPTY_TOKENS: TokenStatus[] = [];
// Mirrors ManualJobRunner.ASYNC_JOB_KEYS on the backend -- these two make live,
// retrying Facebook API calls and now run in the background instead of
// blocking the request, so the response won't yet reflect the new run.
const ASYNC_JOB_KEYS = new Set(["PublishingSchedulerJob", "TokenPublishingEscalationJob"]);

function getUserCacheScope(user: User) {
  return user.id ?? user.email.trim().toLowerCase();
}

export default function SystemHealthScreen({ user }: Props) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [exporting, setExporting] = useState(false);
  const [runningJobKey, setRunningJobKey] = useState<string | null>(null);
  const [busyTokenId, setBusyTokenId] = useState<string | null>(null);
  const [manualEntryTokenId, setManualEntryTokenId] = useState<string | null>(null);
  const [manualTokenValue, setManualTokenValue] = useState("");
  const [connectPageId, setConnectPageId] = useState("");
  const [connectAccessToken, setConnectAccessToken] = useState("");
  const [connectingPage, setConnectingPage] = useState(false);

  // Active top-level tab (integrations | storage | jobs | performance)
  const [activeTab, setActiveTab] = useState<SystemHealthTab>(() => {
    const hash = window.location.hash.replace("#", "");
    if (hash === "integrations" || hash === "performance" || hash === "storage" || hash === "jobs") {
      return hash as SystemHealthTab;
    }
    return "integrations";
  });

  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const mobileNavRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!mobileNavOpen) return;
    const handleClickOutside = (e: MouseEvent) => {
      if (mobileNavRef.current && !mobileNavRef.current.contains(e.target as Node)) {
        setMobileNavOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [mobileNavOpen]);

  // Background Jobs search & status filter
  const [jobSearch, setJobSearch] = useState("");
  const [jobStatusFilter, setJobStatusFilter] = useState<string>("ALL");

  const canReauthorize = user.role === "admin";
  const isOwner = user.adminOwner === true;

  const healthScope = {
    role: user.role,
    userId: getUserCacheScope(user),
  };
  const healthQuery = useQuery({
    queryKey: queryKeys.systemHealth.summary(healthScope),
    queryFn: ({ signal }) => getSystemHealthSummary(signal).then((response) => response.data),
    staleTime: SYSTEM_HEALTH_STALE_TIME_MS,
    meta: authenticatedQueryMeta,
  });
  const tokensQuery = useQuery({
    queryKey: queryKeys.systemHealth.tokens(healthScope),
    queryFn: ({ signal }) => getSystemHealthTokens(signal).then((response) => response.data),
    enabled: activeTab === "integrations",
    staleTime: SYSTEM_HEALTH_STALE_TIME_MS,
    meta: authenticatedQueryMeta,
  });

  const summary = healthQuery.data ?? null;
  const tokens = tokensQuery.data ?? EMPTY_TOKENS;
  const loading = healthQuery.isLoading || healthQuery.isFetching;
  const loadError = healthQuery.error ? "Unable to load system health metrics." : "";

  function handleTabChange(tab: SystemHealthTab) {
    setActiveTab(tab);
    window.history.pushState(null, "", `#${tab}`);
  }

  function refreshHealth() {
    void queryClient.invalidateQueries({ queryKey: ["system-health"] });
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
    const async = ASYNC_JOB_KEYS.has(job.key);
    try {
      await runSystemHealthJob(job.key);
      await queryClient.invalidateQueries({ queryKey: ["system-health"] });
      toast.success(
        async
          ? `Started ${job.jobName} in the background — it can take a few minutes; refresh to see its result.`
          : `Ran ${job.jobName}.`,
      );
    } catch {
      toast.error(`Unable to ${async ? "start" : "run"} ${job.jobName}.`);
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

  function toggleManualEntry(tokenId: string) {
    setManualEntryTokenId((current) => (current === tokenId ? null : tokenId));
    setManualTokenValue("");
  }

  async function handleSetManualToken(tokenId: string) {
    if (!manualTokenValue.trim()) return;
    setBusyTokenId(tokenId);
    try {
      await setSystemHealthTokenManually(tokenId, manualTokenValue.trim());
      await queryClient.invalidateQueries({ queryKey: ["system-health"] });
      toast.success("Facebook Page Access Token verified and updated.");
      setManualEntryTokenId(null);
      setManualTokenValue("");
    } catch (err: unknown) {
      const backendMessage = (err as { response?: { data?: { error?: string } } })?.response?.data?.error;
      toast.error(backendMessage || "Unable to save the token. Make sure it's a valid Page Access Token.");
    } finally {
      setBusyTokenId(null);
    }
  }

  async function handleConnectPage() {
    if (!connectPageId.trim() || !connectAccessToken.trim()) return;
    setConnectingPage(true);
    try {
      await connectFacebookPage(connectPageId.trim(), connectAccessToken.trim());
      await queryClient.invalidateQueries({ queryKey: ["system-health"] });
      toast.success("Facebook Page connected. Publishing now targets this page.");
      setConnectPageId("");
      setConnectAccessToken("");
    } catch (err: unknown) {
      const backendMessage = (err as { response?: { data?: { error?: string } } })?.response?.data?.error;
      toast.error(backendMessage || "Unable to connect this page. Make sure the Page ID and token match.");
    } finally {
      setConnectingPage(false);
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

  const tabsConfig = useMemo(() => {
    if (!summary) return [];
    return [
      {
        id: "integrations" as SystemHealthTab,
        label: "Services & AI Integrations",
        icon: "ti ti-plug-connected",
        count: summary.externalServices.length + tokens.length,
        hasAlert: !!tabAlerts.integrations,
      },
      {
        id: "storage" as SystemHealthTab,
        label: "Storage & Capacity",
        icon: "ti ti-database",
        count: summary.storage.length,
        hasAlert: !!tabAlerts.storage,
      },
      {
        id: "jobs" as SystemHealthTab,
        label: "Background Jobs",
        icon: "ti ti-cpu",
        count: summary.backgroundJobs.length,
        hasAlert: !!tabAlerts.jobs,
      },
      {
        id: "performance" as SystemHealthTab,
        label: "Operational Telemetry",
        icon: "ti ti-chart-dots",
        count: summary.operationalMetrics.length,
        hasAlert: !!tabAlerts.performance,
      },
    ];
  }, [summary, tokens, tabAlerts]);

  const currentTabConfig = tabsConfig.find((t) => t.id === activeTab) || tabsConfig[0];

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
            <h1 className="dash-view-title sys-title">System Health & Diagnostics</h1>
            <p className="dash-view-desc">Infrastructure probes, AI vision quotas, capacity telemetry, and background automation</p>
            {summary && (
              <div className="sys-meta-row">
                <span className="sys-meta-pill">
                  <i className="ti ti-clock" aria-hidden="true" />
                  Telemetry Snapshot: {formatDate(summary.generatedAt)}
                </span>
                <span className="sys-meta-pill">
                  <i className="ti ti-shield-check" aria-hidden="true" />
                  Monitoring Active
                </span>
                <span className="sys-meta-pill sys-meta-pill-institution">
                  <i className="ti ti-building" aria-hidden="true" />
                  DASIG Regional Hub
                </span>
              </div>
            )}
          </div>
          <div className="sys-toolbar">
            <button
              type="button"
              className="sys-btn sys-btn-ghost"
              onClick={refreshHealth}
              disabled={loading}
              title="Refresh system health metrics"
            >
              <i className={loading ? "ti ti-loader-2 sys-spin" : "ti ti-refresh"} aria-hidden="true" />
              <span>{loading ? "Refreshing..." : "Refresh"}</span>
            </button>
            <button
              type="button"
              className="sys-btn sys-btn-ghost"
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

            {/* ── 4-Tab Segmented Navigation Bar (Desktop) & Tab-Styled Dropdown (Mobile) ── */}
            <div className="sys-main-tabs-wrapper">
              {/* Responsive Mobile / Tablet Tab-Styled Dropdown */}
              <div className="sys-mobile-tab-dropdown" ref={mobileNavRef}>
                <button
                  type="button"
                  className="sys-mobile-tab-trigger"
                  onClick={() => setMobileNavOpen((prev) => !prev)}
                  aria-expanded={mobileNavOpen}
                  aria-haspopup="listbox"
                  aria-label="Select System Health Section"
                >
                  <div className="sys-mobile-trigger-content">
                    <i className={currentTabConfig.icon} aria-hidden="true" />
                    <span className="sys-mobile-trigger-label">{currentTabConfig.label}</span>
                    <span className={`sys-main-tab-badge${currentTabConfig.hasAlert ? " has-alert" : ""}`}>
                      {currentTabConfig.hasAlert && <span className="sys-tab-alert-dot" aria-hidden="true" />}
                      {currentTabConfig.count}
                    </span>
                  </div>
                  <i className={`ti ti-chevron-${mobileNavOpen ? "up" : "down"} sys-mobile-trigger-arrow`} aria-hidden="true" />
                </button>

                {mobileNavOpen && (
                  <div className="sys-mobile-tab-menu" role="listbox">
                    {tabsConfig.map((tab) => {
                      const isSelected = tab.id === activeTab;
                      return (
                        <button
                          key={tab.id}
                          type="button"
                          role="option"
                          aria-selected={isSelected}
                          className={`sys-mobile-tab-item${isSelected ? " is-active" : ""}`}
                          onClick={() => {
                            handleTabChange(tab.id);
                            setMobileNavOpen(false);
                          }}
                        >
                          <div className="sys-mobile-item-left">
                            <i className={tab.icon} aria-hidden="true" />
                            <span>{tab.label}</span>
                          </div>
                          <div className="sys-mobile-item-right">
                            <span className={`sys-main-tab-badge${tab.hasAlert ? " has-alert" : ""}`}>
                              {tab.hasAlert && <span className="sys-tab-alert-dot" aria-hidden="true" />}
                              {tab.count}
                            </span>
                            {isSelected && <i className="ti ti-check sys-mobile-item-check" aria-hidden="true" />}
                          </div>
                        </button>
                      );
                    })}
                  </div>
                )}
              </div>

              {/* Desktop Segmented Tablist */}
              <div className="sys-main-tabs" role="tablist" aria-label="System Health Navigation">
                {tabsConfig.map((tab) => (
                  <button
                    key={tab.id}
                    type="button"
                    role="tab"
                    className={`sys-main-tab${activeTab === tab.id ? " is-active" : ""}`}
                    onClick={() => handleTabChange(tab.id)}
                    aria-selected={activeTab === tab.id}
                  >
                    <i className={tab.icon} aria-hidden="true" />
                    <span>{tab.label}</span>
                    <span className={`sys-main-tab-badge${tab.hasAlert ? " has-alert" : ""}`}>
                      {tab.hasAlert && <span className="sys-tab-alert-dot" aria-hidden="true" />}
                      {tab.count}
                    </span>
                  </button>
                ))}
              </div>
            </div>

            {/* ── TAB: Operational Performance (Split into Editorial SLAs & AI Pipeline Diagnostics) ── */}
            {activeTab === "performance" && (
              <div className="sys-tab-content sys-grouped-content">
                {/* 1. Core Editorial & Publishing Benchmarks */}
                <Section
                  title="Editorial & Moderation Benchmarks · Last 30 Days"
                  icon="ti ti-checklist"
                  subtitle="Monitors submission review turnaround times, editorial approval rates, and automated Facebook publishing reliability"
                >
                  <div className="sys-perf-compact-grid">
                    {summary.operationalMetrics
                      .filter((item) =>
                        [
                          "approval_turnaround_time",
                          "edit_approve_rate",
                          "manual_fallback_resolution_rate",
                          "publish_success_rate",
                          "live_event_fast_track_volume",
                          "missed_review_rate",
                        ].includes(item.key)
                      )
                      .map((item) => (
                        <CompactMetricCard item={item} key={item.key} />
                      ))}
                  </div>
                </Section>

                {/* 2. Background AI Pipeline Diagnostics */}
                <Section
                  title="Background AI Pipeline & Embedding Telemetry"
                  icon="ti ti-sparkles"
                  subtitle="Tracks background worker queues for caption generation, Voyage semantic embeddings, and recommendation accuracy"
                >
                  <div className="sys-perf-compact-grid">
                    {summary.operationalMetrics
                      .filter(
                        (item) =>
                          ![
                            "approval_turnaround_time",
                            "edit_approve_rate",
                            "manual_fallback_resolution_rate",
                            "publish_success_rate",
                            "live_event_fast_track_volume",
                            "missed_review_rate",
                          ].includes(item.key)
                      )
                      .map((item) => (
                        <CompactMetricCard item={item} key={item.key} />
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
                  {tokensQuery.isLoading ? (
                    <div className="sys-cardless-loader" aria-live="polite">
                      <div className="dc-dot-triangle-container">
                        <div className="loader-dots" />
                      </div>
                    </div>
                  ) : tokensQuery.isError && !tokensQuery.data ? (
                    <div className="alert alert-err" role="alert">
                      <span>Unable to load Facebook access tokens.</span>
                      <button type="button" className="sys-btn sys-btn-ghost sys-btn-sm" onClick={() => void tokensQuery.refetch()}>
                        Retry
                      </button>
                    </div>
                  ) : (
                    <TokenTable
                      tokens={tokens}
                      busyTokenId={busyTokenId}
                      canReauthorize={canReauthorize}
                      onReauthorize={handleReauthorize}
                      manualEntryTokenId={manualEntryTokenId}
                      manualTokenValue={manualTokenValue}
                      onToggleManualEntry={toggleManualEntry}
                      onManualTokenValueChange={setManualTokenValue}
                      onSetManualToken={handleSetManualToken}
                    />
                  )}
                </Section>

                {/* Connect a Different Page — Admin Owner only */}
                {isOwner && (
                  <Section
                    title="Connect a Different Facebook Page"
                    icon="ti ti-replace"
                    subtitle="Owner-only. Changes which page DASIGConnect publishes to — every other page's token is deactivated the moment this succeeds."
                  >
                    <div className="card-wrap sys-manual-token-row" style={{ padding: 16 }}>
                      <input
                        className="sys-input"
                        placeholder="Facebook Page ID"
                        value={connectPageId}
                        onChange={(e) => setConnectPageId(e.target.value)}
                        autoComplete="off"
                      />
                      <input
                        type="password"
                        className="sys-input"
                        placeholder="Page Access Token for that page"
                        value={connectAccessToken}
                        onChange={(e) => setConnectAccessToken(e.target.value)}
                        autoComplete="off"
                      />
                      <button
                        type="button"
                        className="sys-btn sys-btn-primary sys-btn-sm"
                        disabled={!connectPageId.trim() || !connectAccessToken.trim() || connectingPage}
                        onClick={() => void handleConnectPage()}
                      >
                        <i className={connectingPage ? "ti ti-loader-2 sys-spin" : "ti ti-replace"} aria-hidden="true" />
                        <span>Connect Page</span>
                      </button>
                      <span className="sys-manual-token-hint">
                        The token is verified against this Page ID before anything changes. This cannot be undone from here — connect back to the previous page the same way if needed.
                      </span>
                    </div>
                  </Section>
                )}

                {/* 1. Generative AI Vision Models & Quotas */}
                <Section
                  title="AI Vision & Multimodal Intelligence"
                  icon="ti ti-sparkles"
                  subtitle="Active multimodal vision models, daily generation credit quotas, and provider failover"
                >
                  <div className="sys-ai-services-grid">
                    {summary.externalServices
                      .filter((s) => s.service.toLowerCase().includes("gemini") || s.service.toLowerCase().includes("claude"))
                      .map((item) => (
                        <AiServiceCard item={item} key={item.service} />
                      ))}
                  </div>
                </Section>

                {/* 2. Platform Infrastructure & Cloud Connections */}
                <Section
                  title="Platform Infrastructure & Cloud Connections"
                  icon="ti ti-server-2"
                  subtitle="Live reachability status and round-trip probe latency across storage, database, and messaging channels"
                >
                  <div className="sys-infra-services-grid">
                    {summary.externalServices
                      .filter((s) => !s.service.toLowerCase().includes("gemini") && !s.service.toLowerCase().includes("claude"))
                      .map((item) => (
                        <InfraServiceCard item={item} key={item.service} />
                      ))}
                  </div>
                </Section>
              </div>
            )}

            {/* ── TAB 4: Scheduled Background Jobs ── */}
            {activeTab === "jobs" && (
              <div className="sys-tab-content">
                {/* Admin Management Directory Toolbar */}
                <section
                  className="um-data-card is-directory is-headerless"
                  style={{ marginTop: 0, marginBottom: "16px" }}
                >
                  <div className="um-filter-bar um-users-filter-bar">
                    <div className="um-filter-pills-wrap">
                      <div className="um-filter-pills" role="group" aria-label="Filter background jobs by status">
                        {(["ALL", "HEALTHY", "SCHEDULED", "WARNING", "UNHEALTHY"] as const).map((st) => {
                          const count = jobCounts[st] ?? 0;
                          if (st !== "ALL" && count === 0) return null;
                          return (
                            <button
                              key={st}
                              type="button"
                              className={`um-filter-pill${jobStatusFilter === st ? " is-active" : ""}`}
                              onClick={() => setJobStatusFilter(st)}
                              aria-pressed={jobStatusFilter === st}
                            >
                              <span>{st === "ALL" ? "All Jobs" : labelStatus(st as HealthStatus)}</span>
                              <span className="um-filter-pill-count">{count}</span>
                            </button>
                          );
                        })}
                      </div>
                    </div>

                    <div className="um-search-wrap">
                      <i className="ti ti-search um-search-icon" aria-hidden="true" />
                      <input
                        className="um-search-input"
                        type="search"
                        placeholder="Search jobs..."
                        value={jobSearch}
                        onChange={(e) => setJobSearch(e.target.value)}
                        aria-label="Search background jobs"
                      />
                      {jobSearch && (
                        <button
                          type="button"
                          className="um-search-clear"
                          onClick={() => setJobSearch("")}
                          aria-label="Clear search"
                          style={{
                            position: "absolute",
                            right: 12,
                            top: "50%",
                            transform: "translateY(-50%)",
                            background: "none",
                            border: "none",
                            color: "var(--d-muted, #94a3b8)",
                            cursor: "pointer",
                            padding: 4,
                            display: "flex",
                            alignItems: "center",
                          }}
                        >
                          <i className="ti ti-x" aria-hidden="true" />
                        </button>
                      )}
                    </div>
                  </div>
                </section>

                <JobTable
                  jobs={filteredJobs}
                  runningJobKey={runningJobKey}
                  hasFilters={jobSearch !== "" || jobStatusFilter !== "ALL"}
                  onClearFilters={() => {
                    setJobSearch("");
                    setJobStatusFilter("ALL");
                  }}
                  onRun={(job) => void handleRunJob(job)}
                />
              </div>
            )}
          </>
        ) : (
          <div className="card-wrap sys-error-card">
            <i className="ti ti-alert-circle sys-error-icon" aria-hidden="true" />
            <h3>System Health Unavailable</h3>
            <p>{loadError || "Unable to retrieve real-time system health metrics from the backend."}</p>
            <button type="button" className="sys-btn sys-btn-ghost" onClick={refreshHealth}>
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

/* ---------- Compact Informative Metric Card ---------- */
function CompactMetricCard({ item }: { item: OperationalMetric }) {
  const unavailable = item.status === "UNAVAILABLE";
  const noActivity = isNoActivityMetric(item);
  const percentCapped = item.unit === "percent" ? Math.min(Math.max(item.value, 0), 100) : null;
  const isPercentMetric = percentCapped !== null && !noActivity && !unavailable;

  return (
    <div className={`card-wrap sys-perf-card sys-perf-${item.status.toLowerCase()}`}>
      <div className="sys-perf-card-top">
        <div className="sys-perf-card-header">
          <span className="sys-card-icon-box sys-card-icon-sm">
            <i className={metricIcon(item.key)} aria-hidden="true" />
          </span>
          <div className="sys-perf-name-wrap">
            <h4 className="sys-perf-title">{item.label}</h4>
            <span className="sys-perf-sample-meta">{sampleLabel(item)}</span>
          </div>
        </div>
        <StatusBadge status={item.status} />
      </div>

      <div className="sys-perf-card-body">
        <div className="sys-perf-val-row">
          <span className={`sys-perf-value ${noActivity || unavailable ? "is-muted" : ""}`}>
            {unavailable ? "No data" : noActivity ? "— No activity" : formatMetricValue(item)}
          </span>
          <span className="sys-perf-benchmark-pill">{getMetricBenchmark(item)}</span>
        </div>

        {isPercentMetric && (
          <div className="sys-perf-meter-wrap">
            <div className="sys-perf-meter-track" title={`${percentCapped}%`}>
              <div
                className={`sys-perf-meter-fill sys-meter-${item.status.toLowerCase()}`}
                style={{ width: `${percentCapped}%` }}
              />
            </div>
          </div>
        )}
      </div>

      <div className="sys-perf-card-footer">
        <i className="ti ti-info-circle" aria-hidden="true" />
        <span>{getMetricExplanation(item)}</span>
      </div>
    </div>
  );
}

function getMetricExplanation(item: OperationalMetric): string {
  switch (item.key) {
    case "approval_turnaround_time":
      return item.value <= 24
        ? "Moderator review latency is operating well within the 24-hour SLA benchmark."
        : `Average turnaround time is currently ${item.value.toFixed(1)}h (+${(item.value - 24).toFixed(1)}h above the 24h SLA target). Reviewing pending queues is advised.`;
    case "edit_approve_rate":
      return `${item.value.toFixed(1)}% of submissions required revisions before approval. Standard direct-approval threshold is ≤15%.`;
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
    case "missed_review_rate":
      return `${item.value.toFixed(1)}% of submissions that reached a review outcome missed their scheduled review deadline entirely, rather than being approved or rejected in time.`;
    default:
      return item.detail || "Operational health telemetry for the last 30 days.";
  }
}

function getMetricBenchmark(item: OperationalMetric): string {
  if (item.key === "approval_turnaround_time") return item.value <= 24 ? "Target SLA: ≤ 24h (Met)" : "Target SLA: ≤ 24h (Over)";
  if (item.key === "publish_success_rate") return item.value >= 95 ? "Target: ≥ 95% (Met)" : "Target: ≥ 95% (Below)";
  if (item.key === "edit_approve_rate") return "Benchmark: ≤ 15%";
  if (item.key === "manual_fallback_resolution_rate") return "Target: 100% Resolved";
  if (item.key === "live_event_fast_track_volume") return "Expedited Window";
  if (item.key === "missed_review_rate") return item.value <= 10 ? "Target: ≤ 10% (Met)" : "Target: ≤ 10% (Over)";
  
  if (item.key === "media_ai_queue_depth") return item.value <= 50 ? "Target: ≤ 50 (Met)" : "Target: ≤ 50 (Over)";
  if (item.key === "media_ai_queue_completion_rate") return item.value >= 95 ? "Target: ≥ 95% (Met)" : "Target: ≥ 95% (Below)";
  if (item.key === "media_ai_voyage_failure_rate") return item.value <= 10 ? "Target: ≤ 10% (Met)" : "Target: ≤ 10% (Over)";
  if (item.key === "media_ai_embedding_coverage") return item.value >= 90 ? "Target: ≥ 90% (Met)" : "Target: ≥ 90% (Below)";
  if (item.key === "media_ai_suggestion_no_result_rate") return "Search Accuracy Benchmark";
  if (item.key === "media_ai_suggestion_acceptance_rate" || item.key === "media_ai_suggestion_dismissal_rate") return "Adoption Benchmark";
  if (item.key === "media_ai_shadow_top_change_rate") return "Algorithm A/B Test";
  
  return "Operational Benchmark";
}

function getServiceCategory(serviceName: string): string {
  const name = serviceName.toLowerCase();
  if (name.includes("gemini")) return "Multimodal AI Vision";
  if (name.includes("claude") || name.includes("anthropic")) return "AI Vision (Fallback)";
  if (name.includes("voyage")) return "Vector Embeddings";
  if (name.includes("facebook") || name.includes("meta")) return "Graph API Publishing";
  if (name.includes("postgres") || name.includes("database") || name.includes("sql")) return "Relational DB";
  if (name.includes("r2") || name.includes("cloudflare") || name.includes("storage")) return "Object Media Storage";
  if (name.includes("email") || name.includes("resend") || name.includes("smtp")) return "Transactional Mail";
  return "Infrastructure";
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
        <span className="sys-percent-tag">{item.usedPercent}% used</span>
      </div>

      <div className={`sys-meter sys-meter-${item.status.toLowerCase()}`} aria-label={`${item.usedPercent}% used`}>
        <span style={{ width: `${percentCapped}%` }} />
      </div>
    </div>
  );
}

function AiServiceCard({ item }: { item: ExternalServiceHealth }) {
  const hasQuota = item.quotaLimit !== null && item.quotaLimit !== undefined && item.quotaLimit > 0;
  const quotaRemaining = item.quotaRemaining ?? 0;
  const quotaLimit = item.quotaLimit ?? 1;
  const percentLeft = Math.min(100, Math.max(0, Math.round((quotaRemaining / quotaLimit) * 100)));
  const isGemini = item.service.toLowerCase().includes("gemini");

  return (
    <div className={`card-wrap sys-ai-card sys-ai-${item.status.toLowerCase()}`}>
      <div className="sys-ai-card-top">
        <div className="sys-ai-header-left">
          <span className="sys-ai-icon-box">
            <i className="ti ti-sparkles" aria-hidden="true" />
          </span>
          <div>
            <div className="sys-ai-tag-row">
              <span className="sys-service-category-tag">
                {isGemini ? "Primary Vision Provider" : "Standby Failover Vision"}
              </span>
            </div>
            <h3 className="sys-ai-title">{item.service}</h3>
          </div>
        </div>
        <StatusBadge status={item.status} />
      </div>

      <p className="sys-ai-detail">{item.detail || "Connected and responsive"}</p>

      {hasQuota ? (
        <div className="sys-service-quota-block">
          <div className="sys-quota-header-row">
            <span className="sys-quota-label">
              <i className="ti ti-coins" aria-hidden="true" /> Daily API Free-Tier Quota
            </span>
            <span
              className={`sys-quota-pill ${
                quotaRemaining === 0 ? "is-empty" : quotaRemaining <= 5 ? "is-low" : "is-healthy"
              }`}
            >
              <strong>{quotaRemaining}</strong> / {quotaLimit} Left ({percentLeft}%)
            </span>
          </div>
          <div
            className={`sys-meter sys-meter-quota sys-meter-${
              quotaRemaining === 0 ? "unhealthy" : quotaRemaining <= 5 ? "warning" : "healthy"
            }`}
            aria-label={`${quotaRemaining}/${quotaLimit} credits remaining`}
          >
            <span style={{ width: `${percentLeft}%` }} />
          </div>
        </div>
      ) : (
        <div className="sys-ai-standby-block">
          <i className="ti ti-shield-check" aria-hidden="true" />
          <span>Active fallback provider — processes captions when Gemini quota is exhausted.</span>
        </div>
      )}

      <div className="sys-service-meta-footer">
        {item.resetAt && (
          <div className="sys-service-expiry sys-service-reset-pill" title={`Quota resets at ${formatDate(item.resetAt)}`}>
            <i className="ti ti-clock-hour-4" aria-hidden="true" />
            <span>
              Resets {item.secondsUntilExpiry ? `in ${formatDurationSeconds(item.secondsUntilExpiry)}` : formatDate(item.resetAt)}
            </span>
          </div>
        )}

        {isGemini && (
          <span className="sys-service-fallback-badge">
            <i className="ti ti-arrows-exchange" aria-hidden="true" />
            Auto-routing to Claude Active
          </span>
        )}
      </div>
    </div>
  );
}

function InfraServiceCard({ item }: { item: ExternalServiceHealth }) {
  const categoryTag = getServiceCategory(item.service);
  return (
    <div className="card-wrap sys-infra-card">
      <div className="sys-infra-left">
        <span className="sys-card-icon-box sys-infra-icon">
          <i className={serviceIcon(item.service)} aria-hidden="true" />
        </span>
        <div className="sys-infra-info">
          <div className="sys-infra-title-row">
            <h4 className="sys-infra-title">{item.service}</h4>
            <span className="sys-service-category-tag">{categoryTag}</span>
          </div>
          <p className="sys-infra-detail">{item.detail || "Connected and operational"}</p>
        </div>
      </div>
      <div className="sys-infra-right">
        <StatusBadge status={item.status} />
      </div>
    </div>
  );
}

function JobTable({
  jobs,
  runningJobKey,
  hasFilters,
  onClearFilters,
  onRun,
}: {
  jobs: BackgroundJobHealth[];
  runningJobKey: string | null;
  hasFilters: boolean;
  onClearFilters: () => void;
  onRun: (job: BackgroundJobHealth) => void;
}) {
  const anyRunning = runningJobKey !== null;

  return (
    <div className="um-data-card um-table-card is-directory" style={{ marginTop: 0 }}>
      {jobs.length === 0 ? (
        <div className="um-empty-state">
          <div className="um-empty-icon" aria-hidden="true">
            <i className="ti ti-cpu" />
          </div>
          <p className="um-empty-title">
            {hasFilters ? "No matching background jobs found" : "No background jobs found"}
          </p>
          <p className="um-empty-sub">
            {hasFilters
              ? "Try changing your search terms or status filter."
              : "Background jobs will appear here once scheduled."}
          </p>
          {hasFilters && (
            <button
              type="button"
              className="um-empty-clear"
              onClick={onClearFilters}
            >
              Clear filters
            </button>
          )}
        </div>
      ) : (
        <div className="um-table-wrap">
          <table className="um-table sys-jobs-table">
            <thead>
              <tr>
                <th style={{ width: "32%", paddingLeft: "24px" }}>JOB NAME</th>
                <th style={{ width: "14%" }}>STATUS</th>
                <th style={{ width: "16%" }}>LAST RUN</th>
                <th style={{ width: "16%" }}>LAST SUCCESS</th>
                <th style={{ width: "10%" }}>DURATION</th>
                <th style={{ width: "12%", textAlign: "right", paddingRight: "24px" }}>ACTIONS</th>
              </tr>
            </thead>
            <tbody>
              {jobs.map((job) => {
                const running = runningJobKey === job.key;
                return (
                  <tr key={job.key}>
                    <td style={{ paddingLeft: "24px" }}>
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
                    <td style={{ textAlign: "right", paddingRight: "24px", whiteSpace: "nowrap" }}>
                      <button
                        type="button"
                        className="sys-btn sys-btn-ghost sys-btn-sm"
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
        </div>
      )}
    </div>
  );
}

function TokenTable({
  tokens,
  busyTokenId,
  canReauthorize,
  onReauthorize,
  manualEntryTokenId,
  manualTokenValue,
  onToggleManualEntry,
  onManualTokenValueChange,
  onSetManualToken,
}: {
  tokens: TokenStatus[];
  busyTokenId: string | null;
  canReauthorize: boolean;
  onReauthorize: (token: TokenStatus) => void;
  manualEntryTokenId: string | null;
  manualTokenValue: string;
  onToggleManualEntry: (tokenId: string) => void;
  onManualTokenValueChange: (value: string) => void;
  onSetManualToken: (tokenId: string) => void;
}) {
  if (tokens.length === 0) {
    return (
      <div className="um-data-card is-directory" style={{ marginTop: 0 }}>
        <div className="um-empty-state">
          <div className="um-empty-icon" aria-hidden="true">
            <i className="ti ti-brand-facebook" />
          </div>
          <p className="um-empty-title">No Facebook Page Tokens Configured</p>
          <p className="um-empty-sub">
            Set FACEBOOK_PAGE_ID and FACEBOOK_PAGE_ACCESS_TOKEN in the backend environment to connect a page — it appears here once the app restarts.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="um-data-card um-table-card is-directory" style={{ marginTop: 0 }}>
      <div className="um-table-wrap">
        <table className="um-table sys-token-table">
          <thead>
            <tr>
              <th style={{ width: "30%", paddingLeft: "24px" }}>CONNECTED PAGE</th>
              <th style={{ width: "18%" }}>STATUS</th>
              <th style={{ width: "20%" }}>EXPIRATION</th>
              <th style={{ width: "16%" }}>LAST VALIDATED</th>
              <th style={{ width: "16%", textAlign: "right", paddingRight: "24px" }}>ACTION</th>
            </tr>
          </thead>
          <tbody>
            {tokens.map((token) => (
              <Fragment key={token.id}>
                <tr>
                  <td style={{ paddingLeft: "24px" }}>
                    <div className="sys-page-cell">
                      <i className="ti ti-brand-facebook" />
                      <strong>Page ····{token.pageId.slice(-4) || "----"}</strong>
                    </div>
                  </td>
                  <td title={token.validationFailureReason ?? undefined}>
                    <StatusBadge status={tokenStatusToHealth(token.tokenStatus)} />
                  </td>
                  <td><span className="sys-date-text">{formatDate(token.expiresAt)}</span></td>
                  <td><span className="sys-date-text">{formatDate(token.lastValidatedAt)}</span></td>
                  <td style={{ textAlign: "right", paddingRight: "24px", whiteSpace: "nowrap" }}>
                    <div style={{ display: "flex", gap: 6, justifyContent: "flex-end", flexWrap: "nowrap" }}>
                      <button
                        type="button"
                        className="sys-btn sys-btn-ghost sys-btn-sm"
                        disabled={!canReauthorize || busyTokenId === token.id}
                        onClick={() => onReauthorize(token)}
                        title="Renew Facebook Page Access Token via Facebook OAuth"
                      >
                        <i className={busyTokenId === token.id && manualEntryTokenId !== token.id ? "ti ti-loader-2 sys-spin" : "ti ti-refresh"} aria-hidden="true" />
                        <span>Reauthorize</span>
                      </button>
                      <button
                        type="button"
                        className="sys-btn sys-btn-ghost sys-btn-sm"
                        disabled={!canReauthorize}
                        onClick={() => onToggleManualEntry(token.id)}
                        title="Paste a Page Access Token directly, without the OAuth flow"
                      >
                        <i className="ti ti-key" aria-hidden="true" />
                        <span>{manualEntryTokenId === token.id ? "Cancel" : "Set Manually"}</span>
                      </button>
                    </div>
                  </td>
                </tr>
                {manualEntryTokenId === token.id && (
                  <tr>
                    <td colSpan={5} style={{ paddingLeft: "24px", paddingRight: "24px" }}>
                      <div className="sys-manual-token-row">
                        <input
                          type="password"
                          className="sys-input"
                          placeholder="Paste the Page Access Token"
                          value={manualTokenValue}
                          onChange={(e) => onManualTokenValueChange(e.target.value)}
                          autoComplete="off"
                        />
                        <button
                          type="button"
                          className="sys-btn sys-btn-primary sys-btn-sm"
                          disabled={!manualTokenValue.trim() || busyTokenId === token.id}
                          onClick={() => onSetManualToken(token.id)}
                        >
                          <i className={busyTokenId === token.id ? "ti ti-loader-2 sys-spin" : "ti ti-device-floppy"} aria-hidden="true" />
                          <span>Save</span>
                        </button>
                        <span className="sys-manual-token-hint">
                          Must already be a Page Access Token for page ····{token.pageId.slice(-4) || "----"} — this cannot change which page is connected.
                        </span>
                      </div>
                    </td>
                  </tr>
                )}
              </Fragment>
            ))}
          </tbody>
        </table>
      </div>
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

function formatDurationSeconds(seconds: number) {
  if (seconds <= 0) return "now";
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  if (hours > 0) {
    return minutes > 0 ? `${hours}h ${minutes}m` : `${hours}h`;
  }
  return `${Math.max(1, minutes)}m`;
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
    case "missed_review_rate":
      return "ti ti-calendar-x";
    case "media_ai_queue_depth":
      return "ti ti-layers-linked";
    case "media_ai_queue_completion_rate":
      return "ti ti-list-check";
    case "media_ai_voyage_failure_rate":
      return "ti ti-plug-x";
    case "media_ai_embedding_coverage":
      return "ti ti-vector";
    case "media_ai_suggestion_no_result_rate":
      return "ti ti-search-off";
    case "media_ai_suggestion_acceptance_rate":
      return "ti ti-thumb-up";
    case "media_ai_suggestion_dismissal_rate":
      return "ti ti-thumb-down";
    case "media_ai_shadow_top_change_rate":
      return "ti ti-arrows-shuffle";
    default:
      return "ti ti-chart-bar";
  }
}

function serviceIcon(service: string) {
  const name = service.toLowerCase();
  if (name.includes("gemini") || name.includes("google")) return "ti ti-sparkles";
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
    return `${item.value} fast-track submission${item.value === 1 ? "" : "s"}`;
  }
  return `${item.sampleSize} record${item.sampleSize === 1 ? "" : "s"}`;
}

function isNoActivityMetric(item: OperationalMetric) {
  if (item.key === "live_event_fast_track_volume") {
    return item.value === 0 && item.sampleSize === 0;
  }
  return item.sampleSize === 0;
}

function formatJobName(jobName: string) {
  if (!/[a-z][A-Z]/.test(jobName) && !jobName.endsWith("Job")) return jobName;
  return jobName.replace(/([a-z])([A-Z])/g, "$1 $2").replace(/\s?Job$/, "");
}

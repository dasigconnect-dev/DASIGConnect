import { useCallback, useEffect, useMemo, useState } from "react";
import {
  downloadAuditLogCsv,
  getAuditCategories,
  getAuditLogs,
  type AuditEntityType,
  type AuditLogCategory,
  type AuditLogEntry,
  type AuditLogFilterParams,
  type AuditMetadataOptions,
} from "../../api/auditLogApi";
import { useToast } from "../../context/ToastContext";
import type { User } from "../../types/auth.types";
import BrandedSelect from "../../components/ui/BrandedSelect";
import AuditDetailModal from "./AuditDetailModal";
import "../../styles/audit-log.css";
import "../../styles/dasig-loader.css";

interface Props {
  user: User;
}

type DatePreset = "today" | "7d" | "30d" | "all" | "custom";

function formatDate(iso: string) {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("en-PH", {
    month: "short",
    day: "numeric",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
    hour12: true,
  });
}

function timeAgo(iso: string) {
  if (!iso) return "";
  const diff = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return "just now";
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days}d ago`;
  return "";
}

function categoryBadgeClass(category: string): string {
  switch (category) {
    case "APPROVAL":
    case "PUBLISHING":
      return "sp-approved";
    case "REJECTION":
    case "SECURITY":
      return "pill-failed";
    case "EDIT_AND_REVISION":
    case "RESCHEDULE_AND_OVERRIDE":
      return "pill-revision";
    case "ACCOUNT_MANAGEMENT":
    case "INSTITUTION_MANAGEMENT":
    case "CONFIGURATION":
    case "MEDIA_LIFECYCLE":
    default:
      return "sp-scheduled";
  }
}

function getPresetDates(preset: DatePreset): { start?: string; end?: string } {
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, "0");
  const toDateStr = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  const todayStr = toDateStr(now);

  switch (preset) {
    case "today":
      return { start: todayStr, end: todayStr };
    case "7d": {
      const d = new Date();
      d.setDate(d.getDate() - 7);
      return { start: toDateStr(d), end: todayStr };
    }
    case "30d": {
      const d = new Date();
      d.setDate(d.getDate() - 30);
      return { start: toDateStr(d), end: todayStr };
    }
    case "all":
    default:
      return {};
  }
}

const DEFAULT_CATEGORIES = [
  { key: "APPROVAL", label: "Approvals" },
  { key: "REJECTION", label: "Rejections" },
  { key: "EDIT_AND_REVISION", label: "Edits & Revisions" },
  { key: "RESCHEDULE_AND_OVERRIDE", label: "Reschedules & Overrides" },
  { key: "PUBLISHING", label: "Publishing" },
  { key: "ACCOUNT_MANAGEMENT", label: "Account Management" },
  { key: "INSTITUTION_MANAGEMENT", label: "Institution Management" },
  { key: "MEDIA_LIFECYCLE", label: "Media Lifecycle" },
  { key: "CONFIGURATION", label: "Configuration" },
  { key: "SECURITY", label: "Security" },
];

const DEFAULT_ENTITY_TYPES = [
  { key: "SUBMISSION", label: "Submissions" },
  { key: "USER", label: "Users" },
  { key: "INSTITUTION", label: "Institutions" },
  { key: "MEDIA_ASSET", label: "Media Assets" },
  { key: "SYSTEM_SETTING", label: "System Settings" },
];

export default function AuditLogScreen({ user: _user }: Props) {
  const toast = useToast();

  // Filter States
  const [datePreset, setDatePreset] = useState<DatePreset>("all");
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [category, setCategory] = useState<AuditLogCategory | "">("");
  const [entityType, setEntityType] = useState<AuditEntityType | "">("");
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const pageSize = 20;

  // Data States
  const [logs, setLogs] = useState<AuditLogEntry[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [exporting, setExporting] = useState(false);
  const [metadataOptions, setMetadataOptions] = useState<AuditMetadataOptions | null>(null);

  // Selected Log for Modal
  const [selectedEntry, setSelectedEntry] = useState<AuditLogEntry | null>(null);

  // Load Categories on mount
  useEffect(() => {
    getAuditCategories()
      .then((data) => setMetadataOptions(data))
      .catch(() => {
        // Fallback default categories if endpoint is slow
      });
  }, []);

  const filterParams: AuditLogFilterParams = useMemo(() => {
    return {
      startDate: startDate ? `${startDate}T00:00:00Z` : undefined,
      endDate: endDate ? `${endDate}T23:59:59Z` : undefined,
      category: category || undefined,
      entityType: entityType || undefined,
      search: search.trim() || undefined,
      page,
      size: pageSize,
    };
  }, [startDate, endDate, category, entityType, search, page]);

  const loadData = useCallback(
    async (signal?: AbortSignal) => {
      setLoading(true);
      setLoadError("");
      try {
        const response = await getAuditLogs(filterParams, signal);
        if (signal?.aborted) return;
        setLogs(response.content || []);
        setTotalElements(response.totalElements || 0);
        setTotalPages(response.totalPages || 0);
        setLoadError("");
        setLoading(false);
      } catch (err: unknown) {
        if (signal?.aborted) return;
        const isCanceled =
          (err as { code?: string })?.code === "ERR_CANCELED" ||
          (err as { name?: string })?.name === "CanceledError" ||
          (err as { name?: string })?.name === "AbortError";
        if (isCanceled) return;

        setLoadError(
          (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
            "Unable to connect to the audit service. Please retry."
        );
        setLoading(false);
      }
    },
    [filterParams]
  );

  useEffect(() => {
    const controller = new AbortController();
    void loadData(controller.signal);
    return () => controller.abort();
  }, [loadData]);

  function handlePresetChange(preset: DatePreset) {
    setLoading(true);
    setDatePreset(preset);
    if (preset !== "custom") {
      const { start, end } = getPresetDates(preset);
      setStartDate(start ?? "");
      setEndDate(end ?? "");
      setPage(0);
    }
  }

  function handleResetFilters() {
    setLoading(true);
    setDatePreset("all");
    setStartDate("");
    setEndDate("");
    setCategory("");
    setEntityType("");
    setSearch("");
    setPage(0);
  }

  const isFiltered = Boolean(
    startDate || endDate || category || entityType || search || datePreset !== "all"
  );

  // DOST Region 7 CSV Export
  async function handleExport() {
    setExporting(true);
    try {
      await downloadAuditLogCsv(filterParams);
      toast.success("DOST Region 7 Audit Log exported successfully.");
    } catch {
      toast.error("Failed to export audit log CSV.");
    } finally {
      setExporting(false);
    }
  }

  const categoriesList = metadataOptions?.categories?.length ? metadataOptions.categories : DEFAULT_CATEGORIES;
  const entityTypesList = metadataOptions?.entityTypes?.length ? metadataOptions.entityTypes : DEFAULT_ENTITY_TYPES;
  const categoryOptions = [
    { value: "", label: "All Categories" },
    ...categoriesList.map((c) => ({ value: c.key, label: c.label })),
  ];

  const entityOptions = [
    { value: "", label: "All Entities" },
    ...entityTypesList.map((et) => ({ value: et.key, label: et.label })),
  ];

  return (
    <div id="screen-audit" style={{ background: "var(--d-bg)" }}>
      <div className="dash-body audit-screen-container">

        {/* ── Standard Header View ── */}
        <div className="dash-view-header audit-header-row">
          <div>
            <h1 className="dash-view-title">Audit Log Review</h1>
            <p className="dash-view-desc">
              Immutable record of system state changes, workflow decisions, and administrative actions
            </p>
          </div>

          <div className="audit-header-actions">
            <button
              type="button"
              className="notif-btn notif-btn-ghost"
              onClick={() => void loadData()}
              disabled={loading}
              title="Refresh audit log"
            >
              <i className={`ti ti-refresh${loading ? " spin" : ""}`} style={{ fontSize: 14 }} />
              <span>Refresh</span>
            </button>

            <button
              type="button"
              className="audit-export-btn"
              onClick={handleExport}
              disabled={exporting || loading}
              title="Download formatted CSV report for DOST Region 7 governance reporting"
            >
              {exporting ? (
                <>
                  <i className="ti ti-loader-2 spin" style={{ fontSize: 14 }} />
                  <span>Exporting...</span>
                </>
              ) : (
                <>
                  <i className="ti ti-download" style={{ fontSize: 14 }} />
                  <span>Export DOST-7 CSV</span>
                </>
              )}
            </button>
          </div>
        </div>

        {/* ── Toolbar Card (Filters + Search) ── */}
        <div className="card-wrap audit-toolbar-card">
          <div className="audit-toolbar-inner">
            {/* Left Filter Group */}
            <div className="audit-filters-group">
              {/* Time Window Pill Tabs */}
              <div className="sub-status-tabs" role="group" aria-label="Time window filter">
                <button
                  type="button"
                  className={`sub-status-tab${datePreset === "all" ? " is-active" : ""}`}
                  onClick={() => handlePresetChange("all")}
                >
                  <span>All</span>
                  <span className="sub-status-tab-count">
                    {datePreset === "all" ? totalElements : "•"}
                  </span>
                </button>
                <button
                  type="button"
                  className={`sub-status-tab${datePreset === "today" ? " is-active" : ""}`}
                  onClick={() => handlePresetChange("today")}
                >
                  <span>Today</span>
                  <span className="sub-status-tab-count">
                    {datePreset === "today" ? totalElements : "•"}
                  </span>
                </button>
                <button
                  type="button"
                  className={`sub-status-tab${datePreset === "7d" ? " is-active" : ""}`}
                  onClick={() => handlePresetChange("7d")}
                >
                  <span>7D</span>
                  <span className="sub-status-tab-count">
                    {datePreset === "7d" ? totalElements : "•"}
                  </span>
                </button>
                <button
                  type="button"
                  className={`sub-status-tab${datePreset === "30d" ? " is-active" : ""}`}
                  onClick={() => handlePresetChange("30d")}
                >
                  <span>30D</span>
                  <span className="sub-status-tab-count">
                    {datePreset === "30d" ? totalElements : "•"}
                  </span>
                </button>
              </div>

              {/* Category Dropdown */}
              <div className="audit-filter-item">
                <BrandedSelect
                  value={category}
                  onChange={(v) => {
                    setLoading(true);
                    setCategory(v as AuditLogCategory);
                    setPage(0);
                  }}
                  ariaLabel="Filter by category"
                  options={categoryOptions}
                />
              </div>

              {/* Entity Dropdown */}
              <div className="audit-filter-item">
                <BrandedSelect
                  value={entityType}
                  onChange={(v) => {
                    setLoading(true);
                    setEntityType(v as AuditEntityType);
                    setPage(0);
                  }}
                  ariaLabel="Filter by entity type"
                  options={entityOptions}
                />
              </div>
            </div>

            {/* Right Search Box */}
            <div className="im-search-wrap" style={{ margin: 0 }}>
              <i className="ti ti-search im-search-icon" aria-hidden="true" />
              <input
                className="im-search-input"
                type="search"
                placeholder="Search actor or action..."
                value={search}
                onChange={(e) => {
                  setLoading(true);
                  setSearch(e.target.value);
                  setPage(0);
                }}
                aria-label="Search audit log"
              />
              {search && (
                <button
                  type="button"
                  className="im-search-clear"
                  onClick={() => {
                    setLoading(true);
                    setSearch("");
                    setPage(0);
                  }}
                  aria-label="Clear search"
                  style={{ position: "absolute", right: 8, background: "none", border: "none", cursor: "pointer", color: "var(--d-muted)" }}
                >
                  <i className="ti ti-x" />
                </button>
              )}
            </div>
          </div>
        </div>

        {/* ── Matching Count Subheader Row ── */}
        <div className="audit-meta-row">
          <span className="audit-count-label">
            {loading ? (
              "Loading audit events..."
            ) : totalElements > 0 ? (
              `Showing ${page * pageSize + 1}–${Math.min((page + 1) * pageSize, totalElements)} of ${totalElements} audit events`
            ) : (
              "Showing 0–0 of 0 audit events"
            )}
          </span>
          <span
            className="audit-entity-badge"
            title="Records are immutable and tamper-evident"
            style={{ display: "inline-flex", alignItems: "center", gap: 5, fontSize: 11.5, color: "#1877F2", background: "#EFF6FF", border: "1px solid #BFDBFE", borderRadius: 999, padding: "3px 10px", fontWeight: 600 }}
          >
            <i className="ti ti-clock-shield" style={{ fontSize: 13 }} />
            <span>Immutable Trail</span>
          </span>
        </div>

        {/* ── Main Data Table Card ── */}
        <div className="card-wrap audit-table-card">
          {loading && (
            <div
              style={{
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                minHeight: "360px",
                width: "100%",
                padding: "64px 24px",
              }}
              role="status"
              aria-label="Loading Audit Log"
            >
              <div className="dc-dot-triangle-container">
                <div className="dc-dot-triangle-label">
                  <span>Loading Audit Log</span>
                  <span className="dc-dot-triangle-label-dots">
                    <span className="dc-dot-triangle-dot-char">.</span>
                    <span className="dc-dot-triangle-dot-char">.</span>
                    <span className="dc-dot-triangle-dot-char">.</span>
                  </span>
                </div>
                <div className="loader-stage" style={{ display: "flex", alignItems: "center", justifyContent: "center" }}>
                  <div className="loader-dots" />
                </div>
              </div>
            </div>
          )}

          {!loading && loadError && logs.length === 0 && (
            <div className="audit-state-box">
              <i className="ti ti-alert-triangle" style={{ fontSize: 32, color: "#ef4444", marginBottom: 8 }} />
              <h3 style={{ fontSize: 15, fontWeight: 700, color: "#0C1D3D", margin: "0 0 4px" }}>
                Could Not Load the Audit Log
              </h3>
              <p style={{ fontSize: 13, color: "var(--d-muted)", margin: "0 0 16px" }}>{loadError}</p>
              <button
                type="button"
                className="notif-btn notif-btn-ghost"
                onClick={() => void loadData()}
              >
                <i className="ti ti-refresh" /> Retry
              </button>
            </div>
          )}

          {!loading && !loadError && logs.length === 0 && (
            <div className="audit-state-box">
              <i className="ti ti-search-off" style={{ fontSize: 32, color: "#94a3b8", marginBottom: 8 }} />
              <h3 style={{ fontSize: 15, fontWeight: 700, color: "#0C1D3D", margin: "0 0 4px" }}>
                No Audit Entries Found
              </h3>
              <p style={{ fontSize: 13, color: "var(--d-muted)", margin: "0 0 14px", maxWidth: 420 }}>
                No records match your selected filters. Try broadening your date range or clearing search keywords.
              </p>
              {isFiltered && (
                <button
                  type="button"
                  className="notif-btn notif-btn-ghost"
                  onClick={handleResetFilters}
                >
                  <i className="ti ti-rotate-clockwise" /> Reset Filters
                </button>
              )}
            </div>
          )}

          {!loading && !loadError && logs.length > 0 && (
            <>
              <div className="audit-table-wrap">
                <table className="data-table" id="audit-main-table">
                  <thead>
                    <tr>
                      <th style={{ width: "22%" }}>ACTOR</th>
                      <th style={{ width: "20%" }}>TIMESTAMP (PHT)</th>
                      <th style={{ width: "18%" }}>ACTION / CATEGORY</th>
                      <th style={{ width: "18%" }}>AFFECTED ENTITY</th>
                      <th style={{ width: "16%" }}>SUMMARY</th>
                      <th style={{ width: "6%", textAlign: "right" }}>DETAIL</th>
                    </tr>
                  </thead>
                  <tbody className="act-table-animate">
                    {logs.map((entry) => {
                      const relative = timeAgo(entry.timestamp);
                      const formatted = formatDate(entry.timestamp);
                      const isEntityActive = entry.entity.exists;
                      const badgeClass = categoryBadgeClass(entry.category);

                      return (
                        <tr
                          key={entry.id}
                          onClick={() => setSelectedEntry(entry)}
                          style={{ cursor: "pointer" }}
                        >
                          {/* Actor */}
                          <td>
                            <div className="audit-cell-actor">
                              <div className="audit-avatar-circle">
                                {entry.actor?.name ? entry.actor.name.charAt(0).toUpperCase() : "S"}
                              </div>
                              <div className="audit-actor-text">
                                <span className="audit-actor-name-row">
                                  <strong>{entry.actor?.name || entry.actor?.email || "System Automation"}</strong>
                                  {entry.actor?.role === "SUPER_ADMINISTRATOR" && (
                                    <span className="audit-actor-role">
                                      • Super Admin
                                    </span>
                                  )}
                                </span>
                                {entry.actor?.name && entry.actor?.email && (
                                  <span className="audit-actor-sub">{entry.actor.email}</span>
                                )}
                              </div>
                            </div>
                          </td>

                          {/* Timestamp */}
                          <td>
                            <div className="audit-cell-timestamp">
                              <span className="act-title" style={{ fontSize: "12.5px" }}>{formatted}</span>
                              {relative && <span className="act-category">{relative}</span>}
                            </div>
                          </td>

                          {/* Category & Action */}
                          <td>
                            <div className="audit-cell-cat">
                              <span className={`status-pill ${badgeClass}`}>
                                {entry.categoryLabel}
                              </span>
                              <span className="audit-action-label">
                                {entry.actionLabel}
                              </span>
                            </div>
                          </td>

                          {/* Affected Entity */}
                          <td>
                            {isEntityActive ? (
                              entry.entity.jumpUrl ? (
                                <a
                                  href={entry.entity.jumpUrl}
                                  className="audit-entity-link"
                                  onClick={(e) => e.stopPropagation()}
                                  title="Jump to entity"
                                >
                                  <i className="ti ti-link" style={{ fontSize: 12 }} />
                                  <span>{entry.entity.label}</span>
                                </a>
                              ) : (
                                <span className="audit-entity-tag">
                                  {entry.entity.label}
                                </span>
                              )
                            ) : (
                              <span className="audit-entity-unavailable" title="Entity no longer available">
                                <i className="ti ti-alert-circle" style={{ fontSize: 12 }} />
                                <span>Deleted / Unavailable</span>
                              </span>
                            )}
                          </td>

                          {/* Summary */}
                          <td>
                            <div className="audit-summary-text" title={entry.summary}>
                              {entry.summary || "—"}
                            </div>
                          </td>

                          {/* Action Details Button */}
                          <td style={{ textAlign: "right" }}>
                            <button
                              type="button"
                              className="audit-view-btn"
                              onClick={(e) => {
                                e.stopPropagation();
                                setSelectedEntry(entry);
                              }}
                              title="Inspect full audit event details and diffs"
                            >
                              <i className="ti ti-eye" style={{ fontSize: 13 }} />
                              <span>View</span>
                            </button>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>

              {/* Pagination Controls */}
              <div className="audit-pagination-row">
                <span className="audit-pagination-info">
                  Page {page + 1} of {Math.max(1, totalPages)}
                </span>
                <div className="audit-pagination-nav">
                  <button
                    type="button"
                    className="audit-page-btn"
                    disabled={page <= 0}
                    onClick={() => {
                      setLoading(true);
                      setPage((p) => Math.max(0, p - 1));
                    }}
                  >
                    <i className="ti ti-chevron-left" />
                    Previous
                  </button>
                  <button
                    type="button"
                    className="audit-page-btn"
                    disabled={page >= totalPages - 1}
                    onClick={() => {
                      setLoading(true);
                      setPage((p) => p + 1);
                    }}
                  >
                    Next
                    <i className="ti ti-chevron-right" />
                  </button>
                </div>
              </div>
            </>
          )}
        </div>

        {/* ── Detail Modal ── */}
        {selectedEntry && (
          <AuditDetailModal
            entry={selectedEntry}
            onClose={() => setSelectedEntry(null)}
          />
        )}
      </div>
    </div>
  );
}

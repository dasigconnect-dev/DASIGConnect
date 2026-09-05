import { useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  downloadAuditLogCsv,
  formatActorRole,
  getAuditCategories,
  getAuditLogs,
  type AuditEntityType,
  type AuditLogCategory,
  type AuditLogEntry,
  type AuditLogFilterParams,
} from "../../api/auditLogApi";
import { useToast } from "../../context/ToastContext";
import type { User } from "../../types/auth.types";
import { authenticatedQueryMeta } from "../../lib/queryClient";
import { queryKeys } from "../../lib/queryKeys";
import BrandedSelect from "../../components/ui/BrandedSelect";
import AuditDetailModal from "./AuditDetailModal";
import { SkeletonRows } from "../user-management/components/LoadingPrimitives";
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

const AUDIT_LOG_STALE_TIME_MS = 15_000;
const AUDIT_METADATA_STALE_TIME_MS = 5 * 60_000;

function getUserCacheScope(user: User) {
  return user.id ?? user.email.trim().toLowerCase();
}

function isCanceledError(err: unknown) {
  return (
    (err as { code?: string })?.code === "ERR_CANCELED" ||
    (err as { name?: string })?.name === "CanceledError" ||
    (err as { name?: string })?.name === "AbortError"
  );
}

function getAuditLoadError(err: unknown) {
  if (isCanceledError(err)) return "";
  return (
    (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
    "Unable to connect to the audit service. Please retry."
  );
}

export default function AuditLogScreen({ user }: Props) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const userScope = getUserCacheScope(user);

  // Filter States
  const [datePreset, setDatePreset] = useState<DatePreset>("all");
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [category, setCategory] = useState<AuditLogCategory | "">("");
  const [entityType, setEntityType] = useState<AuditEntityType | "">("");
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const pageSize = 20;

  const [exporting, setExporting] = useState(false);

  // Selected Log for Modal
  const [selectedEntry, setSelectedEntry] = useState<AuditLogEntry | null>(null);

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

  const metadataQuery = useQuery({
    queryKey: queryKeys.auditLog.metadata({
      role: user.role,
      userId: userScope,
    }),
    queryFn: ({ signal }) => getAuditCategories(signal),
    staleTime: AUDIT_METADATA_STALE_TIME_MS,
    meta: authenticatedQueryMeta,
  });

  const auditLogQuery = useQuery({
    queryKey: queryKeys.auditLog.page({
      role: user.role,
      userId: userScope,
      page,
      pageSize,
      startDate: filterParams.startDate,
      endDate: filterParams.endDate,
      category: filterParams.category,
      entityType: filterParams.entityType,
      search: filterParams.search,
    }),
    queryFn: ({ signal }) => getAuditLogs(filterParams, signal),
    staleTime: AUDIT_LOG_STALE_TIME_MS,
    meta: authenticatedQueryMeta,
  });

  const auditPage = auditLogQuery.data;
  const metadataOptions = metadataQuery.data ?? null;
  const logs = auditPage?.content ?? [];
  const totalElements = auditPage?.totalElements ?? 0;
  const totalPages = auditPage?.totalPages ?? 0;
  const loading = auditLogQuery.isLoading || auditLogQuery.isFetching;
  const loadError = auditLogQuery.error ? getAuditLoadError(auditLogQuery.error) : "";

  function refreshAuditLog() {
    void queryClient.invalidateQueries({ queryKey: ["audit-log"] });
  }

  function handlePresetChange(preset: DatePreset) {
    setDatePreset(preset);
    if (preset !== "custom") {
      const { start, end } = getPresetDates(preset);
      setStartDate(start ?? "");
      setEndDate(end ?? "");
      setPage(0);
    }
  }

  function handleResetFilters() {
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
              onClick={refreshAuditLog}
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
            style={{ display: "inline-flex", alignItems: "center", gap: 5, fontSize: 11.5, color: "var(--d-blue, #0B5FCC)", background: "#EFF6FF", border: "1px solid #BFDBFE", borderRadius: 999, padding: "3px 10px", fontWeight: 600 }}
          >
            <i className="ti ti-clock-shield" style={{ fontSize: 13 }} />
            <span>Immutable Trail</span>
          </span>
        </div>

        {/* ── Main Data Table Card ── */}
        {/* ── Main Data Table Card ── */}
        <div className="card-wrap audit-table-card">
          {!loading && loadError && logs.length === 0 ? (
            <div className="audit-state-box">
              <i className="ti ti-alert-triangle" style={{ fontSize: 32, color: "#ef4444", marginBottom: 8 }} />
              <h3 style={{ fontSize: 15, fontWeight: 700, color: "#0C1D3D", margin: "0 0 4px" }}>
                Could Not Load the Audit Log
              </h3>
              <p style={{ fontSize: 13, color: "var(--d-muted)", margin: "0 0 16px" }}>{loadError}</p>
              <button
                type="button"
                className="notif-btn notif-btn-ghost"
                onClick={refreshAuditLog}
              >
                <i className="ti ti-refresh" /> Retry
              </button>
            </div>
          ) : !loading && logs.length === 0 ? (
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
          ) : (
            <>
              <div className="audit-table-wrap">
                <table className="data-table" id="audit-main-table">
                  <thead>
                    <tr>
                      <th style={{ width: "24%" }}>ACTOR</th>
                      <th style={{ width: "20%" }}>TIMESTAMP (PHT)</th>
                      <th style={{ width: "18%" }}>ACTION / CATEGORY</th>
                      <th style={{ width: "18%" }}>AFFECTED ENTITY</th>
                      <th style={{ width: "20%" }}>SUMMARY</th>
                    </tr>
                  </thead>
                  <tbody className={loading ? undefined : "act-table-animate"}>
                    {loading ? (
                      <SkeletonRows rows={6} columns={5} />
                    ) : (
                      logs.map((entry) => {
                        const relative = timeAgo(entry.timestamp);
                        const formatted = formatDate(entry.timestamp);
                        const isEntityActive = entry.entity.exists;
                        const badgeClass = categoryBadgeClass(entry.category);

                        return (
                          <tr
                            key={entry.id}
                            className="audit-clickable-row"
                            onClick={() => setSelectedEntry(entry)}
                            title="Click to view full event details and diffs"
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
                                    {entry.actor && (
                                      <span className="audit-actor-role">
                                        • {formatActorRole(entry.actor)}
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
                          </tr>
                        );
                      })
                    )}
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

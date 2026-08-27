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
import "../../styles/audit-log.css";
import AuditDetailModal from "./AuditDetailModal";

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

function categoryClass(category: string): string {
  switch (category) {
    case "APPROVAL": return "cat-approval";
    case "REJECTION": return "cat-rejection";
    case "EDIT_AND_REVISION": return "cat-edit";
    case "RESCHEDULE_AND_OVERRIDE": return "cat-reschedule";
    case "PUBLISHING": return "cat-publish";
    case "ACCOUNT_MANAGEMENT": return "cat-account";
    case "INSTITUTION_MANAGEMENT": return "cat-institution";
    case "MEDIA_LIFECYCLE": return "cat-media";
    case "CONFIGURATION": return "cat-config";
    case "SECURITY": return "cat-security";
    default: return "cat-other";
  }
}

function getPresetDates(preset: DatePreset): { start?: string; end?: string } {
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, "0");
  const toDateStr = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;

  const todayStr = toDateStr(now);

  switch (preset) {
    case "today": {
      return { start: todayStr, end: todayStr };
    }
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
      try {
        const response = await getAuditLogs(filterParams, signal);
        if (signal?.aborted) return;
        setLogs(response.content || []);
        setTotalElements(response.totalElements || 0);
        setTotalPages(response.totalPages || 0);
      } catch (err: unknown) {
        if ((err as { name?: string }).name === "CanceledError") return;
        toast.error("Unable to load audit logs. Please try again.");
      } finally {
        setLoading(false);
      }
    },
    [filterParams, toast]
  );

  useEffect(() => {
    const controller = new AbortController();
    void loadData(controller.signal);
    return () => controller.abort();
  }, [loadData]);

  // Handle Preset Changes
  function handlePresetChange(newPreset: DatePreset) {
    setDatePreset(newPreset);
    if (newPreset !== "custom") {
      const { start, end } = getPresetDates(newPreset);
      setStartDate(start ?? "");
      setEndDate(end ?? "");
      setPage(0);
    }
  }

  function handleCustomDateChange(start: string, end: string) {
    setDatePreset("custom");
    setStartDate(start);
    setEndDate(end);
    setPage(0);
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
      toast.error("Failed to export audit log CSV. You can retry without losing your filters.");
    } finally {
      setExporting(false);
    }
  }

  return (
    <div className="audit-screen">
      {/* ── Screen Header ── */}
      <div className="audit-header">
        <div className="audit-title-group">
          <h1>
            <i className="ti ti-clipboard-list" />
            Audit Log Review
          </h1>
          <p className="audit-subtitle">
            Immutable record of all system state changes, workflow decisions, and administrative actions.
          </p>
        </div>
        <div className="audit-toolbar">
          <button
            type="button"
            className="audit-export-btn"
            onClick={handleExport}
            disabled={exporting || loading}
            title="Download formatted CSV report for DOST Region 7 governance reporting"
          >
            {exporting ? (
              <>
                <div className="spinner-ring spinner-ring-xs" />
                Exporting...
              </>
            ) : (
              <>
                <i className="ti ti-download" />
                Export DOST-7 CSV
              </>
            )}
          </button>
        </div>
      </div>

      {/* ── Overview Summary Banner ── */}
      <div className="audit-overview">
        <div className="audit-overview-info">
          <div className="audit-overview-icon">
            <i className="ti ti-shield-check" />
          </div>
          <div className="audit-overview-text">
            <strong>System Governance Record</strong>
            <span>
              {isFiltered ? "Filtered log view active" : "Showing all-time immutable audit trail"}
            </span>
          </div>
        </div>
        <div className="audit-overview-counts">
          <div className="audit-count-item">
            <span>Matching Events</span>
            <strong>{totalElements.toLocaleString()}</strong>
          </div>
          <div className="audit-count-item">
            <span>Total Pages</span>
            <strong>{totalPages.toLocaleString()}</strong>
          </div>
        </div>
      </div>

      {/* ── Filter & Search Panel ── */}
      <div className="audit-filter-panel">
        {/* Date Presets */}
        <div className="audit-filter-presets">
          <span className="audit-preset-label">Time Window:</span>
          <button
            type="button"
            className={`audit-preset-pill ${datePreset === "today" ? "active" : ""}`}
            onClick={() => handlePresetChange("today")}
          >
            Today
          </button>
          <button
            type="button"
            className={`audit-preset-pill ${datePreset === "7d" ? "active" : ""}`}
            onClick={() => handlePresetChange("7d")}
          >
            Last 7 Days
          </button>
          <button
            type="button"
            className={`audit-preset-pill ${datePreset === "30d" ? "active" : ""}`}
            onClick={() => handlePresetChange("30d")}
          >
            Last 30 Days
          </button>
          <button
            type="button"
            className={`audit-preset-pill ${datePreset === "all" ? "active" : ""}`}
            onClick={() => handlePresetChange("all")}
          >
            All Time
          </button>
          {datePreset === "custom" && (
            <span className="audit-preset-pill active">Custom Range</span>
          )}
        </div>

        {/* Filter Input Grid */}
        <div className="audit-filter-grid">
          {/* Start Date */}
          <div className="audit-filter-field">
            <label htmlFor="audit-start-date">From Date</label>
            <input
              id="audit-start-date"
              type="date"
              className="audit-input"
              value={startDate}
              onChange={(e) => handleCustomDateChange(e.target.value, endDate)}
            />
          </div>

          {/* End Date */}
          <div className="audit-filter-field">
            <label htmlFor="audit-end-date">To Date</label>
            <input
              id="audit-end-date"
              type="date"
              className="audit-input"
              value={endDate}
              onChange={(e) => handleCustomDateChange(startDate, e.target.value)}
            />
          </div>

          {/* Action Category Dropdown */}
          <div className="audit-filter-field">
            <label htmlFor="audit-category">Action Category</label>
            <select
              id="audit-category"
              className="audit-select"
              value={category}
              onChange={(e) => {
                setCategory(e.target.value as AuditLogCategory);
                setPage(0);
              }}
            >
              <option value="">All Categories</option>
              {metadataOptions?.categories.map((c) => (
                <option key={c.key} value={c.key}>
                  {c.label}
                </option>
              ))}
            </select>
          </div>

          {/* Entity Type Dropdown */}
          <div className="audit-filter-field">
            <label htmlFor="audit-entity-type">Affected Entity</label>
            <select
              id="audit-entity-type"
              className="audit-select"
              value={entityType}
              onChange={(e) => {
                setEntityType(e.target.value as AuditEntityType);
                setPage(0);
              }}
            >
              <option value="">All Entities</option>
              {metadataOptions?.entityTypes.map((et) => (
                <option key={et.key} value={et.key}>
                  {et.label}
                </option>
              ))}
            </select>
          </div>

          {/* Search Input */}
          <div className="audit-filter-field audit-search-field">
            <label htmlFor="audit-search">Search Actor / Action</label>
            <i className="ti ti-search" />
            <input
              id="audit-search"
              type="text"
              className="audit-input"
              placeholder="Search by actor or keyword..."
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(0);
              }}
            />
          </div>

          {/* Clear Filters Button */}
          {isFiltered && (
            <div className="audit-filter-field" style={{ alignSelf: "flex-end" }}>
              <button
                type="button"
                className="audit-reset-btn"
                onClick={handleResetFilters}
                title="Clear all active filters"
              >
                <i className="ti ti-rotate-clockwise" />
                Clear Filters
              </button>
            </div>
          )}
        </div>
      </div>

      {/* ── Table Card ── */}
      <div className="audit-table-card">
        {loading && (
          <div className="audit-state-container">
            <div className="spinner-ring" />
            <span>Loading audit log entries...</span>
          </div>
        )}

        {!loading && logs.length === 0 && (
          <div className="audit-state-container">
            <i className="ti ti-search-off" />
            <h3>No Audit Entries Found</h3>
            <p>
              No audit records match the selected filter criteria. Try adjusting your date range,
              clearing search terms, or resetting filters.
            </p>
            {isFiltered && (
              <button
                type="button"
                className="audit-export-btn"
                style={{ background: "#f1f5f9", color: "#334155" }}
                onClick={handleResetFilters}
              >
                Reset All Filters
              </button>
            )}
          </div>
        )}

        {!loading && logs.length > 0 && (
          <>
            <div className="audit-table-wrap">
              <table className="audit-table">
                <thead>
                  <tr>
                    <th>Timestamp (PHT)</th>
                    <th>Actor</th>
                    <th>Action Category</th>
                    <th>Affected Entity</th>
                    <th>Summary / Reason</th>
                    <th style={{ textAlign: "right" }}>Details</th>
                  </tr>
                </thead>
                <tbody>
                  {logs.map((entry) => {
                    const relative = timeAgo(entry.timestamp);
                    const formatted = formatDate(entry.timestamp);
                    const isEntityActive = entry.entity.exists;

                    return (
                      <tr key={entry.id}>
                        {/* Timestamp */}
                        <td>
                          <div className="audit-timestamp-cell">
                            <span className="audit-timestamp-primary">{formatted}</span>
                            {relative && (
                              <span className="audit-timestamp-relative">{relative}</span>
                            )}
                          </div>
                        </td>

                        {/* Actor */}
                        <td>
                          <div className="audit-actor-cell">
                            <div className="audit-avatar">
                              {entry.actor?.name
                                ? entry.actor.name.charAt(0).toUpperCase()
                                : "S"}
                            </div>
                            <div className="audit-actor-info">
                              <span className="audit-actor-name">
                                {entry.actor?.name ?? "System Automation"}
                                {entry.actor?.role === "SUPER_ADMINISTRATOR" && (
                                  <span className="chip-admin" style={{ fontSize: "10px", padding: "1px 6px" }}>
                                    Super Admin
                                  </span>
                                )}
                              </span>
                              <span className="audit-actor-email">
                                {entry.actor?.email ?? "system@dasigconnect.gov.ph"}
                              </span>
                            </div>
                          </div>
                        </td>

                        {/* Category & Action */}
                        <td>
                          <div style={{ display: "flex", flexDirection: "column", gap: "4px" }}>
                            <span className={`audit-cat-badge ${categoryClass(entry.category)}`}>
                              {entry.categoryLabel}
                            </span>
                            <span style={{ fontSize: "11px", color: "#64748b", textTransform: "capitalize" }}>
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
                                className="audit-entity-badge audit-entity-link"
                                title="Jump to entity"
                              >
                                <i className="ti ti-external-link" />
                                {entry.entity.label}
                              </a>
                            ) : (
                              <span className="audit-entity-badge audit-entity-link">
                                {entry.entity.label}
                              </span>
                            )
                          ) : (
                            <span
                              className="audit-entity-badge audit-entity-unavailable"
                              title="Entity has been deleted or is no longer available"
                            >
                              <i className="ti ti-alert-triangle" />
                              [Entity no longer available]
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
                            onClick={() => setSelectedEntry(entry)}
                            title="Inspect full audit event details and diffs"
                          >
                            <i className="ti ti-eye" />
                            View
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            {/* Pagination Controls */}
            <div className="audit-pagination">
              <div className="audit-pagination-info">
                Showing {page * pageSize + 1}–
                {Math.min((page + 1) * pageSize, totalElements)} of {totalElements.toLocaleString()}{" "}
                events
              </div>
              <div className="audit-pagination-nav">
                <button
                  type="button"
                  className="audit-page-btn"
                  disabled={page <= 0}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                >
                  <i className="ti ti-chevron-left" />
                  Previous
                </button>
                <span style={{ fontSize: "12px", fontWeight: 700, color: "#344054", margin: "0 6px" }}>
                  Page {page + 1} of {Math.max(1, totalPages)}
                </span>
                <button
                  type="button"
                  className="audit-page-btn"
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage((p) => p + 1)}
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
  );
}

import { useEffect, useMemo, useState } from "react";
import { createPortal } from "react-dom";
import {
  downloadAnalyticsCsv,
  getAnalyticsReport,
  type AnalyticsExportMetric,
  type AnalyticsRange,
  type AnalyticsReportDto,
} from "../../../api/analyticsApi";
import { formatDateRange, formatNumber } from "../analyticsUtils";

interface Props {
  metric: AnalyticsExportMetric | null;
  range: AnalyticsRange;
  institutionId?: string | null;
  busy: boolean;
  onBusyChange: (busy: boolean) => void;
  onClose: () => void;
}

const REPORT_LABELS: Record<AnalyticsExportMetric, string> = {
  "posting-delay": "Posting Delay Report",
  "content-completeness": "Content Completeness Report",
  "posts-by-institution": "Posts by Institution Report",
  "ai-performance": "AI Performance Report",
  "operational-health": "Operational Health Report",
  "facebook-engagement": "Facebook Engagement Report",
};

const REPORT_ICONS: Record<AnalyticsExportMetric, string> = {
  "posting-delay": "ti ti-clock-hour-4",
  "content-completeness": "ti ti-checklist",
  "posts-by-institution": "ti ti-speakerphone",
  "ai-performance": "ti ti-robot",
  "operational-health": "ti ti-heartbeat",
  "facebook-engagement": "ti ti-brand-facebook",
};

const REPORT_UNITS: Record<AnalyticsExportMetric, string> = {
  "posting-delay": "days",
  "content-completeness": "percent",
  "posts-by-institution": "posts",
  "ai-performance": "events",
  "operational-health": "percent",
  "facebook-engagement": "reach",
};

// The second tab shows the metric-specific rows (same data as the CSV), so its
// label matches what those rows actually are per report instead of always saying
// "Submission Detail".
const DETAIL_TAB_LABEL: Record<AnalyticsExportMetric, string> = {
  "posting-delay": "Submission Detail",
  "content-completeness": "Submission Detail",
  "posts-by-institution": "By Institution",
  "ai-performance": "By Interaction",
  "operational-health": "Metric Summary",
  "facebook-engagement": "Per-Post Engagement",
};

// Friendlier headers for the columns that come back from the aggregate rows.
const COLUMN_LABELS: Record<string, string> = {
  institution_name: "Institution",
  event_title: "Submission Title",
  status: "State",
  first_submitted_at: "First Submitted",
  published_at: "Published At",
  delay_days: "Delay (days)",
  has_event_title: "Event Title",
  has_event_date: "Event Date",
  has_caption: "Caption",
  has_media: "Media",
  post_count: "Posts",
  interaction_type: "Interaction",
  action_taken: "Action",
  event_count: "Events",
  comments_count: "Comments",
  metric: "Metric",
  value: "Value",
  pending: "Awaiting Sync",
};

const HIDDEN_COLUMNS = new Set(["submission_id", "id"]);

type ActiveTab = "daily" | "detail";

function humanizeKey(key: string): string {
  return COLUMN_LABELS[key] ?? key.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
}

function looksLikeDate(value: string): boolean {
  return /^\d{4}-\d{2}-\d{2}([T ]\d{2}:\d{2})?/.test(value);
}

function formatCell(value: string | number | boolean | null): string {
  if (value === null || value === undefined || value === "") return "—";
  if (typeof value === "boolean") return value ? "Yes" : "No";
  if (typeof value === "number") {
    return Number.isInteger(value) ? formatNumber(value) : formatNumber(Math.round(value * 100) / 100);
  }
  if (looksLikeDate(value)) {
    const parsed = new Date(value);
    if (!Number.isNaN(parsed.getTime())) {
      return parsed.toLocaleString("en-PH", {
        month: "short",
        day: "numeric",
        year: "numeric",
        hour: value.length > 10 ? "numeric" : undefined,
        minute: value.length > 10 ? "2-digit" : undefined,
        hour12: true,
      });
    }
  }
  return value;
}

export default function FullReportModal({
  metric,
  range,
  institutionId,
  busy,
  onBusyChange,
  onClose,
}: Props) {
  const [report, setReport] = useState<AnalyticsReportDto | null>(null);
  const [error, setError] = useState<{ key: string; message: string } | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [tabEntry, setTabEntry] = useState<{ forMetric: string; tab: ActiveTab } | null>(null);
  const dailyHasData = (report?.dailyBreakdown ?? []).some(
    (p) => p.value !== 0 || (p.secondaryValue ?? 0) !== 0,
  );
  const defaultTab: ActiveTab =
    dailyHasData || !(report && report.aggregateRows.length > 0) ? "daily" : "detail";
  const activeTab: ActiveTab = tabEntry?.forMetric === metric ? tabEntry.tab : defaultTab;
  const requestKey = metric
    ? `${metric}:${range}:${institutionId ?? "network"}:${refreshKey}`
    : "";

  useEffect(() => {
    if (!metric) return;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", onKeyDown);
    };
  }, [metric, onClose]);

  function switchTab(tab: ActiveTab) {
    if (metric) setTabEntry({ forMetric: metric, tab });
  }

  useEffect(() => {
    if (!metric) return;
    const controller = new AbortController();
    const activeKey = `${metric}:${range}:${institutionId ?? "network"}:${refreshKey}`;
    getAnalyticsReport(metric, range, institutionId, controller.signal)
      .then((res) => {
        setReport(res.data);
        setError(null);
      })
      .catch((err: { code?: string }) => {
        if (err?.code !== "ERR_CANCELED") {
          setError({ key: activeKey, message: "Could not load the full report." });
        }
      });
    return () => controller.abort();
  }, [metric, range, institutionId, refreshKey]);

  const maxDailyValue = useMemo(
    () => Math.max(...(report?.dailyBreakdown ?? []).map((point) => point.value), 1),
    [report],
  );

  if (!metric) return null;

  const reportReady = report?.metric === metric && report.range === range;
  const activeError = error?.key === requestKey ? error.message : null;
  const loading = !reportReady && !activeError;

  const detailRows = reportReady ? report.aggregateRows : [];
  const detailColumns = detailRows.length
    ? Object.keys(detailRows[0]).filter((key) => !HIDDEN_COLUMNS.has(key))
    : [];

  async function handleDownload() {
    if (!metric) return;
    onBusyChange(true);
    try {
      await downloadAnalyticsCsv(metric, range, institutionId);
    } finally {
      onBusyChange(false);
    }
  }

  function reloadReport() {
    setReport(null);
    setError(null);
    setRefreshKey((v) => v + 1);
  }

  return createPortal(
    <div
      className="analytics-modal-backdrop"
      role="presentation"
      onClick={onClose}
    >
      <div
        className="analytics-modal-wide"
        role="dialog"
        aria-modal="true"
        aria-labelledby="report-modal-title"
        onClick={(e) => e.stopPropagation()}
      >
        {/* ── Header ── */}
        <div className="analytics-modal-header">
          <div className="analytics-modal-title-row">
            <div className="analytics-modal-title-inner">
              <div className="analytics-modal-metric-icon">
                <i className={REPORT_ICONS[metric]} aria-hidden="true" />
              </div>
              <div>
                <h2 id="report-modal-title">{REPORT_LABELS[metric]}</h2>
                <p>
                  {reportReady
                    ? formatDateRange(report.periodStart, report.periodEnd)
                    : `${range.toUpperCase()} detail report`}
                </p>
              </div>
            </div>
            <button
              type="button"
              className="analytics-icon-btn"
              onClick={onClose}
              aria-label="Close report"
            >
              <i className="ti ti-x" aria-hidden="true" />
            </button>
          </div>
        </div>

        {/* ── Tab Bar (shown once loaded) ── */}
        {!loading && !activeError && reportReady && (
          <div className="analytics-modal-tabs" role="tablist" aria-label="Report sections">
            <button
              type="button"
              role="tab"
              aria-selected={activeTab === "daily"}
              className={`analytics-tab-btn${activeTab === "daily" ? " active" : ""}`}
              onClick={() => switchTab("daily")}
            >
              <i className="ti ti-chart-bar" aria-hidden="true" />
              <span>Daily Breakdown</span>
              <span className="analytics-tab-count">{report.dailyBreakdown.length}</span>
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={activeTab === "detail"}
              className={`analytics-tab-btn${activeTab === "detail" ? " active" : ""}`}
              onClick={() => switchTab("detail")}
            >
              <i className="ti ti-table" aria-hidden="true" />
              <span>{DETAIL_TAB_LABEL[metric]}</span>
              <span className="analytics-tab-count">{report.aggregateRows.length}</span>
            </button>
          </div>
        )}

        {/* ── Body with active scrollbar ── */}
        <div className="analytics-report-body">
          {loading && (
            <div className="analytics-report-state">
              <div className="dc-dot-triangle-container" style={{ padding: "40px 0" }}>
                <div className="loader-dots" />
                <div className="dc-dot-triangle-label">
                  Loading Report Details
                  <span className="dc-dot-triangle-label-dots">
                    <span className="dc-dot-triangle-dot-char">.</span>
                    <span className="dc-dot-triangle-dot-char">.</span>
                    <span className="dc-dot-triangle-dot-char">.</span>
                  </span>
                </div>
              </div>
            </div>
          )}

          {!loading && activeError && (
            <div className="analytics-report-state">
              <i className="ti ti-alert-circle" aria-hidden="true" style={{ fontSize: "32px", color: "#ef4444" }} />
              <strong style={{ color: "#0c1d3d" }}>Unable to load report</strong>
              <span style={{ color: "#64748b", fontSize: "13px" }}>{activeError}</span>
              <button type="button" className="notif-btn notif-btn-ghost" onClick={reloadReport} style={{ marginTop: "8px" }}>
                <i className="ti ti-refresh" /> Retry
              </button>
            </div>
          )}

          {!loading && !activeError && reportReady && (
            <>
              {/* Daily Breakdown Tab */}
              {activeTab === "daily" && (
                <section className="analytics-report-section" role="tabpanel" aria-label="Daily Breakdown">
                  <div className="analytics-report-section-header">
                    <div>
                      <h3>Daily Breakdown</h3>
                      <p>Per-day values for {REPORT_LABELS[metric]} over the selected timeframe.</p>
                    </div>
                  </div>
                  {report.dailyBreakdown.length === 0 ? (
                    <div className="analytics-empty">No daily data recorded for this period.</div>
                  ) : (
                    <div className="analytics-daily-list">
                      {report.dailyBreakdown.map((point) => (
                        <div className="analytics-daily-row" key={point.date}>
                          <span className="analytics-daily-date">
                            {new Date(point.date).toLocaleDateString("en-PH", {
                              month: "short",
                              day: "numeric",
                              year: "numeric",
                            })}
                          </span>
                          <div
                            className="analytics-bar-track"
                            aria-hidden="true"
                            title={`${formatReportValue(point.value, REPORT_UNITS[metric])}`}
                          >
                            <span
                              style={{
                                width: `${Math.min(100, Math.max((point.value / maxDailyValue) * 100, 3))}%`,
                              }}
                            />
                          </div>
                          <strong className="analytics-daily-val">
                            {formatReportValue(point.value, REPORT_UNITS[metric])}
                          </strong>
                          {point.secondaryValue !== null && (
                            <em className="analytics-daily-sub">
                              {formatNumber(point.secondaryValue)} total
                            </em>
                          )}
                        </div>
                      ))}
                    </div>
                  )}
                </section>
              )}

              {/* Metric-specific detail tab — same rows as the CSV export */}
              {activeTab === "detail" && (
                <section className="analytics-report-section" role="tabpanel" aria-label={DETAIL_TAB_LABEL[metric]}>
                  <div className="analytics-report-section-header">
                    <div>
                      <h3>{DETAIL_TAB_LABEL[metric]}</h3>
                      <p>Every row behind {REPORT_LABELS[metric]} for this period — matches the CSV export.</p>
                    </div>
                  </div>
                  {detailRows.length === 0 ? (
                    <div className="analytics-empty">No rows recorded for this period.</div>
                  ) : (
                    <div className="analytics-table-wrap">
                      <table className="analytics-table">
                        <thead>
                          <tr>
                            {detailColumns.map((col) => (
                              <th key={col}>{humanizeKey(col)}</th>
                            ))}
                          </tr>
                        </thead>
                        <tbody>
                          {detailRows.map((row, rowIndex) => (
                            <tr key={String(row.submission_id ?? row.id ?? rowIndex)}>
                              {detailColumns.map((col) => {
                                const raw = row[col];
                                const isState = col === "status" && typeof raw === "string";
                                return (
                                  <td key={col}>
                                    {isState ? (
                                      <span className={`status-pill ${getStatusPillClass(raw as string)}`}>
                                        {raw as string}
                                      </span>
                                    ) : (
                                      formatCell(raw)
                                    )}
                                  </td>
                                );
                              })}
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </section>
              )}
            </>
          )}
        </div>

        {/* ── Footer Actions ── */}
        <div className="analytics-modal-actions">
          <button type="button" className="notif-btn notif-btn-ghost notif-btn-sm" onClick={onClose}>
            <span>Close</span>
          </button>
          <button
            type="button"
            className="notif-btn notif-btn-primary notif-btn-sm"
            onClick={() => void handleDownload()}
            disabled={busy || loading}
          >
            <i className="ti ti-download" aria-hidden="true" />
            <span>{busy ? "Preparing Export…" : "Download CSV Report"}</span>
          </button>
        </div>
      </div>
    </div>,
    document.body,
  );
}

function getStatusPillClass(state: string): string {
  const s = (state || "").toLowerCase();
  if (s.includes("published")) return "sp-approved";
  if (s.includes("review") || s.includes("pending")) return "sp-pending";
  if (s.includes("revision")) return "pill-revision";
  if (s.includes("scheduled")) return "sp-scheduled";
  return "sp-scheduled";
}

function formatReportValue(value: number, unit: string): string {
  if (unit === "percent") return `${value.toFixed(1)}%`;
  if (unit === "days") return `${value.toFixed(1)}d`;
  return formatNumber(value);
}

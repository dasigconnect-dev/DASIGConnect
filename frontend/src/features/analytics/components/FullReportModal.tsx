import { useEffect, useMemo, useState } from "react";
import { createPortal } from "react-dom";
import {
  downloadAnalyticsCsv,
  getAnalyticsReport,
  type AnalyticsExportMetric,
  type AnalyticsRange,
  type AnalyticsReportDto,
} from "../../../api/analyticsApi";
import { formatDateRange, formatMetric, formatNumber } from "../analyticsUtils";

interface Props {
  metric: AnalyticsExportMetric | null;
  range: AnalyticsRange;
  institutionId?: string | null;
  category?: string | null;
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

type ActiveTab = "daily" | "submissions";

export default function FullReportModal({
  metric,
  range,
  institutionId,
  category,
  busy,
  onBusyChange,
  onClose,
}: Props) {
  const [report, setReport] = useState<AnalyticsReportDto | null>(null);
  const [error, setError] = useState<{ key: string; message: string } | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [tabEntry, setTabEntry] = useState<{ forMetric: string; tab: ActiveTab } | null>(null);
  const activeTab: ActiveTab = tabEntry?.forMetric === metric ? tabEntry.tab : "daily";
  const requestKey = metric
    ? `${metric}:${range}:${institutionId ?? "network"}:${category ?? "all"}:${refreshKey}`
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
    const activeKey = `${metric}:${range}:${institutionId ?? "network"}:${category ?? "all"}:${refreshKey}`;
    getAnalyticsReport(metric, range, institutionId, category, controller.signal)
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
  }, [metric, range, institutionId, category, refreshKey]);

  const maxDailyValue = useMemo(
    () => Math.max(...(report?.dailyBreakdown ?? []).map((point) => point.value), 1),
    [report],
  );

  if (!metric) return null;

  const reportReady = report?.metric === metric && report.range === range;
  const activeError = error?.key === requestKey ? error.message : null;
  const loading = !reportReady && !activeError;

  const showContributor = reportReady && report.submissions.some((r) => r.contributorName);
  const showInstitution = reportReady && report.submissions.some((r) => r.institutionName);
  const showRevisions = reportReady && report.submissions.some((r) => r.revisionCycles !== null);

  async function handleDownload() {
    if (!metric) return;
    onBusyChange(true);
    try {
      await downloadAnalyticsCsv(metric, range, institutionId, category);
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
              aria-selected={activeTab === "submissions"}
              className={`analytics-tab-btn${activeTab === "submissions" ? " active" : ""}`}
              onClick={() => switchTab("submissions")}
            >
              <i className="ti ti-table" aria-hidden="true" />
              <span>Submission Detail</span>
              <span className="analytics-tab-count">{report.submissions.length}</span>
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

              {/* Submission Detail Tab */}
              {activeTab === "submissions" && (
                <section className="analytics-report-section" role="tabpanel" aria-label="Submission Detail">
                  <div className="analytics-report-section-header">
                    <div>
                      <h3>Submission Detail</h3>
                      <p>Full itemized list of submissions recorded during this reporting period.</p>
                    </div>
                  </div>
                  <div className="analytics-table-wrap">
                    <table className="analytics-table">
                      <thead>
                        <tr>
                          <th>Submission Title</th>
                          <th>Publication State</th>
                          <th>First Submitted</th>
                          <th>Published At</th>
                          <th>Delay</th>
                          <th>Completeness</th>
                          {showContributor && <th>Contributor</th>}
                          {showInstitution && <th>Institution</th>}
                          {showRevisions && <th>Revisions</th>}
                        </tr>
                      </thead>
                      <tbody>
                        {report.submissions.length === 0 ? (
                          <tr>
                            <td colSpan={9} style={{ textAlign: "center", padding: "36px 16px", color: "var(--d-muted)" }}>
                              No submission rows for this period.
                            </td>
                          </tr>
                        ) : (
                          report.submissions.map((row) => (
                            <tr key={row.submissionId}>
                              <td>
                                <strong style={{ color: "var(--d-text, #0c1d3d)" }}>{row.eventTitle || "Untitled"}</strong>
                              </td>
                              <td>
                                <span className={`status-pill ${getStatusPillClass(row.publicationState)}`}>
                                  {row.publicationState}
                                </span>
                              </td>
                              <td>{formatNullableDate(row.firstSubmittedAt)}</td>
                              <td>{formatNullableDate(row.publishedAt)}</td>
                              <td>
                                {formatMetric({
                                  id: "delay",
                                  label: "Delay",
                                  value: row.postingDelayDays,
                                  unit: "days",
                                  sampleSize: 1,
                                  target: null,
                                  targetMet: true,
                                  deltaPercent: null,
                                  sparkline: [],
                                  secondaryLabel: null,
                                  secondaryValue: null,
                                })}
                              </td>
                              <td>
                                <span style={{ fontWeight: 600, color: row.complete ? "#16a34a" : "#ca8a04" }}>
                                  {row.complete ? "100%" : "Partial"}
                                </span>
                              </td>
                              {showContributor && <td>{row.contributorName ?? "—"}</td>}
                              {showInstitution && <td>{row.institutionName ?? "—"}</td>}
                              {showRevisions && <td>{row.revisionCycles !== null ? `${row.revisionCycles} cycles` : "—"}</td>}
                            </tr>
                          ))
                        )}
                      </tbody>
                    </table>
                  </div>
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

function formatNullableDate(value: string | null): string {
  if (!value) return "—";
  return new Date(value).toLocaleString("en-PH", {
    month: "short",
    day: "numeric",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
    hour12: true,
  });
}

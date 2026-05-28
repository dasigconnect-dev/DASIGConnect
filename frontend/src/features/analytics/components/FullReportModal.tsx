import { useEffect, useMemo, useState } from "react";
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
};

const REPORT_ICONS: Record<AnalyticsExportMetric, string> = {
  "posting-delay": "ti ti-clock",
  "content-completeness": "ti ti-checklist",
  "posts-by-institution": "ti ti-speakerphone",
  "ai-performance": "ti ti-robot",
  "operational-health": "ti ti-activity",
};

const REPORT_UNITS: Record<AnalyticsExportMetric, string> = {
  "posting-delay": "days",
  "content-completeness": "percent",
  "posts-by-institution": "posts",
  "ai-performance": "events",
  "operational-health": "percent",
};

type ActiveTab = "daily" | "submissions";

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
  // Tab is bound to the metric it was chosen for — auto-resets to "daily" when metric changes
  const [tabEntry, setTabEntry] = useState<{ forMetric: string; tab: ActiveTab } | null>(null);
  const activeTab: ActiveTab = tabEntry?.forMetric === metric ? tabEntry.tab : "daily";
  const requestKey = metric ? `${metric}:${range}:${institutionId ?? "network"}:${refreshKey}` : "";

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

  const showContributor = reportReady && report.submissions.some((r) => r.contributorName);
  const showInstitution = reportReady && report.submissions.some((r) => r.institutionName);
  const showRevisions = reportReady && report.submissions.some((r) => r.revisionCycles !== null);

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

  return (
    <div
      className="analytics-modal-backdrop"
      role="presentation"
      onMouseDown={onClose}
    >
      <div
        className="analytics-modal-wide"
        role="dialog"
        aria-modal="true"
        aria-labelledby="report-modal-title"
        onMouseDown={(e) => e.stopPropagation()}
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
              Daily Breakdown
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
              Submission Detail
              <span className="analytics-tab-count">{report.submissions.length}</span>
            </button>
          </div>
        )}

        {/* ── Body ── */}
        <div className="analytics-report-body">
          {loading && (
            <div className="analytics-report-state">
              <div className="analytics-skeleton" />
              <span>Loading report details…</span>
            </div>
          )}

          {!loading && activeError && (
            <div className="analytics-report-state">
              <i className="ti ti-alert-circle" aria-hidden="true" />
              <span>{activeError}</span>
              <button type="button" className="btn-secondary" onClick={reloadReport}>
                Retry
              </button>
            </div>
          )}

          {!loading && !activeError && reportReady && (
            <>
              {/* Daily Breakdown Tab */}
              {activeTab === "daily" && (
                <section className="analytics-report-section" role="tabpanel" aria-label="Daily Breakdown">
                  <h3>Daily Breakdown</h3>
                  <p>Per-day values for the selected metric and role scope.</p>
                  {report.dailyBreakdown.length === 0 ? (
                    <div className="analytics-empty">No daily data for this period.</div>
                  ) : (
                    <div className="analytics-daily-list">
                      {report.dailyBreakdown.map((point) => (
                        <div className="analytics-daily-row" key={point.date}>
                          <span>
                            {new Date(point.date).toLocaleDateString(undefined, {
                              month: "short",
                              day: "numeric",
                            })}
                          </span>
                          <div
                            className="analytics-bar-track"
                            aria-hidden="true"
                            title={`${formatReportValue(point.value, REPORT_UNITS[metric])}`}
                          >
                            <span
                              style={{
                                width: `${(point.value / maxDailyValue) * 100}%`,
                              }}
                            />
                          </div>
                          <strong>{formatReportValue(point.value, REPORT_UNITS[metric])}</strong>
                          {point.secondaryValue !== null && (
                            <em>{formatNumber(point.secondaryValue)}</em>
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
                  <h3>Submission Detail</h3>
                  <p>Rows are scoped by role. Restricted columns are omitted automatically.</p>
                  <div className="analytics-table-wrap">
                    <table className="analytics-table">
                      <thead>
                        <tr>
                          <th>Submission</th>
                          <th>State</th>
                          <th>First Submitted</th>
                          <th>Published</th>
                          <th>Delay</th>
                          <th>Complete</th>
                          {showContributor && <th>Contributor</th>}
                          {showInstitution && <th>Institution</th>}
                          {showRevisions && <th>Revisions</th>}
                        </tr>
                      </thead>
                      <tbody>
                        {report.submissions.length === 0 ? (
                          <tr>
                            <td colSpan={9}>No submission rows for this period.</td>
                          </tr>
                        ) : (
                          report.submissions.map((row) => (
                            <tr key={row.submissionId}>
                              <td>{row.eventTitle}</td>
                              <td>{row.publicationState}</td>
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
                              <td>{row.complete ? "Yes" : "No"}</td>
                              {showContributor && <td>{row.contributorName ?? ""}</td>}
                              {showInstitution && <td>{row.institutionName ?? ""}</td>}
                              {showRevisions && <td>{row.revisionCycles ?? ""}</td>}
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
          <button type="button" className="btn-secondary" onClick={onClose}>
            Close
          </button>
          <button
            type="button"
            className="btn-primary"
            onClick={() => void handleDownload()}
            disabled={busy}
          >
            <i className="ti ti-download" aria-hidden="true" />
            {busy ? "Preparing…" : "Download CSV"}
          </button>
        </div>
      </div>
    </div>
  );
}

function formatReportValue(value: number, unit: string): string {
  if (unit === "percent") return `${value.toFixed(1)}%`;
  if (unit === "days") return `${value.toFixed(1)}d`;
  return formatNumber(value);
}

function formatNullableDate(value: string | null): string {
  if (!value) return "-";
  return new Date(value).toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

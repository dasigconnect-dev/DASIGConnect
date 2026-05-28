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

const REPORT_UNITS: Record<AnalyticsExportMetric, string> = {
  "posting-delay": "days",
  "content-completeness": "percent",
  "posts-by-institution": "posts",
  "ai-performance": "events",
  "operational-health": "percent",
};

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
  const requestKey = metric ? `${metric}:${range}:${institutionId ?? "network"}:${refreshKey}` : "";

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
    setRefreshKey((value) => value + 1);
  }

  return (
    <div className="analytics-modal-backdrop" role="presentation" onMouseDown={onClose}>
      <div className="analytics-modal analytics-modal-wide" role="dialog" aria-modal="true" onMouseDown={(e) => e.stopPropagation()}>
        <div className="analytics-modal-header">
          <div>
            <h2>{REPORT_LABELS[metric]}</h2>
            <p>
              {report ? formatDateRange(report.periodStart, report.periodEnd) : `${range.toUpperCase()} detail report`}
            </p>
          </div>
          <button type="button" className="analytics-icon-btn" onClick={onClose} aria-label="Close report modal">
            <i className="ti ti-x" aria-hidden="true" />
          </button>
        </div>

        {loading && (
          <div className="analytics-report-state">
            <div className="analytics-skeleton" />
            <span>Loading report details...</span>
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
          <div className="analytics-report-body">
            <section className="analytics-report-section">
              <div className="analytics-panel-header">
                <div>
                  <h3>Daily Breakdown</h3>
                  <p>Per-day values for the selected metric and role scope.</p>
                </div>
              </div>
              {report.dailyBreakdown.length === 0 ? (
                <div className="analytics-empty">No daily data for this period.</div>
              ) : (
                <div className="analytics-daily-list">
                  {report.dailyBreakdown.map((point) => (
                    <div className="analytics-daily-row" key={point.date}>
                      <span>{new Date(point.date).toLocaleDateString(undefined, { month: "short", day: "numeric" })}</span>
                      <div className="analytics-bar-track">
                        <span style={{ width: `${(point.value / maxDailyValue) * 100}%` }} />
                      </div>
                      <strong>{formatReportValue(point.value, REPORT_UNITS[metric])}</strong>
                      {point.secondaryValue !== null && <em>{formatNumber(point.secondaryValue)}</em>}
                    </div>
                  ))}
                </div>
              )}
            </section>

            <section className="analytics-report-section">
              <div className="analytics-panel-header">
                <div>
                  <h3>Submission Detail</h3>
                  <p>Rows are scoped by role. Restricted columns are omitted automatically.</p>
                </div>
              </div>
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
                      {report.submissions.some((row) => row.contributorName) && <th>Contributor</th>}
                      {report.submissions.some((row) => row.institutionName) && <th>Institution</th>}
                      {report.submissions.some((row) => row.revisionCycles !== null) && <th>Revisions</th>}
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
                          <td>{formatMetric({
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
                          })}</td>
                          <td>{row.complete ? "Yes" : "No"}</td>
                          {report.submissions.some((item) => item.contributorName) && <td>{row.contributorName ?? ""}</td>}
                          {report.submissions.some((item) => item.institutionName) && <td>{row.institutionName ?? ""}</td>}
                          {report.submissions.some((item) => item.revisionCycles !== null) && <td>{row.revisionCycles ?? ""}</td>}
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </section>
          </div>
        )}

        <div className="analytics-modal-actions">
          <button type="button" className="btn-secondary" onClick={onClose}>
            Close
          </button>
          <button type="button" className="btn-primary" onClick={() => void handleDownload()} disabled={busy}>
            <i className="ti ti-download" aria-hidden="true" />
            {busy ? "Preparing..." : "Download CSV"}
          </button>
        </div>
      </div>
    </div>
  );
}

function formatReportValue(value: number, unit: string) {
  if (unit === "percent") return `${value.toFixed(1)}%`;
  if (unit === "days") return `${value.toFixed(1)}d`;
  return formatNumber(value);
}

function formatNullableDate(value: string | null) {
  if (!value) return "-";
  return new Date(value).toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

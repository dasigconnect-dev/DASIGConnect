import {
  downloadAnalyticsCsv,
  type AnalyticsExportMetric,
  type AnalyticsRange,
} from "../../../api/analyticsApi";

interface Props {
  metric: AnalyticsExportMetric | null;
  range: AnalyticsRange;
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

export default function FullReportModal({
  metric,
  range,
  busy,
  onBusyChange,
  onClose,
}: Props) {
  if (!metric) return null;

  async function handleDownload() {
    if (!metric) return;
    onBusyChange(true);
    try {
      await downloadAnalyticsCsv(metric, range);
      onClose();
    } finally {
      onBusyChange(false);
    }
  }

  return (
    <div className="analytics-modal-backdrop" role="presentation" onMouseDown={onClose}>
      <div className="analytics-modal" role="dialog" aria-modal="true" onMouseDown={(e) => e.stopPropagation()}>
        <div className="analytics-modal-header">
          <div>
            <h2>{REPORT_LABELS[metric]}</h2>
            <p>CSV export for the selected {range.toUpperCase()} period.</p>
          </div>
          <button type="button" className="analytics-icon-btn" onClick={onClose} aria-label="Close report modal">
            <i className="ti ti-x" aria-hidden="true" />
          </button>
        </div>
        <div className="analytics-report-preview">
          <i className="ti ti-file-spreadsheet" aria-hidden="true" />
          <div>
            <strong>Download detailed CSV</strong>
            <span>The file includes the rows backing this dashboard metric.</span>
          </div>
        </div>
        <div className="analytics-modal-actions">
          <button type="button" className="btn-secondary" onClick={onClose}>
            Cancel
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

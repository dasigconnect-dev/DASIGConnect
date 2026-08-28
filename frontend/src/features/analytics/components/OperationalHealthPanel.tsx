import type { OperationalHealthDto } from "../../../api/analyticsApi";
import { formatNumber, formatPercent } from "../analyticsUtils";

interface Props {
  data: OperationalHealthDto;
  onOpenReport: () => void;
}

export default function OperationalHealthPanel({ data, onOpenReport }: Props) {
  const cells: Array<{ label: string; value: string; sub: string }> = [
    {
      label: "Publishing Success",
      value: formatPercent(data.publishingSuccessRate),
      sub: `${formatNumber(data.successfulPublicationAttempts)} of ${formatNumber(data.publicationAttempts)} attempts`,
    },
    {
      label: "On-Time Publication",
      value: formatPercent(data.onTimePublicationRate),
      sub: `${formatNumber(data.onTimePublications)} within ±5 mins`,
    },
    {
      label: "Deadline Risk",
      value: formatPercent(data.validationTimeoutRiskRate),
      sub: `${formatNumber(data.validationDeadlineRisks)} active risks`,
    },
    {
      label: "Override Rate",
      value: formatPercent(data.overrideRate),
      sub: `${formatNumber(data.overrideAuditEvents)} override events`,
    },
  ];

  return (
    <>
      <div className="analytics-stat-grid">
        {cells.map((cell) => (
          <div className="analytics-stat-cell" key={cell.label}>
            <span className="analytics-stat-cell-label">{cell.label}</span>
            <span className="analytics-stat-cell-value">{cell.value}</span>
            <span className="analytics-stat-cell-sub">{cell.sub}</span>
          </div>
        ))}
      </div>

      <div className="analytics-panel-report-row">
        <button type="button" className="analytics-text-btn" onClick={onOpenReport}>
          View Health Report
          <i className="ti ti-arrow-right" aria-hidden="true" />
        </button>
      </div>
    </>
  );
}

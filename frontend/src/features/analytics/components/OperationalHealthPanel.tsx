import type { OperationalHealthDto } from "../../../api/analyticsApi";
import { formatNumber, formatPercent } from "../analyticsUtils";

interface Props {
  data: OperationalHealthDto;
  onOpenReport: () => void;
}

export default function OperationalHealthPanel({ data, onOpenReport }: Props) {
  const metrics = [
    {
      label: "Publishing success",
      value: formatPercent(data.publishingSuccessRate),
      meta: `${formatNumber(data.successfulPublicationAttempts)} of ${formatNumber(data.publicationAttempts)} attempts`,
    },
    {
      label: "On-time publication",
      value: formatPercent(data.onTimePublicationRate),
      meta: `${formatNumber(data.onTimePublications)} within ±5 minutes`,
    },
    {
      label: "Deadline risk",
      value: formatPercent(data.validationTimeoutRiskRate),
      meta: `${formatNumber(data.validationDeadlineRisks)} active risks`,
    },
    {
      label: "Override rate",
      value: formatPercent(data.overrideRate),
      meta: `${formatNumber(data.overrideAuditEvents)} override audit events`,
    },
  ];

  return (
    <section className="analytics-panel">
      <div className="analytics-panel-header">
        <div>
          <h2>Operational Health</h2>
          <p>Workflow, publishing, deadline, and administrator workload signals</p>
        </div>
      </div>

      <div className="analytics-health-grid">
        {metrics.map((metric) => (
          <div className="analytics-health-card" key={metric.label}>
            <span>{metric.label}</span>
            <strong>{metric.value}</strong>
            <em>{metric.meta}</em>
          </div>
        ))}
      </div>

      <div className="analytics-admin-actions">
        <span>Administrator actions</span>
        <strong>{formatNumber(data.administratorActions)}</strong>
      </div>

      <button type="button" className="analytics-link-btn" onClick={onOpenReport}>
        <i className="ti ti-activity" aria-hidden="true" />
        Health report
      </button>
    </section>
  );
}

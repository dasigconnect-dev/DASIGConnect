import type { EngagementRecommendations } from "../../../api/submissionApi";
import { formatDateTime } from "../utils";

export function EngagementRecommendationsPanel({
  loading,
  recommendations,
  selectedAt,
  onSelect,
}: {
  loading: boolean;
  recommendations: EngagementRecommendations | null;
  selectedAt?: string;
  onSelect: (scheduledAt: string) => void;
}) {
  if (loading) {
    return (
      <div className="sub-engagement-panel sub-engagement-loading" aria-live="polite">
        <i className="ti ti-loader-2"></i> Finding the best engagement times…
      </div>
    );
  }
  if (!recommendations || recommendations.slots.length === 0) return null;
  return (
    <div className="sub-engagement-panel">
      <div className="sub-engagement-heading">
        <span><i className="ti ti-chart-line"></i> Recommended times</span>
        <small>{recommendations.source === "HISTORICAL" ? `${recommendations.sampleSize} Facebook posts analyzed` : "Best-practice guidance"}</small>
      </div>
      {recommendations.notice && <p className="sub-engagement-notice">{recommendations.notice}</p>}
      <div className="sub-engagement-slots">
        {recommendations.slots.map((slot) => (
          <button
            type="button"
            className={selectedAt && new Date(selectedAt).getTime() === new Date(slot.scheduledAt).getTime() ? "selected" : ""}
            key={slot.scheduledAt}
            onClick={() => onSelect(slot.scheduledAt)}
          >
            <strong>{formatDateTime(slot.scheduledAt)}</strong>
            <span>{slot.windowLabel}</span>
            {slot.warnings.length > 0 && <em>{slot.warnings[0]}</em>}
          </button>
        ))}
      </div>
      <small className="sub-engagement-manual">You can ignore these suggestions and choose any valid custom time.</small>
    </div>
  );
}

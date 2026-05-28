import type { FailedPublication } from "../../api/resolutionApi";
import ResolutionActionButtons from "./ResolutionActionButtons";
import ResolutionStatusBadge from "./ResolutionStatusBadge";

function formatDatetime(iso: string | null) {
  if (!iso) return "Not scheduled";
  return new Date(iso).toLocaleString("en-PH", {
    month: "short",
    day: "numeric",
    year: "numeric",
    hour: "numeric",
    minute: "2-digit",
    hour12: true,
  });
}

function timeAgo(iso: string | null) {
  if (!iso) return null;
  const diff = Date.now() - new Date(iso).getTime();
  const minutes = Math.floor(diff / 60000);
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  return `${Math.floor(hours / 24)}d ago`;
}

interface ResolutionFailureCardProps {
  item: FailedPublication;
  busy: boolean;
  onRetry: () => void;
  onStartManual: () => void;
  onCancelManual: () => void;
  onComplete: () => void;
}

export default function ResolutionFailureCard({
  item,
  busy,
  onRetry,
  onStartManual,
  onCancelManual,
  onComplete,
}: ResolutionFailureCardProps) {
  return (
    <article className="res-card">
      <div className="res-card-header">
        <div className="res-card-meta">
          <span className="res-institution">{item.institutionName}</span>
          <span className="res-dot">/</span>
          <span className="res-scheduled">
            Scheduled {formatDatetime(item.scheduledAt)}
          </span>
        </div>
        <ResolutionStatusBadge
          manualPublishInProgress={item.manualPublishInProgress}
        />
      </div>

      <h3 className="res-title">{item.eventTitle}</h3>

      <div className="res-stats">
        <div className="res-stat">
          <span className="res-stat-label">Attempts</span>
          <span className="res-stat-value">{item.retryCount}</span>
        </div>
        {item.lastAttemptAt && (
          <div className="res-stat">
            <span className="res-stat-label">Last attempt</span>
            <span className="res-stat-value">{timeAgo(item.lastAttemptAt)}</span>
          </div>
        )}
        {item.manualPublishInProgress && (
          <div className="res-stat">
            <span className="res-stat-label">Workflow</span>
            <span className="res-stat-value res-manual-badge">
              <i className="ti ti-user-check" aria-hidden="true" />
              Manual session open
            </span>
          </div>
        )}
      </div>

      {item.lastError && (
        <div className="res-error-detail">
          <i className="ti ti-bug" aria-hidden="true" />
          <span>{item.lastError}</span>
        </div>
      )}

      <ResolutionActionButtons
        busy={busy}
        manualPublishInProgress={item.manualPublishInProgress}
        onRetry={onRetry}
        onStartManual={onStartManual}
        onCancelManual={onCancelManual}
        onComplete={onComplete}
      />
    </article>
  );
}

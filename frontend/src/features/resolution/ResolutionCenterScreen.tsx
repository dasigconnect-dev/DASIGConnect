import { useState } from "react";
import type { FailedPublication } from "../../api/resolutionApi";
import type { User } from "../../types/auth.types";
import { useResolutionFailures } from "../../hooks/useResolutionFailures";
import ResolutionFailureCard from "./ResolutionFailureCard";
import ResolutionRetryModal from "./ResolutionRetryModal";
import ManualPublishWorkflowPanel from "./ManualPublishWorkflowPanel";
import {
  ResolutionEmptyState,
  ResolutionErrorState,
  ResolutionLoadingState,
} from "./ResolutionStates";

interface ResolutionCenterScreenProps {
  user: User;
}

export default function ResolutionCenterScreen({
  user,
}: ResolutionCenterScreenProps) {
  const {
    failures,
    loading,
    error,
    busy,
    activeDetail,
    detailLoading,
    refresh,
    handleRetry,
    handleStartManual,
    handleCancelManual,
    handleCompleteManual,
    openWorkflowPanel,
    closeWorkflowPanel,
  } = useResolutionFailures();

  const [retryItem, setRetryItem] = useState<FailedPublication | null>(null);

  return (
    <div className="screen-root" data-role={user.role}>
      <div className="screen-header">
        <div>
          <h1 className="screen-title">Resolution Center</h1>
          <p className="screen-subtitle">
            Failed publications requiring administrator action
          </p>
        </div>
        <button
          type="button"
          className="btn-secondary"
          onClick={refresh}
          disabled={loading}
        >
          <i className="ti ti-refresh" aria-hidden="true" />
          Refresh
        </button>
      </div>

      {loading && <ResolutionLoadingState />}

      {!loading && error && (
        <ResolutionErrorState message={error} onRetry={refresh} />
      )}

      {!loading && !error && failures.length === 0 && (
        <ResolutionEmptyState />
      )}

      {!loading && !error && failures.length > 0 && (
        <div className="res-list">
          {failures.map((item) => (
            <ResolutionFailureCard
              key={item.submissionId}
              item={item}
              busy={busy === item.submissionId}
              onRetry={() => setRetryItem(item)}
              onStartManual={() => void handleStartManual(item)}
              onCancelManual={() => void handleCancelManual(item)}
              onComplete={() => openWorkflowPanel(item)}
            />
          ))}
        </div>
      )}

      <ResolutionRetryModal
        item={retryItem}
        busy={retryItem ? busy === retryItem.submissionId : false}
        onConfirm={() => {
          if (retryItem) {
            void handleRetry(retryItem).then(() => setRetryItem(null));
          }
        }}
        onClose={() => setRetryItem(null)}
      />

      <ManualPublishWorkflowPanel
        detail={activeDetail}
        loading={detailLoading}
        busy={activeDetail ? busy === activeDetail.submissionId : detailLoading}
        onConfirm={(postUrl, notes) => {
          if (activeDetail) {
            const item = failures.find(
              (f) => f.submissionId === activeDetail.submissionId,
            );
            if (item) {
              void handleCompleteManual(item, postUrl, notes);
            }
          }
        }}
        onCancel={() => {
          if (activeDetail) {
            const item = failures.find(
              (f) => f.submissionId === activeDetail.submissionId,
            );
            if (item) {
              void handleCancelManual(item);
            }
          }
        }}
        onClose={closeWorkflowPanel}
      />
    </div>
  );
}

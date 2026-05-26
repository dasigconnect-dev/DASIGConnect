import { useState } from "react";
import type { FailedPublication } from "../../api/resolutionApi";
import type { User } from "../../types/auth.types";
import { useResolutionFailures } from "../../hooks/useResolutionFailures";
import ResolutionFailureCard from "./ResolutionFailureCard";
import ResolutionRetryModal from "./ResolutionRetryModal";
import ResolutionCompleteModal from "./ResolutionCompleteModal";
import {
  ResolutionEmptyState,
  ResolutionErrorState,
  ResolutionLoadingState,
} from "./ResolutionStates";

interface ResolutionCenterScreenProps {
  user: User;
}

type ActionModal =
  | { type: "retry"; item: FailedPublication }
  | { type: "complete"; item: FailedPublication }
  | null;

export default function ResolutionCenterScreen({
  user,
}: ResolutionCenterScreenProps) {
  const {
    failures,
    loading,
    error,
    busy,
    refresh,
    handleRetry,
    handleStartManual,
    handleCancelManual,
    handleCompleteManual,
  } = useResolutionFailures();
  const [modal, setModal] = useState<ActionModal>(null);

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
              onRetry={() => setModal({ type: "retry", item })}
              onStartManual={() => void handleStartManual(item)}
              onCancelManual={() => void handleCancelManual(item)}
              onComplete={() => setModal({ type: "complete", item })}
            />
          ))}
        </div>
      )}

      <ResolutionRetryModal
        item={modal?.type === "retry" ? modal.item : null}
        busy={modal?.type === "retry" ? busy === modal.item.submissionId : false}
        onConfirm={() => {
          if (modal?.type === "retry") {
            void handleRetry(modal.item).then(() => setModal(null));
          }
        }}
        onClose={() => setModal(null)}
      />

      <ResolutionCompleteModal
        item={modal?.type === "complete" ? modal.item : null}
        busy={
          modal?.type === "complete" ? busy === modal.item.submissionId : false
        }
        onConfirm={(postUrl, notes) => {
          if (modal?.type === "complete") {
            void handleCompleteManual(modal.item, postUrl, notes).then(() =>
              setModal(null),
            );
          }
        }}
        onClose={() => setModal(null)}
      />
    </div>
  );
}

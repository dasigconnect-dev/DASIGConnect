import { useRef, useState } from "react";
import type { User } from "../../types/auth.types";
import { useResolutionCounts } from "../../hooks/useResolutionCounts";
import { useResolutionFailures } from "../../hooks/useResolutionFailures";
import type { FailedPublication } from "../../api/resolutionApi";

import ResolutionFailureCard from "./ResolutionFailureCard";
import ResolutionRetryModal from "./ResolutionRetryModal";
import ManualPublishWorkflowPanel from "./ManualPublishWorkflowPanel";
import {
  ResolutionEmptyState,
  ResolutionErrorState,
  ResolutionLoadingState,
} from "./ResolutionStates";
import ValidationTimeoutTab from "./ValidationTimeoutTab";
import OverrideRequestsTab from "./OverrideRequestsTab";
import DirectPostTab from "./DirectPostTab";
import SystemAuditTab from "./SystemAuditTab";

import "../../styles/resolution.css";

type TabId = "failures" | "timeouts" | "overrides" | "direct-post" | "system";

interface TabDef {
  id: TabId;
  label: string;
  icon: string;
  count?: number;
}

interface ResolutionCenterScreenProps {
  user: User;
}

export default function ResolutionCenterScreen({ user }: ResolutionCenterScreenProps) {
  const [activeTab, setActiveTab] = useState<TabId>("failures");
  const [refreshSignal, setRefreshSignal] = useState(0);
  const [tokenIssueCount, setTokenIssueCount] = useState(0);
  const tokenSectionRef = useRef<HTMLElement>(null);

  const counts = useResolutionCounts(refreshSignal);

  // API Failures tab state (UC-3.4)
  const {
    failures,
    loading: failLoading,
    error: failError,
    busy,
    activeDetail,
    detailLoading,
    refresh: refreshFailures,
    handleRetry,
    handleStartManual,
    handleCancelManual,
    handleCompleteManual,
    openWorkflowPanel,
    closeWorkflowPanel,
  } = useResolutionFailures();

  const [retryItem, setRetryItem] = useState<FailedPublication | null>(null);

  function refresh() {
    setRefreshSignal((n) => n + 1);
    refreshFailures();
  }

  function navigateToSystemAudit() {
    setActiveTab("system");
    setTimeout(() => tokenSectionRef.current?.scrollIntoView({ behavior: "smooth" }), 100);
  }

  const tabs: TabDef[] = [
    { id: "failures",     label: "API Failures",        icon: "ti-alert-circle",    count: counts.failures },
    { id: "timeouts",     label: "Validation Timeouts", icon: "ti-clock-exclamation", count: counts.timeouts },
    { id: "overrides",    label: "Override Requests",   icon: "ti-shield-exclamation", count: counts.overrides },
    { id: "direct-post",  label: "Direct Post",         icon: "ti-bolt" },
    { id: "system",       label: "System & Audit",      icon: "ti-key",             count: tokenIssueCount },
  ];

  return (
    <div className="screen-root rc-root" data-role={user.role}>
      {/* ── Header ── */}
      <div className="screen-header">
        <div>
          <h1 className="screen-title">Resolution Center</h1>
          <p className="screen-subtitle">
            Administrator exception-handling hub — UC-3.5
          </p>
        </div>
        <button type="button" className="btn-secondary" onClick={refresh} disabled={failLoading}>
          <i className="ti ti-refresh" aria-hidden="true" />
          Refresh
        </button>
      </div>

      {/* ── Tab bar ── */}
      <div className="rc-tab-bar" role="tablist" aria-label="Resolution Center tabs">
        {tabs.map((tab) => (
          <button
            key={tab.id}
            type="button"
            role="tab"
            aria-selected={activeTab === tab.id}
            className={`rc-tab${activeTab === tab.id ? " rc-tab-active" : ""}`}
            onClick={() => setActiveTab(tab.id)}
          >
            <i className={`ti ${tab.icon}`} aria-hidden="true" />
            <span>{tab.label}</span>
            {tab.count !== undefined && tab.count > 0 && (
              <span className="rc-tab-badge" aria-label={`${tab.count} pending`}>
                {tab.count > 99 ? "99+" : tab.count}
              </span>
            )}
          </button>
        ))}
      </div>

      {/* ── Tab panels ── */}
      <div className="rc-tab-panel" role="tabpanel">

        {/* Tab 1 — API Failures (UC-3.4 extended) */}
        {activeTab === "failures" && (
          <>
            {failLoading && <ResolutionLoadingState />}
            {!failLoading && failError && (
              <ResolutionErrorState message={failError} onRetry={refreshFailures} />
            )}
            {!failLoading && !failError && failures.length === 0 && <ResolutionEmptyState />}
            {!failLoading && !failError && failures.length > 0 && (
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
            {/* Investigate Token shortcut */}
            {!failLoading && !failError && failures.length > 0 && (
              <div className="rc-failures-footer">
                <span className="rc-muted">Recurring failures may indicate a token issue.</span>
                <button
                  type="button"
                  className="rc-link-btn"
                  onClick={navigateToSystemAudit}
                >
                  <i className="ti ti-key" aria-hidden="true" />
                  Investigate Token →
                </button>
              </div>
            )}
          </>
        )}

        {/* Tab 2 — Validation Timeouts */}
        {activeTab === "timeouts" && (
          <ValidationTimeoutTab
            refreshSignal={refreshSignal}
          />
        )}

        {/* Tab 3 — Override Requests */}
        {activeTab === "overrides" && (
          <OverrideRequestsTab
            refreshSignal={refreshSignal}
          />
        )}

        {/* Tab 4 — Direct Post */}
        {activeTab === "direct-post" && <DirectPostTab />}

        {/* Tab 5 — System & Audit */}
        {activeTab === "system" && (
          <SystemAuditTab
            refreshSignal={refreshSignal}
            onIssueCount={setTokenIssueCount}
            tokenSectionRef={tokenSectionRef}
          />
        )}
      </div>

      {/* ── Shared modals (Tab 1) ── */}
      <ResolutionRetryModal
        item={retryItem}
        busy={retryItem ? busy === retryItem.submissionId : false}
        onConfirm={() => {
          if (retryItem) void handleRetry(retryItem).then(() => setRetryItem(null));
        }}
        onClose={() => setRetryItem(null)}
      />

      <ManualPublishWorkflowPanel
        detail={activeDetail}
        loading={detailLoading}
        busy={activeDetail ? busy === activeDetail.submissionId : detailLoading}
        onConfirm={(postUrl, notes) => {
          if (activeDetail) {
            const item = failures.find((f) => f.submissionId === activeDetail.submissionId);
            if (item) void handleCompleteManual(item, postUrl, notes);
          }
        }}
        onCancel={() => {
          if (activeDetail) {
            const item = failures.find((f) => f.submissionId === activeDetail.submissionId);
            if (item) void handleCancelManual(item);
          }
        }}
        onClose={closeWorkflowPanel}
      />
    </div>
  );
}

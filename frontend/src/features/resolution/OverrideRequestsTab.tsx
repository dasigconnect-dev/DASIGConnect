import { useCallback, useEffect, useState } from "react";
import {
  approveOverride,
  denyOverride,
  getOverrideRequests,
  suggestOverride,
  type OverrideRequest,
} from "../../api/resolutionApi";
import { useToast } from "../../context/ToastContext";
import RejectOnBehalfModal from "./RejectOnBehalfModal";
import SlotSuggestionModal from "./SlotSuggestionModal";

function formatSlot(iso: string) {
  return new Date(iso).toLocaleString("en-PH", {
    month: "short", day: "numeric", year: "numeric",
    hour: "numeric", minute: "2-digit", hour12: true,
  });
}

function contributorName(item: OverrideRequest) {
  const parts = [item.contributorFirstName, item.contributorLastName].filter(Boolean);
  return parts.length > 0 ? parts.join(" ") : item.contributorEmail;
}

function isExpired(requestedSlot: string) {
  return new Date(requestedSlot).getTime() < Date.now();
}

interface Props {
  refreshSignal: number;
  onCountChange?: (n: number) => void;
}

export default function OverrideRequestsTab({ refreshSignal, onCountChange }: Props) {
  const toast = useToast();
  const [items, setItems] = useState<OverrideRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const [denyTarget, setDenyTarget] = useState<OverrideRequest | null>(null);
  const [suggestTarget, setSuggestTarget] = useState<OverrideRequest | null>(null);

  const load = useCallback((signal?: AbortSignal) => {
    setLoading(true);
    setError("");
    getOverrideRequests(signal)
      .then((res) => {
        setItems(res.data);
        const active = res.data.filter((r) => !isExpired(r.requestedSlot)).length;
        onCountChange?.(active);
      })
      .catch((err: unknown) => {
        if ((err as { name?: string }).name === "CanceledError") return;
        setError("Could not load override requests.");
      })
      .finally(() => setLoading(false));
  }, [onCountChange]);

  useEffect(() => {
    const controller = new AbortController();
    queueMicrotask(() => load(controller.signal));
    return () => controller.abort();
  }, [load, refreshSignal]);

  async function handleApprove(item: OverrideRequest) {
    setBusy(item.id);
    try {
      await approveOverride(item.id);
      toast.success(`Override approved — slot reserved for "${item.eventTitle}".`);
      load();
    } catch {
      toast.error("Approval failed. Please try again.");
    } finally {
      setBusy(null);
    }
  }

  async function handleSuggestConfirm(requestId: string, suggestedSlot: string, message: string) {
    setBusy(requestId);
    try {
      await suggestOverride(requestId, { suggestedSlot, message: message || undefined });
      toast.success("Alternative slot suggested — Contributor notified.");
      setSuggestTarget(null);
      load();
    } catch {
      toast.error("Suggestion failed. The slot may violate guard rail rules.");
    } finally {
      setBusy(null);
    }
  }

  async function handleDenyConfirm(_reasonCode: string, reason: string) {
    if (!denyTarget) return;
    setBusy(denyTarget.id);
    try {
      await denyOverride(denyTarget.id, { reason: reason || undefined });
      toast.info(`Override denied for "${denyTarget.eventTitle}".`);
      setDenyTarget(null);
      load();
    } catch {
      toast.error("Denial failed. Please try again.");
    } finally {
      setBusy(null);
    }
  }

  if (loading) {
    return (
      <div className="rc-tab-state">
        <div className="spinner-ring" />
        <span>Loading override requests...</span>
      </div>
    );
  }

  if (error) {
    return (
      <div className="rc-tab-state rc-tab-state-error">
        <i className="ti ti-alert-circle" aria-hidden="true" />
        <span>{error}</span>
        <button type="button" className="btn-secondary" onClick={() => load()}>Retry</button>
      </div>
    );
  }

  const active = items.filter((r) => !isExpired(r.requestedSlot));
  const expired = items.filter((r) => isExpired(r.requestedSlot));

  if (active.length === 0 && expired.length === 0) {
    return (
      <div className="rc-tab-state rc-tab-state-empty">
        <i className="ti ti-circle-check" aria-hidden="true" />
        <span>No pending override requests</span>
        <p>Override requests from Contributors will appear here.</p>
      </div>
    );
  }

  const renderRow = (item: OverrideRequest, faded = false) => {
    const isBusy = busy === item.id;
    const repeated = item.overrideRequestCount >= 2;
    return (
      <tr key={item.id} className={`rc-tr${faded ? " rc-tr-faded" : ""}`}>
        <td className="rc-td-title">
          {item.eventTitle}
          {repeated && (
            <span className="rc-repeat-flag" title="Multiple override requests for this submission">
              <i className="ti ti-alert-triangle" aria-hidden="true" /> Repeated
            </span>
          )}
        </td>
        <td>{item.institutionName}</td>
        <td>{contributorName(item)}</td>
        <td className="rc-td-mono">{formatSlot(item.requestedSlot)}</td>
        <td>
          <span className="rc-rule-badge">{item.violatedRule}</span>
        </td>
        <td className="rc-td-reason">
          {item.overrideReason ? (
            <span className="rc-reason-text" title={item.overrideReason}>
              {item.overrideReason.length > 60
                ? item.overrideReason.slice(0, 60) + "…"
                : item.overrideReason}
            </span>
          ) : (
            <span className="rc-muted">—</span>
          )}
        </td>
        {!faded ? (
          <td className="rc-td-actions">
            <button
              type="button"
              className="rc-act-btn rc-act-approve"
              disabled={isBusy}
              onClick={() => void handleApprove(item)}
              title="Approve override — bypass guard rail for this submission"
            >
              {isBusy ? <div className="spinner-ring spinner-ring-xs" /> : <i className="ti ti-check" />}
              Approve
            </button>
            <button
              type="button"
              className="rc-act-btn rc-act-suggest"
              disabled={isBusy}
              onClick={() => setSuggestTarget(item)}
              title="Suggest an alternative compliant slot"
            >
              <i className="ti ti-calendar-plus" />
              Suggest
            </button>
            <button
              type="button"
              className="rc-act-btn rc-act-reject"
              disabled={isBusy}
              onClick={() => setDenyTarget(item)}
              title="Deny this override request"
            >
              <i className="ti ti-x" />
              Deny
            </button>
          </td>
        ) : (
          <td className="rc-td-actions">
            <span className="rc-muted rc-expired-label">Slot passed</span>
          </td>
        )}
      </tr>
    );
  };

  return (
    <>
      {/* Active requests */}
      {active.length > 0 && (
        <div className="rc-table-wrap">
          <div className="rc-table-section-label">
            Active Requests — sorted by urgency
          </div>
          <table className="rc-table">
            <thead>
              <tr>
                <th>Event</th>
                <th>Institution</th>
                <th>Contributor</th>
                <th>Requested Slot</th>
                <th>Guard Rail</th>
                <th>Justification</th>
                <th className="rc-th-actions">Actions</th>
              </tr>
            </thead>
            <tbody>{active.map((r) => renderRow(r, false))}</tbody>
          </table>
        </div>
      )}

      {/* Expired requests */}
      {expired.length > 0 && (
        <details className="rc-expired-section">
          <summary className="rc-expired-summary">
            <i className="ti ti-clock-x" aria-hidden="true" />
            Expired Requests
            <span className="rc-expired-count">{expired.length}</span>
          </summary>
          <div className="rc-table-wrap rc-table-wrap-inner">
            <table className="rc-table">
              <thead>
                <tr>
                  <th>Event</th>
                  <th>Institution</th>
                  <th>Contributor</th>
                  <th>Requested Slot</th>
                  <th>Guard Rail</th>
                  <th>Justification</th>
                  <th className="rc-th-actions">Status</th>
                </tr>
              </thead>
              <tbody>{expired.map((r) => renderRow(r, true))}</tbody>
            </table>
          </div>
        </details>
      )}

      <SlotSuggestionModal
        request={suggestTarget}
        busy={suggestTarget ? busy === suggestTarget.id : false}
        onConfirm={handleSuggestConfirm}
        onClose={() => setSuggestTarget(null)}
      />

      <RejectOnBehalfModal
        open={denyTarget !== null}
        eventTitle={denyTarget?.eventTitle ?? ""}
        mode="override"
        busy={denyTarget ? busy === denyTarget.id : false}
        onConfirm={handleDenyConfirm}
        onClose={() => setDenyTarget(null)}
      />
    </>
  );
}

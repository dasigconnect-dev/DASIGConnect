import { useCallback, useEffect, useState } from "react";
import {
  approveTimeout,
  deferTimeout,
  getTimeoutEscalations,
  rejectTimeout,
  type TimeoutEscalation,
} from "../../api/resolutionApi";
import { useToast } from "../../context/ToastContext";
import RejectOnBehalfModal from "./RejectOnBehalfModal";

function minutesUntil(iso: string | null): number {
  if (!iso) return Infinity;
  return Math.floor((new Date(iso).getTime() - Date.now()) / 60_000);
}

function formatScheduled(iso: string | null) {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("en-PH", {
    month: "short", day: "numeric", hour: "numeric", minute: "2-digit", hour12: true,
  });
}

function contributorName(item: TimeoutEscalation) {
  const parts = [item.contributorFirstName, item.contributorLastName].filter(Boolean);
  return parts.length > 0 ? parts.join(" ") : item.contributorEmail;
}

interface UrgencyPillProps { scheduledAt: string | null }
function UrgencyPill({ scheduledAt }: UrgencyPillProps) {
  const mins = minutesUntil(scheduledAt);
  if (mins <= 0) return <span className="rc-urgency rc-urgency-red">Overdue</span>;
  if (mins < 10) return <span className="rc-urgency rc-urgency-red">{mins}m left</span>;
  if (mins < 20) return <span className="rc-urgency rc-urgency-amber">{mins}m left</span>;
  return <span className="rc-urgency rc-urgency-green">{mins}m left</span>;
}

interface Props {
  refreshSignal: number;
  onCountChange?: (n: number) => void;
}

export default function ValidationTimeoutTab({ refreshSignal, onCountChange }: Props) {
  const toast = useToast();
  const [items, setItems] = useState<TimeoutEscalation[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const [rejectTarget, setRejectTarget] = useState<TimeoutEscalation | null>(null);

  const load = useCallback((signal?: AbortSignal) => {
    setLoading(true);
    setError("");
    getTimeoutEscalations(signal)
      .then((res) => {
        setItems(res.data);
        onCountChange?.(res.data.length);
      })
      .catch((err: unknown) => {
        if ((err as { name?: string }).name === "CanceledError") return;
        setError("Could not load timeout escalations.");
      })
      .finally(() => setLoading(false));
  }, [onCountChange]);

  useEffect(() => {
    const controller = new AbortController();
    queueMicrotask(() => load(controller.signal));
    return () => controller.abort();
  }, [load, refreshSignal]);

  async function handleApprove(item: TimeoutEscalation) {
    setBusy(item.submissionId);
    try {
      await approveTimeout(item.submissionId);
      toast.success(`"${item.eventTitle}" approved — transitioning to SCHEDULED.`);
      load();
    } catch {
      toast.error("Approval failed. Please try again.");
    } finally {
      setBusy(null);
    }
  }

  async function handleDefer(item: TimeoutEscalation) {
    setBusy(item.submissionId);
    try {
      await deferTimeout(item.submissionId);
      toast.info(`"${item.eventTitle}" deferred — Contributor notified to resubmit.`);
      load();
    } catch {
      toast.error("Defer failed. Please try again.");
    } finally {
      setBusy(null);
    }
  }

  async function handleRejectConfirm(reasonCode: string, notes: string) {
    if (!rejectTarget) return;
    setBusy(rejectTarget.submissionId);
    try {
      await rejectTimeout(rejectTarget.submissionId, { reasonCode, notes: notes || undefined });
      toast.success(`"${rejectTarget.eventTitle}" rejected.`);
      setRejectTarget(null);
      load();
    } catch {
      toast.error("Rejection failed. Please try again.");
    } finally {
      setBusy(null);
    }
  }

  if (loading) {
    return (
      <div className="rc-tab-state">
        <div className="spinner-ring" />
        <span>Loading escalations...</span>
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

  if (items.length === 0) {
    return (
      <div className="rc-tab-state rc-tab-state-empty">
        <i className="ti ti-circle-check" aria-hidden="true" />
        <span>No pending validation timeouts</span>
        <p>All escalated submissions have been resolved.</p>
      </div>
    );
  }

  return (
    <>
      <div className="rc-table-wrap">
        <table className="rc-table">
          <thead>
            <tr>
              <th>Event</th>
              <th>Institution</th>
              <th>Contributor</th>
              <th>Scheduled</th>
              <th>Urgency</th>
              <th>Status</th>
              <th className="rc-th-actions">Actions</th>
            </tr>
          </thead>
          <tbody>
            {items.map((item) => {
              const isBusy = busy === item.submissionId;
              return (
                <tr key={item.submissionId} className="rc-tr">
                  <td className="rc-td-title">{item.eventTitle}</td>
                  <td>{item.institutionName}</td>
                  <td>{contributorName(item)}</td>
                  <td className="rc-td-mono">{formatScheduled(item.scheduledAt)}</td>
                  <td><UrgencyPill scheduledAt={item.scheduledAt} /></td>
                  <td>
                    <span className={`rc-status-badge rc-status-${item.status.toLowerCase()}`}>
                      {item.status.replace(/_/g, " ")}
                    </span>
                  </td>
                  <td className="rc-td-actions">
                    <button
                      type="button"
                      className="rc-act-btn rc-act-approve"
                      disabled={isBusy}
                      onClick={() => void handleApprove(item)}
                      title="Approve as fallback Validator"
                    >
                      {isBusy ? <div className="spinner-ring spinner-ring-xs" /> : <i className="ti ti-check" />}
                      Approve
                    </button>
                    <button
                      type="button"
                      className="rc-act-btn rc-act-defer"
                      disabled={isBusy}
                      onClick={() => void handleDefer(item)}
                      title="Defer — move to NEEDS_REVISION"
                    >
                      <i className="ti ti-clock-pause" />
                      Defer
                    </button>
                    <button
                      type="button"
                      className="rc-act-btn rc-act-reject"
                      disabled={isBusy}
                      onClick={() => setRejectTarget(item)}
                      title="Reject on behalf"
                    >
                      <i className="ti ti-x" />
                      Reject
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      <RejectOnBehalfModal
        open={rejectTarget !== null}
        eventTitle={rejectTarget?.eventTitle ?? ""}
        mode="timeout"
        busy={rejectTarget ? busy === rejectTarget.submissionId : false}
        onConfirm={handleRejectConfirm}
        onClose={() => setRejectTarget(null)}
      />
    </>
  );
}

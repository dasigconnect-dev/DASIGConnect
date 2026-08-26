import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { getSubmission, type SavedMediaAsset, type SubmissionSummary } from "../../api/submissionApi";
import {
  acquireReviewLock,
  approveSubmission,
  editSubmission,
  getReviewLockStatus,
  getValidationQueue,
  rejectSubmission,
  releaseReviewLock,
  requestSubmissionRevision,
  type RejectionReasonCode,
  type ReviewLock,
} from "../../api/validationApi";
import { useToast } from "../../context/ToastContext";
import type { User } from "../../types/auth.types";
import { getWatermarkConfiguration } from "../../api/watermarkApi";
import type { WatermarkConfiguration } from "../../types/watermark.types";
import WatermarkOverlay from "../../components/watermark/WatermarkOverlay";
import {
  useValidationLog,
  useValidationQueue,
} from "./hooks/useValidationQueue";
import { useResolutionFailures } from "../../hooks/useResolutionFailures";
import type { FailedPublication } from "../../api/resolutionApi";
import ResolutionRetryModal from "../resolution/ResolutionRetryModal";
import ManualPublishWorkflowPanel from "../resolution/ManualPublishWorkflowPanel";
import "../../styles/resolution.css";

interface ValidationQueueScreenProps {
  user: User;
}

type QueueFilter = "pending" | "in_review" | "all" | "failed";
type SortKey = "publish_slot" | "submitted";
type DecisionModal = "approve" | "revise" | "reject" | null;
const MODAL_EXIT_MS = 190;
const REVIEWABLE_STATUSES = new Set(["pending", "in_review"]);

interface EditFormState {
  eventTitle: string;
  eventDate: string;
  caption: string;
  description: string;
  tags: string;
}

function emptyEditForm(): EditFormState {
  return { eventTitle: "", eventDate: "", caption: "", description: "", tags: "" };
}

function toEditForm(summary: SubmissionSummary): EditFormState {
  return {
    eventTitle: summary.eventTitle || "",
    eventDate: summary.eventDate ? summary.eventDate.slice(0, 10) : "",
    caption: summary.caption || "",
    description: summary.description || "",
    tags: summary.tags?.join(", ") || "",
  };
}

const rejectionReasons: Array<{ code: RejectionReasonCode; label: string }> = [
  { code: "INCOMPLETE_CONTENT", label: "Incomplete content" },
  { code: "INAPPROPRIATE_CONTENT", label: "Inappropriate content" },
  { code: "WRONG_FORMAT", label: "Wrong format" },
  { code: "DUPLICATE_EVENT", label: "Duplicate event" },
  { code: "WRONG_INSTITUTION", label: "Wrong institution" },
  { code: "OTHER", label: "Other" },
];

const statusLabel: Record<string, string> = {
  pending: "Pending",
  in_review: "In Review",
  needs_revision: "Needs Revision",
  missed_review: "Missed Review",
  scheduled: "Scheduled",
  publishing: "Publishing",
  published: "Published",
  published_manual: "Published (Manual)",
  admin_direct_post: "Direct Post",
  direct_post_scheduled: "Direct Post Scheduled",
  direct_post_publishing: "Direct Post Publishing",
  direct_post_failed: "Direct Post Failed",
  publish_failed: "Publish Failed",
  rejected: "Rejected",
};

export default function ValidationQueueScreen({
  user,
}: ValidationQueueScreenProps) {
  const toast = useToast();
  const { queue: activeQueue, loading: activeLoading, error: activeError, refresh } = useValidationQueue();
  const [allQueue, setAllQueue] = useState<SubmissionSummary[]>([]);
  const [allLoading, setAllLoading] = useState(false);
  const [allError, setAllError] = useState("");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [selected, setSelected] = useState<SubmissionSummary | null>(null);
  const [selectedLoading, setSelectedLoading] = useState(false);
  const [locks, setLocks] = useState<Record<string, ReviewLock>>({});
  const [lockNotice, setLockNotice] = useState("");
  const [lockBusy, setLockBusy] = useState(false);
  const [filter, setFilter] = useState<QueueFilter>("pending");
  const [sortKey, setSortKey] = useState<SortKey>("publish_slot");
  const [search, setSearch] = useState("");
  const [mediaIndex, setMediaIndex] = useState(0);
  const [renderedModal, setRenderedModal] = useState<DecisionModal>(null);
  const [modalClosing, setModalClosing] = useState(false);
  const [decisionBusy, setDecisionBusy] = useState(false);
  const [remarks, setRemarks] = useState("");
  const [reasonCode, setReasonCode] =
    useState<RejectionReasonCode>("INCOMPLETE_CONTENT");
  const [notes, setNotes] = useState("");
  const [editMode, setEditMode] = useState(false);
  const [editSaving, setEditSaving] = useState(false);
  const [editForm, setEditForm] = useState<EditFormState>(emptyEditForm());
  const [editedThisSession, setEditedThisSession] = useState(false);
  const { log, loading: logLoading, refresh: refreshLog } = useValidationLog(selectedId);
  const modalExitTimer = useRef<number | null>(null);

  const {
    failures,
    loading: failuresLoading,
    error: failuresError,
    busy: failureBusy,
    activeDetail: manualPublishDetail,
    detailLoading: manualPublishDetailLoading,
    handleRetryWithNewSchedule: handleFailureRetryWithNewSchedule,
    handleStartManual,
    handleCancelManual,
    handleCompleteManual,
    openWorkflowPanel,
    closeWorkflowPanel,
  } = useResolutionFailures();
  const [retryItem, setRetryItem] = useState<FailedPublication | null>(null);
  const [selectedFailureId, setSelectedFailureId] = useState<string | null>(null);
  const [failureContent, setFailureContent] = useState<SubmissionSummary | null>(null);
  const [failureContentLoading, setFailureContentLoading] = useState(false);
  const [failureMediaIndex, setFailureMediaIndex] = useState(0);

  const isAllMode = filter === "all";
  const isFailedMode = filter === "failed";
  const selectedFailure = selectedFailureId
    ? failures.find((f) => f.submissionId === selectedFailureId) ?? null
    : null;
  const queue = isAllMode ? allQueue : activeQueue;
  const loading = isAllMode ? allLoading : activeLoading;
  const error = isAllMode ? allError : activeError;

  const filteredQueue = useMemo(() => {
    const term = search.trim().toLowerCase();
    return queue
      .filter((item) => {
        const status = normalizeStatus(item.status);
        if (filter !== "all" && status !== filter) return false;
        if (!term) return true;
        return [
          item.eventTitle,
          item.contributorEmail,
          item.institutionName,
          item.eventDate,
          item.caption,
          item.description,
          item.tags?.join(" "),
        ]
          .filter(Boolean)
          .some((value) => value!.toLowerCase().includes(term));
      })
      .sort((a, b) => {
        const left =
          sortKey === "publish_slot" ? a.scheduledAt || "" : a.submittedAt || a.createdAt || "";
        const right =
          sortKey === "publish_slot" ? b.scheduledAt || "" : b.submittedAt || b.createdAt || "";
        const cmp = left.localeCompare(right);
        return isAllMode ? -cmp : cmp;
      });
  }, [filter, queue, search, sortKey]);

  const visibleLog = useMemo(
    () =>
      log.filter(
        (entry) =>
          entry.action !== "lock_acquired" &&
          entry.action !== "lock_released",
      ),
    [log],
  );

  const pendingCount = activeQueue.filter(
    (item) => normalizeStatus(item.status) === "pending",
  ).length;
  const reviewCount = activeQueue.filter(
    (item) => normalizeStatus(item.status) === "in_review",
  ).length;

  const activeLock = selected ? locks[selected.id] ?? null : null;

  const mediaAssets = selected?.mediaAssets ?? [];
  const isSelfReview =
    Boolean(selected?.contributorEmail) &&
    selected?.contributorEmail?.toLowerCase() === user.email.toLowerCase();
  const isTerminalStatus = Boolean(
    selected && !REVIEWABLE_STATUSES.has(normalizeStatus(selected.status ?? "")),
  );

  const [watermarkConfig, setWatermarkConfig] = useState<WatermarkConfiguration | null>(null);
  const [showWatermarkPreview, setShowWatermarkPreview] = useState<boolean>(true);

  useEffect(() => {
    void getWatermarkConfiguration()
      .then((res) => setWatermarkConfig(res.data))
      .catch(() => undefined);
  }, []);

  useEffect(() => {
    if (!isAllMode) return;
    let active = true;
    queueMicrotask(() => {
      if (!active) return;
      setAllLoading(true);
      setAllError("");
      getValidationQueue({ history : true })
        .then((res) => { if (active) setAllQueue(res.data); })
        .catch((err: unknown) => { if (active) setAllError(readApiError(err, "Unable to load all submissions.")); })
        .finally(() => { if (active) setAllLoading(false); });
    });
    return () => { active = false; };
  }, [isAllMode]);

  useEffect(() => {
    if (!isFailedMode || selectedFailureId || failuresLoading || failures.length === 0) return;
    queueMicrotask(() => setSelectedFailureId(failures[0].submissionId));
  }, [isFailedMode, failuresLoading, failures, selectedFailureId]);

  useEffect(() => {
    if (!selectedFailureId) {
      queueMicrotask(() => setFailureContent(null));
      return;
    }
    let active = true;
    queueMicrotask(() => {
      if (!active) return;
      setFailureContent(null);
      setFailureMediaIndex(0);
      setFailureContentLoading(true);
      getSubmission(selectedFailureId)
        .then((res) => { if (active) setFailureContent(res.data); })
        .catch((err: unknown) => { if (active) toast.error(readApiError(err, "Unable to load submission content.")); })
        .finally(() => { if (active) setFailureContentLoading(false); });
    });
    return () => { active = false; };
  }, [selectedFailureId]);

  useEffect(() => {
    if (selectedId || loading || queue.length === 0) return;
    void openSubmission(queue[0]);
  }, [loading, queue, selectedId]);

  useEffect(() => {
    return () => {
      if (modalExitTimer.current) window.clearTimeout(modalExitTimer.current);
    };
  }, []);

  function handleFilterChange(next: QueueFilter) {
    if (next === filter) return;
    // The active review lock (and the currently open submission) persists across
    // tab switches — only explicitly opening a different submission releases it.
    setSortKey(next === "all" ? "submitted" : "publish_slot");
    setFilter(next);
  }

  function openDecisionModal(nextModal: Exclude<DecisionModal, null>) {
    if (modalExitTimer.current) window.clearTimeout(modalExitTimer.current);
    setModalClosing(false);
    setRenderedModal(nextModal);
  }

  function closeDecisionModal() {
    if (!renderedModal || modalClosing) return;
    setModalClosing(true);
    modalExitTimer.current = window.setTimeout(() => {
      setRenderedModal(null);
      setModalClosing(false);
      modalExitTimer.current = null;
    }, MODAL_EXIT_MS);
  }

  async function openSubmission(summary: SubmissionSummary) {
    if (selectedId === summary.id) {
      return;
    }

    // Opening a different submission does not release any lock already held —
    // locks persist per-submission until explicitly unlocked, decided, or expired.
    setSelectedId(summary.id);
    setSelected(summary);
    setSelectedLoading(true);
    setMediaIndex(0);
    setLockNotice("");
    setEditMode(false);
    setEditedThisSession(false);

    try {
      const detail = await getSubmission(summary.id);
      setSelected(detail.data);

      // Restore lock UI state (e.g. after a page refresh) without acquiring
      // anything — a read-only check against the backend's current lock.
      if (REVIEWABLE_STATUSES.has(normalizeStatus(detail.data.status))) {
        const lockStatus = await getReviewLockStatus(summary.id);
        if (lockStatus.data) {
          if (lockStatus.data.lockedByEmail.toLowerCase() === user.email.toLowerCase()) {
            setLockFor(summary.id, lockStatus.data);
          } else {
            setLockNotice(`This submission is currently being reviewed by ${lockStatus.data.lockedByEmail}.`);
          }
        }
      }
    } catch (err: unknown) {
      toast.error(readApiError(err, "Unable to open this submission."));
    } finally {
      setSelectedLoading(false);
    }
  }

  function setLockFor(submissionId: string, lock: ReviewLock) {
    setLocks((prev) => ({ ...prev, [submissionId]: lock }));
  }

  function clearLockFor(submissionId: string) {
    setLocks((prev) => {
      if (!(submissionId in prev)) return prev;
      const next = { ...prev };
      delete next[submissionId];
      return next;
    });
  }

  async function handleAcquireLock() {
    if (!selected) return;
    setLockBusy(true);
    try {
      const lock = await acquireReviewLock(selected.id);
      setLockFor(selected.id, lock.data);
      setLockNotice("");
      await refresh();
    } catch (err: unknown) {
      const message = readApiError(err, "Unable to acquire the review lock.");
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 409) {
        setLockNotice(message);
      } else {
        toast.error(message);
      }
    } finally {
      setLockBusy(false);
    }
  }

  async function handleReleaseLock() {
    if (!activeLock) return;
    setLockBusy(true);
    try {
      await releaseReviewLock(activeLock.submissionId);
      clearLockFor(activeLock.submissionId);
      toast.info("Review lock released.");
      await refresh();
    } catch (err: unknown) {
      toast.error(readApiError(err, "Unable to release the review lock."));
    } finally {
      setLockBusy(false);
    }
  }

  async function handleApprove() {
    if (!selected) return;
    setDecisionBusy(true);
    try {
      await approveSubmission(selected.id);
      toast.success("Submission approved and scheduled.");
      closeDecisionModal();
      clearLockFor(selected.id);
      setSelected(null);
      setSelectedId(null);
      await refresh();
    } catch (err: unknown) {
      toast.error(readApiError(err, "Approval failed."));
    } finally {
      setDecisionBusy(false);
    }
  }

  function handleStartEdit() {
    if (!selected) return;
    setEditForm(toEditForm(selected));
    setEditMode(true);
  }

  function handleCancelEdit() {
    setEditMode(false);
  }

  async function handleSaveEdit() {
    if (!selected) return;
    setEditSaving(true);
    try {
      await editSubmission(selected.id, {
        eventTitle: editForm.eventTitle,
        eventDate: editForm.eventDate || undefined,
        caption: editForm.caption,
        description: editForm.description,
        tags: editForm.tags
          ? editForm.tags.split(",").map((t) => t.trim()).filter(Boolean)
          : undefined,
      });
      // A9: the submission stays IN_REVIEW — re-render the edited content and
      // keep the panel + lock open so the Administrator can still pick a
      // terminal action.
      const detail = await getSubmission(selected.id);
      setSelected(detail.data);
      setEditMode(false);
      setEditedThisSession(true);
      await refreshLog();
      toast.success("Changes saved — choose a terminal action.");
    } catch (err: unknown) {
      toast.error(readApiError(err, "Saving the edit failed."));
    } finally {
      setEditSaving(false);
    }
  }

  async function handleRevise() {
    if (!selected) return;
    if (remarks.trim().length < 10) {
      toast.error("Revision remarks must be at least 10 characters.");
      return;
    }
    setDecisionBusy(true);
    try {
      await requestSubmissionRevision(selected.id, { remarks: remarks.trim() });
      toast.warning("Revision request sent to the contributor.");
      closeDecisionModal();
      setRemarks("");
      clearLockFor(selected.id);
      setSelected(null);
      setSelectedId(null);
      await refresh();
    } catch (err: unknown) {
      toast.error(readApiError(err, "Revision request failed."));
    } finally {
      setDecisionBusy(false);
    }
  }

  async function handleReject() {
    if (!selected) return;
    if (reasonCode === "OTHER" && notes.trim().length === 0) {
      toast.error("Notes are required when the rejection reason is Other.");
      return;
    }
    setDecisionBusy(true);
    try {
      await rejectSubmission(selected.id, {
        reasonCode,
        notes: notes.trim() || undefined,
      });
      toast.info("Submission rejected and contributor notified.");
      closeDecisionModal();
      setNotes("");
      setReasonCode("INCOMPLETE_CONTENT");
      clearLockFor(selected.id);
      setSelected(null);
      setSelectedId(null);
      await refresh();
    } catch (err: unknown) {
      toast.error(readApiError(err, "Rejection failed."));
    } finally {
      setDecisionBusy(false);
    }
  }

  return (
    <div className="val-page">
      <aside className="val-queue-panel">
        <div className="val-queue-header">
          <div className="val-title-row">
            <div>
              <h1>Review Queue</h1>
            </div>
            <span className="val-count">{isFailedMode ? failures.length : filteredQueue.length}</span>
          </div>

          <div className="val-tabs" role="tablist" aria-label="Queue filters">
            <button
              className={filter === "pending" ? "active" : ""}
              type="button"
              onClick={() => handleFilterChange("pending")}
            >
              Pending <span>{pendingCount}</span>
            </button>
            <button
              className={filter === "in_review" ? "active" : ""}
              type="button"
              onClick={() => handleFilterChange("in_review")}
            >
              Review <span>{reviewCount}</span>
            </button>
            <button
              className={filter === "all" ? "active" : ""}
              type="button"
              onClick={() => handleFilterChange("all")}
            >
              All <span>{allQueue.length}</span>
            </button>
            <button
              className={filter === "failed" ? "active" : ""}
              type="button"
              onClick={() => handleFilterChange("failed")}
            >
              Failed <span>{failures.length}</span>
            </button>
          </div>

          <label className="val-search">
            <i className="ti ti-search"></i>
            <input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Search title, contributor, tags..."
            />
          </label>
        </div>

        {!isFailedMode && (
          <div className="val-sort-row">
            <span>Sort by</span>
            <button
              className={sortKey === "publish_slot" ? "active" : ""}
              type="button"
              onClick={() => setSortKey("publish_slot")}
            >
              <i className="ti ti-calendar-due"></i> Publish Slot
            </button>
            <button
              className={sortKey === "submitted" ? "active" : ""}
              type="button"
              onClick={() => setSortKey("submitted")}
            >
              <i className="ti ti-send"></i> Submitted
            </button>
          </div>
        )}

        <div className="val-queue-list">
          {isFailedMode ? (
            <>
              {failuresLoading && <QueueState icon="ti-loader-2 val-spin" title="Loading failed publications" />}
              {!failuresLoading && failuresError && (
                <QueueState icon="ti-database-off" title="Unable to load failures" subtitle={failuresError} />
              )}
              {!failuresLoading && !failuresError && failures.length === 0 && (
                <QueueState
                  icon="ti-circle-check"
                  title="No failed publications"
                  subtitle="Automated publish failures needing manual recovery will appear here."
                />
              )}
              {!failuresLoading &&
                !failuresError &&
                failures.map((item) => (
                  <button
                    className={`val-queue-item ${item.submissionId === selectedFailureId ? "active" : ""}`}
                    key={item.submissionId}
                    type="button"
                    onClick={() => setSelectedFailureId(item.submissionId)}
                  >
                    <div className="val-qi-head">
                      <strong>{item.eventTitle || "Untitled submission"}</strong>
                      <span className={`val-status ${item.status === "missed_review" ? "needs_revision" : "publish_failed"}`}>
                        {item.status === "missed_review"
                          ? "Missed Review"
                          : item.manualPublishInProgress
                            ? "Manual Session Open"
                            : "Publish Failed"}
                      </span>
                    </div>
                    <div className="val-qi-meta">
                      <span>{item.institutionName || "Unknown institution"}</span>
                      <i></i>
                      <span>{item.retryCount} retry attempt{item.retryCount === 1 ? "" : "s"}</span>
                    </div>
                    <div className="val-qi-bottom">
                      <span className="val-deadline">
                        <i className="ti ti-clock"></i>
                        {item.scheduledAt ? formatDateTime(item.scheduledAt) : "No slot"}
                      </span>
                      {item.lastAttemptAt && (
                        <span className="val-media-count">
                          <i className="ti ti-history"></i> {formatDate(item.lastAttemptAt)}
                        </span>
                      )}
                    </div>
                  </button>
                ))}
            </>
          ) : (
            <>
              {loading && <QueueState icon="ti-loader-2 val-spin" title="Loading validation queue" />}
              {!loading && error && (
                <QueueState
                  icon="ti-database-off"
                  title="Unable to load queue"
                  subtitle={error}
                />
              )}
              {!loading && !error && filteredQueue.length === 0 && (
                <QueueState
                  icon="ti-inbox"
                  title="No submissions in this view"
                  subtitle="Approved, rejected, and revisioned submissions leave the active queue."
                />
              )}
              {!loading &&
                !error &&
                filteredQueue.map((item) => (
                  <button
                    className={`val-queue-item ${item.id === selectedId ? "active" : ""} ${deadlineTone(item.scheduledAt)}`}
                    key={item.id}
                    type="button"
                    onClick={() => void openSubmission(item)}
                  >
                    <div className="val-qi-head">
                      <strong>{item.eventTitle || "Untitled submission"}</strong>
                      <span className={`val-status ${normalizeStatus(item.status)}`}>
                        {statusLabel[normalizeStatus(item.status)] || item.status}
                      </span>
                    </div>
                    <div className="val-qi-meta">
                      <span>{item.institutionName || "Unknown institution"}</span>
                      <i></i>
                      <span>{item.contributorEmail || "Unknown contributor"}</span>
                      <i></i>
                      <span>{formatDate(item.submittedAt || item.createdAt || item.eventDate)}</span>
                    </div>
                    <div className="val-qi-bottom">
                      {item.fastTrack ? (
                        <span className="val-deadline val-live">
                          <i className="ti ti-broadcast"></i>
                          Live Event
                        </span>
                      ) : (
                        <span className="val-deadline">
                          <i className="ti ti-clock"></i>
                          {item.scheduledAt ? formatDateTime(item.scheduledAt) : "No slot"}
                        </span>
                      )}
                      <span className="val-media-count">
                        <i className="ti ti-photo"></i> {item.mediaCount ?? 0}
                      </span>
                    </div>
                  </button>
                ))}
            </>
          )}
        </div>
      </aside>

      <main className="val-review-panel">
        {isFailedMode && !selectedFailure && (
          <div className="val-empty">
            <i className="ti ti-mood-sad"></i>
            <h2>{failures.length === 0 ? "No failed publications" : "Select a failed submission"}</h2>
            <p>
              {failures.length === 0
                ? "Automated publish failures needing manual recovery will appear here."
                : "Open an item from the list to retry it or fall back to manual publishing."}
            </p>
          </div>
        )}

        {isFailedMode && selectedFailure && (
          <>
            {selectedFailure.lastManualPublishAbandonedAt && (
              <NoticeBar
                tone="warn"
                icon="ti-alert-triangle"
                text={`A manual publish session was abandoned on ${formatDateTime(selectedFailure.lastManualPublishAbandonedAt)}.`}
              />
            )}

            <div className="val-scroll">
              <header className="val-review-header">
                <div>
                  <div className="val-badge-row">
                    <span className="val-inst">{selectedFailure.institutionName || "Unknown institution"}</span>
                    <span className="val-sub-id">{shortId(selectedFailure.submissionId)}</span>
                  </div>
                  <h2>{selectedFailure.eventTitle || "Untitled submission"}</h2>
                  {failureContent?.contributorEmail && (
                    <p>
                      <i className="ti ti-user"></i>
                      Submitted by <strong>{failureContent.contributorEmail}</strong>
                    </p>
                  )}
                </div>
                <div className="val-slot-card">
                  <span>Publish Slot</span>
                  <strong>
                    {selectedFailure.scheduledAt ? formatDate(selectedFailure.scheduledAt) : "Unscheduled"}
                  </strong>
                  <small>
                    {selectedFailure.scheduledAt ? formatTime(selectedFailure.scheduledAt) : "No preferred time"}
                  </small>
                </div>
              </header>

              {failureContentLoading && (
                <p className="val-muted">Loading submission content...</p>
              )}

              {!failureContentLoading && failureContent && (
                <>
                  <MediaCarousel
                    mediaAssets={failureContent.mediaAssets ?? []}
                    mediaIndex={failureMediaIndex}
                    onMediaIndexChange={setFailureMediaIndex}
                  />
                  <SubmissionDetailCards content={failureContent} />
                </>
              )}

              <section className="val-detail-grid">
                <DetailCard icon="ti-refresh" label="Retry Attempts">
                  {selectedFailure.retryCount}
                </DetailCard>
                <DetailCard icon="ti-clock-hour-4" label="Last Attempt">
                  {selectedFailure.lastAttemptAt ? formatDateTime(selectedFailure.lastAttemptAt) : "No attempts recorded"}
                </DetailCard>
                {selectedFailure.lastError && (
                  <DetailCard icon="ti-bug" label="Last Error" full muted>
                    {selectedFailure.lastError}
                  </DetailCard>
                )}
              </section>
            </div>

            <footer className="val-action-bar">
              <span>
                <i className="ti ti-info-circle"></i>
                {selectedFailure.status === "missed_review"
                  ? "This submission missed its review window. Assign a new schedule to send it back to the approval queue."
                  : selectedFailure.manualPublishInProgress
                    ? "A manual publish session is already open for this submission."
                    : "Retry automatically, or fall back to manual publishing."}
              </span>
              {selectedFailure.status === "missed_review" ? (
                <button
                  className="val-btn success"
                  type="button"
                  disabled={failureBusy === selectedFailure.submissionId}
                  onClick={() => setRetryItem(selectedFailure)}
                >
                  <i className="ti ti-calendar-plus"></i> Retry with New Schedule
                </button>
              ) : selectedFailure.manualPublishInProgress ? (
                <>
                  <button
                    className="val-btn ghost"
                    type="button"
                    disabled={failureBusy === selectedFailure.submissionId}
                    onClick={() => void handleCancelManual(selectedFailure)}
                  >
                    <i className="ti ti-x"></i> Cancel Manual Session
                  </button>
                  <button
                    className="val-btn success"
                    type="button"
                    onClick={() => openWorkflowPanel(selectedFailure)}
                  >
                    <i className="ti ti-user-check"></i> Continue Manual Publish
                  </button>
                </>
              ) : (
                <>
                  <button
                    className="val-btn ghost"
                    type="button"
                    disabled={failureBusy === selectedFailure.submissionId}
                    onClick={() => setRetryItem(selectedFailure)}
                  >
                    <i className="ti ti-refresh"></i> Retry
                  </button>
                  <button
                    className="val-btn success"
                    type="button"
                    disabled={failureBusy === selectedFailure.submissionId}
                    onClick={() => void handleStartManual(selectedFailure)}
                  >
                    <i className="ti ti-user-check"></i> Start Manual Publish
                  </button>
                </>
              )}
            </footer>
          </>
        )}

        {!isFailedMode && !selected && !selectedLoading && (
          <div className="val-empty">
            <i className="ti ti-clipboard-check"></i>
            <h2>Select a submission</h2>
            <p>Open an item from the queue to acquire a review lock and inspect its content.</p>
          </div>
        )}

        {!isFailedMode && selected && (
          <>
            {isSelfReview && (
              <NoticeBar
                tone="warn"
                icon="ti-alert-triangle"
                text="You are reviewing your own submission. This action will be flagged in the audit log."
              />
            )}
            {lockNotice && (
              <NoticeBar tone="warn" icon="ti-lock" text={lockNotice} />
            )}
            {activeLock && (
              <NoticeBar
                tone="info"
                icon="ti-lock-open"
                text={`You hold the review lock until ${formatDateTime(activeLock.expiresAt)}.`}
              />
            )}

            <div className="val-scroll">
              <header className="val-review-header">
                <div>
                  <div className="val-badge-row">
                    <span className="val-inst">{selected.institutionName || "Unknown institution"}</span>
                    <span className="val-sub-id">{shortId(selected.id)}</span>
                  </div>
                  <h2>{selected.eventTitle || "Untitled submission"}</h2>
                  <p>
                    <i className="ti ti-user"></i>
                    Submitted by <strong>{selected.contributorEmail}</strong>
                  </p>
                </div>
                {selected.fastTrack ? (
                  <div className="val-slot-card val-live">
                    <span>
                      <i className="ti ti-broadcast"></i> Live
                    </span>
                    <strong>Publishes immediately</strong>
                  </div>
                ) : (
                  <div className="val-slot-card">
                    <span>Publish Slot</span>
                    <strong>
                      {selected.scheduledAt
                        ? formatDate(selected.scheduledAt)
                        : "Unscheduled"}
                    </strong>
                    <small>
                      {selected.scheduledAt
                        ? formatTime(selected.scheduledAt)
                        : "No preferred time"}
                    </small>
                  </div>
                )}
              </header>

              <MediaCarousel
                mediaAssets={mediaAssets}
                mediaIndex={mediaIndex}
                onMediaIndexChange={setMediaIndex}
                watermarkConfig={watermarkConfig}
                showWatermarkPreview={showWatermarkPreview}
                onToggleWatermark={() => setShowWatermarkPreview((prev) => !prev)}
              />

              {editMode ? (
                <section className="val-detail-grid val-edit-grid">
                  <label className="val-edit-field">
                    <span>Event Title</span>
                    <input
                      value={editForm.eventTitle}
                      onChange={(e) => setEditForm({ ...editForm, eventTitle: e.target.value })}
                    />
                  </label>
                  <label className="val-edit-field">
                    <span>Event Date</span>
                    <input
                      type="date"
                      value={editForm.eventDate}
                      onChange={(e) => setEditForm({ ...editForm, eventDate: e.target.value })}
                    />
                  </label>
                  <label className="val-edit-field full">
                    <span>Tags (comma-separated)</span>
                    <input
                      value={editForm.tags}
                      onChange={(e) => setEditForm({ ...editForm, tags: e.target.value })}
                    />
                  </label>
                  <label className="val-edit-field full">
                    <span>Facebook Caption</span>
                    <textarea
                      rows={4}
                      value={editForm.caption}
                      onChange={(e) => setEditForm({ ...editForm, caption: e.target.value })}
                    />
                  </label>
                  <label className="val-edit-field full">
                    <span>Administrator Notes</span>
                    <textarea
                      rows={3}
                      value={editForm.description}
                      onChange={(e) => setEditForm({ ...editForm, description: e.target.value })}
                    />
                  </label>
                </section>
              ) : (
                <SubmissionDetailCards content={selected} />
              )}

              <section className="val-log-card">
                <div className="val-section-head">
                  <div>
                    <i className="ti ti-history"></i>
                    History
                  </div>
                  <span>{visibleLog.length}</span>
                </div>
                {logLoading && <p className="val-muted">Loading audit log...</p>}
                {!logLoading && visibleLog.length === 0 && (
                  <p className="val-muted">
                    {isTerminalStatus
                      ? "No validation actions recorded — this submission was not reviewed through the validation workflow."
                      : "No approval, revision, rejection, or timeout actions recorded yet."}
                  </p>
                )}
                {!logLoading &&
                  visibleLog.map((entry) => (
                    <div className="val-log-item" key={entry.id}>
                      <div className="val-log-dot">
                        <i className={`ti ${logIcon(entry.action)}`}></i>
                      </div>
                      <div>
                        <strong>
                          {formatAction(entry.action)}
                          {entry.selfReview && <span className="val-log-flag">Self-review</span>}
                          {entry.fastTrack && <span className="val-log-flag">Fast-Track</span>}
                        </strong>
                        <span>
                          {entry.validatorEmail} · {formatDateTime(entry.createdAt)}
                        </span>
                        {entry.remarks && <p>{entry.remarks}</p>}
                        {entry.rejectionReason && <p>{entry.rejectionReason}</p>}
                        {entry.editDiff && <EditDiffView diffJson={entry.editDiff} />}
                      </div>
                    </div>
                  ))}
              </section>
            </div>

            {isTerminalStatus ? (
              <footer className="val-action-bar val-action-bar--readonly">
                <span>
                  <i className="ti ti-eye"></i>
                  Read-only — this submission is {statusLabel[normalizeStatus(selected?.status ?? "")] ?? selected?.status ?? "in a terminal state"} and can no longer be acted on.
                </span>
              </footer>
            ) : editMode ? (
              <footer className="val-action-bar">
                <span>
                  <i className="ti ti-pencil"></i>
                  Editing — saving keeps the submission In Review; you still choose a terminal action after.
                </span>
                <button
                  className="val-btn ghost"
                  type="button"
                  disabled={editSaving}
                  onClick={handleCancelEdit}
                >
                  Cancel
                </button>
                <button
                  className="val-btn success"
                  type="button"
                  disabled={editSaving}
                  onClick={() => void handleSaveEdit()}
                >
                  <i className="ti ti-device-floppy"></i>
                  {editSaving ? "Saving..." : "Save Changes"}
                </button>
              </footer>
            ) : activeLock ? (
              <footer className="val-action-bar">
                <span>
                  <i className="ti ti-info-circle"></i>
                  Record your decision. Actions are permanent and logged.
                </span>
                <button
                  className="val-btn ghost"
                  type="button"
                  disabled={lockBusy}
                  onClick={() => void handleReleaseLock()}
                >
                  <i className="ti ti-lock-open"></i> Unlock
                </button>
                <button
                  className="val-btn danger"
                  type="button"
                  onClick={() => openDecisionModal("reject")}
                >
                  <i className="ti ti-ban"></i> Reject
                </button>
                <button
                  className="val-btn warn"
                  type="button"
                  onClick={() => openDecisionModal("revise")}
                >
                  <i className="ti ti-pencil-exclamation"></i> Request Revision
                </button>
                <button
                  className="val-btn ghost"
                  type="button"
                  onClick={handleStartEdit}
                >
                  <i className="ti ti-pencil"></i> Edit
                </button>
                <button
                  className="val-btn success"
                  type="button"
                  onClick={() => openDecisionModal("approve")}
                >
                  <i className="ti ti-circle-check"></i> Approve
                </button>
              </footer>
            ) : (
              <footer className="val-action-bar">
                <span>
                  <i className="ti ti-info-circle"></i>
                  Acquire a review lock to record a decision.
                </span>
                <button
                  className="val-btn success"
                  type="button"
                  disabled={lockBusy}
                  onClick={() => void handleAcquireLock()}
                >
                  <i className="ti ti-lock"></i>
                  {lockBusy ? "Locking..." : "Review"}
                </button>
              </footer>
            )}
          </>
        )}
      </main>

      {renderedModal === "approve" && (
        <DecisionDialog
          icon="ti-circle-check"
          tone="success"
          title="Approve submission?"
          body={
            editedThisSession
              ? "The submission will move to Scheduled and its publish slot will be permanently locked. This will be recorded as an edited approval and the contributor is notified that you made changes."
              : "The submission will move to Scheduled and its publish slot will be permanently locked."
          }
          confirmLabel={decisionBusy ? "Approving..." : "Approve"}
          exiting={modalClosing}
          confirmBusy={decisionBusy}
          onCancel={closeDecisionModal}
          onConfirm={() => void handleApprove()}
        />
      )}

      {renderedModal === "revise" && (
        <DecisionDialog
          icon="ti-pencil-exclamation"
          tone="warn"
          title="Request revision"
          body="Tell the contributor what must change before this can be approved."
          confirmLabel={decisionBusy ? "Sending..." : "Send Revision Request"}
          exiting={modalClosing}
          confirmBusy={decisionBusy}
          onCancel={closeDecisionModal}
          onConfirm={() => void handleRevise()}
        >
          <textarea
            className="val-modal-input"
            value={remarks}
            onChange={(event) => setRemarks(event.target.value)}
            rows={5}
            placeholder="Write at least 10 characters..."
          />
          <small className={remarks.trim().length >= 10 ? "ok" : "err"}>
            {remarks.trim().length} / 10 min
          </small>
        </DecisionDialog>
      )}

      {renderedModal === "reject" && (
        <DecisionDialog
          icon="ti-ban"
          tone="danger"
          title="Reject submission"
          body="Choose the rejection reason that will be recorded in the validation audit log."
          confirmLabel={decisionBusy ? "Rejecting..." : "Reject Submission"}
          exiting={modalClosing}
          confirmBusy={decisionBusy}
          onCancel={closeDecisionModal}
          onConfirm={() => void handleReject()}
        >
          <div className="val-reason-grid">
            {rejectionReasons.map((reason) => (
              <button
                className={reasonCode === reason.code ? "selected" : ""}
                key={reason.code}
                type="button"
                onClick={() => setReasonCode(reason.code)}
              >
                {reason.label}
              </button>
            ))}
          </div>
          <textarea
            className="val-modal-input"
            value={notes}
            onChange={(event) => setNotes(event.target.value)}
            rows={4}
            placeholder={
              reasonCode === "OTHER"
                ? "Required for Other..."
                : "Optional notes..."
            }
          />
        </DecisionDialog>
      )}

      <ResolutionRetryModal
        item={retryItem}
        busy={retryItem ? failureBusy === retryItem.submissionId : false}
        onConfirmWithNewSchedule={(scheduledAt, overrideReason) => {
          if (retryItem) {
            void handleFailureRetryWithNewSchedule(retryItem, scheduledAt, overrideReason)
              .then(() => setRetryItem(null))
              .catch(() => undefined);
          }
        }}
        onClose={() => setRetryItem(null)}
      />

      <ManualPublishWorkflowPanel
        detail={manualPublishDetail}
        loading={manualPublishDetailLoading}
        busy={manualPublishDetail ? failureBusy === manualPublishDetail.submissionId : false}
        onConfirm={(postUrl, notes2) => {
          const failure = failures.find((f) => f.submissionId === manualPublishDetail?.submissionId);
          if (failure) void handleCompleteManual(failure, postUrl, notes2);
        }}
        onCancel={() => {
          const failure = failures.find((f) => f.submissionId === manualPublishDetail?.submissionId);
          if (failure) void handleCancelManual(failure);
        }}
        onClose={closeWorkflowPanel}
      />
    </div>
  );
}

function NoticeBar({
  tone,
  icon,
  text,
}: {
  tone: "info" | "warn" | "danger";
  icon: string;
  text: string;
}) {
  return (
    <div className={`val-notice ${tone}`}>
      <i className={`ti ${icon}`}></i>
      <span>{text}</span>
    </div>
  );
}

function EditDiffView({ diffJson }: { diffJson: string }) {
  const entries = parseEditDiff(diffJson);
  if (entries.length === 0) return null;
  return (
    <div className="val-edit-diff">
      {entries.map(([field, change]) => (
        <div key={field} className="val-edit-diff-row">
          <span className="val-edit-diff-field">{formatAction(field)}</span>
          <span className="val-edit-diff-from">{String(change.from) || "—"}</span>
          <i className="ti ti-arrow-right"></i>
          <span className="val-edit-diff-to">{String(change.to) || "—"}</span>
        </div>
      ))}
    </div>
  );
}

function MediaCarousel({
  mediaAssets,
  mediaIndex,
  onMediaIndexChange,
  watermarkConfig,
  showWatermarkPreview = true,
  onToggleWatermark,
}: {
  mediaAssets: SavedMediaAsset[];
  mediaIndex: number;
  onMediaIndexChange: (index: number) => void;
  watermarkConfig?: WatermarkConfiguration | null;
  showWatermarkPreview?: boolean;
  onToggleWatermark?: () => void;
}) {
  const selectedMedia = mediaAssets[mediaIndex];
  return (
    <section className="val-media-section">
      {watermarkConfig?.enabled && onToggleWatermark && (
        <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: "8px" }}>
          <button
            type="button"
            className={`val-wm-toggle-btn ${showWatermarkPreview ? "active" : ""}`}
            onClick={onToggleWatermark}
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: "6px",
              padding: "5px 12px",
              fontSize: "12px",
              fontWeight: 600,
              borderRadius: "20px",
              border: showWatermarkPreview ? "1px solid #93c5fd" : "1px solid #cbd5e1",
              background: showWatermarkPreview ? "#eff6ff" : "#f8fafc",
              color: showWatermarkPreview ? "#1d4ed8" : "#64748b",
              cursor: "pointer",
              transition: "all 0.15s ease",
            }}
          >
            <i className={`ti ${showWatermarkPreview ? "ti-badge-filled" : "ti-badge"}`} />
            <span>{showWatermarkPreview ? "Watermark Preview: ON" : "Watermark Preview: OFF"}</span>
          </button>
        </div>
      )}
      <div className="val-carousel">
        {selectedMedia ? (
          isImage(selectedMedia.fileType) ? (
            <div style={{ position: "relative", width: "100%", height: "100%", display: "flex", alignItems: "center", justifyContent: "center" }}>
              <img src={selectedMedia.storageUrl} alt={selectedMedia.fileName} />
              {showWatermarkPreview && watermarkConfig?.enabled && (
                <WatermarkOverlay elements={watermarkConfig.elements} />
              )}
            </div>
          ) : (
            <div className="val-video-placeholder">
              <i className="ti ti-player-play-filled"></i>
              <span>{selectedMedia.fileName}</span>
            </div>
          )
        ) : (
          <div className="val-no-media">
            <i className="ti ti-photo-off"></i>
            <span>No media assets attached</span>
          </div>
        )}
        {mediaAssets.length > 1 && (
          <>
            <button
              className="val-carrow left"
              type="button"
              onClick={() =>
                onMediaIndexChange((mediaIndex - 1 + mediaAssets.length) % mediaAssets.length)
              }
            >
              <i className="ti ti-chevron-left"></i>
            </button>
            <button
              className="val-carrow right"
              type="button"
              onClick={() => onMediaIndexChange((mediaIndex + 1) % mediaAssets.length)}
            >
              <i className="ti ti-chevron-right"></i>
            </button>
          </>
        )}
        <div className="val-carousel-meta">
          <span>
            {mediaAssets.length ? `${mediaIndex + 1} / ${mediaAssets.length}` : "0 / 0"}
          </span>
          <b>{selectedMedia?.fileType || "MEDIA"}</b>
        </div>
      </div>
      {mediaAssets.length > 0 && (
        <div className="val-thumbs">
          {mediaAssets.map((asset, index) => (
            <button
              className={index === mediaIndex ? "active" : ""}
              key={asset.id}
              type="button"
              onClick={() => onMediaIndexChange(index)}
              title={asset.fileName}
            >
              {isImage(asset.fileType) ? (
                <img src={asset.storageUrl} alt="" />
              ) : (
                <i className="ti ti-video"></i>
              )}
            </button>
          ))}
        </div>
      )}
    </section>
  );
}

function SubmissionDetailCards({ content }: { content: SubmissionSummary }) {
  return (
    <section className="val-detail-grid">
      <DetailCard icon="ti-calendar-event" label="Event Date">
        {formatDate(content.eventDate)}
      </DetailCard>
      <DetailCard icon="ti-sparkles" label="Tags" full>
        <div className="val-tag-row">
          {content.tags?.length ? (
            content.tags.map((tag) => <span key={tag}>{tag}</span>)
          ) : (
            <em>No tags supplied</em>
          )}
        </div>
      </DetailCard>
      <DetailCard icon="ti-brand-facebook" label="Facebook Caption" full>
        <p className="val-caption">{content.caption || "No caption supplied."}</p>
      </DetailCard>
      {content.description && (
        <DetailCard icon="ti-notes" label="Administrator Notes" full muted>
          {content.description}
        </DetailCard>
      )}
    </section>
  );
}

function DetailCard({
  icon,
  label,
  full,
  muted,
  children,
}: {
  icon: string;
  label: string;
  full?: boolean;
  muted?: boolean;
  children: ReactNode;
}) {
  return (
    <div className={`val-detail-card ${full ? "full" : ""} ${muted ? "muted" : ""}`}>
      <div className="val-detail-label">
        <i className={`ti ${icon}`}></i>
        {label}
      </div>
      <div className="val-detail-value">{children}</div>
    </div>
  );
}

function QueueState({
  icon,
  title,
  subtitle,
}: {
  icon: string;
  title: string;
  subtitle?: string;
}) {
  return (
    <div className="val-queue-state">
      <i className={`ti ${icon}`}></i>
      <strong>{title}</strong>
      {subtitle && <span>{subtitle}</span>}
    </div>
  );
}

function DecisionDialog({
  icon,
  tone,
  title,
  body,
  confirmLabel,
  exiting,
  confirmBusy,
  onCancel,
  onConfirm,
  children,
}: {
  icon: string;
  tone: "success" | "warn" | "danger";
  title: string;
  body: string;
  confirmLabel: string;
  exiting: boolean;
  confirmBusy: boolean;
  onCancel: () => void;
  onConfirm: () => void;
  children?: ReactNode;
}) {
  return createPortal(
    <div
      className={`val-modal-overlay${exiting ? " is-closing" : ""}`}
      onClick={onCancel}
    >
      <div className="val-modal" onClick={(event) => event.stopPropagation()}>
        <div className={`val-modal-icon ${tone}`}>
          <i className={`ti ${icon}`}></i>
        </div>
        <h3>{title}</h3>
        <p>{body}</p>
        {children}
        <div className="val-modal-actions">
          <button type="button" className="ghost" onClick={onCancel}>
            Cancel
          </button>
          <button
            type="button"
            className={tone}
            onClick={onConfirm}
            disabled={confirmBusy}
            aria-busy={confirmBusy}
          >
            {confirmBusy && <i className="ti ti-loader-2 val-spin"></i>}
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>,
    document.body,
  );
}

function parseEditDiff(diffJson: string): Array<[string, { from: unknown; to: unknown }]> {
  try {
    return Object.entries(JSON.parse(diffJson) as Record<string, { from: unknown; to: unknown }>);
  } catch {
    return [];
  }
}

function normalizeStatus(value: string) {
  return value.toLowerCase();
}

function deadlineTone(value?: string) {
  if (!value) return "";
  const hours = (new Date(value).getTime() - Date.now()) / 36e5;
  if (hours <= 6) return "critical";
  if (hours <= 24) return "urgent";
  return "";
}

function isImage(fileType: string) {
  return ["jpeg", "jpg", "png", "webp", "gif", "image"].some((type) =>
    fileType.toLowerCase().includes(type),
  );
}

function shortId(id: string) {
  return `SUB-${id.slice(0, 8).toUpperCase()}`;
}

function logIcon(action: string) {
  if (action.includes("approved")) return "ti-circle-check";
  if (action.includes("edited")) return "ti-pencil";
  if (action.includes("revision")) return "ti-pencil-exclamation";
  if (action.includes("rejected")) return "ti-ban";
  if (action.includes("lock")) return "ti-lock";
  return "ti-history";
}

function formatAction(action: string) {
  return action.replace(/_/g, " ").replace(/\b\w/g, (char) => char.toUpperCase());
}

function formatDate(value?: string) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric",
  }).format(date);
}

function formatTime(value?: string) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return new Intl.DateTimeFormat(undefined, {
    hour: "numeric",
    minute: "2-digit",
  }).format(date);
}

function formatDateTime(value?: string) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat(undefined, {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(date);
}

function readApiError(error: unknown, fallback: string) {
  const err = error as { response?: { data?: { error?: string; message?: string } }; message?: string };
  return err?.response?.data?.error || err?.response?.data?.message || err?.message || fallback;
}

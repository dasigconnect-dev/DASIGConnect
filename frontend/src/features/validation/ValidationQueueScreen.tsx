import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { getSubmission, type SubmissionSummary } from "../../api/submissionApi";
import {
  acquireReviewLock,
  approveSubmission,
  getValidationQueue,
  rejectSubmission,
  releaseReviewLock,
  requestSubmissionRevision,
  type RejectionReasonCode,
  type ReviewLock,
} from "../../api/validationApi";
import { useToast } from "../../context/ToastContext";
import type { User } from "../../types/auth.types";
import {
  useValidationLog,
  useValidationQueue,
} from "./hooks/useValidationQueue";

interface ValidationQueueScreenProps {
  user: User;
}

type QueueFilter = "pending" | "in_review" | "all" | "history";
type SortKey = "deadline" | "submitted";
type DecisionModal = "approve" | "revise" | "reject" | null;
const MODAL_EXIT_MS = 190;
const REVIEWABLE_STATUSES = new Set(["pending", "in_review"]);

const rejectionReasons: Array<{ code: RejectionReasonCode; label: string }> = [
  { code: "INCOMPLETE_CONTENT", label: "Incomplete content" },
  { code: "INAPPROPRIATE_CONTENT", label: "Inappropriate content" },
  { code: "WRONG_FORMAT", label: "Wrong format" },
  { code: "DUPLICATE_EVENT", label: "Duplicate event" },
  { code: "WRONG_INSTITUTION", label: "Wrong institution" },
  { code: "OTHER", label: "Other" },
];

const statusLabel: Record<string, string> = {
  pending: "Pending Approval",
  in_review: "In Review",
  needs_revision: "Needs Revision",
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
  const [historyQueue, setHistoryQueue] = useState<SubmissionSummary[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState("");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [selected, setSelected] = useState<SubmissionSummary | null>(null);
  const [selectedLoading, setSelectedLoading] = useState(false);
  const [activeLock, setActiveLock] = useState<ReviewLock | null>(null);
  const [lockNotice, setLockNotice] = useState("");
  const [filter, setFilter] = useState<QueueFilter>("pending");
  const [sortKey, setSortKey] = useState<SortKey>("deadline");
  const [search, setSearch] = useState("");
  const [mediaIndex, setMediaIndex] = useState(0);
  const [renderedModal, setRenderedModal] = useState<DecisionModal>(null);
  const [modalClosing, setModalClosing] = useState(false);
  const [decisionBusy, setDecisionBusy] = useState(false);
  const [remarks, setRemarks] = useState("");
  const [reasonCode, setReasonCode] =
    useState<RejectionReasonCode>("INCOMPLETE_CONTENT");
  const [notes, setNotes] = useState("");
  const { log, loading: logLoading } = useValidationLog(selectedId);
  const modalExitTimer = useRef<number | null>(null);
  const activeLockRef = useRef<ReviewLock | null>(null);

  const isHistoryMode = filter === "history";
  const queue = isHistoryMode ? historyQueue : activeQueue;
  const loading = isHistoryMode ? historyLoading : activeLoading;
  const error = isHistoryMode ? historyError : activeError;

  const filteredQueue = useMemo(() => {
    const term = search.trim().toLowerCase();
    return queue
      .filter((item) => {
        const status = normalizeStatus(item.status);
        if (filter !== "all" && filter !== "history" && status !== filter) return false;
        if (!term) return true;
        return [
          item.eventTitle,
          item.contributorEmail,
          item.category,
          item.tags?.join(" "),
        ]
          .filter(Boolean)
          .some((value) => value!.toLowerCase().includes(term));
      })
      .sort((a, b) => {
        if (a.fastTrack !== b.fastTrack) return a.fastTrack ? -1 : 1;
        const left =
          sortKey === "deadline" ? a.scheduledAt || "" : a.submittedAt || a.createdAt || "";
        const right =
          sortKey === "deadline" ? b.scheduledAt || "" : b.submittedAt || b.createdAt || "";
        const cmp = left.localeCompare(right);
        return isHistoryMode ? -cmp : cmp;
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

  const mediaAssets = selected?.mediaAssets ?? [];
  const selectedMedia = mediaAssets[mediaIndex];
  const isSelfReview =
    user.role !== "admin" &&
    Boolean(selected?.contributorEmail) &&
    selected?.contributorEmail?.toLowerCase() === user.email.toLowerCase();
  const isTerminalStatus = Boolean(
    selected && !REVIEWABLE_STATUSES.has(normalizeStatus(selected.status ?? "")),
  );
  const canAct = Boolean(selected && activeLock && !isSelfReview && !isTerminalStatus);

  useEffect(() => {
    if (!isHistoryMode) return;
    let active = true;
    queueMicrotask(() => {
      if (!active) return;
      setHistoryLoading(true);
      setHistoryError("");
      getValidationQueue({ history: true })
        .then((res) => { if (active) setHistoryQueue(res.data); })
        .catch((err: unknown) => { if (active) setHistoryError(readApiError(err, "Unable to load submission history.")); })
        .finally(() => { if (active) setHistoryLoading(false); });
    });
    return () => { active = false; };
  }, [isHistoryMode]);

  useEffect(() => {
    if (selectedId || loading || queue.length === 0) return;
    void openSubmission(queue[0]);
  }, [loading, queue, selectedId]);

  useEffect(() => {
    activeLockRef.current = activeLock;
  }, [activeLock]);

  useEffect(() => {
    return () => {
      if (activeLockRef.current) {
        void releaseReviewLock(activeLockRef.current.submissionId);
      }
      if (modalExitTimer.current) window.clearTimeout(modalExitTimer.current);
    };
  }, []);

  function handleFilterChange(next: QueueFilter) {
    if (next === filter) return;
    if (activeLock) {
      void releaseReviewLock(activeLock.submissionId).catch(() => undefined);
      setActiveLock(null);
    }
    setSelected(null);
    setSelectedId(null);
    setLockNotice("");
    setSortKey(next === "history" ? "submitted" : "deadline");
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
    if (selectedId === summary.id && activeLock?.submissionId === summary.id) {
      return;
    }

    setSelectedId(summary.id);
    setSelected(summary);
    setSelectedLoading(true);
    setMediaIndex(0);
    setLockNotice("");

    if (activeLock && activeLock.submissionId !== summary.id) {
      await releaseReviewLock(activeLock.submissionId).catch(() => undefined);
      setActiveLock(null);
    }

    try {
      const detail = await getSubmission(summary.id);
      setSelected(detail.data);

      if (!REVIEWABLE_STATUSES.has(normalizeStatus(summary.status))) {
        setActiveLock(null);
        setLockNotice("");
        return;
      }

      if (user.role !== "admin" && summary.contributorEmail?.toLowerCase() === user.email.toLowerCase()) {
        setActiveLock(null);
        setLockNotice("Self-validation blocked. This submission must be reviewed by another Validator.");
        return;
      }

      const lock = await acquireReviewLock(summary.id);
      setActiveLock(lock.data);
      setLockNotice("");
    } catch (err: unknown) {
      const message = readApiError(err, "Unable to open this submission.");
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 409) {
        setActiveLock(null);
        setLockNotice(message);
      } else {
        toast.error(message);
      }
    } finally {
      setSelectedLoading(false);
    }
  }

  async function handleApprove() {
    if (!selected) return;
    setDecisionBusy(true);
    try {
      await approveSubmission(selected.id);
      toast.success("Submission approved and scheduled.");
      closeDecisionModal();
      setActiveLock(null);
      setSelected(null);
      setSelectedId(null);
      await refresh();
    } catch (err: unknown) {
      toast.error(readApiError(err, "Approval failed."));
    } finally {
      setDecisionBusy(false);
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
      setActiveLock(null);
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
      setActiveLock(null);
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
              <div className="val-kicker">Validation Workspace</div>
              <h1>Review Queue</h1>
            </div>
            <span className="val-count">{filteredQueue.length}</span>
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
              All
            </button>
            <button
              className={filter === "history" ? "active" : ""}
              type="button"
              onClick={() => handleFilterChange("history")}
            >
              History
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

        <div className="val-sort-row">
          <span>Sort</span>
          <button
            className={sortKey === "deadline" ? "active" : ""}
            type="button"
            onClick={() => setSortKey("deadline")}
          >
            <i className="ti ti-calendar-due"></i> Deadline
          </button>
          <button
            className={sortKey === "submitted" ? "active" : ""}
            type="button"
            onClick={() => setSortKey("submitted")}
          >
            <i className="ti ti-send"></i> Submitted
          </button>
        </div>

        <div className="val-queue-list">
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
                    {item.fastTrack ? "Fast-Track" : statusLabel[normalizeStatus(item.status)] || item.status}
                  </span>
                </div>
                <div className="val-qi-meta">
                  <span>{item.contributorEmail || "Unknown contributor"}</span>
                  <i></i>
                  <span>{formatDate(item.submittedAt || item.createdAt || item.eventDate)}</span>
                </div>
                <div className="val-qi-bottom">
                  <span className="val-deadline">
                    <i className="ti ti-clock"></i>
                    {item.fastTrack ? "Urgent approval" : item.scheduledAt ? formatDateTime(item.scheduledAt) : "No slot"}
                  </span>
                  <span className="val-media-count">
                    <i className="ti ti-photo"></i> {item.mediaCount ?? 0}
                  </span>
                </div>
              </button>
            ))}
        </div>
      </aside>

      <main className="val-review-panel">
        {!selected && !selectedLoading && (
          <div className="val-empty">
            <i className="ti ti-clipboard-check"></i>
            <h2>Select a submission</h2>
            <p>Open an item from the queue to acquire a review lock and inspect its content.</p>
          </div>
        )}

        {selected && (
          <>
            {isSelfReview && (
              <NoticeBar
                tone="danger"
                icon="ti-alert-triangle"
                text="Self-validation blocked. This submission is routed to another reviewer."
              />
            )}
            {lockNotice && !isSelfReview && (
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
                    <span className="val-inst">{user.inst}</span>
                    <span className="val-sub-id">{shortId(selected.id)}</span>
                    {selected.fastTrack && <span className="val-urgent-badge">Fast-Track</span>}
                  </div>
                  <h2>{selected.eventTitle || "Untitled submission"}</h2>
                  <p>
                    <i className="ti ti-user"></i>
                    Submitted by <strong>{selected.contributorEmail}</strong>
                  </p>
                </div>
                <div className="val-slot-card">
                  <span>Publish Slot</span>
                  <strong>
                    {selected.fastTrack
                      ? "Fast-Track"
                      : selected.scheduledAt
                      ? formatDate(selected.scheduledAt)
                      : "Unscheduled"}
                  </strong>
                  <small>
                    {selected.fastTrack
                      ? "Urgent approval, no scheduled slot"
                      : selected.scheduledAt
                      ? formatTime(selected.scheduledAt)
                      : "No preferred time"}
                  </small>
                </div>
              </header>

              <section className="val-media-section">
                <div className="val-carousel">
                  {selectedMedia ? (
                    isImage(selectedMedia.fileType) ? (
                      <img src={selectedMedia.storageUrl} alt={selectedMedia.fileName} />
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
                          setMediaIndex(
                            (mediaIndex - 1 + mediaAssets.length) %
                              mediaAssets.length,
                          )
                        }
                      >
                        <i className="ti ti-chevron-left"></i>
                      </button>
                      <button
                        className="val-carrow right"
                        type="button"
                        onClick={() =>
                          setMediaIndex((mediaIndex + 1) % mediaAssets.length)
                        }
                      >
                        <i className="ti ti-chevron-right"></i>
                      </button>
                    </>
                  )}
                  <div className="val-carousel-meta">
                    <span>
                      {mediaAssets.length
                        ? `${mediaIndex + 1} / ${mediaAssets.length}`
                        : "0 / 0"}
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
                        onClick={() => setMediaIndex(index)}
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

              <section className="val-detail-grid">
                <DetailCard icon="ti-calendar-event" label="Event Date">
                  {formatDate(selected.eventDate)}
                </DetailCard>
                <DetailCard icon="ti-list" label="Category">
                  {selected.category || "Uncategorized"}
                </DetailCard>
                <DetailCard icon="ti-sparkles" label="Tags" full>
                  <div className="val-tag-row">
                    {selected.tags?.length ? (
                      selected.tags.map((tag) => <span key={tag}>{tag}</span>)
                    ) : (
                      <em>No tags supplied</em>
                    )}
                  </div>
                </DetailCard>
                <DetailCard icon="ti-brand-facebook" label="Facebook Caption" full>
                  <p className="val-caption">
                    {selected.caption || "No caption supplied."}
                  </p>
                </DetailCard>
                {selected.description && (
                  <DetailCard icon="ti-notes" label="Validator Notes" full muted>
                    {selected.description}
                  </DetailCard>
                )}
              </section>

              <section className="val-log-card">
                <div className="val-section-head">
                  <div>
                    <i className="ti ti-history"></i>
                    Validation History
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
                        <strong>{formatAction(entry.action)}</strong>
                        <span>
                          {entry.validatorEmail} · {formatDateTime(entry.createdAt)}
                        </span>
                        {entry.remarks && <p>{entry.remarks}</p>}
                        {entry.rejectionReason && <p>{entry.rejectionReason}</p>}
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
            ) : (
              <footer className="val-action-bar">
                <span>
                  <i className="ti ti-info-circle"></i>
                  {canAct
                    ? "Record your decision. Actions are permanent and logged."
                    : "Actions unavailable until you hold the review lock."}
                </span>
                <button
                  className="val-btn danger"
                  type="button"
                  disabled={!canAct}
                  onClick={() => openDecisionModal("reject")}
                >
                  <i className="ti ti-ban"></i> Reject
                </button>
                <button
                  className="val-btn warn"
                  type="button"
                  disabled={!canAct}
                  onClick={() => openDecisionModal("revise")}
                >
                  <i className="ti ti-pencil-exclamation"></i> Request Revision
                </button>
                <button
                  className="val-btn success"
                  type="button"
                  disabled={!canAct}
                  onClick={() => openDecisionModal("approve")}
                >
                  <i className="ti ti-circle-check"></i> Approve
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
          body="The submission will move to Scheduled and its publish slot will be permanently locked."
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

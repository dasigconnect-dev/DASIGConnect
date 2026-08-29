import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import {
  getSubmission,
  validateGuardRails,
  type GuardRailResult,
  type SavedMediaAsset,
  type SubmissionSummary,
} from "../../api/submissionApi";
import {
  acquireReviewLock,
  approveSubmission,
  attachValidationLibraryAsset,
  detachValidationAsset,
  editSubmission,
  getReviewLockStatus,
  getValidationQueue,
  rejectSubmission,
  releaseReviewLock,
  reorderValidationMedia,
  requestSubmissionRevision,
  uploadValidationMedia,
  type RejectionReasonCode,
  type ReviewLock,
  type ValidationLog,
} from "../../api/validationApi";
import type { SubmissionMediaItem } from "../../types/media";
import FancyTextTool, { type FancyTextSelection } from "../submission/components/FancyTextTool";
import ReviewLibraryPickerModal from "./ReviewLibraryPickerModal";
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
import "../../styles/dasig-loader.css";
import "../../styles/resolution.css";

interface ValidationQueueScreenProps {
  user: User;
}

type QueueFilter = "pending" | "in_review" | "all" | "failed";
type SortKey = "publish_slot" | "submitted";
type DecisionModal = "approve" | "revise" | "reject" | null;
const MODAL_EXIT_MS = 190;
const REVIEWABLE_STATUSES = new Set(["pending", "in_review"]);

const VIDEO_EXT = new Set(["mp4", "mov", "webm", "avi", "mkv"]);

interface EditMediaItem {
  key: string;
  assetId?: string;
  file?: File;
  previewUrl: string;
  fileName: string;
  isImage: boolean;
  caption: string;
  skipWatermark: boolean;
}

interface EditFormState {
  eventTitle: string;
  eventDate: string;
  caption: string;
  description: string;
  tags: string;
  scheduledDate: string;
  scheduledTime: string;
  media: EditMediaItem[];
  removedAssetIds: string[];
}

function emptyEditForm(): EditFormState {
  return {
    eventTitle: "",
    eventDate: "",
    caption: "",
    description: "",
    tags: "",
    scheduledDate: "",
    scheduledTime: "",
    media: [],
    removedAssetIds: [],
  };
}

function savedAssetToMediaItem(asset: SavedMediaAsset): EditMediaItem {
  return {
    key: `saved-${asset.id}`,
    assetId: asset.id,
    previewUrl: asset.storageUrl,
    fileName: asset.fileName,
    isImage: !VIDEO_EXT.has(asset.fileType?.toLowerCase() ?? ""),
    caption: asset.caption ?? "",
    skipWatermark: Boolean(asset.skipWatermark),
  };
}

function toEditForm(summary: SubmissionSummary): EditFormState {
  const scheduled = summary.scheduledAt ? new Date(summary.scheduledAt) : null;
  return {
    eventTitle: summary.eventTitle || "",
    eventDate: summary.eventDate ? summary.eventDate.slice(0, 10) : "",
    caption: summary.caption || "",
    description: summary.description || "",
    tags: summary.tags?.join(", ") || "",
    scheduledDate: scheduled ? scheduled.toISOString().slice(0, 10) : "",
    scheduledTime: scheduled
      ? `${String(scheduled.getHours()).padStart(2, "0")}:${String(scheduled.getMinutes()).padStart(2, "0")}`
      : "",
    media: (summary.mediaAssets ?? []).map(savedAssetToMediaItem),
    removedAssetIds: [],
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
  const [isPanelCollapsed, setIsPanelCollapsed] = useState(false);
  const [filter, setFilter] = useState<QueueFilter>("all");
  const [sortKey, setSortKey] = useState<SortKey>("submitted");
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
  const [captionSelection, setCaptionSelection] = useState<FancyTextSelection>({ start: 0, end: 0 });
  const [guardRails, setGuardRails] = useState<GuardRailResult | null>(null);
  const [guardRailsLoading, setGuardRailsLoading] = useState(false);
  const [editTab, setEditTab] = useState<"details" | "media" | "schedule">("details");
  const [overrideReason, setOverrideReason] = useState("");
  const [libraryPickerOpen, setLibraryPickerOpen] = useState(false);
  const editCaptionRef = useRef<HTMLTextAreaElement | null>(null);
  const editFileInputRef = useRef<HTMLInputElement | null>(null);
  const { log, loading: logLoading, refresh: refreshLog } = useValidationLog(selectedId);
  const modalExitTimer = useRef<number | null>(null);
  const openRequestRef = useRef(0);

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
  const { log: failureLog, loading: failureLogLoading } = useValidationLog(selectedFailureId);

  const isAllMode = filter === "all";
  const isFailedMode = filter === "failed";
  const filteredFailures = useMemo(() => {
    const term = search.trim().toLowerCase();
    if (!term) return failures;
    return failures.filter((item) =>
      [item.eventTitle, item.institutionName, item.lastError]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(term)),
    );
  }, [failures, search]);
  const selectedFailure = selectedFailureId
    ? filteredFailures.find((f) => f.submissionId === selectedFailureId) ?? null
    : null;
  const combinedQueue = useMemo(() => {
    const submissions = new Map<string, SubmissionSummary>();
    [...activeQueue, ...allQueue].forEach((item) => submissions.set(item.id, item));
    return Array.from(submissions.values());
  }, [activeQueue, allQueue]);
  const queue = isAllMode ? combinedQueue : activeQueue;
  const loading = isAllMode ? activeLoading || allLoading : activeLoading;
  const error = isAllMode ? activeError || allError : activeError;

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
        // Live Event / Fast-Track submissions never reserve a slot — once
        // published their publish time IS their slot, so fall back to it.
        const left =
          sortKey === "publish_slot"
            ? a.scheduledAt || a.publishedAt || ""
            : a.submittedAt || a.createdAt || "";
        const right =
          sortKey === "publish_slot"
            ? b.scheduledAt || b.publishedAt || ""
            : b.submittedAt || b.createdAt || "";
        const cmp = left.localeCompare(right);
        return isAllMode ? -cmp : cmp;
      });
  }, [filter, isAllMode, queue, search, sortKey]);

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
  const [showHistoryModal, setShowHistoryModal] = useState<boolean>(false);

  useEffect(() => {
    void getWatermarkConfiguration()
      .then((res) => setWatermarkConfig(res.data))
      .catch(() => undefined);
  }, []);

  const fetchAllQueue = useCallback(() => {
    setAllLoading(true);
    setAllError("");
    return getValidationQueue({ history: true })
      .then((res) => {
        setAllQueue(Array.isArray(res.data) ? res.data : []);
      })
      .catch((err: unknown) => {
        setAllError(readApiError(err, "Unable to load all submissions."));
      })
      .finally(() => setAllLoading(false));
  }, []);

  useEffect(() => {
    let active = true;
    queueMicrotask(() => {
      if (active) void fetchAllQueue();
    });
    return () => {
      active = false;
    };
  }, [fetchAllQueue]);



  useEffect(() => {
    if (!isFailedMode || failuresLoading) return;
    const selectionIsVisible = selectedFailureId
      ? filteredFailures.some((item) => item.submissionId === selectedFailureId)
      : false;
    if (selectionIsVisible) return;
    queueMicrotask(() => setSelectedFailureId(filteredFailures[0]?.submissionId ?? null));
  }, [isFailedMode, failuresLoading, filteredFailures, selectedFailureId]);

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
  }, [selectedFailureId, toast]);

  useEffect(() => {
    return () => {
      if (modalExitTimer.current) window.clearTimeout(modalExitTimer.current);
    };
  }, []);

  function handleFilterChange(next: QueueFilter) {
    if (next === filter) return;
    openRequestRef.current += 1;
    setSortKey(next === "all" ? "submitted" : "publish_slot");
    setFilter(next);
    setSelectedId(null);
    setSelected(null);
    setSelectedLoading(false);
    setLockNotice("");
    setEditMode(false);
    setShowHistoryModal(false);
    if (next !== "failed") {
      setSelectedFailureId(null);
      setFailureContent(null);
    }
    if (next === "all") {
      void fetchAllQueue();
    }
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

  const openSubmission = useCallback(async (summary: SubmissionSummary) => {
    if (selectedId === summary.id) {
      return;
    }

    const requestId = ++openRequestRef.current;

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
      if (requestId !== openRequestRef.current) return;
      setSelected(detail.data);

      // Restore lock UI state (e.g. after a page refresh) without acquiring
      // anything — a read-only check against the backend's current lock.
      if (REVIEWABLE_STATUSES.has(normalizeStatus(detail.data.status))) {
        const lockStatus = await getReviewLockStatus(summary.id);
        if (requestId !== openRequestRef.current) return;
        const lock = lockStatus.data;
        if (lock) {
          if (lock.lockedByEmail.toLowerCase() === user.email.toLowerCase()) {
            setLocks((prev) => ({ ...prev, [summary.id]: lock }));
          } else {
            setLockNotice(`This submission is currently being reviewed by ${lock.lockedByEmail}.`);
          }
        }
      }
    } catch (err: unknown) {
      if (requestId !== openRequestRef.current) return;
      toast.error(readApiError(err, "Unable to open this submission."));
    } finally {
      if (requestId === openRequestRef.current) setSelectedLoading(false);
    }
  }, [selectedId, toast, user.email]);

  useEffect(() => {
    if (isFailedMode || loading) return;
    const selectedIsVisible = selectedId
      ? filteredQueue.some((item) => item.id === selectedId)
      : false;
    if (selectedIsVisible) return;

    if (filteredQueue.length > 0) {
      const firstSubmission = filteredQueue[0];
      queueMicrotask(() => void openSubmission(firstSubmission));
      return;
    }

    if (selectedId || selected) {
      const requestId = ++openRequestRef.current;
      queueMicrotask(() => {
        if (requestId !== openRequestRef.current) return;
        setSelectedId(null);
        setSelected(null);
        setSelectedLoading(false);
      });
    }
  }, [isFailedMode, loading, filteredQueue, selectedId, selected, openSubmission]);

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

  // Keep the review lock alive while a submission panel is open. The backend TTL
  // is 15 min and ReviewLockCleanupJob reverts in_review → pending once it lapses,
  // so a long edit session would otherwise silently lose the lock (and the row
  // drops off the Review tab). Ping every 5 min while the tab is visible; the
  // backend renews the holder's TTL idempotently.
  useEffect(() => {
    if (!selected || !activeLock) return;
    const submissionId = selected.id;
    const renew = async () => {
      if (document.visibilityState !== "visible") return;
      try {
        const res = await acquireReviewLock(submissionId);
        setLockFor(submissionId, res.data);
      } catch (err: unknown) {
        const status = (err as { response?: { status?: number } })?.response?.status;
        if (status === 403 || status === 409) {
          clearLockFor(submissionId);
          setLockNotice(
            "Your review lock was lost. Re-acquire it from the action bar to continue.",
          );
        }
        // transient errors are ignored — the next tick retries
      }
    };
    const timer = window.setInterval(renew, 5 * 60 * 1000);
    const onVisibility = () => {
      if (document.visibilityState === "visible") void renew();
    };
    document.addEventListener("visibilitychange", onVisibility);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener("visibilitychange", onVisibility);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selected?.id, Boolean(activeLock)]);

  function handleLockLost(submissionId: string) {
    clearLockFor(submissionId);
    setEditMode(false);
    setLockNotice(
      "Your review lock is no longer held — the submission has returned to the queue " +
        "(or was claimed by another reviewer). Re-open it to continue.",
    );
    void refresh();
    void fetchAllQueue();
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
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 403 || status === 409) {
        closeDecisionModal();
        handleLockLost(selected.id);
        toast.error("Review lock expired before the approval could be recorded.");
      } else {
        toast.error(readApiError(err, "Approval failed."));
      }
    } finally {
      setDecisionBusy(false);
    }
  }

  function handleStartEdit() {
    if (!selected) return;
    setEditForm(toEditForm(selected));
    setGuardRails(null);
    setCaptionSelection({ start: 0, end: 0 });
    setEditTab("details");
    setOverrideReason("");
    setIsPanelCollapsed(true);
    setEditMode(true);
  }

  function handleCancelEdit() {
    setEditMode(false);
    setGuardRails(null);
  }

  const editScheduledAtIso = useMemo(() => {
    if (!editForm.scheduledDate || !editForm.scheduledTime) return "";
    const d = new Date(`${editForm.scheduledDate}T${editForm.scheduledTime}`);
    return Number.isNaN(d.getTime()) ? "" : d.toISOString();
  }, [editForm.scheduledDate, editForm.scheduledTime]);

  const originalScheduledIso = selected?.scheduledAt
    ? new Date(selected.scheduledAt).toISOString()
    : "";
  const scheduleChanged = editScheduledAtIso !== originalScheduledIso;

  useEffect(() => {
    if (!editMode || !scheduleChanged || !editScheduledAtIso || !selected) {
      setGuardRails(null);
      return;
    }
    const controller = new AbortController();
    setGuardRailsLoading(true);
    validateGuardRails(editScheduledAtIso, selected.institutionId)
      .then((res) => setGuardRails(res.data))
      .catch(() => setGuardRails(null))
      .finally(() => setGuardRailsLoading(false));
    return () => controller.abort();
  }, [editMode, scheduleChanged, editScheduledAtIso, selected]);

  const hardBlocked = (guardRails?.hardBlocks?.length ?? 0) > 0;
  const canSaveEdit =
    !editSaving && (!hardBlocked || overrideReason.trim().length >= 10);

  function updateMedia(key: string, patch: Partial<EditMediaItem>) {
    setEditForm((f) => ({
      ...f,
      media: f.media.map((m) => (m.key === key ? { ...m, ...patch } : m)),
    }));
  }

  function moveMedia(index: number, dir: -1 | 1) {
    setEditForm((f) => {
      const next = [...f.media];
      const target = index + dir;
      if (target < 0 || target >= next.length) return f;
      [next[index], next[target]] = [next[target], next[index]];
      return { ...f, media: next };
    });
  }

  function removeMedia(item: EditMediaItem) {
    setEditForm((f) => ({
      ...f,
      media: f.media.filter((m) => m.key !== item.key),
      removedAssetIds: item.assetId ? [...f.removedAssetIds, item.assetId] : f.removedAssetIds,
    }));
  }

  function addMediaFiles(files: FileList | null) {
    if (!files?.length) return;
    const items: EditMediaItem[] = Array.from(files).map((file) => {
      const ext = file.name.split(".").pop()?.toLowerCase() ?? "";
      return {
        key: `new-${crypto.randomUUID()}`,
        file,
        previewUrl: URL.createObjectURL(file),
        fileName: file.name,
        isImage: !VIDEO_EXT.has(ext),
        caption: "",
        skipWatermark: false,
      };
    });
    setEditForm((f) => ({ ...f, media: [...f.media, ...items] }));
  }

  function addLibraryAssets(items: SubmissionMediaItem[]) {
    setEditForm((f) => {
      const have = new Set(f.media.map((m) => m.assetId).filter(Boolean));
      const next: EditMediaItem[] = items
        .filter((it): it is SubmissionMediaItem & { assetId: string } =>
          Boolean(it.assetId) && !have.has(it.assetId),
        )
        .map((it) => ({
          key: `lib-${it.assetId}`,
          assetId: it.assetId,
          previewUrl: it.previewUrl,
          fileName: it.fileName,
          isImage: it.mediaType !== "video",
          caption: "",
          skipWatermark: false,
        }));
      return { ...f, media: [...f.media, ...next] };
    });
  }

  async function handleSaveEdit() {
    if (!selected || !canSaveEdit) return;
    setEditSaving(true);
    try {
      const id = selected.id;

      // 1. upload any new device files
      const newFiles = editForm.media.filter((m) => m.file).map((m) => m.file as File);
      if (newFiles.length > 0) await uploadValidationMedia(id, newFiles);

      // 2. attach staged library picks that aren't on the submission yet
      const attached = new Set((selected.mediaAssets ?? []).map((a) => a.id));
      for (const item of editForm.media) {
        if (item.assetId && !attached.has(item.assetId)) {
          await attachValidationLibraryAsset(id, item.assetId).catch(() => undefined);
        }
      }

      // 3. detach removed assets
      for (const assetId of editForm.removedAssetIds) {
        await detachValidationAsset(id, assetId).catch(() => undefined);
      }

      // 4. reorder + per-item caption / skip-watermark. Re-read to learn the
      //    server-assigned ids for freshly uploaded files.
      const afterMedia = (await getSubmission(id)).data.mediaAssets ?? [];
      const orderedIds: string[] = [];
      const captions: Record<string, string> = {};
      const skips: Record<string, boolean> = {};
      const used = new Set<string>();
      for (const item of editForm.media) {
        let match: SavedMediaAsset | undefined;
        if (item.assetId) {
          match = afterMedia.find((a) => a.id === item.assetId);
        } else {
          match = afterMedia.find((a) => !used.has(a.id) && a.fileName === item.fileName);
        }
        if (!match) continue;
        used.add(match.id);
        orderedIds.push(match.id);
        if (item.caption.trim()) captions[match.id] = item.caption.trim();
        if (item.isImage && item.skipWatermark) skips[match.id] = true;
      }
      if (orderedIds.length === afterMedia.length && afterMedia.length > 0) {
        await reorderValidationMedia(id, orderedIds, captions, skips);
      }

      // 4. scalar fields + reschedule
      await editSubmission(id, {
        eventTitle: editForm.eventTitle,
        eventDate: editForm.eventDate || undefined,
        caption: editForm.caption,
        description: hardBlocked && overrideReason.trim() ? overrideReason.trim() : undefined,
        tags: editForm.tags
          ? editForm.tags.split(",").map((t) => t.trim()).filter(Boolean)
          : undefined,
        scheduledAt: scheduleChanged && editScheduledAtIso ? editScheduledAtIso : undefined,
      });

      // A9: stays IN_REVIEW — refresh content, keep panel + lock open.
      const detail = await getSubmission(id);
      setSelected(detail.data);
      setEditMode(false);
      setGuardRails(null);
      setEditedThisSession(true);
      await refreshLog();
      toast.success("Changes saved — choose a terminal action.");
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 403 || status === 409) {
        handleLockLost(selected.id);
        toast.error("Review lock expired before your changes could be saved.");
      } else {
        toast.error(readApiError(err, "Saving the edit failed."));
      }
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
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 403 || status === 409) {
        closeDecisionModal();
        handleLockLost(selected.id);
        toast.error("Review lock expired before the revision request could be sent.");
      } else {
        toast.error(readApiError(err, "Revision request failed."));
      }
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
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 403 || status === 409) {
        closeDecisionModal();
        handleLockLost(selected.id);
        toast.error("Review lock expired before the rejection could be recorded.");
      } else {
        toast.error(readApiError(err, "Rejection failed."));
      }
    } finally {
      setDecisionBusy(false);
    }
  }

  return (
    <div className={`val-page ${isPanelCollapsed ? "is-queue-collapsed" : ""}`}>
      <aside className="val-queue-panel">
        <div className="val-queue-header">
          <div className="val-title-row">
            <div>
              <span className="val-kicker">Content operations</span>
              <h1>Review Queue</h1>
              <p>Review, refine, and release network content.</p>
            </div>
            <button
              type="button"
              className="val-collapse-btn"
              onClick={() => setIsPanelCollapsed(true)}
              title="Collapse queue panel (<<)"
              aria-label="Collapse queue list"
            >
              <i className="ti ti-chevrons-left" />
            </button>
          </div>

          <div className="val-tabs" role="tablist" aria-label="Queue filters">
            <button
              className={filter === "all" ? "active" : ""}
              type="button"
              role="tab"
              aria-selected={filter === "all"}
              onClick={() => handleFilterChange("all")}
            >
              All <span>{combinedQueue.length}</span>
            </button>
            <button
              className={filter === "pending" ? "active" : ""}
              type="button"
              role="tab"
              aria-selected={filter === "pending"}
              onClick={() => handleFilterChange("pending")}
            >
              Pending <span>{pendingCount}</span>
            </button>
            <button
              className={filter === "in_review" ? "active" : ""}
              type="button"
              role="tab"
              aria-selected={filter === "in_review"}
              onClick={() => handleFilterChange("in_review")}
            >
              Review <span>{reviewCount}</span>
            </button>
            <button
              className={filter === "failed" ? "active" : ""}
              type="button"
              role="tab"
              aria-selected={filter === "failed"}
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
              aria-label="Search review queue"
            />
            {search && (
              <button
                type="button"
                className="val-search-clear"
                onClick={() => setSearch("")}
                aria-label="Clear queue search"
              >
                <i className="ti ti-x" />
              </button>
            )}
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
              {!failuresLoading && !failuresError && filteredFailures.length === 0 && (
                <QueueState
                  icon={failures.length === 0 ? "ti-circle-check" : "ti-search-off"}
                  title={failures.length === 0 ? "No failed publications" : "No matching failures"}
                  subtitle={
                    failures.length === 0
                      ? "Automated publish failures needing manual recovery will appear here."
                      : "Try a different title, institution, or error term."
                  }
                />
              )}
              {!failuresLoading &&
                !failuresError &&
                filteredFailures.map((item) => (
                  <button
                    className={`val-queue-item ${item.submissionId === selectedFailureId ? "active" : ""}`}
                    key={item.submissionId}
                    type="button"
                    onClick={() => setSelectedFailureId(item.submissionId)}
                    title={`${item.eventTitle || "Untitled submission"} • ${item.institutionName || "Unknown institution"}`}
                  >
                    <div className="val-qi-head">
                      <strong className="val-qi-title" title={item.eventTitle || "Untitled submission"}>
                        {item.eventTitle || "Untitled submission"}
                      </strong>
                      <span className={`val-status ${normalizeStatus(item.status)}`}>
                        {item.status === "missed_review"
                          ? "Missed Review"
                          : item.manualPublishInProgress
                            ? "Manual Session"
                            : statusLabel[normalizeStatus(item.status)] || "Publish Failed"}
                      </span>
                    </div>

                    <div className="val-qi-institution">
                      <i className="ti ti-building" aria-hidden="true" />
                      <span title={item.institutionName || "Unknown institution"}>
                        {item.institutionName || "Unknown institution"}
                      </span>
                    </div>

                    <div className="val-qi-bottom">
                      <div className="val-qi-bottom-left">
                        <span className="val-deadline">
                          <i className="ti ti-clock"></i>
                          <span>{item.scheduledAt ? formatDateTime(item.scheduledAt) : "No slot"}</span>
                        </span>
                        <span className="val-qi-date">
                          {item.retryCount} attempt{item.retryCount === 1 ? "" : "s"}
                        </span>
                      </div>

                      {item.lastAttemptAt && (
                        <span className="val-media-count" title={`Last attempt: ${formatDate(item.lastAttemptAt)}`}>
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
                    title={`${item.eventTitle || "Untitled submission"} • ${item.institutionName || "Unknown institution"}`}
                  >
                    <div className="val-qi-head">
                      <strong className="val-qi-title" title={item.eventTitle || "Untitled submission"}>
                        {item.eventTitle || "Untitled submission"}
                      </strong>
                      <span className={`val-status ${normalizeStatus(item.status)}`}>
                        {statusLabel[normalizeStatus(item.status)] || item.status}
                      </span>
                    </div>

                    <div className="val-qi-institution">
                      <i className="ti ti-building" aria-hidden="true" />
                      <span title={item.institutionName || "Unknown institution"}>
                        {item.institutionName || "Unknown institution"}
                      </span>
                    </div>

                    <div className="val-qi-bottom">
                      <div className="val-qi-bottom-left">
                        {item.fastTrack ? (
                          <span className="val-deadline val-live">
                            <i className="ti ti-broadcast"></i>
                            <span>{item.publishedAt ? `Live · ${formatDateTime(item.publishedAt)}` : "Live Event"}</span>
                          </span>
                        ) : (
                          <span className="val-deadline">
                            <i className="ti ti-clock"></i>
                            <span>
                              {item.scheduledAt
                                ? formatDateTime(item.scheduledAt)
                                : item.publishedAt
                                  ? formatDateTime(item.publishedAt)
                                  : "No slot"}
                            </span>
                          </span>
                        )}
                        <span className="val-qi-date">
                          {formatDate(item.submittedAt || item.createdAt || item.eventDate)}
                        </span>
                      </div>

                      <span className="val-media-count" title={`${item.mediaCount ?? 0} media files`}>
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
        {isPanelCollapsed && (
          <button
            type="button"
            className="val-expand-btn"
            onClick={() => setIsPanelCollapsed(false)}
            title="Expand queue panel (>>)"
            aria-label="Expand queue list"
          >
            <i className="ti ti-chevrons-right" />
            <span>Show Queue</span>
          </button>
        )}
        {isFailedMode && !selectedFailure && (
          <div className="val-empty">
            <i className="ti ti-mood-sad"></i>
            <h2>{filteredFailures.length === 0 ? "No matching failed publications" : "Select a failed submission"}</h2>
            <p>
              {filteredFailures.length === 0
                ? failures.length === 0
                  ? "Automated publish failures needing manual recovery will appear here."
                  : "Clear or adjust your search to see failed publications."
                : "Open an item from the list to retry it or fall back to manual publishing."}
            </p>
          </div>
        )}

        {isFailedMode && selectedFailure && (
          <>
            <div className="val-scroll">
              {selectedFailure.lastManualPublishAbandonedAt && (
                <NoticeBar
                  tone="warn"
                  icon="ti-alert-triangle"
                  text={`A manual publish session was abandoned on ${formatDateTime(selectedFailure.lastManualPublishAbandonedAt)}.`}
                />
              )}

              {failureContentLoading ? (
                <PanelContentLoader text="Loading submission details" />
              ) : failureContent ? (
                <>
                  <FacebookPostPreviewCard
                    submission={failureContent}
                    editMode={false}
                    editForm={emptyEditForm()}
                    mediaAssets={failureContent.mediaAssets ?? []}
                    mediaIndex={failureMediaIndex}
                    onMediaIndexChange={setFailureMediaIndex}
                    watermarkConfig={watermarkConfig}
                    showWatermarkPreview={showWatermarkPreview}
                    onToggleWatermark={() => setShowWatermarkPreview((prev) => !prev)}
                    onOpenHistory={() => setShowHistoryModal(true)}
                  />

                  <section className="val-detail-grid" style={{ width: "100%", maxWidth: "620px" }}>
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
                </>
              ) : null}
            </div>

            {showHistoryModal && failureContent && (
              <ValidationHistoryModal
                submission={failureContent}
                log={failureLog}
                loading={failureLogLoading}
                isTerminalStatus={false}
                onClose={() => setShowHistoryModal(false)}
              />
            )}

            <footer className="val-action-bar">
              <div className="val-action-status">
                <span className="val-action-hint">
                  <i className="ti ti-info-circle" />
                  <span>
                    {selectedFailure.status === "missed_review"
                      ? "This submission missed its review window. Assign a new schedule to send it back to the approval queue."
                      : selectedFailure.manualPublishInProgress
                        ? "A manual publish session is already open for this submission."
                        : "Retry automatically, or fall back to manual publishing."}
                  </span>
                </span>
              </div>
              <div className="val-action-group">
                {selectedFailure.status === "missed_review" ? (
                  <button
                    className="val-btn val-btn-primary"
                    type="button"
                    disabled={failureBusy === selectedFailure.submissionId}
                    onClick={() => setRetryItem(selectedFailure)}
                  >
                    <i className="ti ti-calendar-plus" />
                    <span>Retry with New Schedule</span>
                  </button>
                ) : selectedFailure.manualPublishInProgress ? (
                  <>
                    <button
                      className="val-btn val-btn-danger-outline"
                      type="button"
                      disabled={failureBusy === selectedFailure.submissionId}
                      onClick={() => void handleCancelManual(selectedFailure)}
                    >
                      <i className="ti ti-x" />
                      <span>Cancel Manual Session</span>
                    </button>
                    <button
                      className="val-btn val-btn-primary"
                      type="button"
                      onClick={() => openWorkflowPanel(selectedFailure)}
                    >
                      <i className="ti ti-user-check" />
                      <span>Continue Manual Publish</span>
                    </button>
                  </>
                ) : (
                  <>
                    <button
                      className="val-btn val-btn-secondary"
                      type="button"
                      disabled={failureBusy === selectedFailure.submissionId}
                      onClick={() => setRetryItem(selectedFailure)}
                    >
                      <i className="ti ti-refresh" />
                      <span>Retry</span>
                    </button>
                    <button
                      className="val-btn val-btn-primary"
                      type="button"
                      disabled={failureBusy === selectedFailure.submissionId}
                      onClick={() => void handleStartManual(selectedFailure)}
                    >
                      <i className="ti ti-user-check" />
                      <span>Start Manual Publish</span>
                    </button>
                  </>
                )}
              </div>
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
            <div className="val-scroll">
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

              {selectedLoading ? (
                <PanelContentLoader text="Loading submission details" />
              ) : (
                <div className={editMode ? "val-edit-layout" : "val-edit-layout--off"}>
                  <FacebookPostPreviewCard
                    submission={selected}
                    editMode={editMode}
                    editForm={editForm}
                    mediaAssets={mediaAssets}
                    mediaIndex={mediaIndex}
                    onMediaIndexChange={setMediaIndex}
                    watermarkConfig={watermarkConfig}
                    showWatermarkPreview={showWatermarkPreview}
                    onToggleWatermark={() => setShowWatermarkPreview((prev) => !prev)}
                    onOpenHistory={() => setShowHistoryModal(true)}
                  />

                  {editMode && (
                    <section className="val-edit-grid-panel">
                      <div className="val-edit-tabs" role="tablist">
                        {(
                          [
                            ["details", "ti-file-text", "Details"],
                            ["media", "ti-photo", "Media"],
                            ["schedule", "ti-calendar-clock", "Schedule"],
                          ] as const
                        ).map(([key, icon, label]) => (
                          <button
                            key={key}
                            type="button"
                            role="tab"
                            aria-selected={editTab === key}
                            className={`val-edit-tab${editTab === key ? " active" : ""}`}
                            onClick={() => setEditTab(key)}
                          >
                            <i className={`ti ${icon}`} />
                            <span>{label}</span>
                            {key === "media" && editForm.media.length > 0 && (
                              <em>{editForm.media.length}</em>
                            )}
                            {key === "schedule" && hardBlocked && <em className="warn">!</em>}
                          </button>
                        ))}
                      </div>

                      {editTab === "details" && (
                        <div className="val-edit-body">
                          <div className="val-edit-row">
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
                          </div>

                          <div className="val-edit-field">
                            <div className="val-edit-label-row">
                              <span>Caption</span>
                              <FancyTextTool
                                caption={editForm.caption}
                                selection={captionSelection}
                                onReplaceSelection={(next, sel) => {
                                  setEditForm((f) => ({ ...f, caption: next }));
                                  setCaptionSelection(sel);
                                }}
                                onPreviewSelection={(next) => setEditForm((f) => ({ ...f, caption: next }))}
                                onRestoreSelection={setCaptionSelection}
                              />
                            </div>
                            <textarea
                              ref={editCaptionRef}
                              rows={6}
                              value={editForm.caption}
                              onChange={(e) => {
                                setEditForm({ ...editForm, caption: e.target.value });
                                setCaptionSelection({ start: e.target.selectionStart, end: e.target.selectionEnd });
                              }}
                              onSelect={(e) =>
                                setCaptionSelection({
                                  start: e.currentTarget.selectionStart,
                                  end: e.currentTarget.selectionEnd,
                                })
                              }
                            />
                          </div>

                          <label className="val-edit-field">
                            <span>Tags</span>
                            <input
                              placeholder="comma-separated, e.g. Volunteer, Outreach"
                              value={editForm.tags}
                              onChange={(e) => setEditForm({ ...editForm, tags: e.target.value })}
                            />
                          </label>
                        </div>
                      )}

                      {editTab === "media" && (
                        <div className="val-edit-body">
                          <div className="val-edit-label-row">
                            <span>Attached Media</span>
                            <div className="val-edit-add-media-group">
                              <button
                                type="button"
                                className="val-edit-add-media"
                                onClick={() => editFileInputRef.current?.click()}
                              >
                                <i className="ti ti-plus" /> Add files
                              </button>
                              <button
                                type="button"
                                className="val-edit-add-media"
                                onClick={() => setLibraryPickerOpen(true)}
                              >
                                <i className="ti ti-library-photo" /> From Library
                              </button>
                            </div>
                            <input
                              ref={editFileInputRef}
                              type="file"
                              accept="image/*,video/*"
                              multiple
                              hidden
                              onChange={(e) => {
                                addMediaFiles(e.target.files);
                                e.target.value = "";
                              }}
                            />
                          </div>
                          <div className="val-edit-media-list">
                            {editForm.media.length === 0 && (
                              <p className="val-edit-media-empty">
                                <i className="ti ti-photo-off" />
                                No media attached — add files above.
                              </p>
                            )}
                            {editForm.media.map((item, i) => (
                              <div key={item.key} className="val-edit-media-row">
                                <div className="val-edit-media-thumb">
                                  {item.isImage ? (
                                    <img src={item.previewUrl} alt={item.fileName} />
                                  ) : (
                                    <video src={item.previewUrl} muted />
                                  )}
                                </div>
                                <div className="val-edit-media-main">
                                  <div className="val-edit-media-name">{item.fileName}</div>
                                  <input
                                    className="val-edit-media-caption"
                                    placeholder="Optional caption"
                                    value={item.caption}
                                    onChange={(e) => updateMedia(item.key, { caption: e.target.value })}
                                  />
                                  {item.isImage && (
                                    <label className="val-edit-media-wm">
                                      <input
                                        type="checkbox"
                                        checked={item.skipWatermark}
                                        onChange={(e) => updateMedia(item.key, { skipWatermark: e.target.checked })}
                                      />
                                      Skip watermark
                                    </label>
                                  )}
                                </div>
                                <div className="val-edit-media-actions">
                                  <button type="button" onClick={() => moveMedia(i, -1)} disabled={i === 0} aria-label="Move up">
                                    <i className="ti ti-chevron-up" />
                                  </button>
                                  <button type="button" onClick={() => moveMedia(i, 1)} disabled={i === editForm.media.length - 1} aria-label="Move down">
                                    <i className="ti ti-chevron-down" />
                                  </button>
                                  <button type="button" className="val-edit-media-remove" onClick={() => removeMedia(item)} aria-label="Remove">
                                    <i className="ti ti-trash" />
                                  </button>
                                </div>
                              </div>
                            ))}
                          </div>
                        </div>
                      )}

                      {editTab === "schedule" && (
                        <div className="val-edit-body">
                          <div className="val-edit-row">
                            <label className="val-edit-field">
                              <span>Preferred Date</span>
                              <input
                                type="date"
                                value={editForm.scheduledDate}
                                onChange={(e) => setEditForm({ ...editForm, scheduledDate: e.target.value })}
                              />
                            </label>
                            <label className="val-edit-field">
                              <span>Preferred Time</span>
                              <input
                                type="time"
                                value={editForm.scheduledTime}
                                onChange={(e) => setEditForm({ ...editForm, scheduledTime: e.target.value })}
                              />
                            </label>
                          </div>

                          {scheduleChanged && (
                            <div className="val-edit-gr">
                              {guardRailsLoading && <span className="val-edit-gr-loading">Checking slot…</span>}
                              {!guardRailsLoading &&
                                !guardRails?.hardBlocks?.length &&
                                !guardRails?.softWarnings?.length && (
                                  <div className="val-edit-gr-ok">
                                    <i className="ti ti-circle-check" /> Slot is clear.
                                  </div>
                                )}
                              {guardRails?.hardBlocks?.map((v, i) => (
                                <div key={`h${i}`} className="val-edit-gr-block">
                                  <i className="ti ti-alert-triangle" /> {v.message}
                                </div>
                              ))}
                              {guardRails?.softWarnings?.map((v, i) => (
                                <div key={`s${i}`} className="val-edit-gr-warn">
                                  <i className="ti ti-info-circle" /> {v.message}
                                </div>
                              ))}
                            </div>
                          )}

                          {hardBlocked && (
                            <label className="val-edit-field">
                              <span>Override reason (required — a guard rail is blocked)</span>
                              <textarea
                                rows={3}
                                value={overrideReason}
                                onChange={(e) => setOverrideReason(e.target.value)}
                              />
                            </label>
                          )}
                        </div>
                      )}
                    </section>
                  )}
                </div>
              )}
            </div>

            {showHistoryModal && (
              <ValidationHistoryModal
                submission={selected}
                log={log}
                loading={logLoading}
                isTerminalStatus={isTerminalStatus}
                onClose={() => setShowHistoryModal(false)}
              />
            )}

            {isTerminalStatus ? (
              <footer className="val-action-bar val-action-bar--readonly">
                <div className="val-action-status">
                  <span className="val-action-hint">
                    <i className="ti ti-eye" />
                    Read-only — this submission is {statusLabel[normalizeStatus(selected?.status ?? "")] ?? selected?.status ?? "in a terminal state"}.
                  </span>
                </div>
              </footer>
            ) : editMode ? (
              <footer className="val-action-bar">
                <div className="val-action-status">
                  <span className="val-action-edit-pill">
                    <i className="ti ti-pencil" />
                    Editing — saving keeps In Review; select a terminal action after.
                  </span>
                </div>
                <div className="val-action-group">
                  <button
                    className="val-btn val-btn-secondary"
                    type="button"
                    disabled={editSaving}
                    onClick={handleCancelEdit}
                  >
                    Cancel
                  </button>
                  <button
                    className="val-btn val-btn-primary"
                    type="button"
                    disabled={!canSaveEdit}
                    onClick={() => void handleSaveEdit()}
                  >
                    <i className="ti ti-device-floppy" />
                    <span>{editSaving ? "Saving..." : "Save Changes"}</span>
                  </button>
                </div>
              </footer>
            ) : activeLock ? (
              <footer className="val-action-bar">
                <div className="val-action-status">
                  <span className="val-action-lock-pill">
                    <i className="ti ti-lock-check" />
                    Review in progress
                  </span>
                </div>
                <div className="val-action-group">
                  <button
                    className="val-btn val-btn-subtle"
                    type="button"
                    disabled={lockBusy}
                    onClick={() => void handleReleaseLock()}
                    title="Release lock and return to queue"
                  >
                    <i className="ti ti-lock-open" />
                    <span>Unlock</span>
                  </button>

                  <div className="val-action-divider" />

                  <button
                    className="val-btn val-btn-danger-outline"
                    type="button"
                    onClick={() => openDecisionModal("reject")}
                  >
                    <i className="ti ti-ban" />
                    <span>Reject</span>
                  </button>
                  <button
                    className="val-btn val-btn-secondary"
                    type="button"
                    onClick={() => openDecisionModal("revise")}
                  >
                    <i className="ti ti-pencil-exclamation" />
                    <span>Request Revision</span>
                  </button>
                  <button
                    className="val-btn val-btn-blue-outline"
                    type="button"
                    onClick={handleStartEdit}
                  >
                    <i className="ti ti-pencil" />
                    <span>Edit</span>
                  </button>
                  <button
                    className="val-btn val-btn-primary"
                    type="button"
                    onClick={() => openDecisionModal("approve")}
                  >
                    <i className="ti ti-check" />
                    <span>Approve</span>
                  </button>
                </div>
              </footer>
            ) : (
              <footer className="val-action-bar">
                <div className="val-action-status">
                  <span className="val-action-hint">
                    <i className="ti ti-info-circle" />
                    Acquire review lock to record a decision.
                  </span>
                </div>
                <div className="val-action-group">
                  <button
                    className="val-btn val-btn-primary"
                    type="button"
                    disabled={lockBusy}
                    onClick={() => void handleAcquireLock()}
                  >
                    <i className="ti ti-lock" />
                    <span>{lockBusy ? "Locking..." : "Start Review"}</span>
                  </button>
                </div>
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

      {editMode && libraryPickerOpen && selected && (
        <ReviewLibraryPickerModal
          institutionId={selected.institutionId}
          excludeIds={editForm.media.map((m) => m.assetId).filter((x): x is string => Boolean(x))}
          onAdd={addLibraryAssets}
          onClose={() => setLibraryPickerOpen(false)}
        />
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

function PanelContentLoader({ text = "Loading submission details..." }: { text?: string }) {
  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        minHeight: "360px",
        width: "100%",
        padding: "64px 24px",
      }}
      role="status"
      aria-label={text}
    >
      <div className="dc-dot-triangle-container">
        <div className="dc-dot-triangle-label">
          <span>{text}</span>
          <span className="dc-dot-triangle-label-dots">
            <span className="dc-dot-triangle-dot-char">.</span>
            <span className="dc-dot-triangle-dot-char">.</span>
            <span className="dc-dot-triangle-dot-char">.</span>
          </span>
        </div>
        <div className="loader-stage" style={{ display: "flex", alignItems: "center", justifyContent: "center" }}>
          <div className="loader-dots" />
        </div>
      </div>
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

function FacebookPostImage({
  src,
  alt = "",
  showWatermark = false,
  watermarkConfig = null,
  skipWatermark = false,
}: {
  src: string;
  alt?: string;
  showWatermark?: boolean;
  watermarkConfig?: WatermarkConfiguration | null;
  skipWatermark?: boolean;
}) {
  const [isVeryTall, setIsVeryTall] = useState(false);

  useEffect(() => {
    setIsVeryTall(false);
  }, [src]);

  return (
    <div className={`val-fb-image-wrapper ${isVeryTall ? "is-very-tall" : ""}`}>
      <img
        src={src}
        alt={alt}
        className={isVeryTall ? "val-fb-img-cover" : "val-fb-img-natural"}
        onLoad={(e) => {
          const { naturalWidth, naturalHeight } = e.currentTarget;
          if (naturalWidth && naturalHeight) {
            const ratio = naturalWidth / naturalHeight;
            // Facebook feed rule:
            // - If ratio >= 4/5 (0.8): natural scaling (100% width, auto height)
            // - If ratio < 4/5 (0.8): cap container to 4:5 aspect ratio and crop with object-fit: cover
            setIsVeryTall(ratio < 0.8);
          }
        }}
      />
      {showWatermark && watermarkConfig?.enabled && !skipWatermark && (
        <WatermarkOverlay elements={watermarkConfig.elements} />
      )}
    </div>
  );
}

function FacebookPostPreviewCard({
  submission,
  editMode,
  editForm,
  mediaAssets,
  mediaIndex,
  onMediaIndexChange,
  watermarkConfig,
  showWatermarkPreview = true,
  onToggleWatermark,
  onOpenHistory,
}: {
  submission: SubmissionSummary;
  editMode: boolean;
  editForm: EditFormState;
  mediaAssets: SavedMediaAsset[];
  mediaIndex: number;
  onMediaIndexChange: (index: number) => void;
  watermarkConfig?: WatermarkConfiguration | null;
  showWatermarkPreview?: boolean;
  onToggleWatermark?: () => void;
  onOpenHistory?: () => void;
}) {
  const selectedMedia = mediaAssets[mediaIndex];
  const pageName = submission.institutionName || "DasigConnect";
  const displayCaption = editMode ? editForm.caption : (submission.caption || submission.eventTitle);
  const displayTags: string[] = editMode
    ? editForm.tags.split(",").map((t: string) => t.trim()).filter(Boolean)
    : (submission.tags || []);

  const formattedTags: string[] = displayTags.map((t: string) => (t.startsWith("#") ? t : `#${t}`));

  return (
    <article className="val-fb-card" aria-label="Facebook Post Preview">
      {/* 1. Facebook Page Header */}
      <div className="val-fb-header">
        <div className="val-fb-author">
          <div className="val-fb-avatar" aria-hidden="true">
            <i className="ti ti-brand-facebook" />
          </div>
          <div className="val-fb-author-meta">
            <div className="val-fb-author-name">
              <strong>{pageName}</strong>
            </div>
            <div className="val-fb-time-row">
              <span>Published by {pageName}</span>
              <span className="val-fb-dot">·</span>
              <span>
                {submission.fastTrack
                  ? "Live Event Fast-Track"
                  : submission.scheduledAt
                  ? `Scheduled • ${formatDate(submission.scheduledAt)}`
                  : "18h"}
              </span>
              <span className="val-fb-dot">·</span>
              <i className="ti ti-world" title="Public on Facebook" />
            </div>
          </div>
        </div>

        <div className="val-fb-header-actions">
          {watermarkConfig?.enabled && onToggleWatermark && (
            <button
              type="button"
              className={`val-wm-toggle-btn ${showWatermarkPreview ? "active" : ""}`}
              onClick={onToggleWatermark}
              title="Toggle watermark overlay on media preview"
            >
              <i className={`ti ${showWatermarkPreview ? "ti-badge-filled" : "ti-badge"}`} />
              <span>{showWatermarkPreview ? "Watermark: ON" : "Watermark: OFF"}</span>
            </button>
          )}
          {onOpenHistory && (
            <button
              type="button"
              className="val-fb-more-btn"
              onClick={onOpenHistory}
              title="View History & Audit Details (•••)"
              aria-label="View history and audit details"
            >
              <i className="ti ti-dots" />
            </button>
          )}
        </div>
      </div>

      {/* 2. Facebook Post Caption & Hashtags (Starts directly withoutCMS title headline) */}
      <div className="val-fb-body">
        {displayCaption ? (
          <p className="val-fb-text">{displayCaption}</p>
        ) : (
          <p className="val-fb-text val-fb-text-empty">No caption supplied.</p>
        )}
        {formattedTags.length > 0 && (
          <div className="val-fb-hashtags">
            {formattedTags.map((tag: string) => (
              <span key={tag} className="val-fb-hashtag">{tag}</span>
            ))}
          </div>
        )}
      </div>

      {/* 3. Media Frame with Dynamic Facebook Aspect-Ratio Handling */}
      <div className="val-fb-media-frame">
        {selectedMedia ? (
          isImage(selectedMedia.fileType) ? (
            <FacebookPostImage
              src={selectedMedia.storageUrl}
              alt={selectedMedia.fileName}
              showWatermark={showWatermarkPreview}
              watermarkConfig={watermarkConfig}
              skipWatermark={selectedMedia.skipWatermark}
            />
          ) : (
            <div className="val-fb-video-wrapper">
              <video src={selectedMedia.storageUrl} controls playsInline />
            </div>
          )
        ) : (
          <div className="val-fb-no-media">
            <i className="ti ti-photo-off" />
            <span>No media attached</span>
          </div>
        )}

        {mediaAssets.length > 1 && (
          <>
            <button
              className="val-fb-arrow left"
              type="button"
              aria-label="Previous media"
              onClick={() =>
                onMediaIndexChange((mediaIndex - 1 + mediaAssets.length) % mediaAssets.length)
              }
            >
              <i className="ti ti-chevron-left" />
            </button>
            <button
              className="val-fb-arrow right"
              type="button"
              aria-label="Next media"
              onClick={() => onMediaIndexChange((mediaIndex + 1) % mediaAssets.length)}
            >
              <i className="ti ti-chevron-right" />
            </button>
            <div className="val-fb-counter">
              {mediaIndex + 1} / {mediaAssets.length}
            </div>
          </>
        )}
      </div>

      {/* Multi-Photo Carousel Dots / Thumbnails */}
      {mediaAssets.length > 1 && (
        <div className="val-fb-thumbs">
          {mediaAssets.map((asset, index) => (
            <button
              className={`val-fb-thumb ${index === mediaIndex ? "active" : ""}`}
              key={asset.id}
              type="button"
              onClick={() => onMediaIndexChange(index)}
              title={asset.fileName}
            >
              {isImage(asset.fileType) ? (
                <img src={asset.storageUrl} alt="" />
              ) : (
                <div className="val-fb-thumb-video"><i className="ti ti-video" /></div>
              )}
            </button>
          ))}
        </div>
      )}

      {/* 4. Insights Bar (Image 2) */}
      <div className="val-fb-insights-bar" aria-hidden="true">
        <span className="val-fb-insights-link">See insights</span>
        <button type="button" tabIndex={-1} className="val-fb-create-ad-btn">
          Create ad
        </button>
      </div>

      {/* 5. Engagement Action Buttons (Image 2) */}
      <div className="val-fb-action-buttons" aria-hidden="true">
        <button type="button" className="val-fb-action-btn" tabIndex={-1}>
          <i className="ti ti-thumb-up" />
          <span>Like</span>
        </button>
        <button type="button" className="val-fb-action-btn" tabIndex={-1}>
          <i className="ti ti-message-circle" />
          <span>Comment</span>
        </button>
        <button type="button" className="val-fb-action-btn" tabIndex={-1}>
          <i className="ti ti-share-3" />
          <span>Share</span>
        </button>
      </div>

      {/* 6. Comment as page input (Image 2) */}
      <div className="val-fb-comment-bar" aria-hidden="true">
        <div className="val-fb-comment-avatar">
          <i className="ti ti-brand-facebook" />
        </div>
        <div className="val-fb-comment-input-box">
          <span>Comment as {pageName}</span>
          <div className="val-fb-comment-tools">
            <i className="ti ti-mood-smile" />
            <i className="ti ti-camera" />
            <i className="ti ti-gif" />
            <i className="ti ti-sticker" />
          </div>
        </div>
      </div>
    </article>
  );
}

function ValidationHistoryModal({
  submission,
  log,
  loading,
  isTerminalStatus,
  onClose,
}: {
  submission: SubmissionSummary;
  log: ValidationLog[];
  loading: boolean;
  isTerminalStatus: boolean;
  onClose: () => void;
}) {
  const visibleLog = log.filter(
    (entry) =>
      entry.action !== "lock_acquired" &&
      entry.action !== "lock_released",
  );

  return createPortal(
    <div className="val-modal-overlay" onClick={onClose} role="dialog" aria-modal="true">
      <div className="val-history-modal" onClick={(e) => e.stopPropagation()}>
        {/* Header */}
        <div className="val-history-header">
          <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
            <div
              style={{
                width: "36px",
                height: "36px",
                borderRadius: "10px",
                background: "#eff6ff",
                color: "#1877f2",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                fontSize: "18px",
              }}
            >
              <i className="ti ti-history" />
            </div>
            <div>
              <h3 style={{ margin: 0, fontSize: "16px", fontWeight: 700, color: "var(--val-text)" }}>
                Submission History & Details
              </h3>
              <span style={{ fontSize: "12px", color: "var(--val-muted)" }}>
                {shortId(submission.id)} · {submission.institutionName || "Unknown Institution"}
              </span>
            </div>
          </div>
          <button
            type="button"
            className="val-collapse-btn"
            onClick={onClose}
            aria-label="Close"
            style={{ color: "#64748b" }}
          >
            <i className="ti ti-x" />
          </button>
        </div>

        {/* Body */}
        <div className="val-history-body">
          {/* Metadata Grid */}
          <div className="val-history-meta-grid">
            <div className="val-history-meta-item">
              <span>Submitted By</span>
              <strong>{submission.contributorEmail || "—"}</strong>
            </div>
            <div className="val-history-meta-item">
              <span>Event Date</span>
              <strong>{formatDate(submission.eventDate)}</strong>
            </div>
            {submission.scheduledAt && (
              <div className="val-history-meta-item">
                <span>Scheduled Slot</span>
                <strong>{formatDate(submission.scheduledAt)} at {formatTime(submission.scheduledAt)}</strong>
              </div>
            )}
            <div className="val-history-meta-item">
              <span>Status</span>
              <strong style={{ color: "#1877f2", textTransform: "capitalize" }}>
                {statusLabel[normalizeStatus(submission.status)] || normalizeStatus(submission.status).replace(/_/g, " ") || "Unknown"}
              </strong>
            </div>
          </div>

          {/* Moderator / Contributor Notes */}
          {submission.description && (
            <div style={{ padding: "12px 14px", background: "#f8fafc", borderRadius: "8px", border: "1px solid var(--val-border)" }}>
              <span style={{ fontSize: "11px", fontWeight: 700, textTransform: "uppercase", color: "var(--val-muted)" }}>
                Moderator Notes
              </span>
              <p style={{ margin: "4px 0 0", fontSize: "13px", color: "var(--val-text-2)", whiteSpace: "pre-wrap" }}>
                {submission.description}
              </p>
            </div>
          )}

          {/* Timeline Events */}
          <div>
            <div className="val-history-section-title">
              <span>Audit Events ({visibleLog.length})</span>
            </div>

            {loading && (
              <div style={{ display: "flex", alignItems: "center", gap: "8px", color: "var(--val-muted)", padding: "20px 0" }}>
                <i className="ti ti-loader-2 val-spin" />
                <span>Loading audit trail...</span>
              </div>
            )}

            {!loading && visibleLog.length === 0 && (
              <p style={{ color: "var(--val-muted)", fontSize: "13px", fontStyle: "italic", margin: "8px 0" }}>
                {isTerminalStatus
                  ? "No validation actions recorded -- this submission was not reviewed through the validation workflow."
                  : "No approval, revision, rejection, or timeout actions recorded yet."}
              </p>
            )}

            {!loading && visibleLog.length > 0 && (
              <div className="val-history-list">
                {visibleLog.map((entry) => (
                  <div className="val-log-item" key={entry.id}>
                    <div className={`val-log-dot action-${entry.action}`}>
                      <i className={`ti ${logIcon(entry.action)}`}></i>
                    </div>
                    <div className="val-log-content">
                      <strong>
                        {formatAction(entry.action)}
                        {entry.selfReview && <span className="val-log-flag">Self-review</span>}
                        {entry.fastTrack && <span className="val-log-flag fast-track">Fast-Track</span>}
                      </strong>
                      <span className="val-log-meta">
                        {entry.validatorEmail} · {formatDateTime(entry.createdAt)}
                      </span>
                      {entry.remarks && <p className="val-log-remarks">{entry.remarks}</p>}
                      {entry.rejectionReason && <p className="val-log-remarks">{entry.rejectionReason}</p>}
                      {entry.editDiff && <EditDiffView diffJson={entry.editDiff} />}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Footer */}
        <div
          style={{
            display: "flex",
            justifyContent: "flex-end",
            padding: "14px 24px",
            borderTop: "1px solid var(--val-border, #e2e8f0)",
            background: "var(--val-surface, #ffffff)",
          }}
        >
          <button type="button" className="val-btn val-btn-secondary" onClick={onClose}>
            Close
          </button>
        </div>
      </div>
    </div>,
    document.body,
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

function normalizeStatus(value?: string | null) {
  return String(value ?? "").toLowerCase().replace(/-/g, "_");
}

function deadlineTone(value?: string) {
  if (!value) return "";
  const hours = (new Date(value).getTime() - Date.now()) / 36e5;
  if (hours <= 6) return "critical";
  if (hours <= 24) return "urgent";
  return "";
}

function isImage(fileType?: string | null): boolean {
  if (!fileType) return true;
  const lower = String(fileType).toLowerCase();
  return !lower.includes("video") && !lower.includes("mp4") && !lower.includes("mov");
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

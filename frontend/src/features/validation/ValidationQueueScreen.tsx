import {
  lazy,
  Suspense,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { useQueryClient } from "@tanstack/react-query";
import { createPortal } from "react-dom";
import {
  getEngagementRecommendations,
  getSubmission,
  validateGuardRails,
  type EngagementRecommendations,
  type GuardRailResult,
  type SavedMediaAsset,
  type SubmissionStatus,
  type SubmissionSummary,
} from "../../api/submissionApi";
import {
  acquireReviewLock,
  approveSubmission,
  attachValidationLibraryAsset,
  detachValidationAsset,
  editSubmission,
  getReviewLockStatus,
  rejectSubmission,
  releaseReviewLock,
  reorderValidationMedia,
  requestSubmissionRevision,
  type RejectionReasonCode,
  type ReviewLock,
  type ValidationLog,
} from "../../api/validationApi";
import type { SubmissionMediaItem } from "../../types/media";
import { useAiCaptionAssist } from "../../hooks/useAiCaptionAssist";
import type { CaptionTone } from "../../api/aiApi";
import AiCaptionButton from "../submission/components/AiCaptionButton";
import { extractHashtags } from "../submission/utils";
import FancyTextTool, { type FancyTextSelection } from "../submission/components/FancyTextTool";

const AiCaptionSuggestion = lazy(() => import("../submission/components/AiCaptionSuggestion"));
const AiCaptionPromptDialog = lazy(() => import("../submission/components/AiCaptionPromptDialog"));
const MediaAssetsPicker = lazy(() => import("../../components/media/MediaAssetsPicker"));
const EngagementRecommendationsPanel = lazy(() =>
  import("../submission/components/EngagementRecommendationsPanel").then((m) => ({
    default: m.EngagementRecommendationsPanel,
  })),
);
import {
  encodeRevisionRemarks,
  formatRevisionRemarksForDisplay,
  REVISION_SUPPORTED_FIELDS,
} from "../submission/utils/revisionComments";
import ReviewLibraryPickerModal from "./ReviewLibraryPickerModal";
import { useToast } from "../../context/ToastContext";
import type { User } from "../../types/auth.types";
import type { WatermarkConfiguration } from "../../types/watermark.types";
import OptimizedImage, { canTransformImageType } from "../../components/media/OptimizedImage";
import WatermarkOverlay from "../../components/watermark/WatermarkOverlay";
import { useWatermarkConfiguration } from "../../hooks/useWatermarkConfiguration";
import { authenticatedQueryMeta } from "../../lib/queryClient";
import { invalidateQueryRoots } from "../../lib/queryInvalidation";
import { mutationCacheDependencies, queryKeys } from "../../lib/queryKeys";
import {
  useValidationLog,
  useValidationQueue,
} from "./hooks/useValidationQueue";
import { useResolutionFailures } from "../../hooks/useResolutionFailures";
import { useIncrementalPagination } from "../../hooks/useIncrementalPagination";
import type { FailedPublication } from "../../api/resolutionApi";
import ResolutionRetryModal from "./ResolutionRetryModal";
import ManualPublishWorkflowPanel from "./ManualPublishWorkflowPanel";
import "../../styles/dasig-loader.css";
import "../../styles/resolution.css";
import "../../styles/validation.css";
// Reused Submit Content authoring components (AI caption button, engagement
// panel) rely on the `--sub-*` tokens and `.ai-caption-*` rules defined here.
import "../../styles/submission.css";
import SpotlightTour from "../onboarding/components/SpotlightTour";
import { useScreenTour } from "../onboarding/hooks/useScreenTour";
import {
  validationQueueTourSteps,
  validationReviewTourSteps,
  validationFailedTourSteps,
  validationEditTourSteps,
} from "../onboarding/tours/validationTour";

interface ValidationQueueScreenProps {
  user: User;
}

type QueueFilter =
  | "pending"
  | "in_review"
  | "needs_revision"
  | "scheduled"
  | "published"
  | "rejected"
  | "all"
  | "failed";
/** Tabs whose submissions only exist in the history query, not the active queue. */
const HISTORY_ONLY_STATUSES = new Set<QueueFilter>(["scheduled", "published", "rejected"]);
const TAB_ORDER: Array<{ key: QueueFilter; label: string }> = [
  { key: "all", label: "All" },
  { key: "pending", label: "Pending" },
  { key: "in_review", label: "In Review" },
  { key: "needs_revision", label: "Needs Revision" },
  { key: "scheduled", label: "Scheduled" },
  { key: "published", label: "Published" },
  { key: "rejected", label: "Rejected" },
  { key: "failed", label: "Failed" },
];
type SortKey = "publish_slot" | "submitted";
type DecisionModal = "approve" | "revise" | "reject" | null;
const MODAL_EXIT_MS = 190;
const REVIEWABLE_STATUSES = new Set(["pending", "in_review"]);
const SUBMISSION_DETAIL_STALE_TIME_MS = 60_000;

const VIDEO_EXT = new Set(["mp4", "mov", "webm", "avi", "mkv"]);

interface EditMediaItem {
  key: string;
  assetId?: string;
  /**
   * Legacy field kept for MediaAssetsPicker interop only. Device uploads are not
   * allowed during review (A10), so this is never populated here.
   */
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
  scheduledDate: string;
  scheduledTime: string;
  media: EditMediaItem[];
  removedAssetIds: string[];
  /** A10: optional moderator note when attaching Library media not originally submitted. */
  mediaAddNote: string;
  /**
   * Publishing mode snapshot. Fixed during review by default — only an Admin
   * unlocking the override toggle may change this away from what the
   * submission already was (see the Publishing Mode control in the Schedule
   * tab). Never sent back to the server unless it actually changed.
   */
  fastTrack: boolean;
}

function emptyEditForm(): EditFormState {
  return {
    eventTitle: "",
    eventDate: "",
    caption: "",
    description: "",
    scheduledDate: "",
    scheduledTime: "",
    media: [],
    removedAssetIds: [],
    mediaAddNote: "",
    fastTrack: false,
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
    scheduledDate: scheduled ? scheduled.toISOString().slice(0, 10) : "",
    scheduledTime: scheduled
      ? `${String(scheduled.getHours()).padStart(2, "0")}:${String(scheduled.getMinutes()).padStart(2, "0")}`
      : "",
    media: (summary.mediaAssets ?? []).map(savedAssetToMediaItem),
    removedAssetIds: [],
    mediaAddNote: "",
    fastTrack: Boolean(summary.fastTrack),
  };
}

// ── Adapters between the moderator edit model (EditMediaItem) and the shared
//    MediaAssetsPicker model (SubmissionMediaItem). EditMediaItem stays the
//    source of truth; the picker only drives add / reorder / remove.
function editMediaItemToPickerItem(m: EditMediaItem): SubmissionMediaItem {
  return {
    clientId: m.key,
    source: m.assetId ? "library" : "upload",
    assetId: m.assetId,
    file: m.file,
    previewUrl: m.previewUrl,
    mediaType: m.isImage ? "image" : "video",
    fileName: m.fileName,
  };
}

/** Reconcile the picker's returned list back into EditFormState.media. */
function reconcileEditMedia(form: EditFormState, next: SubmissionMediaItem[]): EditFormState {
  const byKey = new Map(form.media.map((m) => [m.key, m]));
  const media: EditMediaItem[] = next.map((item) => {
    const existing = byKey.get(item.clientId);
    if (existing) return existing;
    return {
      key: item.clientId,
      assetId: item.assetId,
      file: item.file,
      previewUrl: item.previewUrl,
      fileName: item.fileName,
      isImage: item.mediaType === "image",
      caption: "",
      skipWatermark: false,
    };
  });
  const survivingKeys = new Set(next.map((i) => i.clientId));
  const dropped = form.media.filter((m) => !survivingKeys.has(m.key) && m.assetId);
  const presentAssetIds = new Set(media.map((m) => m.assetId).filter(Boolean));
  const removedAssetIds = [
    ...form.removedAssetIds,
    ...dropped.map((m) => m.assetId as string),
  ].filter((id) => !presentAssetIds.has(id));
  return { ...form, media, removedAssetIds };
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

function getUserCacheScope(user: User) {
  return user.id ?? user.email.trim().toLowerCase();
}

/** Matches the existing mobile master-detail breakpoint in validation.css (`@media (max-width: 860px)`). */
const DESKTOP_MEDIA_QUERY = "(min-width: 861px)";

/** True above the mobile master-detail breakpoint — gates the full-width queue (no selection) vs. split (selection) layout, which only applies at desktop/tablet widths. Below it, the existing mobile queue/review toggle is unchanged. */
function useIsDesktop(): boolean {
  const [isDesktop, setIsDesktop] = useState(
    () => typeof window !== "undefined" && window.matchMedia(DESKTOP_MEDIA_QUERY).matches,
  );
  useEffect(() => {
    const mql = window.matchMedia(DESKTOP_MEDIA_QUERY);
    const handler = (e: MediaQueryListEvent) => setIsDesktop(e.matches);
    mql.addEventListener("change", handler);
    return () => mql.removeEventListener("change", handler);
  }, []);
  return isDesktop;
}

export default function ValidationQueueScreen({
  user,
}: ValidationQueueScreenProps) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const currentUserScope = getUserCacheScope(user);
  const [filter, setFilter] = useState<QueueFilter>("all");
  const isAllMode = filter === "all";
  const isFailedMode = filter === "failed";
  const needsHistory = isAllMode || HISTORY_ONLY_STATUSES.has(filter);
  const isDesktop = useIsDesktop();
  const { queue: activeQueue, loading: activeLoading, error: activeError } = useValidationQueue(user);
  const { queue: allQueue, loading: allLoading, error: allError, refresh: refreshAllQueue } = useValidationQueue(user, true, needsHistory);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [selected, setSelected] = useState<SubmissionSummary | null>(null);
  const [selectedLoading, setSelectedLoading] = useState(false);
  const [locks, setLocks] = useState<Record<string, ReviewLock>>({});
  const [lockNotice, setLockNotice] = useState("");
  const [lockBusy, setLockBusy] = useState(false);
  const [lockVerification, setLockVerification] = useState<{
    submissionId: string;
    status: "checking" | "verified" | "error";
  } | null>(null);
  const [isPanelCollapsed, setIsPanelCollapsed] = useState(false);
  const [mobileView, setMobileView] = useState<"queue" | "review">("queue");
  const [showDetails, setShowDetails] = useState(true);
  const [sortKey, setSortKey] = useState<SortKey>("submitted");
  const [search, setSearch] = useState("");
  const [mediaIndex, setMediaIndex] = useState(0);
  const [renderedModal, setRenderedModal] = useState<DecisionModal>(null);
  const [modalClosing, setModalClosing] = useState(false);
  const [decisionBusy, setDecisionBusy] = useState(false);
  const [remarks, setRemarks] = useState("");
  const [revisionFieldComments, setRevisionFieldComments] = useState<Record<string, string>>({});
  const [activeRevisionField, setActiveRevisionField] = useState<string | null>("caption");
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
  const isAdmin = user.role === "admin";
  const editCaptionRef = useRef<HTMLTextAreaElement | null>(null);
  const [libraryPickerOpen, setLibraryPickerOpen] = useState(false);
  // Submit Content authoring features brought into moderator edit mode.
  const [captionPromptOpen, setCaptionPromptOpen] = useState(false);
  const [mediaSettingsKey, setMediaSettingsKey] = useState<string | null>(null);
  const [engagementRecs, setEngagementRecs] = useState<EngagementRecommendations | null>(null);
  const [engagementLoading, setEngagementLoading] = useState(false);
  const { log, loading: logLoading, refresh: refreshLog } = useValidationLog(user, selectedId);
  const modalExitTimer = useRef<number | null>(null);
  const openRequestRef = useRef(0);

  const submissionDetailQueryKey = useCallback(
    (submissionId: string, institutionId?: string | null) =>
      queryKeys.submissions.editorDetail({
        role: user.role,
        userId: currentUserScope,
        institutionId: institutionId ?? user.institutionId ?? null,
        submissionId,
      }),
    [currentUserScope, user.institutionId, user.role],
  );

  const fetchSubmissionDetail = useCallback(
    (
      submissionId: string,
      institutionId?: string | null,
      staleTime = SUBMISSION_DETAIL_STALE_TIME_MS,
    ) =>
      queryClient.fetchQuery({
        queryKey: submissionDetailQueryKey(submissionId, institutionId),
        queryFn: ({ signal }) => getSubmission(submissionId, signal).then((res) => res.data),
        staleTime,
        meta: authenticatedQueryMeta,
      }),
    [queryClient, submissionDetailQueryKey],
  );

  const invalidateValidationWorkflow = useCallback(() => {
    return invalidateQueryRoots(queryClient, mutationCacheDependencies.validationWorkflow);
  }, [queryClient]);

  const {
    failures,
    loading: failuresLoading,
    error: failuresError,
    busy: failureBusy,
    activeDetail: manualPublishDetail,
    detailLoading: manualPublishDetailLoading,
    handleRetryWithNewSchedule: handleFailureRetryWithNewSchedule,
    handleRetry: handleFailureRetry,
    handleRetryAsLive: handleFailureRetryAsLive,
    handleStartManual,
    handleCancelManual,
    handleCompleteManual,
    openWorkflowPanel,
    closeWorkflowPanel,
  } = useResolutionFailures(user);
  const [retryItem, setRetryItem] = useState<FailedPublication | null>(null);

  const filteredFailures = useMemo(() => {
    const term = search.trim().toLowerCase();
    if (!term) return failures;
    return failures.filter((item) =>
      [item.eventTitle, item.institutionName, item.lastError]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(term)),
    );
  }, [failures, search]);
  // Failed items are just submissions whose status is publish_failed/missed_review —
  // opening one goes through the exact same openSubmission()/selectedId path as any
  // other tab (see below); this just adds the retry-specific extras (retry count,
  // last error, etc.) on top of the one shared selection, instead of a second
  // parallel selection system with its own content-area panel.
  const failureInfo = useMemo(
    () => failures.find((f) => f.submissionId === selectedId) ?? null,
    [failures, selectedId],
  );
  const combinedQueue = useMemo(() => {
    const submissions = new Map<string, SubmissionSummary>();
    [...activeQueue, ...allQueue].forEach((item) => submissions.set(item.id, item));
    return Array.from(submissions.values());
  }, [activeQueue, allQueue]);
  const queue = needsHistory ? combinedQueue : activeQueue;
  const loading = needsHistory ? activeLoading || allLoading : activeLoading;
  const error = needsHistory ? activeError || allError : activeError;

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
        // Fast-Track (Live Event) submissions are urgent — they sort to the
        // top of the active queue (UC-1.9 A5). Not applied in history-backed
        // tabs, where already-resolved items are just browsed by date.
        if (!needsHistory) {
          const fastTrackDiff = Number(Boolean(b.fastTrack)) - Number(Boolean(a.fastTrack));
          if (fastTrackDiff !== 0) return fastTrackDiff;
        }
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
        return needsHistory ? -cmp : cmp;
      });
  }, [filter, needsHistory, queue, search, sortKey]);

  const {
    visibleItems: visibleQueue,
    hasMore: hasMoreQueue,
    totalCount: totalQueueCount,
    sentinelRef: queueSentinelRef,
  } = useIncrementalPagination(filteredQueue, {
    pageSize: 15,
    initialSize: 15,
    resetDeps: [filter, search, sortKey],
    selectedItemId: selectedId,
    getItemId: (item) => (item as SubmissionSummary)?.id,
  });

  const {
    visibleItems: visibleFailures,
    hasMore: hasMoreFailures,
    totalCount: totalFailuresCount,
    sentinelRef: failuresSentinelRef,
  } = useIncrementalPagination(filteredFailures, {
    pageSize: 15,
    initialSize: 15,
    resetDeps: [search],
    selectedItemId: selectedId,
    getItemId: (item) => (item as FailedPublication)?.submissionId,
  });

  const pendingCount = activeQueue.filter(
    (item) => normalizeStatus(item.status) === "pending",
  ).length;
  const reviewCount = activeQueue.filter(
    (item) => normalizeStatus(item.status) === "in_review",
  ).length;
  const needsRevisionCount = activeQueue.filter(
    (item) => normalizeStatus(item.status) === "needs_revision",
  ).length;
  const scheduledCount = combinedQueue.filter(
    (item) => normalizeStatus(item.status) === "scheduled",
  ).length;
  const publishedCount = combinedQueue.filter(
    (item) => normalizeStatus(item.status) === "published",
  ).length;
  const rejectedCount = combinedQueue.filter(
    (item) => normalizeStatus(item.status) === "rejected",
  ).length;
  const tabCounts: Record<QueueFilter, number> = {
    all: combinedQueue.length,
    pending: pendingCount,
    in_review: reviewCount,
    needs_revision: needsRevisionCount,
    scheduled: scheduledCount,
    published: publishedCount,
    rejected: rejectedCount,
    failed: failures.length,
  };
  const hasActiveSelection = Boolean(selectedId);
  const isQueueExpanded = isDesktop && !hasActiveSelection;

  const selectedLockVerification = selected && lockVerification?.submissionId === selected.id
    ? lockVerification.status
    : null;
  const lockVerificationChecking = selectedLockVerification === "checking";
  const activeLock = selected && selectedLockVerification === "verified"
    ? locks[selected.id] ?? null
    : null;

  const mediaAssets = selected?.mediaAssets ?? [];
  const isSelfReview =
    Boolean(selected?.contributorEmail) &&
    selected?.contributorEmail?.toLowerCase() === user.email.toLowerCase();
  const isTerminalStatus = Boolean(
    selected && !REVIEWABLE_STATUSES.has(normalizeStatus(selected.status ?? "")),
  );

  const watermarkQuery = useWatermarkConfiguration({
    user,
    enabled: Boolean(selectedId),
  });
  const watermarkConfig = watermarkQuery.data ?? null;
  const [showWatermarkPreview, setShowWatermarkPreview] = useState<boolean>(true);
  const [showHistoryModal, setShowHistoryModal] = useState<boolean>(false);

  const {
    startTour: startQueueTour,
    tourProps: queueTourProps,
  } = useScreenTour({
    screenId: "validation-queue",
    steps: validationQueueTourSteps,
    autoStartDelayMs: 700,
    canStart: !loading && !selectedId && !isFailedMode,
  });

  const {
    startTour: startReviewTour,
    tourProps: reviewTourProps,
  } = useScreenTour({
    screenId: "validation-review",
    steps: validationReviewTourSteps,
    autoStartDelayMs: 600,
    canStart: Boolean(selected) && !failureInfo && !editMode && !queueTourProps.isOpen,
  });

  const {
    startTour: startFailedTour,
    tourProps: failedTourProps,
  } = useScreenTour({
    screenId: "validation-failed",
    steps: validationFailedTourSteps,
    autoStartDelayMs: 600,
    canStart: Boolean(failureInfo) && !queueTourProps.isOpen,
  });

  const {
    startTour: startEditTour,
    tourProps: editTourProps,
  } = useScreenTour({
    screenId: "validation-edit",
    steps: validationEditTourSteps,
    autoStartDelayMs: 500,
    canStart: Boolean(selected) && editMode && !reviewTourProps.isOpen && !queueTourProps.isOpen,
  });


  useEffect(() => {
    return () => {
      if (modalExitTimer.current) window.clearTimeout(modalExitTimer.current);
    };
  }, []);

  function handleFilterChange(next: QueueFilter) {
    if (next === filter) return;
    setMobileView("queue");
    setSortKey(next === "all" ? "submitted" : "publish_slot");
    setFilter(next);
    setShowHistoryModal(false);
    // Deliberately does NOT clear selectedId/selected here — switching tabs
    // is just re-filtering the list on the left; a submission already open
    // for review (including a failed one) should stay open (desktop stays in
    // split view, not snap back to the full-width queue) unless it isn't
    // part of the new tab's results at all, which the auto-select effect
    // below already handles.
    if (next === "all" || HISTORY_ONLY_STATUSES.has(next)) {
      void refreshAllQueue();
    }
  }

  /** Desktop-only: clears the selection to return to the full-width queue view. */
  function handleBackToQueue() {
    openRequestRef.current += 1;
    setSelectedId(null);
    setSelected(null);
    setSelectedLoading(false);
    setLockVerification(null);
  }

  function openDecisionModal(nextModal: Exclude<DecisionModal, null>) {
    if (nextModal === "approve" && isSelfReview) {
      toast.error("Your own submission must be reviewed by another moderator.");
      return;
    }
    if (modalExitTimer.current) window.clearTimeout(modalExitTimer.current);
    setModalClosing(false);
    if (nextModal === "revise") {
      setRemarks("Please revise all input fields marked with a comment icon.");
      setRevisionFieldComments({});
      setActiveRevisionField("caption");
    }
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
    const detailQueryKey = submissionDetailQueryKey(summary.id, summary.institutionId);
    const cachedDetail = queryClient.getQueryData<SubmissionSummary>(detailQueryKey);
    const requiresLockVerification = REVIEWABLE_STATUSES.has(normalizeStatus(summary.status));
    // NEEDS_REVISION is still being actively edited/autosaved by the contributor —
    // GET /submissions/{id} always returns the LIVE row (it's the same endpoint the
    // contributor's own editor uses), which would leak their in-progress edits into
    // this read-only view. The queue summary itself already carries the frozen
    // review_snapshot values instead, so it's used as-is with no live re-fetch.
    const isFrozenSnapshot = normalizeStatus(summary.status) === "needs_revision";

    setSelectedId(summary.id);
    setSelected(cachedDetail ?? summary);
    setSelectedLoading(!cachedDetail && !isFrozenSnapshot);
    setMediaIndex(0);
    setLockNotice("");
    setLockVerification(
      requiresLockVerification
        ? { submissionId: summary.id, status: "checking" }
        : null,
    );
    setEditMode(false);
    setEditedThisSession(false);

    const detailRequest = isFrozenSnapshot
      ? Promise.resolve()
      : fetchSubmissionDetail(summary.id, summary.institutionId)
          .then((detail) => {
            if (requestId === openRequestRef.current) setSelected(detail);
          })
          .catch((err: unknown) => {
            if (requestId === openRequestRef.current) {
              toast.error(readApiError(err, "Unable to open this submission."));
            }
          })
          .finally(() => {
            if (requestId === openRequestRef.current) setSelectedLoading(false);
          });

    // Lock state is deliberately not query-cached as permission. Its live check
    // runs independently so readable content does not wait for authorization UI.
    const lockRequest = requiresLockVerification
      ? getReviewLockStatus(summary.id)
          .then((lockStatus) => {
            if (requestId !== openRequestRef.current) return;
            const lock = lockStatus.data;
            if (lock?.lockedByEmail.toLowerCase() === user.email.toLowerCase()) {
              setLocks((prev) => ({ ...prev, [summary.id]: lock }));
              setLockNotice("");
            } else {
              setLocks((prev) => {
                if (!(summary.id in prev)) return prev;
                const next = { ...prev };
                delete next[summary.id];
                return next;
              });
              setLockNotice(
                lock
                  ? `This submission is currently being reviewed by Moderator ${lock.lockedByEmail}.`
                  : "",
              );
            }
            setLockVerification({ submissionId: summary.id, status: "verified" });
          })
          .catch(() => {
            if (requestId !== openRequestRef.current) return;
            setLocks((prev) => {
              if (!(summary.id in prev)) return prev;
              const next = { ...prev };
              delete next[summary.id];
              return next;
            });
            setLockVerification({ submissionId: summary.id, status: "error" });
            setLockNotice("Unable to verify the current review lock. Start Review will retry securely.");
          })
      : Promise.resolve();

    await Promise.allSettled([detailRequest, lockRequest]);
  }, [fetchSubmissionDetail, queryClient, selectedId, submissionDetailQueryKey, toast, user.email]);

  function clearSelection() {
    const requestId = ++openRequestRef.current;
    queueMicrotask(() => {
      if (requestId !== openRequestRef.current) return;
      setSelectedId(null);
      setSelected(null);
      setSelectedLoading(false);
      setLockVerification(null);
    });
  }

  // Keeps whatever's open in sync with the active tab — for every tab
  // including Failed, which is just another status filter on the same
  // selection now, not a parallel selection system with its own panel.
  useEffect(() => {
    if (loading || (isFailedMode && failuresLoading)) return;

    const selectedIsInCurrentTab = selectedId
      ? (isFailedMode ? filteredFailures.some((f) => f.submissionId === selectedId) : filteredQueue.some((item) => item.id === selectedId))
      : false;
    if (selectedIsInCurrentTab) return;

    if (isDesktop) {
      // Switching tabs only re-filters the left-hand list — an already-open
      // submission must stay open even if it doesn't match the tab just
      // clicked (e.g. viewing a Pending item, then clicking Failed). Existence
      // is checked against every known source (active+history queue, and the
      // failures list), not just the current tab's own filtered view — only
      // clear if it's genuinely gone everywhere (deleted, or no longer returned
      // by any query at all).
      const stillExists = selectedId
        ? combinedQueue.some((item) => item.id === selectedId) || failures.some((f) => f.submissionId === selectedId)
        : true;
      if (stillExists) return;
      if (selectedId || selected) clearSelection();
      return;
    }

    // Mobile: the list is always the starting screen, so auto-pick a first
    // item from the current tab as a convenience once the previous
    // selection (if any) no longer matches it.
    if (isFailedMode) {
      const first = filteredFailures[0];
      if (first) {
        queueMicrotask(() =>
          void openSubmission({
            id: first.submissionId,
            institutionId: first.institutionId,
            institutionName: first.institutionName,
            eventTitle: first.eventTitle,
            eventDate: "",
            status: first.status as SubmissionStatus,
            scheduledAt: first.scheduledAt ?? undefined,
            fastTrack: first.fastTrack,
          }),
        );
        return;
      }
    } else if (filteredQueue.length > 0) {
      queueMicrotask(() => void openSubmission(filteredQueue[0]));
      return;
    }

    if (selectedId || selected) clearSelection();
  }, [isFailedMode, loading, failuresLoading, filteredFailures, filteredQueue, combinedQueue, failures, selectedId, selected, openSubmission, isDesktop]);

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
    if (isSelfReview) {
      toast.error("You cannot review your own submission. Another Moderator must review it.");
      return;
    }
    setLockBusy(true);
    try {
      const lock = await acquireReviewLock(selected.id);
      setLockFor(selected.id, lock.data);
      setLockVerification({ submissionId: selected.id, status: "verified" });
      setLockNotice("");
      await invalidateValidationWorkflow();
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
      await invalidateValidationWorkflow();
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
    void invalidateValidationWorkflow();
  }

  async function handleApprove() {
    if (!selected) return;
    if (isSelfReview) {
      toast.error("Your own submission must be reviewed by another moderator.");
      return;
    }
    setDecisionBusy(true);
    try {
      await approveSubmission(selected.id);
      toast.success("Submission approved and scheduled.");
      closeDecisionModal();
      clearLockFor(selected.id);
      setSelected(null);
      setSelectedId(null);
      setMobileView("queue");
      await invalidateValidationWorkflow();
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

  async function handleStartEdit() {
    if (!selected) return;
    // The queue summary carries no mediaAssets — only the detail does. Guarantee
    // a full record so the editor seeds the already-attached media.
    let full = selected;
    if (!Array.isArray(selected.mediaAssets)) {
      try {
        full = await fetchSubmissionDetail(selected.id, selected.institutionId);
        setSelected(full);
      } catch {
        /* fall through with what we have */
      }
    }
    setEditForm(toEditForm(full));
    setGuardRails(null);
    setCaptionSelection({ start: 0, end: 0 });
    setEditTab("details");
    setOverrideReason("");
    setIsPanelCollapsed(true);
    setEditMode(true);
  }

  // Safety net for the race where edit mode opens before the detail's media
  // arrives: backfill the attached media once, only while the moderator hasn't
  // touched the media list yet.
  useEffect(() => {
    if (!editMode || !selected) return;
    const assets = selected.mediaAssets ?? [];
    if (assets.length === 0) return;
    queueMicrotask(() => {
      setEditForm((f) =>
        f.media.length === 0 && f.removedAssetIds.length === 0
          ? { ...f, media: assets.map(savedAssetToMediaItem) }
          : f,
      );
    });
  }, [editMode, selected]);

  function handleCancelEdit() {
    setEditMode(false);
    setGuardRails(null);
  }

  // Publishing mode is fixed during review by default (see ValidationService.edit's
  // admin-only server-side guard); this Admin-only override mirrors Content
  // Submission's Schedule/Live Event toggle. Switching to Live drops any
  // in-progress schedule; switching to Scheduled just clears the flag so the
  // date/time fields below become editable again.
  function updateEditFastTrack(value: boolean) {
    setEditForm((f) => ({
      ...f,
      fastTrack: value,
      scheduledDate: value ? "" : f.scheduledDate,
      scheduledTime: value ? "" : f.scheduledTime,
    }));
  }

  const editScheduledAtIso = useMemo(() => {
    if (!editForm.scheduledDate || !editForm.scheduledTime) return "";
    const d = new Date(`${editForm.scheduledDate}T${editForm.scheduledTime}`);
    return Number.isNaN(d.getTime()) ? "" : d.toISOString();
  }, [editForm.scheduledDate, editForm.scheduledTime]);

  const originalScheduledIso = selected?.scheduledAt
    ? new Date(selected.scheduledAt).toISOString()
    : "";
  const scheduleChanged = !editForm.fastTrack && editScheduledAtIso !== originalScheduledIso;
  const fastTrackChanged = editForm.fastTrack !== Boolean(selected?.fastTrack);

  useEffect(() => {
    let active = true;
    if (!editMode || !scheduleChanged || !editScheduledAtIso || !selected) {
      queueMicrotask(() => {
        if (!active) return;
        setGuardRails(null);
        setGuardRailsLoading(false);
      });
      return () => {
        active = false;
      };
    }
    const controller = new AbortController();
    queueMicrotask(() => {
      if (!active) return;
      setGuardRailsLoading(true);
      validateGuardRails(
        editScheduledAtIso,
        selected.institutionId,
        selected.id,
        controller.signal,
      )
        .then((res) => {
          if (active) setGuardRails(res.data);
        })
        .catch(() => {
          if (active && !controller.signal.aborted) setGuardRails(null);
        })
        .finally(() => {
          if (active) setGuardRailsLoading(false);
        });
    });
    return () => {
      active = false;
      controller.abort();
    };
  }, [editMode, scheduleChanged, editScheduledAtIso, selected]);

  const hardBlocked = (guardRails?.hardBlocks?.length ?? 0) > 0;

  // ── Content completeness (mirrors SubmissionService.assertContentComplete) ─
  const editMissingFields = useMemo(() => {
    const missing: string[] = [];
    if (!editForm.eventTitle.trim()) missing.push("an event title");
    if (!editForm.eventDate) missing.push("an event date");
    if (!editForm.caption.trim()) missing.push("a caption");
    if (editForm.media.length < 1) missing.push("at least one media attachment");
    return missing;
  }, [editForm]);

  // Only an admin can bypass a hard block — with a reason. Moderators cannot
  // save a blocked slot at all.
  const canSaveEdit =
    !editSaving &&
    editMissingFields.length === 0 &&
    (!hardBlocked || (isAdmin && overrideReason.trim().length >= 10));

  // ── AI caption assist (Details tab) ──────────────────────────────────────
  const editCaptionHashtags = useMemo(
    () => extractHashtags(editForm.caption),
    [editForm.caption],
  );
  const editHasImage = editForm.media.some((m) => m.isImage);
  const aiCaption = useAiCaptionAssist(
    editMode ? selectedId : null,
    editHasImage,
    editForm.caption,
  );

  function applyEditCaption(caption: string) {
    setEditForm((f) => ({ ...f, caption }));
  }

  async function handleAiCaptionPromptSubmit(prompt: string, tone: CaptionTone) {
    const generated = await aiCaption.suggest(prompt, tone, undefined, editForm.caption);
    if (generated) {
      toast.success("AI caption generated! Review and refine, or click Approve.");
      return generated;
    } else if (aiCaption.notice) {
      toast.error(aiCaption.notice);
      return null;
    } else {
      toast.error("AI caption could not be generated. Please try again.");
      return null;
    }
  }

  function handleAiCaptionApprove(caption: string, tone: CaptionTone) {
    applyEditCaption(caption);
    aiCaption.logApply(tone, "use");
    setCaptionPromptOpen(false);
    toast.success("AI caption approved and placed in your caption box!");
  }

  // ── Recommended publish times (Schedule tab) ─────────────────────────────
  useEffect(() => {
    if (!editMode || editTab !== "schedule" || !selected) {
      return;
    }
    const controller = new AbortController();
    let active = true;
    queueMicrotask(() => {
      if (!active) return;
      setEngagementLoading(true);
      getEngagementRecommendations(selected.institutionId, controller.signal)
        .then((res) => {
          if (active) setEngagementRecs(res.data.available ? res.data : null);
        })
        .catch((err: unknown) => {
          if (active && (err as { name?: string })?.name !== "CanceledError") {
            setEngagementRecs(null);
          }
        })
        .finally(() => {
          if (active) setEngagementLoading(false);
        });
    });
    return () => {
      active = false;
      controller.abort();
    };
  }, [editMode, editTab, selected]);

  function applyRecommendedSlot(scheduledAt: string) {
    const slot = new Date(scheduledAt);
    if (Number.isNaN(slot.getTime())) return;
    setEditForm((f) => ({
      ...f,
      scheduledDate: `${slot.getFullYear()}-${String(slot.getMonth() + 1).padStart(2, "0")}-${String(slot.getDate()).padStart(2, "0")}`,
      scheduledTime: `${String(slot.getHours()).padStart(2, "0")}:${String(slot.getMinutes()).padStart(2, "0")}`,
    }));
  }

  function updateMedia(key: string, patch: Partial<EditMediaItem>) {
    setEditForm((f) => ({
      ...f,
      media: f.media.map((m) => (m.key === key ? { ...m, ...patch } : m)),
    }));
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
      return {
        ...f,
        media: [...f.media, ...next],
        removedAssetIds: f.removedAssetIds.filter(
          (id) => !next.some((n) => n.assetId === id),
        ),
      };
    });
  }

  async function handleSaveEdit() {
    if (!selected) return;
    if (editMissingFields.length > 0) {
      toast.error(`Add ${editMissingFields.join(", ")} before saving.`);
      return;
    }
    if (!canSaveEdit) return;
    setEditSaving(true);
    try {
      const id = selected.id;

      // Attach staged library picks that aren't on the submission yet. The
      // optional note is recorded on the distinct `media_added` audit event.
      // Device-file uploads are not allowed during review (A10) — moderators
      // attach vetted Library assets only; new media goes back via Request Revision.
      const attached = new Set((selected.mediaAssets ?? []).map((a) => a.id));
      const note = editForm.mediaAddNote.trim() || undefined;
      for (const item of editForm.media) {
        if (item.assetId && !attached.has(item.assetId)) {
          await attachValidationLibraryAsset(id, item.assetId, note).catch(() => undefined);
        }
      }

      // 3. detach removed assets
      for (const assetId of editForm.removedAssetIds) {
        await detachValidationAsset(id, assetId).catch(() => undefined);
      }

      // 4. reorder + per-item caption / skip-watermark. Re-read to learn the
      //    server-assigned ids for freshly uploaded files.
      const afterMedia = (await fetchSubmissionDetail(id, selected.institutionId, 0)).mediaAssets ?? [];
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
        overrideReason:
          isAdmin && hardBlocked && overrideReason.trim() ? overrideReason.trim() : undefined,
        // Tags live only in the caption's #hashtags, matching Submit Content.
        tags: [],
        // Publishing mode is fixed unless an Admin used the override toggle —
        // omit the field entirely otherwise so the backend never even considers
        // changing it (also enforced server-side, admin-only).
        fastTrack: isAdmin && fastTrackChanged ? editForm.fastTrack : undefined,
        scheduledAt: scheduleChanged && editScheduledAtIso ? editScheduledAtIso : undefined,
      });

      // A9: stays IN_REVIEW — refresh content, keep panel + lock open.
      const detail = await fetchSubmissionDetail(id, selected.institutionId, 0);
      setSelected(detail);
      setEditMode(false);
      setGuardRails(null);
      setEditedThisSession(true);
      await invalidateValidationWorkflow();
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
    const finalRemarks = encodeRevisionRemarks(remarks, revisionFieldComments);
    // Validate the human-written content, not the JSON envelope that
    // encodeRevisionRemarks wraps around field-specific comments.
    const writtenLength =
      remarks.trim().length +
      Object.values(revisionFieldComments).reduce(
        (sum, value) => sum + (value || "").trim().length,
        0,
      );
    if (writtenLength < 10) {
      toast.error("Revision remarks must be at least 10 characters.");
      return;
    }
    setDecisionBusy(true);

    try {
      await requestSubmissionRevision(selected.id, { remarks: finalRemarks.trim() });
      toast.warning("Revision request sent to the contributor.");
      closeDecisionModal();
      setRemarks("");
      setRevisionFieldComments({});
      setActiveRevisionField(null);
      clearLockFor(selected.id);
      setSelected(null);
      setSelectedId(null);
      setMobileView("queue");
      await invalidateValidationWorkflow();
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
      setMobileView("queue");
      await invalidateValidationWorkflow();
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
    <div
      className={`val-page ${isPanelCollapsed ? "is-queue-collapsed" : ""} ${isQueueExpanded ? "is-queue-expanded" : ""} val-mobile-view--${mobileView}`}
    >
      <aside className="val-queue-panel">
        <div className="val-queue-header">
          <div className="val-title-row">
            <div>
              <span className="val-kicker">Content operations</span>
              <h1>Review Queue</h1>
              <p>Review, refine, and release network content.</p>
            </div>
            <div className="val-title-actions">
              <button
                type="button"
                className="val-guide-btn"
                onClick={() => startQueueTour(true)}
                title="Show the Review Queue guide"
                aria-label="Show the Review Queue guide"
              >
                <i className="ti ti-help-circle" />
                <span>Guide</span>
              </button>
              {!isQueueExpanded && isDesktop && (
                <>
                  <button
                    type="button"
                    className="val-collapse-btn"
                    onClick={handleBackToQueue}
                    title="Expand to full-width queue"
                    aria-label="Expand to full-width queue"
                  >
                    <i className="ti ti-arrows-maximize" />
                  </button>
                  <button
                    type="button"
                    className="val-collapse-btn"
                    onClick={() => setIsPanelCollapsed(true)}
                    title="Hide queue panel (<<)"
                    aria-label="Hide queue list"
                  >
                    <i className="ti ti-chevrons-left" />
                  </button>
                </>
              )}
            </div>
          </div>

          <div className="val-toolbar-card">
            <div
              className={`val-tabs ${isQueueExpanded ? "val-tabs--expanded" : ""}`}
              role="tablist"
              aria-label="Queue filters"
              tabIndex={0}
              onWheel={(e) => {
                if (e.deltaY !== 0) {
                  e.currentTarget.scrollLeft += e.deltaY;
                }
              }}
            >
              {TAB_ORDER.map((tab) => (
                <button
                  key={tab.key}
                  className={filter === tab.key ? "active" : ""}
                  type="button"
                  role="tab"
                  aria-selected={filter === tab.key}
                  onClick={() => handleFilterChange(tab.key)}
                >
                  <span className="val-tab-label">{tab.label}</span>
                  <span className="val-tab-count">{tabCounts[tab.key]}</span>
                </button>
              ))}
            </div>

            <div className="val-toolbar-controls">
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
            </div>
          </div>
        </div>

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
                visibleFailures.map((item) => (
                  <button
                    className={`val-queue-item ${item.submissionId === selectedId ? "active" : ""}`}
                    key={item.submissionId}
                    type="button"
                    onClick={() => {
                      void openSubmission({
                        id: item.submissionId,
                        institutionId: item.institutionId,
                        institutionName: item.institutionName,
                        eventTitle: item.eventTitle,
                        eventDate: "",
                        status: item.status as SubmissionStatus,
                        scheduledAt: item.scheduledAt ?? undefined,
                        fastTrack: item.fastTrack,
                      });
                      setMobileView("review");
                    }}
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
                    <div className="val-qi-mobile-action">
                      <span className="val-qi-mobile-btn">
                        <i className="ti ti-refresh" />
                        <span>Inspect &amp; Recover</span>
                        <i className="ti ti-chevron-right" />
                      </span>
                    </div>
                  </button>
                ))}

              {hasMoreFailures && (
                <div ref={failuresSentinelRef} className="val-load-more-sentinel">
                  <div className="val-load-more-spinner" />
                  <span>Loading more items...</span>
                </div>
              )}

              {!hasMoreFailures && totalFailuresCount > 15 && (
                <div className="val-queue-end-indicator">
                  <span>Showing all {totalFailuresCount} failures</span>
                </div>
              )}
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
                visibleQueue.map((item) => (
                  <button
                    className={`val-queue-item ${item.id === selectedId ? "active" : ""} ${normalizeStatus(item.status) === "pending" ? deadlineTone(item.scheduledAt) : ""}`}
                    key={item.id}
                    type="button"
                    onClick={() => {
                      void openSubmission(item);
                      setMobileView("review");
                    }}
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
                    <div className="val-qi-mobile-action">
                      <span className="val-qi-mobile-btn">
                        <i className="ti ti-lock" />
                        <span>Start Review</span>
                        <i className="ti ti-chevron-right" />
                      </span>
                    </div>
                  </button>
                ))}

              {hasMoreQueue && (
                <div ref={queueSentinelRef} className="val-load-more-sentinel">
                  <div className="val-load-more-spinner" />
                  <span>Loading more items...</span>
                </div>
              )}

              {!hasMoreQueue && totalQueueCount > 15 && (
                <div className="val-queue-end-indicator">
                  <span>Showing all {totalQueueCount} submissions</span>
                </div>
              )}
            </>
          )}
        </div>
      </aside>

      <main className="val-review-panel">
        <div className="val-mobile-topbar">
          <button
            type="button"
            className="val-mobile-back-btn"
            onClick={() => setMobileView("queue")}
            aria-label="Back to queue list"
          >
            <i className="ti ti-arrow-left" />
            <span>Back to Queue</span>
          </button>
          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            {selected && (
              <button
                type="button"
                className="val-guide-btn"
                onClick={() => (editMode ? startEditTour(true) : failureInfo ? startFailedTour(true) : startReviewTour(true))}
                title="Show the Content Submission guide"
                aria-label="Show the Content Submission guide"
              >
                <i className="ti ti-help-circle" />
                <span>Guide</span>
              </button>
            )}
          </div>
        </div>
        {(isPanelCollapsed || (isDesktop && selected)) && (
          <div className="val-review-toolbar">
            <div>
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
            </div>
            {isDesktop && selected && (
              <button
                type="button"
                className="val-guide-btn"
                onClick={() => (editMode ? startEditTour(true) : failureInfo ? startFailedTour(true) : startReviewTour(true))}
                title="Show the Content Submission guide"
                aria-label="Show the Content Submission guide"
              >
                <i className="ti ti-help-circle" />
                <span>Guide</span>
              </button>
            )}
          </div>
        )}
        {selected && !editMode && !selectedLoading && (
          <button
            type="button"
            className={`val-details-btn ${showDetails ? "is-hidden" : ""}`}
            onClick={() => setShowDetails(true)}
            title="Show submission details"
            aria-label="Show submission details"
          >
            <i className="ti ti-layout-sidebar-right-expand" />
            <span>Details</span>
          </button>
        )}

        {!selected && !selectedLoading && (
          <div className="val-empty">
            <i className="ti ti-clipboard-check"></i>
            <h2>Select a submission</h2>
            <p>Open an item from the queue to acquire a review lock and inspect its content.</p>
          </div>
        )}

        {selected && (
          <>
            <div className="val-scroll">
              {isSelfReview && normalizeStatus(selected.status ?? "") === "pending" && (
                <NoticeBar
                  tone="warn"
                  icon="ti-alert-triangle"
                  text="You cannot review your own submission. Another Moderator must review it."
                />
              )}
              {failureInfo?.lastManualPublishAbandonedAt && (
                <NoticeBar
                  tone="warn"
                  icon="ti-alert-triangle"
                  text={`A manual publish session was abandoned on ${formatDateTime(failureInfo.lastManualPublishAbandonedAt)}.`}
                />
              )}
              {lockNotice && (
                <NoticeBar tone="warn" icon="ti-lock" text={lockNotice} />
              )}

              {selectedLoading ? (
                <PanelContentLoader text="Loading submission details" />
              ) : (
                <div
                  className={
                    editMode
                      ? "val-edit-layout"
                      : `val-review-layout ${showDetails ? "is-details-open" : "is-details-collapsed"}`
                  }
                >
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

                  {!editMode && (
                    <SubmissionDetailsPanel
                      submission={selected}
                      log={log}
                      currentUserEmail={user.email}
                      onHide={() => setShowDetails(false)}
                      isOpen={showDetails}
                      retryCount={failureInfo?.retryCount}
                      lastAttemptAt={failureInfo?.lastAttemptAt}
                      lastError={failureInfo?.lastError}
                    />
                  )}

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

                          <div className="val-edit-field" id="val-edit-caption-group">
                            <div className="val-edit-label-row">
                              <span>Caption</span>
                              <div className="val-edit-caption-tools">
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
                                <AiCaptionButton
                                  state={aiCaption.state}
                                  canSuggest={aiCaption.canSuggest}
                                  rateLimitReset={aiCaption.rateLimitReset}
                                  notice={aiCaption.notice}
                                  onSuggest={() => setCaptionPromptOpen(true)}
                                />
                              </div>
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
                            {aiCaption.variants && (
                              <Suspense fallback={null}>
                                <AiCaptionSuggestion
                                  variants={aiCaption.variants}
                                  onApply={(caption, tone, action) => {
                                    applyEditCaption(caption);
                                    aiCaption.logApply(tone, action);
                                  }}
                                  onDismissOne={aiCaption.logDismissOne}
                                  onDismissAll={aiCaption.dismissAll}
                                  onRegenerate={aiCaption.regenerate}
                                />
                              </Suspense>
                            )}
                          </div>

                          <div className="val-edit-field">
                            <span>Tags</span>
                            {editCaptionHashtags.length > 0 ? (
                              <div className="val-edit-hashtags">
                                {editCaptionHashtags.map((tag) => (
                                  <span key={tag} className="val-edit-hashtag">{tag}</span>
                                ))}
                              </div>
                            ) : (
                              <p className="val-edit-hashtags-hint">
                                Add <code>#hashtags</code> in the caption — they become the post's tags.
                              </p>
                            )}
                          </div>
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
                                onClick={() => setLibraryPickerOpen(true)}
                              >
                                <i className="ti ti-library-photo" /> From Library
                              </button>
                            </div>
                          </div>
                          <p className="val-edit-media-note-hint">
                            <i className="ti ti-info-circle" /> Only vetted Media Library
                            assets can be added during review. New media a contributor
                            needs to supply should go back via Request Revision.
                          </p>
                          <Suspense fallback={<PanelContentLoader text="Loading media tools" />}>
                            <MediaAssetsPicker
                              sourceTabs={false}
                              items={editForm.media.map(editMediaItemToPickerItem)}
                              onItemsChange={(next) => {
                                if (next.length === 0) {
                                  toast.error("Keep at least one media asset.");
                                  return;
                                }
                                setEditForm((f) => reconcileEditMedia(f, next));
                              }}
                              submissionId={selected?.id ?? null}
                              institutionId={selected?.institutionId}
                              eventTitle={editForm.eventTitle}
                              caption={editForm.caption}
                              category={selected?.category ?? ""}
                              tags={editCaptionHashtags.map((h) => h.slice(1))}
                              onItemClick={(item) => setMediaSettingsKey(item.clientId)}
                              getItemCaption={(item) =>
                                editForm.media.find((m) => m.key === item.clientId)?.caption ?? ""
                              }
                            />
                          </Suspense>
                          {editForm.media.some(
                            (m) =>
                              m.assetId &&
                              !(selected?.mediaAssets ?? []).some((a) => a.id === m.assetId),
                          ) && (
                            <label className="val-edit-field val-edit-media-note">
                              <span>
                                Why is this media being added? (optional — recorded on the
                                audit trail)
                              </span>
                              <textarea
                                rows={2}
                                value={editForm.mediaAddNote}
                                onChange={(e) =>
                                  setEditForm({ ...editForm, mediaAddNote: e.target.value })
                                }
                                placeholder="e.g. contributor's photos were all title slides — added a crowd shot from the Library"
                              />
                            </label>
                          )}
                        </div>
                      )}

                      {editTab === "schedule" && (
                        <div className="val-edit-body">
                          <div className="val-edit-mode-row">
                            <div className="val-edit-mode-label">
                              Publishing Mode
                              <i
                                className="ti ti-info-circle"
                                title={
                                  isAdmin
                                    ? "Fixed during review by default. Use this toggle to deliberately override it."
                                    : "Fixed during review — only an Administrator can change the publishing mode."
                                }
                              />
                            </div>
                            {isAdmin ? (
                              <div className="sub-mode-toggle" role="group" aria-label="Publishing mode">
                                <button
                                  type="button"
                                  className={!editForm.fastTrack ? "active" : ""}
                                  onClick={() => updateEditFastTrack(false)}
                                  aria-pressed={!editForm.fastTrack}
                                >
                                  <i className="ti ti-calendar" />
                                  <span>Schedule</span>
                                </button>
                                <button
                                  type="button"
                                  className={editForm.fastTrack ? "active" : ""}
                                  onClick={() => updateEditFastTrack(true)}
                                  aria-pressed={editForm.fastTrack}
                                >
                                  <i className="ti ti-bolt" />
                                  <span>Live Event</span>
                                </button>
                              </div>
                            ) : (
                              <span className={`val-details-mode ${editForm.fastTrack ? "is-live" : "is-scheduled"}`}>
                                <i className={`ti ${editForm.fastTrack ? "ti-bolt" : "ti-calendar-clock"}`} />
                                {editForm.fastTrack ? "Live Event" : "Scheduled"}
                              </span>
                            )}
                          </div>

                          {editForm.fastTrack ? (
                            <div className="val-edit-mode-note">
                              <i className="ti ti-info-circle" /> This is a Live Event submission — it publishes
                              immediately on approval and has no scheduled slot.
                            </div>
                          ) : (
                            <>
                          <Suspense fallback={null}>
                            <EngagementRecommendationsPanel
                              loading={engagementLoading}
                              recommendations={engagementRecs}
                              selectedAt={editScheduledAtIso || undefined}
                              onSelect={applyRecommendedSlot}
                            />
                          </Suspense>
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
                              {hardBlocked && !isAdmin && (
                                <p className="val-edit-gr-note">
                                  Only an administrator can override a guard rail — choose a compliant time.
                                </p>
                              )}
                            </div>
                          )}

                          {hardBlocked && isAdmin && (
                            <label className="val-edit-field">
                              <span>Override reason (required — bypassing a guard rail is audited)</span>
                              <textarea
                                rows={3}
                                value={overrideReason}
                                onChange={(e) => setOverrideReason(e.target.value)}
                                placeholder="Explain why this slot is necessary…"
                              />
                            </label>
                          )}
                            </>
                          )}
                        </div>
                      )}
                    </section>
                  )}
                </div>
              )}

              {failureInfo?.unresolvedPhotoIds && (
                <section className="val-detail-grid" style={{ width: "100%", maxWidth: "620px" }}>
                  {failureInfo.unresolvedPhotoIds && (
                    <DetailCard icon="ti-photo-off" label="Orphaned Facebook Photos" full muted>
                      <p style={{ margin: "0 0 4px" }}>
                        These photos were staged but could not be deleted after the post failed to publish —
                        they may still exist unpublished on the Facebook Page and need manual removal.
                      </p>
                      <code style={{ fontSize: "0.85em", wordBreak: "break-all" }}>
                        {(() => {
                          try {
                            return (JSON.parse(failureInfo.unresolvedPhotoIds) as string[]).join(", ");
                          } catch {
                            return failureInfo.unresolvedPhotoIds;
                          }
                        })()}
                      </code>
                    </DetailCard>
                  )}
                </section>
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

            {failureInfo ? (
              <footer className="val-action-bar">
                <div className="val-action-status">
                  <span className="val-action-hint">
                    <i className="ti ti-info-circle" />
                    <span>
                      {failureInfo.status === "missed_review"
                        ? "This submission missed its review window. Assign a new schedule to send it back to the approval queue."
                        : failureInfo.manualPublishInProgress
                          ? "A manual publish session is already open for this submission."
                          : "Retry automatically, or fall back to manual publishing."}
                    </span>
                  </span>
                </div>
                <div className="val-action-group">
                  {failureInfo.status === "missed_review" ? (
                    <button
                      className="val-btn val-btn-primary"
                      type="button"
                      disabled={failureBusy === failureInfo.submissionId}
                      onClick={() => setRetryItem(failureInfo)}
                    >
                      <i className="ti ti-calendar-plus" />
                      <span>Retry with New Schedule</span>
                    </button>
                  ) : failureInfo.manualPublishInProgress ? (
                    <>
                      <button
                        className="val-btn val-btn-danger-outline"
                        type="button"
                        disabled={failureBusy === failureInfo.submissionId}
                        onClick={() => void handleCancelManual(failureInfo)}
                      >
                        <i className="ti ti-x" />
                        <span>Cancel Manual Session</span>
                      </button>
                      <button
                        className="val-btn val-btn-primary"
                        type="button"
                        onClick={() => openWorkflowPanel(failureInfo)}
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
                        disabled={failureBusy === failureInfo.submissionId}
                        onClick={() =>
                          // A Moderator retrying an already-Live submission has no
                          // mode decision to make — retry it as-is, no modal. Any
                          // other case (Scheduled needs a new time; an Admin may
                          // also want to change the mode) opens the picker.
                          !isAdmin && failureInfo.fastTrack
                            ? void handleFailureRetry(failureInfo)
                            : setRetryItem(failureInfo)
                        }
                      >
                        <i className="ti ti-refresh" />
                        <span>Retry</span>
                      </button>
                      <button
                        className="val-btn val-btn-primary"
                        type="button"
                        disabled={failureBusy === failureInfo.submissionId}
                        onClick={() => void handleStartManual(failureInfo)}
                      >
                        <i className="ti ti-user-check" />
                        <span>Start Manual Publish</span>
                      </button>
                    </>
                  )}
                </div>
              </footer>
            ) : isTerminalStatus ? (
              <footer className="val-action-bar val-action-bar--readonly" id="val-review-actions">
                <div className="val-action-status">
                  <span className="val-action-hint">
                    <i className="ti ti-eye" />
                    {normalizeStatus(selected?.status ?? "") === "needs_revision"
                      ? "Read-only — showing what was last submitted for review. The contributor is revising it now; this will update once they resubmit."
                      : `Read-only — this submission is ${statusLabel[normalizeStatus(selected?.status ?? "")] ?? selected?.status ?? "in a terminal state"}.`}
                  </span>
                </div>
              </footer>
            ) : editMode ? (
              <footer className="val-action-bar" id="val-edit-actions">
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
                    id="val-btn-save-edit"
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
              <footer className="val-action-bar" id="val-review-actions">
                <div className="val-action-status">
                  <span className="val-action-lock-pill">
                    <i className="ti ti-lock-check" />
                    Review in progress by you until {formatDateTime(activeLock.expiresAt)}
                  </span>
                </div>
                <div className="val-action-group" id="val-review-decision-group">
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
                    id="val-btn-reject"
                    className="val-btn val-btn-danger-outline"
                    type="button"
                    onClick={() => openDecisionModal("reject")}
                  >
                    <i className="ti ti-ban" />
                    <span>Reject</span>
                  </button>
                  <button
                    id="val-btn-revise"
                    className="val-btn val-btn-secondary"
                    type="button"
                    onClick={() => openDecisionModal("revise")}
                  >
                    <i className="ti ti-pencil-exclamation" />
                    <span>Request Revision</span>
                  </button>
                  <button
                    id="val-btn-edit"
                    className="val-btn val-btn-blue-outline"
                    type="button"
                    onClick={handleStartEdit}
                  >
                    <i className="ti ti-pencil" />
                    <span>Edit</span>
                  </button>
                  <button
                    id="val-btn-approve"
                    className="val-btn val-btn-primary"
                    type="button"
                    disabled={isSelfReview}
                    title={isSelfReview ? "Your own submission must be reviewed by another moderator." : undefined}
                    onClick={() => openDecisionModal("approve")}
                  >
                    <i className="ti ti-check" />
                    <span>Approve</span>
                  </button>
                </div>
              </footer>
            ) : (
              <footer className="val-action-bar" id="val-review-actions">
                <div className="val-action-status">
                  <span className="val-action-hint">
                    <i className="ti ti-info-circle" />
                    {isSelfReview
                      ? "You cannot review your own submission."
                      : "Acquire review lock to record a decision."}
                  </span>
                </div>
                <div className="val-action-group">
                  <button
                    id="val-btn-start-review"
                    className="val-btn val-btn-primary"
                    type="button"
                    disabled={lockBusy || lockVerificationChecking || isSelfReview}
                    title={isSelfReview ? "Another Moderator must review this submission." : undefined}
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
          dialogClassName="val-modal--wide"
        >
          <div className="val-revision-form">
            <div className="val-revision-group">
              <label className="val-revision-label">
                <span>General Instructions</span>
                <span className="val-revision-hint">Shown in editor banner</span>
              </label>
              <textarea
                className="val-modal-input"
                value={remarks}
                onChange={(event) => setRemarks(event.target.value)}
                rows={3}
                placeholder="Write general instructions (e.g. Please revise all input fields marked with a comment icon)..."
              />
              <small className={remarks.trim().length >= 10 ? "ok" : "err"}>
                {remarks.trim().length} / 10 min
              </small>
            </div>

            <div className="val-revision-group">
              <label className="val-revision-label">
                <span>Field-Specific Comments</span>
                <span className="val-revision-hint">Click a field to add a note under it</span>
              </label>

              <div className="val-revision-chips">
                {REVISION_SUPPORTED_FIELDS.map((field) => {
                  const hasComment = Boolean(revisionFieldComments[field.key]?.trim());
                  const isActive = activeRevisionField === field.key;
                  return (
                    <button
                      key={field.key}
                      type="button"
                      className={`val-revision-chip ${hasComment ? "has-comment" : ""} ${isActive ? "is-active" : ""}`}
                      onClick={() => {
                        setActiveRevisionField(activeRevisionField === field.key ? null : field.key);
                      }}
                    >
                      <i className={`ti ${field.icon}`} />
                      <span>{field.label}</span>
                      {hasComment ? (
                        <i className="ti ti-check val-chip-check" />
                      ) : (
                        <i className="ti ti-plus val-chip-plus" />
                      )}
                    </button>
                  );
                })}
              </div>

              {activeRevisionField && (() => {
                const currentFieldMeta = REVISION_SUPPORTED_FIELDS.find((f) => f.key === activeRevisionField);
                if (!currentFieldMeta) return null;
                const commentVal = revisionFieldComments[activeRevisionField] || "";
                return (
                  <div className="val-revision-field-box">
                    <div className="val-revision-field-head">
                      <div className="val-revision-field-name">
                        <i className={`ti ${currentFieldMeta.icon}`} />
                        <strong>{currentFieldMeta.label} Comment</strong>
                      </div>
                      {commentVal && (
                        <button
                          type="button"
                          className="val-revision-field-clear"
                          onClick={() => {
                            setRevisionFieldComments((prev) => {
                              const next = { ...prev };
                              delete next[activeRevisionField];
                              return next;
                            });
                          }}
                          title="Remove comment for this field"
                        >
                          <i className="ti ti-x" />
                          <span>Clear</span>
                        </button>
                      )}
                    </div>
                    <textarea
                      className="val-modal-input val-revision-field-input"
                      value={commentVal}
                      onChange={(e) => {
                        const v = e.target.value;
                        setRevisionFieldComments((prev) => ({
                          ...prev,
                          [activeRevisionField]: v,
                        }));
                      }}
                      rows={3}
                      placeholder={`Explain what needs to be changed in ${currentFieldMeta.label}...`}
                      autoFocus
                    />
                  </div>
                );
              })()}

              {Object.keys(revisionFieldComments).filter(k => revisionFieldComments[k]?.trim()).length > 0 && (
                <div className="val-revision-attached-summary">
                  <i className="ti ti-info-circle" />
                  <span>
                    {Object.keys(revisionFieldComments).filter(k => revisionFieldComments[k]?.trim()).length} field-specific comment(s) attached
                  </span>
                </div>
              )}
            </div>
          </div>
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
        canOverride={isAdmin}
        busy={retryItem ? failureBusy === retryItem.submissionId : false}
        onConfirmWithNewSchedule={(scheduledAt, overrideReason) => {
          if (retryItem) {
            void handleFailureRetryWithNewSchedule(retryItem, scheduledAt, overrideReason)
              .then(() => setRetryItem(null))
              .catch(() => undefined);
          }
        }}
        onConfirmKeepLive={() => {
          if (retryItem) {
            void handleFailureRetry(retryItem)
              .then(() => setRetryItem(null))
              .catch(() => undefined);
          }
        }}
        onConfirmForceLive={() => {
          if (retryItem) {
            void handleFailureRetryAsLive(retryItem)
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
          excludeIds={editForm.media
            .map((m) => m.assetId)
            .filter((x): x is string => Boolean(x))}
          onAdd={addLibraryAssets}
          onClose={() => setLibraryPickerOpen(false)}
        />
      )}

      {editMode && captionPromptOpen && (
        <Suspense fallback={null}>
          <AiCaptionPromptDialog
            open={captionPromptOpen}
            state={aiCaption.state}
            hasImageAssets={editHasImage}
            existingCaption={editForm.caption}
            onClose={() => setCaptionPromptOpen(false)}
            onSubmit={(prompt, tone) => handleAiCaptionPromptSubmit(prompt, tone)}
            onApprove={handleAiCaptionApprove}
          />
        </Suspense>
      )}

      {editMode && mediaSettingsKey && (() => {
        const item = editForm.media.find((m) => m.key === mediaSettingsKey);
        if (!item) return null;
        return (
          <MediaItemSettingsModal
            item={item}
            onChange={(patch) => updateMedia(item.key, patch)}
            onClose={() => setMediaSettingsKey(null)}
          />
        );
      })()}

      <SpotlightTour {...queueTourProps} />
      <SpotlightTour {...reviewTourProps} />
      <SpotlightTour {...failedTourProps} />
      <SpotlightTour {...editTourProps} />
    </div>
  );
}

function MediaItemSettingsModal({
  item,
  onChange,
  onClose,
}: {
  item: EditMediaItem;
  onChange: (patch: Partial<EditMediaItem>) => void;
  onClose: () => void;
}) {
  return createPortal(
    <div className="val-modal-overlay" role="dialog" aria-modal="true" onClick={onClose}>
      <div className="val-media-settings-modal" onClick={(e) => e.stopPropagation()}>
        <div className="val-preview-modal-head">
          <span><i className="ti ti-photo-edit" /> Media settings</span>
          <button type="button" className="val-details-hide" onClick={onClose} aria-label="Close">
            <i className="ti ti-x" />
          </button>
        </div>
        <div className="val-media-settings-body">
          <div className="val-media-settings-name">{item.fileName}</div>
          <label className="val-edit-field">
            <span>Caption for this item</span>
            <textarea
              rows={3}
              maxLength={500}
              placeholder="Optional caption"
              value={item.caption}
              onChange={(e) => onChange({ caption: e.target.value })}
            />
          </label>
          {item.isImage && (
            <label className="val-media-settings-wm">
              <input
                type="checkbox"
                checked={item.skipWatermark}
                onChange={(e) => onChange({ skipWatermark: e.target.checked })}
              />
              Skip watermark on this image
            </label>
          )}
        </div>
      </div>
    </div>,
    document.body,
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
  const [loadFailed, setLoadFailed] = useState(false);
  const [retryToken, setRetryToken] = useState(0);

  useEffect(() => {
    queueMicrotask(() => {
      setIsVeryTall(false);
      setLoadFailed(false);
      setRetryToken(0);
    });
  }, [src]);

  // A7: a broken/unreachable media asset must not block review — the reviewer
  // can retry the load (e.g. a transient R2/network hiccup) or acknowledge it
  // and proceed straight to Request Revision/Reject with the rest of the
  // submission's content still visible.
  if (loadFailed) {
    return (
      <div className="val-fb-image-wrapper val-fb-image-error">
        <i className="ti ti-photo-off" aria-hidden="true" />
        <span>This media asset failed to load.</span>
        <button
          type="button"
          className="val-btn val-btn-secondary"
          onClick={() => {
            setLoadFailed(false);
            setRetryToken((n) => n + 1);
          }}
        >
          <i className="ti ti-refresh" aria-hidden="true" />
          Retry
        </button>
      </div>
    );
  }

  return (
    <div className={`val-fb-image-wrapper ${isVeryTall ? "is-very-tall" : ""}`}>
      <img
        key={retryToken}
        src={retryToken > 0 ? `${src}${src.includes("?") ? "&" : "?"}retry=${retryToken}` : src}
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
        onError={() => setLoadFailed(true)}
      />
      {showWatermark && watermarkConfig?.enabled && !skipWatermark && (
        <WatermarkOverlay elements={watermarkConfig.elements} />
      )}
    </div>
  );
}

/**
 * Collapsible side panel beside the Facebook preview: who submitted it, who
 * edited it during review, its institution, when it was submitted, and how /
 * when it publishes. Live Event Fast-Track posts have no reserved slot — they
 * go out the moment a moderator approves them.
 */
function SubmissionDetailsPanel({
  submission,
  log,
  currentUserEmail,
  onHide,
  isOpen = true,
  retryCount,
  lastAttemptAt,
  lastError,
}: {
  submission: SubmissionSummary;
  log: ValidationLog[];
  currentUserEmail: string;
  onHide: () => void;
  isOpen?: boolean;
  /** Failed-tab only — a regular submission's review has no retry history. */
  retryCount?: number;
  lastAttemptAt?: string | null;
  lastError?: string | null;
}) {
  const isLive = Boolean(submission.fastTrack);
  const slot = submission.scheduledAt;
  const missingSlot = !isLive && !slot;
  const submittedAt = submission.submittedAt || submission.createdAt;
  const modeClass = isLive ? "is-live" : missingSlot ? "is-unset" : "is-scheduled";

  const isYou = (email?: string | null) =>
    Boolean(email) && email!.toLowerCase() === currentUserEmail.toLowerCase();

  // Co-authors = anyone who applied an inline edit during review, newest activity last.
  const editors: { email: string; count: number; lastAt: string }[] = [];
  for (const entry of log) {
    if (entry.action !== "edited") continue;
    const match = editors.find(
      (e) => e.email.toLowerCase() === entry.validatorEmail.toLowerCase(),
    );
    if (match) {
      match.count += 1;
      match.lastAt = entry.createdAt;
    } else {
      editors.push({ email: entry.validatorEmail, count: 1, lastAt: entry.createdAt });
    }
  }

  return (
    <aside
      className={`val-details-panel ${isOpen ? "is-open" : "is-collapsed"}`}
      aria-label="Submission details"
      aria-hidden={!isOpen}
    >
      <div className="val-details-panel-inner">
        <div className="val-details-head">
          <span>
            <i className="ti ti-info-circle" /> Submission details
          </span>
          <button
            type="button"
            className="val-details-hide"
            onClick={onHide}
            title="Hide details"
            aria-label="Hide submission details panel"
          >
            <i className="ti ti-layout-sidebar-right-collapse" />
          </button>
        </div>

      <dl className="val-details-list">
        <div>
          <dt>Submitted by</dt>
          <dd>
            {submission.contributorEmail || "—"}
            {isYou(submission.contributorEmail) && <span className="val-details-you">You</span>}
          </dd>
        </div>

        <div>
          <dt>Institution</dt>
          <dd>{submission.institutionName || "—"}</dd>
        </div>

        <div>
          <dt>Submitted</dt>
          <dd>{submittedAt ? `${formatDate(submittedAt)} at ${formatTime(submittedAt)}` : "—"}</dd>
        </div>

        <div>
          <dt>Publishing</dt>
          <dd>
            <span className={`val-details-mode ${modeClass}`}>
              <i
                className={`ti ${
                  isLive ? "ti-bolt" : missingSlot ? "ti-calendar-x" : "ti-calendar-clock"
                }`}
              />
              {isLive ? "Live Event" : missingSlot ? "No slot" : "Scheduled"}
            </span>
            <span className="val-details-sub">
              {isLive
                ? "Publishes immediately on approval"
                : missingSlot
                  ? "No publish slot selected"
                  : `${formatDate(slot)} at ${formatTime(slot)}`}
            </span>
            {isLive && submission.liveEventName && (
              <span className="val-details-sub">Event: {submission.liveEventName}</span>
            )}
            {submission.publishedAt && (
              <span className="val-details-sub">
                Published {formatDate(submission.publishedAt)} at {formatTime(submission.publishedAt)}
              </span>
            )}
          </dd>
        </div>

        {retryCount !== undefined && (
          <div>
            <dt>Retry attempts</dt>
            <dd>{retryCount}</dd>
          </div>
        )}

        {retryCount !== undefined && (
          <div>
            <dt>Last attempt</dt>
            <dd>{lastAttemptAt ? formatDateTime(lastAttemptAt) : "No attempts recorded"}</dd>
          </div>
        )}

        {lastError && (
          <div>
            <dt>Last error</dt>
            <dd>{humanizeFacebookError(lastError)}</dd>
          </div>
        )}

        <div>
          <dt>Edits during review</dt>
          <dd>
            {editors.length === 0 ? (
              <span className="val-details-muted">None yet</span>
            ) : (
              <ul className="val-details-editors">
                {editors.map((e) => (
                  <li key={e.email}>
                    <span>
                      {e.email}
                      {isYou(e.email) && <span className="val-details-you">You</span>}
                    </span>
                    <span className="val-details-muted">
                      {e.count} edit{e.count > 1 ? "s" : ""} · {formatDateTime(e.lastAt)}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </dd>
        </div>
      </dl>
      </div>
    </aside>
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
    ? extractHashtags(editForm.caption)
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
              <span className="val-fb-published-by" title={`Published by ${pageName}`}>
                Published by {pageName}
              </span>
              <span className="val-fb-dot">·</span>
              <span className="val-fb-schedule-text">
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
                <OptimizedImage
                  src={asset.storageUrl}
                  alt=""
                  width={72}
                  height={72}
                  sizes="72px"
                  candidateWidths={[72, 144]}
                  transform={canTransformImageType(asset.fileType)}
                />
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
                color: "var(--val-blue, #1877f2)",
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
              <strong style={{ color: "var(--val-blue, #1877f2)", textTransform: "capitalize" }}>
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
                        {editSeverityBadge(entry.editSeverity)}
                        {entry.selfReview && <span className="val-log-flag">Self-review</span>}
                        {entry.fastTrack && <span className="val-log-flag fast-track">Fast-Track</span>}
                      </strong>
                      <span className="val-log-meta">
                        {entry.validatorEmail} · {formatDateTime(entry.createdAt)}
                      </span>
                      {entry.remarks && (
                        <p className="val-log-remarks">
                          {formatRevisionRemarksForDisplay(entry.remarks)}
                        </p>
                      )}
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
          <button type="button" className="val-btn val-btn-primary" onClick={onClose}>
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
  dialogClassName,
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
  dialogClassName?: string;
  children?: ReactNode;
}) {
  return createPortal(
    <div
      className={`val-modal-overlay${exiting ? " is-closing" : ""}`}
      onClick={onCancel}
    >
      <div className={`val-modal ${dialogClassName || ""}`} onClick={(event) => event.stopPropagation()}>
        <div className="val-modal-header">
          <div className={`val-modal-icon ${tone}`}>
            <i className={`ti ${icon}`}></i>
          </div>
          <div className="val-modal-header-text">
            <h3>{title}</h3>
            <p>{body}</p>
          </div>
        </div>
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

/**
 * The stored publish error is the raw Facebook Graph API exception body
 * (`Graph API error: {"message": "...", "type": "OAuthException", "code":
 * 190, "error_subcode": 463, "fbtrace_id": "..."}`) — accurate for debugging,
 * but unreadable for a moderator deciding what to do next. Strips it down to
 * just the human message, and gives the single most common real-world case
 * (an expired/invalid Page access token, Graph API code 190) a specific,
 * actionable message instead of Facebook's own wording.
 */
function humanizeFacebookError(raw?: string | null): string {
  if (!raw) return "";
  const jsonStart = raw.indexOf("{");
  if (jsonStart < 0) return raw;
  let parsed: { message?: string; type?: string; code?: number } | null;
  try {
    parsed = JSON.parse(raw.slice(jsonStart));
  } catch {
    parsed = null;
  }
  if (!parsed?.message) return raw;
  if (parsed.type === "OAuthException" && parsed.code === 190) {
    return "The Facebook connection has expired. An Admin needs to reconnect the Page (Settings → System Health → Tokens) before this can publish.";
  }
  return parsed.message;
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

/** A10: small badge showing how prominently a moderator review-edit was logged. */
function editSeverityBadge(severity?: string | null): ReactNode {
  if (!severity) return null;
  const map: Record<string, { label: string; cls: string }> = {
    quiet: { label: "Minor edit", cls: "sev-quiet" },
    flagged: { label: "Significant edit", cls: "sev-flagged" },
    added_media: { label: "Media added", cls: "sev-added-media" },
  };
  const meta = map[severity.toLowerCase()];
  if (!meta) return null;
  return <span className={`val-log-flag ${meta.cls}`}>{meta.label}</span>;
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

import { useEffect, useMemo, useRef, useState } from "react";
import { useLocation, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { listInstitutions, type InstitutionResponse } from "../../api/authApi";
import {
  attachAsset,
  createDraft,
  deleteDraft,
  detachAsset,
  getEngagementRecommendations,
  getSubmission,
  reorderSubmissionMedia,
  submitForReview,
  updateDraft,
  uploadSubmissionMedia,
  validateGuardRails,
  withdrawSubmission,
  type GuardRailResult,
  type EngagementRecommendations,
  type SavedMediaAsset,
  type SubmissionSummary,
} from "../../api/submissionApi";
import { getMediaAsset, listMediaAlbums } from "../../api/mediaApi";
import {
  createPostTemplate,
  deletePostTemplate,
  listPostTemplates,
  type PostTemplate as ApiPostTemplate,
} from "../../api/postTemplateApi";
import {
  useSubmissionLookups,
  useSubmissions,
} from "../../hooks/useSubmissions";
import { useFacebookPreviewData } from "../../hooks/useFacebookPreviewData";
import { fileMediaKey, savedMediaKey } from "../../hooks/useMediaReorder";
import type { User } from "../../types/auth.types";
import type { SubmissionMediaItem } from "../../types/media";
import type { CaptionTone } from "../../api/aiApi";
import { useToast } from "../../context/ToastContext";
import MediaAssetsPicker from "../../components/media/MediaAssetsPicker";
import BrandedSelect from "../../components/ui/BrandedSelect";
import { useAiCaptionAssist } from "../../hooks/useAiCaptionAssist";
import AiCaptionButton from "./components/AiCaptionButton";
import AiCaptionPromptDialog from "./components/AiCaptionPromptDialog";
import AiCaptionSuggestion from "./components/AiCaptionSuggestion";
import FancyTextTool, { type FancyTextSelection } from "./components/FancyTextTool";
import SubmissionReadOnlyBody from "./components/SubmissionReadOnlyView";
import AlbumCombobox from "../../components/ui/AlbumCombobox";
import "../../styles/dasig-loader.css";

import type { CenterMode, FormState, ModalState, PendingLeaveAction, ProgressStep, QueueFilter, ReadinessTarget, SaveState } from "./types";
import { initialForm, postTemplates, statusLabels, submissionDetailsMemoryCache } from "./constants";
import {
  appendHashtagToCaption,
  CAPTION_WORD_LIMIT,
  captionTone,
  captionsForSavedIds,
  dateToInputValue,
  defaultMediaTags,
  effectiveMediaTags,
  extractHashtags,
  formatDate,
  formatRole,
  formatTimeInput,
  getDirtySignature,
  getErrorMessage,
  getOrderedLocalFiles,
  getPreviewValidation,
  getReadinessChecklist,
  getSubmissionStatusIcon,
  isConflictError,
  isDefaultInstitution,
  isDirtyDraft,
  isDraftStatus,
  isPublishedStatus,
  isPublishFailedStatus,
  matchesQueueSearch,
  mediaCaptionsFromSavedAssets,
  mediaSkipWatermarkFromSavedAssets,
  normalizeHashtagInput,
  normalizeMediaTag,
  pickerMediaKey,
  pruneMediaCaptions,
  pruneMediaFlags,
  removeHashtag,
  resolveSavedMediaCaptions,
  resolveSavedMediaOrder,
  resolveSavedMediaSkipWatermarks,
  savedAssetToPickerItem,
  shouldSyncMediaDetails,
  skipWatermarksForSavedIds,
  sortFilesByOrder,
  sortSavedAssetsByOrder,
  toPayload,
  trimToWordLimit,
  upsertSubmission,
} from "./utils";
import {
  CheckItem,
  ConfirmModal,
  DraftExitModal,
  Field,
  GuardSection,
  QueueLoadingState,
  QueueState,
  ReadinessRing,
  ReadinessSkeleton,
  SectionHead,
} from "./components/SharedPrimitives";
import { StepPanelActions, StepProgress } from "./components/StepProgress";
import { EngagementRecommendationsPanel } from "./components/EngagementRecommendationsPanel";
import { InPageFacebookPreview } from "./components/InPageFacebookPreview";
import { CalendarDateField } from "./components/CalendarDateField";
import { TimePickerField } from "./components/TimePickerField";
import { SubmissionCardMedia } from "./components/SubmissionCardMedia";

const AUTO_SAVE_DELAY_MS = 1200;

interface SubmissionScreenProps {
  user: User;
}

const templateIcons: Record<string, string> = {
  "event-announcement": "ti ti-calendar-event",
  "event-recap": "ti ti-confetti",
  "competition-call": "ti ti-trophy",
  "partner-spotlight": "ti ti-building-community",
};

type ComposerTemplate = (typeof postTemplates)[number] & {
  custom?: boolean;
  sourceSubmissionId?: string | null;
  createdAt?: string;
};

function apiTemplateToComposerTemplate(template: ApiPostTemplate): ComposerTemplate {
  return {
    id: template.id,
    name: template.name,
    target: template.target,
    category: template.category,
    tags: template.tags ?? [],
    caption: template.caption,
    custom: true,
    sourceSubmissionId: template.sourceSubmissionId ?? null,
    createdAt: template.createdAt,
  };
}

export default function SubmissionScreen({ user }: SubmissionScreenProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const { submissionId: routeSubmissionId } = useParams<{ submissionId?: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const { submissions, setSubmissions, loading, error, refresh } =
    useSubmissions();
  const {
    lookups,
    loading: lookupsLoading,
  } = useSubmissionLookups();
  const toast = useToast();
  const detailsSectionRef = useRef<HTMLElement | null>(null);
  const mediaSectionRef = useRef<HTMLElement | null>(null);
  const scheduleSectionRef = useRef<HTMLElement | null>(null);
  const eventTitleRef = useRef<HTMLInputElement | null>(null);
  const eventDateRef = useRef<HTMLDivElement | null>(null);
  const captionRef = useRef<HTMLTextAreaElement | null>(null);
  const tagsInputRef = useRef<HTMLInputElement | null>(null);
  const albumNameRef = useRef<HTMLInputElement | null>(null);
  const mediaTagsInputRef = useRef<HTMLInputElement | null>(null);
  const prefilledRef = useRef(false);
  const filterParamConsumedRef = useRef(false);
  const routedSubmissionRef = useRef<string | null>(null);
  const cleanSignatureRef = useRef(getDirtySignature(initialForm));
  const shouldPromptBeforeLeaveRef = useRef(false);
  const browserBackGuardRef = useRef(false);
  const [filter, setFilter] = useState<QueueFilter>(() => {
    const tab = new URLSearchParams(window.location.search).get("tab");
    const valid: QueueFilter[] = ["drafts", "submitted", "published", "failed", "all"];
    if (tab && (valid as string[]).includes(tab)) return tab as QueueFilter;
    return "all";
  });
  const [queueSearch, setQueueSearch] = useState("");
  const [listDetails, setListDetails] = useState<
    Record<string, { caption: string; mediaAssets: SavedMediaAsset[] }>
  >(() => ({ ...submissionDetailsMemoryCache }));
  const [form, setForm] = useState<FormState>(initialForm);
  const [pickerItems, setPickerItems] = useState<SubmissionMediaItem[]>([]);
  const [saveState, setSaveState] = useState<SaveState>("idle");
  const [modal, setModal] = useState<ModalState>(null);
  const [captionMediaKey, setCaptionMediaKey] = useState<string | null>(null);
  const [hashtagInput, setHashtagInput] = useState("");
  const [mediaTagInput, setMediaTagInput] = useState("");
  const [customTemplates, setCustomTemplates] = useState<ComposerTemplate[]>([]);
  const [templatesLoading, setTemplatesLoading] = useState(false);
  const [templateSaveOpen, setTemplateSaveOpen] = useState(false);
  const [templateName, setTemplateName] = useState("");
  const [savingTemplate, setSavingTemplate] = useState(false);
  const [pendingLeaveAction, setPendingLeaveAction] =
    useState<PendingLeaveAction>(null);
  const [centerMode, setCenterMode] = useState<CenterMode>("edit");
  const [activeMediaIndex, setActiveMediaIndex] = useState(0);
  const [reorderingMedia, setReorderingMedia] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [withdrawing, setWithdrawing] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [hydratingId, setHydratingId] = useState<string | null>(null);
  const [refreshingQueue, setRefreshingQueue] = useState(false);
  const [guardRailsLoading, setGuardRailsLoading] = useState(false);
  const [guardRails, setGuardRails] = useState<GuardRailResult | null>(null);
  const [guardRailError, setGuardRailError] = useState("");
  const [engagementRecommendations, setEngagementRecommendations] =
    useState<EngagementRecommendations | null>(null);
  const [engagementLoading, setEngagementLoading] = useState(false);
  const [institutions, setInstitutions] = useState<InstitutionResponse[]>([]);
  const [institutionsLoading, setInstitutionsLoading] = useState(false);
  const [institutionsError, setInstitutionsError] = useState("");
  const [existingAlbums, setExistingAlbums] = useState<string[]>([]);
  const [activeStep, setActiveStep] = useState<ProgressStep>("media");
  const [captionSelection, setCaptionSelection] = useState<FancyTextSelection>({
    start: 0,
    end: 0,
  });
  const [captionPromptOpen, setCaptionPromptOpen] = useState(false);
  const [fancyTextPreviewActive, setFancyTextPreviewActive] = useState(false);
  const isAdminComposer = user.role === "administrator" || user.role === "super_administrator";
  const isMySubmissionsPage = location.pathname === "/submissions";
  const selectedInstitutionId = isAdminComposer ? form.institutionId : user.institutionId || "";
  const [mediaUploadFailed, setMediaUploadFailed] = useState(false);
  const selectedPostingInstitution = useMemo(
    () => institutions.find((institution) => institution.id === form.institutionId) ?? null,
    [form.institutionId, institutions],
  );
  const selectedPostingIsDefault = Boolean(
    selectedPostingInstitution && isDefaultInstitution(selectedPostingInstitution),
  );

  const queued = useMemo(() => {
    const byFilter = (() => {
      if (filter === "drafts") return submissions.filter((item) => isDraftStatus(item.status));
      if (filter === "submitted")
        return submissions.filter((item) => !isDraftStatus(item.status) && !isPublishedStatus(item.status) && !isPublishFailedStatus(item.status));
      if (filter === "published") return submissions.filter((item) => isPublishedStatus(item.status));
      if (filter === "failed") return submissions.filter((item) => isPublishFailedStatus(item.status));
      return submissions;
    })();
    return byFilter.filter((item) => matchesQueueSearch(item, queueSearch));
  }, [filter, queueSearch, submissions]);

  const draftCount = useMemo(
    () => submissions.filter((item) => isDraftStatus(item.status)).length,
    [submissions],
  );

  const submittedCount = useMemo(
    () => submissions.filter((item) => !isDraftStatus(item.status) && !isPublishedStatus(item.status) && !isPublishFailedStatus(item.status)).length,
    [submissions],
  );

  const publishedCount = useMemo(
    () => submissions.filter((item) => isPublishedStatus(item.status)).length,
    [submissions],
  );

  const failedCount = useMemo(
    () => submissions.filter((item) => isPublishFailedStatus(item.status)).length,
    [submissions],
  );

  const scheduledAt = useMemo(() => {
    if (form.fastTrack) return undefined;
    if (!form.scheduledDate || !form.scheduledTime) return undefined;
    const date = new Date(`${form.scheduledDate}T${form.scheduledTime}`);
    if (Number.isNaN(date.getTime())) return undefined;
    return date.toISOString();
  }, [form.fastTrack, form.scheduledDate, form.scheduledTime]);

  const readiness = useMemo(
    () => getReadinessChecklist(form, scheduledAt, lookups, guardRails, guardRailsLoading),
    [form, guardRails, guardRailsLoading, lookups, scheduledAt],
  );
  const recommendedWarnings = useMemo(
    () => readiness.recommended.filter((item) => !item.pass),
    [readiness.recommended],
  );
  const hasRecommendedWarnings =
    readiness.requiredComplete === readiness.required.length &&
    recommendedWarnings.length > 0;
  const captionHashtags = useMemo(() => extractHashtags(form.caption), [form.caption]);
  const composerTemplates = useMemo<ComposerTemplate[]>(
    () => [...postTemplates, ...customTemplates],
    [customTemplates],
  );
  const captionMediaItem = useMemo(
    () => pickerItems.find((item) => pickerMediaKey(item) === captionMediaKey),
    [captionMediaKey, pickerItems],
  );
  const facebookPreview = useFacebookPreviewData({
    caption: form.caption,
    scheduledAt,
    files: form.files,
    savedAssets: form.savedAssets,
    mediaOrder: form.mediaOrder,
  });
  const previewValidation = useMemo(
    () => getPreviewValidation(form, scheduledAt, lookups, guardRails),
    [form, guardRails, lookups, scheduledAt],
  );
  const submitDisabledReason =
    previewValidation.blockingErrors.length > 0
      ? previewValidation.blockingErrors[0]
      : undefined;
  const isEditableSubmission = form.status === "draft" || form.status === "needs_revision";
  const canSubmitCurrentSubmission = isEditableSubmission;
  const isReadOnlySubmission = !isEditableSubmission;
  const canUseAiCaption = !isReadOnlySubmission;
  const hasMedia = form.files.length > 0 || form.savedAssets.length > 0;
  const isDirty = useMemo(
    () =>
      !isReadOnlySubmission &&
      isDirtyDraft(form) &&
      getDirtySignature(form) !== cleanSignatureRef.current,
    [form, isReadOnlySubmission],
  );
  const shouldPromptBeforeLeave = isDirty;
  const busy =
    saveState === "saving" || submitting || withdrawing || deleting || reorderingMedia;

  useEffect(() => {
    if (!isAdminComposer) return;
    const controller = new AbortController();
    setInstitutionsLoading(true);
    listInstitutions(controller.signal)
      .then((response) => {
        const activeInstitutions = response.data.filter(
          (institution) => institution.status?.toLowerCase() !== "inactive",
        );
        setInstitutions(activeInstitutions);
        setInstitutionsError("");
        setForm((prev) => {
          if (prev.institutionId || prev.id) return prev;
          const dasig = activeInstitutions.find(
            (inst) => isDefaultInstitution(inst),
          );
          return dasig ? { ...prev, institutionId: dasig.id } : prev;
        });
      })
      .catch((err: unknown) => {
        if ((err as { name?: string })?.name === "CanceledError") return;
        setInstitutionsError(getErrorMessage(err, "Institution list could not be loaded."));
      })
      .finally(() => setInstitutionsLoading(false));
    return () => controller.abort();
  }, [isAdminComposer]);

  useEffect(() => {
    const controller = new AbortController();
    setTemplatesLoading(true);
    listPostTemplates(controller.signal)
      .then((response) => {
        setCustomTemplates((response.data ?? []).map(apiTemplateToComposerTemplate));
      })
      .catch((err: unknown) => {
        if ((err as { name?: string })?.name === "CanceledError") return;
        toast.error("Could not load saved templates.");
      })
      .finally(() => setTemplatesLoading(false));
    return () => controller.abort();
  }, [toast]);

  useEffect(() => {
    if (!selectedInstitutionId) {
      setExistingAlbums([]);
      return;
    }

    const controller = new AbortController();
    listMediaAlbums(selectedInstitutionId, controller.signal)
      .then((response) => {
        setExistingAlbums((response.data ?? []).map((album) => album.name));
      })
      .catch((err: unknown) => {
        if ((err as { name?: string })?.name === "CanceledError") return;
        setExistingAlbums([]);
        toast.error("Could not load media albums.");
      });

    return () => controller.abort();
  }, [selectedInstitutionId, toast]);

  const isDetailsComplete = useMemo(
    () =>
      Boolean(form.eventTitle.trim()) &&
      Boolean(form.eventDate) &&
      Boolean(form.caption.trim()),
    [form.eventTitle, form.eventDate, form.caption],
  );

  async function handleStepNav(step: ProgressStep) {
    if (step === "details" && !hasMedia) {
      toast.warning("Add at least one media file before entering Post Details.");
      setActiveStep("media");
      return;
    }
    if (step === "schedule" && !hasMedia) {
      toast.warning("Add at least one media file before setting a schedule.");
      setActiveStep("media");
      return;
    }
    if (step === "schedule" && !isDetailsComplete) {
      toast.warning(
        "Complete Post Details — title, event date, and caption — before setting a schedule.",
      );
      setActiveStep("details");
      return;
    }
    if (step === "media" && isDetailsComplete && !form.id && !busy) {
      const saved = await saveDraft();
      if (!saved) return;
    }
    setActiveStep(step);
  }

  function handleReadinessJump(target: ReadinessTarget) {
    setCenterMode("edit");
    const step: ProgressStep =
      target === "media" || target === "fileRequirements" || target === "mediaCaptions"
        ? "media"
        : target === "schedule" || target === "album" || target === "mediaTags"
          ? "schedule"
          : "details";
    if (step === "details" && !hasMedia) {
      handleStepNav("details");
      return;
    }
    if (step === "schedule" && !isDetailsComplete) {
      handleStepNav("schedule");
      return;
    }
    setActiveStep(step);

    window.setTimeout(() => {
      const targetNode =
        target === "eventTitle"
          ? eventTitleRef.current
          : target === "eventDate"
            ? eventDateRef.current
            : target === "caption" || target === "captionLength"
              ? captionRef.current
              : target === "tags"
                ? tagsInputRef.current
                : target === "album"
                  ? albumNameRef.current
                  : target === "mediaTags"
                    ? mediaTagsInputRef.current
                : target === "schedule"
                  ? scheduleSectionRef.current
                  : mediaSectionRef.current;
      targetNode?.scrollIntoView({ behavior: "smooth", block: "center" });
      if (targetNode instanceof HTMLInputElement || targetNode instanceof HTMLTextAreaElement) {
        targetNode.focus();
      }
    }, 80);
  }

  useEffect(() => {
    shouldPromptBeforeLeaveRef.current = shouldPromptBeforeLeave;
  }, [shouldPromptBeforeLeave]);

  useEffect(() => {
    if (shouldPromptBeforeLeave && !browserBackGuardRef.current) {
      window.history.pushState(
        { dasigSubmissionGuard: true },
        "",
        window.location.href,
      );
      browserBackGuardRef.current = true;
    }
    if (!shouldPromptBeforeLeave) {
      browserBackGuardRef.current = false;
    }
  }, [shouldPromptBeforeLeave]);

  useEffect(() => {
    function handleBrowserBack() {
      if (!shouldPromptBeforeLeaveRef.current) return;
      window.history.pushState(
        { dasigSubmissionGuard: true },
        "",
        window.location.href,
      );
      browserBackGuardRef.current = true;
      setPendingLeaveAction(() => exitSubmission);
      setModal("draft-exit");
    }

    window.addEventListener("popstate", handleBrowserBack);
    return () => window.removeEventListener("popstate", handleBrowserBack);
  }, [navigate]);

  const progressSteps = useMemo(
    () => {
      const steps: Array<{
        id: ProgressStep;
        label: string;
        complete: boolean;
      }> = [
        {
          id: "media" as const,
          label: "Add Media",
          complete: hasMedia,
        },
        {
          id: "details" as const,
          label: "Post Details",
          complete:
            Boolean(form.eventTitle.trim()) &&
            Boolean(form.eventDate) &&
            Boolean(form.caption.trim()),
        },
      ];
      steps.push({
        id: "schedule" as const,
        label: "Organize & Schedule",
        complete: Boolean(form.albumName.trim()) && (form.fastTrack || Boolean(scheduledAt)),
      });
      return steps;
    },
    [form.albumName, form.caption, form.eventDate, form.eventTitle, form.fastTrack, hasMedia, scheduledAt],
  );

  const hasImageAssets = useMemo(
    () =>
      form.files.some((f) => f.type.startsWith("image/")) ||
      form.savedAssets.some(
        (a) => !["mp4", "mov", "webm"].includes(a.fileType),
      ),
    [form.files, form.savedAssets],
  );
  const aiCaption = useAiCaptionAssist(form.id, hasImageAssets, form.caption);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      if (!scheduledAt) {
        setGuardRails(null);
        setGuardRailError("");
        setGuardRailsLoading(false);
        return;
      }
      if (isAdminComposer && !selectedInstitutionId) {
        setGuardRails(null);
        setGuardRailError("Select an institution scope before validating a schedule.");
        setGuardRailsLoading(false);
        return;
      }

      setGuardRailsLoading(true);
      validateGuardRails(scheduledAt, selectedInstitutionId || undefined)
        .then((response) => {
          setGuardRails(response.data);
          setGuardRailError("");
        })
        .catch((err: unknown) => {
          setGuardRails(null);
          setGuardRailError(getErrorMessage(err, "Slot validation is unavailable."));
        })
        .finally(() => setGuardRailsLoading(false));
    }, scheduledAt ? 350 : 0);

    return () => window.clearTimeout(timer);
  }, [isAdminComposer, scheduledAt, selectedInstitutionId]);

  useEffect(() => {
    if (activeStep !== "schedule" || form.fastTrack || isReadOnlySubmission
        || (isAdminComposer && !selectedInstitutionId)) {
      setEngagementRecommendations(null);
      setEngagementLoading(false);
      return;
    }
    const controller = new AbortController();
    setEngagementLoading(true);
    getEngagementRecommendations(selectedInstitutionId, controller.signal)
      .then((response) => {
        setEngagementRecommendations(response.data.available ? response.data : null);
      })
      .catch((error: unknown) => {
        if ((error as { name?: string })?.name !== "CanceledError") {
          setEngagementRecommendations(null);
        }
      })
      .finally(() => setEngagementLoading(false));
    return () => controller.abort();
  }, [activeStep, form.fastTrack, isReadOnlySubmission, selectedInstitutionId]);

  // Clean up ?tab= from the URL after it has been consumed by the lazy filter initializer.
  useEffect(() => {
    if (filterParamConsumedRef.current) return;
    if (!searchParams.get("tab")) return;
    filterParamConsumedRef.current = true;
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        next.delete("tab");
        return next;
      },
      { replace: true },
    );
  }, [searchParams, setSearchParams]);

  // Consume ?assetIds= from the Media Library "New Post" action exactly once.
  useEffect(() => {
    if (prefilledRef.current) return;
    const raw = searchParams.get("assetIds");
    if (!raw) return;
    prefilledRef.current = true;

    const ids = raw.split(",").map((value) => value.trim()).filter(Boolean);
    if (ids.length === 0) return;

    void (async () => {
      const results = await Promise.allSettled(ids.map((id) => getMediaAsset(id)));
      const assets: SavedMediaAsset[] = results
        .filter((result): result is PromiseFulfilledResult<Awaited<ReturnType<typeof getMediaAsset>>> =>
          result.status === "fulfilled",
        )
        .map((result) => {
          const asset = result.value.data;
          return {
            id: asset.id,
            storageUrl: asset.storageUrl,
            fileName: asset.fileName,
            fileType: asset.fileType,
            fileSizeBytes: asset.fileSizeBytes,
          };
        });

      if (assets.length === 0) {
        toast.error("Selected media could not be loaded.");
        return;
      }

      setForm((current) => ({
        ...current,
        savedAssets: assets,
        mediaOrder: assets.map((asset) => savedMediaKey(asset.id)),
        pendingAssetIds: assets.map((asset) => asset.id),
      }));
      setPickerItems(assets.map(savedAssetToPickerItem));

      if (assets.length < ids.length) {
        toast.warning("Some selected assets could not be loaded.");
      } else {
        toast.success(
          `${assets.length} asset${assets.length > 1 ? "s" : ""} ready to attach.`,
        );
      }
    })();
  }, [searchParams, toast]);

  useEffect(() => {
    if (!isMySubmissionsPage) return;
    const needsPreview = queued.filter(
      (item) =>
        (!item.caption || ((item.mediaCount ?? 0) > 0 && !item.mediaAssets?.length)) &&
        !listDetails[item.id],
    );
    if (needsPreview.length === 0) return;

    let cancelled = false;
    void Promise.allSettled(needsPreview.map((item) => getSubmission(item.id))).then((results) => {
      if (cancelled) return;
      const nextEntries: Record<string, { caption: string; mediaAssets: SavedMediaAsset[] }> = {};
      results.forEach((result, index) => {
        const id = needsPreview[index]?.id;
        if (!id) return;
        const entry =
          result.status === "fulfilled"
            ? {
                caption: result.value.data.caption ?? "",
                mediaAssets: result.value.data.mediaAssets ?? [],
              }
            : { caption: "", mediaAssets: [] };
        nextEntries[id] = entry;
        submissionDetailsMemoryCache[id] = entry;
      });
      setListDetails((current) => ({ ...current, ...nextEntries }));
    });

    return () => {
      cancelled = true;
    };
  }, [isMySubmissionsPage, listDetails, queued]);

  useEffect(() => {
    const submissionId = routeSubmissionId ?? searchParams.get("submissionId");
    if (!submissionId || submissionId === "new") {
      routedSubmissionRef.current = null;
      return;
    }
    if (routedSubmissionRef.current === submissionId) return;
    routedSubmissionRef.current = submissionId;
    const existing = submissions.find((s) => s.id === submissionId);
    const initialStatus = existing?.status ?? "pending";
    const editableDraft = initialStatus === "draft" || initialStatus === "needs_revision";
    setFilter(editableDraft ? "drafts" : "submitted");
    setCenterMode("edit");
    if (existing) {
      setForm((current) => ({
        ...current,
        id: existing.id,
        status: existing.status,
        eventTitle: existing.eventTitle || "",
        eventDate: existing.eventDate || "",
        institutionId: existing.institutionId || current.institutionId,
      }));
    }
    void applySubmission({
      id: submissionId,
      institutionId: existing?.institutionId || user.institutionId || "",
      institutionName: existing?.institutionName || user.inst,
      eventTitle: existing?.eventTitle || "",
      eventDate: existing?.eventDate || "",
      status: initialStatus,
    });
  }, [routeSubmissionId, searchParams, submissions, user.inst, user.institutionId]);

  function clearAssetIdParam() {
    if (!searchParams.has("assetIds")) return;
    const next = new URLSearchParams(searchParams);
    next.delete("assetIds");
    setSearchParams(next, { replace: true });
  }

  async function attachPendingAssets(submissionId: string, pendingIds: string[]) {
    let latest: Awaited<ReturnType<typeof attachAsset>> | null = null;
    for (const assetId of pendingIds) {
      try {
        latest = await attachAsset(submissionId, assetId);
      } catch (err: unknown) {
        if (!isConflictError(err)) {
          toast.warning(
            getErrorMessage(err, "A selected asset could not be attached."),
          );
        }
      }
    }
    return latest;
  }

  function updateField<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((current) => {
      const next = { ...current, [key]: value };
      if (key === "eventTitle" && typeof value === "string") {
        const previousDefault = current.eventTitle.trim();
        const shouldRefreshDefaultTag =
          current.mediaTags.length === 0 ||
          (current.mediaTags.length === 1 && current.mediaTags[0] === previousDefault);
        if (shouldRefreshDefaultTag) {
          next.mediaTags = defaultMediaTags(value);
        }
      }
      return next;
    });
    setSaveState("idle");
  }

  function captureCaptionSelection(element: HTMLTextAreaElement) {
    setCaptionSelection({
      start: element.selectionStart,
      end: element.selectionEnd,
    });
  }

  function restoreCaptionSelection(nextSelection: FancyTextSelection) {
    setCaptionSelection(nextSelection);

    window.requestAnimationFrame(() => {
      const textarea = captionRef.current;
      if (!textarea) return;

      textarea.focus();
      textarea.setSelectionRange(nextSelection.start, nextSelection.end);
    });
  }

  function updateCaptionSelection(
    nextCaption: string,
    nextSelection: FancyTextSelection,
  ) {
    updateField("caption", trimToWordLimit(nextCaption));
    restoreCaptionSelection(nextSelection);
  }

  function updateCaption(nextCaption: string) {
    const limitedCaption = trimToWordLimit(nextCaption);
    if (limitedCaption !== nextCaption) {
      toast.warning(`Caption is limited to ${CAPTION_WORD_LIMIT} characters.`);
    }
    updateField("caption", limitedCaption);
  }

  async function handleAiCaptionPromptSubmit(prompt: string, tone: CaptionTone) {
    const generated = await aiCaption.suggest(prompt, tone, undefined, form.caption);
    if (generated) setCaptionPromptOpen(false);
  }

  function updateFastTrack(value: boolean) {
    if (value && !form.fastTrack && (form.scheduledDate || form.scheduledTime)) {
      setModal("fast-track-switch");
      return;
    }
    applyFastTrackMode(value);
  }

  function applyFastTrackMode(value: boolean) {
    setForm((current) => ({
      ...current,
      fastTrack: value,
      liveEventName: value ? current.liveEventName : "",
    }));
    setGuardRails(value ? null : guardRails);
    setGuardRailError(value ? "" : guardRailError);
    setSaveState("idle");
  }

  function applyEngagementSlot(value: string) {
    const slot = new Date(value);
    if (Number.isNaN(slot.getTime())) return;
    setForm((current) => ({
      ...current,
      scheduledDate: dateToInputValue(slot),
      scheduledTime: `${String(slot.getHours()).padStart(2, "0")}:${String(slot.getMinutes()).padStart(2, "0")}`,
    }));
    setSaveState("idle");
  }

  function applyTemplate(templateId: string) {
    const template = composerTemplates.find((item) => item.id === templateId);
    if (!template || isReadOnlySubmission) return;
    if (!hasMedia) {
      toast.warning("Add at least one media file before choosing a post template.");
      setActiveStep("media");
      return;
    }

    setForm((current) => ({
      ...current,
      selectedTemplateId: template.id,
      caption: template.caption,
      category: "",
      tags: [],
    }));
    setSaveState("idle");
    toast.info(`${template.name} template applied.`);
  }

  function clearTemplate() {
    if (isReadOnlySubmission) return;
    setForm((current) => ({
      ...current,
      selectedTemplateId: null,
      caption: "",
      tags: [],
    }));
    setSaveState("idle");
  }

  function openSaveTemplateModal() {
    if (isReadOnlySubmission) return;
    if (!form.caption.trim()) {
      toast.warning("Add a caption before saving this submission as a template.");
      return;
    }
    setTemplateName(form.eventTitle.trim() ? `${form.eventTitle.trim()} Template` : "My Template");
    setTemplateSaveOpen(true);
  }

  async function saveCustomTemplate() {
    const name = templateName.trim();
    if (!name) {
      toast.warning("Template name is required.");
      return;
    }
    const savedTags = Array.from(
      new Set(
        captionHashtags
          .map((tag) => tag.replace(/^#/, "").trim())
          .filter(Boolean),
      ),
    ).slice(0, 4);
    setSavingTemplate(true);
    try {
      const response = await createPostTemplate({
        name,
        target: form.eventTitle.trim()
          ? `Saved from ${form.eventTitle.trim()}`
          : "Saved from submission",
        category: form.category || "Custom",
        tags: savedTags.length > 0 ? savedTags : ["Custom"],
        caption: form.caption,
        sourceSubmissionId: form.id,
        institutionId: selectedInstitutionId || null,
      });
      const template = apiTemplateToComposerTemplate(response.data);
      setCustomTemplates((current) => [template, ...current]);
      setForm((current) => ({ ...current, selectedTemplateId: template.id }));
      setTemplateSaveOpen(false);
      toast.success("Template saved.");
    } catch (err) {
      toast.error(getErrorMessage(err, "Could not save template."));
    } finally {
      setSavingTemplate(false);
    }
  }

  async function deleteCustomTemplate(templateId: string) {
    try {
      await deletePostTemplate(templateId);
      setCustomTemplates((current) => current.filter((template) => template.id !== templateId));
      setForm((current) =>
        current.selectedTemplateId === templateId
          ? { ...current, selectedTemplateId: null }
          : current,
      );
      toast.success("Template deleted.");
    } catch (err) {
      toast.error(getErrorMessage(err, "Could not delete template."));
    }
  }

  function addHashtagToCaption() {
    if (isReadOnlySubmission) return;
    const hashtag = normalizeHashtagInput(hashtagInput);
    if (!hashtag) return;
    setForm((current) => ({
      ...current,
      caption: appendHashtagToCaption(current.caption, hashtag),
      tags: [],
    }));
    setHashtagInput("");
    setSaveState("idle");
  }

  function removeHashtagFromCaption(hashtag: string) {
    if (isReadOnlySubmission) return;
    setForm((current) => ({
      ...current,
      caption: removeHashtag(current.caption, hashtag),
      tags: [],
    }));
    setSaveState("idle");
  }

  function addMediaTag() {
    if (isReadOnlySubmission) return;
    const tag = normalizeMediaTag(mediaTagInput);
    if (!tag) return;
    setForm((current) => {
      const existing = effectiveMediaTags(current);
      if (existing.some((item) => item.toLowerCase() === tag.toLowerCase())) {
        return current;
      }
      return {
        ...current,
        mediaTags: [...existing, tag],
      };
    });
    setMediaTagInput("");
    setSaveState("idle");
  }

  function removeMediaTag(tag: string) {
    if (isReadOnlySubmission) return;
    setForm((current) => ({
      ...current,
      mediaTags: effectiveMediaTags(current).filter((item) => item !== tag),
    }));
    setSaveState("idle");
  }

  function applyAutoAlbum() {
    if (isReadOnlySubmission) return;
    updateField("albumName", form.eventTitle.trim() || form.liveEventName.trim() || "Auto-Matched Album");
  }

  function resetComposer() {
    setForm(initialForm);
    setPickerItems([]);
    setCaptionMediaKey(null);
    setHashtagInput("");
    setMediaTagInput("");
    setActiveMediaIndex(0);
    setCenterMode("edit");
    setGuardRails(null);
    setGuardRailError("");
    setSaveState("idle");
    cleanSignatureRef.current = getDirtySignature(initialForm);
    setFilter("drafts");
    setActiveStep("media");
    setMediaUploadFailed(false);
    clearAssetIdParam();
  }

  function startNewSubmission() {
    resetComposer();
    setModal(null);
  }

  function exitSubmission() {
    const returnTo = (location.state as { returnTo?: string } | null)?.returnTo;
    setModal(null);
    setPendingLeaveAction(null);
    setForm(initialForm);
    setPickerItems([]);
    setCaptionMediaKey(null);
    setHashtagInput("");
    setMediaTagInput("");
    setActiveMediaIndex(0);
    setCenterMode("edit");
    setGuardRails(null);
    setGuardRailError("");
    setSaveState("idle");
    cleanSignatureRef.current = getDirtySignature(initialForm);
    browserBackGuardRef.current = false;
    navigate(returnTo || "/submissions", { replace: true });
  }

  function resumeExistingDraft() {
    const existingDraft =
      submissions.find((item) => item.status === "draft") ||
      submissions.find((item) => item.status === "needs_revision") ||
      (form.id ? submissions.find((item) => item.id === form.id) : undefined);
    setFilter("drafts");
    setModal(null);
    if (existingDraft) {
      void applySubmission(existingDraft);
      return;
    }
    setActiveStep("media");
  }

  function requestLeave(action: () => void) {
    if (shouldPromptBeforeLeave) {
      setPendingLeaveAction(() => action);
      setModal("draft-exit");
      return;
    }
    action();
  }

  function handleBack() {
    requestLeave(exitSubmission);
  }

  async function refreshQueue() {
    if (refreshingQueue) return;
    setRefreshingQueue(true);
    try {
      await refresh();
    } finally {
      setRefreshingQueue(false);
    }
  }

  async function applySubmission(summary: SubmissionSummary) {
    setHydratingId(summary.id);
    try {
      const { data: submission } = await getSubmission(summary.id);
      const nextForm: FormState = {
        id: submission.id,
        status: submission.status,
        institutionId: submission.institutionId || "",
        selectedTemplateId: submission.templateId ?? null,
        fastTrack: Boolean(submission.fastTrack),
        liveEventName: submission.liveEventName || "",
        eventTitle: submission.eventTitle || "",
        eventDate: submission.eventDate || "",
        caption: submission.caption || "",
        description: "",
        category: "",
        scheduledDate: submission.scheduledAt
          ? submission.scheduledAt.slice(0, 10)
          : "",
        scheduledTime: submission.scheduledAt
          ? formatTimeInput(submission.scheduledAt)
          : "",
        tags: [],
        albumName: submission.albumName || "",
        mediaTags: submission.mediaTags ?? defaultMediaTags(submission.eventTitle || ""),
        files: [],
        savedAssets: submission.mediaAssets ?? [],
        mediaOrder: (submission.mediaAssets ?? []).map((asset) =>
          savedMediaKey(asset.id),
        ),
        mediaCaptions: Object.fromEntries(
          (submission.mediaAssets ?? []).map((asset) => [
            savedMediaKey(asset.id),
            asset.caption ?? "",
          ]),
        ),
        mediaSkipWatermark: mediaSkipWatermarkFromSavedAssets(submission.mediaAssets ?? []),
        pendingAssetIds: [],
        removedAssetIds: [],
      };
      setForm(nextForm);
      setPickerItems((submission.mediaAssets ?? []).map(savedAssetToPickerItem));
      setCaptionMediaKey(null);
      setHashtagInput("");
      setMediaTagInput("");
      setActiveMediaIndex(0);
      const editableDraft = submission.status === "draft" || submission.status === "needs_revision";
      setFilter(editableDraft ? "drafts" : "submitted");
      setActiveStep(editableDraft ? "media" : "details");
      setCenterMode("edit");
      setSaveState("saved");
      setMediaUploadFailed(false);
      cleanSignatureRef.current = getDirtySignature(nextForm);
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } }).response?.status;
      if (status === 403) {
        toast.error(
          getErrorMessage(err, "You do not have access to this submission."),
        );
      } else if (status === 404) {
        toast.error("This submission no longer exists.");
      } else {
        toast.error(getErrorMessage(err, "Could not load submission detail."));
      }
      // Don't strand the user on a half-populated editor — return to the list.
      exitSubmission();
    } finally {
      setHydratingId(null);
    }
  }

  async function saveDraft(options: { silent?: boolean } = {}) {
    if (isReadOnlySubmission) return false;
    if (busy) return false;
    if (isAdminComposer && !form.institutionId) {
      if (!options.silent) {
        toast.error("Select an institution scope before saving this draft.");
      }
      setActiveStep("details");
      return false;
    }
    setSaveState("saving");
    try {
      const payload = toPayload(form, scheduledAt);
      const response = form.id
        ? await updateDraft(form.id, payload)
        : await createDraft(payload);
      let finalResponse = response;
      for (const assetId of form.removedAssetIds) {
        await detachAsset(response.data.id, assetId).catch(() => undefined);
      }
      if (form.pendingAssetIds.length > 0) {
        const attached = await attachPendingAssets(
          response.data.id,
          form.pendingAssetIds,
        );
        if (attached) finalResponse = attached;
      }
      if (form.files.length > 0) {
        const uploadResult = await uploadLocalFilesSafely(
          finalResponse.data.id,
          getOrderedLocalFiles(form),
          finalResponse,
        );
        finalResponse = uploadResult as typeof response;
      }
      const savedAssets = finalResponse.data.mediaAssets ?? [];
      const orderedAssetIds = resolveSavedMediaOrder(form, savedAssets);
      const mediaCaptions = resolveSavedMediaCaptions(form, savedAssets, orderedAssetIds);
      const skipWatermarks = resolveSavedMediaSkipWatermarks(form, savedAssets, orderedAssetIds);
      if (orderedAssetIds.length === savedAssets.length && shouldSyncMediaDetails(savedAssets, mediaCaptions, skipWatermarks)) {
        finalResponse = await reorderSubmissionMedia(
          finalResponse.data.id,
          orderedAssetIds,
          mediaCaptions,
          skipWatermarks,
        );
      }
      const orderedSavedAssets = finalResponse.data.mediaAssets ?? savedAssets;
      const nextForm: FormState = {
        ...form,
        id: finalResponse.data.id,
        status: finalResponse.data.status,
        files: [],
        savedAssets: orderedSavedAssets,
        mediaOrder: orderedSavedAssets.map((asset) => savedMediaKey(asset.id)),
        mediaCaptions: mediaCaptionsFromSavedAssets(orderedSavedAssets),
        mediaSkipWatermark: mediaSkipWatermarkFromSavedAssets(orderedSavedAssets),
        pendingAssetIds: [],
      };
      setForm((current) => ({
        ...current,
        id: finalResponse.data.id,
        status: finalResponse.data.status,
        files: [],
        savedAssets: orderedSavedAssets,
        mediaOrder: orderedSavedAssets.map((asset) => savedMediaKey(asset.id)),
        mediaCaptions: mediaCaptionsFromSavedAssets(orderedSavedAssets),
        mediaSkipWatermark: mediaSkipWatermarkFromSavedAssets(orderedSavedAssets),
        pendingAssetIds: [],
        removedAssetIds: [],
      }));
      setPickerItems(orderedSavedAssets.map(savedAssetToPickerItem));
      setCaptionMediaKey(null);
      setSubmissions((current) =>
        upsertSubmission(current, finalResponse.data),
      );
      clearAssetIdParam();
      setSaveState("saved");
      setMediaUploadFailed(false);
      cleanSignatureRef.current = getDirtySignature(nextForm);
      if (!options.silent) toast.success("Draft saved.");
      return true;
    } catch (err: unknown) {
      setSaveState("idle");
      if (form.files.length > 0) setMediaUploadFailed(true);
      if (!options.silent) {
        toast.error(getErrorMessage(err, "Draft could not be saved."));
      }
      return false;
    }
  }

  useEffect(() => {
    if (!isDirty || busy || fancyTextPreviewActive) return;
    // Never auto-create a draft. The first save must be explicit (Save Draft /
    // Submit / advancing to the Media step); autosave only persists edits to a
    // draft that already exists.
    if (!form.id) return;
    if (isAdminComposer && !form.institutionId) return;

    const timer = window.setTimeout(() => {
      void saveDraft({ silent: true });
    }, AUTO_SAVE_DELAY_MS);

    return () => window.clearTimeout(timer);
  }, [busy, fancyTextPreviewActive, form, isAdminComposer, isDirty, scheduledAt]);

  async function handleSave() {
    await saveDraft();
  }

  async function handleSaveDraftAndExit() {
    const saved = await saveDraft();
    if (saved) {
      const leave = pendingLeaveAction;
      setPendingLeaveAction(null);
      setModal(null);
      if (leave) {
        leave();
      } else {
        exitSubmission();
      }
    }
  }

  async function handleSubmit() {
    if (isReadOnlySubmission || busy) return;
    const missing: string[] = [];
    if (isAdminComposer && !form.institutionId) missing.push("an institution scope");
    if (!form.eventTitle.trim()) missing.push("an event title");
    if (!form.eventDate) missing.push("an event date");
    if (!form.caption.trim()) missing.push("a caption");
    if (!hasMedia) missing.push("at least one media attachment");
    if (!form.albumName.trim()) missing.push("an album assignment");
    if (!form.fastTrack && !scheduledAt) missing.push("a preferred schedule");
    if (missing.length > 0) {
      toast.error(`Add ${missing.join(", ")} before submitting.`);
      if ((isAdminComposer && !form.institutionId) || !form.eventTitle.trim() || !form.eventDate || !form.caption.trim()) {
        setActiveStep("details");
      } else if (!hasMedia) {
        setActiveStep("media");
      } else {
        setActiveStep("schedule");
      }
      setModal(null);
      return;
    }

    setSubmitting(true);
    try {
      const payload = toPayload(form, scheduledAt);
      const draft = form.id
        ? await updateDraft(form.id, payload)
        : await createDraft(payload);
      let draftResponse = draft;
      if (form.pendingAssetIds.length > 0) {
        const attached = await attachPendingAssets(
          draft.data.id,
          form.pendingAssetIds,
        );
        if (attached) draftResponse = attached;
      }
      if (form.files.length > 0) {
        try {
          const uploadResult = await uploadLocalFilesSafely(
            draftResponse.data.id,
            getOrderedLocalFiles(form),
            draftResponse,
          );
          draftResponse = uploadResult as typeof draft;
        } catch (uploadErr: unknown) {
          toast.error(
            "Media upload failed. Your draft was preserved; use Save Draft or Submit to retry. " +
              getErrorMessage(uploadErr, "Check your connection and try again."),
          );
          setMediaUploadFailed(true);
          setActiveStep("media");
          return;
        }
      }
      const savedAssets = draftResponse.data.mediaAssets ?? [];
      const orderedAssetIds = resolveSavedMediaOrder(form, savedAssets);
      const mediaCaptions = resolveSavedMediaCaptions(form, savedAssets, orderedAssetIds);
      const skipWatermarks = resolveSavedMediaSkipWatermarks(form, savedAssets, orderedAssetIds);
      if (orderedAssetIds.length === savedAssets.length && shouldSyncMediaDetails(savedAssets, mediaCaptions, skipWatermarks)) {
        draftResponse = await reorderSubmissionMedia(
          draftResponse.data.id,
          orderedAssetIds,
          mediaCaptions,
          skipWatermarks,
        );
      }
      const submitted = await submitForReview(draftResponse.data.id);
      setSubmissions((current) => upsertSubmission(current, submitted.data));
      const submittedAssets = submitted.data.mediaAssets ?? form.savedAssets;
      setForm((current) => ({
        ...current,
        id: submitted.data.id,
        status: submitted.data.status,
        files: [],
        savedAssets: submittedAssets,
        mediaOrder: submittedAssets.map((asset) => savedMediaKey(asset.id)),
        mediaCaptions: mediaCaptionsFromSavedAssets(submittedAssets),
        mediaSkipWatermark: mediaSkipWatermarkFromSavedAssets(submittedAssets),
        pendingAssetIds: [],
        removedAssetIds: [],
      }));
      setPickerItems(submittedAssets.map(savedAssetToPickerItem));
      setCaptionMediaKey(null);
      clearAssetIdParam();
      setModal("success");
      toast.success("Submission sent for approval.");
      void refresh();
    } catch (err: unknown) {
      toast.error(getErrorMessage(err, "Submission failed."));
    } finally {
      setSubmitting(false);
    }
  }

  async function uploadLocalFilesSafely<T extends { data: { mediaAssets?: SavedMediaAsset[] } }>(
    submissionId: string,
    files: File[],
    initialResponse: T,
  ): Promise<T> {
    let latestResponse = initialResponse;

    for (let index = 0; index < files.length; index += 1) {
      try {
        const uploaded = await uploadSubmissionMedia(submissionId, [files[index]]);
        if (uploaded) latestResponse = uploaded as unknown as T;
      } catch (error) {
        const remainingFiles = files.slice(index);
        const savedAssets = latestResponse.data.mediaAssets ?? form.savedAssets;
        setForm((current) => ({
          ...current,
          id: submissionId,
          files: remainingFiles,
          savedAssets,
          mediaOrder: [
            ...savedAssets.map((asset) => savedMediaKey(asset.id)),
            ...remainingFiles.map(fileMediaKey),
          ],
          pendingAssetIds: [],
          removedAssetIds: [],
        }));
        setPickerItems((current) => [
          ...savedAssets.map(savedAssetToPickerItem),
          ...current.filter(
            (item) => item.file != null && remainingFiles.includes(item.file),
          ),
        ]);
        throw error;
      }
    }

    return latestResponse;
  }

  async function handleDelete() {
    if (isReadOnlySubmission || busy) return;
    setDeleting(true);
    if (!form.id) {
      setModal(null);
      setDeleting(false);
      exitSubmission();
      return;
    }

    try {
      await deleteDraft(form.id);
      setSubmissions((current) =>
        current.filter((item) => item.id !== form.id),
      );
      setModal(null);
      toast.info("Draft deleted.");
      exitSubmission();
    } catch (err: unknown) {
      toast.error(getErrorMessage(err, "Draft could not be deleted."));
    } finally {
      setDeleting(false);
    }
  }

  async function handleWithdraw() {
    if (form.status !== "pending" || !form.id || busy) return;
    setWithdrawing(true);
    try {
      const { data } = await withdrawSubmission(form.id);
      setSubmissions((current) => upsertSubmission(current, data));
      const nextAssets = data.mediaAssets ?? form.savedAssets;
      setForm((current) => ({
        ...current,
        status: data.status,
        savedAssets: nextAssets,
        mediaOrder: nextAssets.map((asset) => savedMediaKey(asset.id)),
        mediaCaptions: mediaCaptionsFromSavedAssets(nextAssets),
      }));
      setPickerItems(nextAssets.map(savedAssetToPickerItem));
      setCaptionMediaKey(null);
      setFilter("drafts");
      setModal(null);
      toast.success("Submission withdrawn to draft.");
      void refresh();
    } catch (err: unknown) {
      toast.error(getErrorMessage(err, "Submission could not be withdrawn."));
    } finally {
      setWithdrawing(false);
    }
  }

  async function handleDiscardDraftAndExit() {
    const leave = pendingLeaveAction;
    setPendingLeaveAction(null);
    setModal(null);
    if (leave) {
      leave();
    } else {
      exitSubmission();
    }
  }

  function handleContinueEditing() {
    setPendingLeaveAction(null);
    setModal(null);
  }

  function handlePickerChange(items: SubmissionMediaItem[]) {
    setPickerItems(items);
    if (captionMediaKey && !items.some((item) => pickerMediaKey(item) === captionMediaKey)) {
      setCaptionMediaKey(null);
    }
    const currentSavedIds = new Set(form.savedAssets.map((a) => a.id));
    const newFiles = items.filter((i) => i.source === "upload" && i.file).map((i) => i.file!);
    const newSavedAssets: SavedMediaAsset[] = items
      .filter((i) => i.assetId)
      .map((i) => {
        const existing = form.savedAssets.find((a) => a.id === i.assetId);
        return existing ?? {
          id: i.assetId!,
          storageUrl: i.previewUrl,
          fileName: i.fileName,
          fileType: i.mediaType === "video" ? "mp4" : (i.fileName.split(".").pop()?.toLowerCase() ?? "jpg"),
          fileSizeBytes: 0,
        };
      });
    const newSavedIds = new Set(newSavedAssets.map((a) => a.id));
    const justRemoved = form.savedAssets
      .filter((a) => currentSavedIds.has(a.id) && !newSavedIds.has(a.id))
      .map((a) => a.id);
    const newPendingAssetIds = items
      .filter((i) => i.assetId && !currentSavedIds.has(i.assetId))
      .map((i) => i.assetId!);
    const newMediaOrder = items.map((i) =>
      i.assetId ? savedMediaKey(i.assetId) : i.file ? fileMediaKey(i.file) : i.clientId,
    );
    setForm((current) => ({
      ...current,
      files: newFiles,
      savedAssets: newSavedAssets,
      pendingAssetIds: newPendingAssetIds,
      removedAssetIds: [...new Set([...current.removedAssetIds, ...justRemoved])],
      mediaOrder: newMediaOrder,
      mediaCaptions: pruneMediaCaptions(current.mediaCaptions, newMediaOrder),
      mediaSkipWatermark: pruneMediaFlags(current.mediaSkipWatermark, newMediaOrder),
    }));
    setSaveState("idle");
  }

  function updateMediaCaption(mediaKey: string, caption: string) {
    setForm((current) => ({
      ...current,
      mediaCaptions: {
        ...current.mediaCaptions,
        [mediaKey]: caption,
      },
    }));
    setSaveState("idle");
  }

  function updateMediaSkipWatermark(mediaKey: string, skipWatermark: boolean) {
    setForm((current) => ({
      ...current,
      mediaSkipWatermark: {
        ...current.mediaSkipWatermark,
        [mediaKey]: skipWatermark,
      },
    }));
    setSaveState("idle");
  }

  function openMediaCaption(item: SubmissionMediaItem) {
    setCaptionMediaKey(pickerMediaKey(item));
  }

  async function handleReorderMedia(orderedIds: string[]) {
    if (isReadOnlySubmission) return;
    const sortedSavedAssets = sortSavedAssetsByOrder(form.savedAssets, orderedIds);
    const sortedFiles = sortFilesByOrder(form.files, orderedIds);
    setForm((current) => ({
      ...current,
      savedAssets: sortedSavedAssets,
      files: sortedFiles,
      mediaOrder: orderedIds,
    }));
    setSaveState("idle");

    // Sync picker items to match the new order
    const orderMap = new Map(orderedIds.map((id, i) => [id, i]));
    setPickerItems((prev) =>
      [...prev].sort((a, b) => {
        const aKey = a.assetId ? savedMediaKey(a.assetId) : a.file ? fileMediaKey(a.file) : a.clientId;
        const bKey = b.assetId ? savedMediaKey(b.assetId) : b.file ? fileMediaKey(b.file) : b.clientId;
        return (orderMap.get(aKey) ?? 999) - (orderMap.get(bKey) ?? 999);
      }),
    );

    if (!form.id || sortedFiles.length > 0 || sortedSavedAssets.length <= 1) {
      return;
    }

    setReorderingMedia(true);
    try {
      const savedIds = orderedIds
        .filter((id) => id.startsWith("saved:"))
        .map((id) => id.replace("saved:", ""));
      const captions = captionsForSavedIds(form.mediaCaptions, savedIds);
      const skipWatermarks = skipWatermarksForSavedIds(form.mediaSkipWatermark, savedIds);
      const { data } = await reorderSubmissionMedia(form.id, savedIds, captions, skipWatermarks);
      const nextAssets = data.mediaAssets ?? sortedSavedAssets;
      const nextForm: FormState = {
        ...form,
        savedAssets: nextAssets,
        files: [],
        mediaOrder: nextAssets.map((asset) => savedMediaKey(asset.id)),
        mediaCaptions: mediaCaptionsFromSavedAssets(nextAssets),
        mediaSkipWatermark: mediaSkipWatermarkFromSavedAssets(nextAssets),
      };
      setForm((current) => ({
        ...current,
        savedAssets: nextAssets,
        mediaOrder: nextAssets.map((asset) => savedMediaKey(asset.id)),
        mediaCaptions: mediaCaptionsFromSavedAssets(nextAssets),
        mediaSkipWatermark: mediaSkipWatermarkFromSavedAssets(nextAssets),
      }));
      setPickerItems(nextAssets.map(savedAssetToPickerItem));
      setSubmissions((current) => upsertSubmission(current, data));
      toast.success("Media order updated.");
      cleanSignatureRef.current = getDirtySignature(nextForm);
    } catch (err: unknown) {
      toast.error(getErrorMessage(err, "Media order could not be saved."));
    } finally {
      setReorderingMedia(false);
    }
  }

  function handleEditPreviewDetails() {
    const nextStep: ProgressStep = hasMedia ? "details" : "media";
    const nextRef = hasMedia ? detailsSectionRef : mediaSectionRef;
    setCenterMode("edit");
    setActiveStep(nextStep);
    window.setTimeout(() => {
      nextRef.current?.scrollIntoView({
        behavior: "smooth",
        block: "start",
      });
    }, 0);
  }

  if (isMySubmissionsPage) {
    return (
      <div className="submission-screen sub-list-shell-page">
        <main className="sub-list-page">
          <section className="sub-list-head">
            <div>
              <h1 className="sub-list-title">My Submissions</h1>
              <p className="sub-list-subtitle">
                View drafts, submitted posts, and published content before opening the composer.
              </p>
            </div>
            <div className="sub-list-actions">
              <button
                className="sub-btn-ghost"
                type="button"
                onClick={() => void refreshQueue()}
                disabled={refreshingQueue || loading}
              >
                {refreshingQueue || loading ? (
                  <i className="ti ti-loader-2 sub-spin"></i>
                ) : (
                  <i className="ti ti-refresh"></i>
                )}
              </button>
              <button
                className="sub-list-new"
                type="button"
                onClick={() => navigate("/submissions/new")}
              >
                <i className="ti ti-plus"></i>
                New Submission
              </button>
            </div>
          </section>

          <div className="sub-toolbar-card" style={{ marginBottom: "16px" }}>
            <div className="sub-registry-toolbar">
              <div className="sub-status-tabs" role="group" aria-label="Filter submissions by status">
                <button
                  type="button"
                  className={`sub-status-tab${filter === "all" ? " is-active" : ""}`}
                  onClick={() => setFilter("all")}
                  aria-pressed={filter === "all"}
                >
                  All
                  <span className="sub-status-tab-count">{loading ? "-" : submissions.length}</span>
                </button>
                <button
                  type="button"
                  className={`sub-status-tab${filter === "drafts" ? " is-active" : ""}`}
                  onClick={() => setFilter("drafts")}
                  aria-pressed={filter === "drafts"}
                >
                  Drafts
                  <span className="sub-status-tab-count">{loading ? "-" : draftCount}</span>
                </button>
                <button
                  type="button"
                  className={`sub-status-tab${filter === "submitted" ? " is-active" : ""}`}
                  onClick={() => setFilter("submitted")}
                  aria-pressed={filter === "submitted"}
                >
                  Submitted
                  <span className="sub-status-tab-count">{loading ? "-" : submittedCount}</span>
                </button>
                <button
                  type="button"
                  className={`sub-status-tab${filter === "published" ? " is-active" : ""}`}
                  onClick={() => setFilter("published")}
                  aria-pressed={filter === "published"}
                >
                  Published
                  <span className="sub-status-tab-count">{loading ? "-" : publishedCount}</span>
                </button>
                <button
                  type="button"
                  className={`sub-status-tab${filter === "failed" ? " is-active" : ""}`}
                  onClick={() => setFilter("failed")}
                  aria-pressed={filter === "failed"}
                >
                  Publish Failed
                  <span className="sub-status-tab-count">{loading ? "-" : failedCount}</span>
                </button>
              </div>

              <div className="sub-search-wrap">
                <i className="ti ti-search sub-search-icon" aria-hidden="true"></i>
                <input
                  type="search"
                  className="sub-search-input"
                  value={queueSearch}
                  onChange={(event) => setQueueSearch(event.target.value)}
                  placeholder="Search submissions..."
                  aria-label="Search submissions"
                />
              </div>
            </div>
          </div>

          <section className="sub-list-results" aria-label="My submissions">
            {loading || refreshingQueue ? (
              <QueueLoadingState />
            ) : error ? (
              <QueueState
                icon="ti-database-off"
                title="Unable to load submissions"
                description="Check your session and backend connection, then refresh the page."
              />
            ) : queued.length === 0 ? (
              <QueueState
                icon="ti-folder-open"
                title="No submissions found"
                description="Try another filter or create a new submission."
              />
            ) : (
              queued.map((item) => {
                const detail = listDetails[item.id];
                const mediaAssets =
                  item.mediaAssets?.length ? item.mediaAssets : detail?.mediaAssets ?? [];
                const thumbnail = mediaAssets[0];
                const captionPreview = item.caption || detail?.caption || "";
                return (
                  <article
                    className="sub-fb-post-card"
                    key={item.id}
                    onClick={() => navigate(`/submissions/${item.id}`)}
                    role="button"
                    tabIndex={0}
                    onKeyDown={(e) => {
                      if (e.key === "Enter" || e.key === " ") {
                        e.preventDefault();
                        navigate(`/submissions/${item.id}`);
                      }
                    }}
                  >
                    {/* Header: FB Brand Avatar + Page Info + Status Badge */}
                    <div className="sub-fb-card-head">
                      <div className="sub-fb-avatar" aria-hidden="true">
                        <i className="ti ti-brand-facebook"></i>
                      </div>
                      <div className="sub-fb-author">
                        <div className="sub-fb-author-name">
                          {item.institutionName || user.inst || "DASIGCONNECT"}
                        </div>
                        <div className="sub-fb-author-meta">
                          <span>{formatDate(item.eventDate)}</span>
                          <span className="sub-fb-dot" aria-hidden="true">•</span>
                          <i className="ti ti-world" title="Public post" aria-hidden="true"></i>
                        </div>
                      </div>
                      <div className="sub-fb-status-wrap">
                        <span className={`sub-qi-badge status-${item.status}`}>
                          <i className={getSubmissionStatusIcon(item.status)} aria-hidden="true"></i>
                          {statusLabels[item.status]}
                        </span>
                      </div>
                    </div>

                    {/* Post Content: Event Title & Caption */}
                    <div className="sub-fb-card-content">
                      {item.eventTitle && <h2 className="sub-fb-event-title">{item.eventTitle}</h2>}
                      {captionPreview ? (
                        <p className="sub-fb-caption-text">{captionPreview}</p>
                      ) : (
                        <p className="sub-fb-caption-text sub-fb-empty-text">No caption provided.</p>
                      )}
                    </div>

                    {/* Media Container with Circular Loader */}
                    <SubmissionCardMedia
                      thumbnail={thumbnail}
                      mediaCount={item.mediaCount}
                      detailsLoaded={Boolean(listDetails[item.id])}
                    />

                    {/* Reactions & Engagement Row */}
                    <div className="sub-fb-reactions-bar">
                      <div className="sub-fb-reactions-icons">
                        <span className="sub-fb-react-icon fb-like-icon" title="Like">
                          <i className="ti ti-thumb-up-filled"></i>
                        </span>
                        <span className="sub-fb-react-icon fb-heart-icon" title="Love">
                          <i className="ti ti-heart-filled"></i>
                        </span>
                        <span className="sub-fb-reactions-text">
                          {(item.mediaCount ?? 0)} media · {item.eventTitle ? "1 Post" : "Draft"}
                        </span>
                      </div>
                      <div className="sub-fb-open-action">
                        <span>Open details</span>
                        <i className="ti ti-chevron-right"></i>
                      </div>
                    </div>

                    {/* Facebook Interactive Bar */}
                    <div className="sub-fb-actions-bar" aria-hidden="true">
                      <div className="sub-fb-action-btn">
                        <i className="ti ti-thumb-up"></i>
                        <span>Like</span>
                      </div>
                      <div className="sub-fb-action-btn">
                        <i className="ti ti-message-circle"></i>
                        <span>Comment</span>
                      </div>
                      <div className="sub-fb-action-btn">
                        <i className="ti ti-share-3"></i>
                        <span>Share</span>
                      </div>
                    </div>
                  </article>
                );
              })
            )}
          </section>
        </main>
      </div>
    );
  }

  if (
    routeSubmissionId &&
    routeSubmissionId !== "new" &&
    (!form.id || form.id !== routeSubmissionId || hydratingId === routeSubmissionId)
  ) {
    return (
      <div className="submission-screen">
        <nav className="sub-topnav">
          <div className="sub-nav-left">
            <button
              className="sub-back-btn"
              type="button"
              onClick={handleBack}
              aria-label="Back to My Submissions"
            >
              <i className="ti ti-arrow-left"></i>
              <span>Back to My Submissions</span>
            </button>
          </div>
        </nav>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "center", minHeight: "65vh" }}>
          <QueueLoadingState />
        </div>
      </div>
    );
  }

  return (
    <div className="submission-screen">
      <nav className="sub-topnav">
        <div className="sub-nav-left">
          <button
            className="sub-back-btn"
            type="button"
            onPointerDown={(event) => {
              event.stopPropagation();
            }}
            onClick={handleBack}
            aria-label="Back to My Submissions"
          >
            <i className="ti ti-arrow-left"></i>
            <span>Back to My Submissions</span>
          </button>
        </div>
        <div className="sub-nav-right">
          {(isDirty || saveState === "saving" || saveState === "saved") && (
            <div
              className={`sub-nav-save-status ${saveState === "saved" && !isDirty ? "saved" : ""}`}
            >
              <i
                className={
                  saveState === "saving"
                    ? "ti ti-loader-2 sub-spin"
                    : saveState === "saved" && !isDirty
                      ? "ti ti-cloud-check"
                      : "ti ti-cloud"
                }
              ></i>
              {saveState === "saving"
                ? "Saving..."
                : saveState === "saved" && !isDirty
                  ? "Draft saved"
                  : "Unsaved draft"}
            </div>
          )}
          <div className="sub-nav-chip">{formatRole(user.role)}</div>
          <div className="sub-nav-avatar">{user.initials}</div>
        </div>
      </nav>

      {(loading || lookupsLoading || hydratingId) && (
        <div className="sub-route-loader" aria-hidden="true">
          <span></span>
        </div>
      )}

      <div className={`sub-workspace${isReadOnlySubmission ? " is-readonly" : ""}`}>
        {!isReadOnlySubmission && (
        <aside className="sub-sidebar sub-template-sidebar">
          {!form.fastTrack && (
            <section className="sub-sidebar-templates" aria-label="Post templates">
              <div className="sub-sidebar-template-head">
                <div>
                  <div className="sub-sidebar-section-title">Post Templates</div>
                  <div className="sub-sidebar-section-subtitle">
                    {hasMedia
                      ? "Insert a baseline caption structure."
                      : "Add media first to unlock templates."}
                  </div>
                </div>
                {form.selectedTemplateId && (
                  <div className="sub-sidebar-template-actions">
                    <button
                      className="sub-sidebar-template-clear"
                      type="button"
                      disabled={isReadOnlySubmission}
                      onClick={clearTemplate}
                    >
                      Clear
                    </button>
                  </div>
                )}
              </div>
              {templatesLoading && (
                <div className="sub-sidebar-template-loading">
                  <i className="ti ti-loader-2 sub-spin" aria-hidden="true" />
                  Loading saved templates
                </div>
              )}
              <div className="sub-sidebar-template-list">
                {composerTemplates.map((template) => (
                  <button
                    key={template.id}
                    type="button"
                    className={`sub-sidebar-template-card ${
                      form.selectedTemplateId === template.id ? "active" : ""
                    }`}
                    disabled={isReadOnlySubmission || !hasMedia}
                    title={!hasMedia ? "Add media first before choosing a template." : undefined}
                    onClick={() => applyTemplate(template.id)}
                  >
                    <span className="sub-sidebar-template-main">
                      <span className="sub-sidebar-template-icon" aria-hidden="true">
                        <i className={templateIcons[template.id] ?? "ti ti-template"} />
                      </span>
                      <span className="sub-sidebar-template-copy">
                        <span className="sub-sidebar-template-name">{template.name}</span>
                        <span className="sub-sidebar-template-target">{template.target}</span>
                      </span>
                      {template.custom && (
                        <span
                          role="button"
                          tabIndex={0}
                      className="sub-sidebar-template-delete"
                          aria-label={`Delete ${template.name} template`}
                          title="Delete template"
                          onClick={(event) => {
                            event.stopPropagation();
                            void deleteCustomTemplate(template.id);
                          }}
                          onKeyDown={(event) => {
                            if (event.key !== "Enter" && event.key !== " ") return;
                            event.preventDefault();
                            event.stopPropagation();
                            void deleteCustomTemplate(template.id);
                          }}
                        >
                          <i className="ti ti-trash" aria-hidden="true" />
                        </span>
                      )}
                    </span>
                    <span className="sub-sidebar-template-preview">
                      {template.caption}
                    </span>
                    <span className="sub-sidebar-template-tags">
                      {template.tags.slice(0, 3).map((tag) => (
                        <span key={tag}>{tag}</span>
                      ))}
                    </span>
                  </button>
                ))}
              </div>
              <div className="sub-sidebar-template-footer">
                <button
                  className="sub-sidebar-template-save"
                  type="button"
                  disabled={isReadOnlySubmission || savingTemplate || !form.caption.trim()}
                  title={!form.caption.trim() ? "Add a caption before saving as a template." : undefined}
                  onClick={openSaveTemplateModal}
                >
                  <i className="ti ti-plus" aria-hidden="true" />
                  Save as Template
                </button>
              </div>
            </section>
          )}
        </aside>
        )}

        <main className={`sub-form-canvas${isReadOnlySubmission ? " sub-ro" : ""}`}>
          <div className="sub-form-page-head">
            <div>
              <h1 className="sub-form-page-title">
                {centerMode === "preview"
                  ? "Facebook Preview"
                  : isReadOnlySubmission
                    ? `${statusLabels[form.status]} submission`
                    : "Submit Content"}
              </h1>
              {!isReadOnlySubmission && (
                <p className="sub-form-page-sub">
                  {centerMode === "preview"
                    ? "Review how followers will see this post before sending it for approval."
                    : "Prepare event media, caption, tags, and a preferred publishing slot."}
                </p>
              )}
              {isReadOnlySubmission && (
                <div className="sub-readonly-note">
                  <i className="ti ti-eye"></i>
                  Read-only — this submission can no longer be edited
                </div>
              )}
            </div>
            {!isReadOnlySubmission && (
            <div className="sub-form-page-actions">
              {centerMode === "preview" ? (
                <button
                  className="sub-btn-ghost"
                  type="button"
                  onClick={handleEditPreviewDetails}
                  disabled={busy || Boolean(hydratingId)}
                >
                  <i className="ti ti-arrow-left"></i> Back to Editing
                </button>
              ) : (
                <button
                  className="sub-btn-ghost preview"
                  type="button"
                  onClick={() => setCenterMode("preview")}
                  disabled={busy || Boolean(hydratingId)}
                >
                  <i className="ti ti-brand-facebook"></i> Preview
                </button>
              )}
              {form.status === "draft" && (
                <button
                  className="sub-btn-ghost danger"
                  type="button"
                  onClick={() => setModal("delete")}
                  disabled={busy || Boolean(hydratingId)}
                >
                  {deleting ? <i className="ti ti-loader-2 sub-spin"></i> : <i className="ti ti-trash"></i>} Delete
                </button>
              )}
              {isDirty && (
                <button
                  className="sub-btn-ghost save"
                  type="button"
                  onClick={() => void handleSave()}
                  disabled={busy || Boolean(hydratingId)}
                >
                  {saveState === "saving" ? <i className="ti ti-loader-2 sub-spin"></i> : <i className="ti ti-device-floppy"></i>} Save Draft
                </button>
              )}
              <button
                className="sub-btn-primary"
                type="button"
                onClick={() => setModal("submit")}
                disabled={busy || Boolean(hydratingId) || previewValidation.blockingErrors.length > 0}
                title={previewValidation.blockingErrors[0]}
              >
                {submitting ? <i className="ti ti-loader-2 sub-spin"></i> : <i className="ti ti-send"></i>} Submit for Approval
              </button>
            </div>
            )}
            {isReadOnlySubmission && form.status === "pending" && (
              <div className="sub-form-page-actions">
                <button
                  className="sub-btn-ghost"
                  type="button"
                  onClick={() => setModal("withdraw")}
                  disabled={busy || Boolean(hydratingId)}
                >
                  {withdrawing ? <i className="ti ti-loader-2 sub-spin"></i> : <i className="ti ti-arrow-back-up"></i>} Withdraw
                </button>
              </div>
            )}
          </div>

          {centerMode === "preview" ? (
            <InPageFacebookPreview
              pageName={facebookPreview.pageName}
              pageAvatarUrl={facebookPreview.pageAvatarUrl}
              publishDate={facebookPreview.publishDate}
              caption={facebookPreview.caption}
              mediaItems={facebookPreview.mediaItems}
              activeMediaIndex={activeMediaIndex}
              canSaveDraft={form.status === "draft" && isDirty}
              canSubmitForReview={canSubmitCurrentSubmission}
              submitDisabledReason={
                canSubmitCurrentSubmission
                  ? submitDisabledReason
                  : "This submission has already moved beyond draft status."
              }
              isSaving={saveState === "saving"}
              isSubmitting={submitting}
              reorderDisabled={isReadOnlySubmission || reorderingMedia || saveState === "saving" || submitting}
              onMediaIndexChange={setActiveMediaIndex}
              onReorderMedia={(orderedIds) => void handleReorderMedia(orderedIds)}
              onSaveDraft={() => void handleSave()}
              onSubmitForReview={() => setModal("submit")}
              onEditDetails={handleEditPreviewDetails}
            />
          ) : isReadOnlySubmission ? (
            <SubmissionReadOnlyBody
              form={form}
              scheduledAt={scheduledAt}
              mediaItems={pickerItems}
              captionHashtags={captionHashtags}
              mediaTags={effectiveMediaTags(form)}
              facebookPreview={facebookPreview}
              activeMediaIndex={activeMediaIndex}
              onMediaIndexChange={setActiveMediaIndex}
            />
          ) : (
            <>
          {!isReadOnlySubmission && (
            <StepProgress
              steps={progressSteps}
              activeStep={activeStep}
              hasMedia={hasMedia}
              isDetailsComplete={isDetailsComplete}
              onStepClick={handleStepNav}
            />
          )}

          <section
            className={`sub-form-section sub-step-panel ${isReadOnlySubmission || activeStep === "details" ? "active" : ""}`}
            ref={detailsSectionRef}
            hidden={!isReadOnlySubmission && activeStep !== "details"}
          >
            <SectionHead
              icon="ti-edit"
              tone="blue"
              title="Post Details"
              subtitle="Use backend field names for the saved submission draft."
            />
            {isAdminComposer && (
              <Field label="Posting As">
                <BrandedSelect
                  value={form.institutionId}
                  placeholder={institutionsLoading ? "Loading institutions..." : "Select institution"}
                  hint={institutionsLoading ? undefined : "Select institution"}
                  options={institutions.map((institution) => ({
                    value: institution.id,
                    label: isDefaultInstitution(institution)
                      ? `${institution.name} (Default)`
                      : institution.name,
                  }))}
                  disabled={isReadOnlySubmission || Boolean(form.id)}
                  loading={institutionsLoading}
                  ariaLabel="Posting As"
                  className={`sub-posting-select${selectedPostingIsDefault ? " is-default" : ""}`}
                  onChange={(value) => updateField("institutionId", value)}
                />
                {selectedPostingIsDefault && (
                  <div className="sub-inline-default-note">
                    <i className="ti ti-sparkles" aria-hidden="true"></i>
                    Default institution for network-wide DASIG announcements.
                  </div>
                )}
                {institutionsError && (
                  <div className="sub-inline-note">{institutionsError}</div>
                )}
              </Field>
            )}
            <div className="sub-field-row">
              <Field label="Event Title">
                <input
                  ref={eventTitleRef}
                  className="sub-finput"
                  readOnly={isReadOnlySubmission}
                  value={form.eventTitle}
                  onChange={(event) =>
                    updateField("eventTitle", event.target.value)
                  }
                />
              </Field>
              <div ref={eventDateRef}>
                <Field label="Event Date">
                  <CalendarDateField
                    value={form.eventDate}
                    readOnly={isReadOnlySubmission}
                    placeholder="Select event date"
                    onChange={(value) => updateField("eventDate", value)}
                  />
                </Field>
              </div>
            </div>

            <Field
              label="Caption"
              action={
                !isReadOnlySubmission ? (
                  <div className="sub-caption-actions">
                    <FancyTextTool
                      caption={form.caption}
                      selection={captionSelection}
                      disabled={isReadOnlySubmission}
                      onReplaceSelection={updateCaptionSelection}
                      onPreviewSelection={updateCaptionSelection}
                      onRestoreSelection={restoreCaptionSelection}
                      onPreviewStateChange={setFancyTextPreviewActive}
                    />
                    {canUseAiCaption && !form.fastTrack && (
                      <AiCaptionButton
                        state={aiCaption.state}
                        canSuggest={aiCaption.canSuggest}
                        rateLimitReset={aiCaption.rateLimitReset}
                        notice={aiCaption.notice}
                        onSuggest={() => setCaptionPromptOpen(true)}
                      />
                    )}
                  </div>
                ) : undefined
              }
            >
              <div className="sub-caption-wrapper">
                <textarea
                  ref={captionRef}
                  className={`sub-finput ${captionTone(form.caption)}`}
                  rows={4}
                  readOnly={isReadOnlySubmission}
                  value={form.caption}
                  onChange={(event) => {
                    updateCaption(event.target.value);
                    captureCaptionSelection(event.currentTarget);
                  }}
                  onClick={(event) => captureCaptionSelection(event.currentTarget)}
                  onKeyUp={(event) => captureCaptionSelection(event.currentTarget)}
                  onSelect={(event) => captureCaptionSelection(event.currentTarget)}
                  placeholder="Write a compelling caption for the DASIG Facebook page..."
                />
                <span className={`sub-caption-counter ${captionTone(form.caption)}`}>
                  {Array.from(form.caption).length} / {CAPTION_WORD_LIMIT} characters
                </span>
              </div>
              {canUseAiCaption && !form.fastTrack && aiCaption.variants && (
                <AiCaptionSuggestion
                  variants={aiCaption.variants}
                  onApply={(caption, tone, action) => {
                    if (!canUseAiCaption) return;
                    updateCaption(caption);
                    aiCaption.logApply(tone, action);
                  }}
                  onDismissOne={aiCaption.logDismissOne}
                  onDismissAll={aiCaption.dismissAll}
                  onRegenerate={aiCaption.regenerate}
                />
              )}
              <div className="sub-finput-hint">
                Captions can contain up to {CAPTION_WORD_LIMIT} characters. Include relevant tags.
              </div>
              {canUseAiCaption && !form.fastTrack && (
                <AiCaptionPromptDialog
                  open={captionPromptOpen}
                  state={aiCaption.state}
                  hasImageAssets={hasImageAssets}
                  existingCaption={form.caption}
                  onClose={() => setCaptionPromptOpen(false)}
                  onSubmit={(prompt, tone) => void handleAiCaptionPromptSubmit(prompt, tone)}
                />
              )}
            </Field>

            <Field label="Tags">
              <div className="sub-hashtag-entry">
                <input
                  ref={tagsInputRef}
                  className="sub-finput"
                  readOnly={isReadOnlySubmission}
                  value={hashtagInput}
                  onChange={(event) => setHashtagInput(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter") {
                      event.preventDefault();
                      addHashtagToCaption();
                    }
                  }}
                  placeholder="Add tag"
                />
                <button
                  className="sub-hashtag-add"
                  type="button"
                  disabled={isReadOnlySubmission || !normalizeHashtagInput(hashtagInput)}
                  onClick={addHashtagToCaption}
                >
                  <i className="ti ti-plus" aria-hidden />
                  Add
                </button>
              </div>
              <div className="sub-tag-row">
                {captionHashtags.length > 0 ? (
                  captionHashtags.map((hashtag) => (
                    <button
                      key={hashtag}
                      type="button"
                      className="sub-tag active"
                      disabled={isReadOnlySubmission}
                      onClick={() => removeHashtagFromCaption(hashtag)}
                    >
                      {hashtag}
                    </button>
                  ))
                ) : (
                  <span className="sub-muted-text">No tags in caption.</span>
                )}
              </div>
            </Field>
          </section>

          <section
            className={`sub-form-section sub-step-panel ${isReadOnlySubmission || activeStep === "media" ? "active" : ""}`}
            ref={mediaSectionRef}
            hidden={!isReadOnlySubmission && activeStep !== "media"}
          >
            <SectionHead
              icon="ti-photo-up"
              tone="blue"
              title="Add Media"
              subtitle="Upload files, pick from your library, or let AI suggest relevant assets."
            />
            <MediaAssetsPicker
              items={pickerItems}
              onItemsChange={handlePickerChange}
              submissionId={form.id}
              eventTitle={form.eventTitle}
              caption={form.caption}
              category=""
              tags={captionHashtags.map((hashtag) => hashtag.slice(1))}
              disabled={!isEditableSubmission}
              onItemClick={openMediaCaption}
              getItemCaption={(item) => form.mediaCaptions[pickerMediaKey(item)] ?? ""}
            />
            {pickerItems.some((item) => item.mediaType === "image") &&
              pickerItems.some((item) => item.mediaType === "video") && (
                <div className="sub-inline-warning" role="status">
                  <i className="ti ti-alert-triangle" aria-hidden />
                  Mixed image and video attachments are allowed, but this post will require manual publishing.
                </div>
              )}
            {mediaUploadFailed && form.files.length > 0 && (
              <div className="sub-inline-error" role="alert">
                <span>Upload interrupted. Your draft and selected files are still available.</span>
                <button type="button" className="link-btn" onClick={() => void handleSave()} disabled={busy}>
                  Retry upload
                </button>
              </div>
            )}
          </section>

          <section
            className={`sub-form-section sub-step-panel ${isReadOnlySubmission || activeStep === "schedule" ? "active" : ""}`}
            ref={scheduleSectionRef}
            hidden={!isReadOnlySubmission && activeStep !== "schedule"}
          >
            <SectionHead
              icon="ti-folders"
              tone="blue"
              title="Organize & Schedule"
              subtitle={
                form.fastTrack
                  ? "Assign the media album, add media tags, and submit as urgent live-event content."
                  : "Assign the media album, add media tags, then choose the preferred publishing slot."
              }
            />
            <Field
              label="Album Assignment"
              tooltip="Select an existing album, type to create a new one, or let AI auto-match based on your event details."
            >
                <AlbumCombobox
                    value={form.albumName}
                    existingAlbums={existingAlbums}
                    readOnly={isReadOnlySubmission}
                    placeholder="Search, select, or create a new album"
                    onChange={(value) => updateField("albumName", value)}
                    onAutoMatch={applyAutoAlbum}
                />
                </Field>

            <Field label="Media Tags"
              tooltip="Add relevant media tags to help categorize and search for this content later.">
              <div className="sub-hashtag-entry">
                <input
                  ref={mediaTagsInputRef}
                  className="sub-finput"
                  value={mediaTagInput}
                  onChange={(event) => setMediaTagInput(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter") {
                      event.preventDefault();
                      addMediaTag();
                    }
                  }}
                  placeholder="Add album tag"
                  disabled={isReadOnlySubmission}
                />
                <button
                  type="button"
                  className="sub-hashtag-add"
                  disabled={isReadOnlySubmission || !normalizeMediaTag(mediaTagInput)}
                  onClick={addMediaTag}
                >
                  <i className="ti ti-plus" aria-hidden /> Add
                </button>
              </div>
              <div className="sub-tag-row">
                {effectiveMediaTags(form).length > 0 ? (
                  effectiveMediaTags(form).map((tag) => (
                    <button
                      key={tag}
                      type="button"
                      className="sub-tag active"
                      onClick={() => removeMediaTag(tag)}
                      disabled={isReadOnlySubmission}
                    >
                      {tag}
                    </button>
                  ))
                ) : (
                  <span className="sub-muted-text">Event title will appear here as the default album tag.</span>
                )}
              </div>
            </Field>

            {!isReadOnlySubmission && (
                <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "16px", marginTop: "24px" }}>
                    <div style={{ display: "flex", alignItems: "center", gap: "6px", fontWeight: 600, color: "#374151", fontSize: "14px" }}>
                    Publishing Mode
                    <i
                        className="ti ti-info-circle"
                        title="Choose 'Schedule' to plan a future post, or 'Live Event' to bypass the calendar queue for urgent, immediate publication."
                        style={{ color: "#9ca3af", cursor: "help", fontSize: "15px" }}
                    />
                    </div>

                    {/* Segmented Pill Toggle */}
                    <div style={{ display: "flex", background: "#f3f4f6", padding: "4px", borderRadius: "8px", gap: "4px" }}>
                    <button
                        type="button"
                        style={{
                        padding: "6px 12px",
                        border: "none",
                        borderRadius: "6px",
                        background: !form.fastTrack ? "#fff" : "transparent",
                        color: !form.fastTrack ? "#111827" : "#6b7280",
                        boxShadow: !form.fastTrack ? "0 1px 3px rgba(0,0,0,0.1)" : "none",
                        fontWeight: !form.fastTrack ? 600 : 500,
                        fontSize: "13px",
                        cursor: "pointer",
                        display: "flex",
                        alignItems: "center",
                        gap: "6px",
                        transition: "all 0.2s"
                        }}
                        onClick={() => updateFastTrack(false)}
                    >
                        <i className="ti ti-calendar" /> Schedule
                    </button>
                    <button
                        type="button"
                        style={{
                        padding: "6px 12px",
                        border: "none",
                        borderRadius: "6px",
                        background: form.fastTrack ? "#fff" : "transparent",
                        color: form.fastTrack ? "#111827" : "#6b7280",
                        boxShadow: form.fastTrack ? "0 1px 3px rgba(0,0,0,0.1)" : "none",
                        fontWeight: form.fastTrack ? 600 : 500,
                        fontSize: "13px",
                        cursor: "pointer",
                        display: "flex",
                        alignItems: "center",
                        gap: "6px",
                        transition: "all 0.2s"
                        }}
                        onClick={() => updateFastTrack(true)}
                    >
                        <i className="ti ti-bolt" style={{ color: form.fastTrack ? "#eab308" : "inherit" }} /> Live Event
                    </button>
                    </div>
                </div>
                )}
            {form.fastTrack && (
                <div className="sub-inline-note" style={{ marginTop: "16px", marginBottom: "8px" }}>
                    <i className="ti ti-info-circle" style={{ marginRight: "6px" }}></i>
                    Fast-Track submissions keep your post details and media, skip the scheduled slot, and move as urgent in the approval queue.
                </div>
            )}
            {!form.fastTrack && (
              <>
            <EngagementRecommendationsPanel
              loading={engagementLoading}
              recommendations={engagementRecommendations}
              selectedAt={scheduledAt}
              onSelect={applyEngagementSlot}
            />
            <div className="sub-field-row">
              <Field label="Preferred Date">
                <CalendarDateField
                  value={form.scheduledDate}
                  readOnly={isReadOnlySubmission || form.fastTrack}
                  placeholder="Select preferred date"
                  minValue={dateToInputValue(new Date())}
                  onChange={(value) => updateField("scheduledDate", value)}
                />
              </Field>
              <Field label="Preferred Time">
                <TimePickerField
                  value={form.scheduledTime}
                  readOnly={isReadOnlySubmission || form.fastTrack}
                  placeholder="Select preferred time"
                  onChange={(value) => updateField("scheduledTime", value)}
                />
              </Field>
            </div>
              </>
            )}
            {!form.fastTrack && guardRailError && (
              <div className="sub-inline-error">{guardRailError}</div>
            )}
          </section>

          {!isReadOnlySubmission && (
            <StepPanelActions
              activeStep={activeStep}
              hasMedia={hasMedia}
              isDetailsComplete={isDetailsComplete}
              onStepChange={handleStepNav}
            />
          )}
            </>
          )}
        </main>

        <aside className="sub-guard-panel">
          <div className="sub-guard-scroll">
            {lookupsLoading || hydratingId ? (
              <ReadinessSkeleton />
            ) : (
            <div className="sub-guard-header">
              <div className="sub-guard-title">
                <i className="ti ti-shield-check"></i> Readiness
              </div>
              <ReadinessRing score={readiness.score} />
              <div className="sub-score-grade">{readiness.grade}</div>
              <div className="sub-score-desc">{readiness.description}</div>
            </div>
            )}

            <GuardSection
              title="Required"
              icon="ti-list-check"
              meta={`${readiness.requiredComplete} / ${readiness.required.length}`}
              defaultOpen
            >
              {readiness.required.map((item) => (
                <CheckItem
                  key={item.title}
                  pass={item.pass}
                  idle={item.idle}
                  title={item.title}
                  sub={item.sub}
                  onClick={isReadOnlySubmission ? undefined : () => handleReadinessJump(item.target)}
                />
              ))}
            </GuardSection>

            <GuardSection
              title="Recommended"
              icon="ti-sparkles"
              meta={`${readiness.recommendedComplete} / ${readiness.recommended.length}`}
            >
              {readiness.recommended.map((item) => (
                <CheckItem
                  key={item.title}
                  pass={item.pass}
                  idle={item.idle}
                  title={item.title}
                  sub={item.sub}
                  onClick={isReadOnlySubmission ? undefined : () => handleReadinessJump(item.target)}
                />
              ))}
            </GuardSection>
          </div>

          <div className="sub-guard-actions">
            {!isReadOnlySubmission && (
              <>
            <button
              className="sub-guard-submit-btn"
              type="button"
              onClick={() => setModal("submit")}
              disabled={busy || Boolean(hydratingId) || previewValidation.blockingErrors.length > 0}
              title={previewValidation.blockingErrors[0]}
            >
              {submitting ? <i className="ti ti-loader-2 sub-spin"></i> : <i className="ti ti-send"></i>} Submit for Approval
            </button>
            {isDirty && (
              <button
                className="sub-guard-save-btn"
                type="button"
                onClick={() => void handleSave()}
                disabled={busy || Boolean(hydratingId)}
              >
                {saveState === "saving" ? <i className="ti ti-loader-2 sub-spin"></i> : <i className="ti ti-device-floppy"></i>} Save Draft
              </button>
            )}
              </>
            )}
          </div>
        </aside>
      </div>

      {templateSaveOpen && (
        <div
          className="sub-modal-overlay"
          role="presentation"
          onMouseDown={() => setTemplateSaveOpen(false)}
        >
          <div
            className="sub-modal sub-template-save-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="template-save-title"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <button
              className="sub-modal-close"
              type="button"
              onClick={() => setTemplateSaveOpen(false)}
              aria-label="Close save template"
            >
              <i className="ti ti-x" aria-hidden />
            </button>
            <div className="sub-template-save-head">
              <div className="sub-template-save-icon">
                <i className="ti ti-template" aria-hidden />
              </div>
              <div>
                <div className="sub-modal-title" id="template-save-title">Save Post Template</div>
                <div className="sub-modal-desc">
                  Turn this submission caption into a reusable template for future posts.
                </div>
              </div>
            </div>
            <div className="sub-template-save-grid">
              <div className="sub-template-save-fields">
                <label className="sub-template-save-field">
                  <span>Template Name</span>
                  <input
                    className="sub-finput"
                    value={templateName}
                    onChange={(event) => setTemplateName(event.target.value)}
                    maxLength={80}
                    placeholder="Example: Scholarship announcement"
                    autoFocus
                  />
                </label>
                <div className="sub-template-save-meta">
                  <span>{Array.from(form.caption).length} characters</span>
                  <span>{captionHashtags.length} tag(s)</span>
                </div>
              </div>
              <div className="sub-template-save-preview">
                <span>Template Content</span>
                <p>{form.caption}</p>
              </div>
            </div>
            <div className="sub-modal-actions">
              <button
                className="sub-modal-btn cancel"
                type="button"
                onClick={() => setTemplateSaveOpen(false)}
              >
                Cancel
              </button>
              <button
                className="sub-modal-btn info"
                type="button"
                disabled={savingTemplate}
                onClick={() => void saveCustomTemplate()}
              >
                {savingTemplate ? "Saving..." : "Save Template"}
              </button>
            </div>
          </div>
        </div>
      )}

      {captionMediaItem && captionMediaKey && (
        <div
          className="sub-modal-overlay"
          role="presentation"
          onMouseDown={() => setCaptionMediaKey(null)}
        >
          <div
            className="sub-modal sub-media-caption-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="media-caption-title"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <button
              className="sub-modal-close"
              type="button"
              onClick={() => setCaptionMediaKey(null)}
              aria-label="Close media caption"
            >
              <i className="ti ti-x" aria-hidden />
            </button>
            <div className="sub-media-caption-preview">
              {captionMediaItem.mediaType === "video" ? (
                <video
                  src={captionMediaItem.previewUrl}
                  controls
                  muted
                  playsInline
                  preload="metadata"
                />
              ) : (
                <img src={captionMediaItem.previewUrl} alt={captionMediaItem.fileName} />
              )}
            </div>
            <div className="sub-media-caption-modal-head">
              <div>
                <div className="sub-modal-title" id="media-caption-title">Media Caption</div>
                <div className="sub-modal-desc">{captionMediaItem.fileName}</div>
              </div>
              <span>{(form.mediaCaptions[captionMediaKey] ?? "").length} / 500</span>
            </div>
            <textarea
              className="sub-finput"
              rows={4}
              maxLength={500}
              readOnly={isReadOnlySubmission}
              value={form.mediaCaptions[captionMediaKey] ?? ""}
              onChange={(event) => updateMediaCaption(captionMediaKey, event.target.value)}
              placeholder="Optional caption for this media"
              autoFocus
            />
            {captionMediaItem.mediaType === "image" && (
              <label className="sub-watermark-toggle">
                <input
                  type="checkbox"
                  checked={Boolean(form.mediaSkipWatermark[captionMediaKey])}
                  disabled={isReadOnlySubmission}
                  onChange={(event) =>
                    updateMediaSkipWatermark(captionMediaKey, event.target.checked)
                  }
                />
                <span>
                  <strong>Skip watermark for this image</strong>
                  <small>Exclude this photo from automatic watermarking at approval.</small>
                </span>
              </label>
            )}
            <div className="sub-modal-actions">
              <button
                className="sub-modal-btn info"
                type="button"
                onClick={() => setCaptionMediaKey(null)}
              >
                Done
              </button>
            </div>
          </div>
        </div>
      )}

      {modal === "submit" && (
        <ConfirmModal
          icon={hasRecommendedWarnings ? "ti-alert-triangle" : "ti-send"}
          title={hasRecommendedWarnings ? "Submit with recommended warnings?" : "Submit for Approval?"}
          description={
            hasRecommendedWarnings
              ? `Required checks are complete, but ${recommendedWarnings.length} recommended item(s) still need attention: ${recommendedWarnings.map((item) => item.title).join(", ")}. You can still submit for approval.`
              : `This submission will be sent for administrator approval. Readiness score: ${readiness.score} / 100.`
          }
          cancelLabel={hasRecommendedWarnings ? "Review Warnings" : "Go Back"}
          confirmLabel={submitting ? "Submitting..." : hasRecommendedWarnings ? "Submit Anyway" : "Confirm Submission"}
          loading={submitting}
          disabled={busy}
          onCancel={() => setModal(null)}
          onConfirm={() => void handleSubmit()}
        />
      )}
      {modal === "success" && (
        <ConfirmModal
          icon="ti-circle-check"
          tone="success"
          title="Submission sent!"
          description="Your content has been submitted for approval. You will be notified when it is reviewed."
          confirmLabel="Done"
          onConfirm={() => setModal(null)}
        />
      )}
      {modal === "delete" && (
        <ConfirmModal
          icon="ti-trash"
          tone="danger"
          title="Delete this draft?"
          description="This will delete the current draft from the submission queue."
          cancelLabel="Cancel"
          confirmLabel={deleting ? "Deleting..." : "Delete Draft"}
          loading={deleting}
          disabled={busy}
          onCancel={() => setModal(null)}
          onConfirm={() => void handleDelete()}
        />
      )}
      {modal === "withdraw" && (
        <ConfirmModal
          icon="ti-arrow-back-up"
          title="Withdraw submission?"
          description="This will return the pending approval submission to draft so you can edit it again."
          cancelLabel="Cancel"
          confirmLabel={withdrawing ? "Withdrawing..." : "Withdraw"}
          loading={withdrawing}
          disabled={busy}
          onCancel={() => setModal(null)}
          onConfirm={() => void handleWithdraw()}
        />
      )}
      {modal === "fast-track-switch" && (
        <ConfirmModal
          icon="ti-bolt"
          title="Switch to Fast-Track?"
          description="This will hide scheduling controls while Fast-Track is enabled, but your entered post details and preferred schedule will stay saved in the draft."
          cancelLabel="Cancel"
          confirmLabel="Switch Mode"
          onCancel={() => setModal(null)}
          onConfirm={() => {
            applyFastTrackMode(true);
            setModal(null);
          }}
        />
      )}
      {modal === "draft-choice" && (
        <ConfirmModal
          icon="ti-pencil"
          title="You have an unfinished draft"
          description="Resume the existing draft to keep working, or start a clean submission with empty media, details, schedule, and readiness state."
          cancelLabel="Resume Existing Draft"
          confirmLabel="Start New Submission"
          onCancel={resumeExistingDraft}
          onConfirm={startNewSubmission}
        />
      )}
      {modal === "draft-exit" && (
        <DraftExitModal
          saving={saveState === "saving"}
          disabled={busy}
          onSave={() => void handleSaveDraftAndExit()}
          onDiscard={() => void handleDiscardDraftAndExit()}
          onContinue={handleContinueEditing}
        />
      )}
    </div>
  );
}

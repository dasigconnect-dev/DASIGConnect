import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
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
  type SubmissionLookups,
  type SubmissionPayload,
  type SubmissionStatus,
  type SubmissionSummary,
} from "../../api/submissionApi";
import { getMediaAsset } from "../../api/mediaApi";
import {
  useSubmissionLookups,
  useSubmissions,
} from "../../hooks/useSubmissions";
import { useFacebookPreviewData } from "../../hooks/useFacebookPreviewData";
import { fileMediaKey, savedMediaKey } from "../../hooks/useMediaReorder";
import type { User } from "../../types/auth.types";
import type { FacebookPreviewMediaItem } from "../../types/facebook";
import type { SubmissionMediaItem } from "../../types/media";
import { useToast } from "../../context/ToastContext";
import FacebookPreviewCard from "../../components/facebook/FacebookPreviewCard";
import FacebookPreviewMediaReorder from "../../components/facebook/FacebookPreviewMediaReorder";
import MediaAssetsPicker from "../../components/media/MediaAssetsPicker";
import BrandedSelect from "../../components/ui/BrandedSelect";
import { useAiCaptionAssist } from "../../hooks/useAiCaptionAssist";
import AiCaptionButton from "./components/AiCaptionButton";
import AiCaptionSuggestion from "./components/AiCaptionSuggestion";
import AlbumCombobox from "../../components/ui/AlbumCombobox";
import "../../styles/dasig-loader.css";

interface SubmissionScreenProps {
  user: User;
}

interface FormState {
  id: string | null;
  status: SubmissionStatus;
  institutionId: string;
  selectedTemplateId: string | null;
  fastTrack: boolean;
  liveEventName: string;
  eventTitle: string;
  eventDate: string;
  caption: string;
  description: string;
  category: string;
  scheduledDate: string;
  scheduledTime: string;
  tags: string[];
  albumName: string;
  mediaTags: string[];
  files: File[];
  savedAssets: SavedMediaAsset[];
  mediaOrder: string[];
  mediaCaptions: Record<string, string>;
  mediaSkipWatermark: Record<string, boolean>;
  pendingAssetIds: string[];
  removedAssetIds: string[];
}

type QueueFilter = "drafts" | "submitted" | "published" | "failed" | "all";
type ModalState =
  | "submit"
  | "success"
  | "delete"
  | "withdraw"
  | "fast-track-switch"
  | "draft-choice"
  | "draft-exit"
  | null;
type SaveState = "idle" | "saving" | "saved";
type PendingLeaveAction = (() => void) | null;
type ProgressStep = "media" | "details" | "schedule";
type CenterMode = "edit" | "preview";

const initialForm: FormState = {
  id: null,
  status: "draft",
  institutionId: "",
  selectedTemplateId: null,
  fastTrack: false,
  liveEventName: "",
  eventTitle: "",
  eventDate: "",
  caption: "",
  description: "",
  category: "",
  scheduledDate: "",
  scheduledTime: "",
  tags: [],
  albumName: "",
  mediaTags: [],
  files: [],
  savedAssets: [],
  mediaOrder: [],
  mediaCaptions: {},
  mediaSkipWatermark: {},
  pendingAssetIds: [],
  removedAssetIds: [],
};

const statusLabels: Record<SubmissionStatus, string> = {
  draft: "Draft",
  pending: "Pending Approval",
  in_review: "Under Review",
  needs_revision: "Needs Revision",
  scheduled: "Scheduled",
  publishing: "Publishing",
  publish_failed: "Publish Failed",
  published: "Published",
  published_manual: "Published",
  admin_direct_post: "Direct Post",
  direct_post_scheduled: "Direct Post Scheduled",
  direct_post_publishing: "Direct Post Publishing",
  direct_post_failed: "Direct Post Failed",
  rejected: "Rejected",
};

function isDraftStatus(status: SubmissionStatus) {
  return status === "draft" || status === "needs_revision";
}

function isPublishedStatus(status: SubmissionStatus) {
  return status === "published" || status === "published_manual" || status === "admin_direct_post";
}

function isPublishFailedStatus(status: SubmissionStatus) {
  return status === "publish_failed" || status === "direct_post_failed";
}

function getSubmissionStatusIcon(status: SubmissionStatus) {
  switch (status) {
    case "published":
    case "published_manual":
      return "ti ti-circle-check";
    case "scheduled":
    case "direct_post_scheduled":
      return "ti ti-calendar-event";
    case "pending":
    case "in_review":
      return "ti ti-clock";
    case "needs_revision":
      return "ti ti-alert-triangle";
    case "publish_failed":
    case "direct_post_failed":
    case "rejected":
      return "ti ti-circle-x";
    case "publishing":
    case "direct_post_publishing":
      return "ti ti-loader-2 sub-spin";
    case "admin_direct_post":
      return "ti ti-send";
    case "draft":
    default:
      return "ti ti-edit";
  }
}

function matchesQueueSearch(item: SubmissionSummary, query: string) {
  const normalized = query.trim().toLowerCase();
  if (!normalized) return true;
  return [
    item.eventTitle,
    item.caption,
    item.institutionName,
    item.contributorEmail,
    statusLabels[item.status],
    item.liveEventName,
  ].some((value) => value?.toLowerCase().includes(normalized));
}

function isVideoFileType(fileType?: string | null) {
  if (!fileType) return false;
  const normalized = fileType.toLowerCase();
  return normalized.startsWith("video/") || ["mp4", "mov", "webm"].includes(normalized);
}

function isImageFileType(fileType?: string | null) {
  if (!fileType) return false;
  const normalized = fileType.toLowerCase();
  return (
    normalized.startsWith("image/") ||
    ["jpeg", "jpg", "png", "webp", "gif"].includes(normalized)
  );
}

const postTemplates = [
  {
    id: "event-announcement",
    name: "Event Announcement",
    target: "Upcoming seminars, workshops, summits",
    category: "Seminar / Webinar",
    tags: ["DASIG", "DOST", "Innovation"],
    caption: [
      "[EVENT TITLE]",
      "",
      "Date:",
      "Venue or Platform:",
      "Registration Link:",
      "",
      "[Brief description / Call to action]",
      "",
      "#DASIGCentralVisayas #DOST7 #InnovationEvent",
    ].join("\n"),
  },
  {
    id: "event-recap",
    name: "Event Recap / Milestone",
    target: "Post-activity highlights, achievements",
    category: "Awards and Recognition",
    tags: ["DASIG", "DOST"],
    caption: [
      "HISTORY HAS BEEN MADE",
      "EVENT RECAP",
      "",
      "[Summary of accomplishments / key takeaways]",
      "",
      "[Acknowledged partners and attendees]",
      "",
      "#DASIGCentralVisayas #HistoryMadeHere #DOST7",
    ].join("\n"),
  },
  {
    id: "competition-call",
    name: "Competition / Pitching Call",
    target: "Hackathons, reverse pitching challenges",
    category: "Innovation",
    tags: ["DASIG", "Innovation"],
    caption: [
      "CALL FOR INNOVATORS / PARTICIPANTS",
      "",
      "[Challenge Theme / Problem Statement]",
      "Prizes or Opportunities:",
      "Deadline for Submission:",
      "Apply here:",
      "",
      "#FlipTheScript #ReversePitching #DASIG",
    ].join("\n"),
  },
  {
    id: "partner-spotlight",
    name: "Partner Feature / Spotlight",
    target: "Member university/HEI spotlights",
    category: "Partnership / Collaboration",
    tags: ["DASIG", "Innovation", "Partnership"],
    caption: [
      "INSTITUTIONAL SPOTLIGHT: [University Name]",
      "",
      "[Feature on student research, lab innovation, or award]",
      "",
      "#ConnectedInnovation #CentralVisayas #[UniversityTag]",
    ].join("\n"),
  },
];

const submissionDetailsMemoryCache: Record<string, { caption: string; mediaAssets: SavedMediaAsset[] }> = {};

const DEFAULT_INSTITUTION_NAME = "dasig central visayas";
const DEFAULT_INSTITUTION_CODE = "dasig-cv";

function isDefaultInstitution(institution: InstitutionResponse) {
  return (
    Boolean(institution.isProtected ?? institution.protected) ||
    institution.name.trim().toLowerCase() === DEFAULT_INSTITUTION_NAME ||
    institution.institutionCode.trim().toLowerCase() === DEFAULT_INSTITUTION_CODE
  );
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
  const [activeStep, setActiveStep] = useState<ProgressStep>("media");
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
  
  const [existingAlbums, setExistingAlbums] = useState<string[]>([
    "2026 Hackathons", 
    "DOST Region 7 Announcements", 
    "Webinars"
  ]); //replace with api call instead of dummy data

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
    const template = postTemplates.find((item) => item.id === templateId);
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
    } catch {
      toast.error("Could not load submission detail.");
    } finally {
      setHydratingId(null);
    }
  }

  async function saveDraft() {
    if (isReadOnlySubmission) return false;
    if (busy) return false;
    if (isAdminComposer && !form.institutionId) {
      toast.error("Select an institution scope before saving this draft.");
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
      toast.success("Draft saved.");
      return true;
    } catch (err: unknown) {
      setSaveState("idle");
      if (form.files.length > 0) setMediaUploadFailed(true);
      toast.error(getErrorMessage(err, "Draft could not be saved."));
      return false;
    }
  }

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
      resetComposer();
      setModal(null);
      setDeleting(false);
      return;
    }

    try {
      await deleteDraft(form.id);
      setSubmissions((current) =>
        current.filter((item) => item.id !== form.id),
      );
      resetComposer();
      setModal(null);
      toast.info("Draft deleted.");
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
              <div className="sub-sidebar-eyebrow">Workspace</div>
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
                Refresh
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

      <div className="sub-workspace">
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
                  <button
                    className="sub-sidebar-template-clear"
                    type="button"
                    disabled={isReadOnlySubmission}
                    onClick={() => updateField("selectedTemplateId", null)}
                  >
                    Clear
                  </button>
                )}
              </div>
              <div className="sub-sidebar-template-list">
                {postTemplates.map((template) => (
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
                    <span className="sub-sidebar-template-name">{template.name}</span>
                    <span className="sub-sidebar-template-target">{template.target}</span>
                  </button>
                ))}
              </div>
            </section>
          )}
        </aside>

        <main className="sub-form-canvas">
          <div className="sub-form-page-head">
            <div>
              <h1 className="sub-form-page-title">
                {centerMode === "preview"
                  ? "Facebook Preview"
                  : isReadOnlySubmission
                    ? "Submitted Preview"
                    : "Submit Content"}
              </h1>
              <p className="sub-form-page-sub">
                {centerMode === "preview"
                  ? "Review how followers will see this post before sending it for approval."
                  : isReadOnlySubmission
                    ? "Preview the content exactly as it was submitted."
                    : "Prepare event media, caption, tags, and a preferred publishing slot."}
              </p>
              {isReadOnlySubmission && (
                <div className="sub-readonly-note">
                  <i className="ti ti-eye"></i>
                  Viewing {statusLabels[form.status]} submission
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
                canUseAiCaption && !form.fastTrack ? (
                  <AiCaptionButton
                    state={aiCaption.state}
                    canSuggest={aiCaption.canSuggest}
                    rateLimitReset={aiCaption.rateLimitReset}
                    onSuggest={aiCaption.suggest}
                  />
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
                  onChange={(event) => updateField("caption", event.target.value)}
                  placeholder="Write a compelling caption for the DASIG Facebook page..."
                />
                <span className={`sub-caption-counter ${captionTone(form.caption)}`}>
                  {form.caption.length} / 500
                </span>
              </div>
              {canUseAiCaption && !form.fastTrack && aiCaption.variants && (
                <AiCaptionSuggestion
                  variants={aiCaption.variants}
                  onApply={(caption, tone, action) => {
                    if (!canUseAiCaption) return;
                    updateField("caption", caption);
                    aiCaption.logApply(tone, action);
                  }}
                  onDismissOne={aiCaption.logDismissOne}
                  onDismissAll={aiCaption.dismissAll}
                  onRegenerate={aiCaption.regenerate}
                />
              )}
              <div className="sub-finput-hint">
                Captions between 150-500 characters perform best on Facebook.
                Include relevant tags.
              </div>
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
                  onClick={() => handleReadinessJump(item.target)}
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
                  onClick={() => handleReadinessJump(item.target)}
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

function SectionHead({
  icon,
  tone,
  title,
  subtitle,
}: {
  icon: string;
  tone: string;
  title: string;
  subtitle: string;
}) {
  return (
    <div className="sub-section-head">
      <div className="sub-section-label">
        <div className={`sub-section-icon ${tone}`}>
          <i className={`ti ${icon}`}></i>
        </div>
        <div>
          <div className="sub-section-title">{title}</div>
          <div className="sub-section-subtitle">{subtitle}</div>
        </div>
      </div>
    </div>
  );
}

function InPageFacebookPreview({
  pageName,
  pageAvatarUrl,
  publishDate,
  caption,
  mediaItems,
  activeMediaIndex,
  canSaveDraft,
  canSubmitForReview,
  submitDisabledReason,
  isSaving,
  isSubmitting,
  reorderDisabled,
  onMediaIndexChange,
  onReorderMedia,
  onSaveDraft,
  onSubmitForReview,
  onEditDetails,
}: {
  pageName: string;
  pageAvatarUrl?: string;
  publishDate?: string;
  caption: string;
  mediaItems: FacebookPreviewMediaItem[];
  activeMediaIndex: number;
  canSaveDraft: boolean;
  canSubmitForReview: boolean;
  submitDisabledReason?: string;
  isSaving: boolean;
  isSubmitting: boolean;
  reorderDisabled?: boolean;
  onMediaIndexChange: (index: number) => void;
  onReorderMedia: (orderedIds: string[]) => void;
  onSaveDraft: () => void;
  onSubmitForReview: () => void;
  onEditDetails: () => void;
}) {
  return (
    <section className="sub-preview-workflow" aria-labelledby="sub-preview-title">
      <div className="sub-preview-tab-panel">
        <div className="sub-preview-stage-head">
          <div>
            <span>Public feed preview</span>
            <h2 id="sub-preview-title">What followers will see</h2>
          </div>
          <p>
            Preview the public-facing post before it moves into approval.
          </p>
        </div>
        <FacebookPreviewCard
          pageName={pageName}
          pageAvatarUrl={pageAvatarUrl}
          publishDate={publishDate}
          caption={caption}
          mediaItems={mediaItems}
          activeMediaIndex={activeMediaIndex}
          onMediaIndexChange={onMediaIndexChange}
          size="large"
        />
        <FacebookPreviewMediaReorder
          mediaItems={mediaItems}
          activeMediaId={mediaItems[activeMediaIndex]?.id}
          disabled={reorderDisabled}
          onSelect={onMediaIndexChange}
          onReorder={onReorderMedia}
        />
      </div>

      <div className="sub-preview-footer">
        <div className="sub-preview-guidance" role="status">
          <i className="ti ti-shield-check" aria-hidden="true" />
          <span>
            {submitDisabledReason ||
              "Submitting sends this post for approval. Save as draft if you still want to refine it."}
          </span>
        </div>
        <button
          className="sub-preview-btn secondary"
          type="button"
          onClick={onEditDetails}
        >
          <i className="ti ti-edit" aria-hidden="true" />
          Back to Editing
        </button>
        {canSaveDraft && (
          <button
            className="sub-preview-btn secondary"
            type="button"
            disabled={isSaving || isSubmitting}
            onClick={onSaveDraft}
          >
            <i
              className={`ti ${isSaving ? "ti-loader-2 sub-spin" : "ti-device-floppy"}`}
              aria-hidden="true"
            />
            {isSaving ? "Saving..." : "Save Draft"}
          </button>
        )}
        {canSubmitForReview && (
          <button
            className="sub-preview-btn primary"
            type="button"
            disabled={Boolean(submitDisabledReason) || isSaving || isSubmitting}
            onClick={onSubmitForReview}
          >
            <i
              className={`ti ${isSubmitting ? "ti-loader-2 sub-spin" : "ti-send"}`}
              aria-hidden="true"
            />
            {isSubmitting ? "Submitting..." : "Submit for Approval"}
          </button>
        )}
      </div>
    </section>
  );
}

function Field({
  label,
  count,
  tone,
  action,
  tooltip,
  children,
}: {
  label: string;
  count?: string;
  tone?: string;
  action?: ReactNode;
  tooltip?: string;
  children: ReactNode;
}) {
  return (
    <label className="sub-fgroup">
      <span className="sub-flabel">
        <span style={{ display: "flex", alignItems: "center", gap: "6px" }}>
          {label}
          {tooltip && (
            <i 
              className="ti ti-info-circle" 
              title={tooltip} 
              style={{ color: "#9ca3af", cursor: "help", fontSize: "15px" }} 
            />
          )}
        </span>
        <span className="sub-flabel-right">
          {count && (
            <span className={`sub-flabel-count ${tone || ""}`}>{count}</span>
          )}
          {action}
        </span>
      </span>
      {children}
    </label>
  );
}

function GuardSection({
  title,
  icon,
  meta,
  defaultOpen = false,
  children,
}: {
  title: string;
  icon: string;
  meta?: string;
  defaultOpen?: boolean;
  children: ReactNode;
}) {
  return (
    <details className="sub-guard-section" open={defaultOpen}>
      <summary className="sub-guard-section-title">
        <span>
          <i className={`ti ${icon}`}></i> {title}
        </span>
        <span className="sub-guard-section-meta">
          {meta && <small>{meta}</small>}
          <i className="ti ti-chevron-down" aria-hidden="true"></i>
        </span>
      </summary>
      <div className="sub-guard-section-body">{children}</div>
    </details>
  );
}

function CheckItem({
  pass,
  idle,
  title,
  sub,
  onClick,
}: {
  pass: boolean;
  idle?: boolean;
  title: string;
  sub: string;
  onClick?: () => void;
}) {
  const content = (
    <>
      <div
        className={`sub-check-icon ${idle ? "idle" : pass ? "pass" : "warn"}`}
      >
        <i
          className={`ti ${idle ? "ti-clock" : pass ? "ti-check" : "ti-alert-triangle"}`}
        ></i>
      </div>
      <div>
        <div className="sub-check-title">{title}</div>
        <div className="sub-check-sub">{sub}</div>
      </div>
    </>
  );

  if (onClick) {
    return (
      <button className="sub-check-item sub-check-action" type="button" onClick={onClick}>
        {content}
      </button>
    );
  }

  return (
    <div className="sub-check-item">
      {content}
    </div>
  );
}

function StepProgress({
  steps,
  activeStep,
  hasMedia,
  isDetailsComplete,
  onStepClick,
}: {
  steps: Array<{
    id: ProgressStep;
    label: string;
    complete: boolean;
  }>;
  activeStep: ProgressStep;
  hasMedia: boolean;
  isDetailsComplete: boolean;
  onStepClick: (step: ProgressStep) => void;
}) {
  function isLocked(id: ProgressStep) {
    return (id === "details" && !hasMedia) || (id === "schedule" && (!hasMedia || !isDetailsComplete));
  }

  function lockTitle(id: ProgressStep) {
    if (id === "details" && !hasMedia) {
      return "Add media first before entering Post Details.";
    }
    if (id === "schedule" && !hasMedia) {
      return "Add media first before setting a schedule.";
    }
    if (id === "schedule" && !isDetailsComplete) {
      return "Complete Post Details first - title, event date, and caption are required.";
    }
    return undefined;
  }

  return (
    <div className="sub-step-nav" aria-label="Submission progress">
      {steps.map((step, index) => {
        const active = activeStep === step.id;
        const locked = isLocked(step.id);
        return (
          <button
            key={step.id}
            className={`sub-step ${active ? "active" : ""} ${step.complete ? "complete" : ""} ${locked ? "locked" : ""}`}
            type="button"
            title={lockTitle(step.id)}
            onClick={() => onStepClick(step.id)}
          >
            <span className="sub-step-circle">
              {locked ? (
                <i className="ti ti-lock"></i>
              ) : step.complete ? (
                <i className="ti ti-check"></i>
              ) : (
                index + 1
              )}
            </span>
            <span className="sub-step-text">
              <span>Step {index + 1}</span>
              {step.label}
            </span>
          </button>
        );
      })}
    </div>
  );
}

function StepPanelActions({
  activeStep,
  hasMedia,
  isDetailsComplete,
  onStepChange,
}: {
  activeStep: ProgressStep;
  hasMedia: boolean;
  isDetailsComplete: boolean;
  onStepChange: (step: ProgressStep) => void;
}) {
  const order: ProgressStep[] = ["media", "details", "schedule"];
  const index = order.indexOf(activeStep);
  const previous = index > 0 ? order[index - 1] : null;
  const next = index < order.length - 1 ? order[index + 1] : null;
  const nextIsLocked =
    (next === "details" && !hasMedia) ||
    (next === "schedule" && (!hasMedia || !isDetailsComplete));
  const nextLockedTitle =
    next === "details" && !hasMedia
      ? "Add media first before entering Post Details."
      : next === "schedule" && !hasMedia
        ? "Add media first before setting a schedule."
        : next === "schedule" && !isDetailsComplete
          ? "Complete Post Details first - title, event date, and caption are required."
          : undefined;

  return (
    <div className="sub-step-panel-actions">
      <button
        type="button"
        className="sub-step-panel-btn secondary"
        onClick={() => previous && onStepChange(previous)}
        disabled={!previous}
      >
        <i className="ti ti-arrow-left"></i> Previous
      </button>
      {next ? (
        <button
          type="button"
          className={`sub-step-panel-btn ${nextIsLocked ? "locked" : "primary"}`}
          onClick={() => onStepChange(next)}
          title={nextLockedTitle}
        >
          {nextIsLocked ? (
            <>
              <i className="ti ti-lock"></i> {next === "details" ? "Add Media First" : "Complete Details First"}
            </>
          ) : (
            <>
              Next: {stepLabel(next)} <i className="ti ti-arrow-right"></i>
            </>
          )}
        </button>
      ) : (
        <span className="sub-step-panel-ready">
          <i className="ti ti-check"></i> Final step
        </span>
      )}
    </div>
  );
}

function EngagementRecommendationsPanel({
  loading,
  recommendations,
  selectedAt,
  onSelect,
}: {
  loading: boolean;
  recommendations: EngagementRecommendations | null;
  selectedAt?: string;
  onSelect: (scheduledAt: string) => void;
}) {
  if (loading) {
    return (
      <div className="sub-engagement-panel sub-engagement-loading" aria-live="polite">
        <i className="ti ti-loader-2"></i> Finding the best engagement times…
      </div>
    );
  }
  if (!recommendations || recommendations.slots.length === 0) return null;
  return (
    <div className="sub-engagement-panel">
      <div className="sub-engagement-heading">
        <span><i className="ti ti-chart-line"></i> Recommended times</span>
        <small>{recommendations.source === "HISTORICAL" ? `${recommendations.sampleSize} Facebook posts analyzed` : "Best-practice guidance"}</small>
      </div>
      {recommendations.notice && <p className="sub-engagement-notice">{recommendations.notice}</p>}
      <div className="sub-engagement-slots">
        {recommendations.slots.map((slot) => (
          <button
            type="button"
            className={selectedAt && new Date(selectedAt).getTime() === new Date(slot.scheduledAt).getTime() ? "selected" : ""}
            key={slot.scheduledAt}
            onClick={() => onSelect(slot.scheduledAt)}
          >
            <strong>{formatDateTime(slot.scheduledAt)}</strong>
            <span>{slot.windowLabel}</span>
            {slot.warnings.length > 0 && <em>{slot.warnings[0]}</em>}
          </button>
        ))}
      </div>
      <small className="sub-engagement-manual">You can ignore these suggestions and choose any valid custom time.</small>
    </div>
  );
}

function CalendarDateField({
  value,
  placeholder,
  readOnly,
  minValue,
  onChange,
}: {
  value: string;
  placeholder: string;
  readOnly?: boolean;
  minValue?: string;
  onChange: (value: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const { rootRef, popoverRef, placement, maxHeight } =
    usePopoverCollision(open);
  const selectedDate = parseInputDate(value);
  const [visibleMonth, setVisibleMonth] = useState(() => {
    const base = selectedDate || new Date();
    return new Date(base.getFullYear(), base.getMonth(), 1);
  });

  useEffect(() => {
    if (!open) return;
    function handlePointerDown(event: PointerEvent) {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    }
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") setOpen(false);
    }
    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [open, rootRef]);

  useEffect(() => {
    if (selectedDate) {
      setVisibleMonth(
        new Date(selectedDate.getFullYear(), selectedDate.getMonth(), 1),
      );
    }
  }, [value, selectedDate]);

  const days = useMemo(() => buildCalendarDays(visibleMonth), [visibleMonth]);
  const todayValue = dateToInputValue(new Date());
  const displayValue = selectedDate ? formatLongDate(value) : "";

  function moveMonth(offset: number) {
    setVisibleMonth(
      (current) => new Date(current.getFullYear(), current.getMonth() + offset, 1),
    );
  }

  function selectDate(next: string) {
    onChange(next);
    setOpen(false);
  }

  return (
    <div
      className={`sub-date-field ${open ? "is-open" : ""} ${placement}`}
      ref={rootRef}
    >
      <button
        className={`sub-date-trigger ${open ? "open" : ""}`}
        type="button"
        disabled={readOnly}
        onClick={() => {
          if (!readOnly) setOpen((current) => !current);
        }}
        aria-haspopup="dialog"
        aria-expanded={open}
      >
        <span className={displayValue ? "" : "placeholder"}>
          {displayValue || placeholder}
        </span>
        <i className="ti ti-calendar-event"></i>
      </button>

      {open && !readOnly && (
        <div
          className="sub-date-popover"
          ref={popoverRef}
          role="dialog"
          aria-label={placeholder}
          style={{ maxHeight }}
        >
          <div className="sub-date-popover-head">
            <button
              type="button"
              className="sub-date-nav"
              onClick={() => moveMonth(-1)}
              aria-label="Previous month"
            >
              <i className="ti ti-chevron-left"></i>
            </button>
            <div>
              <div className="sub-date-month">
                {visibleMonth.toLocaleDateString(undefined, {
                  month: "long",
                  year: "numeric",
                })}
              </div>
              <div className="sub-date-hint">Pick a calendar date</div>
            </div>
            <button
              type="button"
              className="sub-date-nav"
              onClick={() => moveMonth(1)}
              aria-label="Next month"
            >
              <i className="ti ti-chevron-right"></i>
            </button>
          </div>

          <div className="sub-date-weekdays" aria-hidden="true">
            {["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"].map((day) => (
              <span key={day}>{day}</span>
            ))}
          </div>

          <div className="sub-date-grid">
            {days.map((day) => {
              const isPast = minValue ? day.value < minValue : false;
              return (
                <button
                  key={day.value}
                  className={[
                    "sub-date-day",
                    day.inMonth ? "" : "muted",
                    day.value === value ? "selected" : "",
                    day.value === todayValue ? "today" : "",
                    isPast ? "past" : "",
                  ].join(" ")}
                  type="button"
                  disabled={isPast}
                  onClick={() => selectDate(day.value)}
                >
                  {day.date.getDate()}
                </button>
              );
            })}
          </div>

          <div className="sub-date-actions">
            <button
              type="button"
              onClick={() => {
                onChange("");
                setOpen(false);
              }}
            >
              Clear
            </button>
            <button type="button" onClick={() => selectDate(todayValue)}>
              Today
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

function TimePickerField({
  value,
  placeholder,
  readOnly,
  onChange,
}: {
  value: string;
  placeholder: string;
  readOnly?: boolean;
  onChange: (value: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const { rootRef, popoverRef, placement, maxHeight } =
    usePopoverCollision(open);
  const [draft, setDraft] = useState(() => parseTimeValue(value));
  const displayValue = value ? formatTimeDisplay(value) : "";

  useEffect(() => {
    if (open) setDraft(parseTimeValue(value));
  }, [open, value]);

  useEffect(() => {
    if (!open) return;
    function handlePointerDown(event: PointerEvent) {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    }
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") setOpen(false);
    }
    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [open, rootRef]);

  function draftToMinutes(parts: { hour: number; minute: number; period: "AM" | "PM" }) {
    let h = parts.hour;
    if (parts.period === "PM" && h !== 12) h += 12;
    if (parts.period === "AM" && h === 12) h = 0;
    return h * 60 + parts.minute;
  }

  const draftMinutes = draftToMinutes(draft);
  const isOutOfRange = draftMinutes < 8 * 60 || draftMinutes > 20 * 60;

  function adjust(part: "hour" | "minute", offset: number) {
    setDraft((current) => {
      if (part === "hour") {
        return { ...current, hour: cycleNumber(current.hour + offset, 1, 12) };
      }
      return { ...current, minute: cycleNumber(current.minute + offset, 0, 59) };
    });
  }

  function applyTime() {
    if (isOutOfRange) return;
    onChange(timePartsToValue(draft));
    setOpen(false);
  }

  return (
    <div
      className={`sub-time-field ${open ? "is-open" : ""} ${placement}`}
      ref={rootRef}
    >
      <button
        className={`sub-time-trigger ${open ? "open" : ""}`}
        type="button"
        disabled={readOnly}
        onClick={() => {
          if (!readOnly) setOpen((current) => !current);
        }}
        aria-haspopup="dialog"
        aria-expanded={open}
      >
        <span className={displayValue ? "" : "placeholder"}>
          {displayValue || placeholder}
        </span>
        <i className="ti ti-clock"></i>
      </button>

      {open && !readOnly && (
        <div
          className="sub-time-popover"
          ref={popoverRef}
          role="dialog"
          aria-label={placeholder}
          style={{ maxHeight }}
        >
          <div className="sub-time-head">
            <div>
              <div className="sub-time-title">Preferred time</div>
              <div className="sub-time-hint">Set the requested publish time</div>
            </div>
            <div className="sub-time-preview">{formatTimeParts(draft)}</div>
          </div>

          <div className="sub-time-controls">
            <TimeStepper
              label="Hour"
              value={String(draft.hour).padStart(2, "0")}
              onIncrement={() => adjust("hour", 1)}
              onDecrement={() => adjust("hour", -1)}
            />
            <TimeStepper
              label="Minute"
              value={String(draft.minute).padStart(2, "0")}
              onIncrement={() => adjust("minute", 1)}
              onDecrement={() => adjust("minute", -1)}
            />
            <div className="sub-time-period" aria-label="Meridiem">
              {(["AM", "PM"] as const).map((period) => (
                <button
                  key={period}
                  type="button"
                  className={draft.period === period ? "active" : ""}
                  onClick={() =>
                    setDraft((current) => ({ ...current, period }))
                  }
                >
                  {period}
                </button>
              ))}
            </div>
          </div>

          <div className="sub-time-quick">
            {[0, 15, 30, 45].map((minute) => (
              <button
                key={minute}
                type="button"
                className={draft.minute === minute ? "active" : ""}
                onClick={() => setDraft((current) => ({ ...current, minute }))}
              >
                :{String(minute).padStart(2, "0")}
              </button>
            ))}
          </div>

          {isOutOfRange && (
            <div className="sub-time-range-error">
              <i className="ti ti-alert-triangle"></i>
              Time must be between 8:00 AM and 8:00 PM.
            </div>
          )}

          <div className="sub-time-actions">
            <button
              type="button"
              onClick={() => {
                onChange("");
                setOpen(false);
              }}
            >
              Clear
            </button>
            <button type="button" onClick={applyTime} disabled={isOutOfRange}>
              Apply Time
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

function TimeStepper({
  label,
  value,
  onIncrement,
  onDecrement,
}: {
  label: string;
  value: string;
  onIncrement: () => void;
  onDecrement: () => void;
}) {
  return (
    <div className="sub-time-stepper">
      <button type="button" onClick={onIncrement} aria-label={`Increase ${label}`}>
        <i className="ti ti-chevron-up"></i>
      </button>
      <div>
        <span>{label}</span>
        <strong>{value}</strong>
      </div>
      <button type="button" onClick={onDecrement} aria-label={`Decrease ${label}`}>
        <i className="ti ti-chevron-down"></i>
      </button>
    </div>
  );
}

function DraftExitModal({
  saving,
  disabled,
  onSave,
  onDiscard,
  onContinue,
}: {
  saving: boolean;
  disabled: boolean;
  onSave: () => void;
  onDiscard: () => void;
  onContinue: () => void;
}) {
  useEffect(() => {
    if (disabled) return undefined;

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key !== "Escape") return;
      event.stopPropagation();
      onContinue();
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [disabled, onContinue]);

  return (
    <div
      className="sub-modal-overlay"
      onClick={disabled ? undefined : onContinue}
    >
      <div
        className="sub-modal sub-modal--draft-exit"
        role="dialog"
        aria-modal="true"
        aria-labelledby="draft-exit-title"
        aria-describedby="draft-exit-description"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="sub-modal-icon info">
          <i className="ti ti-notes"></i>
        </div>
        <div className="sub-modal-title" id="draft-exit-title">Save this post as a draft?</div>
        <div className="sub-modal-desc" id="draft-exit-description">
          You have unsaved content. Save it as a draft, discard your changes, or
          continue editing.
        </div>
        <div className="sub-modal-actions sub-modal-actions--three">
          <button
            className="sub-modal-btn sub-modal-btn--continue"
            type="button"
            onClick={onContinue}
            disabled={disabled}
          >
            Continue Editing
          </button>
          <button
            className="sub-modal-btn sub-modal-btn--discard"
            type="button"
            onClick={onDiscard}
            disabled={disabled}
          >
            Discard
          </button>
          <button
            className="sub-modal-btn sub-modal-btn--save"
            type="button"
            onClick={onSave}
            disabled={disabled}
            aria-busy={saving}
          >
            {saving && <i className="ti ti-loader-2 sub-spin"></i>}
            {saving ? "Saving..." : "Save Draft"}
          </button>
        </div>
      </div>
    </div>
  );
}

function usePopoverCollision(open: boolean) {
  const rootRef = useRef<HTMLDivElement | null>(null);
  const popoverRef = useRef<HTMLDivElement | null>(null);
  const [placement, setPlacement] = useState<"drop-down" | "drop-up">("drop-down");
  const [maxHeight, setMaxHeight] = useState(420);

  useEffect(() => {
    if (!open) return;
    let frame = 0;
    const viewportGap = 18;
    const triggerGap = 10;
    const minComfortHeight = 260;

    function updatePlacement() {
      window.cancelAnimationFrame(frame);
      frame = window.requestAnimationFrame(() => {
        const root = rootRef.current;
        const popover = popoverRef.current;
        if (!root || !popover) return;
        const rootRect = root.getBoundingClientRect();
        const naturalHeight = popover.scrollHeight;
        const spaceBelow =
          window.innerHeight - rootRect.bottom - triggerGap - viewportGap;
        const spaceAbove = rootRect.top - triggerGap - viewportGap;
        const shouldDropUp =
          spaceBelow < Math.min(naturalHeight, minComfortHeight) &&
          spaceAbove > spaceBelow;
        const availableSpace = shouldDropUp ? spaceAbove : spaceBelow;
        setPlacement(shouldDropUp ? "drop-up" : "drop-down");
        setMaxHeight(Math.max(220, Math.min(naturalHeight, availableSpace)));
      });
    }

    updatePlacement();
    window.addEventListener("resize", updatePlacement);
    window.addEventListener("scroll", updatePlacement, true);
    return () => {
      window.cancelAnimationFrame(frame);
      window.removeEventListener("resize", updatePlacement);
      window.removeEventListener("scroll", updatePlacement, true);
    };
  }, [open]);

  return { rootRef, popoverRef, placement, maxHeight };
}

function stepLabel(step: ProgressStep) {
  if (step === "media") return "Add Media";
  if (step === "details") return "Post Details";
  return "Preferred Schedule";
}

function QueueLoadingState() {
  return (
    <div
      className="sub-queue-loading"
      role="status"
      aria-label="Loading submissions"
    >
      <div className="dc-dot-triangle-container">
        <div className="dc-dot-triangle-label">
          <span>Loading</span>
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


function SubmissionCardMedia({
  thumbnail,
  mediaCount = 0,
  detailsLoaded = false,
}: {
  thumbnail?: SavedMediaAsset;
  mediaCount?: number;
  detailsLoaded?: boolean;
}) {
  const [loaded, setLoaded] = useState(false);
  const [hasError, setHasError] = useState(false);

  // If there is media attached (mediaCount > 0) but the thumbnail is still being fetched from backend:
  if (mediaCount > 0 && !thumbnail?.storageUrl && !detailsLoaded) {
    return (
      <div className="sub-fb-media-container">
        <div className="sub-fb-media-loader" aria-label="Loading media">
          <div className="sub-fb-spinner"></div>
        </div>
      </div>
    );
  }

  // If there is no media attached or media failed to load:
  if (!thumbnail?.storageUrl || hasError || mediaCount === 0) {
    return (
      <div className="sub-fb-media-container">
        <div className="sub-fb-no-media">
          <i className="ti ti-photo"></i>
          <span>No media attached</span>
        </div>
      </div>
    );
  }

  const isImg = isImageFileType(thumbnail.fileType);
  const isVid = isVideoFileType(thumbnail.fileType);

  return (
    <div className="sub-fb-media-container">
      {!loaded && (
        <div className="sub-fb-media-loader" aria-label="Loading media">
          <div className="sub-fb-spinner"></div>
        </div>
      )}
      {isImg ? (
        <img
          src={thumbnail.storageUrl}
          alt=""
          className={`sub-fb-media-img${loaded ? " is-loaded" : " is-loading"}`}
          onLoad={() => setLoaded(true)}
          onError={() => {
            setLoaded(true);
            setHasError(true);
          }}
          loading="lazy"
        />
      ) : isVid ? (
        <video
          src={thumbnail.storageUrl}
          muted
          playsInline
          preload="metadata"
          className={`sub-fb-media-video${loaded ? " is-loaded" : " is-loading"}`}
          onLoadedData={() => setLoaded(true)}
          onError={() => {
            setLoaded(true);
            setHasError(true);
          }}
        />
      ) : null}
      {(mediaCount ?? 0) > 1 && loaded && (
        <span className="sub-fb-media-badge">
          <i className="ti ti-photo-copy"></i>
          +{(mediaCount ?? 0) - 1} photos
        </span>
      )}
    </div>
  );
}

function ReadinessSkeleton() {
  return (
    <div className="sub-readiness-skeleton" aria-label="Loading readiness">
      <span className="sub-skel-ring sub-shimmer"></span>
      <span className="sub-skel-line wide sub-shimmer"></span>
      <span className="sub-skel-line sub-shimmer"></span>
    </div>
  );
}

function QueueState({
  icon,
  title,
  description,
}: {
  icon: string;
  title: string;
  description?: string;
}) {
  return (
    <div className="sub-queue-state">
      <i className={`ti ${icon}`}></i>
      <span>{title}</span>
      {description && <small>{description}</small>}
    </div>
  );
}

function ReadinessRing({ score }: { score: number }) {
  const circumference = 175.9;
  const offset = circumference - (score / 100) * circumference;
  return (
    <div className="sub-score-ring">
      <svg viewBox="0 0 64 64">
        <circle className="sub-score-bg" cx="32" cy="32" r="28" />
        <circle
          className="sub-score-fill"
          cx="32"
          cy="32"
          r="28"
          style={{
            strokeDashoffset: offset,
            stroke:
              score >= 80 ? "#16A34A" : score >= 60 ? "#D97706" : "#DC2626",
          }}
        />
      </svg>
      <div
        className="sub-score-num"
        style={{
          color: score >= 80 ? "#16A34A" : score >= 60 ? "#D97706" : "#DC2626",
        }}
      >
        {score}
      </div>
    </div>
  );
}

function ConfirmModal({
  icon,
  tone = "info",
  title,
  description,
  cancelLabel,
  confirmLabel,
  loading = false,
  disabled = false,
  onCancel,
  onConfirm,
}: {
  icon: string;
  tone?: "info" | "success" | "danger";
  title: string;
  description: string;
  cancelLabel?: string;
  confirmLabel: string;
  loading?: boolean;
  disabled?: boolean;
  onCancel?: () => void;
  onConfirm: () => void;
}) {
  return (
    <div
      className="sub-modal-overlay"
      onClick={disabled ? undefined : onCancel || onConfirm}
    >
      <div className="sub-modal" onClick={(event) => event.stopPropagation()}>
        <div className={`sub-modal-icon ${tone}`}>
          <i className={`ti ${icon}`}></i>
        </div>
        <div className="sub-modal-title">{title}</div>
        <div className="sub-modal-desc">{description}</div>
        <div className="sub-modal-actions">
          {onCancel && (
            <button
              className="sub-modal-btn cancel"
              type="button"
              onClick={onCancel}
              disabled={disabled}
            >
              {cancelLabel}
            </button>
          )}
          <button
            className={`sub-modal-btn ${tone}`}
            type="button"
            onClick={onConfirm}
            disabled={disabled}
            aria-busy={loading}
          >
            {loading && <i className="ti ti-loader-2 sub-spin"></i>}
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}



function savedAssetToPickerItem(asset: SavedMediaAsset): SubmissionMediaItem {
  const isVideo = ["mp4", "mov", "webm"].includes(asset.fileType.toLowerCase());
  return {
    clientId: `library-${asset.id}`,
    source: "library",
    assetId: asset.id,
    previewUrl: asset.storageUrl,
    mediaType: isVideo ? "video" : "image",
    fileName: asset.fileName,
  };
}

function toPayload(form: FormState, scheduledAt?: string): SubmissionPayload {
  return {
    institutionId: form.institutionId || undefined,
    eventTitle: form.eventTitle.trim() || "Untitled submission",
    eventDate: form.eventDate || new Date().toISOString().slice(0, 10),
    caption: form.caption.trim(),
    description: "",
    scheduledAt,
    category: "",
    templateId: form.fastTrack ? "" : form.selectedTemplateId ?? "",
    fastTrack: form.fastTrack,
    liveEventName: form.fastTrack ? form.liveEventName.trim() : "",
    tags: [],
    albumName: form.albumName.trim() || null,
    mediaTags: effectiveMediaTags(form),
  };
}

function pickerMediaKey(item: SubmissionMediaItem) {
  if (item.assetId) return savedMediaKey(item.assetId);
  if (item.file) return fileMediaKey(item.file);
  return item.clientId;
}

function extractHashtags(caption: string) {
  const matches = caption.match(/#[A-Za-z0-9_]+/g) ?? [];
  return [...new Set(matches)];
}

function normalizeHashtagInput(value: string) {
  const clean = value.trim().replace(/^#+/, "").replace(/[^A-Za-z0-9_]/g, "");
  return clean ? `#${clean}` : "";
}

function appendHashtagToCaption(caption: string, hashtag: string) {
  if (extractHashtags(caption).some((item) => item.toLowerCase() === hashtag.toLowerCase())) {
    return caption;
  }
  const trimmed = caption.trimEnd();
  return trimmed ? `${trimmed} ${hashtag}` : hashtag;
}

function removeHashtag(caption: string, hashtag: string) {
  return caption
    .replace(new RegExp(`(^|\\s)${escapeRegExp(hashtag)}(?=\\s|$)`, "g"), " ")
    .replace(/[ \t]{2,}/g, " ")
    .replace(/\n[ \t]+/g, "\n")
    .trim();
}

function normalizeMediaTag(value: string) {
  return value.trim().replace(/^#+/, "").replace(/\s+/g, " ");
}

function defaultMediaTags(eventTitle: string) {
  const tag = normalizeMediaTag(eventTitle);
  return tag ? [tag] : [];
}

function effectiveMediaTags(form: FormState) {
  return form.mediaTags.length > 0 ? form.mediaTags : defaultMediaTags(form.eventTitle);
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function isDirtyDraft(form: FormState) {
  return Boolean(
    form.eventTitle.trim() ||
      form.institutionId ||
      form.eventDate ||
      form.caption.trim() ||
      form.fastTrack ||
      form.liveEventName.trim() ||
      form.albumName.trim() ||
      form.mediaTags.length ||
      form.scheduledDate ||
      form.scheduledTime ||
      form.files.length ||
      form.savedAssets.length ||
      Object.values(form.mediaCaptions ?? {}).some((caption) => caption.trim()) ||
      Object.values(form.mediaSkipWatermark ?? {}).some(Boolean) ||
      form.pendingAssetIds.length,
  );
}

function getDirtySignature(form: FormState) {
  return JSON.stringify({
    eventTitle: form.eventTitle.trim(),
    institutionId: form.institutionId,
    eventDate: form.eventDate,
    selectedTemplateId: form.selectedTemplateId,
    fastTrack: form.fastTrack,
    liveEventName: form.liveEventName.trim(),
    caption: form.caption.trim(),
    albumName: form.albumName.trim(),
    mediaTags: effectiveMediaTags(form),
    scheduledDate: form.scheduledDate,
    scheduledTime: form.scheduledTime,
    files: form.files.map((file) => ({
      name: file.name,
      size: file.size,
      type: file.type,
      lastModified: file.lastModified,
    })),
    savedAssetIds: form.savedAssets.map((asset) => asset.id),
    mediaOrder: form.mediaOrder,
    mediaCaptions: form.mediaCaptions ?? {},
    mediaSkipWatermark: form.mediaSkipWatermark ?? {},
    pendingAssetIds: form.pendingAssetIds,
  });
}

function upsertSubmission(items: SubmissionSummary[], next: SubmissionSummary) {
  const exists = items.some((item) => item.id === next.id);
  if (!exists) return [next, ...items];
  return items.map((item) => (item.id === next.id ? next : item));
}

function getErrorMessage(error: unknown, fallback: string) {
  if (typeof error !== "object" || error === null) return fallback;
  const maybeError = error as {
    message?: string;
    response?: { data?: { error?: string } };
  };
  return maybeError.response?.data?.error || maybeError.message || fallback;
}

function isConflictError(error: unknown) {
  if (typeof error !== "object" || error === null) return false;
  const status = (error as { response?: { status?: number } }).response?.status;
  return status === 409;
}

function getOrderedLocalFiles(form: FormState) {
  if (form.mediaOrder.length === 0) return form.files;
  const filesByKey = new Map(form.files.map((file) => [fileMediaKey(file), file]));
  const ordered = form.mediaOrder
    .map((id) => filesByKey.get(id))
    .filter((file): file is File => Boolean(file));
  return ordered.length === form.files.length ? ordered : form.files;
}

function resolveSavedMediaOrder(form: FormState, savedAssets: SavedMediaAsset[]) {
  const existingIds = new Set(form.savedAssets.map((asset) => asset.id));
  const newAssets = savedAssets.filter((asset) => !existingIds.has(asset.id));
  const newAssetQueue = [...newAssets];
  const savedIds = new Set(savedAssets.map((asset) => asset.id));
  const resolved = form.mediaOrder
    .map((id) => {
      if (id.startsWith("saved:")) return id.replace("saved:", "");
      if (id.startsWith("local:")) return newAssetQueue.shift()?.id;
      return undefined;
    })
    .filter((id): id is string => Boolean(id && savedIds.has(id)));

  savedAssets.forEach((asset) => {
    if (!resolved.includes(asset.id)) resolved.push(asset.id);
  });
  return resolved;
}

function resolveSavedMediaCaptions(
  form: FormState,
  savedAssets: SavedMediaAsset[],
  orderedAssetIds: string[],
) {
  const existingIds = new Set(form.savedAssets.map((asset) => asset.id));
  const newAssets = savedAssets.filter((asset) => !existingIds.has(asset.id));
  const newAssetQueue = [...newAssets];
  const captions: Record<string, string> = {};

  form.mediaOrder.forEach((mediaKey) => {
    const assetId = mediaKey.startsWith("saved:")
      ? mediaKey.replace("saved:", "")
      : mediaKey.startsWith("local:")
        ? newAssetQueue.shift()?.id
        : undefined;
    if (!assetId || !orderedAssetIds.includes(assetId)) return;
    captions[assetId] = (form.mediaCaptions[mediaKey] ?? "").trim();
  });

  savedAssets.forEach((asset) => {
    if (!(asset.id in captions)) captions[asset.id] = asset.caption ?? "";
  });

  return captions;
}

function resolveSavedMediaSkipWatermarks(
  form: FormState,
  savedAssets: SavedMediaAsset[],
  orderedAssetIds: string[],
) {
  const existingIds = new Set(form.savedAssets.map((asset) => asset.id));
  const newAssets = savedAssets.filter((asset) => !existingIds.has(asset.id));
  const newAssetQueue = [...newAssets];
  const skipWatermarks: Record<string, boolean> = {};

  form.mediaOrder.forEach((mediaKey) => {
    const assetId = mediaKey.startsWith("saved:")
      ? mediaKey.replace("saved:", "")
      : mediaKey.startsWith("local:")
        ? newAssetQueue.shift()?.id
        : undefined;
    if (!assetId || !orderedAssetIds.includes(assetId)) return;
    skipWatermarks[assetId] = Boolean(form.mediaSkipWatermark[mediaKey]);
  });

  savedAssets.forEach((asset) => {
    if (!(asset.id in skipWatermarks)) skipWatermarks[asset.id] = Boolean(asset.skipWatermark);
  });

  return skipWatermarks;
}

function shouldSyncMediaDetails(
  savedAssets: SavedMediaAsset[],
  mediaCaptions: Record<string, string>,
  skipWatermarks: Record<string, boolean>,
) {
  if (savedAssets.length > 1) return true;
  return savedAssets.some(
    (asset) =>
      (asset.caption ?? "") !== (mediaCaptions[asset.id] ?? "") ||
      Boolean(asset.skipWatermark) !== Boolean(skipWatermarks[asset.id]),
  );
}

function mediaCaptionsFromSavedAssets(savedAssets: SavedMediaAsset[]) {
  return Object.fromEntries(
    savedAssets.map((asset) => [savedMediaKey(asset.id), asset.caption ?? ""]),
  );
}

function mediaSkipWatermarkFromSavedAssets(savedAssets: SavedMediaAsset[]) {
  return Object.fromEntries(
    savedAssets.map((asset) => [savedMediaKey(asset.id), Boolean(asset.skipWatermark)]),
  );
}

function captionsForSavedIds(
  mediaCaptions: Record<string, string>,
  savedIds: string[],
) {
  return Object.fromEntries(
    savedIds.map((id) => [id, (mediaCaptions[savedMediaKey(id)] ?? "").trim()]),
  );
}

function skipWatermarksForSavedIds(
  mediaSkipWatermark: Record<string, boolean>,
  savedIds: string[],
) {
  return Object.fromEntries(
    savedIds.map((id) => [id, Boolean(mediaSkipWatermark[savedMediaKey(id)])]),
  );
}

function pruneMediaCaptions(captions: Record<string, string>, mediaOrder: string[]) {
  const activeKeys = new Set(mediaOrder);
  return Object.fromEntries(
    Object.entries(captions).filter(([key]) => activeKeys.has(key)),
  );
}

function pruneMediaFlags(flags: Record<string, boolean>, mediaOrder: string[]) {
  const activeKeys = new Set(mediaOrder);
  return Object.fromEntries(
    Object.entries(flags).filter(([key]) => activeKeys.has(key)),
  );
}

function sortSavedAssetsByOrder(
  savedAssets: SavedMediaAsset[],
  orderedIds: string[],
) {
  const orderMap = new Map(orderedIds.map((id, index) => [id, index]));
  return [...savedAssets].sort((a, b) => {
    const aIndex = orderMap.get(savedMediaKey(a.id)) ?? Number.MAX_SAFE_INTEGER;
    const bIndex = orderMap.get(savedMediaKey(b.id)) ?? Number.MAX_SAFE_INTEGER;
    return aIndex - bIndex;
  });
}

function sortFilesByOrder(files: File[], orderedIds: string[]) {
  const orderMap = new Map(orderedIds.map((id, index) => [id, index]));
  return [...files].sort((a, b) => {
    const aIndex = orderMap.get(fileMediaKey(a)) ?? Number.MAX_SAFE_INTEGER;
    const bIndex = orderMap.get(fileMediaKey(b)) ?? Number.MAX_SAFE_INTEGER;
    return aIndex - bIndex;
  });
}

type ReadinessTarget =
  | "eventTitle"
  | "eventDate"
  | "caption"
  | "media"
  | "fileRequirements"
  | "schedule"
  | "album"
  | "captionLength"
  | "tags"
  | "mediaTags"
  | "mediaCaptions"
  | "template";

interface ReadinessCheck {
  title: string;
  sub: string;
  pass: boolean;
  idle?: boolean;
  target: ReadinessTarget;
}

function getReadinessChecklist(
  form: FormState,
  scheduledAt: string | undefined,
  lookups: SubmissionLookups,
  guardRails: GuardRailResult | null,
  guardRailsLoading: boolean,
) {
  const fileCount = form.files.length + form.savedAssets.length;
  const mediaTags = effectiveMediaTags(form);
  const filesWithinLimit = form.files.every(
    (file) => file.size <= lookups.maxFileSizeMb * 1024 * 1024,
  );
  const acceptedFormats = form.files.every((file) =>
    isAllowedFile(file, lookups.allowedFileTypes),
  );
  const scheduledDate = scheduledAt ? new Date(scheduledAt) : null;
  const futureSlot = !scheduledDate || scheduledDate > new Date();
  const publishWindow = !form.scheduledTime || isWithinPublishWindow(form.scheduledTime);
  const slotReady = form.fastTrack
    ? true
    : Boolean(scheduledAt) && futureSlot && publishWindow && !guardRails?.blocked;

  const required: ReadinessCheck[] = [
    {
      title: "Event title",
      target: "eventTitle",
      pass: Boolean(form.eventTitle.trim()),
      sub: form.eventTitle || "Required",
    },
    {
      title: "Event date",
      target: "eventDate",
      pass: Boolean(form.eventDate),
      sub: form.eventDate ? formatDate(form.eventDate) : "Required",
    },
    {
      title: "Caption",
      target: "caption",
      pass: Boolean(form.caption.trim()),
      sub: form.caption.trim() ? `${form.caption.length} characters` : "Required",
    },
    {
      title: "Media attachment",
      target: "media",
      pass: fileCount > 0,
      sub: fileCount > 0 ? `${fileCount} file(s) attached` : "At least one file required",
    },
    {
      title: "File requirements",
      target: "fileRequirements",
      pass: filesWithinLimit && acceptedFormats,
      sub: filesWithinLimit && acceptedFormats
        ? "Size and format accepted"
        : `${lookups.maxFileSizeMb} MB max; ${lookups.allowedFileTypes.join(", ") || "accepted media only"}`,
    },
    {
      title: "Album assignment",
      target: "album",
      pass: Boolean(form.albumName.trim()),
      sub: form.albumName.trim() || "Required before approval submission",
    },
    {
      title: form.fastTrack ? "Fast-Track route" : "Schedule guard rails",
      target: form.fastTrack ? "caption" : "schedule",
      pass: slotReady,
      idle: !form.fastTrack && guardRailsLoading,
      sub: form.fastTrack
        ? "No scheduled slot required"
        : guardRailsLoading
          ? "Checking selected slot..."
          : !scheduledAt
            ? "Preferred date and time required"
            : !futureSlot
              ? "Schedule must be in the future"
              : !publishWindow
                ? "Publish time must be 8:00 AM - 8:00 PM"
                : guardRails?.blocked
                  ? "Resolve blocked publishing slot"
                  : formatDateTime(scheduledAt),
    },
  ];

  const mediaCaptionCount = Object.values(form.mediaCaptions ?? {}).filter((caption) => caption.trim()).length;
  const recommended: ReadinessCheck[] = [
    {
      title: "Caption length",
      target: "captionLength",
      pass: captionTone(form.caption) === "ok",
      sub: `${form.caption.length} / 500 characters`,
    },
    {
      title: "Tags in caption",
      target: "tags",
      pass: extractHashtags(form.caption).length > 0,
      sub: extractHashtags(form.caption).length > 0
        ? `${extractHashtags(form.caption).length} tag(s) included`
        : "Add tags for discoverability",
    },
    {
      title: "Media tags",
      target: "mediaTags",
      pass: mediaTags.length > 0,
      sub: mediaTags.length > 0
        ? `${mediaTags.length} media tag(s) added`
        : "Event title is used as the default tag",
    },
    {
      title: "Per-media captions",
      target: "mediaCaptions",
      pass: fileCount === 0 || mediaCaptionCount > 0,
      sub: mediaCaptionCount > 0
        ? `${mediaCaptionCount} media caption(s) added`
        : "Optional context for attached media",
    },
    {
      title: "Template used",
      target: "template",
      pass: form.fastTrack || Boolean(form.selectedTemplateId),
      sub: form.fastTrack
        ? "Skipped for Fast-Track"
        : form.selectedTemplateId
          ? "Template selected"
          : "Optional baseline structure",
    },
  ];

  const requiredComplete = required.filter((item) => item.pass).length;
  const recommendedComplete = recommended.filter((item) => item.pass).length;
  const score = Math.round(
    (requiredComplete / required.length) * 75
      + (recommendedComplete / recommended.length) * 25,
  );

  return {
    score,
    required,
    recommended,
    requiredComplete,
    recommendedComplete,
    grade:
      requiredComplete === required.length
        ? "Ready to submit"
        : "Incomplete",
    description:
      requiredComplete === required.length
        ? "Required checks pass. Recommended items can still improve the post."
        : "Complete the required items before sending for approval.",
  };
}

function getPreviewValidation(
  form: FormState,
  scheduledAt: string | undefined,
  lookups: SubmissionLookups,
  guardRails: GuardRailResult | null,
) {
  const missingItems: string[] = [];
  const blockingErrors: string[] = [];
  const oversizedFile = form.files.find(
    (file) => file.size > lookups.maxFileSizeMb * 1024 * 1024,
  );
  const unsupportedFile = form.files.find(
    (file) => !isAllowedFile(file, lookups.allowedFileTypes),
  );

  if (!form.eventTitle.trim()) missingItems.push("Add an event title.");
  if (!form.eventDate) missingItems.push("Select the event date.");
  if (!form.caption.trim()) missingItems.push("Write a caption.");
  if (form.files.length + form.savedAssets.length < 1) missingItems.push("Attach at least one media asset.");
  if (!form.albumName.trim()) missingItems.push("Assign an album.");
  if (!form.fastTrack && !scheduledAt) missingItems.push("Choose a preferred schedule.");
  if (scheduledAt && new Date(scheduledAt) <= new Date()) {
    missingItems.push("Schedule must be set in the future.");
  }
  if (form.scheduledTime) {
    if (!isWithinPublishWindow(form.scheduledTime)) {
      missingItems.push("Publish time must be between 8:00 AM and 8:00 PM.");
    }
  }
  if (oversizedFile) {
    missingItems.push(
      `${oversizedFile.name} is larger than ${lookups.maxFileSizeMb} MB.`,
    );
  }
  if (unsupportedFile) {
    missingItems.push(`${unsupportedFile.name} uses an unsupported format.`);
  }
  if (!form.fastTrack && guardRails?.blocked) {
    missingItems.push("Resolve the blocked publishing slot.");
  }

  if (!form.eventTitle.trim()) blockingErrors.push("Event title is required.");
  if (!form.eventDate) blockingErrors.push("Event date is required.");
  if (!form.caption.trim()) blockingErrors.push("Caption is required.");
  if (form.files.length + form.savedAssets.length < 1) blockingErrors.push("At least one media attachment is required.");
  if (!form.albumName.trim()) blockingErrors.push("Album assignment is required.");
  if (!form.fastTrack && !scheduledAt) blockingErrors.push("Preferred schedule is required.");
  if (scheduledAt && new Date(scheduledAt) <= new Date()) {
    blockingErrors.push("Preferred schedule must be set in the future.");
  }
  if (form.scheduledTime) {
    if (!isWithinPublishWindow(form.scheduledTime)) {
      blockingErrors.push("Publish time must be between 8:00 AM and 8:00 PM.");
    }
  }
  if (oversizedFile) {
    blockingErrors.push(
      `File size must stay within ${lookups.maxFileSizeMb} MB per file.`,
    );
  }
  if (unsupportedFile) {
    blockingErrors.push("Only accepted image and video formats can be submitted.");
  }

  return { missingItems, blockingErrors };
}

function isWithinPublishWindow(timeValue: string) {
  const [h] = timeValue.split(":").map(Number);
  const m = Number(timeValue.split(":")[1]) || 0;
  const totalMin = h * 60 + m;
  return totalMin >= 8 * 60 && totalMin <= 20 * 60;
}

function captionTone(caption: string) {
  if (caption.length >= 150 && caption.length <= 500) return "ok";
  if (caption.length === 0) return "";
  return "warn";
}


function formatDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat(undefined, {
    month: "short",
    day: "numeric",
  }).format(date);
}

function formatDateTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat(undefined, {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(date);
}

function formatLongDate(value: string) {
  const date = parseInputDate(value);
  if (!date) return value;
  return new Intl.DateTimeFormat(undefined, {
    month: "long",
    day: "numeric",
    year: "numeric",
  }).format(date);
}

function formatTimeDisplay(value: string) {
  return formatTimeParts(parseTimeValue(value));
}

function formatTimeParts(parts: {
  hour: number;
  minute: number;
  period: "AM" | "PM";
}) {
  return `${parts.hour}:${String(parts.minute).padStart(2, "0")} ${parts.period}`;
}

function parseTimeValue(value: string) {
  if (!value) {
    const now = new Date();
    return toTimeParts(now.getHours(), now.getMinutes());
  }
  const [hourPart, minutePart] = value.split(":").map(Number);
  if (Number.isNaN(hourPart) || Number.isNaN(minutePart)) {
    const now = new Date();
    return toTimeParts(now.getHours(), now.getMinutes());
  }
  return toTimeParts(hourPart, minutePart);
}

function toTimeParts(hour24: number, minute: number) {
  const period: "AM" | "PM" = hour24 >= 12 ? "PM" : "AM";
  const hour = hour24 % 12 || 12;
  return {
    hour,
    minute: Math.min(Math.max(minute, 0), 59),
    period,
  };
}

function timePartsToValue(parts: {
  hour: number;
  minute: number;
  period: "AM" | "PM";
}) {
  const hour24 =
    parts.period === "PM"
      ? parts.hour === 12
        ? 12
        : parts.hour + 12
      : parts.hour === 12
        ? 0
        : parts.hour;
  return `${String(hour24).padStart(2, "0")}:${String(parts.minute).padStart(2, "0")}`;
}

function cycleNumber(value: number, min: number, max: number) {
  if (value > max) return min;
  if (value < min) return max;
  return value;
}

function parseInputDate(value: string) {
  if (!value) return null;
  const [year, month, day] = value.split("-").map(Number);
  if (!year || !month || !day) return null;
  const date = new Date(year, month - 1, day);
  if (Number.isNaN(date.getTime())) return null;
  return date;
}

function dateToInputValue(date: Date) {
  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, "0"),
    String(date.getDate()).padStart(2, "0"),
  ].join("-");
}

function buildCalendarDays(monthDate: Date) {
  const firstOfMonth = new Date(
    monthDate.getFullYear(),
    monthDate.getMonth(),
    1,
  );
  const start = new Date(firstOfMonth);
  start.setDate(firstOfMonth.getDate() - firstOfMonth.getDay());

  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(start);
    date.setDate(start.getDate() + index);
    return {
      date,
      value: dateToInputValue(date),
      inMonth: date.getMonth() === monthDate.getMonth(),
    };
  });
}

function formatTimeInput(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return `${date.getHours().toString().padStart(2, "0")}:${date.getMinutes().toString().padStart(2, "0")}`;
}

function formatRole(role: User["role"]) {
  if (role === "super_administrator") return "Super Administrator";
  if (role === "administrator") return "Administrator";
  return "Contributor";
}

function isAllowedFile(file: File, allowedFileTypes: string[]) {
  if (allowedFileTypes.length === 0) return true;
  const extension = normalizeFileType(
    file.name.split(".").pop()?.toLowerCase() || "",
  );
  return Boolean(extension && allowedFileTypes.includes(extension));
}


function normalizeFileType(fileType: string) {
  return fileType === "jpg" ? "jpeg" : fileType;
}

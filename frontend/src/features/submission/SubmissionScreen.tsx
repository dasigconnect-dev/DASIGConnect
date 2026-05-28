import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  attachAsset,
  createDraft,
  deleteDraft,
  getSubmission,
  reorderSubmissionMedia,
  submitForReview,
  updateDraft,
  uploadSubmissionMedia,
  validateGuardRails,
  type GuardRailResult,
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
import type {
  FacebookPreviewDetailsData,
  FacebookPreviewMediaItem,
} from "../../types/facebook";
import type { SubmissionMediaItem } from "../../types/media";
import { useToast } from "../../context/ToastContext";
import FacebookPreviewCard from "../../components/facebook/FacebookPreviewCard";
import FacebookPreviewDetails from "../../components/facebook/FacebookPreviewDetails";
import FacebookPreviewMediaReorder from "../../components/facebook/FacebookPreviewMediaReorder";
import MediaAssetsPicker from "../../components/media/MediaAssetsPicker";
import BrandedSelect from "../../components/ui/BrandedSelect";
import { useAiCaptionAssist } from "../../hooks/useAiCaptionAssist";
import AiCaptionButton from "./components/AiCaptionButton";
import AiCaptionSuggestion from "./components/AiCaptionSuggestion";

interface SubmissionScreenProps {
  user: User;
}

interface FormState {
  id: string | null;
  status: SubmissionStatus;
  eventTitle: string;
  eventDate: string;
  caption: string;
  description: string;
  category: string;
  scheduledDate: string;
  scheduledTime: string;
  tags: string[];
  files: File[];
  savedAssets: SavedMediaAsset[];
  mediaOrder: string[];
  pendingAssetIds: string[];
}

type QueueFilter = "drafts" | "submitted" | "all";
type ModalState =
  | "submit"
  | "success"
  | "delete"
  | "draft-choice"
  | "draft-exit"
  | null;
type SaveState = "idle" | "saving" | "saved";
type PendingLeaveAction = (() => void) | null;
type ProgressStep = "media" | "details" | "schedule";
type CenterMode = "edit" | "preview";
type PreviewTab = "preview" | "details";

const initialForm: FormState = {
  id: null,
  status: "draft",
  eventTitle: "",
  eventDate: "",
  caption: "",
  description: "",
  category: "",
  scheduledDate: "",
  scheduledTime: "",
  tags: [],
  files: [],
  savedAssets: [],
  mediaOrder: [],
  pendingAssetIds: [],
};

const statusLabels: Record<SubmissionStatus, string> = {
  draft: "Draft",
  pending: "Submitted",
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

export default function SubmissionScreen({ user }: SubmissionScreenProps) {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { submissions, setSubmissions, loading, error, refresh } =
    useSubmissions();
  const {
    lookups,
    loading: lookupsLoading,
    error: lookupsError,
  } = useSubmissionLookups();
  const toast = useToast();
  const detailsSectionRef = useRef<HTMLElement | null>(null);
  const prefilledRef = useRef(false);
  const cleanSignatureRef = useRef(getDirtySignature(initialForm));
  const shouldPromptBeforeLeaveRef = useRef(false);
  const browserBackGuardRef = useRef(false);
  const [filter, setFilter] = useState<QueueFilter>("drafts");
  const [form, setForm] = useState<FormState>(initialForm);
  const [pickerItems, setPickerItems] = useState<SubmissionMediaItem[]>([]);
  const [saveState, setSaveState] = useState<SaveState>("idle");
  const [modal, setModal] = useState<ModalState>(null);
  const [pendingLeaveAction, setPendingLeaveAction] =
    useState<PendingLeaveAction>(null);
  const [centerMode, setCenterMode] = useState<CenterMode>("edit");
  const [previewTab, setPreviewTab] = useState<PreviewTab>("preview");
  const [activeMediaIndex, setActiveMediaIndex] = useState(0);
  const [reorderingMedia, setReorderingMedia] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [hydratingId, setHydratingId] = useState<string | null>(null);
  const [refreshingQueue, setRefreshingQueue] = useState(false);
  const [guardRailsLoading, setGuardRailsLoading] = useState(false);
  const [guardRails, setGuardRails] = useState<GuardRailResult | null>(null);
  const [guardRailError, setGuardRailError] = useState("");
  const [activeStep, setActiveStep] = useState<ProgressStep>("details");

  const queued = useMemo(() => {
    if (filter === "drafts")
      return submissions.filter((item) => item.status === "draft");
    if (filter === "submitted")
      return submissions.filter((item) => item.status !== "draft");
    return submissions;
  }, [filter, submissions]);

  const draftCount = useMemo(
    () => submissions.filter((item) => item.status === "draft").length,
    [submissions],
  );

  const submittedCount = useMemo(
    () => submissions.filter((item) => item.status !== "draft").length,
    [submissions],
  );

  const scheduledAt = useMemo(() => {
    if (!form.scheduledDate || !form.scheduledTime) return undefined;
    const date = new Date(`${form.scheduledDate}T${form.scheduledTime}`);
    if (Number.isNaN(date.getTime())) return undefined;
    return date.toISOString();
  }, [form.scheduledDate, form.scheduledTime]);

  const readiness = useMemo(
    () => calculateReadiness(form, guardRails),
    [form, guardRails],
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
  const previewDetails = useMemo<FacebookPreviewDetailsData>(
    () =>
      getPreviewDetails({
        form,
        institution: user.inst,
        scheduledAt,
        lookups,
        guardRails,
        guardRailError,
        readinessScore: readiness.score,
        missingItems: previewValidation.missingItems,
      }),
    [
      form,
      guardRails,
      guardRailError,
      lookups,
      previewValidation.missingItems,
      readiness.score,
      scheduledAt,
      user.inst,
    ],
  );
  const submitDisabledReason =
    previewValidation.blockingErrors.length > 0
      ? previewValidation.blockingErrors[0]
      : undefined;
  const canSubmitCurrentSubmission = form.status === "draft";
  const isReadOnlySubmission = form.status !== "draft";
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
    saveState === "saving" || submitting || deleting || reorderingMedia;

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
    () => [
      {
        id: "details" as const,
        label: "Post Details",
        complete:
          Boolean(form.eventTitle.trim()) &&
          Boolean(form.eventDate) &&
          Boolean(form.caption.trim()),
      },
      {
        id: "media" as const,
        label: "Media Assets",
        complete: hasMedia,
      },
      {
        id: "schedule" as const,
        label: "Preferred Schedule",
        complete: Boolean(scheduledAt),
      },
    ],
    [form.caption, form.eventDate, form.eventTitle, hasMedia, scheduledAt],
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

      setGuardRailsLoading(true);
      validateGuardRails(scheduledAt)
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
  }, [scheduledAt]);

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
    setForm((current) => ({ ...current, [key]: value }));
    setSaveState("idle");
  }

  function resetComposer() {
    setForm(initialForm);
    setPickerItems([]);
    setActiveMediaIndex(0);
    setCenterMode("edit");
    setPreviewTab("preview");
    setGuardRails(null);
    setGuardRailError("");
    setSaveState("idle");
    cleanSignatureRef.current = getDirtySignature(initialForm);
    setFilter("drafts");
    setActiveStep("details");
    clearAssetIdParam();
  }

  function startNewSubmission() {
    resetComposer();
    setModal(null);
  }

  function exitSubmission() {
    setModal(null);
    setPendingLeaveAction(null);
    setForm(initialForm);
    setPickerItems([]);
    setActiveMediaIndex(0);
    setCenterMode("edit");
    setPreviewTab("preview");
    setGuardRails(null);
    setGuardRailError("");
    setSaveState("idle");
    cleanSignatureRef.current = getDirtySignature(initialForm);
    browserBackGuardRef.current = false;
    navigate("/dashboard", { replace: true });
  }

  function handleNewSubmission() {
    if (isReadOnlySubmission) {
      startNewSubmission();
      return;
    }
    const existingDraft = submissions.find((item) => item.status === "draft");
    if (existingDraft || isDirty) {
      setModal("draft-choice");
      return;
    }
    startNewSubmission();
  }

  function resumeExistingDraft() {
    const existingDraft =
      submissions.find((item) => item.status === "draft") ||
      (form.id ? submissions.find((item) => item.id === form.id) : undefined);
    setFilter("drafts");
    setModal(null);
    if (existingDraft) {
      void applySubmission(existingDraft);
      return;
    }
    setActiveStep("details");
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
        eventTitle: submission.eventTitle || "",
        eventDate: submission.eventDate || "",
        caption: submission.caption || "",
        description: submission.description || "",
        category: submission.category || "",
        scheduledDate: submission.scheduledAt
          ? submission.scheduledAt.slice(0, 10)
          : "",
        scheduledTime: submission.scheduledAt
          ? formatTimeInput(submission.scheduledAt)
          : "",
        tags: submission.tags ?? [],
        files: [],
        savedAssets: submission.mediaAssets ?? [],
        mediaOrder: (submission.mediaAssets ?? []).map((asset) =>
          savedMediaKey(asset.id),
        ),
        pendingAssetIds: [],
      };
      setForm(nextForm);
      setPickerItems((submission.mediaAssets ?? []).map(savedAssetToPickerItem));
      setActiveMediaIndex(0);
      setFilter(submission.status === "draft" ? "drafts" : "submitted");
      setActiveStep("details");
      setCenterMode("edit");
      setPreviewTab("preview");
      setSaveState("saved");
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
    setSaveState("saving");
    try {
      const payload = toPayload(form, scheduledAt);
      const response = form.id
        ? await updateDraft(form.id, payload)
        : await createDraft(payload);
      let finalResponse = response;
      if (form.pendingAssetIds.length > 0) {
        const attached = await attachPendingAssets(
          response.data.id,
          form.pendingAssetIds,
        );
        if (attached) finalResponse = attached;
      }
      if (form.files.length > 0) {
        const uploadResult = await uploadSubmissionMedia(
          finalResponse.data.id,
          getOrderedLocalFiles(form),
        );
        if (uploadResult) finalResponse = uploadResult as typeof response;
      }
      const savedAssets = finalResponse.data.mediaAssets ?? [];
      const orderedAssetIds = resolveSavedMediaOrder(form, savedAssets);
      if (orderedAssetIds.length === savedAssets.length && savedAssets.length > 1) {
        finalResponse = await reorderSubmissionMedia(
          finalResponse.data.id,
          orderedAssetIds,
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
        pendingAssetIds: [],
      };
      setForm((current) => ({
        ...current,
        id: finalResponse.data.id,
        status: finalResponse.data.status,
        files: [],
        savedAssets: orderedSavedAssets,
        mediaOrder: orderedSavedAssets.map((asset) => savedMediaKey(asset.id)),
        pendingAssetIds: [],
      }));
      setPickerItems(orderedSavedAssets.map(savedAssetToPickerItem));
      setSubmissions((current) =>
        upsertSubmission(current, finalResponse.data),
      );
      clearAssetIdParam();
      setSaveState("saved");
      cleanSignatureRef.current = getDirtySignature(nextForm);
      toast.success("Draft saved.");
      return true;
    } catch (err: unknown) {
      setSaveState("idle");
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
    if (!form.eventTitle.trim()) missing.push("an event title");
    if (!form.eventDate) missing.push("an event date");
    if (!form.caption.trim()) missing.push("a caption");
    if (!scheduledAt) missing.push("a preferred schedule");
    if (missing.length > 0) {
      toast.error(`Add ${missing.join(", ")} before submitting.`);
      if (!form.eventTitle.trim() || !form.eventDate || !form.caption.trim()) {
        setActiveStep("details");
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
          const uploadResult = await uploadSubmissionMedia(
            draftResponse.data.id,
            getOrderedLocalFiles(form),
          );
          if (uploadResult) draftResponse = uploadResult as typeof draft;
        } catch (uploadErr: unknown) {
          toast.warning(
            "Media upload skipped: " +
              getErrorMessage(uploadErr, "Supabase not configured"),
          );
        }
      }
      const savedAssets = draftResponse.data.mediaAssets ?? [];
      const orderedAssetIds = resolveSavedMediaOrder(form, savedAssets);
      if (orderedAssetIds.length === savedAssets.length && savedAssets.length > 1) {
        draftResponse = await reorderSubmissionMedia(
          draftResponse.data.id,
          orderedAssetIds,
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
        pendingAssetIds: [],
      }));
      setPickerItems(submittedAssets.map(savedAssetToPickerItem));
      clearAssetIdParam();
      setModal("success");
      toast.success("Submission sent for review.");
      void refresh();
    } catch (err: unknown) {
      toast.error(getErrorMessage(err, "Submission failed."));
    } finally {
      setSubmitting(false);
    }
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
      mediaOrder: newMediaOrder,
    }));
    setSaveState("idle");
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
      const { data } = await reorderSubmissionMedia(form.id, savedIds);
      const nextAssets = data.mediaAssets ?? sortedSavedAssets;
      const nextForm: FormState = {
        ...form,
        savedAssets: nextAssets,
        files: [],
        mediaOrder: nextAssets.map((asset) => savedMediaKey(asset.id)),
      };
      setForm((current) => ({
        ...current,
        savedAssets: nextAssets,
        mediaOrder: nextAssets.map((asset) => savedMediaKey(asset.id)),
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
    setCenterMode("edit");
    setActiveStep("details");
    window.setTimeout(() => {
      detailsSectionRef.current?.scrollIntoView({
        behavior: "smooth",
        block: "start",
      });
    }, 0);
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
          >
            <i className="ti ti-arrow-left"></i>
            <span>Back</span>
          </button>
          <div className="sub-nav-brand">
            <div className="sub-nav-brand-icon">
              <BrandMark />
            </div>
            <div className="sub-nav-brand-name">
              DASIG<em>Connect</em>
            </div>
          </div>
          <div className="sub-nav-breadcrumb">
            <i className="ti ti-chevron-right"></i>
            <span>Submit Content</span>
          </div>
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
        <aside className="sub-sidebar">
          <div className="sub-sidebar-header">
            <div className="sub-sidebar-title-row">
              <div>
                <div className="sub-sidebar-eyebrow">Workspace</div>
                <div className="sub-sidebar-title">Submission Queue</div>
              </div>
              <button
                className="sub-sidebar-count"
                type="button"
                onClick={() => void refreshQueue()}
                disabled={refreshingQueue || loading}
                aria-label="Refresh submissions"
              >
                {refreshingQueue || loading ? (
                  <i className="ti ti-loader-2 sub-spin"></i>
                ) : (
                  queued.length
                )}
              </button>
            </div>
            <div className="sub-sidebar-tabs">
              <button
                className={`sub-stab ${filter === "drafts" ? "active" : ""}`}
                type="button"
                onClick={() => setFilter("drafts")}
              >
                <span className="sub-stab-label">Drafts</span>
                <span className="sub-stab-badge">{draftCount}</span>
              </button>
              <button
                className={`sub-stab ${filter === "submitted" ? "active" : ""}`}
                type="button"
                onClick={() => setFilter("submitted")}
              >
                <span className="sub-stab-label">Submitted</span>
                <span className="sub-stab-badge">{submittedCount}</span>
              </button>
              <button
                className={`sub-stab ${filter === "all" ? "active" : ""}`}
                type="button"
                onClick={() => setFilter("all")}
              >
                <span className="sub-stab-label">All</span>
                <span className="sub-stab-badge">{submissions.length}</span>
              </button>
            </div>
          </div>
          <div className="sub-sidebar-list" aria-label="Submission queue">
            {loading && <QueueSkeleton />}
            {!loading && error && (
              <QueueState
                icon="ti-database-off"
                title="Unable to load submissions"
                description="Check your session and backend connection, then refresh the queue."
              />
            )}
            {!loading && !error && queued.length === 0 && (
              <QueueState
                icon="ti-folder-open"
                title="No submissions in this view"
              />
            )}
            {!loading &&
              !error &&
              queued.map((item) => (
                <button
                  className={`sub-queue-item ${item.id === form.id ? "active" : ""}`}
                  key={item.id}
                  type="button"
                  onClick={() => void applySubmission(item)}
                  disabled={Boolean(hydratingId)}
                  aria-busy={hydratingId === item.id}
                >
                  <div className="sub-qi-top">
                    <div className="sub-qi-title">
                      {item.eventTitle || "Untitled submission"}
                    </div>
                    <span className={`sub-qi-badge status-${item.status}`}>
                      {hydratingId === item.id ? "Loading" : statusLabels[item.status]}
                    </span>
                  </div>
                  <div className="sub-qi-meta">
                    <i className="ti ti-building"></i>
                    {item.institutionName || user.inst}
                    <span className="sub-qi-dot"></span>
                    {formatDate(item.eventDate)}
                  </div>
                  <div className="sub-qi-media">
                    <div className="sub-qi-thumb">
                      <i className="ti ti-photo"></i>
                    </div>
                    {(item.mediaCount ?? 0) > 1 && (
                      <div className="sub-qi-thumb">
                        +{(item.mediaCount ?? 0) - 1}
                      </div>
                    )}
                  </div>
                </button>
              ))}
          </div>
          <div className="sub-sidebar-new">
            <button
              className="sub-btn-new"
              type="button"
              onClick={handleNewSubmission}
              disabled={busy || Boolean(hydratingId)}
            >
              New Submission
            </button>
          </div>
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
                  ? "Review how followers will see this post before sending it to validator review."
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
                  onClick={() => {
                    setPreviewTab("preview");
                    setCenterMode("preview");
                  }}
                  disabled={busy || Boolean(hydratingId)}
                >
                  <i className="ti ti-brand-facebook"></i> Preview
                </button>
              )}
              <button
                className="sub-btn-ghost danger"
                type="button"
                onClick={() => setModal("delete")}
                disabled={busy || Boolean(hydratingId)}
              >
                {deleting ? <i className="ti ti-loader-2 sub-spin"></i> : <i className="ti ti-trash"></i>} Delete
              </button>
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
                disabled={busy || Boolean(hydratingId)}
              >
                {submitting ? <i className="ti ti-loader-2 sub-spin"></i> : <i className="ti ti-send"></i>} Submit for Review
              </button>
            </div>
            )}
          </div>

          {centerMode === "preview" ? (
            <InPageFacebookPreview
              activeTab={previewTab}
              onTabChange={setPreviewTab}
              pageName={facebookPreview.pageName}
              pageAvatarUrl={facebookPreview.pageAvatarUrl}
              publishDate={facebookPreview.publishDate}
              caption={facebookPreview.caption}
              mediaItems={facebookPreview.mediaItems}
              activeMediaIndex={activeMediaIndex}
              details={previewDetails}
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
              onStepClick={setActiveStep}
            />
          )}

          <section
            className={`sub-form-section sub-step-panel ${isReadOnlySubmission || activeStep === "details" ? "active" : ""}`}
            ref={detailsSectionRef}
            hidden={!isReadOnlySubmission && activeStep !== "details"}
          >
            <SectionHead
              icon="ti-edit"
              tone="gold"
              title="Post Details"
              subtitle="Use backend field names for the saved submission draft."
            />
            <div className="sub-field-row">
              <Field label="Event Title">
                <input
                  className="sub-finput"
                  readOnly={isReadOnlySubmission}
                  value={form.eventTitle}
                  onChange={(event) =>
                    updateField("eventTitle", event.target.value)
                  }
                />
              </Field>
              <Field label="Event Date">
                <CalendarDateField
                  value={form.eventDate}
                  readOnly={isReadOnlySubmission}
                  placeholder="Select event date"
                  onChange={(value) => updateField("eventDate", value)}
                />
              </Field>
            </div>
            <Field
              label="Caption"
              action={
                canUseAiCaption ? (
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
              {canUseAiCaption && aiCaption.variants && (
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
                Include relevant hashtags.
              </div>
            </Field>

            <div className="sub-field-row">
              <Field label="Event Category">
                <BrandedSelect
                  value={form.category}
                  placeholder={lookupsLoading ? "Loading..." : "Select a category"}
                  hint={lookupsLoading ? undefined : "Select a category"}
                  options={(lookups.categories ?? []).map((category) => ({
                    value: category,
                    label: category,
                  }))}
                  disabled={isReadOnlySubmission}
                  loading={lookupsLoading}
                  ariaLabel="Select event category"
                  onChange={(value) => updateField("category", value)}
                />
              </Field>
              <Field label="Institution Scope">
                <input className="sub-finput" value={user.inst} readOnly />
              </Field>
            </div>

            <Field label="Tags">
              {lookupsError && (
                <div className="sub-inline-note">
                  Tag options could not be loaded. You can still save and
                  submit.
                </div>
              )}
              <div className="sub-tag-row">
                {lookups.availableTags?.map((tag) => (
                  <button
                    key={tag}
                    type="button"
                    className={`sub-tag ${form.tags.includes(tag) ? "active" : ""}`}
                    disabled={isReadOnlySubmission}
                    onClick={() =>
                      updateField(
                        "tags",
                        form.tags.includes(tag)
                          ? form.tags.filter((t) => t !== tag)
                          : [...form.tags, tag],
                      )
                    }
                  >
                    {tag}
                  </button>
                ))}
                {!lookupsLoading && !lookups.availableTags?.length && (
                  <span className="sub-muted-text">No tags available.</span>
                )}
              </div>
            </Field>

            <Field label="Description / Validator Notes">
              <textarea
                className="sub-finput"
                rows={2}
                readOnly={isReadOnlySubmission}
                value={form.description}
                onChange={(event) =>
                  updateField("description", event.target.value)
                }
                placeholder="Notes for your Validator..."
              />
            </Field>
          </section>

          <section
            className={`sub-form-section sub-step-panel ${isReadOnlySubmission || activeStep === "media" ? "active" : ""}`}
            hidden={!isReadOnlySubmission && activeStep !== "media"}
          >
            <SectionHead
              icon="ti-photo-up"
              tone="blue"
              title="Media Assets"
              subtitle="Upload files, pick from your library, or let AI suggest relevant assets."
            />
            <MediaAssetsPicker
              items={pickerItems}
              onItemsChange={handlePickerChange}
              submissionId={form.id}
              eventTitle={form.eventTitle}
              caption={form.caption}
              category={form.category}
              tags={form.tags}
              disabled={form.status !== "draft"}
            />
          </section>

          <section
            className={`sub-form-section sub-step-panel ${isReadOnlySubmission || activeStep === "schedule" ? "active" : ""}`}
            hidden={!isReadOnlySubmission && activeStep !== "schedule"}
          >
            <SectionHead
              icon="ti-calendar-event"
              tone="purple"
              title="Preferred Schedule"
              subtitle="Testing mode allows draft save and submit while guardrails are being tuned."
            />
            <div className="sub-field-row">
              <Field label="Preferred Date">
                <CalendarDateField
                  value={form.scheduledDate}
                  readOnly={isReadOnlySubmission}
                  placeholder="Select preferred date"
                  onChange={(value) => updateField("scheduledDate", value)}
                />
              </Field>
              <Field label="Preferred Time">
                <TimePickerField
                  value={form.scheduledTime}
                  readOnly={isReadOnlySubmission}
                  placeholder="Select preferred time"
                  onChange={(value) => updateField("scheduledTime", value)}
                />
              </Field>
            </div>
            {guardRailError && (
              <div className="sub-inline-error">{guardRailError}</div>
            )}
          </section>

          {!isReadOnlySubmission && (
            <StepPanelActions activeStep={activeStep} onStepChange={setActiveStep} />
          )}
            </>
          )}
        </main>

        <aside className="sub-guard-panel">
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

          <GuardSection title="Required Fields" icon="ti-list-check">
            <CheckItem
              pass={Boolean(form.eventTitle.trim())}
              title="Event title"
              sub={form.eventTitle || "Required"}
            />
            <CheckItem
              pass={Boolean(form.eventDate)}
              title="Event date"
              sub={form.eventDate ? formatDate(form.eventDate) : "Required"}
            />
            <CheckItem
              pass={captionTone(form.caption) === "ok"}
              title="Caption length"
              sub={`${form.caption.length} characters`}
            />
            <CheckItem
              pass
              title="Event category"
              sub="Not required by backend UC-1.3"
            />
          </GuardSection>

          <GuardSection title="Media Quality" icon="ti-photo">
            <CheckItem
              pass
              idle={!hasMedia}
              title="Media attached"
              sub={
                hasMedia
                  ? `${form.savedAssets.length + form.files.length} file(s) attached`
                  : "Optional for text-only posts"
              }
            />
            <CheckItem
              pass={form.files.every(
                (file) => file.size <= lookups.maxFileSizeMb * 1024 * 1024,
              )}
              title="All files within size limit"
              sub={`${lookups.maxFileSizeMb} MB max per file`}
            />
            <CheckItem
              pass={form.files.every((file) =>
                isAllowedFile(file, lookups.allowedFileTypes),
              )}
              title="Accepted file formats only"
              sub={
                lookups.allowedFileTypes.join(", ") || "Awaiting backend lookup"
              }
            />
          </GuardSection>

          <GuardSection title="Scheduling" icon="ti-calendar">
            <CheckItem
              pass={Boolean(scheduledAt)}
              title="Preferred slot selected"
              sub={
                scheduledAt
                  ? formatDateTime(scheduledAt)
                  : "Select date and time"
              }
            />
            {guardRailsLoading ? (
              <CheckItem
                pass
                idle
                title="Slot confirmation"
                sub="Checking preferred schedule..."
              />
            ) : guardRails ? (
              <CheckItem
                pass
                title="Slot confirmation"
                sub={
                  guardRails.clean
                    ? "Guardrails passed"
                    : `Testing override: ${guardRails.hardBlocks.length} issue(s) noted`
                }
              />
            ) : (
              <CheckItem
                pass
                idle
                title="Slot confirmation"
                sub="Testing override active"
              />
            )}
          </GuardSection>

          <div className="sub-fb-preview-wrap">
            <div className="sub-guard-section-title">
              <i className="ti ti-brand-facebook"></i> Facebook Preview
            </div>
            <FacebookPreviewCard
              pageName={facebookPreview.pageName}
              pageAvatarUrl={facebookPreview.pageAvatarUrl}
              publishDate={facebookPreview.publishDate}
              caption={facebookPreview.caption}
              mediaItems={facebookPreview.mediaItems}
              activeMediaIndex={activeMediaIndex}
              onMediaIndexChange={setActiveMediaIndex}
              onOpen={() => {
                setPreviewTab("preview");
                setCenterMode("preview");
              }}
            />
          </div>

          <div className="sub-guard-actions">
            {!isReadOnlySubmission && (
              <>
            <button
              className="sub-guard-submit-btn"
              type="button"
              onClick={() => setModal("submit")}
              disabled={busy || Boolean(hydratingId)}
            >
              {submitting ? <i className="ti ti-loader-2 sub-spin"></i> : <i className="ti ti-send"></i>} Submit for Review
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

      {modal === "submit" && (
        <ConfirmModal
          icon="ti-send"
          title="Submit for Review?"
          description={`This submission has the required title, event date, caption, and preferred schedule. Media is optional for text-only posts. It will be sent to your institution's Validator. Readiness score: ${readiness.score} / 100.`}
          cancelLabel="Go Back"
          confirmLabel={submitting ? "Submitting..." : "Confirm Submission"}
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
          description="Your content has been submitted for validation. You will be notified when it is reviewed."
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
  activeTab,
  onTabChange,
  pageName,
  pageAvatarUrl,
  publishDate,
  caption,
  mediaItems,
  activeMediaIndex,
  details,
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
  activeTab: PreviewTab;
  onTabChange: (tab: PreviewTab) => void;
  pageName: string;
  pageAvatarUrl?: string;
  publishDate?: string;
  caption: string;
  mediaItems: FacebookPreviewMediaItem[];
  activeMediaIndex: number;
  details: FacebookPreviewDetailsData;
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
      <div className="sub-preview-tabs" role="tablist" aria-label="Facebook preview sections">
        <button
          type="button"
          className={activeTab === "preview" ? "active" : ""}
          role="tab"
          aria-selected={activeTab === "preview"}
          onClick={() => onTabChange("preview")}
        >
          <i className="ti ti-brand-facebook" aria-hidden="true" />
          Preview
        </button>
        <button
          type="button"
          className={activeTab === "details" ? "active" : ""}
          role="tab"
          aria-selected={activeTab === "details"}
          onClick={() => onTabChange("details")}
        >
          <i className="ti ti-list-check" aria-hidden="true" />
          Submission Details
          {details.missingItems.length > 0 && <span>{details.missingItems.length}</span>}
        </button>
      </div>

      {activeTab === "preview" ? (
        <div className="sub-preview-tab-panel" role="tabpanel">
          <div className="sub-preview-stage-head">
            <div>
              <span>Public feed preview</span>
              <h2 id="sub-preview-title">What followers will see</h2>
            </div>
            <p>
              Preview the public-facing post before it moves into validator review.
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
      ) : (
        <div className="sub-preview-details-panel" role="tabpanel">
          <FacebookPreviewDetails details={details} />
        </div>
      )}

      <div className="sub-preview-footer">
        <div className="sub-preview-guidance" role="status">
          <i className="ti ti-shield-check" aria-hidden="true" />
          <span>
            {submitDisabledReason ||
              "Submitting sends this post to your institution validator. Save as draft if you still want to refine it."}
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
            {isSubmitting ? "Submitting..." : "Submit for Review"}
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
  children,
}: {
  label: string;
  count?: string;
  tone?: string;
  action?: ReactNode;
  children: ReactNode;
}) {
  return (
    <label className="sub-fgroup">
      <span className="sub-flabel">
        {label}
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
  children,
}: {
  title: string;
  icon: string;
  children: ReactNode;
}) {
  return (
    <div className="sub-guard-section">
      <div className="sub-guard-section-title">
        <i className={`ti ${icon}`}></i> {title}
      </div>
      {children}
    </div>
  );
}

function CheckItem({
  pass,
  idle,
  title,
  sub,
}: {
  pass: boolean;
  idle?: boolean;
  title: string;
  sub: string;
}) {
  return (
    <div className="sub-check-item">
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
    </div>
  );
}

function StepProgress({
  steps,
  activeStep,
  onStepClick,
}: {
  steps: Array<{
    id: ProgressStep;
    label: string;
    complete: boolean;
  }>;
  activeStep: ProgressStep;
  onStepClick: (step: ProgressStep) => void;
}) {
  return (
    <div className="sub-step-nav" aria-label="Submission progress">
      {steps.map((step, index) => {
        const active = activeStep === step.id;
        return (
          <button
            key={step.id}
            className={`sub-step ${active ? "active" : ""} ${step.complete ? "complete" : ""}`}
            type="button"
            onClick={() => onStepClick(step.id)}
          >
            <span className="sub-step-circle">
              {step.complete ? <i className="ti ti-check"></i> : index + 1}
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
  onStepChange,
}: {
  activeStep: ProgressStep;
  onStepChange: (step: ProgressStep) => void;
}) {
  const order: ProgressStep[] = ["details", "media", "schedule"];
  const index = order.indexOf(activeStep);
  const previous = index > 0 ? order[index - 1] : null;
  const next = index < order.length - 1 ? order[index + 1] : null;

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
          className="sub-step-panel-btn primary"
          onClick={() => onStepChange(next)}
        >
          Next: {stepLabel(next)} <i className="ti ti-arrow-right"></i>
        </button>
      ) : (
        <span className="sub-step-panel-ready">
          <i className="ti ti-check"></i> Final step
        </span>
      )}
    </div>
  );
}

function CalendarDateField({
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
            {days.map((day) => (
              <button
                key={day.value}
                className={[
                  "sub-date-day",
                  day.inMonth ? "" : "muted",
                  day.value === value ? "selected" : "",
                  day.value === todayValue ? "today" : "",
                ].join(" ")}
                type="button"
                onClick={() => selectDate(day.value)}
              >
                {day.date.getDate()}
              </button>
            ))}
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

  function adjust(part: "hour" | "minute", offset: number) {
    setDraft((current) => {
      if (part === "hour") {
        return { ...current, hour: cycleNumber(current.hour + offset, 1, 12) };
      }
      return { ...current, minute: cycleNumber(current.minute + offset, 0, 59) };
    });
  }

  function applyTime() {
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
            <button type="button" onClick={applyTime}>
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
  if (step === "media") return "Media Assets";
  if (step === "details") return "Post Details";
  return "Preferred Schedule";
}

function QueueSkeleton() {
  return (
    <div className="sub-queue-skeleton" aria-label="Loading submissions">
      {Array.from({ length: 4 }).map((_, index) => (
        <div className="sub-queue-skeleton-card" key={index}>
          <span className="sub-skel-line wide sub-shimmer"></span>
          <span className="sub-skel-line sub-shimmer"></span>
          <div className="sub-skel-thumbs">
            <span className="sub-skel-thumb sub-shimmer"></span>
            <span className="sub-skel-thumb sub-shimmer"></span>
          </div>
        </div>
      ))}
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

function BrandMark() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 2L22 7V17L12 22L2 17V7L12 2Z" />
    </svg>
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
    eventTitle: form.eventTitle.trim() || "Untitled submission",
    eventDate: form.eventDate || new Date().toISOString().slice(0, 10),
    caption: form.caption.trim(),
    description: form.description.trim(),
    scheduledAt,
    category: form.category || undefined,
    tags: form.tags.length > 0 ? form.tags : undefined,
  };
}

function isDirtyDraft(form: FormState) {
  return Boolean(
    form.eventTitle.trim() ||
      form.eventDate ||
      form.caption.trim() ||
      form.description.trim() ||
      form.category ||
      form.scheduledDate ||
      form.scheduledTime ||
      form.tags.length ||
      form.files.length ||
      form.savedAssets.length ||
      form.pendingAssetIds.length,
  );
}

function getDirtySignature(form: FormState) {
  return JSON.stringify({
    eventTitle: form.eventTitle.trim(),
    eventDate: form.eventDate,
    caption: form.caption.trim(),
    description: form.description.trim(),
    category: form.category,
    scheduledDate: form.scheduledDate,
    scheduledTime: form.scheduledTime,
    tags: form.tags,
    files: form.files.map((file) => ({
      name: file.name,
      size: file.size,
      type: file.type,
      lastModified: file.lastModified,
    })),
    savedAssetIds: form.savedAssets.map((asset) => asset.id),
    mediaOrder: form.mediaOrder,
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

function calculateReadiness(
  form: FormState,
  guardRails: GuardRailResult | null,
) {
  let score = 0;
  if (form.eventTitle.trim()) score += 20;
  if (form.eventDate) score += 20;
  if (captionTone(form.caption) === "ok") score += 25;
  if (form.scheduledDate && form.scheduledTime) score += 20;
  if (guardRails && !guardRails.blocked) score += 15;

  return {
    score,
    grade:
      score >= 80
        ? "Good to submit"
        : score >= 60
          ? "Needs attention"
          : "Incomplete",
    description:
      score >= 80
        ? "Most checks pass. Non-blocking warnings can still be reviewed."
        : score >= 60
          ? "Resolve the highlighted items before sending."
          : "Complete the required fields to prepare this submission.",
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
  if (!scheduledAt) missingItems.push("Choose a preferred schedule.");
  if (oversizedFile) {
    missingItems.push(
      `${oversizedFile.name} is larger than ${lookups.maxFileSizeMb} MB.`,
    );
  }
  if (unsupportedFile) {
    missingItems.push(`${unsupportedFile.name} uses an unsupported format.`);
  }
  if (guardRails?.blocked) {
    missingItems.push("Resolve the blocked publishing slot.");
  }

  if (!form.eventTitle.trim()) blockingErrors.push("Event title is required.");
  if (!form.eventDate) blockingErrors.push("Event date is required.");
  if (!form.caption.trim()) blockingErrors.push("Caption is required.");
  if (!scheduledAt) blockingErrors.push("Preferred schedule is required.");
  if (oversizedFile) {
    blockingErrors.push(
      `File size must stay within ${lookups.maxFileSizeMb} MB per file.`,
    );
  }
  if (unsupportedFile) {
    blockingErrors.push("Only accepted image and video formats can be submitted.");
  }
  if (guardRails?.blocked) {
    blockingErrors.push("The preferred slot is blocked by guardrails.");
  }

  return { missingItems, blockingErrors };
}

function getPreviewDetails({
  form,
  institution,
  scheduledAt,
  lookups,
  guardRails,
  guardRailError,
  readinessScore,
  missingItems,
}: {
  form: FormState;
  institution: string;
  scheduledAt?: string;
  lookups: SubmissionLookups;
  guardRails: GuardRailResult | null;
  guardRailError: string;
  readinessScore: number;
  missingItems: string[];
}): FacebookPreviewDetailsData {
  const hasInvalidSize = form.files.some(
    (file) => file.size > lookups.maxFileSizeMb * 1024 * 1024,
  );
  const hasInvalidType = form.files.some(
    (file) => !isAllowedFile(file, lookups.allowedFileTypes),
  );
  const fileCount = form.files.length + form.savedAssets.length;
  const fileValidation =
    hasInvalidSize || hasInvalidType
      ? {
          label: "File validation",
          value: hasInvalidSize
            ? `${lookups.maxFileSizeMb} MB max per file`
            : "Unsupported file format",
          tone: "error" as const,
        }
      : {
          label: "File validation",
          value: fileCount > 0 ? "Files look ready" : "Optional for text-only posts",
          tone: fileCount > 0 ? ("ok" as const) : ("muted" as const),
        };

  const slotConfirmation = guardRailError
    ? {
        label: "Slot confirmation",
        value: guardRailError,
        tone: "warn" as const,
      }
    : guardRails
      ? {
          label: "Slot confirmation",
          value: guardRails.clean
            ? "Guardrails passed"
            : `Testing override: ${guardRails.hardBlocks.length} issue(s) noted`,
          tone: guardRails.clean ? ("ok" as const) : ("warn" as const),
        }
      : {
          label: "Slot confirmation",
          value: scheduledAt ? "Testing override active" : "No slot selected",
          tone: scheduledAt ? ("muted" as const) : ("warn" as const),
        };

  return {
    statusLabel: statusLabels[form.status],
    readinessScore,
    completionLabel:
      missingItems.length === 0
        ? "Ready for validator review"
        : `${missingItems.length} item${missingItems.length === 1 ? "" : "s"} remaining`,
    category: form.category || "Not selected",
    institution: institution || "Institution",
    tags: form.tags,
    schedule: scheduledAt ? formatDateTime(scheduledAt) : "Not scheduled",
    fileCount,
    fileValidation,
    slotConfirmation,
    aiCaptionAssist: {
      label: "AI caption assist",
      value: "Not available yet",
      tone: "muted",
    },
    validatorNotes: form.description.trim(),
    missingItems,
  };
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
  if (role === "admin") return "Administrator";
  return role.charAt(0).toUpperCase() + role.slice(1);
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

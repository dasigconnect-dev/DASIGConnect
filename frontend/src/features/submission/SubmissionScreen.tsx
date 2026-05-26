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
import type { FacebookPreviewDetailsData } from "../../types/facebook";
import { useToast } from "../../context/ToastContext";
import FacebookPreviewCard from "../../components/facebook/FacebookPreviewCard";
import FacebookPreviewModal from "../../components/facebook/FacebookPreviewModal";
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
type ModalState = "submit" | "success" | "delete" | null;
type SaveState = "idle" | "saving" | "saved";

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
  publish_failed: "Publish Failed",
  published: "Published",
  published_manual: "Published",
  admin_direct_post: "Direct Post",
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
  const [filter, setFilter] = useState<QueueFilter>("drafts");
  const [selectedFileIndex, setSelectedFileIndex] = useState(0);
  const [form, setForm] = useState<FormState>(initialForm);
  const [saveState, setSaveState] = useState<SaveState>("idle");
  const [modal, setModal] = useState<ModalState>(null);
  const [isPreviewModalOpen, setPreviewModalOpen] = useState(false);
  const [activeMediaIndex, setActiveMediaIndex] = useState(0);
  const [reorderingMedia, setReorderingMedia] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [guardRails, setGuardRails] = useState<GuardRailResult | null>(null);
  const [guardRailError, setGuardRailError] = useState("");

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
        missingItems: previewValidation.missingItems,
      }),
    [
      form,
      guardRails,
      guardRailError,
      lookups,
      previewValidation.missingItems,
      scheduledAt,
      user.inst,
    ],
  );
  const submitDisabledReason =
    previewValidation.blockingErrors.length > 0
      ? previewValidation.blockingErrors[0]
      : undefined;
  const canSubmitCurrentSubmission = form.status === "draft";

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
        return;
      }

      validateGuardRails(scheduledAt)
        .then((response) => {
          setGuardRails(response.data);
          setGuardRailError("");
        })
        .catch((err: unknown) => {
          setGuardRails(null);
          setGuardRailError(getErrorMessage(err, "Slot validation is unavailable."));
        });
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

  async function applySubmission(summary: SubmissionSummary) {
    try {
      const { data: submission } = await getSubmission(summary.id);
      setForm({
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
      });
      setActiveMediaIndex(0);
    } catch {
      toast.error("Could not load submission detail.");
    }
  }

  async function handleSave() {
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
      setForm((current) => ({
        ...current,
        id: finalResponse.data.id,
        status: finalResponse.data.status,
        files: [],
        savedAssets: orderedSavedAssets,
        mediaOrder: orderedSavedAssets.map((asset) => savedMediaKey(asset.id)),
        pendingAssetIds: [],
      }));
      setSubmissions((current) =>
        upsertSubmission(current, finalResponse.data),
      );
      clearAssetIdParam();
      setSaveState("saved");
      toast.success("Draft saved.");
    } catch (err: unknown) {
      setSaveState("idle");
      toast.error(getErrorMessage(err, "Draft could not be saved."));
    }
  }

  async function handleSubmit() {
    const missing: string[] = [];
    if (!form.eventTitle.trim()) missing.push("an event title");
    if (!form.eventDate) missing.push("an event date");
    if (!form.caption.trim()) missing.push("a caption");
    if (form.files.length + form.savedAssets.length === 0) {
      missing.push("at least one media file");
    }
    if (missing.length > 0) {
      toast.error(`Add ${missing.join(", ")} before submitting.`);
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
      setForm((current) => ({
        ...current,
        id: submitted.data.id,
        status: submitted.data.status,
        files: [],
        savedAssets: submitted.data.mediaAssets ?? current.savedAssets,
        mediaOrder: (submitted.data.mediaAssets ?? current.savedAssets).map(
          (asset) => savedMediaKey(asset.id),
        ),
        pendingAssetIds: [],
      }));
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
    if (!form.id) {
      setForm(initialForm);
      setModal(null);
      return;
    }

    try {
      await deleteDraft(form.id);
      setSubmissions((current) =>
        current.filter((item) => item.id !== form.id),
      );
      setForm(initialForm);
      setModal(null);
      toast.info("Draft deleted.");
    } catch (err: unknown) {
      toast.error(getErrorMessage(err, "Draft could not be deleted."));
    }
  }

  function handleFiles(files: FileList | null) {
    if (!files) return;
    const nextFiles = Array.from(files);
    setForm((current) => ({
      ...current,
      files: [...current.files, ...nextFiles],
      mediaOrder: [
        ...current.mediaOrder,
        ...nextFiles.map((file) => fileMediaKey(file)),
      ],
    }));
    setSelectedFileIndex(0);
    setActiveMediaIndex(0);
    setSaveState("idle");
  }

  function handleRemoveSavedAsset(assetId: string) {
    setForm((current) => ({
      ...current,
      savedAssets: current.savedAssets.filter((a) => a.id !== assetId),
      mediaOrder: current.mediaOrder.filter(
        (key) => key !== savedMediaKey(assetId),
      ),
    }));
    setSaveState("idle");
  }

  async function handleReorderMedia(orderedIds: string[]) {
    const sortedSavedAssets = sortSavedAssetsByOrder(form.savedAssets, orderedIds);
    const sortedFiles = sortFilesByOrder(form.files, orderedIds);
    setForm((current) => ({
      ...current,
      savedAssets: sortedSavedAssets,
      files: sortedFiles,
      mediaOrder: orderedIds,
    }));
    setSaveState("idle");

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
      setForm((current) => ({
        ...current,
        savedAssets: nextAssets,
        mediaOrder: nextAssets.map((asset) => savedMediaKey(asset.id)),
      }));
      setSubmissions((current) => upsertSubmission(current, data));
      toast.success("Media order updated.");
    } catch (err: unknown) {
      toast.error(getErrorMessage(err, "Media order could not be saved."));
    } finally {
      setReorderingMedia(false);
    }
  }

  function handleEditPreviewDetails() {
    setPreviewModalOpen(false);
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
            onClick={() => navigate("/dashboard")}
          >
            <i className="ti ti-arrow-left"></i>
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
          <div
            className={`sub-nav-save-status ${saveState === "saved" ? "saved" : ""}`}
          >
            <i
              className={
                saveState === "saving"
                  ? "ti ti-loader-2 sub-spin"
                  : saveState === "saved"
                    ? "ti ti-cloud-check"
                    : "ti ti-cloud"
              }
            ></i>
            {saveState === "saving"
              ? "Saving..."
              : saveState === "saved"
                ? "Draft saved"
                : "Unsaved draft"}
          </div>
          <div className="sub-nav-chip">{formatRole(user.role)}</div>
          <div className="sub-nav-avatar">{user.initials}</div>
        </div>
      </nav>

      <div className="sub-workspace">
        <aside className="sub-sidebar">
          <div className="sub-sidebar-header">
            <div className="sub-sidebar-title-row">
              <div>
                <div className="sub-sidebar-eyebrow">Workspace</div>
                <div className="sub-sidebar-title">Submission Queue</div>
              </div>
              <div className="sub-sidebar-count">{queued.length}</div>
            </div>
            <div className="sub-sidebar-tabs">
              <button
                className={`sub-stab ${filter === "drafts" ? "active" : ""}`}
                type="button"
                onClick={() => setFilter("drafts")}
              >
                Drafts {draftCount > 0 && <span>{draftCount}</span>}
              </button>
              <button
                className={`sub-stab ${filter === "submitted" ? "active" : ""}`}
                type="button"
                onClick={() => setFilter("submitted")}
              >
                Submitted {submittedCount > 0 && <span>{submittedCount}</span>}
              </button>
              <button
                className={`sub-stab ${filter === "all" ? "active" : ""}`}
                type="button"
                onClick={() => setFilter("all")}
              >
                All
              </button>
            </div>
          </div>
          <div className="sub-sidebar-list" aria-label="Submission queue">
            {loading && (
              <QueueState
                icon="ti-loader-2 sub-spin"
                title="Loading submissions"
              />
            )}
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
                >
                  <div className="sub-qi-top">
                    <div className="sub-qi-title">
                      {item.eventTitle || "Untitled submission"}
                    </div>
                    <span className={`sub-qi-badge status-${item.status}`}>
                      {statusLabels[item.status]}
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
              onClick={() => setForm(initialForm)}
            >
              <i className="ti ti-plus"></i> New Submission
            </button>
          </div>
        </aside>

        <main className="sub-form-canvas">
          <div className="sub-form-page-head">
            <div>
              <h1 className="sub-form-page-title">Submit Content</h1>
              <p className="sub-form-page-sub">
                Prepare event media, caption, tags, and a preferred publishing
                slot.
              </p>
            </div>
            <div className="sub-form-page-actions">
              <button
                className="sub-btn-ghost"
                type="button"
                onClick={() => setModal("delete")}
              >
                <i className="ti ti-trash"></i> Delete
              </button>
              <button
                className="sub-btn-ghost"
                type="button"
                onClick={() => void handleSave()}
              >
                <i className="ti ti-device-floppy"></i> Save Draft
              </button>
              <button
                className="sub-btn-primary"
                type="button"
                onClick={() => setModal("submit")}
              >
                <i className="ti ti-send"></i> Submit for Review
              </button>
            </div>
          </div>

          <section className="sub-form-section">
            <SectionHead
              icon="ti-photo-up"
              tone="blue"
              title="Media Assets"
              subtitle="Attach photos or videos from the event."
            />
            <label className="sub-upload-zone">
              <input
                className="sub-file-input"
                type="file"
                multiple
                accept="image/*,video/*"
                onChange={(event) => handleFiles(event.target.files)}
              />
              <div className="sub-upload-icon">
                <i className="ti ti-cloud-upload"></i>
              </div>
              <div className="sub-upload-title">Drop files here or browse</div>
              <div className="sub-upload-sub">
                Images and videos are attached to the draft and uploaded when
                you save.
              </div>
              <div className="sub-upload-types">
                <span>JPG</span>
                <span>PNG</span>
                <span>MP4</span>
                <span>MOV</span>
              </div>
            </label>
            {(form.savedAssets.length > 0 || form.files.length > 0) && (
              <div className="sub-filmstrip">
                {form.savedAssets.map((asset) => (
                  <div className="sub-film-item" key={asset.id}>
                    {asset.fileType.startsWith("image") ||
                    ["jpeg", "jpg", "png", "webp", "gif"].includes(
                      asset.fileType,
                    ) ? (
                      <img src={asset.storageUrl} alt={asset.fileName} />
                    ) : (
                      <div className="sub-film-video">
                        <i className="ti ti-video"></i>
                      </div>
                    )}
                    <span className="sub-film-badge">
                      {asset.fileType.toUpperCase()}
                    </span>
                    <span
                      className="sub-film-del"
                      role="button"
                      tabIndex={0}
                      aria-label="Remove asset"
                      onClick={() => handleRemoveSavedAsset(asset.id)}
                      onKeyDown={(e) => {
                        if (e.key === "Enter" || e.key === " ")
                          handleRemoveSavedAsset(asset.id);
                      }}
                    >
                      <i className="ti ti-x"></i>
                    </span>
                  </div>
                ))}
                {form.files.map((file, index) => (
                  <button
                    className={`sub-film-item ${selectedFileIndex === index ? "selected" : ""}`}
                    key={`${file.name}-${file.lastModified}`}
                    type="button"
                    onClick={() => setSelectedFileIndex(index)}
                  >
                    {file.type.startsWith("image/") ? (
                      <img src={filePreviewUrl(file)} alt={file.name} />
                    ) : (
                      <div className="sub-film-video">
                        <i className="ti ti-video"></i>
                      </div>
                    )}
                    <span className="sub-film-num">{index + 1}</span>
                    <span className="sub-film-badge">
                      {file.type.startsWith("video/") ? "VIDEO" : "IMAGE"}
                    </span>
                    <span
                      className="sub-film-del"
                      role="button"
                      tabIndex={0}
                      onClick={(event) => {
                        event.stopPropagation();
                        setForm((current) => ({
                          ...current,
                          files: current.files.filter(
                            (_, fileIndex) => fileIndex !== index,
                          ),
                          mediaOrder: current.mediaOrder.filter(
                            (id) => id !== fileMediaKey(file),
                          ),
                        }));
                        setActiveMediaIndex(0);
                      }}
                    >
                      <i className="ti ti-x"></i>
                    </span>
                  </button>
                ))}
              </div>
            )}
          </section>

          <section className="sub-form-section" ref={detailsSectionRef}>
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
                  value={form.eventTitle}
                  onChange={(event) =>
                    updateField("eventTitle", event.target.value)
                  }
                />
              </Field>
              <Field label="Event Date">
                <input
                  className="sub-finput"
                  type="date"
                  value={form.eventDate}
                  onChange={(event) =>
                    updateField("eventDate", event.target.value)
                  }
                />
              </Field>
            </div>
            <Field
              label="Caption"
              action={
                <AiCaptionButton
                  state={aiCaption.state}
                  canSuggest={aiCaption.canSuggest}
                  rateLimitReset={aiCaption.rateLimitReset}
                  onSuggest={aiCaption.suggest}
                />
              }
            >
              <div className="sub-caption-wrapper">
                <textarea
                  className={`sub-finput ${captionTone(form.caption)}`}
                  rows={4}
                  value={form.caption}
                  onChange={(event) => updateField("caption", event.target.value)}
                  placeholder="Write a compelling caption for the DASIG Facebook page..."
                />
                <span className={`sub-caption-counter ${captionTone(form.caption)}`}>
                  {form.caption.length} / 500
                </span>
              </div>
              {aiCaption.variants && (
                <AiCaptionSuggestion
                  variants={aiCaption.variants}
                  onApply={(caption, tone, action) => {
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
                <select
                  className="sub-fselect"
                  value={form.category}
                  onChange={(event) =>
                    updateField("category", event.target.value)
                  }
                >
                  <option value="">
                    {lookupsLoading ? "Loading..." : "Select a category"}
                  </option>
                  {lookups.categories?.map((cat) => (
                    <option key={cat} value={cat}>
                      {cat}
                    </option>
                  ))}
                </select>
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
                value={form.description}
                onChange={(event) =>
                  updateField("description", event.target.value)
                }
                placeholder="Notes for your Validator..."
              />
            </Field>
          </section>

          <section className="sub-form-section">
            <SectionHead
              icon="ti-calendar-event"
              tone="purple"
              title="Preferred Schedule"
              subtitle="Testing mode allows draft save and submit while guardrails are being tuned."
            />
            <div className="sub-field-row">
              <Field label="Preferred Date">
                <input
                  className="sub-finput"
                  type="date"
                  value={form.scheduledDate}
                  onChange={(event) =>
                    updateField("scheduledDate", event.target.value)
                  }
                />
              </Field>
              <Field label="Preferred Time">
                <input
                  className="sub-finput"
                  type="time"
                  value={form.scheduledTime}
                  onChange={(event) =>
                    updateField("scheduledTime", event.target.value)
                  }
                />
              </Field>
            </div>
            {guardRailError && (
              <div className="sub-inline-error">{guardRailError}</div>
            )}
          </section>
        </main>

        <aside className="sub-guard-panel">
          <div className="sub-guard-header">
            <div className="sub-guard-title">
              <i className="ti ti-shield-check"></i> Readiness
            </div>
            <ReadinessRing score={readiness.score} />
            <div className="sub-score-grade">{readiness.grade}</div>
            <div className="sub-score-desc">{readiness.description}</div>
          </div>

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
              pass={form.files.length > 0 || form.savedAssets.length > 0}
              title="Minimum 1 file uploaded"
              sub={`${form.savedAssets.length + form.files.length} file(s) attached`}
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
            {guardRails ? (
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
              onOpen={() => setPreviewModalOpen(true)}
            />
          </div>

          <div className="sub-guard-actions">
            <button
              className="sub-guard-submit-btn"
              type="button"
              onClick={() => setModal("submit")}
            >
              <i className="ti ti-send"></i> Submit for Review
            </button>
            <button
              className="sub-guard-save-btn"
              type="button"
              onClick={() => void handleSave()}
            >
              <i className="ti ti-device-floppy"></i> Save Draft
            </button>
          </div>
        </aside>
      </div>

      <FacebookPreviewModal
        open={isPreviewModalOpen}
        pageName={facebookPreview.pageName}
        pageAvatarUrl={facebookPreview.pageAvatarUrl}
        publishDate={facebookPreview.publishDate}
        caption={facebookPreview.caption}
        mediaItems={facebookPreview.mediaItems}
        activeMediaIndex={activeMediaIndex}
        details={previewDetails}
        canSaveDraft={form.status === "draft"}
        canSubmitForReview={canSubmitCurrentSubmission}
        submitDisabledReason={
          canSubmitCurrentSubmission
            ? submitDisabledReason
            : "This submission has already moved beyond draft status."
        }
        isSaving={saveState === "saving"}
        isSubmitting={submitting}
        reorderDisabled={reorderingMedia || saveState === "saving" || submitting}
        onClose={() => setPreviewModalOpen(false)}
        onMediaIndexChange={setActiveMediaIndex}
        onReorderMedia={(orderedIds) => void handleReorderMedia(orderedIds)}
        onSaveDraft={() => void handleSave()}
        onSubmitForReview={() => {
          setPreviewModalOpen(false);
          setModal("submit");
        }}
        onEditDetails={handleEditPreviewDetails}
      />

      {modal === "submit" && (
        <ConfirmModal
          icon="ti-send"
          title="Submit for Review?"
          description={`This submission will be sent to your institution's Validator. Testing mode will not block low readiness or guard rail warnings. Readiness score: ${readiness.score} / 100.`}
          cancelLabel="Go Back"
          confirmLabel={submitting ? "Submitting..." : "Confirm Submission"}
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
          confirmLabel="Delete Draft"
          onCancel={() => setModal(null)}
          onConfirm={() => void handleDelete()}
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
  onCancel,
  onConfirm,
}: {
  icon: string;
  tone?: "info" | "success" | "danger";
  title: string;
  description: string;
  cancelLabel?: string;
  confirmLabel: string;
  onCancel?: () => void;
  onConfirm: () => void;
}) {
  return (
    <div className="sub-modal-overlay" onClick={onCancel || onConfirm}>
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
            >
              {cancelLabel}
            </button>
          )}
          <button
            className={`sub-modal-btn ${tone}`}
            type="button"
            onClick={onConfirm}
          >
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
  if (form.eventTitle.trim()) score += 15;
  if (form.eventDate) score += 15;
  if (captionTone(form.caption) === "ok") score += 20;
  score += 10;
  if (form.files.length > 0 || form.savedAssets.length > 0) score += 20;
  if (form.scheduledDate && form.scheduledTime) score += 10;
  if (guardRails && !guardRails.blocked) score += 10;

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
  const hasMedia = form.files.length > 0 || form.savedAssets.length > 0;
  const oversizedFile = form.files.find(
    (file) => file.size > lookups.maxFileSizeMb * 1024 * 1024,
  );
  const unsupportedFile = form.files.find(
    (file) => !isAllowedFile(file, lookups.allowedFileTypes),
  );

  if (!form.eventTitle.trim()) missingItems.push("Add an event title.");
  if (!form.eventDate) missingItems.push("Select the event date.");
  if (!form.caption.trim()) missingItems.push("Write a caption.");
  if (!hasMedia) missingItems.push("Attach at least one image or video.");
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
  if (!hasMedia) blockingErrors.push("At least one media file is required.");
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
  missingItems,
}: {
  form: FormState;
  institution: string;
  scheduledAt?: string;
  lookups: SubmissionLookups;
  guardRails: GuardRailResult | null;
  guardRailError: string;
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
          value: fileCount > 0 ? "Files look ready" : "No files attached",
          tone: fileCount > 0 ? ("ok" as const) : ("warn" as const),
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

const previewUrls = new WeakMap<File, string>();

function filePreviewUrl(file: File) {
  const existing = previewUrls.get(file);
  if (existing) return existing;
  const next = URL.createObjectURL(file);
  previewUrls.set(file, next);
  return next;
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

import type { InstitutionResponse } from "../../api/authApi";
import type {
  GuardRailResult,
  SavedMediaAsset,
  SubmissionLookups,
  SubmissionPayload,
  SubmissionStatus,
  SubmissionSummary,
} from "../../api/submissionApi";
import { fileMediaKey, savedMediaKey } from "../../hooks/useMediaReorder";
import type { SubmissionMediaItem } from "../../types/media";
import type { User } from "../../types/auth.types";
import {
  DEFAULT_INSTITUTION_CODE,
  DEFAULT_INSTITUTION_NAME,
  statusLabels,
} from "./constants";
import type { FormState, ProgressStep, ReadinessCheck } from "./types";

export const CAPTION_WORD_LIMIT = 3000;

export function isDraftStatus(status: SubmissionStatus) {
  return status === "draft" || status === "needs_revision";
}

export function isPublishedStatus(status: SubmissionStatus) {
  return status === "published" || status === "published_manual" || status === "admin_direct_post";
}

export function isPublishFailedStatus(status: SubmissionStatus) {
  return status === "publish_failed" || status === "direct_post_failed";
}

export function getSubmissionStatusIcon(status: SubmissionStatus) {
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

export function matchesQueueSearch(item: SubmissionSummary, query: string) {
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

export function isVideoFileType(fileType?: string | null) {
  if (!fileType) return false;
  const normalized = fileType.toLowerCase();
  return normalized.startsWith("video/") || ["mp4", "mov", "webm"].includes(normalized);
}

export function isImageFileType(fileType?: string | null) {
  if (!fileType) return false;
  const normalized = fileType.toLowerCase();
  return (
    normalized.startsWith("image/") ||
    ["jpeg", "jpg", "png", "webp", "gif"].includes(normalized)
  );
}

export function isDefaultInstitution(institution: InstitutionResponse) {
  return (
    Boolean(institution.isProtected ?? institution.protected) ||
    institution.name.trim().toLowerCase() === DEFAULT_INSTITUTION_NAME ||
    institution.institutionCode.trim().toLowerCase() === DEFAULT_INSTITUTION_CODE
  );
}

export function savedAssetToPickerItem(asset: SavedMediaAsset): SubmissionMediaItem {
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

export function toPayload(form: FormState, scheduledAt?: string): SubmissionPayload {
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

export function pickerMediaKey(item: SubmissionMediaItem) {
  if (item.assetId) return savedMediaKey(item.assetId);
  if (item.file) return fileMediaKey(item.file);
  return item.clientId;
}

export function extractHashtags(caption: string) {
  const matches = caption.match(/#[A-Za-z0-9_]+/g) ?? [];
  return [...new Set(matches)];
}

export function normalizeHashtagInput(value: string) {
  const clean = value.trim().replace(/^#+/, "").replace(/[^A-Za-z0-9_]/g, "");
  return clean ? `#${clean}` : "";
}

export function appendHashtagToCaption(caption: string, hashtag: string) {
  if (extractHashtags(caption).some((item) => item.toLowerCase() === hashtag.toLowerCase())) {
    return caption;
  }
  const trimmed = caption.trimEnd();
  return trimmed ? `${trimmed} ${hashtag}` : hashtag;
}

export function removeHashtag(caption: string, hashtag: string) {
  return caption
    .replace(new RegExp(`(^|\\s)${escapeRegExp(hashtag)}(?=\\s|$)`, "g"), " ")
    .replace(/[ \t]{2,}/g, " ")
    .replace(/\n[ \t]+/g, "\n")
    .trim();
}

export function normalizeMediaTag(value: string) {
  return value.trim().replace(/^#+/, "").replace(/\s+/g, " ");
}

export function defaultMediaTags(eventTitle: string) {
  const tag = normalizeMediaTag(eventTitle);
  return tag ? [tag] : [];
}

export function effectiveMediaTags(form: FormState) {
  return form.mediaTags.length > 0 ? form.mediaTags : defaultMediaTags(form.eventTitle);
}

export function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

export function isDirtyDraft(form: FormState) {
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

export function countWords(value: string) {
  return value.trim().match(/\S+/g)?.length ?? 0;
}

export function trimToWordLimit(value: string, limit = CAPTION_WORD_LIMIT) {
  const characters = Array.from(value);
  if (characters.length <= limit) return value;
  return characters.slice(0, limit).join("");
}

export function getDirtySignature(form: FormState) {
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

export function upsertSubmission(items: SubmissionSummary[], next: SubmissionSummary) {
  const exists = items.some((item) => item.id === next.id);
  if (!exists) return [next, ...items];
  return items.map((item) => (item.id === next.id ? next : item));
}

export function getErrorMessage(error: unknown, fallback: string) {
  if (typeof error !== "object" || error === null) return fallback;
  const maybeError = error as {
    message?: string;
    response?: { data?: { error?: string } };
  };
  return maybeError.response?.data?.error || maybeError.message || fallback;
}

export function isConflictError(error: unknown) {
  if (typeof error !== "object" || error === null) return false;
  const status = (error as { response?: { status?: number } }).response?.status;
  return status === 409;
}

export function getOrderedLocalFiles(form: FormState) {
  if (form.mediaOrder.length === 0) return form.files;
  const filesByKey = new Map(form.files.map((file) => [fileMediaKey(file), file]));
  const ordered = form.mediaOrder
    .map((id) => filesByKey.get(id))
    .filter((file): file is File => Boolean(file));
  return ordered.length === form.files.length ? ordered : form.files;
}

export function resolveSavedMediaOrder(form: FormState, savedAssets: SavedMediaAsset[]) {
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

export function resolveSavedMediaCaptions(
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

export function resolveSavedMediaSkipWatermarks(
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

export function shouldSyncMediaDetails(
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

export function mediaCaptionsFromSavedAssets(savedAssets: SavedMediaAsset[]) {
  return Object.fromEntries(
    savedAssets.map((asset) => [savedMediaKey(asset.id), asset.caption ?? ""]),
  );
}

export function mediaSkipWatermarkFromSavedAssets(savedAssets: SavedMediaAsset[]) {
  return Object.fromEntries(
    savedAssets.map((asset) => [savedMediaKey(asset.id), Boolean(asset.skipWatermark)]),
  );
}

export function captionsForSavedIds(
  mediaCaptions: Record<string, string>,
  savedIds: string[],
) {
  return Object.fromEntries(
    savedIds.map((id) => [id, (mediaCaptions[savedMediaKey(id)] ?? "").trim()]),
  );
}

export function skipWatermarksForSavedIds(
  mediaSkipWatermark: Record<string, boolean>,
  savedIds: string[],
) {
  return Object.fromEntries(
    savedIds.map((id) => [id, Boolean(mediaSkipWatermark[savedMediaKey(id)])]),
  );
}

export function pruneMediaCaptions(captions: Record<string, string>, mediaOrder: string[]) {
  const activeKeys = new Set(mediaOrder);
  return Object.fromEntries(
    Object.entries(captions).filter(([key]) => activeKeys.has(key)),
  );
}

export function pruneMediaFlags(flags: Record<string, boolean>, mediaOrder: string[]) {
  const activeKeys = new Set(mediaOrder);
  return Object.fromEntries(
    Object.entries(flags).filter(([key]) => activeKeys.has(key)),
  );
}

export function sortSavedAssetsByOrder(
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

export function sortFilesByOrder(files: File[], orderedIds: string[]) {
  const orderMap = new Map(orderedIds.map((id, index) => [id, index]));
  return [...files].sort((a, b) => {
    const aIndex = orderMap.get(fileMediaKey(a)) ?? Number.MAX_SAFE_INTEGER;
    const bIndex = orderMap.get(fileMediaKey(b)) ?? Number.MAX_SAFE_INTEGER;
    return aIndex - bIndex;
  });
}

export function getReadinessChecklist(
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
      sub: form.caption.trim() ? `${Array.from(form.caption).length} characters` : "Required",
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
      pass: form.files.length > 0 && filesWithinLimit && acceptedFormats,
      idle: form.files.length === 0,
      sub: form.files.length === 0
        ? `${lookups.maxFileSizeMb} MB max; ${lookups.allowedFileTypes.join(", ") || "accepted media only"}`
        : filesWithinLimit && acceptedFormats
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
      sub: `${Array.from(form.caption).length} / ${CAPTION_WORD_LIMIT} characters`,
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
      pass: mediaCaptionCount > 0,
      idle: fileCount === 0,
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

export function getPreviewValidation(
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

export function isWithinPublishWindow(timeValue: string) {
  const [h] = timeValue.split(":").map(Number);
  const m = Number(timeValue.split(":")[1]) || 0;
  const totalMin = h * 60 + m;
  return totalMin >= 8 * 60 && totalMin <= 20 * 60;
}

export function captionTone(caption: string) {
  const characters = Array.from(caption).length;
  if (characters > 0 && characters <= CAPTION_WORD_LIMIT) return "ok";
  if (characters === 0) return "";
  return "warn";
}

export function formatDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat(undefined, {
    month: "short",
    day: "numeric",
  }).format(date);
}

export function formatDateTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat(undefined, {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(date);
}

export function formatLongDate(value: string) {
  const date = parseInputDate(value);
  if (!date) return value;
  return new Intl.DateTimeFormat(undefined, {
    month: "long",
    day: "numeric",
    year: "numeric",
  }).format(date);
}

export function formatTimeDisplay(value: string) {
  return formatTimeParts(parseTimeValue(value));
}

export function formatTimeParts(parts: {
  hour: number;
  minute: number;
  period: "AM" | "PM";
}) {
  return `${parts.hour}:${String(parts.minute).padStart(2, "0")} ${parts.period}`;
}

export function parseTimeValue(value: string) {
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

export function toTimeParts(hour24: number, minute: number) {
  const period: "AM" | "PM" = hour24 >= 12 ? "PM" : "AM";
  const hour = hour24 % 12 || 12;
  return {
    hour,
    minute: Math.min(Math.max(minute, 0), 59),
    period,
  };
}

export function timePartsToValue(parts: {
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

export function cycleNumber(value: number, min: number, max: number) {
  if (value > max) return min;
  if (value < min) return max;
  return value;
}

export function parseInputDate(value: string) {
  if (!value) return null;
  const [year, month, day] = value.split("-").map(Number);
  if (!year || !month || !day) return null;
  const date = new Date(year, month - 1, day);
  if (Number.isNaN(date.getTime())) return null;
  return date;
}

export function dateToInputValue(date: Date) {
  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, "0"),
    String(date.getDate()).padStart(2, "0"),
  ].join("-");
}

export function buildCalendarDays(monthDate: Date) {
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

export function formatTimeInput(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return `${date.getHours().toString().padStart(2, "0")}:${date.getMinutes().toString().padStart(2, "0")}`;
}

export function formatRole(role: User["role"]) {
  if (role === "super_administrator") return "Super Administrator";
  if (role === "administrator") return "Administrator";
  return "Contributor";
}

export function isAllowedFile(file: File, allowedFileTypes: string[]) {
  if (allowedFileTypes.length === 0) return true;
  const extension = normalizeFileType(
    file.name.split(".").pop()?.toLowerCase() || "",
  );
  return Boolean(extension && allowedFileTypes.includes(extension));
}

export function normalizeFileType(fileType: string) {
  return fileType === "jpg" ? "jpeg" : fileType;
}

export function stepLabel(step: ProgressStep) {
  if (step === "media") return "Add Media";
  if (step === "details") return "Post Details";
  return "Preferred Schedule";
}
